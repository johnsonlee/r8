// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.applymapping;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestShrinkerBuilder;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
// This is a reproduction of b/181887416.
public class ApplyMappingClassPathInterfaceInheritTest extends TestBase {

  private final TestParameters parameters;
  private final boolean minifyLibrary;

  @Parameters(name = "{0}, minifyLibrary: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(), BooleanUtils.values());
  }

  public ApplyMappingClassPathInterfaceInheritTest(
      TestParameters parameters, boolean minifyLibrary) {
    this.parameters = parameters;
    this.minifyLibrary = minifyLibrary;
  }

  @Test
  public void testApplyMapping() throws Exception {
    R8TestCompileResult libraryResult =
        testForR8(parameters.getBackend())
            .addLibraryClasses(LibI.class)
            .addDefaultRuntimeLibrary(parameters)
            .addProgramClasses(ClassPathI.class)
            .applyIf(
                minifyLibrary,
                TestShrinkerBuilder::addKeepAllClassesRuleWithAllowObfuscation,
                TestShrinkerBuilder::addKeepAllClassesRule)
            .setMinApi(parameters)
            .compile();

    ClassSubject classPathIClass = libraryResult.inspector().clazz(ClassPathI.class);
    assertThat(classPathIClass, isPresentAndRenamed(minifyLibrary));

    Path libraryJar = libraryResult.writeToZip();
    testForR8(parameters.getBackend())
        .addLibraryClasses(LibI.class)
        .addDefaultRuntimeLibrary(parameters)
        .addClasspathClasses(ClassPathI.class)
        .addProgramClasses(Main.class)
        .addKeepAllClassesRule()
        .addApplyMapping(libraryResult.getProguardMap())
        .setMinApi(parameters)
        .compile()
        .addRunClasspathClasses(LibI.class)
        .addRunClasspathFiles(libraryJar)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(classPathIClass.getFinalName());
  }

  public interface LibI {}

  public interface ClassPathI extends LibI {}

  public static class Main {

    public static void main(String[] args) throws ClassNotFoundException {
      System.out.println(
          Class.forName(
                  "com.android.tools.r8.naming.applymapping"
                      + ".ApplyMappingClassPathInterfaceInheritTest$ClassPathI")
              .getName());
    }
  }
}
