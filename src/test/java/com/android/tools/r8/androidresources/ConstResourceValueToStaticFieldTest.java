// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.androidresources;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.R8TestCompileResultBase;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.androidresources.AndroidResourceTestingUtils.AndroidTestResource;
import com.android.tools.r8.androidresources.AndroidResourceTestingUtils.AndroidTestResourceBuilder;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ConstResourceValueToStaticFieldTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection parameters() {
    return getTestParameters()
        .withDefaultDexRuntime()
        .withAllApiLevels()
        .withPartialCompilation()
        .build();
  }

  public static AndroidTestResource getTestResources(TemporaryFolder temp) throws Exception {
    return new AndroidTestResourceBuilder()
        .withSimpleManifestAndAppNameString()
        .addRClassInitializeWithDefaultValues(R.string.class)
        .build(temp);
  }

  private R8TestBuilder<? extends R8TestCompileResultBase<?>, R8TestRunResult, ?> getSharedBuilder()
      throws Exception {
    return testForR8(parameters)
        .addProgramClasses(FooBar.class)
        .addAndroidResources(getTestResources(temp))
        .addKeepMainRule(FooBar.class)
        .enableOptimizedShrinking();
  }

  @Test
  public void testR8KeepAll() throws Exception {
    getSharedBuilder()
        .addKeepAllClassesRule()
        .compile()
        .inspect(
            codeInspector -> {
              ClassSubject rStringClass = codeInspector.clazz(R.string.class);
              // Since we have the keep all we should still have the class and the init.
              assertThat(rStringClass, isPresent());
              assertThat(rStringClass.uniqueInstanceInitializer(), isPresent());
              assertThat(rStringClass.field("int", "foo"), isPresent());
              // Even with the keep all we should have removed the field write, and hence the
              // clinit.
              if (!parameters.isRandomPartialCompilation()) {
                assertThat(rStringClass.clinit(), isAbsent());
              }
            })
        .run(parameters.getRuntime(), FooBar.class)
        .assertSuccessWithEmptyOutput();
  }

  @Test
  public void testR8NoKeep() throws Exception {
    getSharedBuilder()
        .compile()
        .inspect(
            codeInspector -> {
              ClassSubject rStringClass = codeInspector.clazz(R.string.class);
              if (!parameters.isRandomPartialCompilation()) {
                assertThat(rStringClass, isAbsent());
              }
            })
        .run(parameters.getRuntime(), FooBar.class)
        .assertSuccessWithEmptyOutput();
  }

  public static class FooBar {
    public static void main(String[] args) {
      if (System.currentTimeMillis() == 0) {
        System.out.println(R.string.foo);
      }
    }
  }

  public static class R {
    public static class string {
      public static int foo = 0x7f110004;
    }
  }
}
