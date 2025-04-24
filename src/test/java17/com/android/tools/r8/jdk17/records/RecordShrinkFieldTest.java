// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.jdk17.records;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.core.AnyOf.anyOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.JdkClassFileProvider;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.nio.file.Path;
import java.util.List;
import org.hamcrest.Matcher;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class RecordShrinkFieldTest extends TestBase {

  private static final String EXPECTED_RESULT_D8 =
      StringUtils.lines(
          "Person[unused=-1, name=Jane Doe, age=42]", "Person[unused=-1, name=Bob, age=42]");
  private static final String EXPECTED_RESULT_R8 = StringUtils.lines("a[a=Jane Doe]", "a[a=Bob]");
  private static final String EXPECTED_RESULT_R8_NO_MINIFICATION =
      StringUtils.lines(
          "RecordShrinkFieldTest$Person[name=Jane Doe]", "RecordShrinkFieldTest$Person[name=Bob]");

  private static final String EXPECTED_RESULT_R8_PARTIAL_INCLUDE_ALL =
      StringUtils.lines("a[unused=-1, name=Jane Doe, age=42]", "a[unused=-1, name=Bob, age=42]");
  private static final String EXPECTED_RESULT_R8_PARTIAL_INCLUDE_NO_MINIFICATION =
      StringUtils.lines(
          "RecordShrinkFieldTest$Person[unused=-1, name=Jane Doe, age=42]",
          "RecordShrinkFieldTest$Person[unused=-1, name=Bob, age=42]");

  private final TestParameters parameters;
  private final boolean minifying;

  public RecordShrinkFieldTest(TestParameters parameters, boolean minifying) {
    this.parameters = parameters;
    this.minifying = minifying;
  }

  @Parameterized.Parameters(name = "{0}, minifying: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters()
            .withDexRuntimes()
            .withAllApiLevelsAlsoForCf()
            .withPartialCompilation()
            .build(),
        BooleanUtils.values());
  }

  @Test
  public void testD8() throws Exception {
    assumeTrue("Only valid in R8", minifying);
    testForD8(parameters)
        .addInnerClassesAndStrippedOuter(getClass())
        .compile()
        .run(parameters.getRuntime(), RecordShrinkField.class)
        .assertSuccessWithOutput(EXPECTED_RESULT_D8);
  }

  @Test
  public void testR8() throws Exception {
    assumeTrue(parameters.getPartialCompilationTestParameters().isRandom());
    parameters.assumeR8TestParameters();
    testForR8(parameters)
        .addInnerClassesAndStrippedOuter(getClass())
        .addKeepMainRule(RecordShrinkField.class)
        .addDontObfuscateUnless(minifying)
        .addR8PartialR8OptionsModification(
            options -> options.getTraceReferencesOptions().skipInnerClassesForTesting = false)
        .compile()
        .inspect(inspector -> inspect(inspector, false))
        .run(parameters.getRuntime(), RecordShrinkField.class)
        .assertSuccessWithOutputThatMatches(getExpectedOutputForR8());
  }

  private Matcher<String> getExpectedOutputForR8() {
    if (parameters.getPartialCompilationTestParameters().isIncludeAll()) {
      return equalTo(
          minifying
              ? EXPECTED_RESULT_R8_PARTIAL_INCLUDE_ALL
              : EXPECTED_RESULT_R8_PARTIAL_INCLUDE_NO_MINIFICATION);
    } else if (parameters.getPartialCompilationTestParameters().isRandom()) {
      return anyOf(
          equalTo(EXPECTED_RESULT_D8),
          equalTo(EXPECTED_RESULT_R8_PARTIAL_INCLUDE_ALL),
          equalTo(EXPECTED_RESULT_R8_PARTIAL_INCLUDE_NO_MINIFICATION));
    } else {
      return equalTo(minifying ? EXPECTED_RESULT_R8 : EXPECTED_RESULT_R8_NO_MINIFICATION);
    }
  }

  @Test
  public void testR8CfThenDex() throws Exception {
    parameters.assumeR8TestParameters();
    Path desugared =
        testForR8(Backend.CF)
            .addInnerClassesAndStrippedOuter(getClass())
            .addKeepMainRule(RecordShrinkField.class)
            .addDontObfuscateUnless(minifying)
            .addLibraryProvider(JdkClassFileProvider.fromSystemJdk())
            .compile()
            .writeToZip();
    testForR8(parameters)
        .addProgramFiles(desugared)
        .addKeepMainRule(RecordShrinkField.class)
        .addDontObfuscateUnless(minifying)
        .compile()
        .inspect(inspector -> inspect(inspector, true))
        .run(parameters.getRuntime(), RecordShrinkField.class)
        .assertSuccessWithOutput(
            minifying ? EXPECTED_RESULT_R8 : EXPECTED_RESULT_R8_NO_MINIFICATION);
  }

  private void inspect(CodeInspector inspector, boolean isCfThenDex) {
    ClassSubject recordClass = inspector.clazz(Person.class);
    int numberOfInstanceFields = recordClass.allInstanceFields().size();
    if (!isCfThenDex || !minifying) {
      if (parameters.getPartialCompilationTestParameters().isRandom()) {
        assertTrue(
            Integer.toString(numberOfInstanceFields),
            numberOfInstanceFields == 1
                || numberOfInstanceFields == 2
                || numberOfInstanceFields == 3);
      } else {
        assertEquals(1, numberOfInstanceFields);
        assertEquals(
            "java.lang.String",
            recordClass.allInstanceFields().get(0).getField().type().toString());
      }
    } else {
      assertEquals(0, numberOfInstanceFields);
    }
  }

  record Person(int unused, String name, int age) {
    Person(String name, int age) {
      this(-1, name, age);
    }
  }

  public class RecordShrinkField {

    public static void main(String[] args) {
      Person jane = new Person("Jane Doe", 42);
      Person bob = new Person("Bob", 42);
      System.out.println(jane);
      System.out.println(bob);
    }
  }
}
