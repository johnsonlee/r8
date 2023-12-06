// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.doctests;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.keepanno.annotations.KeepConstraint;
import com.android.tools.r8.keepanno.annotations.KeepItemKind;
import com.android.tools.r8.keepanno.annotations.KeepTarget;
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

  static final String EXPECTED =
      StringUtils.lines("on Base", "on Sub", "intField = 42", "stringField = Hello!");

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
    return ImmutableList.of(Example1.class, Example2.class);
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

      @UsesReflection({
        @KeepTarget(
            instanceOfClassConstant = BaseClass.class,
            methodName = "hiddenMethod",
            methodParameters = {})
      })
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
    the fields, the `@KeepTarget#kind` is set to `@KeepItemKind#ONLY_FIELDS`. Also, since printing
    the field names and values only requires looking up the field, printing its name and getting
    its value the `@KeepTarget#constraints` are set to just `@KeepConstraint#LOOKUP`,
    `@KeepConstraint#NAME` and `@KeepConstraint#FIELD_GET`.
    INCLUDE END */

    static
    // INCLUDE CODE: UsesReflectionFieldPrinter
    public class MyFieldValuePrinter {

      @UsesReflection({
        @KeepTarget(
            instanceOfClassConstant = PrintableFieldInterface.class,
            kind = KeepItemKind.ONLY_FIELDS,
            constraints = {KeepConstraint.LOOKUP, KeepConstraint.NAME, KeepConstraint.FIELD_GET})
      })
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

  static class TestClass {

    public static void main(String[] args) throws Exception {
      Example1.run();
      Example2.run();
    }
  }
}
