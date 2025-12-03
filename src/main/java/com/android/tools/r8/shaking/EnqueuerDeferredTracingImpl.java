// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;
import static com.android.tools.r8.shaking.ObjectAllocationInfoCollectionUtils.mayHaveFinalizeMethodDirectlyOrIndirectly;
import static com.android.tools.r8.utils.MapUtils.ignoreKey;
import static com.google.common.base.Predicates.alwaysFalse;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.FieldAccessInfoCollectionImpl;
import com.android.tools.r8.graph.FieldAccessInfoImpl;
import com.android.tools.r8.graph.FieldResolutionResult;
import com.android.tools.r8.graph.ProgramDefinition;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.analysis.EnqueuerAnalysisCollection;
import com.android.tools.r8.graph.analysis.FixpointEnqueuerAnalysis;
import com.android.tools.r8.graph.analysis.NewlyLiveMethodEnqueuerAnalysis;
import com.android.tools.r8.graph.bytecodemetadata.BytecodeMetadataProvider;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.conversion.IRFinalizer;
import com.android.tools.r8.ir.conversion.MethodConversionOptions;
import com.android.tools.r8.ir.conversion.passes.AssumeRemover;
import com.android.tools.r8.ir.conversion.passes.ThrowCatchOptimizer;
import com.android.tools.r8.ir.optimize.AssumeInserter;
import com.android.tools.r8.ir.optimize.info.OptimizationInfoRemover;
import com.android.tools.r8.ir.optimize.membervaluepropagation.assume.AssumeInfo;
import com.android.tools.r8.shaking.Enqueuer.FieldAccessKind;
import com.android.tools.r8.shaking.Enqueuer.FieldAccessMetadata;
import com.android.tools.r8.shaking.EnqueuerWorklist.EnqueuerAction;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.ThreadUtils.WorkLoad;
import com.android.tools.r8.utils.collections.ProgramFieldMap;
import com.android.tools.r8.utils.collections.ProgramFieldSet;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import com.android.tools.r8.utils.timing.Timing;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Predicate;

public class EnqueuerDeferredTracingImpl extends EnqueuerDeferredTracing
    implements FixpointEnqueuerAnalysis, NewlyLiveMethodEnqueuerAnalysis {

  private final AppView<? extends AppInfoWithClassHierarchy> appView;
  private final Enqueuer enqueuer;
  private final InternalOptions options;

  // Helper for rewriting code instances at the end of tree shaking.
  private final EnqueuerDeferredTracingRewriter rewriter;

  // Maps each field to the tracing actions that have been deferred for that field. This allows
  // enqueuing previously deferred tracing actions into the worklist if a given field cannot be
  // optimized after all.
  private final ProgramFieldMap<Set<EnqueuerAction>> deferredEnqueuerActions =
      ProgramFieldMap.create();

  // A set of fields that are never eligible for pruning.
  private final ProgramFieldSet ineligibleForPruning = ProgramFieldSet.create();

  // Whether a finalize() method has become live since the last enqueuer fixpoint.
  private boolean newlySeenProgramFinalizer = false;

  EnqueuerDeferredTracingImpl(
      AppView<? extends AppInfoWithClassHierarchy> appView, Enqueuer enqueuer) {
    this.appView = appView;
    this.enqueuer = enqueuer;
    this.options = appView.options();
    this.rewriter = new EnqueuerDeferredTracingRewriter(appView);
  }

  @Override
  public boolean deferTracingOfFieldAccess(
      DexField fieldReference,
      FieldResolutionResult resolutionResult,
      ProgramMethod context,
      FieldAccessKind accessKind,
      FieldAccessMetadata metadata) {
    if (!fieldReference.getType().isPrimitiveType()
        && !options.getTestingOptions().enableEnqueuerDeferredTracingForReferenceFields) {
      return false;
    }

    ProgramField field = resolutionResult.getSingleProgramField();
    if (field == null) {
      return false;
    }

    // Check if field access is consistent with the field access flags.
    if (field.getAccessFlags().isStatic() != accessKind.isStatic()) {
      return enqueueDeferredEnqueuerActions(field);
    }

    if (resolutionResult.isAccessibleFrom(context, appView).isPossiblyFalse()) {
      return enqueueDeferredEnqueuerActions(field);
    }

    // If the access is from a reachability sensitive method, then bail out.
    if (context.getHolder().isReachabilitySensitive()) {
      return enqueueDeferredEnqueuerActions(field);
    }

    if (accessKind.isRead()) {
      // If the value of the field is not guaranteed to be the default value, even if it is never
      // assigned, then give up.
      // TODO(b/205810841): Allow this by handling this in the corresponding IR rewriter.
      AssumeInfo assumeInfo = appView.getAssumeInfoCollection().get(field);
      if (!assumeInfo.getAssumeValue().isUnknown()) {
        return enqueueDeferredEnqueuerActions(field);
      }
      if (field.getAccessFlags().isStatic() && field.getDefinition().hasExplicitStaticValue()) {
        return enqueueDeferredEnqueuerActions(field);
      }
    }

    // During tracing we assume that the field cannot store an object with a finalize method. This
    // is safe to assume since we will check this property at the next fixpoint. If it is found to
    // have a finalize method at that time, we will mark it as ineligible for optimization.
    if (!isEligibleForPruning(field, alwaysFalse())) {
      return enqueueDeferredEnqueuerActions(field);
    }

    // If the field is static, then the field access will trigger the class initializer of the
    // field's holder. Therefore, we unconditionally trace the class initializer in this case.
    // The corresponding IR rewriter will rewrite the field access into an init-class instruction.
    if (accessKind.isStatic()) {
      if (enqueuer.getMode().isInitialTreeShaking() && field.getHolder() != context.getHolder()) {
        // TODO(b/338000616): No support for InitClass until we have LIR in the initial round of
        //  tree shaking.
        return false;
      }
      KeepReason reason =
          enqueuer.getGraphReporter().reportClassReferencedFrom(field.getHolder(), context);
      enqueuer.getWorklist().enqueueTraceTypeReferenceAction(field.getHolder(), reason);
      enqueuer.getWorklist().enqueueTraceDirectAndIndirectClassInitializers(field.getHolder());
    }

    // Field can be removed unless some other field access that has not yet been seen prohibits it.
    // Record an EnqueuerAction that must be traced if that should happen.
    EnqueuerAction deferredEnqueuerAction =
        accessKind.toEnqueuerAction(fieldReference, context, metadata.toDeferred());
    deferredEnqueuerActions
        .computeIfAbsent(field, ignoreKey(LinkedHashSet::new))
        .add(deferredEnqueuerAction);

    return true;
  }

  @Override
  public void notifyReflectiveFieldAccess(ProgramField field, ProgramMethod context) {
    enqueueDeferredEnqueuerActions(field);
  }

  private boolean isEligibleForPruning(
      ProgramField field, Predicate<DexType> mayHaveFinalizeMethodDirectlyOrIndirectly) {
    if (enqueuer.isFieldLive(field)) {
      return false;
    }

    assert enqueuer.getKeepInfo(field).isBottom();
    assert !enqueuer.getKeepInfo(field).isPinned(options) || options.isOptimizedResourceShrinking();

    FieldAccessInfoImpl info = enqueuer.getFieldAccessInfoCollection().get(field.getReference());
    if (info.hasReflectiveAccess()
        || info.isAccessedFromMethodHandle()
        || info.isReadFromAnnotation()
        || info.isReadFromRecordInvokeDynamic()
        || enqueuer.hasMinimumKeepInfoThatMatches(
            field,
            minimumKeepInfo ->
                !minimumKeepInfo.isOptimizationAllowed()
                    || !minimumKeepInfo.isShrinkingAllowed())) {
      return false;
    }
    if (!options.isOptimizing()) {
      assert options.isOptimizedResourceShrinking();
      if (!enqueuer.isRClass(field.getHolder())) {
        return false;
      }
    }

    if (info.isWritten()) {
      // If the assigned value may have an override of Object#finalize() then give up.
      // Note that this check depends on the set of instantiated types, and must therefore be rerun
      // when the enqueuer's fixpoint is reached.
      if (field.getType().isReferenceType()) {
        DexType fieldBaseType = field.getType().getBaseType();
        if (fieldBaseType.isClassType()
            && mayHaveFinalizeMethodDirectlyOrIndirectly.test(fieldBaseType)) {
          return false;
        }
      }
    }

    // We always have precise knowledge of field accesses during tracing.
    assert info.hasKnownReadContexts();
    assert info.hasKnownWriteContexts();

    DexType fieldType = field.getType();

    // If the field is now both read and written, then we cannot optimize the field unless the field
    // type is an uninstantiated class type.
    if (info.isReadDirectly() && info.isWrittenDirectly()) {
      if (!fieldType.isClassType()) {
        return false;
      }
      DexProgramClass fieldTypeDefinition = asProgramClassOrNull(appView.definitionFor(fieldType));
      if (fieldTypeDefinition == null
          || enqueuer
              .getObjectAllocationInfoCollection()
              .isInstantiatedDirectlyOrHasInstantiatedSubtype(fieldTypeDefinition)) {
        return false;
      }
    }

    return !ineligibleForPruning.contains(field);
  }

  private boolean enqueueDeferredEnqueuerActions(ProgramField field) {
    Set<EnqueuerAction> actions = deferredEnqueuerActions.remove(field);
    if (actions != null) {
      enqueuer.getWorklist().enqueueAll(actions);
    }
    ineligibleForPruning.add(field);
    return false;
  }

  @Override
  public void register(EnqueuerAnalysisCollection.Builder analysesBuilder) {
    analysesBuilder.addFixpointAnalysis(this).addNewlyLiveMethodAnalysis(this);
  }

  @Override
  public void processNewlyLiveMethod(
      ProgramMethod method,
      ProgramDefinition context,
      Enqueuer enqueuer,
      EnqueuerWorklist worklist) {
    if (method.getReference().match(appView.dexItemFactory().objectMembers.finalize)) {
      newlySeenProgramFinalizer = true;
    }
  }

  @Override
  public void notifyFixpoint(
      Enqueuer enqueuer, EnqueuerWorklist worklist, ExecutorService executorService, Timing timing)
      throws ExecutionException {
    timing.begin("Process deferred tracing");
    Map<DexType, List<ProgramField>> pendingFinalizerChecks = new IdentityHashMap<>();
    deferredEnqueuerActions.removeIf(
        (field, worklistActions) -> {
          boolean isEligibleForPruning =
              isEligibleForPruning(
                  field,
                  fieldBaseType ->
                      addToPendingFinalizerChecks(fieldBaseType, field, pendingFinalizerChecks));
          if (isEligibleForPruning) {
            return false;
          }
          ineligibleForPruning.add(field);
          worklist.enqueueAll(worklistActions);
          return true;
        });
    pendingFinalizerChecks.forEach(
        (fieldBaseType, fields) -> {
          if (computeMayHaveFinalizeMethodDirectlyOrIndirectly(fieldBaseType)) {
            for (ProgramField field : fields) {
              if (ineligibleForPruning.add(field)) {
                worklist.enqueueAll(deferredEnqueuerActions.remove(field));
              }
            }
          }
        });
    timing.end();
    newlySeenProgramFinalizer = false;
  }

  private boolean addToPendingFinalizerChecks(
      DexType fieldBaseType,
      ProgramField field,
      Map<DexType, List<ProgramField>> pendingFinalizerChecks) {
    pendingFinalizerChecks.computeIfAbsent(fieldBaseType, ignoreKey(ArrayList::new)).add(field);
    return false;
  }

  private boolean computeMayHaveFinalizeMethodDirectlyOrIndirectly(DexType fieldBaseType) {
    DexClass fieldBaseClass = appView.definitionFor(fieldBaseType);
    if (fieldBaseClass == null) {
      return false;
    }
    if (newlySeenProgramFinalizer
        || !fieldBaseClass.isProgramClass()
        || fieldBaseClass.isInterface()) {
      return mayHaveFinalizeMethodDirectlyOrIndirectly(
          appView, fieldBaseType, enqueuer.getObjectAllocationInfoCollection());
    } else {
      return ObjectAllocationInfoCollectionUtils.hasFinalizeMethodDirectly(appView, fieldBaseClass);
    }
  }

  @Override
  public void rewriteApplication(ExecutorService executorService) throws ExecutionException {
    FieldAccessInfoCollectionImpl fieldAccessInfoCollection =
        enqueuer.getFieldAccessInfoCollection();
    ProgramMethodSet methodsToProcess = ProgramMethodSet.create();
    Map<DexField, ProgramField> prunedFields = new IdentityHashMap<>();
    deferredEnqueuerActions.forEach(
        (field, ignore) -> {
          FieldAccessInfoImpl accessInfo = fieldAccessInfoCollection.get(field.getReference());
          prunedFields.put(field.getReference(), field);
          accessInfo.forEachAccessContext(methodsToProcess::add);
          accessInfo.forEachIndirectAccess(reference -> prunedFields.put(reference, field));
        });
    deferredEnqueuerActions.clear();

    // Rewrite application.
    Map<DexProgramClass, ProgramMethodSet> initializedClassesWithContexts =
        new ConcurrentHashMap<>();
    ProgramMethodSet instanceInitializers = ProgramMethodSet.createConcurrent();
    ThreadUtils.processItems(
        methodsToProcess,
        (method, ignored) -> {
          rewriteMethod(method, initializedClassesWithContexts, prunedFields);
          if (method.getDefinition().isInstanceInitializer()) {
            instanceInitializers.add(method);
          }
        },
        appView.options().getThreadingModule(),
        executorService,
        WorkLoad.HEAVY);

    // Register new InitClass instructions.
    initializedClassesWithContexts.forEach(
        (clazz, contexts) ->
            contexts.forEach(context -> enqueuer.traceInitClass(clazz.getType(), context)));
    assert enqueuer.getWorklist().isEmpty();

    // Clear the optimization info of instance initializers since the instance initializer
    // optimization info may change when removing field puts.
    instanceInitializers.forEach(
        method -> OptimizationInfoRemover.processMethod(method.getDefinition()));

    // Prune field access info collection.
    prunedFields.values().forEach(field -> fieldAccessInfoCollection.remove(field.getReference()));
  }

  private void rewriteMethod(
      ProgramMethod method,
      Map<DexProgramClass, ProgramMethodSet> initializedClassesWithContexts,
      Map<DexField, ProgramField> prunedFields) {
    // Build IR.
    IRCode ir = method.buildIR(appView, MethodConversionOptions.forLirPhase(appView));

    new AssumeInserter(appView).insertAssumeInstructions(ir, Timing.empty());

    // Rewrite the IR according to the tracing that has been deferred.
    rewriter.rewriteCode(ir, initializedClassesWithContexts, prunedFields);

    // Run dead code elimination.
    new ThrowCatchOptimizer(appView).run(ir, Timing.empty());
    rewriter.getDeadCodeRemover().run(ir, Timing.empty());
    new AssumeRemover(appView).run(ir, Timing.empty());

    // Finalize out of IR.
    IRFinalizer<?> finalizer =
        ir.getConversionOptions().getFinalizer(rewriter.getDeadCodeRemover(), appView);
    Code newCode = finalizer.finalizeCode(ir, BytecodeMetadataProvider.empty(), Timing.empty());
    method.setCode(newCode, appView);
  }
}
