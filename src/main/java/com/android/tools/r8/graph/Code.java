// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.android.tools.r8.dex.MixedSectionCollection;
import com.android.tools.r8.dex.code.CfOrDexInstruction;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.bytecodemetadata.BytecodeInstructionMetadata;
import com.android.tools.r8.graph.bytecodemetadata.BytecodeMetadata;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.graph.proto.RewrittenPrototypeDescription;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.NumberGenerator;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.Position.OutlineCallerPosition;
import com.android.tools.r8.ir.code.Position.PositionBuilder;
import com.android.tools.r8.ir.conversion.MethodConversionOptions;
import com.android.tools.r8.ir.conversion.MethodConversionOptions.MutableMethodConversionOptions;
import com.android.tools.r8.lightir.LirCode;
import com.android.tools.r8.utils.Int2StructuralItemArrayMap;
import com.android.tools.r8.utils.RetracerForCodePrinting;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import java.util.function.Consumer;

public abstract class Code extends CachedHashValueDexItem {

  public final IRCode buildIR(ProgramMethod method, AppView<?> appView) {
    return buildIR(method, appView, MethodConversionOptions.forLirPhase(appView));
  }

  public abstract IRCode buildIR(
      ProgramMethod method,
      AppView<?> appView,
      MutableMethodConversionOptions conversionOptions);

  public IRCode buildInliningIR(
      ProgramMethod context,
      ProgramMethod method,
      AppView<?> appView,
      GraphLens codeLens,
      NumberGenerator valueNumberGenerator,
      Position callerPosition,
      RewrittenPrototypeDescription protoChanges) {
    throw new Unreachable("Unexpected attempt to build IR graph for inlining from: "
        + getClass().getCanonicalName());
  }

  public boolean hasExplicitCodeLens() {
    return false;
  }

  public GraphLens getCodeLens(AppView<?> appView) {
    return appView.codeLens();
  }

  public BytecodeMetadata<? extends CfOrDexInstruction> getMetadata() {
    return null;
  }

  public BytecodeInstructionMetadata getMetadata(CfOrDexInstruction instruction) {
    return null;
  }

  public void clearMetadata() {}

  public abstract void registerCodeReferences(ProgramMethod method, UseRegistry registry);

  public abstract void registerCodeReferencesForDesugaring(
      ClasspathMethod method, UseRegistry registry);

  public void registerArgumentReferences(DexEncodedMethod method, ArgumentUse registry) {
    throw new Unreachable();
  }

  public Int2ReferenceMap<DebugLocalInfo> collectParameterInfo(
      DexEncodedMethod encodedMethod, AppView<?> appView) {
    throw new Unreachable();
  }

  @Override
  public abstract String toString();

  public abstract String toString(DexEncodedMethod method, RetracerForCodePrinting retracer);

  public boolean isCfCode() {
    return false;
  }

  public boolean isLazyCfCode() {
    return false;
  }

  public boolean isCfWritableCode() {
    return false;
  }

  public boolean isDefaultInstanceInitializerCode() {
    return false;
  }

  public DefaultInstanceInitializerCode asDefaultInstanceInitializerCode() {
    return null;
  }

  public boolean isDexCode() {
    return false;
  }

  public boolean isDexWritableCode() {
    return false;
  }

  public boolean isHorizontalClassMergerCode() {
    return false;
  }

  public boolean isIncompleteHorizontalClassMergerCode() {
    return false;
  }

  public boolean isOutlineCode() {
    return false;
  }

  public boolean isSharedCodeObject() {
    return false;
  }

  public boolean isThrowNullCode() {
    return false;
  }

  public boolean hasMonitorInstructions() {
    return false;
  }

  public boolean isThrowExceptionCode() {
    return false;
  }

  public ThrowNullCode asThrowNullCode() {
    return null;
  }

  public ThrowExceptionCode asThrowExceptionCode() {
    return null;
  }

  /** Estimate the number of IR instructions emitted by buildIR(). */
  public final int estimatedSizeForInlining() {
    return getEstimatedSizeForInliningIfLessThanOrEquals(Integer.MAX_VALUE);
  }

  /** Compute estimatedSizeForInlining() <= threshold. */
  public int getEstimatedSizeForInliningIfLessThanOrEquals(int threshold) {
    throw new Unreachable(getClass().getName());
  }

  public final boolean estimatedSizeForInliningAtMost(int threshold) {
    return getEstimatedSizeForInliningIfLessThanOrEquals(threshold) >= 0;
  }

  public abstract int estimatedDexCodeSizeUpperBoundInBytes();

  public final boolean isLirCode() {
    return asLirCode() != null;
  }

  public LirCode<Integer> asLirCode() {
    return null;
  }

  public CfCode asCfCode() {
    throw new Unreachable(getClass().getCanonicalName() + ".asCfCode()");
  }

  public CfWritableCode asCfWritableCode() {
    throw new Unreachable(getClass().getCanonicalName() + ".asCfWritableCode()");
  }

  public LazyCfCode asLazyCfCode() {
    return null;
  }

  public DexCode asDexCode() {
    return null;
  }

  public DexWritableCode asDexWritableCode() {
    throw new Unreachable(getClass().getCanonicalName() + ".asDexWritableCode()");
  }

  @Override
  protected void collectMixedSectionItems(MixedSectionCollection collection) {
    throw new Unreachable();
  }

  public abstract boolean isEmptyVoidMethod();

  public boolean verifyNoInputReaders() {
    return true;
  }

  public Code getCodeAsInlining(
      DexMethod caller,
      boolean isCallerD8R8Synthesized,
      DexMethod callee,
      boolean isCalleeD8R8Synthesized,
      DexItemFactory factory) {
    throw new Unreachable();
  }

  public static Position newInlineePosition(
      Position callerPosition, Position calleePosition, boolean isCalleeD8R8Synthesized) {
    Position outermostCallee = calleePosition.getOutermostCaller();
    // If the callee is not synthetic, then just append the frame.
    assert outermostCallee.isD8R8Synthesized() == isCalleeD8R8Synthesized;
    if (!isCalleeD8R8Synthesized) {
      assert !outermostCallee.isOutline();
      return calleePosition.withOutermostCallerPosition(callerPosition);
    }
    // We can replace the position since the callee was synthesized by the compiler, however, if
    // the position carries special information we need to copy it.
    if (!outermostCallee.isOutline() && !outermostCallee.isRemoveInnerFramesIfThrowingNpe()) {
      return calleePosition.replacePosition(outermostCallee, callerPosition);
    }
    // If the position is inlining an outline then both frames are to be replaced by the
    // translation of the line positions.
    if (callerPosition.isOutlineCaller() && outermostCallee.isOutline()) {
      OutlineCallerPosition outlineCaller = callerPosition.asOutlineCaller();
      Int2StructuralItemArrayMap<Position> translation = outlineCaller.getOutlinePositions();
      // Map the synthetic line found in the outline to the actual position as encoded in the
      // outline-caller position table. Note that the outline may have more lines than the table
      // if multiple outline instructions translate to the same original caller positions.
      int outlineLine = outermostCallee.getLine();
      Position translatedPosition = null;
      for (int i = 0; i <= outlineLine; i++) {
        Position result = translation.lookup(i);
        if (result != null) {
          translatedPosition = result;
        }
      }
      assert translatedPosition != null;
      // If the caller has outer frames compose them with the translated position.
      if (callerPosition.hasCallerPosition()) {
        translatedPosition =
            translatedPosition.withOutermostCallerPosition(callerPosition.getCallerPosition());
      }
      // If the outline has additional inner frames append them as inner frames on the translation.
      if (calleePosition.hasCallerPosition()) {
        translatedPosition = calleePosition.replacePosition(outermostCallee, translatedPosition);
      }
      return translatedPosition;
    }

    assert !callerPosition.isOutline();
    // Copy the callee frame to ensure transfer of the outline key if present.
    PositionBuilder<?, ?> newCallerBuilder =
        outermostCallee
            .builderWithCopy()
            .setIsD8R8Synthesized(callerPosition.isD8R8Synthesized())
            .setMethod(callerPosition.getMethod())
            .setCallerPosition(callerPosition.getCallerPosition());
    // If the callee is an outline, the line must be that of the outline to maintain the positions.
    if (outermostCallee.isOutline()) {
      // This does not implement inlining an outline. The cases this hits should always be a full
      // "move as inlining" to be correct.
      assert !callerPosition.isOutlineCaller();
      assert callerPosition.isD8R8Synthesized();
      assert callerPosition.getLine() == 0;
    } else {
      newCallerBuilder.setLine(outermostCallee.getLine());
    }
    // Transfer other info from the caller.
    if (callerPosition.isRemoveInnerFramesIfThrowingNpe()) {
      newCallerBuilder.setRemoveInnerFramesIfThrowingNpe(true);
    }
    return calleePosition.replacePosition(outermostCallee, newCallerBuilder.build());
  }

  public void forEachPosition(
      DexMethod method, boolean isD8R8Synthesized, Consumer<Position> positionConsumer) {
    // Intentionally empty. Override where we have fully build CF or DEX code.
  }

  public boolean supportsPendingInlineFrame() {
    return false;
  }
}
