// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndNotRenamed;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.keepanno.annotations.KeepItemKind;
import com.android.tools.r8.keepanno.annotations.KeepTarget;
import com.android.tools.r8.keepanno.annotations.UsedByReflection;
import com.android.tools.r8.keepanno.annotations.UsesReflection;
import com.android.tools.r8.transformers.ClassFileTransformer.MethodPredicate;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

@RunWith(Parameterized.class)
public class KeepEmptyClassTest extends KeepAnnoTestBase {

  static final String EXPECTED = StringUtils.lines("B, #members: 0");

  @Parameter public KeepAnnoParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static List<KeepAnnoParameters> data() {
    return createParameters(
        getTestParameters().withDefaultRuntimes().withMaximumApiLevel().build());
  }

  @Test
  public void test() throws Exception {
    testForKeepAnno(parameters)
        .addProgramClasses(getInputClasses())
        .addProgramClassFileData(
            transformer(B.class).removeMethods(MethodPredicate.all()).transform())
        .setExcludedOuterClass(getClass())
        .run(TestClass.class)
        .assertSuccessWithOutput(EXPECTED)
        .applyIf(parameters.isShrinker(), r -> r.inspect(this::checkOutput));
  }

  public List<Class<?>> getInputClasses() {
    return ImmutableList.of(TestClass.class, A.class);
  }

  private void checkOutput(CodeInspector inspector) {
    assertThat(inspector.clazz(B.class), isPresentAndNotRenamed());
    ClassSubject classA = inspector.clazz(A.class);
    if (parameters.isNativeR8()) {
      assertThat(classA, isAbsent());
    } else {
      assertTrue(parameters.isExtractRules());
      // PG and R8 with keep rules will keep the residual class.
      assertThat(classA, isPresentAndRenamed());
      // R8 using keep rules will soft-pin the precondition method too. The soft pinning is only
      // applied in the first round of tree shaking, however, so R8 can still single caller inline
      // the method after the final round of tree shaking.
      assertThat(
          classA.uniqueMethodWithOriginalName("foo"),
          parameters.isPG() || (parameters.isCurrentR8() && parameters.isExtractRules())
              ? isAbsent()
              : isPresentAndRenamed());
    }
  }

  static class A {

    // Pattern includes any members
    @UsesReflection(@KeepTarget(classConstant = B.class, kind = KeepItemKind.CLASS_AND_MEMBERS))
    public void foo() throws Exception {
      String typeName = B.class.getName();
      int memberCount = B.class.getDeclaredMethods().length + B.class.getDeclaredFields().length;
      System.out.println(typeName.substring(typeName.length() - 1) + ", #members: " + memberCount);
    }
  }

  static class B {
    // Completely empty class ensured by transformer (no default constructor).
  }

  static class TestClass {

    @UsedByReflection(kind = KeepItemKind.CLASS_AND_METHODS)
    public static void main(String[] args) throws Exception {
      new A().foo();
    }
  }
}
