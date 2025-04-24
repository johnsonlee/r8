// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.jdk17.records;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsentOr;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.TestShrinkerBuilder;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import java.lang.reflect.RecordComponent;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RecordComponentSignatureTest extends TestBase {

  private static final String EXPECTED_RESULT =
      StringUtils.lines(
          "Jane Doe",
          "42",
          "true",
          "2",
          "name",
          "java.lang.CharSequence",
          "TN;",
          "0",
          "age",
          "java.lang.Object",
          "TA;",
          "0");
  private static final String EXPECTED_RESULT_R8 = StringUtils.lines("Jane Doe", "42", "true", "0");
  private static final String EXPECTED_RESULT_DESUGARED_NO_NATIVE_RECORDS_SUPPORT =
      StringUtils.lines("Jane Doe", "42", "Class.isRecord not present");
  private static final String EXPECTED_RESULT_DESUGARED_NATIVE_RECORD_SUPPORT =
      StringUtils.lines("Jane Doe", "42", "false");

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public Boolean keepSignatures;

  @Parameters(name = "{0}, keepSignatures: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters()
            .withDexRuntimesAndAllApiLevels()
            .withCfRuntimesStartingFromIncluding(CfVm.JDK17)
            .withAllApiLevelsAlsoForCf()
            .withPartialCompilation()
            .build(),
        BooleanUtils.values());
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    assumeTrue(keepSignatures);
    testForJvm(parameters)
        .addInnerClassesAndStrippedOuter(getClass())
        .run(parameters.getRuntime(), RecordWithSignature.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  @Test
  public void testDesugaring() throws Exception {
    parameters.assumeDexRuntime();
    assumeTrue(keepSignatures);
    testForDesugaring(parameters)
        .addInnerClassesAndStrippedOuter(getClass())
        .run(parameters.getRuntime(), RecordWithSignature.class)
        .applyIf(
            parameters.isCfRuntime(),
            r -> r.assertSuccessWithOutput(EXPECTED_RESULT),
            r ->
                r.assertSuccessWithOutput(
                        runtimeWithRecordsSupport(parameters.getRuntime())
                            ? EXPECTED_RESULT_DESUGARED_NATIVE_RECORD_SUPPORT
                            : EXPECTED_RESULT_DESUGARED_NO_NATIVE_RECORDS_SUPPORT)
                    .inspect(
                        inspector -> {
                          ClassSubject person =
                              inspector.clazz(RecordComponentSignatureTest.Person.class);
                          if (parameters.isCfRuntime()) {
                            assertEquals(2, person.getFinalRecordComponents().size());

                            assertEquals(
                                "name", person.getFinalRecordComponents().get(0).getName());
                            assertTrue(
                                person
                                    .getFinalRecordComponents()
                                    .get(0)
                                    .getType()
                                    .is("java.lang.CharSequence"));
                            assertEquals(
                                "TN;", person.getFinalRecordComponents().get(0).getSignature());
                            assertEquals(
                                0,
                                person.getFinalRecordComponents().get(0).getAnnotations().size());

                            assertEquals("age", person.getFinalRecordComponents().get(1).getName());
                            assertTrue(
                                person
                                    .getFinalRecordComponents()
                                    .get(1)
                                    .getType()
                                    .is("java.lang.Object"));
                            assertEquals(
                                "TA;", person.getFinalRecordComponents().get(1).getSignature());
                            assertEquals(
                                0,
                                person.getFinalRecordComponents().get(1).getAnnotations().size());
                          } else {
                            assertEquals(0, person.getFinalRecordComponents().size());
                          }
                        }));
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    testForR8(parameters)
        .addInnerClassesAndStrippedOuter(getClass())
        .addLibraryFiles(ToolHelper.getAndroidJar(35))
        .addKeepMainRule(RecordWithSignature.class)
        .applyIf(keepSignatures, TestShrinkerBuilder::addKeepAttributeSignature)
        .compile()
        .inspect(
            inspector -> {
              ClassSubject person = inspector.clazz(RecordComponentSignatureTest.Person.class);
              FieldSubject age = person.uniqueFieldWithOriginalName("age");
              assertThat(
                  age, isAbsentOr(parameters.getPartialCompilationTestParameters().isRandom()));
              assertEquals(0, person.getFinalRecordComponents().size());
            })
        .run(parameters.getRuntime(), RecordWithSignature.class)
        .applyIf(
            runtimeWithRecordsSupport(parameters.getRuntime()),
            r ->
                r.assertSuccessWithOutput(
                    parameters.isDexRuntime()
                        ? EXPECTED_RESULT_DESUGARED_NATIVE_RECORD_SUPPORT
                        : EXPECTED_RESULT_R8),
            r -> r.assertSuccessWithOutput(EXPECTED_RESULT_DESUGARED_NO_NATIVE_RECORDS_SUPPORT));
  }

  record Person<N extends CharSequence, A>(N name, A age) {}

  public class RecordWithSignature {

    public static void main(String[] args) {
      Person<String, Integer> janeDoe = new Person<>("Jane Doe", 42);
      System.out.println(janeDoe.name());
      System.out.println(janeDoe.age());
      try {
        Class.class.getDeclaredMethod("isRecord");
      } catch (NoSuchMethodException e) {
        System.out.println("Class.isRecord not present");
        return;
      }
      System.out.println(Person.class.isRecord());
      if (Person.class.isRecord()) {
        System.out.println(Person.class.getRecordComponents().length);
        for (int i = 0; i < Person.class.getRecordComponents().length; i++) {
          RecordComponent c = Person.class.getRecordComponents()[i];
          System.out.println(c.getName());
          System.out.println(c.getType().getName());
          System.out.println(c.getGenericSignature());
          System.out.println(c.getAnnotations().length);
        }
      }
    }
  }
}
