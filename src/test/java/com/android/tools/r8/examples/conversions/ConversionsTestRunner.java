// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.examples.conversions;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.examples.ExamplesTestBase;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ConversionsTestRunner extends ExamplesTestBase {

  public ConversionsTestRunner(TestParameters parameters) {
    super(parameters);
  }

  @Override
  public Class<?> getMainClass() {
    return Conversions.class;
  }

  @Override
  public String getExpected() {
    return StringUtils.lines(
        "1", "1.0", "1.0", "1", "1.0", "1.0", "1", "1", "1.0", "1", "1", "1.0", "1", "\u0001", "1");
  }
}
