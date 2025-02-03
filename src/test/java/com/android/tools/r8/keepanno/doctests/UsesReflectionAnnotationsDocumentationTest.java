// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.doctests;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.keepanno.annotations.AnnotationPattern;
import com.android.tools.r8.keepanno.annotations.KeepConstraint;
import com.android.tools.r8.keepanno.annotations.KeepTarget;
import com.android.tools.r8.keepanno.annotations.UsesReflection;
import com.android.tools.r8.keepanno.doctests.UsesReflectionAnnotationsDocumentationTest.Example1.MyAnnotationPrinter.MyClass;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.google.common.collect.ImmutableList;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class UsesReflectionAnnotationsDocumentationTest extends TestBase {

  static final String EXPECTED = StringUtils.lines("fieldOne = 1", "fieldTwo = 2");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultRuntimes().withMaximumApiLevel().build();
  }

  public UsesReflectionAnnotationsDocumentationTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testReference() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(TestClass.class)
        .addProgramClassesAndInnerClasses(getExampleClasses())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testWithRuleExtraction() throws Exception {
    testForR8(parameters.getBackend())
        .enableExperimentalKeepAnnotations()
        .addProgramClasses(TestClass.class)
        .addProgramClassesAndInnerClasses(getExampleClasses())
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED)
        .inspect(
            inspector -> {
              ClassSubject clazz = inspector.clazz(MyClass.class);
              assertThat(clazz.uniqueFieldWithOriginalName("mFieldOne"), isPresentAndRenamed());
              assertThat(clazz.uniqueFieldWithOriginalName("mFieldTwo"), isPresentAndRenamed());
              assertThat(clazz.uniqueFieldWithOriginalName("mFieldThree"), isAbsent());
            });
  }

  public List<Class<?>> getExampleClasses() {
    return ImmutableList.of(Example1.class);
  }

  static class Example1 {

    /* INCLUDE DOC: UsesReflectionOnAnnotations
    If your program is reflectively inspecting annotations on classes, methods or fields, you
    will need to declare additional "annotation constraints" about what assumptions are made
    about the annotations.

    In the following example, we have defined an annotation that will record the printing name we
    would like to use for fields instead of printing the concrete field name. That may be useful
    so that the field can be renamed to follow coding conventions for example.

    We are only interested in matching objects that contain fields annotated by `MyNameAnnotation`,
    that is specified using `@KeepTarget#fieldAnnotatedByClassConstant`.

    At runtime we need to be able to find the annotation too, so we add a constraint on the
    annotation using `@KeepTarget#constrainAnnotations`.

    Finally, for the sake of example, we don't actually care about the name of the fields
    themselves, so we explicitly declare the smaller set of constraints to be
    `@KeepConstraint#LOOKUP` since we must find the fields via `Class.getDeclaredFields` as well as
    `@KeepConstraint#VISIBILITY_RELAX` and `@KeepConstraint#FIELD_GET` in order to be able to get
    the actual field value without accessibility errors.

    The effect is that the default constraint `@KeepConstraint#NAME` is not specified which allows
    the shrinker to rename the fields at will.
    INCLUDE END */

    static
    // INCLUDE CODE: UsesReflectionOnAnnotations
    public class MyAnnotationPrinter {

      @Target(ElementType.FIELD)
      @Retention(RetentionPolicy.RUNTIME)
      public @interface MyNameAnnotation {
        String value();
      }

      public static class MyClass {
        @MyNameAnnotation("fieldOne")
        public int mFieldOne = 1;

        @MyNameAnnotation("fieldTwo")
        public int mFieldTwo = 2;

        public int mFieldThree = 3;
      }

      @UsesReflection(
          @KeepTarget(
              fieldAnnotatedByClassConstant = MyNameAnnotation.class,
              constrainAnnotations = @AnnotationPattern(constant = MyNameAnnotation.class),
              constraints = {
                KeepConstraint.LOOKUP,
                KeepConstraint.VISIBILITY_RELAX,
                KeepConstraint.FIELD_GET
              }))
      public void printMyNameAnnotatedFields(Object obj) throws Exception {
        for (Field field : obj.getClass().getDeclaredFields()) {
          if (field.isAnnotationPresent(MyNameAnnotation.class)) {
            System.out.println(
                field.getAnnotation(MyNameAnnotation.class).value() + " = " + field.get(obj));
          }
        }
      }
    }

    // INCLUDE END

    static void run() throws Exception {
      new MyAnnotationPrinter().printMyNameAnnotatedFields(new MyAnnotationPrinter.MyClass());
    }
  }

  static class TestClass {

    public static void main(String[] args) throws Exception {
      Example1.run();
    }
  }
}
