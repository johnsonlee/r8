// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.vertical;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InterfaceWithGetProxyClassTest extends TestBase {

  private static final String EXPECTED = StringUtils.lines("Hello world!");

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testReference() throws Exception {
    testForRuntime(parameters)
        .addInnerClasses(InterfaceWithGetProxyClassTest.class)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(InterfaceWithGetProxyClassTest.class)
        .addKeepMainRule(TestClass.class)
        .enableNeverClassInliningAnnotations()
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  static class TestClass {

    public static void main(String[] args) throws Exception {
      Class<?> proxyClass = Proxy.getProxyClass(TestClass.class.getClassLoader(), I.class);
      InvocationHandler ih =
          (proxy, method, args1) -> {
            System.out.print("Hello");
            return null;
          };
      I i = (I) proxyClass.getDeclaredConstructor(InvocationHandler.class).newInstance(ih);
      i.greet();
      new A().greet();
    }
  }

  interface I {

    void greet();
  }

  @NeverClassInline
  static class A implements I {

    @NeverInline
    @Override
    public void greet() {
      System.out.println(" world!");
    }
  }
}
