// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize;

import com.android.tools.r8.graph.AccessControl;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.MethodResolutionResult.SingleResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.ClassInitializationAnalysis;
import com.android.tools.r8.ir.analysis.type.ClassTypeElement;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InvokeDirect;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.optimize.Inliner.InlineAction;
import com.android.tools.r8.ir.optimize.Inliner.InlineResult;
import com.android.tools.r8.ir.optimize.inliner.InliningIRProvider;
import com.android.tools.r8.ir.optimize.inliner.WhyAreYouNotInliningReporter;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.timing.Timing;

/**
 * The InliningOracle contains information needed for when inlining other methods into @method.
 */
public interface InliningOracle {

  AppView<AppInfoWithLiveness> appView();

  boolean canInlineInstanceInitializer(
      InlineAction.Builder actionBuilder,
      IRCode code,
      InvokeDirect invoke,
      ProgramMethod singleTarget,
      InliningIRProvider inliningIRProvider,
      WhyAreYouNotInliningReporter whyAreYouNotInliningReporter);

  InlineResult computeInlining(
      IRCode code,
      InvokeMethod invoke,
      SingleResolutionResult<?> resolutionResult,
      ProgramMethod singleTarget,
      ProgramMethod context,
      ClassInitializationAnalysis classInitializationAnalysis,
      InliningIRProvider inliningIRProvider,
      Timing timing,
      WhyAreYouNotInliningReporter whyAreYouNotInliningReporter);

  default DexProgramClass getDowncastTypeIfNeeded(InvokeMethod invoke, ProgramMethod target) {
    if (invoke.isInvokeMethodWithReceiver()) {
      // If the invoke has a receiver but the actual type of the receiver is different from the
      // computed target holder, inlining requires a downcast of the receiver. In case we don't know
      // the exact type of the receiver we use the static type of the receiver.
      Value receiver = invoke.asInvokeMethodWithReceiver().getReceiver();
      if (!receiver.getType().isClassType()) {
        return target.getHolder();
      }

      ClassTypeElement receiverType =
          getReceiverTypeOrDefault(invoke, receiver.getType().asClassType());
      ClassTypeElement targetType = target.getHolderType().toTypeElement(appView()).asClassType();
      if (!receiverType.lessThanOrEqualUpToNullability(targetType, appView())) {
        return target.getHolder();
      }
    }
    return null;
  }

  ClassTypeElement getReceiverTypeOrDefault(InvokeMethod invoke, ClassTypeElement defaultValue);

  boolean isForcedInliningOracle();

  // TODO(b/142116551): This should be equivalent to invoke.lookupSingleTarget(appView, context)!
  ProgramMethod lookupSingleTarget(InvokeMethod invoke, ProgramMethod context);

  /** Inform the strategy that the inlinee has been inlined. */
  void markInlined(IRCode inlinee);

  boolean passesInliningConstraints(
      IRCode code,
      SingleResolutionResult<?> resolutionResult,
      ProgramMethod candidate,
      WhyAreYouNotInliningReporter whyAreYouNotInliningReporter);

  default boolean setDowncastTypeIfNeeded(
      AppView<AppInfoWithLiveness> appView,
      InlineAction.Builder actionBuilder,
      InvokeMethod invoke,
      ProgramMethod singleTarget,
      ProgramMethod context) {
    DexProgramClass downcastClass = getDowncastTypeIfNeeded(invoke, singleTarget);
    if (downcastClass != null) {
      if (AccessControl.isClassAccessible(downcastClass, context, appView).isPossiblyFalse()) {
        return false;
      }
      actionBuilder.setDowncastClass(downcastClass);
    }
    return true;
  }

  /** Return true if there is still budget for inlining into this method. */
  boolean stillHasBudget(
      InlineAction action, WhyAreYouNotInliningReporter whyAreYouNotInliningReporter);

  /**
   * Check if the inlinee will exceed the the budget for inlining size into current method.
   *
   * <p>Return true if the strategy will *not* allow inlining.
   */
  boolean willExceedBudget(
      InlineAction action,
      IRCode code,
      IRCode inlinee,
      InvokeMethod invoke,
      BasicBlock block,
      WhyAreYouNotInliningReporter whyAreYouNotInliningReporter);
}
