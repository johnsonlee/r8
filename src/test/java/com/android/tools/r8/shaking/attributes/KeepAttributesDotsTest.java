// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.attributes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class KeepAttributesDotsTest extends TestBase {

  private final String keepAttributes;

  @Parameters(name = "-keepattributes {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withNoneRuntime().build(),
        new String[] {".", "...", "XYZ,..", "XYZ,..,A.B"});
  }

  public KeepAttributesDotsTest(TestParameters parameters, String keepAttributes) {
    parameters.assertNoneRuntime();
    this.keepAttributes = keepAttributes;
  }

  @Test
  public void testProguard() throws Exception {
    testForProguard()
        .addInnerClasses(getClass())
        .addKeepAllClassesRule()
        .addKeepAttributes(keepAttributes)
        .addDontWarn(KeepAttributesDotsTest.class)
        .run(TestRuntime.getCheckedInJdk9(), Main.class)
        .assertSuccessWithOutputLines("Hello World!")
        .inspect(inspector -> inspect(inspector, false));
  }

  @Test
  public void testR8() throws Exception {
    testForR8(Backend.CF)
        .addInnerClasses(getClass())
        .addKeepAttributes(keepAttributes)
        .addKeepAllClassesRule()
        .setMapIdTemplate("42")
        .run(TestRuntime.getCheckedInJdk9(), Main.class)
        .assertSuccessWithOutputLines("Hello World!")
        .inspect(inspector -> inspect(inspector, true));
  }

  private void inspect(CodeInspector inspector, boolean isR8) {
    ClassSubject clazz = inspector.clazz(Main.class);
    assertTrue(clazz.getDexProgramClass().annotations().isEmpty());
    MethodSubject main = clazz.uniqueMethodWithOriginalName("main");
    assertTrue(main.getMethod().annotations().isEmpty());
    FieldSubject field = clazz.uniqueFieldWithOriginalName("field");
    assertTrue(field.getField().annotations().isEmpty());
    if (isR8) {
      assertEquals("r8-map-id-42", clazz.getDexProgramClass().getSourceFile().toString());
    } else {
      assertNull(clazz.getDexProgramClass().getSourceFile());
    }
    assertTrue(main.getLocalVariableTable().isEmpty());
  }

  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.METHOD})
  public @interface MethodRuntimeAnnotation {}

  @Retention(RetentionPolicy.CLASS)
  @Target({ElementType.METHOD})
  public @interface MethodCompileTimeAnnotation {}

  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.TYPE})
  public @interface ClassRuntimeAnnotation {}

  @Retention(RetentionPolicy.CLASS)
  @Target({ElementType.TYPE})
  public @interface ClassCompileTimeAnnotation {}

  @ClassCompileTimeAnnotation
  @ClassRuntimeAnnotation
  public static class Main {

    public static class Inner<T> {}

    public Inner<Boolean> field;

    @MethodCompileTimeAnnotation
    @MethodRuntimeAnnotation
    public static void main(String[] args) {
      System.out.println("Hello World!");
    }
  }
}
