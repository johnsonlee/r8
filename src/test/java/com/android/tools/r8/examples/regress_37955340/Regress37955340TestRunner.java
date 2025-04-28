// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.examples.regress_37955340;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.examples.ExamplesTestBase;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class Regress37955340TestRunner extends ExamplesTestBase {

  public Regress37955340TestRunner(TestParameters parameters) {
    super(parameters);
  }

  @Override
  public Class<?> getMainClass() {
    return Regress.class;
  }

  @Override
  public List<Class<?>> getTestClasses() throws Exception {
    return ImmutableList.of(getMainClass(), Regress.A.class, Regress.B.class);
  }

  @Override
  public String getExpected() {
    return StringUtils.lines("2");
  }
}
