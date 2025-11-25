// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.synthesis.globals;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.GlobalSyntheticsTestingConsumer;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.transformers.ClassFileTransformer.MethodPredicate;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Regression test for b/335803299. */
@RunWith(Parameterized.class)
public class GlobalSyntheticStubContextRegressionTest extends TestBase {

  static final String EXPECTED =
      StringUtils.lines("Hello", "all good...", "Hello again", "still good...");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withApiLevel(AndroidApiLevel.N).build();
  }

  public GlobalSyntheticStubContextRegressionTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testDexPerClassFileDir() throws Exception {
    GlobalSyntheticsTestingConsumer globals = new GlobalSyntheticsTestingConsumer();
    Path dexOut =
        testForD8()
            .apply(b -> b.getBuilder().setEnableExperimentalMissingLibraryApiModeling(true))
            .addProgramClassFileData(
                transformClass(TestClass1.class), transformClass(TestClass2.class))
            .setMinApi(parameters)
            .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.LATEST))
            .apply(b -> b.getBuilder().setGlobalSyntheticsConsumer(globals))
            .setOutputMode(OutputMode.DexFilePerClassFile)
            .compile()
            .writeToZip();

    assertTrue(globals.hasGlobals());
    assertEquals(3, globals.getProviders().size());

    testForD8()
        .addProgramFiles(dexOut)
        .setMinApi(parameters)
        .apply(b -> b.getBuilder().addGlobalSyntheticsResourceProviders(globals.getProviders()))
        .run(parameters.getRuntime(), TestClass1.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  private byte[] transformClass(Class<?> clazz) throws IOException {
    return transformer(clazz)
        .transformTryCatchBlock(
            MethodPredicate.all(),
            (start, end, handler, type, visitor) -> {
              assertEquals("java/lang/Exception", type);
              visitor.visitTryCatchBlock(
                  start, end, handler, "android/app/AuthenticationRequiredException");
            })
        .transform();
  }

  static class TestClass1 {

    public static void main(String[] args) {
      Runnable r =
          () -> {
            try {
              System.out.println("Hello");
            } catch (Exception e) {
              System.out.println("fail...");
              throw e;
            }
            System.out.println("all good...");
          };
      r.run();
      TestClass2.main(args);
    }
  }

  static class TestClass2 {

    public static void main(String[] args) {
      try {
        System.out.println("Hello again");
      } catch (Exception e) {
        System.out.println("fail...");
        throw e;
      }
      System.out.println("still good...");
    }
  }
}
