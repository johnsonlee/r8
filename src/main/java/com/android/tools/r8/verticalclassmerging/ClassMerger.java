// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.verticalclassmerging;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;
import static com.android.tools.r8.ir.code.InvokeType.STATIC;
import static com.android.tools.r8.ir.code.InvokeType.VIRTUAL;
import static java.util.function.Predicate.not;

import com.android.tools.r8.cf.CfVersion;
import com.android.tools.r8.classmerging.ClassMergerSharedData;
import com.android.tools.r8.graph.AccessControl;
import com.android.tools.r8.graph.AccessFlags;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DefaultInstanceInitializerCode;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMember;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexTypeList;
import com.android.tools.r8.graph.GenericSignature.ClassSignature;
import com.android.tools.r8.graph.GenericSignature.ClassSignature.ClassSignatureBuilder;
import com.android.tools.r8.graph.GenericSignature.ClassTypeSignature;
import com.android.tools.r8.graph.GenericSignature.FieldTypeSignature;
import com.android.tools.r8.graph.GenericSignature.FormalTypeParameter;
import com.android.tools.r8.graph.GenericSignature.MethodTypeSignature;
import com.android.tools.r8.graph.GenericSignatureContextBuilder;
import com.android.tools.r8.graph.GenericSignatureContextBuilder.TypeParameterContext;
import com.android.tools.r8.graph.GenericSignatureCorrectnessHelper;
import com.android.tools.r8.graph.GenericSignaturePartialTypeArgumentApplier;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedback;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedbackSimple;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.CollectionUtils;
import com.android.tools.r8.utils.collections.DexMethodSignatureMap;
import com.android.tools.r8.utils.collections.DexMethodSignatureSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

class ClassMerger {

  private enum Rename {
    ALWAYS,
    IF_NEEDED
  }

  private static final OptimizationFeedbackSimple feedback =
      OptimizationFeedback.getSimpleFeedback();

  private final AppView<AppInfoWithLiveness> appView;
  private final DexItemFactory dexItemFactory;
  private final VerticalClassMergerGraphLens.Builder lensBuilder;
  private final VerticalClassMergerGraphLens.Builder outerLensBuilder;
  private final VerticallyMergedClasses.Builder verticallyMergedClassesBuilder;

  private final DexProgramClass source;
  private final DexProgramClass target;

  private final ClassMergerSharedData sharedData;
  private final List<IncompleteVerticalClassMergerBridgeCode> synthesizedBridges;

  ClassMerger(
      AppView<AppInfoWithLiveness> appView,
      VerticalClassMergerGraphLens.Builder outerLensBuilder,
      ClassMergerSharedData sharedData,
      List<IncompleteVerticalClassMergerBridgeCode> synthesizedBridges,
      VerticallyMergedClasses.Builder verticallyMergedClassesBuilder,
      VerticalMergeGroup group) {
    this.appView = appView;
    this.dexItemFactory = appView.dexItemFactory();
    this.lensBuilder = new VerticalClassMergerGraphLens.Builder();
    this.outerLensBuilder = outerLensBuilder;
    this.sharedData = sharedData;
    this.synthesizedBridges = synthesizedBridges;
    this.verticallyMergedClassesBuilder = verticallyMergedClassesBuilder;
    this.source = group.getSource();
    this.target = group.getTarget();
  }

  public void setup() {
    setupInvokeSuperMapping();
  }

  private void setupInvokeSuperMapping() {
    source.forEachProgramMethod(this::redirectSuperCallsToMethod);
  }

  private void redirectSuperCallsToMethod(ProgramMethod sourceMethod) {
    if (sourceMethod.getAccessFlags().belongsToDirectPool()) {
      redirectSuperCallsToDirectMethod(sourceMethod);
    } else {
      redirectSuperCallsToVirtualMethod(sourceMethod);
    }
  }

  private void redirectSuperCallsToDirectMethod(ProgramMethod sourceMethod) {
    DexEncodedMethod definition = sourceMethod.getDefinition();
    if (appView.options().canUseNestBasedAccess()
        && source.isInSameNest(target)
        && definition.isInstance()
        && !definition.isInstanceInitializer()
        && AccessControl.isMemberAccessible(sourceMethod, source, target, appView).isTrue()) {
      lensBuilder.mapVirtualMethodToDirectInType(sourceMethod.getReference(), sourceMethod, target);
    }
  }

  private void redirectSuperCallsToVirtualMethod(ProgramMethod sourceMethod) {
    if (sourceMethod.getAccessFlags().isAbstract()) {
      return;
    }
    if (source.isInterface()) {
      // If we merge a default interface method from interface I to its subtype C, then we need
      // to rewrite invocations on the form "invoke-super I.m()" to "invoke-direct C.m$I()".
      //
      // Unlike when we merge a class into its subclass (the else-branch below), we should *not*
      // rewrite any invocations on the form "invoke-super J.m()" to "invoke-direct C.m$I()",
      // if I has a supertype J. This is due to the fact that invoke-super instructions that
      // resolve to a method on an interface never hit an implementation below that interface.
      lensBuilder.mapVirtualMethodToDirectInType(sourceMethod.getReference(), sourceMethod, target);
    } else {
      // If we merge class B into class C, and class C contains an invocation super.m(), then it
      // is insufficient to rewrite "invoke-super B.m()" to "invoke-{direct,virtual} C.m$B()" (the
      // method C.m$B denotes the direct/virtual method that has been created in C for B.m). In
      // particular, there might be an instruction "invoke-super A.m()" in C that resolves to B.m
      // at runtime (A is a superclass of B), which also needs to be rewritten to
      // "invoke-{direct,virtual} C.m$B()".
      //
      // We handle this by adding a mapping for [target] and all of its supertypes.
      DexProgramClass current = target;
      while (current != null) {
        // Only rewrite the invoke-super call if it does not lead to a NoSuchMethodError.
        boolean resolutionSucceeds =
            appView
                .appInfo()
                .resolveMethodOnClass(current, sourceMethod.getReference())
                .isSingleResolution();
        if (!resolutionSucceeds) {
          break;
        }
        DexMethod signatureInHolder =
            sourceMethod.getReference().withHolder(current, dexItemFactory);
        lensBuilder.mapVirtualMethodToDirectInType(signatureInHolder, sourceMethod, target);
        current =
            current.hasSuperType()
                ? asProgramClassOrNull(appView.definitionFor(current.getSuperType()))
                : null;
      }
    }
  }

  public void merge() {
    // Merge the class [clazz] into [targetClass] by adding all methods to
    // targetClass that are not currently contained.
    // Step 1: Merge methods
    DexMethodSignatureSet existingMethods = DexMethodSignatureSet.create();
    existingMethods.addAllMethods(target.methods());

    DexMethodSignatureMap<DexEncodedMethod> directMethods = DexMethodSignatureMap.create();
    DexMethodSignatureMap<DexEncodedMethod> virtualMethods = DexMethodSignatureMap.create();

    Predicate<DexMethod> availableMethodSignatures =
        method ->
            !existingMethods.contains(method)
                && !directMethods.containsKey(method)
                && !virtualMethods.containsKey(method);

    source.forEachProgramDirectMethod(
        directMethod -> {
          DexEncodedMethod method = directMethod.getDefinition();
          DexEncodedMethod movedMethod = moveDirectMethod(method, availableMethodSignatures);
          directMethods.put(movedMethod, movedMethod);
          lensBuilder.recordMove(method, movedMethod);
          blockRedirectionOfSuperCalls(movedMethod);
        });

    for (DexEncodedMethod abstractMethod : source.virtualMethods(DexEncodedMethod::isAbstract)) {
      DexEncodedMethod shadowedBy = findMethodInTarget(abstractMethod);
      if (shadowedBy != null) {
        // Remove abstract/interface methods that are shadowed. The identity mapping below is
        // needed to ensure we correctly fixup the mapping in case the signature refers to
        // merged classes.
        lensBuilder.recordSplit(abstractMethod, shadowedBy, null, null);

        // The override now corresponds to the method in the parent, so unset its synthetic flag
        // if the method in the parent is not synthetic.
        if (!abstractMethod.isSyntheticMethod() && shadowedBy.isSyntheticMethod()) {
          shadowedBy.getAccessFlags().demoteFromSynthetic();
        }
      } else {
        // The method is not shadowed. If it is abstract, we can simply move it to the subclass.
        assert target.isAbstract();
        // Update the holder of [virtualMethod] using renameMethod().
        DexEncodedMethod resultingAbstractMethod = moveMethod(abstractMethod);
        resultingAbstractMethod.setLibraryMethodOverride(abstractMethod.isLibraryMethodOverride());
        lensBuilder.recordMove(abstractMethod, resultingAbstractMethod);
        virtualMethods.put(resultingAbstractMethod, resultingAbstractMethod);
      }
    }

    for (DexEncodedMethod virtualMethod :
        source.virtualMethods(not(DexEncodedMethod::isAbstract))) {
      DexEncodedMethod shadowedBy = findMethodInTarget(virtualMethod);
      DexEncodedMethod resultingMethod;
      if (source.isInterface()) {
        // Moving a default interface method into its subtype. This method could be hit directly
        // via an invoke-super instruction from any of the transitive subtypes of this interface,
        // due to the way invoke-super works on default interface methods. In order to be able
        // to hit this method directly after the merge, we need to make it public, and find a
        // method name that does not collide with one in the hierarchy of this class.
        String resultingMethodBaseName =
            virtualMethod.getName().toString() + '$' + source.getTypeName().replace('.', '$');
        DexMethod resultingMethodReference =
            dexItemFactory.createMethod(
                target.getType(),
                virtualMethod.getProto().prependParameter(source.getType(), dexItemFactory),
                dexItemFactory.createGloballyFreshMemberString(resultingMethodBaseName));
        assert availableMethodSignatures.test(resultingMethodReference);
        resultingMethod =
            virtualMethod.toTypeSubstitutedMethodAsInlining(
                resultingMethodReference,
                dexItemFactory,
                builder ->
                    builder
                        .modifyAccessFlags(AccessFlags::setStatic)
                        .unsetIsLibraryMethodOverride());
      } else {
        // This virtual method could be called directly from a sub class via an invoke-super
        // instruction. Therefore, we translate this virtual method into an instance method with a
        // unique name, such that relevant invoke-super instructions can be rewritten to target
        // this method directly.
        resultingMethod = renameMethod(virtualMethod, availableMethodSignatures, Rename.ALWAYS);
        makePublicFinal(resultingMethod.getAccessFlags());
      }

      DexMethodSignatureMap<DexEncodedMethod> methodPool =
          resultingMethod.belongsToDirectPool() ? directMethods : virtualMethods;
      methodPool.put(resultingMethod, resultingMethod);

      DexEncodedMethod bridge = null;
      DexEncodedMethod override = shadowedBy;
      if (shadowedBy == null) {
        // In addition to the newly added direct method, create a virtual method such that we do
        // not accidentally remove the method from the interface of this class.
        // Note that this method is added independently of whether it will actually be used. If
        // it turns out that the method is never used, it will be removed by the final round
        // of tree shaking.
        bridge = shadowedBy = buildBridgeMethod(virtualMethod, resultingMethod);
        virtualMethods.put(shadowedBy, shadowedBy);
      }

      // Copy over any keep info from the original virtual method.
      ProgramMethod programMethod = new ProgramMethod(target, shadowedBy);
      appView
          .getKeepInfo()
          .mutate(
              mutableKeepInfoCollection ->
                  mutableKeepInfoCollection.joinMethod(
                      programMethod,
                      info ->
                          info.merge(
                              mutableKeepInfoCollection
                                  .getMethodInfo(virtualMethod, source)
                                  .joiner())));

      lensBuilder.recordSplit(virtualMethod, override, bridge, resultingMethod);
    }

    // Rewrite generic signatures before we merge a base with a generic signature.
    rewriteGenericSignatures(target, source, directMethods.values(), virtualMethods.values());

    // Convert out of DefaultInstanceInitializerCode, since this piece of code will require lens
    // code rewriting.
    target.forEachProgramInstanceInitializerMatching(
        method -> method.getCode().isDefaultInstanceInitializerCode(),
        method -> DefaultInstanceInitializerCode.uncanonicalizeCode(appView, method));

    // Step 2: Merge fields
    Set<DexString> existingFieldNames = new HashSet<>();
    for (DexEncodedField field : target.fields()) {
      existingFieldNames.add(field.getReference().name);
    }

    // In principle, we could allow multiple fields with the same name, and then only rename the
    // field in the end when we are done merging all the classes, if it it turns out that the two
    // fields ended up having the same type. This would not be too expensive, since we visit the
    // entire program using VerticalClassMerger.TreeFixer anyway.
    //
    // For now, we conservatively report that a signature is already taken if there is a field
    // with the same name. If minification is used with -overloadaggressively, this is solved
    // later anyway.
    Predicate<DexField> availableFieldSignatures =
        field -> !existingFieldNames.contains(field.name);

    DexEncodedField[] mergedInstanceFields =
        mergeFields(
            source.instanceFields(),
            target.instanceFields(),
            availableFieldSignatures,
            existingFieldNames);

    DexEncodedField[] mergedStaticFields =
        mergeFields(
            source.staticFields(),
            target.staticFields(),
            availableFieldSignatures,
            existingFieldNames);

    // Step 3: Merge interfaces
    Set<DexType> interfaces = mergeArrays(target.interfaces.values, source.interfaces.values);
    // Now destructively update the class.
    // Step 1: Update supertype or fix interfaces.
    if (source.isInterface()) {
      interfaces.remove(source.type);
    } else {
      assert !target.isInterface();
      target.superType = source.superType;
    }
    target.interfaces =
        interfaces.isEmpty()
            ? DexTypeList.empty()
            : new DexTypeList(interfaces.toArray(DexType.EMPTY_ARRAY));
    // Step 2: ensure -if rules cannot target the members that were merged into the target class.
    directMethods.values().forEach(feedback::markMethodCannotBeKept);
    virtualMethods.values().forEach(feedback::markMethodCannotBeKept);
    for (int i = 0; i < source.instanceFields().size(); i++) {
      feedback.markFieldCannotBeKept(mergedInstanceFields[i]);
    }
    for (int i = 0; i < source.staticFields().size(); i++) {
      feedback.markFieldCannotBeKept(mergedStaticFields[i]);
    }
    // Step 3: replace fields and methods.
    target.addDirectMethods(directMethods.values());
    target.addVirtualMethods(virtualMethods.values());
    target.setInstanceFields(mergedInstanceFields);
    target.setStaticFields(mergedStaticFields);
    // Step 4: Clear the members of the source class since they have now been moved to the target.
    source.getMethodCollection().clearDirectMethods();
    source.getMethodCollection().clearVirtualMethods();
    source.clearInstanceFields();
    source.clearStaticFields();
    // Step 5: Update access flags and merge attributes.
    fixupAccessFlags();
    if (source.isNestHost()) {
      target.clearNestHost();
      target.setNestMemberAttributes(source.getNestMembersClassAttributes());
    }
    // Step 6: Record merging.
    assert GenericSignatureCorrectnessHelper.createForVerification(
            appView, GenericSignatureContextBuilder.createForSingleClass(appView, target))
        .evaluateSignaturesForClass(target)
        .isValid();
    outerLensBuilder.merge(lensBuilder);
    verticallyMergedClassesBuilder.add(source, target);
  }

  private void fixupAccessFlags() {
    if (source.getAccessFlags().isEnum()) {
      target.getAccessFlags().setEnum();
    }
  }

  /**
   * The rewriting of generic signatures is pretty simple, but require some bookkeeping. We take the
   * arguments to the base type:
   *
   * <pre>
   *   class Sub<X> extends Base<X, String>
   * </pre>
   *
   * <p>for
   *
   * <pre>
   *   class Base<T,R> extends OtherBase<T> implements I<R> {
   *     T t() { ... };
   *   }
   * </pre>
   *
   * <p>and substitute T -> X and R -> String
   */
  private void rewriteGenericSignatures(
      DexProgramClass target,
      DexProgramClass source,
      Collection<DexEncodedMethod> directMethods,
      Collection<DexEncodedMethod> virtualMethods) {
    ClassSignature targetSignature = target.getClassSignature();
    if (targetSignature.hasNoSignature()) {
      // Null out all source signatures that is moved, but do not clear out the class since this
      // could be referred to by other generic signatures.
      // TODO(b/147504070): If merging classes with enclosing/innerclasses, this needs to be
      //  reconsidered.
      directMethods.forEach(DexEncodedMethod::clearGenericSignature);
      virtualMethods.forEach(DexEncodedMethod::clearGenericSignature);
      source.fields().forEach(DexEncodedMember::clearGenericSignature);
      return;
    }
    GenericSignaturePartialTypeArgumentApplier classApplier =
        getGenericSignatureArgumentApplier(target, source);
    if (classApplier == null) {
      target.clearClassSignature();
      target.members().forEach(DexEncodedMember::clearGenericSignature);
      return;
    }
    // We could generate a substitution map.
    ClassSignature rewrittenSource = classApplier.visitClassSignature(source.getClassSignature());
    // The variables in the class signature is now rewritten to use the targets argument.
    ClassSignatureBuilder builder = ClassSignature.builder();
    builder.addFormalTypeParameters(targetSignature.getFormalTypeParameters());
    if (!source.isInterface()) {
      if (rewrittenSource.hasSignature()) {
        builder.setSuperClassSignature(rewrittenSource.getSuperClassSignatureOrNull());
      } else {
        builder.setSuperClassSignature(new ClassTypeSignature(source.superType));
      }
    } else {
      builder.setSuperClassSignature(targetSignature.getSuperClassSignatureOrNull());
    }
    // Compute the seen set for interfaces to add. This is similar to the merging of interfaces
    // but allow us to maintain the type arguments.
    Set<DexType> seenInterfaces = new HashSet<>();
    if (source.isInterface()) {
      seenInterfaces.add(source.type);
    }
    for (ClassTypeSignature iFace : targetSignature.getSuperInterfaceSignatures()) {
      if (seenInterfaces.add(iFace.type())) {
        builder.addSuperInterfaceSignature(iFace);
      }
    }
    if (rewrittenSource.hasSignature()) {
      for (ClassTypeSignature iFace : rewrittenSource.getSuperInterfaceSignatures()) {
        if (!seenInterfaces.contains(iFace.type())) {
          builder.addSuperInterfaceSignature(iFace);
        }
      }
    } else {
      // Synthesize raw uses of interfaces to align with the actual class
      for (DexType iFace : source.interfaces) {
        if (!seenInterfaces.contains(iFace)) {
          builder.addSuperInterfaceSignature(new ClassTypeSignature(iFace));
        }
      }
    }
    target.setClassSignature(builder.build(dexItemFactory));

    // Go through all type-variable references for members and update them.
    CollectionUtils.forEach(
        method -> {
          MethodTypeSignature methodSignature = method.getGenericSignature();
          if (methodSignature.hasNoSignature()) {
            return;
          }
          method.setGenericSignature(
              classApplier
                  .buildForMethod(methodSignature.getFormalTypeParameters())
                  .visitMethodSignature(methodSignature));
        },
        directMethods,
        virtualMethods);

    source.forEachField(
        field -> {
          if (field.getGenericSignature().hasNoSignature()) {
            return;
          }
          field.setGenericSignature(
              classApplier.visitFieldTypeSignature(field.getGenericSignature()));
        });
  }

  private GenericSignaturePartialTypeArgumentApplier getGenericSignatureArgumentApplier(
      DexProgramClass target, DexProgramClass source) {
    assert target.getClassSignature().hasSignature();
    // We can assert proper structure below because the generic signature validator has run
    // before and pruned invalid signatures.
    List<FieldTypeSignature> genericArgumentsToSuperType =
        target.getClassSignature().getGenericArgumentsToSuperType(source.type, dexItemFactory);
    if (genericArgumentsToSuperType == null) {
      assert false : "Type should be present in generic signature";
      return null;
    }
    Map<String, FieldTypeSignature> substitutionMap = new HashMap<>();
    List<FormalTypeParameter> formals = source.getClassSignature().getFormalTypeParameters();
    if (genericArgumentsToSuperType.size() != formals.size()) {
      if (!genericArgumentsToSuperType.isEmpty()) {
        assert false : "Invalid argument count to formals";
        return null;
      }
    } else {
      for (int i = 0; i < formals.size(); i++) {
        // It is OK to override a generic type variable so we just use put.
        substitutionMap.put(formals.get(i).getName(), genericArgumentsToSuperType.get(i));
      }
    }
    return GenericSignaturePartialTypeArgumentApplier.build(
        appView,
        TypeParameterContext.empty().addPrunedSubstitutions(substitutionMap),
        (type1, type2) -> true,
        type -> true);
  }

  private void blockRedirectionOfSuperCalls(DexEncodedMethod method) {
    // We are merging a class B into C. The methods from B are being moved into C, and then we
    // subsequently rewrite the invoke-super instructions in C that hit a method in B, such that
    // they use an invoke-direct instruction instead. In this process, we need to avoid rewriting
    // the invoke-super instructions that originally was in the superclass B.
    //
    // Example:
    //   class A {
    //     public void m() {}
    //   }
    //   class B extends A {
    //     public void m() { super.m(); } <- invoke must not be rewritten to invoke-direct
    //                                       (this would lead to an infinite loop)
    //   }
    //   class C extends B {
    //     public void m() { super.m(); } <- invoke needs to be rewritten to invoke-direct
    //   }
    lensBuilder.markMethodAsMerged(method);
  }

  private DexEncodedMethod buildBridgeMethod(
      DexEncodedMethod method, DexEncodedMethod invocationTarget) {
    DexMethod newMethod = method.getReference().withHolder(target, dexItemFactory);
    MethodAccessFlags accessFlags = method.getAccessFlags().copy();
    accessFlags.setBridge();
    accessFlags.setSynthetic();
    accessFlags.unsetAbstract();

    assert invocationTarget.isStatic() || invocationTarget.isNonPrivateVirtualMethod();
    IncompleteVerticalClassMergerBridgeCode code =
        new IncompleteVerticalClassMergerBridgeCode(
            method.getReference(),
            invocationTarget.isStatic() ? STATIC : VIRTUAL,
            target.isInterface());

    // Add the bridge to the list of synthesized bridges such that the method signatures will
    // be updated by the end of vertical class merging.
    synthesizedBridges.add(code);

    CfVersion classFileVersion = method.hasClassFileVersion() ? method.getClassFileVersion() : null;
    DexEncodedMethod bridge =
        DexEncodedMethod.syntheticBuilder()
            .setMethod(newMethod)
            .setAccessFlags(accessFlags)
            .setCode(code)
            .setClassFileVersion(classFileVersion)
            .setApiLevelForDefinition(method.getApiLevelForDefinition())
            .setApiLevelForCode(method.getApiLevelForDefinition())
            .setIsLibraryMethodOverride(method.isLibraryMethodOverride())
            .setGenericSignature(method.getGenericSignature())
            .build();
    // The bridge is now the public method serving the role of the original method, and should
    // reflect that this method was publicized.
    assert !method.getAccessFlags().isPromotedToPublic()
        || bridge.getAccessFlags().isPromotedToPublic();
    return bridge;
  }

  // Returns the method that shadows the given method, or null if method is not shadowed.
  private DexEncodedMethod findMethodInTarget(DexEncodedMethod method) {
    DexEncodedMethod resolvedMethod =
        appView.appInfo().resolveMethodOnLegacy(target, method.getReference()).getResolvedMethod();
    assert resolvedMethod != null;
    if (resolvedMethod != method) {
      assert resolvedMethod.isVirtualMethod() == method.isVirtualMethod();
      return resolvedMethod;
    }
    // The method is not actually overridden. This means that we will move `method` to the subtype.
    // If `method` is abstract, then so should the subtype be.
    return null;
  }

  private <T> Set<T> mergeArrays(T[] one, T[] other) {
    Set<T> merged = new LinkedHashSet<>();
    Collections.addAll(merged, one);
    Collections.addAll(merged, other);
    return merged;
  }

  private DexEncodedField[] mergeFields(
      Collection<DexEncodedField> sourceFields,
      Collection<DexEncodedField> targetFields,
      Predicate<DexField> availableFieldSignatures,
      Set<DexString> existingFieldNames) {
    DexEncodedField[] result = new DexEncodedField[sourceFields.size() + targetFields.size()];
    // Add fields from source
    int i = 0;
    for (DexEncodedField field : sourceFields) {
      DexEncodedField resultingField = renameFieldIfNeeded(field, availableFieldSignatures);
      existingFieldNames.add(resultingField.getName());
      lensBuilder.recordMove(field, resultingField);
      result[i] = resultingField;
      i++;
    }
    // Add fields from target.
    for (DexEncodedField field : targetFields) {
      result[i] = field;
      i++;
    }
    return result;
  }

  private DexEncodedMethod moveDirectMethod(
      DexEncodedMethod method, Predicate<DexMethod> availableMethodSignatures) {
    if (method.isClassInitializer()) {
      return moveMethod(method);
    } else if (method.isInstanceInitializer()) {
      return moveInstanceInitializer(method, availableMethodSignatures);
    } else {
      return renameMethod(method, availableMethodSignatures, Rename.IF_NEEDED);
    }
  }

  private DexEncodedMethod moveInstanceInitializer(
      DexEncodedMethod method, Predicate<DexMethod> availableMethodSignatures) {
    DexMethod newSignature =
        dexItemFactory.createInstanceInitializerWithFreshProto(
            method.getReference().withHolder(target, dexItemFactory),
            sharedData.getExtraUnusedArgumentTypes(),
            availableMethodSignatures);
    return method.toTypeSubstitutedMethodAsInlining(newSignature, dexItemFactory);
  }

  private DexEncodedMethod moveMethod(DexEncodedMethod method) {
    DexMethod newSignature = method.getReference().withHolder(target, dexItemFactory);
    return method.toTypeSubstitutedMethodAsInlining(newSignature, dexItemFactory);
  }

  private DexEncodedMethod renameMethod(
      DexEncodedMethod method, Predicate<DexMethod> availableMethodSignatures, Rename strategy) {
    if (strategy == Rename.IF_NEEDED
        && availableMethodSignatures.test(
            method.getReference().withHolder(target, dexItemFactory))) {
      return moveMethod(method);
    }
    DexMethod newReference =
        dexItemFactory.createFreshMethodNameWithHolder(
            method.getName().toSourceString(),
            source.getType(),
            method.getProto(),
            target.getType(),
            availableMethodSignatures);
    return method.toTypeSubstitutedMethodAsInlining(newReference, dexItemFactory);
  }

  private DexEncodedField renameFieldIfNeeded(
      DexEncodedField field, Predicate<DexField> availableFieldSignatures) {
    DexField newReference =
        dexItemFactory.createFreshFieldNameWithoutHolder(
            target.getType(),
            field.getType(),
            field.getName().toString(),
            availableFieldSignatures);
    return field.toTypeSubstitutedField(appView, newReference);
  }

  private static void makePublicFinal(MethodAccessFlags accessFlags) {
    assert !accessFlags.isAbstract();
    accessFlags.unsetPrivate();
    accessFlags.unsetProtected();
    accessFlags.setPublic();
    accessFlags.setFinal();
  }
}
