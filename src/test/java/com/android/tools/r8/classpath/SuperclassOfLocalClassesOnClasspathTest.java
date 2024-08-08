// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.package com.android.tools.r8.classpath;

package com.android.tools.r8.classpath;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.ZipUtils.ZipBuilder;
import java.nio.file.Path;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SuperclassOfLocalClassesOnClasspathTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  static Path classpathDex;
  static Path classpathCf;

  @BeforeClass
  public static void setUpClasspath() throws Exception {
    // Build classpath DEX with API level 1 to work with all API levels.
    classpathDex =
        testForD8(getStaticTemp())
            .addProgramClasses(A.class)
            .setMinApi(AndroidApiLevel.B)
            .compile()
            .writeToZip();
    classpathCf = getStaticTemp().newFile("classpath2.zip").toPath();
    ZipBuilder.builder(classpathCf)
        .addFile(
            DescriptorUtils.getPathFromJavaType(A.class),
            ToolHelper.getClassFileForTestClass(A.class))
        .build();
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8()
        .addClasspathClasses(A.class)
        .addInnerClasses(A.class)
        .addProgramClasses(I.class)
        .addProgramClasses(TestClass.class)
        .setMinApi(parameters)
        .addRunClasspathFiles(classpathDex)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccess();
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        // Split A and its inner classes on classpath/program.
        .addClasspathClasses(A.class)
        .addInnerClasses(A.class)
        .addProgramClasses(I.class)
        .addProgramClasses(TestClass.class)
        .addKeepMainRule(TestClass.class)
        .addKeepRules("-keep class " + A.class.getTypeName() + "$* { *; }")
        .setMinApi(parameters)
        .addRunClasspathFiles(parameters.isDexRuntime() ? classpathDex : classpathCf)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccess();
  }

  interface I {
    void m();
  }

  static class A {
    static {
      // Anonymous local class in <clinit>.
      new I() {
        public void m() {}
      };
    }

    static {
      // Named local class in <clinit>.
      class Local {}
      new Local();
    }

    public A() {
      anonymousLocalClass();
      namedLocalClass();

      // Anonymous local class in <init>.
      new I() {
        public void m() {}
      };

      // Named local class in <init>.
      class Local {
        public void m() {}
      }

      new Local();
    }

    private void anonymousLocalClass() {
      new I() {
        public void m() {}
      };
    }

    private void namedLocalClass() {
      class Local {
        public void m() {}
      }
      new Local();
    }
  }

  static class TestClass {

    public static void main(String[] args) {
      new A();
    }
  }
}
