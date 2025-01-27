// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.partial;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndNotRenamed;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.ThrowingConsumer;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.util.function.Consumer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ClassHierarchyInterleavedD8AndR8Test extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withDexRuntimes()
        .withApiLevelsStartingAtIncluding(AndroidApiLevel.L)
        .build();
  }

  private void runTest(
      Consumer<R8PartialCompilationConfiguration.Builder> partialConfigurationBuilderConsumer,
      ThrowingConsumer<CodeInspector, RuntimeException> inspector)
      throws Exception {
    testForR8Partial(parameters.getBackend())
        .setMinApi(parameters)
        .addProgramClasses(A.class, B.class, C.class, Main.class)
        .addKeepMainRule(Main.class)
        .setR8PartialConfiguration(partialConfigurationBuilderConsumer)
        .compile()
        .inspect(inspector)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithEmptyOutput();
  }

  @Test
  public void testD8Top() throws Exception {
    runTest(
        partialConfigurationBuilder ->
            partialConfigurationBuilder.includeAll().excludeClasses(A.class),
        inspector -> {
          assertThat(inspector.clazz(A.class), isPresentAndNotRenamed());
          assertThat(inspector.clazz(B.class), isAbsent()); // Merged into C.
          assertThat(inspector.clazz(C.class), isPresentAndRenamed());
        });
  }

  @Test
  public void testD8Middle() throws Exception {
    runTest(
        partialConfigurationBuilder ->
            partialConfigurationBuilder.includeAll().excludeClasses(B.class),
        inspector -> {
          assertThat(inspector.clazz(A.class), isPresentAndNotRenamed());
          assertThat(inspector.clazz(B.class), isPresentAndNotRenamed());
          assertThat(inspector.clazz(C.class), isPresentAndRenamed());
        });
  }

  @Test
  public void testD8Bottom() throws Exception {
    runTest(
        partialConfigurationBuilder ->
            partialConfigurationBuilder.includeAll().excludeClasses(C.class),
        inspector -> {
          assertThat(inspector.clazz(A.class), isPresentAndNotRenamed());
          assertThat(inspector.clazz(B.class), isPresentAndNotRenamed());
          assertThat(inspector.clazz(C.class), isPresentAndNotRenamed());
        });
  }

  public static class A {}

  public static class B extends A {}

  public static class C extends B {}

  public static class Main {

    public static void main(String[] args) {
      new C();
    }
  }
}
