// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndNotRenamed;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.keepanno.annotations.ClassNamePattern;
import com.android.tools.r8.keepanno.annotations.KeepConstraint;
import com.android.tools.r8.keepanno.annotations.KeepItemKind;
import com.android.tools.r8.keepanno.annotations.KeepTarget;
import com.android.tools.r8.keepanno.annotations.UsedByReflection;
import com.android.tools.r8.keepanno.annotations.UsesReflection;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ClassAnnotatedByAnyAnnoPatternTest extends TestBase {

  static final String EXPECTED = StringUtils.lines("C1: A1", "C2: A2");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultRuntimes().withApiLevel(AndroidApiLevel.B).build();
  }

  public ClassAnnotatedByAnyAnnoPatternTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testReference() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(getInputClasses())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .enableExperimentalKeepAnnotations()
        .addProgramClasses(getInputClasses())
        .setMinApi(parameters)
        // TODO(b/248408342): Make this implicit when annotations are kept by the keep-annotation.
        .addKeepRuntimeVisibleAnnotations()
        .compile()
        .apply(b -> System.out.println(b.getProguardConfiguration()))
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED)
        .inspect(this::checkOutput);
  }

  public List<Class<?>> getInputClasses() {
    return ImmutableList.of(
        TestClass.class, Reflector.class, A1.class, A2.class, C1.class, C2.class, C3.class);
  }

  private void checkOutput(CodeInspector inspector) {
    // The class constant use will ensure the annotations remains.
    // They are not renamed as the keep-annotation will match them (they are themselves annotated).
    assertThat(inspector.clazz(A1.class), isPresentAndNotRenamed());
    assertThat(inspector.clazz(A2.class), isPresentAndNotRenamed());
    // The first two classes are annotated so the keep-annotation applies and retains their name.
    assertThat(inspector.clazz(C1.class), isPresentAndNotRenamed());
    assertThat(inspector.clazz(C2.class), isPresentAndNotRenamed());
    // The last class will remain due to the class constant, but it is optimized/renamed.
    assertThat(inspector.clazz(C3.class), isPresentAndRenamed());
  }

  @Target(ElementType.TYPE)
  @Retention(RetentionPolicy.RUNTIME)
  @interface A1 {}

  @Target(ElementType.TYPE)
  @Retention(RetentionPolicy.RUNTIME)
  @interface A2 {}

  static class Reflector {

    @UsesReflection({
      @KeepTarget(
          classAnnotatedByClassNamePattern = @ClassNamePattern,
          constraints = {KeepConstraint.ANNOTATIONS, KeepConstraint.NAME}),
    })
    public void foo(Class<?>... classes) throws Exception {
      for (Class<?> clazz : classes) {
        if (clazz.getAnnotations().length > 0) {
          String typeName = clazz.getTypeName();
          System.out.print(typeName.substring(typeName.lastIndexOf('$') + 1) + ":");
          if (clazz.isAnnotationPresent(A1.class)) {
            System.out.print(" A1");
          }
          if (clazz.isAnnotationPresent(A2.class)) {
            System.out.print(" A2");
          }
          System.out.println();
        }
      }
    }
  }

  @A1
  static class C1 {}

  @A2
  static class C2 {}

  static class C3 {}

  static class TestClass {

    @UsedByReflection(kind = KeepItemKind.CLASS_AND_METHODS)
    public static void main(String[] args) throws Exception {
      new Reflector().foo(C1.class, C2.class, C3.class);
    }
  }
}
