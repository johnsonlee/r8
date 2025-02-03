// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.keepanno.annotations.KeepBinding;
import com.android.tools.r8.keepanno.annotations.KeepEdge;
import com.android.tools.r8.keepanno.annotations.KeepTarget;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.lang.reflect.Field;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

@RunWith(Parameterized.class)
public class KeepConjunctiveBindingsWithInlineMembersTest extends KeepAnnoTestBase {

  static final String EXPECTED = StringUtils.lines("Hello, world");

  @Parameter public KeepAnnoParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static List<KeepAnnoParameters> data() {
    return createParameters(
        getTestParameters().withDefaultRuntimes().withMaximumApiLevel().build());
  }

  @Test
  public void test() throws Exception {
    SingleTestRunResult<?> result =
        testForKeepAnno(parameters)
            .addProgramClasses(getInputClasses())
            .addKeepMainRule(TestClass.class)
            .setExcludedOuterClass(getClass())
            .allowUnusedProguardConfigurationRules()
            .run(TestClass.class);
    if (parameters.isReference()) {
      result.assertSuccessWithOutput(EXPECTED);
    } else if (parameters.isPG()) {
      // PG will make the field private and result in access error.
      result.assertFailureWithErrorThatThrows(IllegalAccessException.class);
    } else {
      // R8 will remove the field.
      result.assertSuccessWithOutput("").inspect(this::checkOutput);
    }
  }

  public List<Class<?>> getInputClasses() {
    return ImmutableList.of(TestClass.class, A.class);
  }

  private void checkOutput(CodeInspector inspector) {
    assertThat(inspector.clazz(A.class), isPresent());
    assertThat(inspector.clazz(A.class).uniqueFieldWithOriginalName("fieldA"), isAbsent());
    assertThat(inspector.clazz(A.class).uniqueFieldWithOriginalName("fieldB"), isAbsent());
  }

  static class A {

    public String fieldA = "Hello, world";
    public Integer fieldB = 42;

    @KeepEdge(
        bindings = @KeepBinding(bindingName = "A", classConstant = A.class),
        consequences = {
          // The binding on A defines the required structure of A on input, thus the binding will
          // fail to find the required match when used. (This mirrors -keepclasseswithmembers).
          // Contrast this with the test in KeepDisjunctiveConsequencesTest.
          // This test should have equivalent behavior as KeepConjunctiveBindings and shows that
          // the binding on A is sufficient to impose the two member structure on A.
          @KeepTarget(classFromBinding = "A"),
          @KeepTarget(classFromBinding = "A", fieldTypeConstant = String.class),
          @KeepTarget(classFromBinding = "A", fieldType = "some.NonExistingClass")
        })
    public void foo() throws Exception {
      for (Field field : getClass().getDeclaredFields()) {
        if (field.getType().equals(String.class)) {
          System.out.println(field.get(this));
        }
      }
    }
  }

  static class TestClass {

    public static void main(String[] args) throws Exception {
      new A().foo();
    }
  }
}
