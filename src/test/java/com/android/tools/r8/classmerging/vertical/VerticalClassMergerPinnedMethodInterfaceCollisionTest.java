// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.classmerging.vertical;


import com.android.tools.r8.NoUnusedInterfaceRemoval;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class VerticalClassMergerPinnedMethodInterfaceCollisionTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepClassAndMembersRules(Main.class)
        .addKeepRules(
            "-keep class " + OtherUser.class.getTypeName() + " {",
            "  void f(" + B.class.getTypeName() + ");",
            "}")
        .addVerticallyMergedClassesInspector(
            inspector -> inspector.assertMergedIntoSubtype(A.class))
        .enableNoUnusedInterfaceRemovalAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("B", "B");
  }

  static class Main {

    public static void main(String[] args) {
      keep(new UserImpl());
      new OtherUser().f(new B());
    }

    static void keep(User user) {
      user.f(new B());
    }
  }

  static class A {}

  static class B extends A {

    @Override
    public String toString() {
      return "B";
    }
  }

  @NoUnusedInterfaceRemoval
  @NoVerticalClassMerging
  interface User {

    void f(A a);
  }

  static class UserImpl implements User {

    @Override
    public void f(A a) {
      System.out.println(a);
    }
  }

  static class OtherUser {

    void f(B b) { // f(B)
      System.out.println(b);
    }
  }
}
