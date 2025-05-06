// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.JdkClassFileProvider;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ProgramTypeRenamedAsClasspathTypeTest extends TestBase {

  @Parameter public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withCfRuntimesStartingFromIncluding(CfVm.JDK24)
        .withDexRuntimes()
        .withAllApiLevelsAlsoForCf()
        .withPartialCompilation()
        .build();
  }

  public static String EXPECTED_OUTPUT = StringUtils.lines("program side", "classpath side");

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addInnerClassesAndStrippedOuter(getClass())
        .run(parameters.getRuntime(), ProgramMain.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testR8Split() throws Exception {
    parameters.assumeR8TestParameters();
    Set<String> types = new HashSet<>();
    R8TestCompileResult compile =
        testForR8(Backend.CF)
            .addProgramClasses(ClasspathSide.class, ClasspathMain.class)
            .addLibraryProvider(JdkClassFileProvider.fromSystemJdk())
            .addKeepMainRule(ClasspathMain.class)
            .addKeepClassAndMembersRulesWithAllowObfuscation(ClasspathSide.class)
            .compile();
    compile.inspect(
        i -> {
          assertEquals(2, i.allClasses().size());
          for (FoundClassSubject clazz : i.allClasses()) {
            types.add(clazz.getDexProgramClass().getTypeName());
          }
        });
    Path classpath = compile.writeToZip();
    Path runClasspath = testForD8(parameters).addProgramFiles(classpath).compile().writeToZip();
    testForR8(parameters)
        .addProgramClasses(ProgramSide.class, ProgramMain.class)
        .addClasspathFiles(classpath)
        .addKeepMainRule(ProgramMain.class)
        .addKeepClassAndMembersRulesWithAllowObfuscation(ProgramSide.class)
        .compile()
        .inspect(
            i -> {
              assertEquals(2, i.allClasses().size());
              for (FoundClassSubject clazz : i.allClasses()) {
                types.add(clazz.getDexProgramClass().getTypeName());
              }
              assertEquals(4, types.size());
            })
        .addRunClasspathFiles(runClasspath)
        .run(parameters.getRuntime(), ProgramMain.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  static class ClasspathMain {
    public static void main(String[] args) {
      ClasspathSide.print();
    }
  }

  static class ClasspathSide {
    public static void print() {
      System.out.println("classpath side");
    }
  }

  public static class ProgramMain {
    public static void main(String[] args) {
      ProgramSide.print();
      ClasspathMain.main(args);
    }
  }

  static class ProgramSide {
    public static void print() {
      System.out.println("program side");
    }
  }
}
