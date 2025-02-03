// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno;

import com.android.tools.r8.keepanno.annotations.KeepBinding;
import com.android.tools.r8.keepanno.annotations.KeepEdge;
import com.android.tools.r8.keepanno.annotations.KeepItemKind;
import com.android.tools.r8.keepanno.annotations.KeepTarget;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

@RunWith(Parameterized.class)
public class KeepMainViaBindingTest extends KeepAnnoTestBase {

  static final String EXPECTED = StringUtils.lines("Hello, world!");

  @Parameter public KeepAnnoParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static List<KeepAnnoParameters> data() {
    return createParameters(
        getTestParameters().withDefaultRuntimes().withMaximumApiLevel().build());
  }

  @Test
  public void test() throws Exception {
    testForKeepAnno(parameters)
        .addProgramClasses(getInputClasses())
        .setExcludedOuterClass(getClass())
        .run(TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  public List<Class<?>> getInputClasses() {
    return ImmutableList.of(TestClass.class);
  }

  static class TestClass {

    @KeepEdge(
        bindings =
            @KeepBinding(
                bindingName = "MainMethod",
                classConstant = TestClass.class,
                methodName = "main"),
        consequences =
            @KeepTarget(kind = KeepItemKind.CLASS_AND_METHODS, memberFromBinding = "MainMethod"))
    public static void main(String[] args) throws Exception {
      System.out.println("Hello, world!");
    }
  }
}
