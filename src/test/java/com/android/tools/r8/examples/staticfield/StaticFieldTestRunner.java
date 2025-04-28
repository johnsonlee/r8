// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.examples.staticfield;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.examples.ExamplesTestBase;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class StaticFieldTestRunner extends ExamplesTestBase {

  public StaticFieldTestRunner(TestParameters parameters) {
    super(parameters);
  }

  @Override
  public Class<?> getMainClass() {
    return StaticField.class;
  }

  @Override
  public String getExpected() {
    return StringUtils.lines("101010", "101010", "ABC", "ABC");
  }

  @Test
  @Override
  public void testDebug() throws Exception {
    // TODO(b/79671093): DEX has different line number info during stepping.
    parameters.assumeCfRuntime();
    super.testDebug();
  }
}
