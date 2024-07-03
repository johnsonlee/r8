// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.doctests;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.keepanno.annotations.KeepCondition;
import com.android.tools.r8.keepanno.annotations.KeepConstraint;
import com.android.tools.r8.keepanno.annotations.KeepItemKind;
import com.android.tools.r8.keepanno.annotations.KeepTarget;
import com.android.tools.r8.keepanno.annotations.UsedByReflection;
import com.android.tools.r8.keepanno.annotations.UsesReflection;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.lang.reflect.Field;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class UsesReflectionDocumentationTest extends TestBase {

  static final String EXPECTED_METHOD_EXAMPLE = StringUtils.joinLines("on Base", "on Sub");
  static final String EXPECTED_FIELD_EXAMPLE =
      StringUtils.joinLines("intField = 42", "stringField = Hello!");

  static final String EXPECTED =
      StringUtils.lines(
          EXPECTED_METHOD_EXAMPLE,
          EXPECTED_FIELD_EXAMPLE,
          EXPECTED_FIELD_EXAMPLE,
          EXPECTED_FIELD_EXAMPLE,
          EXPECTED_FIELD_EXAMPLE,
          EXPECTED_FIELD_EXAMPLE);

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultRuntimes().withApiLevel(AndroidApiLevel.B).build();
  }

  public UsesReflectionDocumentationTest(TestParameters parameters) {
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
        .assertSuccessWithOutput(EXPECTED);
  }

  public List<Class<?>> getExampleClasses() {
    return ImmutableList.of(
        Example1.class,
        Example2.class,
        Example2WithConstraints.class,
        Example3.class,
        Example4.class,
        Example5.class);
  }

  static class Example1 {

    static class BaseClass {
      void hiddenMethod() {
        System.out.println("on Base");
      }
    }

    static class SubClass extends BaseClass {
      void hiddenMethod() {
        System.out.println("on Sub");
      }
    }

    /* INCLUDE DOC: UsesReflectionOnVirtualMethod
    For example, if your program is reflectively invoking a method, you
    should annotate the method that is doing the reflection. The annotation must describe the
    assumptions the reflective code makes.

    In the following example, the method `callHiddenMethod` is looking up the method with the name
    `hiddenMethod` on objects that are instances of `BaseClass`. It is then invoking the method with
    no other arguments than the receiver.

    The assumptions the code makes are that all methods with the name
    `hiddenMethod` and the empty list of parameters must remain valid for `getDeclaredMethod` if they
    are objects that are instances of the class `BaseClass` or subclasses thereof.
    INCLUDE END */

    static
    // INCLUDE CODE: UsesReflectionOnVirtualMethod
    public class MyHiddenMethodCaller {

      @UsesReflection(
          @KeepTarget(
              instanceOfClassConstant = BaseClass.class,
              methodName = "hiddenMethod",
              methodParameters = {}))
      public void callHiddenMethod(BaseClass base) throws Exception {
        base.getClass().getDeclaredMethod("hiddenMethod").invoke(base);
      }
    }

    // INCLUDE END

    static void run() throws Exception {
      new MyHiddenMethodCaller().callHiddenMethod(new BaseClass());
      new MyHiddenMethodCaller().callHiddenMethod(new SubClass());
    }
  }

  static class Example2 {

    interface PrintableFieldInterface {}

    static class ClassWithFields implements PrintableFieldInterface {
      final int intField = 42;
      String stringField = "Hello!";
    }

    /* INCLUDE DOC: UsesReflectionFieldPrinter
    For example, if your program is reflectively accessing the fields on a class, you should
    annotate the method that is doing the reflection.

    In the following example, the `printFieldValues` method takes in an object of
    type `PrintableFieldInterface` and then looks for all the fields declared on the class
    of the object.

    The `@KeepTarget` describes these field targets. Since the printing only cares about preserving
    the fields, the `@KeepTarget#kind` is set to `@KeepItemKind#ONLY_FIELDS`.
    INCLUDE END */

    public
    // INCLUDE CODE: UsesReflectionFieldPrinter
    static class MyFieldValuePrinter {

      @UsesReflection(
          @KeepTarget(
              instanceOfClassConstant = PrintableFieldInterface.class,
              kind = KeepItemKind.ONLY_FIELDS))
      public void printFieldValues(PrintableFieldInterface objectWithFields) throws Exception {
        for (Field field : objectWithFields.getClass().getDeclaredFields()) {
          System.out.println(field.getName() + " = " + field.get(objectWithFields));
        }
      }
    }

    // INCLUDE END

    static void run() throws Exception {
      new MyFieldValuePrinter().printFieldValues(new ClassWithFields());
    }
  }

  static class Example2WithConstraints {

    interface PrintableFieldInterface {}

    static class ClassWithFields implements PrintableFieldInterface {
      final int intField = 42;
      String stringField = "Hello!";
    }

    /* INCLUDE DOC: UsesReflectionFieldPrinterWithConstraints
    Let us revisit the example reflectively accessing the fields on a class.

    Notice that printing the field names and values only requires looking up the field, printing
    its name and getting its value. It does not require setting a new value on the field.
    We can thus use a more restrictive set of constraints
    by setting the `@KeepTarget#constraints` property to just `@KeepConstraint#LOOKUP`,
    `@KeepConstraint#NAME` and `@KeepConstraint#FIELD_GET`.
    INCLUDE END */

    public
    // INCLUDE CODE: UsesReflectionFieldPrinterWithConstraints
    static class MyFieldValuePrinter {

      @UsesReflection(
          @KeepTarget(
              instanceOfClassConstant = PrintableFieldInterface.class,
              kind = KeepItemKind.ONLY_FIELDS,
              constraints = {KeepConstraint.LOOKUP, KeepConstraint.NAME, KeepConstraint.FIELD_GET}))
      public void printFieldValues(PrintableFieldInterface objectWithFields) throws Exception {
        for (Field field : objectWithFields.getClass().getDeclaredFields()) {
          System.out.println(field.getName() + " = " + field.get(objectWithFields));
        }
      }
    }

    // INCLUDE END

    static void run() throws Exception {
      new MyFieldValuePrinter().printFieldValues(new ClassWithFields());
    }
  }

  static class Example3 {

    interface PrintableFieldInterface {}

    /* INCLUDE DOC: UsedByReflectionFieldPrinterOnFields
    For example, the same field printing as in the above example might be part of a library.

    In this example, the `MyClassWithFields` is a class you are passing to the
    field-printing utility of the library. Since the library is reflectively accessing each field
    we annotate them with the `@UsedByReflection` annotation.
    INCLUDE END */

    static
    // INCLUDE CODE: UsedByReflectionFieldPrinterOnFields
    public class MyClassWithFields implements PrintableFieldInterface {
      @UsedByReflection final int intField = 42;

      @UsedByReflection String stringField = "Hello!";
    }

    public static void run() throws Exception {
      new FieldValuePrinterLibrary().printFieldValues(new MyClassWithFields());
    }

    // INCLUDE END

    public static class FieldValuePrinterLibrary {

      public void printFieldValues(PrintableFieldInterface objectWithFields) throws Exception {
        for (Field field : objectWithFields.getClass().getDeclaredFields()) {
          System.out.println(field.getName() + " = " + field.get(objectWithFields));
        }
      }
    }
  }

  static class Example4 {

    interface PrintableFieldInterface {}

    /* INCLUDE DOC: UsedByReflectionFieldPrinterOnClass
    Rather than annotate the individual fields we can annotate the holder and add a specification
    similar to the `@KeepTarget`. The `@UsedByReflection#kind` specifies that only the fields are
    used reflectively. In particular, the "field printer" example we are considering here does not
    make reflective assumptions about the holder class, so we should not constrain it.
    INCLUDE END */

    static
    // INCLUDE CODE: UsedByReflectionFieldPrinterOnClass
    @UsedByReflection(kind = KeepItemKind.ONLY_FIELDS) public class MyClassWithFields
        implements PrintableFieldInterface {
      final int intField = 42;
      String stringField = "Hello!";
    }

    // INCLUDE END

    public static void run() throws Exception {
      new FieldValuePrinterLibrary().printFieldValues(new MyClassWithFields());
    }

    public static class FieldValuePrinterLibrary {

      public void printFieldValues(PrintableFieldInterface objectWithFields) throws Exception {
        for (Field field : objectWithFields.getClass().getDeclaredFields()) {
          System.out.println(field.getName() + " = " + field.get(objectWithFields));
        }
      }
    }
  }

  static class Example5 {

    interface PrintableFieldInterface {}

    /* INCLUDE DOC: UsedByReflectionFieldPrinterConditional
    Our use of `@UsedByReflection` is still not as flexible as the original `@UsesReflection`. In
    particular, if we change our code to no longer have any call to the library method
    `printFieldValues` the shrinker will still keep all of the fields on our annotated class.

    This is because the `@UsesReflection` implicitly encodes as a precondition that the annotated
    method is actually used in the program. If not, the `@UsesReflection` annotation is not
    "active".

    Luckily we can specify the same precondition using `@UsedByReflection#preconditions`.
    INCLUDE END */

    static
    // INCLUDE CODE: UsedByReflectionFieldPrinterConditional
    @UsedByReflection(
        preconditions = {
          @KeepCondition(
              classConstant = FieldValuePrinterLibrary.class,
              methodName = "printFieldValues")
        },
        kind = KeepItemKind.ONLY_FIELDS) public class MyClassWithFields
        implements PrintableFieldInterface {
      final int intField = 42;
      String stringField = "Hello!";
    }

    // INCLUDE END

    public static void run() throws Exception {
      new FieldValuePrinterLibrary().printFieldValues(new MyClassWithFields());
    }

    public static class FieldValuePrinterLibrary {

      public void printFieldValues(PrintableFieldInterface objectWithFields) throws Exception {
        for (Field field : objectWithFields.getClass().getDeclaredFields()) {
          System.out.println(field.getName() + " = " + field.get(objectWithFields));
        }
      }
    }
  }

  static class TestClass {

    public static void main(String[] args) throws Exception {
      Example1.run();
      Example2.run();
      Example2WithConstraints.run();
      Example3.run();
      Example4.run();
      Example5.run();
    }
  }
}
