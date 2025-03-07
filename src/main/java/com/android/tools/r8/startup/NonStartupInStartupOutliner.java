// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.startup;

import static com.android.tools.r8.utils.MapUtils.ignoreKey;

import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.contexts.CompilationContext.ProcessorContext;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DefaultUseRegistryWithResult;
import com.android.tools.r8.graph.DexClassAndField;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.synthetic.ForwardMethodBuilder;
import com.android.tools.r8.optimize.singlecaller.SingleCallerInliner;
import com.android.tools.r8.profile.rewriting.ProfileCollectionAdditions;
import com.android.tools.r8.profile.startup.profile.StartupProfile;
import com.android.tools.r8.shaking.KeepMethodInfo.Joiner;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import com.android.tools.r8.utils.timing.Timing;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

// TODO(b/275292237): Preserve class initialization side effects using InitClass.
// TODO(b/275292237): Preserve receiver NPE semantics by inserting null checks.
public class NonStartupInStartupOutliner {

  private final AppView<? extends AppInfoWithClassHierarchy> appView;
  private final DexItemFactory factory;
  private final NonStartupInStartupOutlinerLens.Builder lensBuilder =
      NonStartupInStartupOutlinerLens.builder();
  private final StartupProfile startupProfile;
  private final ProgramMethodSet syntheticMethods;

  public NonStartupInStartupOutliner(AppView<? extends AppInfoWithClassHierarchy> appView) {
    this.appView = appView;
    this.factory = appView.dexItemFactory();
    this.startupProfile = appView.getStartupProfile();
    this.syntheticMethods = ProgramMethodSet.createConcurrent();
  }

  public void runIfNecessary(ExecutorService executorService, Timing timing)
      throws ExecutionException {
    if (!startupProfile.isEmpty() && appView.options().getStartupOptions().isOutliningEnabled()) {
      timing.time("NonStartupInStartupOutliner", () -> run(executorService, timing));
    }
  }

  private void run(ExecutorService executorService, Timing timing) throws ExecutionException {
    Map<DexProgramClass, List<ProgramMethod>> methodsToOutline =
        getMethodsToOutline(executorService);
    if (methodsToOutline.isEmpty()) {
      return;
    }
    ProfileCollectionAdditions profileCollectionAdditions =
        ProfileCollectionAdditions.create(appView);
    performOutlining(methodsToOutline, executorService, profileCollectionAdditions);
    profileCollectionAdditions.commit(appView);
    commitPendingSyntheticClasses();
    setSyntheticKeepInfo();
    rewriteWithLens(executorService, timing);
  }

  private Map<DexProgramClass, List<ProgramMethod>> getMethodsToOutline(
      ExecutorService executorService) throws ExecutionException {
    Map<DexProgramClass, List<ProgramMethod>> methodsToOutline = new ConcurrentHashMap<>();
    ThreadUtils.processItems(
        appView.appInfo().classes(),
        clazz ->
            forEachMethodToOutline(
                clazz,
                method ->
                    methodsToOutline.computeIfAbsent(clazz, ignoreKey(ArrayList::new)).add(method)),
        appView.options().getThreadingModule(),
        executorService);
    return methodsToOutline;
  }

  private void forEachMethodToOutline(DexProgramClass clazz, Consumer<ProgramMethod> fn) {
    if (!startupProfile.isStartupClass(clazz.getType())) {
      return;
    }
    clazz.forEachProgramMethodMatching(
        DexEncodedMethod::hasCode,
        method -> {
          if (!startupProfile.containsMethodRule(method.getReference())
              && !method.getDefinition().isInitializer()
              && canCodeBeMoved(method)) {
            fn.accept(method);
          }
        });
  }

  // TODO(b/275292237): Extend to cover all possible accesses to private items (e.g., consider
  //  method handles).
  private boolean canCodeBeMoved(ProgramMethod method) {
    return method.registerCodeReferencesWithResult(
        new DefaultUseRegistryWithResult<>(appView, method, true) {

          private AppInfoWithClassHierarchy appInfo() {
            return NonStartupInStartupOutliner.this.appView.appInfo();
          }

          private void setCodeCannotBeMoved() {
            setResult(false);
          }

          // Field accesses.

          @Override
          public void registerInstanceFieldRead(DexField field) {
            registerFieldAccess(field);
          }

          @Override
          public void registerInstanceFieldWrite(DexField field) {
            registerFieldAccess(field);
          }

          @Override
          public void registerStaticFieldRead(DexField field) {
            registerFieldAccess(field);
          }

          @Override
          public void registerStaticFieldWrite(DexField field) {
            registerFieldAccess(field);
          }

          private void registerFieldAccess(DexField field) {
            DexClassAndField resolvedField =
                appInfo().resolveField(field, getContext()).getResolutionPair();
            if (resolvedField == null) {
              return;
            }
            if (resolvedField.getAccessFlags().isPrivate()) {
              setCodeCannotBeMoved();
            } else if (resolvedField.getAccessFlags().isProtected()
                && !resolvedField.isSamePackage(getContext())) {
              setCodeCannotBeMoved();
            }
          }

          // Invokes.

          @Override
          public void registerInvokeDirect(DexMethod method) {
            registerInvokeMethod(appInfo().unsafeResolveMethodDueToDexFormat(method));
          }

          @Override
          public void registerInvokeInterface(DexMethod method) {
            registerInvokeMethod(appInfo().resolveMethod(method, true));
          }

          @Override
          public void registerInvokeStatic(DexMethod method) {
            registerInvokeMethod(appInfo().unsafeResolveMethodDueToDexFormat(method));
          }

          @Override
          public void registerInvokeSuper(DexMethod method) {
            setCodeCannotBeMoved();
          }

          @Override
          public void registerInvokeVirtual(DexMethod method) {
            registerInvokeMethod(appInfo().resolveMethod(method, false));
          }

          private void registerInvokeMethod(MethodResolutionResult resolutionResult) {
            DexClassAndMethod resolvedMethod = resolutionResult.getResolutionPair();
            if (resolvedMethod == null) {
              return;
            }
            if (resolvedMethod.getAccessFlags().isPrivate()) {
              setCodeCannotBeMoved();
            } else if (resolvedMethod.getAccessFlags().isProtected()
                && !resolvedMethod.isSamePackage(getContext())) {
              setCodeCannotBeMoved();
            }
          }
        });
  }

  private void performOutlining(
      Map<DexProgramClass, List<ProgramMethod>> methodsToOutline,
      ExecutorService executorService,
      ProfileCollectionAdditions profileCollectionAdditions)
      throws ExecutionException {
    // TODO(b/275292237): Only compute this information for virtual methods in startup classes.
    ProcessorContext processorContext = appView.createProcessorContext();
    ProgramMethodSet monomorphicVirtualMethods =
        SingleCallerInliner.computeMonomorphicVirtualRootMethods(appView, executorService);
    ThreadUtils.processMap(
        methodsToOutline,
        (clazz, methods) ->
            performOutliningForClass(
                clazz,
                methods,
                monomorphicVirtualMethods,
                processorContext,
                profileCollectionAdditions),
        appView.options().getThreadingModule(),
        executorService);
  }

  private void performOutliningForClass(
      DexProgramClass clazz,
      List<ProgramMethod> methodsToOutline,
      ProgramMethodSet monomorphicVirtualMethods,
      ProcessorContext processorContext,
      ProfileCollectionAdditions profileCollectionAdditions) {
    Set<DexEncodedMethod> methodsToRemove = Sets.newIdentityHashSet();
    for (ProgramMethod method : methodsToOutline) {
      MethodProcessingContext methodProcessingContext =
          processorContext.createMethodProcessingContext(method);
      ProgramMethod syntheticMethod;
      boolean isMove = isMoveable(method, monomorphicVirtualMethods);
      if (isMove) {
        syntheticMethod = performMove(method, methodProcessingContext);
        methodsToRemove.add(method.getDefinition());
      } else {
        syntheticMethod = performOutliningForMethod(method, methodProcessingContext);
      }
      profileCollectionAdditions.applyIfContextIsInProfile(
          method.getReference(),
          additionsBuilder -> {
            additionsBuilder
                .addClassRule(syntheticMethod.getHolderType())
                .addMethodRule(syntheticMethod.getReference());
            if (isMove) {
              additionsBuilder.removeMovedMethodRule(method, syntheticMethod);
            }
          });
      syntheticMethods.add(syntheticMethod);
    }
    clazz.getMethodCollection().removeMethods(methodsToRemove);
  }

  private boolean isMoveable(ProgramMethod method, ProgramMethodSet monomorphicVirtualMethods) {
    // If we extend this to D8 then we can never move any methods since this would require a mapping
    // file for retracing.
    assert appView.enableWholeProgramOptimizations();
    if (!appView.getKeepInfo(method).isShrinkingAllowed(appView.options())) {
      // Kept methods can never be moved.
      return false;
    }
    if (method.getAccessFlags().isStatic()) {
      // Static methods can always be moved. Class initialization side effects can be preserved by
      // inserting an InitClass instruction in the beginning of the moved method.
      return true;
    }
    if (method.getAccessFlags().isPrivate()) {
      // Private methods have direct dispatch and can always be made public static.
      return true;
    }
    // Virtual methods can only be staticized and moved if they are monomorphic.
    assert method.getAccessFlags().belongsToVirtualPool();
    return method.getDefinition().isLibraryMethodOverride().isFalse()
        && monomorphicVirtualMethods.contains(method);
  }

  private ProgramMethod performMove(
      ProgramMethod method, MethodProcessingContext methodProcessingContext) {
    ProgramMethod movedMethod =
        createSyntheticMethod(
            method,
            methodProcessingContext,
            method.getAccessFlags().copy().promoteToPublic().promoteToStatic());

    // Record the move in the lens for correct lens code rewriting.
    lensBuilder.recordMove(method, movedMethod);

    return movedMethod;
  }

  private ProgramMethod performOutliningForMethod(
      ProgramMethod method, MethodProcessingContext methodProcessingContext) {
    ProgramMethod outlinedMethod =
        createSyntheticMethod(
            method, methodProcessingContext, MethodAccessFlags.createPublicStaticSynthetic());

    // Rewrite the non-synthetic method to call the synthetic method.
    method.setCode(
        ForwardMethodBuilder.builder(factory)
            .applyIf(
                method.getAccessFlags().isStatic(),
                codeBuilder -> codeBuilder.setStaticSource(method.getReference()),
                codeBuilder -> codeBuilder.setNonStaticSource(method.getReference()))
            .setStaticTarget(outlinedMethod.getReference(), false)
            .buildLir(appView),
        appView);

    return outlinedMethod;
  }

  private ProgramMethod createSyntheticMethod(
      ProgramMethod method,
      MethodProcessingContext methodProcessingContext,
      MethodAccessFlags accessFlags) {
    return appView
        .getSyntheticItems()
        .createMethod(
            kinds -> kinds.NON_STARTUP_IN_STARTUP_OUTLINE,
            methodProcessingContext.createUniqueContext(),
            appView,
            builder ->
                builder
                    .setAccessFlags(accessFlags)
                    .setApiLevelForCode(method.getDefinition().getApiLevelForCode())
                    .setApiLevelForDefinition(method.getDefinition().getApiLevelForDefinition())
                    .setProto(
                        factory.prependHolderToProtoIf(
                            method.getReference(), !method.getAccessFlags().isStatic()))
                    .setCode(
                        syntheticMethod -> {
                          Code code;
                          if (method.getDefinition().getCode().supportsPendingInlineFrame()) {
                            code = method.getDefinition().getCode();
                            builder.setInlineFrame(
                                method.getReference(), method.getDefinition().isD8R8Synthesized());
                          } else {
                            code =
                                method
                                    .getDefinition()
                                    .getCode()
                                    .getCodeAsInlining(
                                        syntheticMethod,
                                        true,
                                        method.getReference(),
                                        method.getDefinition().isD8R8Synthesized(),
                                        factory);
                          }
                          if (!method.getAccessFlags().isStatic()) {
                            DexEncodedMethod.setDebugInfoWithFakeThisParameter(
                                code, syntheticMethod.getArity(), appView);
                          }
                          return code;
                        }));
  }

  private void commitPendingSyntheticClasses() {
    if (appView.getSyntheticItems().hasPendingSyntheticClasses()) {
      appView.rebuildAppInfo();
    }
  }

  private void setSyntheticKeepInfo() {
    appView
        .getKeepInfo()
        .mutate(
            keepInfo ->
                syntheticMethods.forEach(
                    syntheticMethod -> {
                      keepInfo.registerCompilerSynthesizedMethod(syntheticMethod);
                      keepInfo.joinMethod(syntheticMethod, Joiner::disallowInlining);
                    }));
  }

  private void rewriteWithLens(ExecutorService executorService, Timing timing)
      throws ExecutionException {
    if (lensBuilder.isEmpty()) {
      return;
    }
    NonStartupInStartupOutlinerLens lens = lensBuilder.build(appView);
    appView.rewriteWithLens(lens, executorService, timing);
  }
}
