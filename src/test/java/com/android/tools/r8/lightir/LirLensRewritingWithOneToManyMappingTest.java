// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.lightir;

import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForClass;
import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForMethod;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.AssertUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class LirLensRewritingWithOneToManyMappingTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    // TODO(b/354878031): Should succeed.
    AssertUtils.assertFailsCompilationIf(
        parameters.isCfRuntime() || parameters.getApiLevel().isLessThan(AndroidApiLevel.N),
        () ->
            testForR8(parameters.getBackend())
                .addProgramClasses(Main.class, Baz.class, Qux.class)
                .addKeepMainRule(Main.class)
                .addLibraryClasses(Foo.class, Bar.class)
                .addDefaultRuntimeLibrary(parameters)
                .apply(setMockApiLevelForClass(Foo.class, AndroidApiLevel.B))
                .apply(
                    setMockApiLevelForMethod(
                        Foo.class.getDeclaredMethod("method"), AndroidApiLevel.B))
                .apply(setMockApiLevelForClass(Bar.class, AndroidApiLevel.B))
                .enableInliningAnnotations()
                .enableNeverClassInliningAnnotations()
                .enableNoVerticalClassMergingAnnotations()
                .setMinApi(parameters)
                .compile()
                .addRunClasspathClasses(Foo.class, Bar.class)
                .run(parameters.getRuntime(), Main.class)
                .assertSuccessWithOutputLines("Foo", "Foo"));
  }

  static class Main {

    public static void main(String[] args) {
      new Qux().method();
    }
  }

  // Library.

  static class Foo {

    public void method() {
      System.out.println("Foo");
    }
  }

  static class Bar extends Foo {}

  // Program.

  @NeverClassInline
  @NoVerticalClassMerging
  static class Baz extends Bar {}

  @NeverClassInline
  static class Qux extends Baz {

    @NeverInline
    @Override
    public void method() {
      super.method();
      new Baz().method();
    }
  }
}
