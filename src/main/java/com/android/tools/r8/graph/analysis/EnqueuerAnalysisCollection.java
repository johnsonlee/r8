// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph.analysis;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.ClasspathOrLibraryClass;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.FieldResolutionResult;
import com.android.tools.r8.graph.FieldResolutionResult.SingleFieldResolutionResult;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.graph.ProgramDefinition;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.shaking.DefaultEnqueuerUseRegistry;
import com.android.tools.r8.shaking.Enqueuer;
import com.android.tools.r8.shaking.Enqueuer.FieldAccessKind;
import com.android.tools.r8.shaking.EnqueuerWorklist;
import com.android.tools.r8.shaking.KeepReason;
import com.android.tools.r8.utils.ArrayUtils;
import com.android.tools.r8.utils.timing.Timing;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public final class EnqueuerAnalysisCollection {

  // Trace events.
  private final TraceCheckCastEnqueuerAnalysis[] checkCastAnalyses;
  private final TraceConstClassEnqueuerAnalysis[] constClassAnalyses;
  private final TraceExceptionGuardEnqueuerAnalysis[] exceptionGuardAnalyses;
  private final TraceFieldAccessEnqueuerAnalysis[] fieldAccessAnalyses;
  private final TraceInstanceOfEnqueuerAnalysis[] instanceOfAnalyses;
  private final TraceInvokeEnqueuerAnalysis[] invokeAnalyses;
  private final TraceNewInstanceEnqueuerAnalysis[] newInstanceAnalyses;

  // IR analysis.
  private final IrBasedEnqueuerAnalysis[] irBasedEnqueuerAnalyses;

  // Reachability events.
  private final NewlyFailedMethodResolutionEnqueuerAnalysis[] newlyFailedMethodResolutionAnalyses;
  private final NewlyLiveClassEnqueuerAnalysis[] newlyLiveClassAnalyses;
  private final NewlyLiveCodeEnqueuerAnalysis[] newlyLiveCodeAnalyses;
  private final NewlyLiveFieldEnqueuerAnalysis[] newlyLiveFieldAnalyses;
  private final NewlyLiveMethodEnqueuerAnalysis[] newlyLiveMethodAnalyses;
  private final NewlyInstantiatedClassEnqueuerAnalysis[] newlyInstantiatedClassAnalyses;
  private final NewlyLiveNonProgramClassEnqueuerAnalysis[] newlyLiveNonProgramClassAnalyses;
  private final NewlyReachableFieldEnqueuerAnalysis[] newlyReachableFieldAnalyses;
  private final NewlyReferencedFieldEnqueuerAnalysis[] newlyReferencedFieldAnalyses;
  private final NewlyTargetedMethodEnqueuerAnalysis[] newlyTargetedMethodAnalyses;
  private final MarkFieldAsKeptEnqueuerAnalysis[] markFieldAsKeptEnqueuerAnalyses;

  // Tear down events.
  private final FinishedEnqueuerAnalysis[] finishedAnalyses;
  private final FixpointEnqueuerAnalysis[] fixpointAnalyses;

  private EnqueuerAnalysisCollection(
      // Trace events.
      TraceCheckCastEnqueuerAnalysis[] checkCastAnalyses,
      TraceConstClassEnqueuerAnalysis[] constClassAnalyses,
      TraceExceptionGuardEnqueuerAnalysis[] exceptionGuardAnalyses,
      TraceFieldAccessEnqueuerAnalysis[] fieldAccessAnalyses,
      TraceInstanceOfEnqueuerAnalysis[] instanceOfAnalyses,
      TraceInvokeEnqueuerAnalysis[] invokeAnalyses,
      TraceNewInstanceEnqueuerAnalysis[] newInstanceAnalyses,
      // IR analysis.
      IrBasedEnqueuerAnalysis[] irBasedEnqueuerAnalyses,
      // Reachability events.
      NewlyFailedMethodResolutionEnqueuerAnalysis[] newlyFailedMethodResolutionAnalyses,
      NewlyLiveClassEnqueuerAnalysis[] newlyLiveClassAnalyses,
      NewlyLiveCodeEnqueuerAnalysis[] newlyLiveCodeAnalyses,
      NewlyLiveFieldEnqueuerAnalysis[] newlyLiveFieldAnalyses,
      NewlyLiveMethodEnqueuerAnalysis[] newlyLiveMethodAnalyses,
      NewlyInstantiatedClassEnqueuerAnalysis[] newlyInstantiatedClassAnalyses,
      NewlyLiveNonProgramClassEnqueuerAnalysis[] newlyLiveNonProgramClassAnalyses,
      NewlyReachableFieldEnqueuerAnalysis[] newlyReachableFieldAnalyses,
      NewlyReferencedFieldEnqueuerAnalysis[] newlyReferencedFieldAnalyses,
      NewlyTargetedMethodEnqueuerAnalysis[] newlyTargetedMethodAnalyses,
      MarkFieldAsKeptEnqueuerAnalysis[] markFieldAsKeptEnqueuerAnalyses,
      // Tear down events.
      FinishedEnqueuerAnalysis[] finishedAnalyses,
      FixpointEnqueuerAnalysis[] fixpointAnalyses) {
    // Trace events.
    this.checkCastAnalyses = checkCastAnalyses;
    this.constClassAnalyses = constClassAnalyses;
    this.exceptionGuardAnalyses = exceptionGuardAnalyses;
    this.fieldAccessAnalyses = fieldAccessAnalyses;
    this.instanceOfAnalyses = instanceOfAnalyses;
    this.invokeAnalyses = invokeAnalyses;
    this.newInstanceAnalyses = newInstanceAnalyses;
    // IR Analysis.
    this.irBasedEnqueuerAnalyses = irBasedEnqueuerAnalyses;
    // Reachability events.
    this.newlyFailedMethodResolutionAnalyses = newlyFailedMethodResolutionAnalyses;
    this.newlyLiveClassAnalyses = newlyLiveClassAnalyses;
    this.newlyLiveCodeAnalyses = newlyLiveCodeAnalyses;
    this.newlyLiveFieldAnalyses = newlyLiveFieldAnalyses;
    this.newlyLiveMethodAnalyses = newlyLiveMethodAnalyses;
    this.newlyInstantiatedClassAnalyses = newlyInstantiatedClassAnalyses;
    this.newlyLiveNonProgramClassAnalyses = newlyLiveNonProgramClassAnalyses;
    this.newlyReachableFieldAnalyses = newlyReachableFieldAnalyses;
    this.newlyReferencedFieldAnalyses = newlyReferencedFieldAnalyses;
    this.newlyTargetedMethodAnalyses = newlyTargetedMethodAnalyses;
    this.markFieldAsKeptEnqueuerAnalyses = markFieldAsKeptEnqueuerAnalyses;
    // Tear down events.
    this.finishedAnalyses = finishedAnalyses;
    this.fixpointAnalyses = fixpointAnalyses;
  }

  public static Builder builder() {
    return new Builder();
  }

  public boolean isEmpty() {
    return ArrayUtils.isEmpty(checkCastAnalyses)
        && ArrayUtils.isEmpty(constClassAnalyses)
        && ArrayUtils.isEmpty(exceptionGuardAnalyses)
        && ArrayUtils.isEmpty(fieldAccessAnalyses)
        && ArrayUtils.isEmpty(instanceOfAnalyses)
        && ArrayUtils.isEmpty(invokeAnalyses)
        && ArrayUtils.isEmpty(newInstanceAnalyses)
        && ArrayUtils.isEmpty(newlyFailedMethodResolutionAnalyses)
        && ArrayUtils.isEmpty(newlyLiveClassAnalyses)
        && ArrayUtils.isEmpty(newlyLiveCodeAnalyses)
        && ArrayUtils.isEmpty(newlyLiveFieldAnalyses)
        && ArrayUtils.isEmpty(newlyLiveMethodAnalyses)
        && ArrayUtils.isEmpty(newlyInstantiatedClassAnalyses)
        && ArrayUtils.isEmpty(newlyLiveNonProgramClassAnalyses)
        && ArrayUtils.isEmpty(newlyReachableFieldAnalyses)
        && ArrayUtils.isEmpty(newlyReferencedFieldAnalyses)
        && ArrayUtils.isEmpty(newlyTargetedMethodAnalyses)
        && ArrayUtils.isEmpty(finishedAnalyses)
        && ArrayUtils.isEmpty(fixpointAnalyses);
  }

  // Trace events.

  public void traceCheckCast(DexType type, DexClass clazz, ProgramMethod context) {
    for (TraceCheckCastEnqueuerAnalysis analysis : checkCastAnalyses) {
      analysis.traceCheckCast(type, clazz, context);
    }
  }

  public void traceSafeCheckCast(DexType type, DexClass clazz, ProgramMethod context) {
    for (TraceCheckCastEnqueuerAnalysis analysis : checkCastAnalyses) {
      analysis.traceSafeCheckCast(type, clazz, context);
    }
  }

  public void traceConstClass(DexType type, DexClass clazz, ProgramMethod context) {
    for (TraceConstClassEnqueuerAnalysis analysis : constClassAnalyses) {
      analysis.traceConstClass(type, clazz, context);
    }
  }

  public void traceExceptionGuard(DexType guard, DexClass clazz, ProgramMethod context) {
    for (TraceExceptionGuardEnqueuerAnalysis analysis : exceptionGuardAnalyses) {
      analysis.traceExceptionGuard(guard, clazz, context);
    }
  }

  public void traceFieldAccess(
      DexField field,
      SingleFieldResolutionResult<?> resolutionResult,
      ProgramMethod context,
      EnqueuerWorklist worklist,
      FieldAccessKind accessKind) {
    switch (accessKind) {
      case INSTANCE_READ:
        traceInstanceFieldRead(field, resolutionResult, context, worklist);
        break;
      case INSTANCE_WRITE:
        traceInstanceFieldWrite(field, resolutionResult, context, worklist);
        break;
      case STATIC_READ:
        traceStaticFieldRead(field, resolutionResult, context, worklist);
        break;
      case STATIC_WRITE:
        traceStaticFieldWrite(field, resolutionResult, context, worklist);
        break;
      default:
        throw new Unreachable();
    }
  }

  public void traceInstanceFieldRead(
      DexField field,
      FieldResolutionResult resolutionResult,
      ProgramMethod context,
      EnqueuerWorklist worklist) {
    for (TraceFieldAccessEnqueuerAnalysis analysis : fieldAccessAnalyses) {
      analysis.traceInstanceFieldRead(field, resolutionResult, context, worklist);
    }
  }

  public void traceInstanceFieldWrite(
      DexField field,
      SingleFieldResolutionResult<?> resolutionResult,
      ProgramMethod context,
      EnqueuerWorklist worklist) {
    for (TraceFieldAccessEnqueuerAnalysis analysis : fieldAccessAnalyses) {
      analysis.traceInstanceFieldWrite(field, resolutionResult, context, worklist);
    }
  }

  public void traceStaticFieldRead(
      DexField field,
      SingleFieldResolutionResult<?> resolutionResult,
      ProgramMethod context,
      EnqueuerWorklist worklist) {
    for (TraceFieldAccessEnqueuerAnalysis analysis : fieldAccessAnalyses) {
      analysis.traceStaticFieldRead(field, resolutionResult, context, worklist);
    }
  }

  public void traceStaticFieldWrite(
      DexField field,
      SingleFieldResolutionResult<?> resolutionResult,
      ProgramMethod context,
      EnqueuerWorklist worklist) {
    for (TraceFieldAccessEnqueuerAnalysis analysis : fieldAccessAnalyses) {
      analysis.traceStaticFieldWrite(field, resolutionResult, context, worklist);
    }
  }

  public void traceInstanceOf(DexType type, DexClass clazz, ProgramMethod context) {
    for (TraceInstanceOfEnqueuerAnalysis analysis : instanceOfAnalyses) {
      analysis.traceInstanceOf(type, clazz, context);
    }
  }

  public void traceInvokeStatic(
      DexMethod invokedMethod, MethodResolutionResult resolutionResult, ProgramMethod context) {
    for (TraceInvokeEnqueuerAnalysis analysis : invokeAnalyses) {
      analysis.traceInvokeStatic(invokedMethod, resolutionResult, context);
    }
  }

  public void traceInvokeDirect(
      DexMethod invokedMethod, MethodResolutionResult resolutionResult, ProgramMethod context) {
    for (TraceInvokeEnqueuerAnalysis analysis : invokeAnalyses) {
      analysis.traceInvokeDirect(invokedMethod, resolutionResult, context);
    }
  }

  public void traceInvokeInterface(
      DexMethod invokedMethod, MethodResolutionResult resolutionResult, ProgramMethod context) {
    for (TraceInvokeEnqueuerAnalysis analysis : invokeAnalyses) {
      analysis.traceInvokeInterface(invokedMethod, resolutionResult, context);
    }
  }

  public void traceInvokeSuper(
      DexMethod invokedMethod, MethodResolutionResult resolutionResult, ProgramMethod context) {
    for (TraceInvokeEnqueuerAnalysis analysis : invokeAnalyses) {
      analysis.traceInvokeSuper(invokedMethod, resolutionResult, context);
    }
  }

  public void traceInvokeVirtual(
      DexMethod invokedMethod, MethodResolutionResult resolutionResult, ProgramMethod context) {
    for (TraceInvokeEnqueuerAnalysis analysis : invokeAnalyses) {
      analysis.traceInvokeVirtual(invokedMethod, resolutionResult, context);
    }
  }

  public void traceNewInstance(DexType type, DexClass clazz, ProgramMethod context) {
    for (TraceNewInstanceEnqueuerAnalysis analysis : newInstanceAnalyses) {
      analysis.traceNewInstance(type, clazz, context);
    }
  }

  // Reachability events.

  public void processNewlyFailedMethodResolutionTarget(
      DexEncodedMethod method, EnqueuerWorklist worklist) {
    for (NewlyFailedMethodResolutionEnqueuerAnalysis analysis :
        newlyFailedMethodResolutionAnalyses) {
      analysis.processNewlyFailedMethodResolutionTarget(method, worklist);
    }
  }

  public void processNewlyLiveClass(DexProgramClass clazz, EnqueuerWorklist worklist) {
    for (NewlyLiveClassEnqueuerAnalysis analysis : newlyLiveClassAnalyses) {
      analysis.processNewlyLiveClass(clazz, worklist);
    }
  }

  public void processNewlyLiveCode(
      ProgramMethod method, DefaultEnqueuerUseRegistry registry, EnqueuerWorklist worklist) {
    for (NewlyLiveCodeEnqueuerAnalysis analysis : newlyLiveCodeAnalyses) {
      analysis.processNewlyLiveCode(method, registry, worklist);
    }
  }

  public void processNewlyLiveField(
      ProgramField field, ProgramDefinition context, EnqueuerWorklist worklist) {
    for (NewlyLiveFieldEnqueuerAnalysis analysis : newlyLiveFieldAnalyses) {
      analysis.processNewlyLiveField(field, context, worklist);
    }
  }

  public void processNewlyLiveMethod(
      ProgramMethod method,
      ProgramDefinition context,
      Enqueuer enqueuer,
      EnqueuerWorklist worklist) {
    for (NewlyLiveMethodEnqueuerAnalysis analysis : newlyLiveMethodAnalyses) {
      analysis.processNewlyLiveMethod(method, context, enqueuer, worklist);
    }
  }

  public void processNewlyInstantiatedClass(
      DexProgramClass clazz, ProgramMethod context, Timing timing, EnqueuerWorklist worklist) {
    timing.begin("Notify processNewlyInstantiatedClass");
    for (NewlyInstantiatedClassEnqueuerAnalysis analysis : newlyInstantiatedClassAnalyses) {
      analysis.processNewlyInstantiatedClass(clazz, context, worklist);
    }
    timing.end();
  }

  public void processNewlyLiveNonProgramType(ClasspathOrLibraryClass clazz) {
    for (NewlyLiveNonProgramClassEnqueuerAnalysis analysis : newlyLiveNonProgramClassAnalyses) {
      analysis.processNewlyLiveNonProgramType(clazz);
    }
  }

  public void processNewlyReachableField(ProgramField field, EnqueuerWorklist worklist) {
    for (NewlyReachableFieldEnqueuerAnalysis analysis : newlyReachableFieldAnalyses) {
      analysis.processNewlyReachableField(field, worklist);
    }
  }

  public void processNewlyReferencedField(ProgramField field) {
    for (NewlyReferencedFieldEnqueuerAnalysis analysis : newlyReferencedFieldAnalyses) {
      analysis.processNewlyReferencedField(field);
    }
  }

  public void processNewlyTargetedMethod(ProgramMethod method, EnqueuerWorklist worklist) {
    for (NewlyTargetedMethodEnqueuerAnalysis analysis : newlyTargetedMethodAnalyses) {
      analysis.processNewlyTargetedMethod(method, worklist);
    }
  }

  public void processMarkFieldKept(
      ProgramField field, KeepReason reason, EnqueuerWorklist worklist) {
    for (MarkFieldAsKeptEnqueuerAnalysis analysis : markFieldAsKeptEnqueuerAnalyses) {
      analysis.processNewlyKeptField(field, reason, worklist);
    }
  }

  // Tear down events.

  public void done(Enqueuer enqueuer) {
    for (FinishedEnqueuerAnalysis analysis : finishedAnalyses) {
      analysis.done(enqueuer);
    }
  }

  public void notifyFixpoint(
      Enqueuer enqueuer, EnqueuerWorklist worklist, ExecutorService executorService, Timing timing)
      throws ExecutionException {
    for (FixpointEnqueuerAnalysis analysis : fixpointAnalyses) {
      analysis.notifyFixpoint(enqueuer, worklist, executorService, timing);
      if (worklist.hasNext()) {
        break;
      }
    }
  }

  public boolean handleReflectiveInvoke(ProgramMethod method, InvokeMethod invoke) {
    for (IrBasedEnqueuerAnalysis analysis : irBasedEnqueuerAnalyses) {
      if (analysis.handleReflectiveInvoke(method, invoke)) {
        return true;
      }
    }
    return false;
  }

  public static class Builder {

    // Trace events.
    private final List<TraceCheckCastEnqueuerAnalysis> checkCastAnalyses = new ArrayList<>();
    private final List<TraceConstClassEnqueuerAnalysis> constClassAnalyses = new ArrayList<>();
    private final List<TraceExceptionGuardEnqueuerAnalysis> exceptionGuardAnalyses =
        new ArrayList<>();
    private final List<TraceFieldAccessEnqueuerAnalysis> fieldAccessAnalyses = new ArrayList<>();
    private final List<TraceInstanceOfEnqueuerAnalysis> instanceOfAnalyses = new ArrayList<>();
    private final List<TraceInvokeEnqueuerAnalysis> invokeAnalyses = new ArrayList<>();
    private final List<TraceNewInstanceEnqueuerAnalysis> newInstanceAnalyses = new ArrayList<>();

    // IR analysis.
    private final List<IrBasedEnqueuerAnalysis> irBasedEnqueuerAnalyses = new ArrayList<>();

    // Reachability events.
    private final List<NewlyFailedMethodResolutionEnqueuerAnalysis>
        newlyFailedMethodResolutionAnalyses = new ArrayList<>();
    private final List<NewlyLiveClassEnqueuerAnalysis> newlyLiveClassAnalyses = new ArrayList<>();
    private final List<NewlyLiveCodeEnqueuerAnalysis> newlyLiveCodeAnalyses = new ArrayList<>();
    private final List<NewlyLiveFieldEnqueuerAnalysis> newlyLiveFieldAnalyses = new ArrayList<>();
    private final List<NewlyLiveMethodEnqueuerAnalysis> newlyLiveMethodAnalyses = new ArrayList<>();
    private final List<NewlyInstantiatedClassEnqueuerAnalysis> newlyInstantiatedClassAnalyses =
        new ArrayList<>();
    private final List<NewlyLiveNonProgramClassEnqueuerAnalysis> newlyLiveNonProgramClassAnalyses =
        new ArrayList<>();
    private final List<NewlyReachableFieldEnqueuerAnalysis> newlyReachableFieldAnalyses =
        new ArrayList<>();
    private final List<NewlyReferencedFieldEnqueuerAnalysis> newlyReferencedFieldAnalyses =
        new ArrayList<>();
    private final List<NewlyTargetedMethodEnqueuerAnalysis> newlyTargetedMethodAnalyses =
        new ArrayList<>();
    private final List<MarkFieldAsKeptEnqueuerAnalysis> markFieldAsKeptAnalyses = new ArrayList<>();

    // Tear down events.
    private final List<FinishedEnqueuerAnalysis> finishedAnalyses = new ArrayList<>();
    private final List<FixpointEnqueuerAnalysis> fixpointAnalyses = new ArrayList<>();

    private Builder() {}

    // Trace events.

    public Builder addTraceCheckCastAnalysis(TraceCheckCastEnqueuerAnalysis analysis) {
      checkCastAnalyses.add(analysis);
      return this;
    }

    public Builder addTraceConstClassAnalysis(TraceConstClassEnqueuerAnalysis analysis) {
      constClassAnalyses.add(analysis);
      return this;
    }

    public Builder addTraceExceptionGuardAnalysis(TraceExceptionGuardEnqueuerAnalysis analysis) {
      exceptionGuardAnalyses.add(analysis);
      return this;
    }

    public Builder addTraceFieldAccessAnalysis(TraceFieldAccessEnqueuerAnalysis analysis) {
      fieldAccessAnalyses.add(analysis);
      return this;
    }

    public Builder addTraceInstanceOfAnalysis(TraceInstanceOfEnqueuerAnalysis analysis) {
      instanceOfAnalyses.add(analysis);
      return this;
    }

    public Builder addTraceInvokeAnalysis(TraceInvokeEnqueuerAnalysis analysis) {
      invokeAnalyses.add(analysis);
      return this;
    }

    public Builder addTraceNewInstanceAnalysis(TraceNewInstanceEnqueuerAnalysis analysis) {
      newInstanceAnalyses.add(analysis);
      return this;
    }

    // IR analysis.
    public Builder addIrBasedEnqueuerAnalysis(IrBasedEnqueuerAnalysis analysis) {
      irBasedEnqueuerAnalyses.add(analysis);
      return this;
    }

    // Reachability events.

    public Builder addNewlyFailedMethodResolutionAnalysis(
        NewlyFailedMethodResolutionEnqueuerAnalysis analysis) {
      newlyFailedMethodResolutionAnalyses.add(analysis);
      return this;
    }

    public Builder addNewlyLiveClassAnalysis(NewlyLiveClassEnqueuerAnalysis analysis) {
      newlyLiveClassAnalyses.add(analysis);
      return this;
    }

    public Builder addNewlyLiveCodeAnalysis(NewlyLiveCodeEnqueuerAnalysis analysis) {
      newlyLiveCodeAnalyses.add(analysis);
      return this;
    }

    public Builder addNewlyLiveFieldAnalysis(NewlyLiveFieldEnqueuerAnalysis analysis) {
      newlyLiveFieldAnalyses.add(analysis);
      return this;
    }

    public Builder addNewlyLiveMethodAnalysis(NewlyLiveMethodEnqueuerAnalysis analysis) {
      newlyLiveMethodAnalyses.add(analysis);
      return this;
    }

    public Builder addNewlyInstantiatedClassAnalysis(
        NewlyInstantiatedClassEnqueuerAnalysis analysis) {
      newlyInstantiatedClassAnalyses.add(analysis);
      return this;
    }

    public Builder addNewlyLiveNonProgramClassAnalysis(
        NewlyLiveNonProgramClassEnqueuerAnalysis analysis) {
      newlyLiveNonProgramClassAnalyses.add(analysis);
      return this;
    }

    public Builder addNewlyReachableFieldAnalysis(NewlyReachableFieldEnqueuerAnalysis analysis) {
      newlyReachableFieldAnalyses.add(analysis);
      return this;
    }

    public Builder addNewlyReferencedFieldAnalysis(NewlyReferencedFieldEnqueuerAnalysis analysis) {
      newlyReferencedFieldAnalyses.add(analysis);
      return this;
    }

    public Builder addNewlyTargetedMethodAnalysis(NewlyTargetedMethodEnqueuerAnalysis analysis) {
      newlyTargetedMethodAnalyses.add(analysis);
      return this;
    }

    public Builder addMarkFieldAsKeptAnalysis(MarkFieldAsKeptEnqueuerAnalysis analysis) {
      markFieldAsKeptAnalyses.add(analysis);
      return this;
    }

    // Tear down events.

    public Builder addFinishedAnalysis(FinishedEnqueuerAnalysis analysis) {
      finishedAnalyses.add(analysis);
      return this;
    }

    public Builder addFixpointAnalysis(FixpointEnqueuerAnalysis analysis) {
      fixpointAnalyses.add(analysis);
      return this;
    }

    public EnqueuerAnalysisCollection build() {
      return new EnqueuerAnalysisCollection(
          // Trace events.
          checkCastAnalyses.toArray(TraceCheckCastEnqueuerAnalysis[]::new),
          constClassAnalyses.toArray(TraceConstClassEnqueuerAnalysis[]::new),
          exceptionGuardAnalyses.toArray(TraceExceptionGuardEnqueuerAnalysis[]::new),
          fieldAccessAnalyses.toArray(TraceFieldAccessEnqueuerAnalysis[]::new),
          instanceOfAnalyses.toArray(TraceInstanceOfEnqueuerAnalysis[]::new),
          invokeAnalyses.toArray(TraceInvokeEnqueuerAnalysis[]::new),
          newInstanceAnalyses.toArray(TraceNewInstanceEnqueuerAnalysis[]::new),
          // IR analysis.
          irBasedEnqueuerAnalyses.toArray(IrBasedEnqueuerAnalysis[]::new),
          // Reachability events.
          newlyFailedMethodResolutionAnalyses.toArray(
              NewlyFailedMethodResolutionEnqueuerAnalysis[]::new),
          newlyLiveClassAnalyses.toArray(NewlyLiveClassEnqueuerAnalysis[]::new),
          newlyLiveCodeAnalyses.toArray(NewlyLiveCodeEnqueuerAnalysis[]::new),
          newlyLiveFieldAnalyses.toArray(NewlyLiveFieldEnqueuerAnalysis[]::new),
          newlyLiveMethodAnalyses.toArray(NewlyLiveMethodEnqueuerAnalysis[]::new),
          newlyInstantiatedClassAnalyses.toArray(NewlyInstantiatedClassEnqueuerAnalysis[]::new),
          newlyLiveNonProgramClassAnalyses.toArray(NewlyLiveNonProgramClassEnqueuerAnalysis[]::new),
          newlyReachableFieldAnalyses.toArray(NewlyReachableFieldEnqueuerAnalysis[]::new),
          newlyReferencedFieldAnalyses.toArray(NewlyReferencedFieldEnqueuerAnalysis[]::new),
          newlyTargetedMethodAnalyses.toArray(NewlyTargetedMethodEnqueuerAnalysis[]::new),
          markFieldAsKeptAnalyses.toArray(MarkFieldAsKeptEnqueuerAnalysis[]::new),
          // Tear down events.
          finishedAnalyses.toArray(FinishedEnqueuerAnalysis[]::new),
          fixpointAnalyses.toArray(FixpointEnqueuerAnalysis[]::new));
    }
  }
}
