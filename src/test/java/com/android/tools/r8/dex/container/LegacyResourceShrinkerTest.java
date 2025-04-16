// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.container;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.ResourceShrinker;
import com.android.tools.r8.ResourceShrinker.ReferenceChecker;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.IntBox;
import com.android.tools.r8.utils.InternalOptions;
import java.nio.file.Path;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class LegacyResourceShrinkerTest extends DexContainerFormatTestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  private static Path inputA;
  private static Path inputB;

  @BeforeClass
  public static void generateTestApplications() throws Throwable {
    // Build two applications in different packages both with required multidex due to number
    // of methods.
    inputA = getStaticTemp().getRoot().toPath().resolve("application_a.jar");
    inputB = getStaticTemp().getRoot().toPath().resolve("application_b.jar");

    generateApplication(inputA, "a", 10);
    generateApplication(inputB, "b", 10);
  }

  @Test
  public void test() throws Exception {
    Path outputBoth =
        testForD8(Backend.DEX)
            .addProgramFiles(inputA, inputB)
            .setMinApi(InternalOptions.containerDexApiLevel())
            .compile()
            .writeToZip();
    validateSingleContainerDex(outputBoth);
    ResourceShrinker.Command command =
        new ResourceShrinker.Builder().addProgramFiles(outputBoth).build();

    IntBox classCount = new IntBox();
    ToolHelper.runLegacyResourceShrinker(
        new ResourceShrinker.Builder().addProgramFiles(outputBoth),
        new ReferenceChecker() {
          @Override
          public boolean shouldProcess(String internalName) {
            classCount.increment();
            return true;
          }

          @Override
          public void referencedInt(int value) {}

          @Override
          public void referencedString(String value) {}

          @Override
          public void referencedStaticField(String internalName, String fieldName) {}

          @Override
          public void referencedMethod(
              String internalName, String methodName, String methodDescriptor) {}
        });
    assertEquals(20000, classCount.get());
  }
}
