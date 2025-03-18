// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.jdk17.records;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.utils.AndroidApiLevel;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RecordOnlyReferencedFromCodeTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimesAndAllApiLevels().build();
  }

  @Test
  public void testD8() throws Exception {
    testForD8()
        .addInnerClassesAndStrippedOuter(getClass())
        .addLibraryFiles(ToolHelper.getMostRecentAndroidJar())
        .release()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("false");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClassesAndStrippedOuter(getClass())
        .addLibraryFiles(ToolHelper.getMostRecentAndroidJar())
        .addKeepMainRule(Main.class)
        .addDontOptimize()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .applyIf(
            parameters.isDexRuntimeVersion(Version.V14_0_0)
                && parameters.getApiLevel().equals(AndroidApiLevel.U),
            // TODO(b/193004879): The Enqueuer should "prepare" all methods, even if they do not
            //  require desugaring.
            rr -> rr.assertFailureWithErrorThatThrows(NoClassDefFoundError.class),
            rr -> rr.assertSuccessWithOutputLines("false"));
  }

  static class Main {

    public static void main(String[] args) {
      System.out.println(isRecord(args));
    }

    static boolean isRecord(Object o) {
      return o instanceof java.lang.Record;
    }
  }
}
