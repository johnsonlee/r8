// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.jdk21.backport;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfVm;
import java.util.Objects;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ObjectsJava21Test extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static Iterable<?> data() {
    return getTestParameters()
        .withDexRuntimes()
        .withCfRuntimesStartingFromIncluding(CfVm.JDK21)
        .withAllApiLevelsAlsoForCf()
        .withPartialCompilation()
        .build();
  }

  @Test
  public void test() throws Exception {
    testForDesugaring(parameters)
        .addInnerClassesAndStrippedOuter(getClass())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccess();
  }

  public static class TestClass {

    public static void main(String[] args) {
      testToIdentityString();
    }

    private static void testToIdentityString() {
      try {
        Objects.toIdentityString(null);
        throw new AssertionError("fail");
      } catch (NullPointerException e) {
      }

      String string = "string";
      assertTrue(Objects.toIdentityString(string).startsWith(string.getClass().getName() + "@"));
    }

    private static void assertTrue(boolean b) {
      if (!b) {
        throw new AssertionError("Expected true");
      }
    }
  }
}
