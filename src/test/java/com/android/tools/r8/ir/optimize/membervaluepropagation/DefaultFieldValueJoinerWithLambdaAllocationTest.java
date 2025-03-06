// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.membervaluepropagation;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DefaultFieldValueJoinerWithLambdaAllocationTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addInnerClasses(getClass())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("0", "1", "0", "2");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("0", "1", "0", "2");
  }

  public interface A {

    void init();

    void print();
  }

  public static class B implements A {

    int f;

    @Override
    public void init() {
      f = 1;
    }

    @Override
    public void print() {
      System.out.println(f);
    }
  }

  public static class C implements A {

    int g;

    @Override
    public void init() {
      g = 2;
    }

    @Override
    public void print() {
      System.out.println(g);
    }
  }

  interface MySupplier<T> {

    T get();
  }

  public static class Main {
    public static void main(String[] args) {
      for (MySupplier<A> factory : getFactories()) {
        A a = factory.get();
        a.print();
        a.init();
        a.print();
      }
    }

    private static List<MySupplier<A>> getFactories() {
      List<MySupplier<A>> factories = new ArrayList<>();
      factories.add(B::new);
      factories.add(C::new);
      return factories;
    }
  }
}
