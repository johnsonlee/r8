// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.assistant;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.ZipUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.io.IOException;
import java.io.UncheckedIOException;
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
  public void testR8LibRuntimeFiles() throws IOException {
    assumeTrue(ToolHelper.isTestingR8Lib());
    ZipUtils.iter(
        ToolHelper.ASSISTANT_JAR,
        zipEntry -> {
          try {
            if (ZipUtils.isClassFile(zipEntry.getName())) {
              byte[] bytesAssistant =
                  ZipUtils.readSingleEntry(ToolHelper.ASSISTANT_JAR, zipEntry.getName());
              byte[] bytesR8Lib =
                  ZipUtils.readSingleEntry(ToolHelper.R8LIB_JAR, zipEntry.getName());
              assertArrayEquals(bytesAssistant, bytesR8Lib);
            }
          } catch (IOException e) {
            throw new UncheckedIOException(e);
          }
        });
  }

  @Test
  public void testInstrumentation() throws Exception {
    testForAssistant()
        .addInnerClasses(getClass())
        .setMinApi(parameters)
        .compile()
        .inspectOriginalDex(inspector -> inspectStaticCallsInReflectOn(0, inspector))
        .inspect(inspector -> inspectStaticCallsInReflectOn(2, inspector))
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines(
            "Reflectively created new instance of " + Bar.class.getName(),
            "Reflectively got declared method callMe on " + Bar.class.getName());
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
