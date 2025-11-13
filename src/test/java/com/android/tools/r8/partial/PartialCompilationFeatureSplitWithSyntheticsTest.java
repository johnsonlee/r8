// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.partial;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndNotRenamed;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.R8PartialTestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class PartialCompilationFeatureSplitWithSyntheticsTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    parameters.assumeCanUseR8Partial();
    R8PartialTestCompileResult compileResult =
        testForR8Partial(parameters.getBackend())
            .addProgramClasses(Main.class, IncludedBaseClass.class, ExcludedBaseClass.class)
            .addKeepRules("-keepclassmembers class * { java.lang.Runnable createRunnable(); }")
            .addFeatureSplit(IncludedFeature1SplitClass.class, ExcludedFeature1SplitClass.class)
            .addFeatureSplit(IncludedFeature2SplitClass.class, ExcludedFeature2SplitClass.class)
            .collectSyntheticItems()
            .setR8PartialConfiguration(
                builder ->
                    builder.includeClasses(
                        IncludedBaseClass.class,
                        IncludedFeature1SplitClass.class,
                        IncludedFeature2SplitClass.class))
            .setMinApi(parameters)
            .compile();
    compileResult
        .inspect(
            baseInspector -> {
              SyntheticItemsTestUtils syntheticItems = compileResult.getSyntheticItems();
              ClassSubject includedClass = baseInspector.clazz(IncludedBaseClass.class);
              assertThat(includedClass, isPresentAndRenamed());

              ClassSubject includedLambda =
                  baseInspector.clazz(
                      syntheticItems.syntheticLambdaClass(IncludedBaseClass.class, 0));
              assertThat(includedLambda, isPresentAndRenamed());

              ClassSubject excludedClass = baseInspector.clazz(ExcludedBaseClass.class);
              assertThat(excludedClass, isPresentAndNotRenamed());

              ClassSubject excludedLambda =
                  baseInspector.clazz(
                      syntheticItems.syntheticLambdaClass(ExcludedBaseClass.class, 0));
              assertThat(excludedLambda, isPresentAndNotRenamed());
            },
            feature1Inspector -> {
              SyntheticItemsTestUtils syntheticItems = compileResult.getSyntheticItems();
              ClassSubject includedClass =
                  feature1Inspector.clazz(IncludedFeature1SplitClass.class);
              assertThat(includedClass, isPresentAndRenamed());

              ClassSubject includedLambda =
                  feature1Inspector.clazz(
                      syntheticItems.syntheticLambdaClass(IncludedFeature1SplitClass.class, 0));
              assertThat(includedLambda, isPresentAndRenamed());

              ClassSubject excludedClass =
                  feature1Inspector.clazz(ExcludedFeature1SplitClass.class);
              assertThat(excludedClass, isPresentAndNotRenamed());

              ClassSubject excludedLambda =
                  feature1Inspector.clazz(
                      syntheticItems.syntheticLambdaClass(ExcludedFeature1SplitClass.class, 0));
              assertThat(excludedLambda, isPresentAndNotRenamed());
            },
            feature2Inspector -> {
              SyntheticItemsTestUtils syntheticItems = compileResult.getSyntheticItems();
              ClassSubject includedClass =
                  feature2Inspector.clazz(IncludedFeature2SplitClass.class);
              assertThat(includedClass, isPresentAndRenamed());

              ClassSubject includedLambda =
                  feature2Inspector.clazz(
                      syntheticItems.syntheticLambdaClass(IncludedFeature2SplitClass.class, 0));
              assertThat(includedLambda, isPresentAndRenamed());

              ClassSubject excludedClass =
                  feature2Inspector.clazz(ExcludedFeature2SplitClass.class);
              assertThat(excludedClass, isPresentAndNotRenamed());

              ClassSubject excludedLambda =
                  feature2Inspector.clazz(
                      syntheticItems.syntheticLambdaClass(ExcludedFeature2SplitClass.class, 0));
              assertThat(excludedLambda, isPresentAndNotRenamed());
            })
        .addRunClasspathFiles(compileResult.getFeatures())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(
            "IncludedBaseClass",
            "ExcludedBaseClass",
            "IncludedFeature1SplitClass",
            "ExcludedFeature1SplitClass",
            "IncludedFeature2SplitClass",
            "ExcludedFeature2SplitClass");
  }

  static class Main {

    public static void main(String[] args) {
      IncludedBaseClass.main(args);
      ExcludedBaseClass.main(args);
      IncludedFeature1SplitClass.main(args);
      ExcludedFeature1SplitClass.main(args);
      IncludedFeature2SplitClass.main(args);
      ExcludedFeature2SplitClass.main(args);
    }
  }

  static class IncludedBaseClass {

    public static void main(String[] args) {
      createRunnable().run();
    }

    private static Runnable createRunnable() {
      return () -> System.out.println("IncludedBaseClass");
    }
  }

  static class ExcludedBaseClass {

    public static void main(String[] args) {
      createRunnable().run();
    }

    private static Runnable createRunnable() {
      return () -> System.out.println("ExcludedBaseClass");
    }
  }

  static class IncludedFeature1SplitClass {

    public static void main(String[] args) {
      createRunnable().run();
    }

    private static Runnable createRunnable() {
      return () -> System.out.println("IncludedFeature1SplitClass");
    }
  }

  static class ExcludedFeature1SplitClass {

    public static void main(String[] args) {
      createRunnable().run();
    }

    private static Runnable createRunnable() {
      return () -> System.out.println("ExcludedFeature1SplitClass");
    }
  }

  static class IncludedFeature2SplitClass {

    public static void main(String[] args) {
      createRunnable().run();
    }

    private static Runnable createRunnable() {
      return () -> System.out.println("IncludedFeature2SplitClass");
    }
  }

  static class ExcludedFeature2SplitClass {

    public static void main(String[] args) {
      createRunnable().run();
    }

    private static Runnable createRunnable() {
      return () -> System.out.println("ExcludedFeature2SplitClass");
    }
  }
}
