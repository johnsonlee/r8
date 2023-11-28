// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.membervaluepropagation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InstanceFieldsOnlyWrittenInInitializersWithSingleCallerPruningTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    assumeTrue(parameters.canInitNewInstanceUsingSuperclassConstructor());
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        // Ensure that A.<init>(String) is single caller inlined and removed from the application
        // before processing the last instance field write to A.f.
        .addOptionsModification(
            options ->
                options.testing.waveModifier =
                    waves -> {
                      int numberOfWaves = waves.size();
                      for (ProgramMethodSet wave : waves) {
                        boolean changed =
                            wave.removeIf(
                                method -> {
                                  if (method.getHolder().getTypeName().equals(A.class.getTypeName())
                                      && method.getDefinition().isInstanceInitializer()
                                      && method.getParameters().isEmpty()) {
                                    waves.addLast(ProgramMethodSet.create(method));
                                    return true;
                                  }
                                  return false;
                                });
                        if (changed) {
                          break;
                        }
                      }
                      assertEquals(numberOfWaves + 1, waves.size());
                    })
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        // TODO(b/307987907): Should succeed with "Hello, world!".
        .assertSuccessWithOutputLines("HelloHello");
  }

  static class Main {

    public static void main(String[] args) {
      System.out.print(new A());
      System.out.println(new A(", world!"));
    }
  }

  static class A {

    String f;

    @NeverInline
    A() {
      f = "Hello";
    }

    A(String f) {
      this.f = f;
    }

    @Override
    public String toString() {
      return f;
    }
  }
}
