// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.jdk17.records;

import static com.android.tools.r8.utils.DescriptorUtils.getInnerClassNameOrSimpleNameFromDescriptorForTesting;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.JdkClassFileProvider;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfRuntime;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class RecordInvokeCustomTest extends TestBase {

  private static final String EXPECTED_RESULT =
      StringUtils.lines(
          "%s[]",
          "true",
          "true",
          "true",
          "true",
          "true",
          "false",
          "true",
          "true",
          "false",
          "false",
          "%s[%s=Jane Doe, %s=42]");
  private static final String EXPECTED_RESULT_D8 =
      String.format(EXPECTED_RESULT, "Empty", "Person", "name", "age");

  private final TestParameters parameters;

  public RecordInvokeCustomTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withCfRuntimesStartingFromIncluding(CfVm.JDK17)
        .withDexRuntimes()
        .withAllApiLevelsAlsoForCf()
        .withPartialCompilation()
        .build();
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addInnerClassesAndStrippedOuter(getClass())
        .run(parameters.getRuntime(), RecordInvokeCustom.class)
        .assertSuccessWithOutput(EXPECTED_RESULT_D8);
  }

  @Test
  public void testD8() throws Exception {
    testForD8(parameters)
        .addInnerClassesAndStrippedOuter(getClass())
        .compile()
        .run(parameters.getRuntime(), RecordInvokeCustom.class)
        .assertSuccessWithOutput(EXPECTED_RESULT_D8);
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    testForR8(parameters)
        .addInnerClassesAndStrippedOuter(getClass())
        .setMinApi(parameters)
        .addKeepMainRule(RecordInvokeCustom.class)
        .addR8PartialR8OptionsModification(
            options -> options.getTraceReferencesOptions().skipInnerClassesForTesting = false)
        .applyIf(
            parameters.isCfRuntime(),
            b ->
                b.addLibraryProvider(
                    JdkClassFileProvider.fromSystemModulesJdk(
                        CfRuntime.getCheckedInJdk17().getJavaHome())))
        .compile()
        .inspectIf(parameters.isCfRuntime(), RecordTestUtils::assertRecordsAreRecords)
        .run(parameters.getRuntime(), RecordInvokeCustom.class)
        .apply(rr -> rr.assertSuccessWithOutput(getExpectedResultForR8(rr.inspector())));
  }

  private String getExpectedResultForR8(CodeInspector inspector) {
    ClassSubject emptyClassSubject = inspector.clazz(Empty.class);
    assertThat(emptyClassSubject, isPresent());

    ClassSubject personClassSubject = inspector.clazz(Person.class);
    assertThat(personClassSubject, isPresent());

    return String.format(
        EXPECTED_RESULT,
        getInnerClassNameOrSimpleNameFromDescriptorForTesting(
            emptyClassSubject.getFinalDescriptor()),
        getInnerClassNameOrSimpleNameFromDescriptorForTesting(
            personClassSubject.getFinalDescriptor()),
        parameters.getPartialCompilationTestParameters().isNone() ? "a" : "name",
        parameters.getPartialCompilationTestParameters().isNone() ? "b" : "age");
  }

  record Empty() {}

  record Person(String name, int age) {}

  public class RecordInvokeCustom {

    public static void main(String[] args) {
      emptyTest();
      equalityTest();
      toStringTest();
    }

    private static void emptyTest() {
      Empty empty1 = new Empty();
      Empty empty2 = new Empty();
      System.out.println(empty1.toString());
      System.out.println(empty1.equals(empty2));
      System.out.println(empty1.hashCode() == empty2.hashCode());
      System.out.println(empty1.toString().equals(empty2.toString()));
    }

    private static void toStringTest() {
      Person janeDoe = new Person("Jane Doe", 42);
      System.out.println(janeDoe.toString());
    }

    private static void equalityTest() {
      Person jane1 = new Person("Jane Doe", 42);
      Person jane2 = new Person("Jane Doe", 42);
      String nonIdenticalString = "Jane " + (System.currentTimeMillis() > 0 ? "Doe" : "Zan");
      Person jane3 = new Person(nonIdenticalString, 42);
      Person bob = new Person("Bob", 42);
      Person youngJane = new Person("Jane Doe", 22);
      System.out.println(jane1.equals(jane2));
      System.out.println(jane1.toString().equals(jane2.toString()));
      System.out.println(nonIdenticalString == "Jane Doe"); // false.
      System.out.println(nonIdenticalString.equals("Jane Doe")); // true.
      System.out.println(jane1.equals(jane3));
      System.out.println(jane1.equals(bob));
      System.out.println(jane1.equals(youngJane));
    }
  }
}
