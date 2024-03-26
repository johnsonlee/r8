// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.backports;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ir.desugar.desugaredlibrary.ApiLevelRange;
import com.android.tools.r8.utils.AndroidApiLevel;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ApiLevelRangeTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public ApiLevelRangeTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testRange() {
    ApiLevelRange rs = new ApiLevelRange(AndroidApiLevel.S, AndroidApiLevel.R);
    ApiLevelRange op = new ApiLevelRange(AndroidApiLevel.P, AndroidApiLevel.O);
    ApiLevelRange ir = new ApiLevelRange(AndroidApiLevel.R, AndroidApiLevel.I);
    ApiLevelRange r = new ApiLevelRange(AndroidApiLevel.R, null);
    ApiLevelRange i = new ApiLevelRange(AndroidApiLevel.I, null);

    // Overlap with null.
    assertTrue(i.overlap(r));
    assertTrue(r.overlap(i));

    // No overlap with null.
    assertFalse(i.overlap(rs));
    assertFalse(rs.overlap(i));

    // No overlap.
    assertFalse(rs.overlap(op));
    assertFalse(op.overlap(rs));

    // Partial overlap.
    assertTrue(ir.overlap(rs));
    assertTrue(rs.overlap(ir));

    // Full overlap.
    assertTrue(ir.overlap(op));
    assertTrue(op.overlap(ir));
  }
}
