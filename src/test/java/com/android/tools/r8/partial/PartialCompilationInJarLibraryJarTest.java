// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.partial;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.ZipUtils.ZipBuilder;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class PartialCompilationInJarLibraryJarTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().withPartialCompilation().build();
  }

  @Test
  public void test() throws Exception {
    parameters.assumeCanUseR8Partial();
    parameters.getPartialCompilationTestParameters().assumeIsSome();
    Path injar =
        ZipBuilder.builder(temp.newFile("main.zip").toPath())
            .addFile(
                "com/android/tools/r8/partial/PartialCompilationInJarLibraryJarTest$Main.class",
                ToolHelper.getClassFileForTestClass(Main.class))
            .build();
    Path libjar =
        ZipBuilder.builder(temp.newFile("base.zip").toPath())
            .addFile(
                "com/android/tools/r8/partial/PartialCompilationInJarLibraryJarTest$Base.class",
                ToolHelper.getClassFileForTestClass(Base.class))
            .build();
    testForR8(parameters)
        .addKeepMainRule(Main.class)
        .addKeepRules("-injars " + injar, "-libraryjars " + libjar)
        .compile()
        .addRunClasspathClasses(Base.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello, world!");
  }

  static class Base {}

  static class Main extends Base {

    public static void main(String[] args) {
      System.out.println("Hello, world!");
    }
  }
}
