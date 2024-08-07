// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.classmerging.horizontal;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.HorizontallyMergedClassesInspector;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ConstClassAfterVerticalClassMergingTest extends TestBase {

  @Parameter(0)
  public boolean enableSyntheticSharing;

  @Parameter(1)
  public TestParameters parameters;

  @Parameters(name = "{1}, synthetic sharing: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(), getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addHorizontallyMergedClassesInspector(
            HorizontallyMergedClassesInspector::assertNoClassesMerged)
        .addVerticallyMergedClassesInspector(
            inspector ->
                inspector
                    .applyIf(
                        parameters.isDexRuntime(), i -> i.assertMergedIntoSubtype(I.class, J.class))
                    .assertNoOtherClassesMerged())
        .setMinApi(parameters)
        .addOptionsModification(
            options -> options.getTestingOptions().enableSyntheticSharing = enableSyntheticSharing)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("2");
  }

  static class Main {

    public static void main(String[] args) {
      Map<Class<?>, Object> map = new HashMap<>();
      map.put(I.class, (I) () -> System.out.println("I"));
      map.put(J.class, (J) () -> System.out.println("J"));
      System.out.println(map.size());
    }
  }

  interface I {

    void f();
  }

  interface J {

    void g();
  }
}
