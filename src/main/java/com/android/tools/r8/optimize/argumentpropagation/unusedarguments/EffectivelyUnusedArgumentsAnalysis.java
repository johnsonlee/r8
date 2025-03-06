// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation.unusedarguments;

import static com.android.tools.r8.utils.MapUtils.ignoreKey;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethodSignature;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.path.PathConstraintSupplier;
import com.android.tools.r8.ir.analysis.path.state.ConcretePathConstraintAnalysisState;
import com.android.tools.r8.ir.analysis.path.state.PathConstraintKind;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.code.Argument;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.InvokeVirtual;
import com.android.tools.r8.ir.code.LazyDominatorTree;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.MethodParameter;
import com.android.tools.r8.optimize.argumentpropagation.computation.ComputationTreeNode;
import com.android.tools.r8.optimize.argumentpropagation.computation.ComputationTreeUnopCompareNode;
import com.android.tools.r8.optimize.argumentpropagation.utils.ParameterRemovalUtils;
import com.android.tools.r8.optimize.compose.ComposeReferences;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.WorkList;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import com.google.common.collect.Sets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Analysis to find arguments that are effectively unused. The analysis first computes the
 * constraints for a given argument to be effectively unused, and then subsequently solves the
 * computed constraints.
 *
 * <p>Example: Consider the following Companion class.
 *
 * <pre>
 *   static class Companion {
 *     void foo() {
 *       this.bar()
 *     }
 *     void bar() {
 *       doStuff();
 *     }
 *   }
 * </pre>
 *
 * <p>The analysis works as follows.
 *
 * <ol>
 *   <li>When IR processing the Companion.bar() method, the unused argument analysis records that
 *       its receiver is unused.
 *   <li>When IR processing the Companion.foo() method, the effectively unused argument analysis
 *       records that the receiver of Companion.foo() is unused if the receiver of Companion.bar()
 *       is unused.
 *   <li>After IR processing all methods, the effectively unused argument analysis builds a graph
 *       where there is a directed edge p0 -> p1 if the removal of method parameter p0 depends on
 *       the removal of method parameter p1.
 *   <li>The analysis then repeatedly removes method parameters from the graph that have no outgoing
 *       edges and marks such method parameters as being effectively unused.
 * </ol>
 *
 * <p>
 */
public class EffectivelyUnusedArgumentsAnalysis {

  private final AppView<AppInfoWithLiveness> appView;

  // If the condition for a given method parameter evaluates to true then the parameter is unused,
  // regardless (!) of the constraints for the method parameter in `constraints`. If the condition
  // evaluates to false, the method parameter can still be unused if all the constraints for the
  // method parameter are unused.
  //
  // All constraints in this map are currently on the form `(arg & const) != 0`.
  private final Map<MethodParameter, ComputationTreeNode> conditions = new ConcurrentHashMap<>();

  // Maps each method parameter p to the method parameters that must be effectively unused in order
  // for the method parameter p to be effectively unused.
  private final Map<MethodParameter, Set<MethodParameter>> constraints = new ConcurrentHashMap<>();

  // Maps Composable parameters to a condition. If the condition evaluates to true for a given
  // invoke, then the argument to this method parameter can be ignored. Note that this does not
  // imply that the method parameter is unused and can be removed.
  private final Map<MethodParameter, ComputationTreeNode> ignoreComposableArgumentConditions =
      new ConcurrentHashMap<>();

  // Set of virtual methods that can definitely be optimized.
  //
  // We conservatively exclude virtual methods with dynamic dispatch from this set, since the
  // parameters of such methods can only be removed if the same parameter can be removed from all
  // other virtual methods with the same signature in the class hierarchy.
  private final ProgramMethodSet optimizableVirtualMethods = ProgramMethodSet.createConcurrent();

  public EffectivelyUnusedArgumentsAnalysis(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
  }

  public ComputationTreeNode getEffectivelyUnusedCondition(MethodParameter methodParameter) {
    if (ignoreComposableArgumentConditions.containsKey(methodParameter)) {
      assert appView.options().callSiteOptimizationOptions().isComposableArgumentRemovalEnabled();
      return ignoreComposableArgumentConditions.get(methodParameter);
    }
    return conditions.getOrDefault(methodParameter, AbstractValue.unknown());
  }

  public void initializeOptimizableVirtualMethods(Set<DexProgramClass> stronglyConnectedComponent) {
    // Group all virtual methods in this strongly connected component by their signature.
    Map<DexMethodSignature, ProgramMethodSet> methodsBySignature = new HashMap<>();
    for (DexProgramClass clazz : stronglyConnectedComponent) {
      clazz.forEachProgramVirtualMethod(
          method -> {
            ProgramMethodSet methodsWithSameSignature =
                methodsBySignature.computeIfAbsent(
                    method.getMethodSignature(), ignoreKey(ProgramMethodSet::create));
            methodsWithSameSignature.add(method);
          });
    }
    // Mark the unique method signatures as being optimizable.
    methodsBySignature.forEach(
        (signature, methodsWithSignature) -> {
          if (methodsWithSignature.size() == 1) {
            ProgramMethod method = methodsWithSignature.getFirst();
            if (ParameterRemovalUtils.canRemoveUnusedParametersFrom(appView, method)) {
              optimizableVirtualMethods.add(method);
            }
          }
        });
  }

  public void scan(
      ProgramMethod method, IRCode code, PathConstraintSupplier pathConstraintSupplier) {
    new Analyzer(method, code, pathConstraintSupplier).analyze();
  }

  public void computeEffectivelyUnusedArguments(ProgramMethodSet prunedMethods) {
    // Build a graph where nodes are method parameters and there is an edge from method parameter p0
    // to method parameter p1 if the removal of p0 depends on the removal of p1.
    EffectivelyUnusedArgumentsGraph dependenceGraph =
        EffectivelyUnusedArgumentsGraph.create(appView, conditions, constraints, prunedMethods);

    // Remove all unoptimizable method parameters from the graph, as well as all nodes that depend
    // on a node that is unoptimable.
    dependenceGraph.removeUnoptimizableNodes();

    // Repeatedly mark method parameters with no outgoing edges (i.e., no dependencies) as being
    // unused.
    WorkList<EffectivelyUnusedArgumentsGraphNode> worklist =
        WorkList.newIdentityWorkList(dependenceGraph.getNodes());
    while (!worklist.isEmpty()) {
      while (!worklist.isEmpty()) {
        EffectivelyUnusedArgumentsGraphNode node = worklist.removeSeen();
        assert dependenceGraph.verifyContains(node);
        node.removeUnusedSuccessors();
        if (node.getSuccessors().isEmpty()) {
          node.setUnused();
          node.getPredecessors().forEach(worklist::addIfNotSeen);
          node.cleanForRemoval();
          dependenceGraph.remove(node);
        }
      }

      // Handle mutually recursive methods. If there is a cycle p0 -> p1 -> ... -> pn -> p0 and each
      // of the method parameters p0 ... pn has a unique successor, then remove the edge pn -> p0
      // from the graph.
      dependenceGraph.removeClosedCycles(worklist::addIfNotSeen);
    }
  }

  public void onMethodPruned(ProgramMethod method) {
    onMethodCodePruned(method);
  }

  public void onMethodCodePruned(ProgramMethod method) {
    for (int argumentIndex = 0;
        argumentIndex < method.getDefinition().getNumberOfArguments();
        argumentIndex++) {
      MethodParameter methodParameter = new MethodParameter(method, argumentIndex);
      conditions.remove(methodParameter);
      constraints.remove(methodParameter);
    }
  }

  private class Analyzer {

    private final ProgramMethod method;
    private final IRCode code;
    private final PathConstraintSupplier pathConstraintSupplier;

    private Set<BasicBlock> skipToGroupEndBlocks;

    private Analyzer(
        ProgramMethod method, IRCode code, PathConstraintSupplier pathConstraintSupplier) {
      this.method = method;
      this.code = code;
      this.pathConstraintSupplier = pathConstraintSupplier;
    }

    void analyze() {
      // If this method is not subject to optimization, then don't compute effectively unused
      // constraints for the method parameters.
      if (isUnoptimizable(method)) {
        return;
      }
      Iterator<Argument> argumentIterator = code.argumentIterator();
      while (argumentIterator.hasNext()) {
        Argument argument = argumentIterator.next();
        Value argumentValue = argument.outValue();
        computeEffectivelyUnusedConstraints(
            argument,
            argumentValue,
            effectivelyUnusedCondition -> {
              MethodParameter methodParameter = new MethodParameter(method, argument.getIndex());
              assert !conditions.containsKey(methodParameter);
              conditions.put(methodParameter, effectivelyUnusedCondition);
            },
            effectivelyUnusedConstraints -> {
              MethodParameter methodParameter = new MethodParameter(method, argument.getIndex());
              assert !constraints.containsKey(methodParameter);
              constraints.put(methodParameter, effectivelyUnusedConstraints);
            });
      }
    }

    private void computeEffectivelyUnusedConstraints(
        Argument argument,
        Value argumentValue,
        Consumer<ComputationTreeNode> effectivelyUnusedConditionsConsumer,
        Consumer<Set<MethodParameter>> effectivelyUnusedConstraintsConsumer) {
      if (!ParameterRemovalUtils.canRemoveUnusedParameter(appView, method, argument.getIndex())) {
        return;
      }
      if (argumentValue.hasDebugUsers()) {
        return;
      }
      Value usedValue;
      if (argumentValue.hasSingleUniquePhiUser()) {
        // If the argument has one or more phi users, we check if there is a single phi due to a
        // default value for the argument. If so, we record this condition and mark the users of the
        // phi as effectively unused argument constraints for the current argument.
        if (!computeEffectivelyUnusedCondition(
            argumentValue, effectivelyUnusedConditionsConsumer)) {
          return;
        }
        Phi user = argumentValue.singleUniquePhiUser();
        if (user.hasDebugUsers() || user.hasPhiUsers()) {
          return;
        }
        usedValue = user;
      } else if (argumentValue.hasPhiUsers()) {
        computeIgnoreComposableArgumentCondition(argument, argumentValue);
        return;
      } else {
        usedValue = argumentValue;
      }
      Set<MethodParameter> effectivelyUnusedConstraints = new HashSet<>();
      for (Instruction user : usedValue.uniqueUsers()) {
        if (user.isInvokeMethod()) {
          InvokeMethod invoke = user.asInvokeMethod();
          ProgramMethod resolvedMethod =
              appView
                  .appInfo()
                  .unsafeResolveMethodDueToDexFormatLegacy(invoke.getInvokedMethod())
                  .getResolvedProgramMethod();
          if (resolvedMethod == null || isUnoptimizable(resolvedMethod)) {
            return;
          }
          int dependentArgumentIndex =
              ListUtils.uniqueIndexMatching(invoke.arguments(), value -> value == argumentValue);
          if (dependentArgumentIndex < 0
              || !ParameterRemovalUtils.canRemoveUnusedParameter(
                  appView, resolvedMethod, dependentArgumentIndex)) {
            return;
          }
          effectivelyUnusedConstraints.add(
              new MethodParameter(resolvedMethod, dependentArgumentIndex));
        } else {
          return;
        }
      }
      if (!effectivelyUnusedConstraints.isEmpty()) {
        effectivelyUnusedConstraintsConsumer.accept(effectivelyUnusedConstraints);
      }
    }

    private boolean computeEffectivelyUnusedCondition(
        Value argumentValue, Consumer<ComputationTreeNode> effectivelyUnusedConditionsConsumer) {
      assert argumentValue.hasSingleUniquePhiUser();
      if (argumentValue.hasUsers()) {
        return false;
      }
      Phi phi = argumentValue.singleUniquePhiUser();
      ComputationTreeNode condition = getUnusedCondition(argumentValue, phi);
      if (condition == null) {
        return false;
      }
      effectivelyUnusedConditionsConsumer.accept(condition);
      return true;
    }

    private ComputationTreeUnopCompareNode getUnusedCondition(Value argumentValue, Phi phi) {
      if (phi.getOperands().size() != 2) {
        return null;
      }
      BasicBlock block = phi.getBlock();
      ConcretePathConstraintAnalysisState leftState =
          pathConstraintSupplier.getPathConstraint(block.getPredecessor(0)).asConcreteState();
      ConcretePathConstraintAnalysisState rightState =
          pathConstraintSupplier.getPathConstraint(block.getPredecessor(1)).asConcreteState();
      if (leftState == null || rightState == null) {
        return null;
      }
      // Find a condition that can be used to distinguish program paths coming from the two
      // predecessors.
      ComputationTreeNode condition = leftState.getDifferentiatingPathConstraint(rightState);
      if (condition == null || !condition.isArgumentBitSetCompareNode()) {
        return null;
      }
      // Extract the state corresponding to the program path where the argument is unused. If the
      // condition evaluates to false on this program path then negate the condition.
      ComputationTreeUnopCompareNode compareCondition = (ComputationTreeUnopCompareNode) condition;
      ConcretePathConstraintAnalysisState unusedState =
          phi.getOperand(0) == argumentValue ? rightState : leftState;
      if (unusedState.isNegated(compareCondition)) {
        compareCondition = compareCondition.negate();
      }
      return compareCondition;
    }

    private void computeIgnoreComposableArgumentCondition(Argument argument, Value argumentValue) {
      if (!appView.options().callSiteOptimizationOptions().isComposableArgumentRemovalEnabled()
          || !appView.getComposeReferences().isComposable(method)) {
        return;
      }
      Phi phi = getSingleUniquePhiUserInComposableIgnoringSkipToGroupEndPaths(argumentValue);
      if (phi == null) {
        return;
      }
      ComputationTreeUnopCompareNode condition = getUnusedCondition(argumentValue, phi);
      if (condition == null) {
        return;
      }
      for (Instruction user : argumentValue.uniqueUsers()) {
        ConcretePathConstraintAnalysisState pathConstraint =
            pathConstraintSupplier.getPathConstraint(user.getBlock()).asConcreteState();
        if (pathConstraint == null) {
          return;
        }
        pathConstraint.ensureHashMapBacking();
        // Check that the condition being true implies that this use is unreachable.
        if (pathConstraint.getKind(condition) == PathConstraintKind.NEGATIVE
            || pathConstraint.getKind(condition.negate()) == PathConstraintKind.POSITIVE) {
          continue;
        }
        return;
      }
      ignoreComposableArgumentConditions.put(
          new MethodParameter(method, argument.getIndex()), condition);
    }

    private Phi getSingleUniquePhiUserInComposableIgnoringSkipToGroupEndPaths(Value argumentValue) {
      assert appView.options().callSiteOptimizationOptions().isComposableArgumentRemovalEnabled();
      assert appView.getComposeReferences().isComposable(method);
      Phi result = null;
      for (Phi phi : argumentValue.uniquePhiUsers()) {
        for (int operandIndex = 0; operandIndex < phi.getOperands().size(); operandIndex++) {
          Value operand = phi.getOperand(operandIndex);
          if (operand != argumentValue
              || ignoreComposablePath(phi.getBlock().getPredecessor(operandIndex))) {
            continue;
          }
          if (result != null) {
            return null;
          }
          result = phi;
          break;
        }
      }
      return result;
    }

    private boolean ignoreComposablePath(BasicBlock block) {
      assert appView.options().callSiteOptimizationOptions().isComposableArgumentRemovalEnabled();
      assert appView.getComposeReferences().isComposable(method);
      if (skipToGroupEndBlocks == null) {
        skipToGroupEndBlocks = Sets.newIdentityHashSet();
        LazyDominatorTree dominatorTree = new LazyDominatorTree(code);
        ComposeReferences references = appView.getComposeReferences();
        for (InvokeVirtual invoke :
            code.<InvokeVirtual>instructions(Instruction::isInvokeVirtual)) {
          if (invoke.getInvokedMethod().getName().isIdenticalTo(references.skipToGroupEndName)) {
            skipToGroupEndBlocks.addAll(dominatorTree.get().dominatedBlocks(invoke.getBlock()));
          }
        }
      }
      return skipToGroupEndBlocks.contains(block);
    }

    private boolean isUnoptimizable(ProgramMethod method) {
      if (method.getDefinition().belongsToDirectPool()) {
        return !ParameterRemovalUtils.canRemoveUnusedParametersFrom(appView, method);
      }
      if (optimizableVirtualMethods.contains(method)) {
        assert ParameterRemovalUtils.canRemoveUnusedParametersFrom(appView, method);
        return false;
      }
      return true;
    }
  }
}
