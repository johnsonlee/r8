// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.regress;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class Regress339371242 extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultRuntimes().withAllApiLevels().build();
  }

  public Regress339371242(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(TestClass.class, WithLibraryFinalizer.class)
        .addLibraryClasses(LibraryClassWithFinalizer.class)
        .addDefaultRuntimeLibrary(parameters)
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .compile()
        .inspect(
            codeInspector -> {
              ClassSubject clazz = codeInspector.clazz(TestClass.class);
              assertThat(clazz, isPresent());
              assertThat(clazz.uniqueFieldWithOriginalName("handler"), isPresent());
            });
  }

  public static class LibraryClassWithFinalizer {

    @Override
    protected void finalize() throws Throwable {
      super.finalize();
    }
  }

  public static class WithLibraryFinalizer extends LibraryClassWithFinalizer {}

  public static class TestClass {
    private final WithLibraryFinalizer handler;

    public static void main(String[] args) {
      new TestClass().foo();
    }

    public TestClass() {
      handler = new WithLibraryFinalizer();
    }

    public void foo() {
      System.out.println("ab");
    }
  }
}
