// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.examples.loadconst;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.examples.ExamplesTestBase;
import com.android.tools.r8.examples.loop.UdpServer;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class UdpServerTestRunner extends ExamplesTestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        // Test uses Executors.newWorkStealingPool introduced at API 24.
        .withApiLevelsStartingAtIncluding(AndroidApiLevel.N)
        .withAllRuntimes()
        .withPartialCompilation()
        .enableApiLevelsForCf()
        .build();
  }

  public UdpServerTestRunner(TestParameters parameters) {
    super(parameters);
  }

  @Override
  public Class<?> getMainClass() {
    return UdpServer.class;
  }

  @Override
  public List<Class<?>> getTestClasses() throws Exception {
    return ImmutableList.of(getMainClass(), Class.forName(getMainClass().getTypeName() + "$1"));
  }

  @Override
  public String getExpected() {
    return StringUtils.lines("java.util.concurrent.TimeoutException");
  }

  // TODO(b/79671093): We don't match JVM's behavior on this example (line differences).
  @Ignore("b/79671093")
  @Test
  @Override
  public void testDebug() throws Exception {
    super.testDebug();
  }
}
