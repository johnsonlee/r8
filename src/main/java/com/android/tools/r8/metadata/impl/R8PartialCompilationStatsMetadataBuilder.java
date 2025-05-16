// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.metadata.impl;

import static com.android.tools.r8.utils.AssertionUtils.checkNotNegative;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.partial.R8PartialD8Input;
import com.android.tools.r8.partial.R8PartialSubCompilationConfiguration.R8PartialR8SubCompilationConfiguration;
import com.android.tools.r8.utils.IntBox;
import com.android.tools.r8.utils.InternalOptions;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;

public class R8PartialCompilationStatsMetadataBuilder {

  private final int numberOfExcludedClassesInInput;
  private final int numberOfIncludedClassesInInput;

  private int dexCodeSizeOfExcludedClassesInBytes = -1;
  private int dexCodeSizeOfIncludedClassesInBytes = -1;
  private int numberOfIncludedClassesInOutput = -1;

  private R8PartialCompilationStatsMetadataBuilder(R8PartialD8Input input) {
    this.numberOfExcludedClassesInInput = input.getD8Classes().size();
    this.numberOfIncludedClassesInInput = input.getR8Classes().size();
  }

  public static R8PartialCompilationStatsMetadataBuilder create(
      R8PartialD8Input input, InternalOptions options) {
    return options.r8BuildMetadataConsumer != null
        ? new R8PartialCompilationStatsMetadataBuilder(input)
        : null;
  }

  public void finalizeStats(AppView<? extends AppInfoWithClassHierarchy> appView) {
    R8PartialR8SubCompilationConfiguration subCompilationConfiguration =
        appView.options().partialSubCompilationConfiguration.asR8();
    dexCodeSizeOfExcludedClassesInBytes =
        sumDexCodeSizeInBytes(appView, subCompilationConfiguration::isD8Definition);
    dexCodeSizeOfIncludedClassesInBytes =
        sumDexCodeSizeInBytes(appView, subCompilationConfiguration::isR8Definition);
    numberOfIncludedClassesInOutput =
        appView.appInfo().classes().size()
            - subCompilationConfiguration.getDexingOutputClasses().size();
  }

  private static int sumDexCodeSizeInBytes(
      AppView<? extends AppInfoWithClassHierarchy> appView, Predicate<DexProgramClass> predicate) {
    IntBox dexCodeSizeInBytes = new IntBox();
    for (DexProgramClass clazz : Iterables.filter(appView.appInfo().classes(), predicate)) {
      clazz.forEachProgramMethodMatching(
          DexEncodedMethod::hasCode,
          method ->
              dexCodeSizeInBytes.increment(
                  method.getDefinition().getCode().asDexWritableCode().codeSizeInBytes()));
    }
    return dexCodeSizeInBytes.get();
  }

  public R8PartialCompilationStatsMetadataImpl build() {
    // Verify that finalizeStats has been called.
    return new R8PartialCompilationStatsMetadataImpl(
        checkNotNegative(dexCodeSizeOfExcludedClassesInBytes),
        checkNotNegative(dexCodeSizeOfIncludedClassesInBytes),
        numberOfExcludedClassesInInput,
        numberOfIncludedClassesInInput,
        checkNotNegative(numberOfIncludedClassesInOutput));
  }
}
