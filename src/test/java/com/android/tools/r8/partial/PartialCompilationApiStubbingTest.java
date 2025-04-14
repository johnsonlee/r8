// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.partial;

import static com.android.tools.r8.apimodel.ApiModelingTestHelper.getMockApiLevelForClassModification;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentIf;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.R8PartialTestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ThrowableConsumer;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class PartialCompilationApiStubbingTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testExcluded() throws Exception {
    runTest(builder -> builder.addR8ExcludedClasses(ExcludedMain.class));
  }

  @Test
  public void testIncluded() throws Exception {
    runTest(
        builder ->
            builder.addR8IncludedClasses(IncludedMain.class).addKeepMainRule(IncludedMain.class));
  }

  @Test
  public void testBoth() throws Exception {
    runTest(
        builder ->
            builder
                .addR8ExcludedClasses(ExcludedMain.class)
                .addR8IncludedClasses(IncludedMain.class)
                .addKeepMainRule(IncludedMain.class));
  }

  private void runTest(ThrowableConsumer<? super R8PartialTestBuilder> configuration)
      throws Exception {
    parameters.assumeCanUseR8Partial();
    testForR8Partial(parameters)
        .apply(configuration)
        .addLibraryClasses(ExceptionPresentSinceS.class)
        .addDefaultRuntimeLibrary(parameters)
        .addR8PartialD8OptionsModification(
            getMockApiLevelForClassModification(ExceptionPresentSinceS.class, AndroidApiLevel.S))
        .addR8PartialR8OptionsModification(
            getMockApiLevelForClassModification(ExceptionPresentSinceS.class, AndroidApiLevel.S))
        .compile()
        .inspect(
            inspector -> {
              ClassSubject globalSyntheticClass = inspector.clazz(ExceptionPresentSinceS.class);
              assertThat(
                  globalSyntheticClass,
                  isPresentIf(parameters.getApiLevel().isLessThan(AndroidApiLevel.S)));
            });
  }

  static class ExceptionPresentSinceS extends RuntimeException {}

  static class ExcludedMain {

    public static void main(String[] args) {
      try {
        System.out.println("Hello, world!");
      } catch (ExceptionPresentSinceS e) {
        // Ignore.
      }
    }
  }

  static class IncludedMain {

    public static void main(String[] args) {
      try {
        System.out.println("Hello, world!");
      } catch (ExceptionPresentSinceS e) {
        // Ignore.
      }
    }
  }
}
