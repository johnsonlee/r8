// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndNotRenamed;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.keepanno.annotations.KeepItemKind;
import com.android.tools.r8.keepanno.annotations.UsedByReflection;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

@RunWith(Parameterized.class)
public class UsedByReflectionMemberInlingingAllowed extends KeepAnnoTestBase {

  static final String EXPECTED = StringUtils.lines("Hello, world!");

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
        .setExcludedOuterClass(getClass())
        .run(TestClass.class)
        .inspect(
            codeInspector -> {
              ClassSubject clazz = codeInspector.clazz(TestClass.class);
              assertThat(clazz, isPresent());
              MethodSubject main = clazz.uniqueMethodWithOriginalName("main");
              assertThat(main, isPresentAndNotRenamed());
              if (parameters.isShrinker()) {
                // We should just have the void return left in main.
                assertTrue(main.streamInstructions().allMatch(InstructionSubject::isReturnVoid));
              }
              assertThat(
                  clazz.uniqueMethodWithOriginalName("isFalse"),
                  parameters.isShrinker() ? isAbsent() : isPresent());
              assertThat(
                  clazz.uniqueFieldWithOriginalName("IS_FALSE"),
                  parameters.isShrinker() ? isAbsent() : isPresent());
            })
        .assertSuccessWithEmptyOutput();
  }

  public List<Class<?>> getInputClasses() {
    return ImmutableList.of(TestClass.class);
  }

  static class TestClass {
    @UsedByReflection(kind = KeepItemKind.CLASS_AND_MEMBERS)
    public static void main(String[] args) {
      if (IS_FALSE) {
        System.out.println("IS_FALSE");
      }
      if (isFalse()) {
        System.out.println("isFalse");
      }
    }

    private static boolean isFalse() {
      return false;
    }

    private static final boolean IS_FALSE = false;
  }
}
