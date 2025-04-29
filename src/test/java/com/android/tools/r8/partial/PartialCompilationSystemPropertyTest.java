// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.partial;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class PartialCompilationSystemPropertyTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  @Test
  public void test() throws Exception {
    System.setProperty(
        R8PartialCompilationConfiguration.INCLUDE_PROPERTY_NAME, IncludedMain.class.getTypeName());
    testForR8(Backend.DEX)
        .addInnerClasses(getClass())
        .addKeepMainRule(IncludedMain.class)
        .setMinApi(apiLevelWithNativeMultiDexSupport())
        .compile()
        .inspect(
            inspector -> {
              ClassSubject includedClass = inspector.clazz(IncludedMain.class);
              assertThat(includedClass, isPresent());
              assertThat(includedClass.uniqueMethodWithOriginalName("test"), isAbsent());

              ClassSubject excludedClass = inspector.clazz(ExcludedMain.class);
              assertThat(excludedClass, isPresent());
              assertThat(excludedClass.uniqueMethodWithOriginalName("test"), isPresent());
            });
  }

  static class IncludedMain {

    public static void main(String[] args) {
      test();
    }

    // Should be inlined.
    static void test() {}
  }

  static class ExcludedMain {

    public static void main(String[] args) {
      test();
    }

    // Should not be inlined.
    static void test() {}
  }
}
