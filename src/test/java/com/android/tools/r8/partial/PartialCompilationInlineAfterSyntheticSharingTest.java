// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.partial;

import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForClass;

import com.android.tools.r8.LibraryDesugaringTestConfiguration;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.utils.AndroidApiLevel;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class PartialCompilationInlineAfterSyntheticSharingTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    // Only run on runtimes where Function and Predicate are present.
    return getTestParameters().withDexRuntimes().build();
  }

  @Test
  public void test() throws Exception {
    testForR8Partial(parameters.getBackend())
        .addR8ExcludedClasses(ExcludedClass.class)
        .addR8IncludedClasses(IncludedClass.class)
        .addLibraryClasses(Foo.class)
        .addLibraryFiles(ToolHelper.getMostRecentAndroidJar())
        .apply(setMockApiLevelForClass(Foo.class, AndroidApiLevel.LATEST))
        // Enable library desugaring so that API outlining is run lir-to-lir in R8 of R8 partial.
        .enableCoreLibraryDesugaring(
            LibraryDesugaringTestConfiguration.forSpecification(
                LibraryDesugaringSpecification.JDK11.getSpecification()))
        .setMinApi(apiLevelWithNativeMultiDexSupport())
        .compile()
        .addRunClasspathClasses(Foo.class)
        .run(parameters.getRuntime(), ExcludedClass.class)
        .assertSuccessWithOutputLines(
            "class " + Foo.class.getTypeName(), "class " + Foo.class.getTypeName());
  }

  static class ExcludedClass {

    public static void main(String[] args) {
      IncludedClass.test();
      test();
    }

    static void test() {
      // Leads to an API outline. The API outline will be shared with the API outline from
      // IncludedClass.test(). It is important that we don't single caller inline the shared API
      // outline in R8 of R8 partial, since the API outline has a caller in an excluded class.
      System.out.println(Foo.class);
    }
  }

  static class IncludedClass {

    static void test() {
      System.out.println(Foo.class);
    }
  }

  static class Foo {}
}
