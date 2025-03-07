// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.code;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ClasspathMethod;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.graph.bytecodemetadata.BytecodeMetadataProvider;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.graph.proto.RewrittenPrototypeDescription;
import com.android.tools.r8.horizontalclassmerging.ConstructorEntryPoint;
import com.android.tools.r8.horizontalclassmerging.HorizontalClassMergerGraphLens;
import com.android.tools.r8.horizontalclassmerging.IncompleteHorizontalClassMergerCode;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.NumberGenerator;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.Position.SyntheticPosition;
import com.android.tools.r8.ir.conversion.IRBuilder;
import com.android.tools.r8.ir.conversion.IRToLirFinalizer;
import com.android.tools.r8.ir.conversion.MethodConversionOptions;
import com.android.tools.r8.ir.conversion.MethodConversionOptions.MutableMethodConversionOptions;
import com.android.tools.r8.ir.conversion.SourceCode;
import com.android.tools.r8.lightir.LirCode;
import com.android.tools.r8.utils.RetracerForCodePrinting;
import com.android.tools.r8.utils.timing.Timing;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceSortedMap;

public class ConstructorEntryPointSynthesizedCode extends IncompleteHorizontalClassMergerCode {

  private final DexField classIdField;
  private int numberOfUnusedArguments;
  private final Int2ReferenceSortedMap<DexMethod> typeConstructors;

  public ConstructorEntryPointSynthesizedCode(
      Int2ReferenceSortedMap<DexMethod> typeConstructors,
      DexField classIdField,
      int numberOfUnusedArguments) {
    this.typeConstructors = typeConstructors;
    this.classIdField = classIdField;
    this.numberOfUnusedArguments = numberOfUnusedArguments;
  }

  @Override
  public void addExtraUnusedArguments(int numberOfUnusedArguments) {
    this.numberOfUnusedArguments += numberOfUnusedArguments;
  }

  private void registerReachableDefinitions(UseRegistry<?> registry) {
    assert registry.getTraversalContinuation().shouldContinue();
    for (DexMethod typeConstructor : typeConstructors.values()) {
      registry.registerInvokeDirect(typeConstructor);
      if (registry.getTraversalContinuation().shouldBreak()) {
        return;
      }
    }
  }

  @Override
  public boolean hasExplicitCodeLens() {
    return true;
  }

  @Override
  public GraphLens getCodeLens(AppView<?> appView) {
    return appView
        .graphLens()
        .asNonIdentityLens()
        .find(GraphLens::isHorizontalClassMergerGraphLens);
  }

  @Override
  public LirCode<Integer> toLirCode(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      ProgramMethod method,
      HorizontalClassMergerGraphLens lens) {
    for (Int2ReferenceMap.Entry<DexMethod> entry : typeConstructors.int2ReferenceEntrySet()) {
      entry.setValue(lens.getNextMethodSignature(entry.getValue()));
    }
    IRCode irCode = buildIR(method, appView);
    LirCode<Integer> lirCode =
        new IRToLirFinalizer(appView)
            .finalizeCode(irCode, BytecodeMetadataProvider.empty(), Timing.empty());
    return new LirCode<>(lirCode) {

      @Override
      public boolean hasExplicitCodeLens() {
        return true;
      }

      @Override
      public GraphLens getCodeLens(AppView<?> appView) {
        return lens;
      }
    };
  }

  @Override
  public final boolean isEmptyVoidMethod() {
    return false;
  }

  @Override
  public final IRCode buildIR(
      ProgramMethod method,
      AppView<?> appView,
      MutableMethodConversionOptions conversionOptions) {
    SyntheticPosition position =
        SyntheticPosition.builder()
            .setLine(0)
            .setMethod(method.getReference())
            .setIsD8R8Synthesized(true)
            .build();
    SourceCode sourceCode =
        new ConstructorEntryPoint(
            typeConstructors, method, classIdField, numberOfUnusedArguments, position);
    return IRBuilder.create(method, appView, sourceCode).build(method, conversionOptions);
  }

  @Override
  public final IRCode buildInliningIR(
      ProgramMethod context,
      ProgramMethod method,
      AppView<?> appView,
      GraphLens codeLens,
      NumberGenerator valueNumberGenerator,
      Position callerPosition,
      RewrittenPrototypeDescription protoChanges) {
    SourceCode sourceCode =
        new ConstructorEntryPoint(
            typeConstructors, method, classIdField, numberOfUnusedArguments, callerPosition);
    return IRBuilder.createForInlining(
            method, appView, codeLens, sourceCode, valueNumberGenerator, protoChanges)
        .build(context, MethodConversionOptions.nonConverting());
  }

  @Override
  public final String toString() {
    return toString(null, RetracerForCodePrinting.empty());
  }

  @Override
  public final void registerCodeReferences(ProgramMethod method, UseRegistry registry) {
    registerReachableDefinitions(registry);
  }

  @Override
  public final void registerCodeReferencesForDesugaring(
      ClasspathMethod method, UseRegistry registry) {
    registerReachableDefinitions(registry);
  }

  @Override
  protected final int computeHashCode() {
    throw new Unreachable();
  }

  @Override
  protected final boolean computeEquals(Object other) {
    throw new Unreachable();
  }

  @Override
  public final String toString(DexEncodedMethod method, RetracerForCodePrinting retracer) {
    return this.getClass().getSimpleName();
  }

  @Override
  public final int estimatedDexCodeSizeUpperBoundInBytes() {
    return Integer.MAX_VALUE;
  }
}
