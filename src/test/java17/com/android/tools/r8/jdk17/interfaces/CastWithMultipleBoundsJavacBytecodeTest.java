// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.jdk17.interfaces;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

// This test is also present for base test module and JDK-21 to demonstrate the different javac
// byte code.
@RunWith(Parameterized.class)
public class CastWithMultipleBoundsJavacBytecodeTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testR8JavacCode() throws Exception {
    parameters.assumeIsOrSimulateNoneRuntime();
    // javac from JDK-17 generates the following code for getLambda with three checkcasts to
    // the two interface types and then to the static return type:
    //
    // static java.io.Serializable getLambda();
    //   descriptor: ()Ljava/io/Serializable;
    //   flags: (0x0008) ACC_STATIC
    //   Code:
    //     stack=1, locals=0, args_size=0
    //        0: invokedynamic #7,  0              // InvokeDynamic #0:run:()Ljava/lang/Runnable;
    //        5: checkcast     #11                 // class java/io/Serializable
    //        8: checkcast     #13                 // class java/lang/Runnable
    //       11: checkcast     #11                 // class java/io/Serializable
    //       14: areturn
    assertEquals(
        3,
        new CodeInspector(ToolHelper.getClassFileForTestClass(TestClass.class))
            .clazz(TestClass.class)
            .uniqueMethodWithOriginalName("getLambda")
            .streamInstructions()
            .filter(InstructionSubject::isCheckCast)
            .count());
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClassesAndStrippedOuter(getClass())
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .addKeepMainRule(TestClass.class)
        .run(parameters.getRuntime(), TestClass.class);
  }

  static class TestClass {
    static void invokeLambda(Object o)
        throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
      Method runMethod = o.getClass().getMethod("run");
      runMethod.invoke(o);
    }

    static Serializable getLambda() {
      return (Runnable & Serializable) () -> System.out.println("base lambda");
    }

    public static void main(String[] args) throws Exception {
      invokeLambda(getLambda());
    }
  }
}
