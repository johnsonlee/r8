// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.genericsignature;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import java.lang.reflect.Type;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class GenericSignaturePrunedInterfacesObfuscationTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }
  @Test
  public void testR8() throws Exception {
    testForR8Compat(parameters.getBackend())
        .addInnerClasses(getClass())
        .setMinApi(parameters)
        .addKeepMainRule(Main.class)
        .addKeepClassRules(I.class, A.class)
        .addKeepClassRulesWithAllowObfuscation(J.class)
        .addKeepAttributeSignature()
        .addKeepAttributeInnerClassesAndEnclosingMethod()
        .enableInliningAnnotations()
        .run(parameters.getRuntime(), Main.class)
        .apply(
            rr -> {
              ClassSubject jClassSubject = rr.inspector().clazz(J.class);
              assertThat(jClassSubject, isPresentAndRenamed());
              rr.assertSuccessWithOutputLines(
                  "interface " + jClassSubject.getFinalName(),
                  jClassSubject.getFinalName() + "<java.lang.Object>");
            });
  }

  public interface I {}

  public interface J<T> {}

  public static class A implements I {}

  public static class B extends A implements I, J<Object> {

    @NeverInline
    public static void foo() {
      for (Type genericInterface : B.class.getInterfaces()) {
        System.out.println(genericInterface);
      }
      for (Type genericInterface : B.class.getGenericInterfaces()) {
        System.out.println(genericInterface);
      }
    }
  }

  public static class Main {

    public static void main(String[] args) {
      B.foo();
    }
  }
}
