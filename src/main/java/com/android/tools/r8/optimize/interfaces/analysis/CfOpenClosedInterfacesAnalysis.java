// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.interfaces.analysis;

import com.android.tools.r8.cf.code.CfAssignability;
import com.android.tools.r8.cf.code.CfFrameVerifier;
import com.android.tools.r8.cf.code.CfFrameVerifier.StackMapStatus;
import com.android.tools.r8.cf.code.CfFrameVerifierEventConsumer;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfSubtypingAssignability;
import com.android.tools.r8.cf.code.frame.FrameType;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.CfCodeDiagnostics;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndMember;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.analysis.EnqueuerAnalysisCollection;
import com.android.tools.r8.graph.analysis.FinishedEnqueuerAnalysis;
import com.android.tools.r8.graph.analysis.NewlyLiveCodeEnqueuerAnalysis;
import com.android.tools.r8.ir.analysis.framework.intraprocedural.AbstractTransferFunction;
import com.android.tools.r8.ir.analysis.framework.intraprocedural.DataflowAnalysisResult;
import com.android.tools.r8.ir.analysis.framework.intraprocedural.DataflowAnalysisResult.FailedDataflowAnalysisResult;
import com.android.tools.r8.ir.analysis.framework.intraprocedural.TransferFunctionResult;
import com.android.tools.r8.ir.analysis.framework.intraprocedural.cf.CfBlock;
import com.android.tools.r8.ir.analysis.framework.intraprocedural.cf.CfControlFlowGraph;
import com.android.tools.r8.ir.analysis.framework.intraprocedural.cf.CfIntraproceduralDataflowAnalysis;
import com.android.tools.r8.optimize.interfaces.collection.NonEmptyOpenClosedInterfacesCollection;
import com.android.tools.r8.shaking.DefaultEnqueuerUseRegistry;
import com.android.tools.r8.shaking.Enqueuer;
import com.android.tools.r8.shaking.EnqueuerWorklist;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.InternalOptions.OpenClosedInterfacesOptions;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.SetUtils;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.UnverifiableCfCodeDiagnostic;
import com.android.tools.r8.utils.WorkList;
import com.android.tools.r8.utils.collections.ProgramMethodMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

public class CfOpenClosedInterfacesAnalysis
    implements NewlyLiveCodeEnqueuerAnalysis, FinishedEnqueuerAnalysis {

  private static final int BATCH_SIZE = 100;

  private final AppView<AppInfoWithClassHierarchy> appView;
  private final CfAssignability assignability;
  private final Enqueuer enqueuer;
  private final InternalOptions options;

  private final Set<DexClass> openInterfaces = SetUtils.newConcurrentHashSet();
  private List<ProgramMethod> pendingMethods = new ArrayList<>(BATCH_SIZE);
  private final ProgramMethodMap<UnverifiableCfCodeDiagnostic> unverifiableCodeDiagnostics =
      ProgramMethodMap.createConcurrent();

  public CfOpenClosedInterfacesAnalysis(
      AppView<AppInfoWithClassHierarchy> appView, Enqueuer enqueuer) {
    this.appView = appView;
    this.assignability = new CfSubtypingAssignability(appView);
    this.enqueuer = enqueuer;
    this.options = appView.options();
  }

  public static void register(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      Enqueuer enqueuer,
      EnqueuerAnalysisCollection.Builder builder) {
    if (enqueuer.getMode().isInitialTreeShaking()) {
      CfOpenClosedInterfacesAnalysis analysis =
          new CfOpenClosedInterfacesAnalysis(appView.withClassHierarchy(), enqueuer);
      builder.addNewlyLiveCodeAnalysis(analysis).addFinishedAnalysis(analysis);
    }
  }

  @Override
  public void processNewlyLiveCode(
      ProgramMethod method, DefaultEnqueuerUseRegistry registry, EnqueuerWorklist worklist) {
    pendingMethods.add(method);
    // Once we have accumulated <BATCH_SIZE> methods, analyze all of them concurrently.
    if (pendingMethods.size() == BATCH_SIZE) {
      List<ProgramMethod> methods = pendingMethods;
      enqueuer
          .getTaskCollection()
          .submitEnqueuerIndependentTask(
              () -> {
                methods.forEach(this::processMethod);
                return null;
              });
      pendingMethods = new ArrayList<>(BATCH_SIZE);
    }
  }

  @Override
  public void done(Enqueuer enqueuer, ExecutorService executorService) throws ExecutionException {
    processPendingMethods(executorService);
    setClosedInterfaces();
    reportUnverifiableCodeDiagnostics();
  }

  private void processPendingMethods(ExecutorService executorService) throws ExecutionException {
    ThreadUtils.processItems(
        pendingMethods, this::processMethod, options.getThreadingModule(), executorService);
    pendingMethods.clear();
  }

  private void processMethod(ProgramMethod method) {
    Code code = method.getDefinition().getCode();
    if (!code.isCfCode()) {
      assert code.isDexCode();
      return;
    }
    CfCode cfCode = code.asCfCode();
    CfAnalysisConfig config = createConfig(method, cfCode);
    CfOpenClosedInterfacesAnalysisHelper helper =
        new CfOpenClosedInterfacesAnalysisHelper(appView, method, unverifiableCodeDiagnostics);
    StackMapStatus stackMapStatus = runLinearScan(method, cfCode, config, helper);
    if (stackMapStatus.isNotPresent()) {
      runFixpoint(method, cfCode, config, helper);
      cfCode.setStackMapStatus(stackMapStatus);
    } else if (stackMapStatus.isValid()) {
      cfCode.setStackMapStatus(stackMapStatus);
    }
    openInterfaces.addAll(helper.getOpenInterfaces());
  }

  private CfAnalysisConfig createConfig(ProgramMethod method, CfCode code) {
    return new CfAnalysisConfig() {

      @Override
      public CfAssignability getAssignability() {
        return assignability;
      }

      @Override
      public DexMethod getCurrentContext() {
        return method.getReference();
      }

      @Override
      public int getMaxLocals() {
        return code.getMaxLocals();
      }

      @Override
      public int getMaxStack() {
        return code.getMaxStack();
      }

      @Override
      @SuppressWarnings("ReferenceEquality")
      public boolean isImmediateSuperClassOfCurrentContext(DexType type) {
        return type == method.getHolder().getSuperType();
      }

      @Override
      public boolean isStrengthenFramesEnabled() {
        return true;
      }
    };
  }

  private StackMapStatus runLinearScan(
      ProgramMethod method,
      CfCode code,
      CfAnalysisConfig config,
      CfOpenClosedInterfacesAnalysisHelper helper) {
    CfFrameVerifierEventConsumer eventConsumer =
        new CfFrameVerifierEventConsumer() {

          @Override
          public void acceptError(CfCodeDiagnostics diagnostics) {
            helper.registerUnverifiableCodeWithFrames(diagnostics);
          }

          @Override
          public void acceptInstructionState(CfInstruction instruction, CfFrameState state) {
            helper.processInstruction(instruction, state);
          }
        };
    CfFrameVerifier verifier =
        CfFrameVerifier.builder(appView, code, method)
            .setConfig(config)
            .setEventConsumer(eventConsumer)
            .build();
    StackMapStatus stackMapStatus = verifier.run();
    assert stackMapStatus.isValid() || helper.getOpenInterfaces().isEmpty();
    return stackMapStatus;
  }

  private void runFixpoint(
      ProgramMethod method,
      CfCode code,
      CfAnalysisConfig config,
      CfOpenClosedInterfacesAnalysisHelper helper) {
    CfControlFlowGraph cfg = CfControlFlowGraph.create(code, options);
    TransferFunction transfer = new TransferFunction(config, method);
    CfIntraproceduralDataflowAnalysis<CfFrameState> analysis =
        new CfIntraproceduralDataflowAnalysis<>(appView, CfFrameState.bottom(), cfg, transfer);
    DataflowAnalysisResult result = analysis.run(cfg.getEntryBlock());
    if (result.isFailedAnalysisResult()) {
      FailedCfAnalysisResult failedResult = (FailedCfAnalysisResult) result;
      int index =
          failedResult.instruction == null
              ? 0
              : code.getInstructions().indexOf(failedResult.instruction);
      helper.registerUnverifiableCode(method, index, failedResult.errorState);
      return;
    }
    assert result.isSuccessfulAnalysisResult();
    for (CfBlock block : cfg.getBlocks()) {
      if (analysis.isIntermediateBlock(block)) {
        continue;
      }
      CfFrameState state = analysis.computeBlockEntryState(block);
      if (state.isError()) {
        // Any error should terminate with a "FailedAnalysisResult" above.
        assert false;
        helper.registerUnverifiableCode(method, 0, state.asError());
        return;
      }
      do {
        for (int instructionIndex = block.getFirstInstructionIndex();
            instructionIndex <= block.getLastInstructionIndex();
            instructionIndex++) {
          CfInstruction instruction = code.getInstruction(instructionIndex);
          helper.processInstruction(instruction, state);
          state = transfer.apply(instruction, state).asAbstractState();
          if (state.isError()) {
            // Any error should terminate with a "FailedAnalysisResult" above.
            assert false;
            helper.registerUnverifiableCode(method, instructionIndex, state.asError());
            return;
          }
        }
        if (analysis.isBlockWithIntermediateSuccessorBlock(block)) {
          block = cfg.getUniqueSuccessor(block);
        } else {
          block = null;
        }
      } while (block != null);
    }
  }

  private void setClosedInterfaces() {
    // If open interfaces are not allowed and there are one or more suppressions, we should find at
    // least one open interface.
    OpenClosedInterfacesOptions openClosedInterfacesOptions =
        options.getOpenClosedInterfacesOptions();
    assert openClosedInterfacesOptions.isOpenInterfacesAllowed()
            || !openClosedInterfacesOptions.hasSuppressions()
            || !openInterfaces.isEmpty()
        : "Expected to find at least one open interface";

    includeParentOpenInterfaces();

    appView.setOpenClosedInterfacesCollection(
        new NonEmptyOpenClosedInterfacesCollection(
            openInterfaces.stream()
                .map(DexClass::getType)
                .collect(
                    Collectors.toCollection(
                        () -> SetUtils.newIdentityHashSet(openInterfaces.size())))));
  }

  private void includeParentOpenInterfaces() {
    // This includes all parent interfaces of each open interface in the set of open interfaces,
    // by using the open interfaces as the seen set.
    WorkList<DexClass> worklist = WorkList.newWorkList(openInterfaces);
    worklist.addAllIgnoringSeenSet(openInterfaces);
    while (worklist.hasNext()) {
      DexClass openInterface = worklist.next();
      for (DexType indirectOpenInterfaceType : openInterface.getInterfaces()) {
        DexClass indirectOpenInterfaceDefinition = appView.definitionFor(indirectOpenInterfaceType);
        if (indirectOpenInterfaceDefinition != null) {
          worklist.addIfNotSeen(indirectOpenInterfaceDefinition);
        }
      }
    }
  }

  private void reportUnverifiableCodeDiagnostics() {
    Reporter reporter = appView.reporter();
    List<ProgramMethod> methods = new ArrayList<>(unverifiableCodeDiagnostics.size());
    unverifiableCodeDiagnostics.forEach((method, diagnostic) -> methods.add(method));
    methods.sort(Comparator.comparing(DexClassAndMember::getReference));
    methods.forEach(method -> reporter.warning(unverifiableCodeDiagnostics.get(method)));
  }

  private static class FailedCfAnalysisResult extends FailedDataflowAnalysisResult {

    private final CfInstruction instruction;
    private final ErroneousCfFrameState errorState;

    public FailedCfAnalysisResult(CfInstruction instruction, ErroneousCfFrameState errorState) {
      this.instruction = instruction;
      this.errorState = errorState;
    }
  }

  private class TransferFunction
      implements AbstractTransferFunction<CfBlock, CfInstruction, CfFrameState> {

    private final CfAnalysisConfig config;
    private final ProgramMethod context;

    TransferFunction(CfAnalysisConfig config, ProgramMethod context) {
      this.config = config;
      this.context = context;
    }

    @Override
    public FailedDataflowAnalysisResult createFailedAnalysisResult(
        CfInstruction instruction, TransferFunctionResult<CfFrameState> transferResult) {
      ErroneousCfFrameState errorState = (ErroneousCfFrameState) transferResult;
      return new FailedCfAnalysisResult(instruction, errorState);
    }

    @Override
    public TransferFunctionResult<CfFrameState> apply(
        CfInstruction instruction, CfFrameState state) {
      return instruction.evaluate(state, appView, config);
    }

    @Override
    public CfFrameState computeInitialState(CfBlock entryBlock, CfFrameState bottom) {
      CfFrameState initialState = new ConcreteCfFrameState();
      int localIndex = 0;
      if (context.getDefinition().isInstance()) {
        if (context.getDefinition().isInstanceInitializer()) {
          initialState = initialState.storeLocal(localIndex, FrameType.uninitializedThis(), config);
        } else {
          initialState =
              initialState.storeLocal(
                  localIndex,
                  FrameType.initializedNonNullReference(context.getHolderType()),
                  config);
        }
        localIndex++;
      }
      for (DexType parameter : context.getParameters()) {
        initialState =
            initialState.storeLocal(localIndex, FrameType.initialized(parameter), config);
        localIndex += parameter.getRequiredRegisters();
      }
      return initialState;
    }

    @Override
    public CfFrameState computeBlockEntryState(
        CfBlock block, CfBlock predecessor, CfFrameState predecessorExitState) {
      return predecessorExitState;
    }

    @Override
    public CfFrameState computeExceptionalBlockEntryState(
        CfBlock block,
        DexType guard,
        CfBlock throwBlock,
        CfInstruction throwInstruction,
        CfFrameState throwState) {
      return throwState.pushException(config, guard);
    }
  }
}
