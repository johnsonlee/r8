// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DefaultUseRegistryWithResult;
import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexMethodHandle;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexValue;
import com.android.tools.r8.graph.FieldResolutionResult;
import com.android.tools.r8.graph.MethodResolutionResult.SingleResolutionResult;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.bytecodemetadata.BytecodeMetadataProvider;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.BasicBlockIterator;
import com.android.tools.r8.ir.code.FieldInstruction;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.conversion.IRConverter;
import com.android.tools.r8.ir.conversion.IRToLirFinalizer;
import com.android.tools.r8.ir.conversion.PostMethodProcessor;
import com.android.tools.r8.ir.desugar.LambdaDescriptor;
import com.android.tools.r8.ir.desugar.records.RecordRewriterHelper;
import com.android.tools.r8.ir.optimize.AffectedValues;
import com.android.tools.r8.ir.optimize.info.CallSiteOptimizationInfo;
import com.android.tools.r8.lightir.LirCode;
import com.android.tools.r8.lightir.LirConstant;
import com.android.tools.r8.optimize.argumentpropagation.reprocessingcriteria.ArgumentPropagatorReprocessingCriteriaCollection;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.ArrayUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ObjectUtils;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import com.android.tools.r8.utils.timing.Timing;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

/** Finds the methods in the program that should be reprocessed due to argument propagation. */
public class ArgumentPropagatorMethodReprocessingEnqueuer {

  private final AppView<AppInfoWithLiveness> appView;
  private final ProgramMethodSet prunedMethods;
  private final ArgumentPropagatorReprocessingCriteriaCollection reprocessingCriteriaCollection;

  public ArgumentPropagatorMethodReprocessingEnqueuer(
      AppView<AppInfoWithLiveness> appView,
      ProgramMethodSet prunedMethods,
      ArgumentPropagatorReprocessingCriteriaCollection reprocessingCriteriaCollection) {
    this.appView = appView;
    this.prunedMethods = prunedMethods;
    this.reprocessingCriteriaCollection = reprocessingCriteriaCollection;
  }

  /**
   * Called indirectly from {@link IRConverter} to add all methods that require reprocessing to
   * {@param postMethodProcessorBuilder}.
   */
  public void enqueueAndPrepareMethodsForReprocessing(
      ArgumentPropagatorGraphLens graphLens,
      PostMethodProcessor.Builder postMethodProcessorBuilder,
      ExecutorService executorService,
      Timing timing)
      throws ExecutionException {
    timing.begin("Enqueue methods for reprocessing");

    // Bring the methods to reprocess set up-to-date with the current graph lens (i.e., the one
    // prior to the argument propagator lens, which has not yet been installed!).
    timing.begin("Rewrite methods to reprocess");
    postMethodProcessorBuilder.rewrittenWithLens(appView);
    timing.end();

    timing.begin("Enqueue methods with non-trivial info");
    enqueueAffectedCallees(graphLens, postMethodProcessorBuilder);
    timing.end();

    timing.begin("Enqueue affected methods");
    if (graphLens != null) {
      enqueueAffectedCallers(graphLens, postMethodProcessorBuilder, executorService);
    }
    timing.end();

    timing.begin("Eliminate dead field accesses");
    eliminateDeadFieldAndMethodAccesses(executorService);
    timing.end();

    timing.end();
  }

  private void enqueueAffectedCallees(
      ArgumentPropagatorGraphLens graphLens,
      PostMethodProcessor.Builder postMethodProcessorBuilder) {
    GraphLens currentGraphLens = appView.graphLens();
    InternalOptions options = appView.options();
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      clazz.forEachProgramMethodMatching(
          DexEncodedMethod::hasCode,
          method -> {
            if (method.getDefinition().getCode().isSharedCodeObject()) {
              return;
            }

            if (graphLens != null) {
              DexMethod rewrittenMethodSignature =
                  graphLens.getNextMethodSignature(method.getReference());
              if (graphLens.hasPrototypeChanges(rewrittenMethodSignature)) {
                assert appView.getKeepInfo(method).isReprocessingAllowed(options, method);
                postMethodProcessorBuilder.add(method, currentGraphLens);
                appView.testing().callSiteOptimizationInfoInspector.accept(method);
                return;
              }
            }

            CallSiteOptimizationInfo callSiteOptimizationInfo =
                method.getOptimizationInfo().getArgumentInfos();
            if (reprocessingCriteriaCollection
                    .getReprocessingCriteria(method)
                    .shouldReprocess(appView, method, callSiteOptimizationInfo)
                && appView.getKeepInfo(method).isReprocessingAllowed(options, method)) {
              postMethodProcessorBuilder.add(method, currentGraphLens);
              appView.testing().callSiteOptimizationInfoInspector.accept(method);
            }
          });
    }
  }

  @SuppressWarnings("HidingField")
  // TODO(b/190154391): This could invalidate the @NeverReprocessMethod testing annotations (non
  //  critical). If @NeverReprocessMethod is used, we would need to scan the application to mark
  //  methods as unoptimizable prior to removing parameters from the application.
  private void enqueueAffectedCallers(
      ArgumentPropagatorGraphLens graphLens,
      PostMethodProcessor.Builder postMethodProcessorBuilder,
      ExecutorService executorService)
      throws ExecutionException {
    GraphLens currentGraphLens = appView.graphLens();
    Collection<List<ProgramMethod>> methodsToReprocess =
        ThreadUtils.processItemsWithResults(
            appView.appInfo().classes(),
            clazz -> {
              List<ProgramMethod> methodsToReprocessInClass = new ArrayList<>();
              clazz.forEachProgramMethodMatching(
                  DexEncodedMethod::hasCode,
                  method -> {
                    if (!postMethodProcessorBuilder.contains(method, currentGraphLens)) {
                      AffectedMethodUseRegistry registry =
                          new AffectedMethodUseRegistry(appView, method, graphLens);
                      if (method.registerCodeReferencesWithResult(registry)) {
                        assert !method.getOptimizationInfo().hasBeenInlinedIntoSingleCallSite();
                        methodsToReprocessInClass.add(method);
                      }
                    }
                  });
              return methodsToReprocessInClass;
            },
            appView.options().getThreadingModule(),
            executorService);
    methodsToReprocess.forEach(
        methodsToReprocessInClass ->
            postMethodProcessorBuilder.addAll(methodsToReprocessInClass, currentGraphLens));
  }

  private void eliminateDeadFieldAndMethodAccesses(ExecutorService executorService)
      throws ExecutionException {
    ThreadUtils.processItems(
        appView.appInfo().classes(),
        clazz ->
            clazz.forEachProgramMethodMatching(
                m -> m.hasCode() && !m.getCode().isSharedCodeObject(),
                this::eliminateDeadFieldAndMethodAccesses),
        appView.options().getThreadingModule(),
        executorService);
  }

  private void eliminateDeadFieldAndMethodAccesses(ProgramMethod method) {
    Code code = method.getDefinition().getCode();
    if (!code.isLirCode()) {
      assert appView.isCfByteCodePassThrough(method);
      return;
    }
    LirCode<Integer> lirCode = code.asLirCode();
    LirCode<Integer> rewrittenLirCode = eliminateDeadFieldAndMethodAccesses(method, lirCode);
    if (ObjectUtils.notIdentical(lirCode, rewrittenLirCode)) {
      method.setCode(rewrittenLirCode, appView);
    }
  }

  private LirCode<Integer> eliminateDeadFieldAndMethodAccesses(
      ProgramMethod method, LirCode<Integer> lirCode) {
    if (ArrayUtils.none(
        lirCode.getConstantPool(), constant -> isDeadFieldOrMethodAccess(constant, method))) {
      return lirCode;
    }
    IRCode irCode = lirCode.buildIR(method, appView);
    AffectedValues affectedValues = new AffectedValues();
    BasicBlockIterator blocks = irCode.listIterator();
    Set<BasicBlock> blocksToRemove = Sets.newIdentityHashSet();
    while (blocks.hasNext()) {
      BasicBlock block = blocks.next();
      if (blocksToRemove.contains(block)) {
        continue;
      }
      InstructionListIterator instructionIterator = block.listIterator();
      while (instructionIterator.hasNext()) {
        Instruction instruction = instructionIterator.next();
        if (instruction.isFieldInstruction()) {
          FieldInstruction fieldInstruction = instruction.asFieldInstruction();
          if (fieldInstruction == null) {
            continue;
          }
          ProgramField resolvedField =
              fieldInstruction.resolveField(appView, method).getProgramField();
          if (resolvedField == null || !isDeadFieldAccess(resolvedField)) {
            continue;
          }
          assert fieldInstruction.isStaticFieldInstruction()
              == resolvedField.getAccessFlags().isStatic();
          instructionIterator.replaceCurrentInstructionWithThrowNull(
              appView, irCode, blocks, blocksToRemove, affectedValues);
        } else if (instruction.isInvokeMethod()) {
          InvokeMethod invoke = instruction.asInvokeMethod();
          ProgramMethod resolvedMethod =
              invoke.resolveMethod(appView, method).getResolvedProgramMethod();
          if (resolvedMethod == null || !isDeadMethodAccess(resolvedMethod)) {
            continue;
          }
          assert invoke.isInvokeStatic() == resolvedMethod.getAccessFlags().isStatic();
          instructionIterator.replaceCurrentInstructionWithThrowNull(
              appView, irCode, blocks, blocksToRemove, affectedValues);
        }
      }
    }
    irCode.removeBlocks(blocksToRemove);
    affectedValues.narrowingWithAssumeRemoval(appView, irCode);
    irCode.removeRedundantBlocks();
    return new IRToLirFinalizer(appView)
        .finalizeCode(irCode, BytecodeMetadataProvider.empty(), Timing.empty());
  }

  private boolean isDeadFieldOrMethodAccess(LirConstant constant, ProgramMethod context) {
    if (constant instanceof DexField) {
      DexField fieldReference = (DexField) constant;
      return isDeadFieldAccess(fieldReference, context);
    } else if (constant instanceof DexMethod) {
      DexMethod methodReference = (DexMethod) constant;
      return isDeadMethodAccess(methodReference);
    }
    return false;
  }

  private boolean isDeadFieldAccess(DexField fieldReference, ProgramMethod context) {
    ProgramField field = appView.appInfo().resolveField(fieldReference, context).getProgramField();
    return field != null && isDeadFieldAccess(field);
  }

  private boolean isDeadFieldAccess(ProgramField field) {
    return field.getOptimizationInfo().getAbstractValue().isBottom();
  }

  private boolean isDeadMethodAccess(DexMethod methodReference) {
    ProgramMethod resolvedMethod =
        appView
            .appInfo()
            .unsafeResolveMethodDueToDexFormat(methodReference)
            .getResolvedProgramMethod();
    return resolvedMethod != null && isDeadMethodAccess(resolvedMethod);
  }

  private boolean isDeadMethodAccess(ProgramMethod method) {
    return prunedMethods.contains(method);
  }

  static class AffectedMethodUseRegistry
      extends DefaultUseRegistryWithResult<Boolean, ProgramMethod> {

    private final AppView<AppInfoWithLiveness> appViewWithLiveness;
    private final ArgumentPropagatorGraphLens graphLens;

    AffectedMethodUseRegistry(
        AppView<AppInfoWithLiveness> appViewWithLiveness,
        ProgramMethod context,
        ArgumentPropagatorGraphLens graphLens) {
      super(appViewWithLiveness, context, false);
      this.appViewWithLiveness = appViewWithLiveness;
      this.graphLens = graphLens;
    }

    private void markAffected() {
      setResult(Boolean.TRUE);
    }

    @Override
    public void registerInvokeDirect(DexMethod method) {
      registerInvokeMethod(method);
    }

    @Override
    public void registerInvokeInterface(DexMethod method) {
      registerInvokeMethod(method);
    }

    @Override
    public void registerInvokeStatic(DexMethod method) {
      registerInvokeMethod(method);
    }

    @Override
    public void registerInvokeSuper(DexMethod method) {
      registerInvokeMethod(method);
    }

    @Override
    public void registerInvokeVirtual(DexMethod method) {
      registerInvokeMethod(method);
    }

    @SuppressWarnings("ReferenceEquality")
    private void registerInvokeMethod(DexMethod method) {
      SingleResolutionResult<?> resolutionResult =
          appViewWithLiveness
              .appInfo()
              .unsafeResolveMethodDueToDexFormatLegacy(method)
              .asSingleResolution();
      if (resolutionResult == null || !resolutionResult.getResolvedHolder().isProgramClass()) {
        return;
      }

      ProgramMethod resolvedMethod = resolutionResult.getResolvedProgramMethod();
      DexMethod rewrittenMethodReference =
          graphLens.getNextMethodSignature(resolvedMethod.getReference());
      if (rewrittenMethodReference != resolvedMethod.getReference()
          || graphLens.hasPrototypeChanges(rewrittenMethodReference)) {
        markAffected();
      }
    }

    @Override
    public void registerCallSite(DexCallSite callSite) {
      LambdaDescriptor descriptor =
          LambdaDescriptor.tryInfer(callSite, appView, appViewWithLiveness.appInfo(), getContext());
      if (descriptor != null) {
        registerLambdaCallSite(descriptor);
      } else if (RecordRewriterHelper.isInvokeDynamicOnRecord(callSite, appView, getContext())) {
        registerRecordCallSite(callSite);
      }
    }

    private void registerLambdaCallSite(LambdaDescriptor descriptor) {
      assert !descriptor.interfaces.isEmpty();
      ProgramMethod resolvedMainMethod =
          appViewWithLiveness
              .appInfo()
              .resolveMethodOnInterface(descriptor.interfaces.get(0), descriptor.getMainMethod())
              .getResolvedProgramMethod();
      if (resolvedMainMethod == null) {
        return;
      }
      DexMethod rewrittenMainMethod =
          graphLens.getNextMethodSignature(resolvedMainMethod.getReference());
      if (rewrittenMainMethod.isNotIdenticalTo(resolvedMainMethod.getReference())) {
        markAffected();
      } else {
        assert !graphLens.hasPrototypeChanges(rewrittenMainMethod);
      }
    }

    private void registerRecordCallSite(DexCallSite callSite) {
      for (DexValue bootstrapArg : callSite.getBootstrapArgs()) {
        if (bootstrapArg.isDexValueMethodHandle()) {
          DexMethodHandle handle = bootstrapArg.asDexValueMethodHandle().getValue();
          assert handle.isFieldHandle();
          if (registerFieldAccess(handle.asField())) {
            break;
          }
        }
      }
    }

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

    private boolean registerFieldAccess(DexField field) {
      FieldResolutionResult resolutionResult = appViewWithLiveness.appInfo().resolveField(field);
      if (resolutionResult.getSingleProgramField() == null) {
        return false;
      }

      ProgramField resolvedField = resolutionResult.getSingleProgramField();
      DexField rewrittenFieldReference =
          graphLens.getNextFieldSignature(resolvedField.getReference());
      if (rewrittenFieldReference.isNotIdenticalTo(resolvedField.getReference())) {
        markAffected();
        return true;
      }
      return false;
    }
  }
}
