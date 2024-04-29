// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.regress;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class Regress337798768 extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withCfRuntimes().build();
  }

  public Regress337798768(TestParameters parameters) {
    this.parameters = parameters;
  }

  // TODO(b/337798768): should not fail verification
  @Test(expected = CompilationFailedException.class)
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .addOptionsModification(o -> o.testing.enableStrictFrameVerification = true)
        .addKeepClassAndMembersRules(A.class, B.class, I.class)
        .compile();
  }

  interface I {
    void foobar();
  }

  public static class A implements I {

    @Override
    public void foobar() {
      System.out.println("foobar");
    }
  }

  public static class B implements I {
    @Override
    public void foobar() {
      System.out.println("B");
    }
  }

  public static class TestClass {
    public static void main(String[] args) {
      I i =
          System.currentTimeMillis() == 0
              ? null
              : System.currentTimeMillis() == 0 ? new B() : new A();
      i.foobar();
    }
  }
}
