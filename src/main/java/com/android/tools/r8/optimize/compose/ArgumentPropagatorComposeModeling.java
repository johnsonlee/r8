// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.compose;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.MethodResolutionResult.SingleResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.analysis.value.SingleNumberValue;
import com.android.tools.r8.ir.code.InstanceGet;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.code.Or;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcretePrimitiveTypeValueState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.FieldValue;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.NonEmptyValueState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.OrAbstractFunction;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.BooleanUtils;
import com.google.common.collect.Iterables;

public class ArgumentPropagatorComposeModeling {

  private final AppView<AppInfoWithLiveness> appView;
  private final ComposeReferences rewrittenComposeReferences;

  private final DexType rewrittenFunction2Type;
  private final DexString invokeName;

  public ArgumentPropagatorComposeModeling(AppView<AppInfoWithLiveness> appView) {
    assert appView
        .options()
        .getJetpackComposeOptions()
        .isModelingChangedArgumentsToComposableFunctions();
    this.appView = appView;
    this.rewrittenComposeReferences =
        appView
            .getComposeReferences()
            .rewrittenWithLens(appView.graphLens(), GraphLens.getIdentityLens());
    DexItemFactory dexItemFactory = appView.dexItemFactory();
    this.rewrittenFunction2Type =
        appView
            .graphLens()
            .lookupType(
                dexItemFactory.createType("Lkotlin/jvm/functions/Function2;"),
                GraphLens.getIdentityLens());
    this.invokeName = dexItemFactory.createString("invoke");
  }

  /**
   * Models calls to @Composable functions from Compose restart lambdas.
   *
   * <p>The @Composable functions are static and should have one of the following two parameter
   * lists:
   *
   * <ol>
   *   <li>(..., Composable, int)
   *   <li>(..., Composable, int, int)
   * </ol>
   *
   * <p>The int argument after the Composable parameter is the $$changed parameter. The second int
   * argument after the Composable parameter (if present) is the $$default parameter.
   *
   * <p>The call to a @Composable function from its restart lambda follows the following code
   * pattern:
   *
   * <pre>
   *   MyComposableFunction(
   *       ..., this.composer, updateChangedFlags(this.$$changed) || 1, this.$$default)
   * </pre>
   *
   * <p>The modeling performed by this method assumes that updateChangedFlags() does not have any
   * impact on $$changed (see the current implementation below). The modeling also assumes that
   * this.$$changed and this.$$default are captures of the $$changed and $$default parameters of
   * the @Composable function.
   *
   * <pre>
   *   internal fun updateChangedFlags(flags: Int): Int {
   *     val lowBits = flags and changedLowBitMask
   *     val highBits = flags and changedHighBitMask
   *     return ((flags and changedMask) or
   *         (lowBits or (highBits shr 1)) or ((lowBits shl 1) and highBits))
   *   }
   * </pre>
   */
  public NonEmptyValueState modelParameterStateForChangedOrDefaultArgumentToComposableFunction(
      InvokeMethod invoke,
      ProgramMethod singleTarget,
      int argumentIndex,
      Value argument,
      ProgramMethod context) {
    // TODO(b/302483644): Add some robust way of detecting restart lambda contexts.
    if (!context.getHolder().getInterfaces().contains(rewrittenFunction2Type)
        || !invoke.getPosition().getOutermostCaller().getMethod().getName().isEqualTo(invokeName)
        || Iterables.isEmpty(
            context
                .getHolder()
                .instanceFields(
                    f -> f.getName().isIdenticalTo(rewrittenComposeReferences.changedFieldName)))) {
      return null;
    }

    // First check if this is an invoke to a @Composable function.
    if (singleTarget == null
        || !singleTarget
            .getDefinition()
            .annotations()
            .hasAnnotation(rewrittenComposeReferences.composableType)) {
      return null;
    }

    // The @Composable function is expected to be static and have >= 2 parameters.
    if (!invoke.isInvokeStatic()) {
      return null;
    }

    DexMethod invokedMethod = invoke.getInvokedMethod();
    if (invokedMethod.getArity() < 2) {
      return null;
    }

    // Check if the parameters list is one of (..., Composer, int) or (..., Composer, int, int).
    if (!invokedMethod.getParameter(invokedMethod.getArity() - 1).isIntType()) {
      return null;
    }

    boolean hasDefaultParameter =
        invokedMethod.getParameter(invokedMethod.getArity() - 2).isIntType();
    if (hasDefaultParameter && invokedMethod.getArity() < 3) {
      return null;
    }

    int composerParameterIndex =
        invokedMethod.getArity() - 2 - BooleanUtils.intValue(hasDefaultParameter);
    if (!invokedMethod
        .getParameter(composerParameterIndex)
        .isIdenticalTo(rewrittenComposeReferences.composerType)) {
      return null;
    }

    // We only model the $$changed argument to the @Composable function.
    if (argumentIndex != composerParameterIndex + 1) {
      return null;
    }

    assert argument.getType().isInt();

    DexField changedField =
        appView
            .dexItemFactory()
            .createField(
                context.getHolderType(),
                appView.dexItemFactory().intType,
                rewrittenComposeReferences.changedFieldName);

    UpdateChangedFlagsAbstractFunction inFlow = null;
    // We are looking at an argument to the $$changed parameter of the @Composable function.
    // We generally expect this argument to be defined by a call to updateChangedFlags().
    if (argument.isDefinedByInstructionSatisfying(Instruction::isInvokeStatic)) {
      InvokeStatic invokeStatic = argument.getDefinition().asInvokeStatic();
      SingleResolutionResult<?> resolutionResult =
          invokeStatic.resolveMethod(appView, context).asSingleResolution();
      if (resolutionResult == null) {
        return null;
      }
      DexClassAndMethod invokeSingleTarget =
          resolutionResult
              .lookupDispatchTarget(appView, invokeStatic, context)
              .getSingleDispatchTarget();
      if (invokeSingleTarget == null) {
        return null;
      }
      inFlow =
          invokeSingleTarget
              .getOptimizationInfo()
              .getAbstractFunction()
              .asUpdateChangedFlagsAbstractFunction();
      if (inFlow == null) {
        return null;
      }
      // By accounting for the abstract function we can safely strip the call.
      argument = invokeStatic.getFirstArgument();
    }
    // Allow the argument to be defined by `this.$$changed | 1`.
    if (argument.isDefinedByInstructionSatisfying(Instruction::isOr)) {
      Or or = argument.getDefinition().asOr();
      Value maybeNumberOperand = or.leftValue().isConstNumber() ? or.leftValue() : or.rightValue();
      Value otherOperand = or.getOperand(1 - or.inValues().indexOf(maybeNumberOperand));
      if (!maybeNumberOperand.isConstNumber(1)) {
        return null;
      }
      // Strip the OR instruction.
      argument = otherOperand;
      // Update the model from bottom to a special value that effectively throws away any known
      // information about the lowermost bit of $$changed.
      SingleNumberValue one =
          appView.abstractValueFactory().createSingleNumberValue(1, TypeElement.getInt());
      inFlow =
          new UpdateChangedFlagsAbstractFunction(
              new OrAbstractFunction(new FieldValue(changedField), one));
    } else {
      inFlow = new UpdateChangedFlagsAbstractFunction(new FieldValue(changedField));
    }

    // At this point we expect that the restart lambda is reading this.$$changed using an
    // instance-get.
    if (!argument.isDefinedByInstructionSatisfying(Instruction::isInstanceGet)) {
      return null;
    }

    // Check that the instance-get is reading the capture field that we expect it to.
    InstanceGet instanceGet = argument.getDefinition().asInstanceGet();
    if (!instanceGet.getField().isIdenticalTo(changedField)) {
      return null;
    }

    // Return the argument model.
    return new ConcretePrimitiveTypeValueState(inFlow);
  }
}
