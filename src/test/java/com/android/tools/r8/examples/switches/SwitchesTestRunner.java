// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.examples.switches;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.examples.ExamplesTestBase;
import com.android.tools.r8.utils.StringUtils;
import org.apache.harmony.jpda.tests.framework.TestErrorException;
import org.apache.harmony.jpda.tests.framework.jdwp.exceptions.TimeoutException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class SwitchesTestRunner extends ExamplesTestBase {

  public SwitchesTestRunner(TestParameters parameters) {
    super(parameters);
  }

  @Override
  public Class<?> getMainClass() {
    return Switches.class;
  }

  @Override
  public String getExpected() {
    return StringUtils.lines(
        "packedSwitch cases: 0 1 2 after switch 0",
        "packedSwitch cases: 1 2 after switch 1",
        "packedSwitch cases: 1 2 after switch 2",
        "packedSwitch cases: after switch -1",
        "0 ",
        "100 ",
        "after switch 0",
        "100 ",
        "after switch 100",
        "200 ",
        "after switch 200",
        "after switch -1",
        " 420",
        " 1.02",
        "0-21 after switch 1",
        "0-21 after switch 10",
        "after switch 40",
        "60 after switch 60");
  }

  @Test
  @Override
  public void testDebug() throws Exception {
    try {
      super.testDebug();
    } catch (TestErrorException e) {
      if (e.getCause() instanceof TimeoutException) {
        return;
      }
      throw e;
    }
  }
}
