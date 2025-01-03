// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class CatchHandlerCoalescingAfterSplitReturnRewriterTest extends TestBase {

  @Parameter(0)
  public boolean forceSplitReturnRewriter;

  @Parameter(1)
  public TestParameters parameters;

  @Parameters(name = "{1}, split return: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(), getTestParameters().withDexRuntimesAndAllApiLevels().build());
  }

  @Test
  public void testD8() throws Exception {
    testForD8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addOptionsModification(
            options ->
                options.getTestingOptions().forceSplitReturnRewriter = forceSplitReturnRewriter)
        .release()
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              MethodSubject testMethodSubject =
                  inspector.clazz(Main.class).uniqueMethodWithOriginalName("test");
              assertThat(testMethodSubject, isPresent());

              DexCode code = testMethodSubject.getMethod().getCode().asDexCode();
              assertEquals(1, code.getTries().length);
            });
  }

  static class Main {

    public static Object test() {
      try {
        doStuff();
        doStuff();
      } catch (Exception e) {
        System.out.println(e);
        return e;
      }
      return null;
    }

    private static void doStuff() {}
  }
}
