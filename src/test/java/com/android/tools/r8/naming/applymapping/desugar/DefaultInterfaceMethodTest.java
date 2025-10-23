// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming.applymapping.desugar;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsentIf;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.D8TestCompileResult;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DefaultInterfaceMethodTest extends TestBase {

  public static final String OUTPUT = "Called LibraryInterface::foo";
  public static final String EXPECTED = StringUtils.lines(OUTPUT);

  public interface LibraryInterface {
    default void foo() {
      System.out.println(OUTPUT);
    }
  }

  public static class ProgramClass implements LibraryInterface {

    public static void main(String[] args) {
      new ProgramClass().foo();
    }
  }

  @Parameters(name = "{0}")
  public static TestParametersCollection params() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private final TestParameters parameters;

  public DefaultInterfaceMethodTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testJvm() throws Throwable {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addProgramClasses(LibraryInterface.class, ProgramClass.class)
        .run(parameters.getRuntime(), ProgramClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testFullProgram() throws Throwable {
    testForR8(parameters.getBackend())
        .addProgramClasses(LibraryInterface.class, ProgramClass.class)
        .addKeepMainRule(ProgramClass.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), ProgramClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testLibraryLinkedWithProgram() throws Throwable {
    R8TestCompileResult libraryResult =
        testForR8(parameters.getBackend())
            .addProgramClasses(LibraryInterface.class)
            .addKeepClassAndMembersRules(LibraryInterface.class)
            .setMinApi(parameters)
            .compile()
            .inspect(
                inspector -> {
                  ClassSubject libraryClass = inspector.clazz(LibraryInterface.class);
                  assertThat(libraryClass, isPresent());
                  assertThat(libraryClass.uniqueMethodWithOriginalName("foo"), isPresent());
                  if (!parameters.canUseDefaultAndStaticInterfaceMethods()) {
                    // We don't expect the companion class to be present since -keep does not apply
                    // to synthetics.
                    ClassSubject companion =
                        inspector.clazz(
                            SyntheticItemsTestUtils.syntheticCompanionClass(
                                LibraryInterface.class));
                    assertThat(companion, isAbsent());
                  }
                });

    testForR8(parameters.getBackend())
        .addDontShrink()
        .addProgramClasses(ProgramClass.class)
        .addClasspathClasses(LibraryInterface.class)
        .addApplyMapping(libraryResult.getProguardMap())
        .addKeepMainRule(ProgramClass.class)
        .setMinApi(parameters)
        .compile()
        .addRunClasspathFiles(libraryResult.writeToZip())
        .run(parameters.getRuntime(), ProgramClass.class)
        .applyIf(
            parameters.canUseDefaultAndStaticInterfaceMethods(),
            rr -> rr.assertSuccessWithOutput(EXPECTED),
            parameters.isDexRuntime() && parameters.getDexRuntimeVersion().isDalvik(),
            rr -> rr.assertFailureWithErrorThatThrows(NoClassDefFoundError.class),
            rr -> rr.assertFailureWithErrorThatThrows(ClassNotFoundException.class));
  }

  @Test
  public void testDesugaredLibraryLinkedWithProgram() throws Throwable {
    parameters.assumeDexRuntime("Desugaring not required when compiling to CF");

    D8TestCompileResult libraryDesugarResult =
        testForD8(Backend.CF)
            .addProgramClasses(LibraryInterface.class)
            .setMinApi(parameters)
            .compile()
            .inspect(
                inspector -> {
                  ClassSubject companion =
                      inspector.clazz(
                          SyntheticItemsTestUtils.syntheticCompanionClass(LibraryInterface.class));
                  assertThat(
                      companion, isAbsentIf(parameters.canUseDefaultAndStaticInterfaceMethods()));
                });

    R8TestCompileResult libraryResult =
        testForR8(parameters.getBackend())
            .addProgramFiles(libraryDesugarResult.writeToZip())
            .addKeepClassAndMembersRulesWithAllowObfuscation("*")
            .setMinApi(parameters)
            .compile()
            .inspect(
                inspector -> {
                  ClassSubject libraryClass = inspector.clazz(LibraryInterface.class);
                  assertThat(libraryClass, isPresent());
                  assertThat(libraryClass.uniqueMethodWithOriginalName("foo"), isPresent());
                  if (!parameters.canUseDefaultAndStaticInterfaceMethods()) {
                    ClassSubject companion =
                        inspector.clazz(
                            SyntheticItemsTestUtils.syntheticCompanionClass(
                                LibraryInterface.class));
                    assertThat(companion, isPresentAndRenamed());
                    assertThat(
                        companion.uniqueMethodWithOriginalName("$default$foo"),
                        isPresentAndRenamed());
                  }
                });

    testForR8(parameters.getBackend())
        .addDontShrink()
        .addProgramClasses(ProgramClass.class)
        .addClasspathClasses(LibraryInterface.class)
        .addApplyMapping(libraryResult.getProguardMap())
        .addKeepMainRule(ProgramClass.class)
        .setMinApi(parameters)
        .compile()
        .addRunClasspathFiles(libraryResult.writeToZip())
        .run(parameters.getRuntime(), ProgramClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }
}
