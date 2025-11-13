// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.outliner.bottomup;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestCompilerBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.utils.BooleanBox;
import java.util.Collection;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public abstract class BottomUpOutlinerTestBase extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public CompilationMode mode;

  private final BooleanBox receivedCallback = new BooleanBox();

  @Parameters(name = "{0}, mode: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimesAndAllApiLevels().build(), CompilationMode.values());
  }

  @Before
  public void before() {
    receivedCallback.unset();
  }

  @After
  public void after() {
    assertTrue(receivedCallback.isTrue());
  }

  public void assumeRelease() {
    if (mode.isDebug()) {
      receivedCallback.set();
    }
    assumeTrue(mode.isRelease());
  }

  public void configure(
      TestCompilerBuilder<?, ?, ?, ? extends SingleTestRunResult<?>, ?> testBuilder) {
    testBuilder
        .addOptionsModification(
            options -> {
              BottomUpOutlinerOptions outlinerOptions = options.getBottomUpOutlinerOptions();
              assertTrue(outlinerOptions.enable);
              outlinerOptions.forceDebug = true;
              outlinerOptions.outlineConsumerForTesting =
                  outlines -> {
                    inspectOutlines(outlines, options.dexItemFactory());
                    receivedCallback.set();
                  };
              outlinerOptions.outlineStrategyForTesting = this::shouldOutline;
            })
        .applyIfR8(
            b ->
                b.addOptionsModification(
                    options -> options.desugarSpecificOptions().minimizeSyntheticNames = true))
        .collectSyntheticItems()
        .setMode(mode);
  }

  public abstract void inspectOutlines(
      Collection<Outline> outlineCollection, DexItemFactory factory);

  public boolean shouldOutline(Outline outline) {
    return outline.getNumberOfUsers() >= 2;
  }
}
