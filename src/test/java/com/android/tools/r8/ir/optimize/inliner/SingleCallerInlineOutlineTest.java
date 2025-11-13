// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.inliner;

import static com.android.tools.r8.utils.codeinspector.CodeMatchers.invokesMethod;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SingleCallerInlineOutlineTest extends TestBase {

  @Parameter(0)
  public boolean allowInliningOfOutlines;

  @Parameter(1)
  public TestParameters parameters;

  @Parameters(name = "{1}, allow inlining: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(), getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addOptionsModification(
            options -> {
              options.outline.threshold = 2;
              options.outline.minSize = 2;
              options.getTestingOptions().allowInliningOfOutlines = allowInliningOfOutlines;
            })
        .collectSyntheticItems()
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspectWithSyntheticItems(
            (inspector, syntheticItems) -> {
              ClassSubject printerClassSubject = inspector.clazz(Printer.class);
              assertThat(printerClassSubject, isPresent());

              MethodSubject helloMethodSubject =
                  printerClassSubject.uniqueMethodWithOriginalName("hello");
              assertThat(helloMethodSubject, isPresent());

              MethodSubject worldMethodSubject =
                  printerClassSubject.uniqueMethodWithOriginalName("world");
              assertThat(worldMethodSubject, isPresent());

              ClassSubject mainClassSubject = inspector.clazz(Main.class);
              assertThat(mainClassSubject, isPresent());

              MethodSubject outlineCandidateMethodSubject =
                  mainClassSubject.uniqueMethodWithOriginalName("outlineCandidate");
              assertThat(outlineCandidateMethodSubject, isPresent());

              ClassSubject outlineClassSubject =
                  inspector.clazz(syntheticItems.syntheticOutlineClass(Main.class, 0));
              // Note that the outline class is present even when `allowInliningOfOutlines`
              // is true, since we do not run another round of tree shaking after the single
              // caller inliner pass.
              assertThat(outlineClassSubject, isPresent());

              if (allowInliningOfOutlines) {
                assertThat(
                    outlineCandidateMethodSubject,
                    allOf(invokesMethod(helloMethodSubject), invokesMethod(worldMethodSubject)));
              } else {
                assertThat(
                    outlineCandidateMethodSubject,
                    invokesMethod(outlineClassSubject.uniqueMethod()));
              }
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello, world!");
  }

  static class Main {

    static boolean alwaysFalse;

    public static void main(String[] args) {
      outlineCandidate();

      // This will be pruned in the primary optimization pass. At this point R8 still does not know
      // that the `alwaysFalse` field is false.
      if (alwaysFalse()) {
        alwaysFalse = true;
      }

      // This will be pruned in the second optimization pass after the outliner has run. After the
      // second round of tree shaking, outlineCandidate2() will be removed, and the synthetic
      // outline will have a single caller.
      if (alwaysFalse) {
        outlineCandidate2();
      }
    }

    static boolean alwaysFalse() {
      return false;
    }

    @NeverInline
    static void outlineCandidate() {
      Printer.hello();
      Printer.world();
    }

    @NeverInline
    static void outlineCandidate2() {
      Printer.hello();
      Printer.world();
    }
  }

  static class Printer {

    @NeverInline
    static void hello() {
      System.out.print("Hello");
    }

    @NeverInline
    static void world() {
      System.out.println(", world!");
    }
  }
}
