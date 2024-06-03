// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.nestaccesscontrol;

import static com.android.tools.r8.desugar.nestaccesscontrol.NestAccessControlTestUtils.CLASSES_PATH;
import static com.android.tools.r8.desugar.nestaccesscontrol.NestAccessControlTestUtils.CLASS_NAMES;
import static com.android.tools.r8.desugar.nestaccesscontrol.NestAccessControlTestUtils.JAR;
import static com.android.tools.r8.utils.FileUtils.CLASS_EXTENSION;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.core.StringContains.containsString;
import static org.hamcrest.core.StringEndsWith.endsWith;
import static org.junit.Assert.assertThrows;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.D8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import java.nio.file.Path;
import java.util.List;
import org.hamcrest.Matcher;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class NestOnProgramAndLibraryPathTest extends TestBase {

  public NestOnProgramAndLibraryPathTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  @Test
  public void testD8MethodBridges() {
    Assume.assumeTrue(parameters.isDexRuntime());
    // 1 inner class.
    assertThrows(
        CompilationFailedException.class,
        () ->
            compileClassesWithD8ProgramClassesMatching(
                containsString("BasicNestHostWithInnerClassMethods$BasicNestedClass")));
    // Outer class.
    assertThrows(
        CompilationFailedException.class,
        () ->
            compileClassesWithD8ProgramClassesMatching(
                endsWith("BasicNestHostWithInnerClassMethods")));
    // 2 inner classes.
    assertThrows(
        CompilationFailedException.class,
        () ->
            compileClassesWithD8ProgramClassesMatching(
                containsString("NestHostExample$StaticNestMemberInner")));
  }

  @Test
  public void testD8ConstructorBridges() {
    Assume.assumeTrue(parameters.isDexRuntime());
    assertThrows(
        CompilationFailedException.class,
        () ->
            compileClassesWithD8ProgramClassesMatching(
                containsString("BasicNestHostWithInnerClassConstructors$BasicNestedClass")));
    assertThrows(
        CompilationFailedException.class,
        () ->
            compileClassesWithD8ProgramClassesMatching(
                endsWith("BasicNestHostWithInnerClassConstructors")));
  }

  private D8TestCompileResult compileClassesWithD8ProgramClassesMatching(Matcher<String> matcher)
      throws Exception {
    List<Path> matchingClasses =
        CLASS_NAMES.stream()
            .filter(matcher::matches)
            .map(name -> CLASSES_PATH.resolve(name + CLASS_EXTENSION))
            .collect(toList());
    return testForD8()
        .setMinApi(parameters)
        .addProgramFiles(matchingClasses)
        .addLibraryFiles(JAR)
        .compile();
  }
}
