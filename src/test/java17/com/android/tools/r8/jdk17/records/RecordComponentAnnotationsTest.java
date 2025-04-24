// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.jdk17.records;

import static com.android.tools.r8.utils.codeinspector.AnnotationMatchers.hasAnnotationTypes;
import static com.android.tools.r8.utils.codeinspector.AnnotationMatchers.hasElements;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndNotRenamed;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.TestShrinkerBuilder;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.Pair;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RecordComponentAnnotationsTest extends TestBase {

  private static final String ANNOTATION_PREFIX =
      "@" + RecordComponentAnnotationsTest.class.getTypeName() + "$";
  private static final String JVM_UNTIL_20_EXPECTED_RESULT =
      StringUtils.lines(
          "Jane Doe",
          "42",
          "true",
          "2",
          "name",
          "java.lang.String",
          "true",
          "2",
          ANNOTATION_PREFIX + "Annotation(\"a\")",
          ANNOTATION_PREFIX + "AnnotationRecordComponentOnly(\"c\")",
          "age",
          "int",
          "true",
          "2",
          ANNOTATION_PREFIX + "Annotation(\"x\")",
          ANNOTATION_PREFIX + "AnnotationRecordComponentOnly(\"z\")",
          "2",
          "2",
          ANNOTATION_PREFIX + "Annotation(\"x\")",
          ANNOTATION_PREFIX + "AnnotationFieldOnly(\"y\")",
          "2",
          ANNOTATION_PREFIX + "Annotation(\"a\")",
          ANNOTATION_PREFIX + "AnnotationFieldOnly(\"b\")");
  private static final String JVM_FROM_21_EXPECTED_RESULT =
      JVM_UNTIL_20_EXPECTED_RESULT.replaceAll(
          "records.RecordComponentAnnotationsTest\\$Annotation",
          "records.RecordComponentAnnotationsTest.Annotation");
  private static final String ART_EXPECTED_RESULT =
      StringUtils.lines(
          "Jane Doe",
          "42",
          "true",
          "2",
          "name",
          "java.lang.String",
          "true",
          "2",
          ANNOTATION_PREFIX + "Annotation(value=a)",
          ANNOTATION_PREFIX + "AnnotationRecordComponentOnly(value=c)",
          "age",
          "int",
          "true",
          "2",
          ANNOTATION_PREFIX + "Annotation(value=x)",
          ANNOTATION_PREFIX + "AnnotationRecordComponentOnly(value=z)",
          "2",
          "2",
          ANNOTATION_PREFIX + "Annotation(value=x)",
          ANNOTATION_PREFIX + "AnnotationFieldOnly(value=y)",
          "2",
          ANNOTATION_PREFIX + "Annotation(value=a)",
          ANNOTATION_PREFIX + "AnnotationFieldOnly(value=b)");
  private static final String JVM_EXPECTED_RESULT_R8 =
      StringUtils.lines(
          "Jane Doe",
          "42",
          "true",
          "2",
          "a",
          "java.lang.String",
          "true",
          "2",
          ANNOTATION_PREFIX + "Annotation(\"a\")",
          ANNOTATION_PREFIX + "AnnotationRecordComponentOnly(\"c\")",
          "b",
          "int",
          "true",
          "2",
          ANNOTATION_PREFIX + "Annotation(\"x\")",
          ANNOTATION_PREFIX + "AnnotationRecordComponentOnly(\"z\")",
          "2",
          "2",
          ANNOTATION_PREFIX + "Annotation(\"a\")",
          ANNOTATION_PREFIX + "AnnotationFieldOnly(\"b\")",
          "2",
          ANNOTATION_PREFIX + "Annotation(\"x\")",
          ANNOTATION_PREFIX + "AnnotationFieldOnly(\"y\")");
  private static final String ART_EXPECTED_RESULT_R8 =
      StringUtils.lines(
          "Jane Doe",
          "42",
          "true",
          "2",
          "a",
          "java.lang.String",
          "true",
          "2",
          ANNOTATION_PREFIX + "Annotation(value=a)",
          ANNOTATION_PREFIX + "AnnotationRecordComponentOnly(value=c)",
          "b",
          "int",
          "true",
          "2",
          ANNOTATION_PREFIX + "Annotation(value=x)",
          ANNOTATION_PREFIX + "AnnotationRecordComponentOnly(value=z)",
          "2",
          "2",
          ANNOTATION_PREFIX + "Annotation(value=a)",
          ANNOTATION_PREFIX + "AnnotationFieldOnly(value=b)",
          "2",
          ANNOTATION_PREFIX + "Annotation(value=x)",
          ANNOTATION_PREFIX + "AnnotationFieldOnly(value=y)");
  private static final String JVM_EXPECTED_RESULT_R8_NO_KEEP_ANNOTATIONS =
      StringUtils.lines(
          "Jane Doe",
          "42",
          "true",
          "2",
          "a",
          "java.lang.String",
          "true",
          "2",
          ANNOTATION_PREFIX + "Annotation(\"a\")",
          ANNOTATION_PREFIX + "AnnotationRecordComponentOnly(\"c\")",
          "b",
          "int",
          "true",
          "2",
          ANNOTATION_PREFIX + "Annotation(\"x\")",
          ANNOTATION_PREFIX + "AnnotationRecordComponentOnly(\"z\")",
          "2",
          "0",
          "0");
  private static final String ART_EXPECTED_RESULT_R8_NO_KEEP_ANNOTATIONS =
      StringUtils.lines(
          "Jane Doe",
          "42",
          "true",
          "2",
          "a",
          "java.lang.String",
          "true",
          "2",
          ANNOTATION_PREFIX + "Annotation(value=a)",
          ANNOTATION_PREFIX + "AnnotationRecordComponentOnly(value=c)",
          "b",
          "int",
          "true",
          "2",
          ANNOTATION_PREFIX + "Annotation(value=x)",
          ANNOTATION_PREFIX + "AnnotationRecordComponentOnly(value=z)",
          "2",
          "0",
          "0");
  private static final String EXPECTED_RESULT_DESUGARED_RECORD_SUPPORT =
      StringUtils.lines("Jane Doe", "42", "false");
  private static final String EXPECTED_RESULT_DESUGARED_NO_RECORD_SUPPORT =
      StringUtils.lines("Jane Doe", "42", "Class.isRecord not present");

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public Boolean keepAnnotations;

  // Enable once records are no longer partially desugared on platform.
  public boolean recordDesugaringIsOffOnDex = false;

  @Parameters(name = "{0}, keepAnnotations: {1}")
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
    assumeTrue(keepAnnotations);
    testForJvm(parameters)
        .addInnerClassesAndStrippedOuter(getClass())
        .run(parameters.getRuntime(), RecordWithAnnotations.class)
        .assertSuccessWithOutput(
            parameters.getRuntime().asCf().getVm().isLessThanOrEqualTo(CfVm.JDK20)
                ? JVM_UNTIL_20_EXPECTED_RESULT
                : JVM_FROM_21_EXPECTED_RESULT);
  }

  @Test
  public void testDesugaring() throws Exception {
    parameters.assumeDexRuntime();
    assumeTrue(keepAnnotations);
    testForDesugaring(parameters)
        .addInnerClassesAndStrippedOuter(getClass())
        .run(parameters.getRuntime(), RecordWithAnnotations.class)
        .applyIf(
            parameters.isDexRuntime(),
            r ->
                r.assertSuccessWithOutput(
                    runtimeWithRecordsSupport(parameters.getRuntime())
                        ? EXPECTED_RESULT_DESUGARED_RECORD_SUPPORT
                        : EXPECTED_RESULT_DESUGARED_NO_RECORD_SUPPORT),
            r ->
                r.assertSuccessWithOutput(ART_EXPECTED_RESULT)
                    .inspect(
                        inspector -> {
                          ClassSubject person =
                              inspector.clazz("records.RecordComponentAnnotationsTest$Person");
                          FieldSubject name = person.uniqueFieldWithOriginalName("name");
                          assertThat(name, isPresentAndNotRenamed());
                          FieldSubject age = person.uniqueFieldWithOriginalName("age");
                          assertThat(age, isPresentAndNotRenamed());
                          assertEquals(2, person.getFinalRecordComponents().size());

                          assertEquals(
                              name.getFinalName(),
                              person.getFinalRecordComponents().get(0).getName());
                          assertTrue(
                              person
                                  .getFinalRecordComponents()
                                  .get(0)
                                  .getType()
                                  .is("java.lang.String"));
                          assertNull(person.getFinalRecordComponents().get(0).getSignature());
                          assertEquals(
                              2, person.getFinalRecordComponents().get(0).getAnnotations().size());
                          assertThat(
                              person.getFinalRecordComponents().get(0).getAnnotations(),
                              hasAnnotationTypes(
                                  inspector.getTypeSubject(
                                      "records.RecordComponentAnnotationsTest$Annotation"),
                                  inspector.getTypeSubject(
                                      "records.RecordComponentAnnotationsTest$AnnotationRecordComponentOnly")));
                          assertThat(
                              person.getFinalRecordComponents().get(0).getAnnotations().get(0),
                              hasElements(new Pair<>("value", "a")));
                          assertThat(
                              person.getFinalRecordComponents().get(0).getAnnotations().get(1),
                              hasElements(new Pair<>("value", "c")));

                          assertEquals(
                              age.getFinalName(),
                              person.getFinalRecordComponents().get(1).getName());
                          assertTrue(person.getFinalRecordComponents().get(1).getType().is("int"));
                          assertNull(person.getFinalRecordComponents().get(1).getSignature());
                          assertEquals(
                              2, person.getFinalRecordComponents().get(1).getAnnotations().size());
                          assertThat(
                              person.getFinalRecordComponents().get(1).getAnnotations(),
                              hasAnnotationTypes(
                                  inspector.getTypeSubject(
                                      "records.RecordComponentAnnotationsTest$Annotation"),
                                  inspector.getTypeSubject(
                                      "records.RecordComponentAnnotationsTest$AnnotationRecordComponentOnly")));
                          assertThat(
                              person.getFinalRecordComponents().get(1).getAnnotations().get(0),
                              hasElements(new Pair<>("value", "x")));
                          assertThat(
                              person.getFinalRecordComponents().get(1).getAnnotations().get(1),
                              hasElements(new Pair<>("value", "z")));
                        }));
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    testForR8(parameters)
        .addInnerClassesAndStrippedOuter(getClass())
        .addLibraryFiles(ToolHelper.getAndroidJar(35))
        .addKeepMainRule(RecordWithAnnotations.class)
        .addKeepClassAndMembersRulesWithAllowObfuscation(
            RecordComponentAnnotationsTest.Person.class)
        .addKeepClassAndMembersRules(
            RecordComponentAnnotationsTest.Annotation.class,
            RecordComponentAnnotationsTest.AnnotationFieldOnly.class,
            RecordComponentAnnotationsTest.AnnotationRecordComponentOnly.class)
        .applyIf(keepAnnotations, TestShrinkerBuilder::addKeepRuntimeVisibleAnnotations)
        .compile()
        .inspect(
            inspector -> {
              ClassSubject person = inspector.clazz(RecordComponentAnnotationsTest.Person.class);
              FieldSubject name = person.uniqueFieldWithOriginalName("name");
              FieldSubject age = person.uniqueFieldWithOriginalName("age");
              if (parameters.isCfRuntime()) {
                assertEquals(2, person.getFinalRecordComponents().size());

                assertEquals(
                    name.getFinalName(), person.getFinalRecordComponents().get(0).getName());
                assertTrue(
                    person.getFinalRecordComponents().get(0).getType().is("java.lang.String"));
                assertNull(person.getFinalRecordComponents().get(0).getSignature());
                assertEquals(2, person.getFinalRecordComponents().get(0).getAnnotations().size());
                assertThat(
                    person.getFinalRecordComponents().get(0).getAnnotations(),
                    hasAnnotationTypes(
                        inspector.getTypeSubject(
                            RecordComponentAnnotationsTest.Annotation.class.getTypeName()),
                        inspector.getTypeSubject(
                            RecordComponentAnnotationsTest.AnnotationRecordComponentOnly.class
                                .getTypeName())));
                assertThat(
                    person.getFinalRecordComponents().get(0).getAnnotations().get(0),
                    hasElements(new Pair<>("value", "a")));
                assertThat(
                    person.getFinalRecordComponents().get(0).getAnnotations().get(1),
                    hasElements(new Pair<>("value", "c")));

                assertEquals(
                    age.getFinalName(), person.getFinalRecordComponents().get(1).getName());
                assertTrue(person.getFinalRecordComponents().get(1).getType().is("int"));
                assertNull(person.getFinalRecordComponents().get(1).getSignature());
                assertEquals(2, person.getFinalRecordComponents().get(1).getAnnotations().size());
                assertThat(
                    person.getFinalRecordComponents().get(1).getAnnotations(),
                    hasAnnotationTypes(
                        inspector.getTypeSubject(
                            RecordComponentAnnotationsTest.Annotation.class.getTypeName()),
                        inspector.getTypeSubject(
                            RecordComponentAnnotationsTest.AnnotationRecordComponentOnly.class
                                .getTypeName())));
                assertThat(
                    person.getFinalRecordComponents().get(1).getAnnotations().get(0),
                    hasElements(new Pair<>("value", "x")));
                assertThat(
                    person.getFinalRecordComponents().get(1).getAnnotations().get(1),
                    hasElements(new Pair<>("value", "z")));
              } else {
                assertEquals(0, person.getFinalRecordComponents().size());
              }
            })
        .run(parameters.getRuntime(), RecordWithAnnotations.class)
        .applyIf(
            // TODO(b/274888318): EXPECTED_RESULT_R8_NO_KEEP_ANNOTATIONS still has component
            //  annotations.
            parameters.isCfRuntime(),
            r ->
                r.assertSuccessWithOutput(
                    keepAnnotations
                        ? JVM_EXPECTED_RESULT_R8
                        : JVM_EXPECTED_RESULT_R8_NO_KEEP_ANNOTATIONS),
            recordDesugaringIsOffOnDex,
            r ->
                r.assertSuccessWithOutput(
                    keepAnnotations
                        ? ART_EXPECTED_RESULT_R8
                        : ART_EXPECTED_RESULT_R8_NO_KEEP_ANNOTATIONS),
            r ->
                r.assertSuccessWithOutput(
                    runtimeWithRecordsSupport(parameters.getRuntime())
                        ? EXPECTED_RESULT_DESUGARED_RECORD_SUPPORT
                        : EXPECTED_RESULT_DESUGARED_NO_RECORD_SUPPORT));
  }

  @Target({ElementType.FIELD, ElementType.RECORD_COMPONENT})
  @Retention(RetentionPolicy.RUNTIME)
  @interface Annotation {

    String value();
  }

  @Target(ElementType.FIELD)
  @Retention(RetentionPolicy.RUNTIME)
  @interface AnnotationFieldOnly {

    String value();
  }

  @Target(ElementType.RECORD_COMPONENT)
  @Retention(RetentionPolicy.RUNTIME)
  @interface AnnotationRecordComponentOnly {

    String value();
  }

  record Person(
      @Annotation("a") @AnnotationFieldOnly("b") @AnnotationRecordComponentOnly("c") String name,
      @Annotation("x") @AnnotationFieldOnly("y") @AnnotationRecordComponentOnly("z") int age) {}

  public static class RecordWithAnnotations {

    public static void main(String[] args) {
      Person janeDoe = new Person("Jane Doe", 42);
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
          System.out.println(c.getGenericSignature() == null);
          System.out.println(c.getAnnotations().length);
          // Collect and sort the annotations, as the order is not deterministic on Art (tested
          // on Art 14 Beta 3).
          List<String> annotations = new ArrayList<>();
          for (int j = 0; j < c.getAnnotations().length; j++) {
            annotations.add(c.getAnnotations()[j].toString());
          }
          annotations.sort(Comparator.naturalOrder());
          for (int j = 0; j < annotations.size(); j++) {
            System.out.println(annotations.get(j));
          }
        }
        System.out.println(Person.class.getDeclaredFields().length);
        List<Field> fields = new ArrayList<>();
        for (int i = 0; i < Person.class.getDeclaredFields().length; i++) {
          fields.add(Person.class.getDeclaredFields()[i]);
        }
        fields.sort(
            new Comparator<Field>() {
              @Override
              public int compare(Field o1, Field o2) {
                return o1.getName().compareTo(o2.getName());
              }
            });
        for (int i = 0; i < fields.size(); i++) {
          Field f = fields.get(i);
          System.out.println(f.getDeclaredAnnotations().length);
          List<String> annotations = new ArrayList<>();
          for (int j = 0; j < f.getDeclaredAnnotations().length; j++) {
            annotations.add(f.getAnnotations()[j].toString());
          }
          annotations.sort(Comparator.naturalOrder());
          for (int j = 0; j < annotations.size(); j++) {
            System.out.println(annotations.get(j));
          }
        }
      }
    }
  }
}
