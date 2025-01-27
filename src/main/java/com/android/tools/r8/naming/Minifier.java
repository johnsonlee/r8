// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;
import static com.android.tools.r8.utils.StringUtils.EMPTY_CHAR_ARRAY;
import static com.android.tools.r8.utils.SymbolGenerationUtils.RESERVED_NAMES;

import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndField;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.ReferencedMembersCollector;
import com.android.tools.r8.graph.ReferencedMembersCollector.ReferencedMembersConsumer;
import com.android.tools.r8.graph.SubtypingInfo;
import com.android.tools.r8.naming.ClassNameMinifier.ClassNamingStrategy;
import com.android.tools.r8.naming.ClassNameMinifier.ClassRenaming;
import com.android.tools.r8.naming.FieldNameMinifier.FieldRenaming;
import com.android.tools.r8.naming.MethodNameMinifier.MethodRenaming;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.ConsumerUtils;
import com.android.tools.r8.utils.SymbolGenerationUtils;
import com.android.tools.r8.utils.SymbolGenerationUtils.MixedCasing;
import com.android.tools.r8.utils.Timing;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

public class Minifier {

  private final AppView<AppInfoWithLiveness> appView;

  public Minifier(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
  }

  public void run(ExecutorService executorService, Timing timing) throws ExecutionException {
    assert appView.options().isMinifying();
    SubtypingInfo subtypingInfo = MinifierUtils.createSubtypingInfo(appView);
    timing.begin("ComputeInterfaces");
    List<DexClass> interfaces = subtypingInfo.computeReachableInterfacesWithDeterministicOrder();
    timing.end();
    timing.begin("MinifyClasses");
    ClassNameMinifier classNameMinifier =
        new ClassNameMinifier(
            appView,
            appView.options().getLibraryDesugaringOptions().isL8()
                ? new L8MinificationClassNamingStrategy(appView)
                : new MinificationClassNamingStrategy(appView),
            // Use deterministic class order to make sure renaming is deterministic.
            appView.appInfo().classesWithDeterministicOrder());
    ClassRenaming classRenaming = classNameMinifier.computeRenaming(timing);
    timing.end();

    assert new MinifiedRenaming(
            appView, classRenaming, MethodRenaming.empty(), FieldRenaming.empty())
        .verifyNoCollisions(appView.appInfo().classes(), appView.dexItemFactory());

    MemberNamingStrategy minifyMembers = new MinifierMemberNamingStrategy(appView);
    timing.begin("MinifyMethods");
    MethodRenaming methodRenaming =
        new MethodNameMinifier(appView, minifyMembers)
            .computeRenaming(interfaces, subtypingInfo, timing);
    timing.end();

    assert new MinifiedRenaming(appView, classRenaming, methodRenaming, FieldRenaming.empty())
        .verifyNoCollisions(appView.appInfo().classes(), appView.dexItemFactory());

    timing.begin("MinifyFields");
    FieldRenaming fieldRenaming =
        new FieldNameMinifier(appView, subtypingInfo, minifyMembers)
            .computeRenaming(interfaces, timing);
    timing.end();

    // Rename the references that are not rebound to definitions.
    timing.begin("non-rebound-references");
    renameNonReboundReferences(appView, fieldRenaming, methodRenaming, executorService);
    timing.end();

    NamingLens lens = new MinifiedRenaming(appView, classRenaming, methodRenaming, fieldRenaming);
    assert lens.verifyNoCollisions(appView.appInfo().classes(), appView.dexItemFactory());

    appView.testing().namingLensConsumer.accept(appView.dexItemFactory(), lens);
    appView.notifyOptimizationFinishedForTesting();
    appView.setNamingLens(lens);
  }

  public static void renameNonReboundReferences(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      FieldRenaming fieldRenaming,
      MethodRenaming methodRenaming,
      ExecutorService executorService)
      throws ExecutionException {
    Map<DexField, DexString> fieldNames = new ConcurrentHashMap<>(fieldRenaming.renaming);
    fieldRenaming.renaming.clear();
    Map<DexMethod, DexString> methodNames = new ConcurrentHashMap<>(methodRenaming.renaming);
    methodRenaming.renaming.clear();
    ReferencedMembersConsumer consumer =
        new ReferencedMembersConsumer() {

          @Override
          public void onFieldReference(DexField field, ProgramMethod context) {
            // If the given field reference is a non-rebound reference to a program field, then
            // assign the same name as the resolved field.
            if (fieldNames.containsKey(field)) {
              return;
            }
            DexEncodedField resolvedField =
                appView.appInfo().resolveField(field).getResolvedField();
            if (resolvedField != null
                && resolvedField.getReference().isNotIdenticalTo(field)
                && fieldNames.containsKey(resolvedField.getReference())) {
              fieldNames.put(field, fieldNames.get(resolvedField.getReference()));
            }
          }

          @Override
          public void onMethodReference(DexMethod method, ProgramMethod context) {
            // If the given method reference is a non-rebound reference to a program method, then
            // assign the same name as the resolved method.
            if (method.getHolderType().isArrayType() || methodNames.containsKey(method)) {
              return;
            }
            MethodResolutionResult resolutionResult =
                appView.appInfo().unsafeResolveMethodDueToDexFormat(method);
            DexEncodedMethod resolvedMethod = resolutionResult.getResolvedMethod();
            if (resolvedMethod != null
                && resolvedMethod.getReference().isNotIdenticalTo(method)
                && methodNames.containsKey(resolvedMethod.getReference())) {
              methodNames.put(method, methodNames.get(resolvedMethod.getReference()));
            }
            // If resolution fails, the method must be renamed consistently with the targets that
            // give rise to the failure.
            if (resolutionResult.isFailedResolution()) {
              List<DexEncodedMethod> targets = new ArrayList<>();
              resolutionResult
                  .asFailedResolution()
                  .forEachFailureDependency(ConsumerUtils.emptyConsumer(), targets::add);
              if (!targets.isEmpty()) {
                DexString newName = methodNames.get(targets.get(0).getReference());
                assert targets.stream()
                    .allMatch(
                        target -> newName.isIdenticalTo(methodNames.get(target.getReference())));
                if (newName != null) {
                  assert newName.isNotIdenticalTo(targets.get(0).getName());
                  methodNames.put(method, newName);
                }
              }
            }
          }
        };
    new ReferencedMembersCollector(appView, consumer).run(executorService);
    fieldRenaming.renaming.putAll(fieldNames);
    methodRenaming.renaming.putAll(methodNames);
  }

  abstract static class BaseMinificationNamingStrategy {

    // We have to ensure that the names proposed by the minifier is not used in the obfuscation
    // dictionary. We use a list for direct indexing based on a number and a set for looking up.
    private final List<String> obfuscationDictionary;
    private final Set<String> obfuscationDictionaryForLookup;
    private final MixedCasing mixedCasing;

    BaseMinificationNamingStrategy(List<String> obfuscationDictionary, boolean dontUseMixedCasing) {
      assert obfuscationDictionary != null;
      this.obfuscationDictionary = obfuscationDictionary;
      this.obfuscationDictionaryForLookup = new HashSet<>(obfuscationDictionary);
      this.mixedCasing =
          dontUseMixedCasing ? MixedCasing.DONT_USE_MIXED_CASE : MixedCasing.USE_MIXED_CASE;
    }

    /** TODO(b/182992598): using char[] could give problems with unicode */
    String nextName(char[] packagePrefix, InternalNamingState state) {
      StringBuilder nextName = new StringBuilder();
      nextName.append(packagePrefix);
      nextName.append(nextString(packagePrefix, state));
      return nextName.toString();
    }

    String nextString(char[] packagePrefix, InternalNamingState state) {
      String nextString;
      do {
        if (state.getDictionaryIndex() < obfuscationDictionary.size()) {
          nextString = obfuscationDictionary.get(state.incrementDictionaryIndex());
        } else {
          do {
            nextString =
                SymbolGenerationUtils.numberToIdentifier(state.incrementNameIndex(), mixedCasing);
          } while (obfuscationDictionaryForLookup.contains(nextString));
        }
      } while (RESERVED_NAMES.contains(nextString));
      return nextString;
    }
  }

  static class L8MinificationClassNamingStrategy extends MinificationClassNamingStrategy {

    private final String prefix;

    L8MinificationClassNamingStrategy(AppView<AppInfoWithLiveness> appView) {
      super(appView);
      String synthesizedClassPrefix =
          appView.options().getLibraryDesugaringOptions().getSynthesizedClassPrefix();
      prefix = synthesizedClassPrefix.substring(0, synthesizedClassPrefix.length() - 1);
    }

    private boolean startsWithPrefix(char[] packagePrefix) {
      if (packagePrefix.length < prefix.length() + 1) {
        return false;
      }
      for (int i = 0; i < prefix.length(); i++) {
        if (prefix.charAt(i) != packagePrefix[i + 1]) {
          return false;
        }
      }
      return true;
    }

    @Override
    String nextString(char[] packagePrefix, InternalNamingState state) {
      String nextString = super.nextString(packagePrefix, state);
      return startsWithPrefix(packagePrefix) ? nextString : prefix + nextString;
    }
  }

  static class MinificationClassNamingStrategy extends BaseMinificationNamingStrategy
      implements ClassNamingStrategy {

    final AppView<AppInfoWithLiveness> appView;
    final DexItemFactory factory;

    MinificationClassNamingStrategy(AppView<AppInfoWithLiveness> appView) {
      super(
          appView.options().getProguardConfiguration().getClassObfuscationDictionary(),
          appView.options().getProguardConfiguration().hasDontUseMixedCaseClassnames());
      this.appView = appView;
      this.factory = appView.dexItemFactory();
    }

    @Override
    public DexString next(
        DexType type, char[] packagePrefix, InternalNamingState state, Predicate<String> isUsed) {
      String candidate = null;
      String lastName = null;
      do {
        String newName = nextName(packagePrefix, state) + ";";
        if (newName.equals(lastName)) {
          throw new CompilationError(
              "Generating same name '"
                  + newName
                  + "' when given a new minified name to '"
                  + type.toString()
                  + "'.");
        }
        lastName = newName;
        // R.class in Android, which contains constant IDs to assets, can be bundled at any time.
        // Insert `R` immediately so that the class name minifier can skip that name by default.
        if (newName.endsWith("LR;") || newName.endsWith("/R;")) {
          continue;
        }
        candidate = newName;
      } while (candidate == null || isUsed.test(candidate));
      return factory.createString(candidate);
    }

    @Override
    public DexString reservedDescriptor(DexType type) {
      DexProgramClass clazz = asProgramClassOrNull(appView.definitionFor(type));
      if (clazz == null || !appView.getKeepInfo(clazz).isMinificationAllowed(appView.options())) {
        return type.getDescriptor();
      }
      return null;
    }

    @Override
    public boolean isRenamedByApplyMapping(DexType type) {
      return false;
    }
  }

  public static class MinificationPackageNamingStrategy extends BaseMinificationNamingStrategy {

    private final InternalNamingState namingState =
        new InternalNamingState() {

          private int dictionaryIndex = 0;
          private int nameIndex = 1;

          @Override
          public int getDictionaryIndex() {
            return dictionaryIndex;
          }

          @Override
          public int incrementDictionaryIndex() {
            return dictionaryIndex++;
          }

          @Override
          public int incrementNameIndex() {
            return nameIndex++;
          }
        };

    public MinificationPackageNamingStrategy(AppView<?> appView) {
      super(
          appView.options().getProguardConfiguration().getPackageObfuscationDictionary(),
          appView.options().getProguardConfiguration().hasDontUseMixedCaseClassnames());
    }

    public String next(String packagePrefix, Predicate<String> isUsed) {
      // Note that the differences between this method and the other variant for class renaming are
      // 1) this one uses the different dictionary and counter,
      // 2) this one does not append ';' at the end, and
      // 3) this one assumes no 'L' at the beginning to make the return value a binary form.
      String nextPackageName;
      do {
        nextPackageName = nextName(packagePrefix.toCharArray(), namingState);
      } while (isUsed.test(nextPackageName));
      return nextPackageName;
    }
  }

  static class MinifierMemberNamingStrategy extends BaseMinificationNamingStrategy
      implements MemberNamingStrategy {

    final AppView<AppInfoWithLiveness> appView;
    private final DexItemFactory factory;
    private final boolean desugaredLibraryRenaming;

    public MinifierMemberNamingStrategy(AppView<AppInfoWithLiveness> appView) {
      super(appView.options().getProguardConfiguration().getObfuscationDictionary(), false);
      this.appView = appView;
      this.factory = appView.dexItemFactory();
      this.desugaredLibraryRenaming = appView.desugaredLibraryTypeRewriter.isRewriting();
    }

    @Override
    public DexString next(
        DexClassAndMethod method,
        InternalNamingState internalState,
        BiPredicate<DexString, DexMethod> isAvailable) {
      if (!method.isProgramMethod()) {
        return method.getName();
      }
      assert allowMemberRenaming(method);
      DexString candidate;
      do {
        candidate = getNextName(internalState);
      } while (!isAvailable.test(candidate, method.getReference()));
      return candidate;
    }

    @Override
    public DexString next(
        ProgramField field,
        InternalNamingState internalState,
        BiPredicate<DexString, ProgramField> isAvailable) {
      assert allowMemberRenaming(field);
      DexString candidate;
      do {
        candidate = getNextName(internalState);
      } while (!isAvailable.test(candidate, field));
      return candidate;
    }

    private DexString getNextName(InternalNamingState internalState) {
      return factory.createString(nextName(EMPTY_CHAR_ARRAY, internalState));
    }

    @Override
    public DexString getReservedName(DexClassAndMethod method) {
      if (!allowMemberRenaming(method)) {
        return method.getName();
      }
      assert method.isProgramMethod();
      ProgramMethod programMethod = method.asProgramMethod();
      if (method.getHolder().isAnnotation()
          || method.getAccessFlags().isConstructor()
          || !appView.getKeepInfo(programMethod).isMinificationAllowed(appView.options())) {
        return method.getName();
      }
      if (desugaredLibraryRenaming
          && method.getDefinition().isLibraryMethodOverride().isTrue()
          && appView.desugaredLibraryTypeRewriter.hasRewrittenTypeInSignature(
              method.getProto(), appView)) {
        // With desugared library, call-backs names are reserved here.
        return method.getName();
      }
      return null;
    }

    @Override
    public DexString getReservedName(DexClassAndField field) {
      ProgramField programField = field.asProgramField();
      if (programField == null
          || !appView.getKeepInfo(programField).isMinificationAllowed(appView.options())) {
        return field.getName();
      }
      return null;
    }

    @Override
    public boolean allowMemberRenaming(DexClass clazz) {
      return clazz.isProgramClass();
    }
  }
}
