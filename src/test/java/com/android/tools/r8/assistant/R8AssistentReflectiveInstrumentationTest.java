// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.assistant;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.OutputMode;
import com.android.tools.r8.R8Assistant;
import com.android.tools.r8.R8AssistantCommand;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class R8AssistentReflectiveInstrumentationTest extends TestBase {
  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNativeMultidexDexRuntimes().withMaximumApiLevel().build();
  }

  @Test
  public void testInstrumentation() throws Exception {
    Path d8Compilation =
        testForD8(parameters.getBackend())
            .addInnerClasses(getClass())
            .setMinApi(parameters)
            .compile()
            .inspect(codeInspector -> inspectStaticCallsInReflectOn(0, codeInspector))
            .writeToZip();
    R8AssistantCommand.Builder builder = R8AssistantCommand.builder();
    Path outputPath = temp.newFile("instrumented.jar").toPath();
    // TODO(b/393265921): Add testForR8Assistant and avoid building up the command here
    R8AssistantCommand command =
        builder
            .addProgramFiles(d8Compilation)
            .setMinApiLevel(parameters.getApiLevel().getLevel())
            .setOutput(outputPath, OutputMode.DexIndexed)
            .build();
    R8Assistant.run(command);
    inspectStaticCallsInReflectOn(outputPath, 2);

    String artOutput =
        ToolHelper.runArtNoVerificationErrors(
            outputPath.toString(),
            TestClass.class
                .getName()); // For now, just test that the printed logs are what we expect
    String expectedNewInstanceString =
        "Reflectively created new instance of " + Bar.class.getName();
    assertThat(artOutput, containsString(expectedNewInstanceString));
    String expectedGetDeclaredMethod =
        "Reflectively got declared method callMe on " + Bar.class.getName();
    assertThat(artOutput, containsString(expectedGetDeclaredMethod));
  }

  private static void inspectStaticCallsInReflectOn(Path outputPath, int count) throws IOException {
    CodeInspector inspector = new CodeInspector(outputPath);
    inspectStaticCallsInReflectOn(count, inspector);
  }

  private static void inspectStaticCallsInReflectOn(int count, CodeInspector inspector) {
    ClassSubject testClass = inspector.clazz(TestClass.class);
    assertThat(testClass, isPresent());
    MethodSubject reflectOn = testClass.uniqueMethodWithOriginalName("reflectOn");
    assertThat(reflectOn, isPresent());
    long codeCount =
        reflectOn.streamInstructions().filter(InstructionSubject::isInvokeStatic).count();
    assertEquals(count, codeCount);
  }

  static class TestClass {
    public static void main(String[] args) {
      reflectOn(System.currentTimeMillis() == 0 ? Foo.class : Bar.class);
    }

    public static void reflectOn(Class<?> clazz) {
      try {
        clazz.newInstance();
        clazz.getDeclaredMethod("callMe");

      } catch (InstantiationException | IllegalAccessException | NoSuchMethodException e) {
        throw new RuntimeException(e);
      }
    }
  }

  public static class Foo {}

  public static class Bar {
    public void callMe() {}
  }
}
