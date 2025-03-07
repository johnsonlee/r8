// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.numberunboxer;

import static com.android.tools.r8.ir.optimize.numberunboxer.MethodBoxingStatus.UNPROCESSED_CANDIDATE;
import static com.android.tools.r8.ir.optimize.numberunboxer.NumberUnboxerBoxingStatusResolution.MethodBoxingStatusResult.BoxingStatusResult.UNBOX;
import static com.android.tools.r8.ir.optimize.numberunboxer.ValueBoxingStatus.NOT_UNBOXABLE;

import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMember;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ImmediateProgramSubtypingInfo;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.Return;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.PostMethodProcessor;
import com.android.tools.r8.ir.optimize.numberunboxer.NumberUnboxerBoxingStatusResolution.MethodBoxingStatusResult;
import com.android.tools.r8.ir.optimize.numberunboxer.TransitiveDependency.MethodArg;
import com.android.tools.r8.ir.optimize.numberunboxer.TransitiveDependency.MethodRet;
import com.android.tools.r8.optimize.argumentpropagation.utils.ProgramClassesBidirectedGraph;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.MapUtils;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.collections.DexMethodSignatureMap;
import com.android.tools.r8.utils.timing.Timing;
import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class NumberUnboxerImpl extends NumberUnboxer {

  private final AppView<AppInfoWithLiveness> appView;
  private final DexItemFactory factory;
  private final Set<DexType> boxedTypes;

  // All candidate methods are initialized to UNPROCESSED_CANDIDATE (bottom) and methods not in
  // this map are not subject to unboxing.
  private final Map<DexMethod, MethodBoxingStatus> candidateBoxingStatus =
      new ConcurrentHashMap<>();
  private Map<DexMethod, DexMethod> virtualMethodsRepresentative;

  public NumberUnboxerImpl(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
    this.factory = appView.dexItemFactory();
    this.boxedTypes = factory.primitiveToBoxed.values();
  }

  /**
   * The preparation agglomerate targets or virtual calls into a deterministic method amongst them.
   * This allows R8 to compute the boxing status once for all targets of the same call.
   */
  @Override
  public void prepareForPrimaryOptimizationPass(Timing timing, ExecutorService executorService)
      throws ExecutionException {
    timing.begin("Prepare number unboxer tree fixer");
    ImmediateProgramSubtypingInfo immediateSubtypingInfo =
        ImmediateProgramSubtypingInfo.create(appView);
    List<Set<DexProgramClass>> connectedComponents =
        new ProgramClassesBidirectedGraph(appView, immediateSubtypingInfo)
            .computeStronglyConnectedComponents();
    Set<Map<DexMethod, DexMethod>> virtualMethodsRepresentativeToMerge =
        ConcurrentHashMap.newKeySet();
    ThreadUtils.processItems(
        connectedComponents,
        component ->
            virtualMethodsRepresentativeToMerge.add(computeVirtualMethodRepresentative(component)),
        appView.options().getThreadingModule(),
        executorService);
    virtualMethodsRepresentative =
        MapUtils.newImmutableMap(
            builder -> virtualMethodsRepresentativeToMerge.forEach(builder::putAll));
    timing.end();
  }

  // TODO(b/307872552): Do not store irrelevant representative.
  private Map<DexMethod, DexMethod> computeVirtualMethodRepresentative(
      Set<DexProgramClass> component) {
    DexMethodSignatureMap<List<ProgramMethod>> componentVirtualMethods =
        DexMethodSignatureMap.create();
    for (DexProgramClass clazz : component) {
      for (ProgramMethod virtualProgramMethod : clazz.virtualProgramMethods()) {
        List<ProgramMethod> set =
            componentVirtualMethods.computeIfAbsent(virtualProgramMethod, k -> new ArrayList<>());
        set.add(virtualProgramMethod);
      }
      for (ProgramMethod candidate : clazz.directProgramMethods()) {
        if (shouldConsiderForUnboxing(candidate)) {
          candidateBoxingStatus.put(candidate.getReference(), UNPROCESSED_CANDIDATE);
        }
      }
    }
    Map<DexMethod, DexMethod> vMethodRepresentative = new IdentityHashMap<>();
    for (List<ProgramMethod> vMethods : componentVirtualMethods.values()) {
      if (vMethods.size() > 1) {
        if (Iterables.all(vMethods, this::shouldConsiderForUnboxing)
            && Iterables.any(vMethods, m -> !m.getDefinition().isAbstract())) {
          vMethods.sort(Comparator.comparing(DexClassAndMember::getReference));
          ProgramMethod representative = vMethods.get(0);
          for (int i = 1; i < vMethods.size(); i++) {
            vMethodRepresentative.put(
                vMethods.get(i).getReference(), representative.getReference());
          }
          candidateBoxingStatus.put(representative.getReference(), UNPROCESSED_CANDIDATE);
        }
      } else {
        assert vMethods.size() == 1;
        ProgramMethod candidate = vMethods.get(0);
        if (shouldConsiderForUnboxing(candidate) && !candidate.getDefinition().isAbstract()) {
          candidateBoxingStatus.put(candidate.getReference(), UNPROCESSED_CANDIDATE);
        }
      }
    }
    return vMethodRepresentative;
  }

  private void registerMethodUnboxingStatusIfNeeded(
      ProgramMethod method, ValueBoxingStatus returnStatus, ValueBoxingStatus[] args) {
    DexMethod representative = representative(method.getReference());
    if (args == null && (returnStatus == null || returnStatus.isNotUnboxable())) {
      // Effectively NOT_UNBOXABLE, remove the candidate.
      // TODO(b/307872552): Do we need to remove at the end of the wave for determinism?
      candidateBoxingStatus.remove(representative);
      return;
    }
    ValueBoxingStatus nonNullReturnStatus = returnStatus == null ? NOT_UNBOXABLE : returnStatus;
    ValueBoxingStatus[] nonNullArgs =
        args == null ? ValueBoxingStatus.notUnboxableArray(method.getReference().getArity()) : args;
    MethodBoxingStatus unboxingStatus = MethodBoxingStatus.create(nonNullReturnStatus, nonNullArgs);
    assert !unboxingStatus.isNoneUnboxable();
    MethodBoxingStatus newStatus =
        candidateBoxingStatus.computeIfPresent(
            representative, (m, old) -> old.merge(unboxingStatus));
    if (newStatus != null && newStatus.isNoneUnboxable()) {
      // TODO(b/307872552): Do we need to remove at the end of the wave for determinism?
      candidateBoxingStatus.remove(representative);
    }
  }

  private DexMethod representative(DexMethod method) {
    return virtualMethodsRepresentative.getOrDefault(method, method);
  }

  /**
   * Analysis phase: Figures out in each method if parameters, invoke, field accesses and return
   * values are used in boxing operations.
   */
  @Override
  public void analyze(IRCode code) {
    DexMethod contextReference = code.context().getReference();
    ValueBoxingStatus[] args = null;
    ValueBoxingStatus returnStatus = null;
    int shift = BooleanUtils.intValue(!code.context().getDefinition().isStatic());
    for (Instruction next : code.instructions()) {
      if (next.isArgument()) {
        ValueBoxingStatus unboxingStatus = analyzeOutput(next.outValue());
        if (unboxingStatus.mayBeUnboxable()) {
          if (args == null) {
            args = new ValueBoxingStatus[contextReference.getArity()];
            Arrays.fill(args, NOT_UNBOXABLE);
          }
          args[next.asArgument().getIndex() - shift] = unboxingStatus;
        }
      } else if (next.isReturn()) {
        Return ret = next.asReturn();
        if (ret.hasReturnValue() && (returnStatus == null || returnStatus.mayBeUnboxable())) {
          ValueBoxingStatus unboxingStatus = analyzeInput(ret.returnValue(), code.context());
          if (unboxingStatus.mayBeUnboxable()) {
            returnStatus =
                returnStatus == null ? unboxingStatus : returnStatus.merge(unboxingStatus);
          } else {
            returnStatus = NOT_UNBOXABLE;
          }
        }
      } else if (next.isInvokeMethod()) {
        analyzeInvoke(next.asInvokeMethod(), code.context());
      } else if (next.isInvokeCustom()) {
        throw new Unimplemented();
      }
    }
    // TODO(b/307872552): Analyse field access to unbox fields.
    registerMethodUnboxingStatusIfNeeded(code.context(), returnStatus, args);
  }

  private void analyzeInvoke(InvokeMethod invoke, ProgramMethod context) {
    ProgramMethod resolvedMethod =
        appView
            .appInfo()
            .resolveMethodLegacy(invoke.getInvokedMethod(), invoke.getInterfaceBit())
            .getResolvedProgramMethod();
    if (resolvedMethod == null) {
      return;
    }
    ValueBoxingStatus[] args = null;
    int shift = invoke.getFirstNonReceiverArgumentIndex();
    for (int i = shift; i < invoke.inValues().size(); i++) {
      ValueBoxingStatus unboxingStatus = analyzeInput(invoke.getArgument(i), context);
      if (unboxingStatus.mayBeUnboxable()) {
        if (args == null) {
          args = new ValueBoxingStatus[invoke.getInvokedMethod().getArity()];
          Arrays.fill(args, NOT_UNBOXABLE);
        }
        args[i - shift] = unboxingStatus;
      }
    }
    ValueBoxingStatus returnVal = null;
    if (invoke.hasOutValue()) {
      ValueBoxingStatus unboxingStatus = analyzeOutput(invoke.outValue());
      if (unboxingStatus.mayBeUnboxable()) {
        returnVal = unboxingStatus;
      }
    }
    registerMethodUnboxingStatusIfNeeded(resolvedMethod, returnVal, args);
  }

  private boolean shouldConsiderForUnboxing(Value value) {
    return value.getType().isClassType()
        && shouldConsiderForUnboxing(value.getType().asClassType().getClassType());
  }

  private boolean shouldConsiderForUnboxing(ProgramMethod method) {
    if (appView.getKeepInfo().isPinned(method, appView.options())) {
      return false;
    }
    return shouldConsiderForUnboxing(method.getReturnType())
        || Iterables.any(method.getParameters(), this::shouldConsiderForUnboxing);
  }

  private boolean shouldConsiderForUnboxing(DexType type) {
    // TODO(b/307872552): So far we consider only boxed type value to unbox them into their
    // corresponding primitive type, for example, Integer -> int. It would be nice to support
    // the pattern checkCast(BoxType) followed by a boxing operation, so that for example when
    // we have MyClass<T> and T is proven to be an Integer, we can unbox into int.
    // Types to consider: Object, Serializable, Comparable, Number.
    return boxedTypes.contains(type);
  }

  // Inputs are values flowing into a method return, an invoke argument or a field write.
  private ValueBoxingStatus analyzeInput(Value inValue, ProgramMethod context) {
    if (!shouldConsiderForUnboxing(inValue)) {
      return NOT_UNBOXABLE;
    }
    DexType boxedType = inValue.getType().asClassType().getClassType();
    DexType primitiveType = factory.primitiveToBoxed.inverse().get(boxedType);
    DexMethod boxPrimitiveMethod = factory.getBoxPrimitiveMethod(primitiveType);
    if (!inValue.getAliasedValue().isPhi()) {
      Instruction definition = inValue.getAliasedValue().getDefinition();
      if (definition.isArgument()) {
        int shift = BooleanUtils.intValue(!context.getDefinition().isStatic());
        return ValueBoxingStatus.with(
            new MethodArg(
                definition.asArgument().getIndex() - shift,
                representative(context.getReference())));
      }
      if (definition.isInvokeMethod()) {
        if (boxPrimitiveMethod.isIdenticalTo(definition.asInvokeMethod().getInvokedMethod())) {
          // The result of a boxing operation is non nullable.
          if (!inValue.hasPhiUsers() && inValue.hasSingleUniqueUser()) {
            // Unboxing would remove a boxing operation.
            return ValueBoxingStatus.with(1);
          }
          // Unboxing would add and remove a boxing operation.
          return ValueBoxingStatus.with(0);
        }
        InvokeMethod invoke = definition.asInvokeMethod();
        ProgramMethod resolvedMethod =
            appView
                .appInfo()
                .resolveMethodLegacy(invoke.getInvokedMethod(), invoke.getInterfaceBit())
                .getResolvedProgramMethod();
        if (resolvedMethod != null) {
          return ValueBoxingStatus.with(
              new MethodRet(representative(resolvedMethod.getReference())));
        }
      }
    }
    // TODO(b/307872552) We should support field reads as transitive dependencies.
    if (inValue.getType().isNullable()) {
      return NOT_UNBOXABLE;
    }
    // TODO(b/307872552): We could analyze simple phis, for example,
    //  Integer i = bool ? integer.valueOf(1) : Integer.valueOf(2);
    //  removes 2 operations and does not add 1.
    // Since we cannot interpret the definition, unboxing adds a boxing operation.
    return ValueBoxingStatus.with(-1);
  }

  // Outputs are method arguments, invoke return values and field reads.
  private ValueBoxingStatus analyzeOutput(Value outValue) {
    if (!shouldConsiderForUnboxing(outValue)) {
      return NOT_UNBOXABLE;
    }
    DexType boxedType = outValue.getType().asClassType().getClassType();
    DexMethod unboxPrimitiveMethod = factory.getUnboxPrimitiveMethod(boxedType);
    boolean metUnboxingOperation = false;
    boolean metOtherOperation = outValue.hasPhiUsers();
    for (Instruction uniqueUser : outValue.aliasedUsers()) {
      if (uniqueUser.isAssumeWithNonNullAssumption()) {
        // Nothing to do, the assume will be removed by unboxing.
      } else if (uniqueUser.isInvokeMethod()
          && unboxPrimitiveMethod.isIdenticalTo(uniqueUser.asInvokeMethod().getInvokedMethod())) {
        metUnboxingOperation = true;
      } else {
        metOtherOperation = true;
      }
    }
    return ValueBoxingStatus.with(computeBoxingDelta(metUnboxingOperation, metOtherOperation));
  }

  private int computeBoxingDelta(boolean metUnboxingOperation, boolean metOtherOperation) {
    if (metUnboxingOperation) {
      if (metOtherOperation) {
        // Unboxing would add and remove a boxing operation.
        return 0;
      }
      // Unboxing would remove a boxing operation.
      return 1;
    }
    if (metOtherOperation) {
      // Unboxing would add a boxing operation.
      return -1;
    }
    // Unused, unboxing won't change the number of boxing operations.
    return 0;
  }

  @Override
  public void unboxNumbers(
      PostMethodProcessor.Builder postMethodProcessorBuilder,
      Timing timing,
      ExecutorService executorService)
      throws ExecutionException {
    Map<DexMethod, MethodBoxingStatusResult> unboxingResult =
        new NumberUnboxerBoxingStatusResolution(candidateBoxingStatus).resolve();
    if (unboxingResult.isEmpty()) {
      return;
    }

    NumberUnboxerLens numberUnboxerLens =
        new NumberUnboxerTreeFixer(appView, unboxingResult, virtualMethodsRepresentative)
            .fixupTree(executorService, timing);
    appView.rewriteWithLens(numberUnboxerLens, executorService, timing);
    new NumberUnboxerMethodReprocessingEnqueuer(appView, numberUnboxerLens)
        .enqueueMethodsForReprocessing(postMethodProcessorBuilder, executorService, timing);

    if (appView.testing().printNumberUnboxed) {
      printNumberUnboxed(unboxingResult);
    }
  }

  private void printNumberUnboxed(Map<DexMethod, MethodBoxingStatusResult> unboxingResult) {
    StringBuilder stringBuilder = new StringBuilder();
    unboxingResult.forEach(
        (k, v) -> {
          if (v.getRet() == UNBOX) {
            stringBuilder
                .append("Unboxing of return value of ")
                .append(k)
                .append(System.lineSeparator());
          }
          for (int i = 0; i < v.getArgs().length; i++) {
            if (v.getArg(i) == UNBOX) {
              stringBuilder
                  .append("Unboxing of arg ")
                  .append(i)
                  .append(" of ")
                  .append(k)
                  .append(System.lineSeparator());
            }
          }
        });
    appView.reporter().warning(stringBuilder.toString());
  }

  @Override
  public void onMethodPruned(ProgramMethod method) {
    // TODO(b/307872552): Should we do something about this? We might need to change the
    //  representative.
  }

  @Override
  public void onMethodCodePruned(ProgramMethod method) {
    // TODO(b/307872552): I don't think we should do anything here.
  }

  @Override
  public void rewriteWithLens() {
    // TODO(b/307872552): This needs to rewrite the methodBoxingStatus.
  }
}
