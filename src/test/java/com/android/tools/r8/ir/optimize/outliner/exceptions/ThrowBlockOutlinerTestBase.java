// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.outliner.exceptions;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.utils.BooleanBox;
import com.android.tools.r8.utils.InternalOptions;
import java.util.Collection;
import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public abstract class ThrowBlockOutlinerTestBase extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  private final BooleanBox receivedCallback = new BooleanBox();

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimesAndAllApiLevels().build();
  }

  @Before
  public void before() {
    receivedCallback.unset();
  }

  @After
  public void after() {
    assertTrue(receivedCallback.isTrue());
  }

  public void configure(InternalOptions options) {
    assertFalse(options.getThrowBlockOutlinerOptions().enable);
    options.getThrowBlockOutlinerOptions().enable = true;
    options.getThrowBlockOutlinerOptions().outlineConsumerForTesting =
        outlines -> {
          inspectOutlines(outlines, options.dexItemFactory());
          receivedCallback.set();
        };
    options.getThrowBlockOutlinerOptions().outlineStrategyForTesting = this::shouldOutline;
  }

  public abstract void inspectOutlines(
      Collection<ThrowBlockOutline> outlineCollection, DexItemFactory factory);

  public boolean shouldOutline(ThrowBlockOutline outline) {
    return outline.getNumberOfUsers() >= 2;
  }
}
