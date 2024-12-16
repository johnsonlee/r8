// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.argumentpropagation;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentIf;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import java.lang.reflect.Field;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import sun.misc.Unsafe;

@RunWith(Parameterized.class)
public class DefaultFieldValueAnalysisWithKeptSubclassTest extends TestBase {

  @Parameter(0)
  public boolean keepFields;

  @Parameter(1)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(),
        getTestParameters()
            .withCfRuntimes()
            .withDexRuntimesStartingFromIncluding(Version.V5_1_1)
            .withAllApiLevels()
            .build());
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8()
        .addInnerClasses(getClass())
        .release()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class, B.class.getTypeName())
        .assertSuccessWithOutputLines("Hello, world!");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepClassAndMembersRules(Main.class)
        .addKeepClassRules(B.class)
        .applyIf(
            keepFields,
            b ->
                b.addKeepRules(
                    "-keepclassmembers class " + A.class.getTypeName() + " {",
                    "  !static <fields>;",
                    "}"))
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              ClassSubject aClassSubject = inspector.clazz(A.class);
              assertThat(aClassSubject, isPresent());
              assertThat(aClassSubject.uniqueFieldWithOriginalName("f"), isPresentIf(keepFields));
            })
        .run(parameters.getRuntime(), Main.class, B.class.getTypeName())
        .applyIf(
            keepFields,
            rr -> rr.assertSuccessWithOutputLines("Hello, world!"),
            TestRunResult::assertSuccessWithEmptyOutput);
  }

  static class Main {

    public static void main(String[] args) throws Exception {
      B b = (B) getUnsafe().allocateInstance(Class.forName(args[0]));
      b.doStuff();
    }

    private static Unsafe getUnsafe() throws Exception {
      Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
      Field f = unsafeClass.getDeclaredField("theUnsafe");
      f.setAccessible(true);
      return (Unsafe) f.get(null);
    }
  }

  @NoVerticalClassMerging
  abstract static class A {

    boolean f;

    @NeverInline
    void doStuff() {
      if (!f) {
        f = true;
        System.out.println("Hello, world!");
      }
    }
  }

  @NeverClassInline
  static class B extends A {}
}
