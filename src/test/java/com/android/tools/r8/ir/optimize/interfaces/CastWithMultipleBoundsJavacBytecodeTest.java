// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.interfaces;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

// This test is also present for JDK-17 and JDK-21 to demonstrate the different javac byte code.
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
    // javac from JDK-11 generates the following code for getLambda with two checkcasts to
    // the two interface types:
    //
    // static java.io.Serializable getLambda();
    //   descriptor: ()Ljava/io/Serializable;
    //   flags: (0x0008) ACC_STATIC
    //   Code:
    //     stack=1, locals=0, args_size=0
    //        0: invokedynamic #2,  0              // InvokeDynamic #0:run:()Ljava/lang/Runnable;
    //        5: checkcast     #3                  // class java/io/Serializable
    //        8: checkcast     #4                  // class java/lang/Runnable
    //       11: areturn
    assertEquals(
        2,
        new CodeInspector(ToolHelper.getClassFileForTestClass(TestClass.class))
            .clazz(TestClass.class)
            .uniqueMethodWithOriginalName("getLambda")
            .streamInstructions()
            .filter(InstructionSubject::isCheckCast)
            .count());
  }

  @Test
  public void testR8() throws Exception {
    Assert.assertThrows(
        CompilationFailedException.class,
        () ->
            testForR8(parameters.getBackend())
                .addInnerClasses(getClass())
                .addKeepMainRule(TestClass.class)
                .setMinApi(parameters)
                .compileWithExpectedDiagnostics(
                    diagnostics ->
                        diagnostics.assertErrorsMatch(
                            diagnosticMessage(
                                containsString(
                                    "Unexpected open interface java.io.Serializable")))));

    testForR8(parameters.getBackend())
        .addOptionsModification(
            options ->
                options
                    .getOpenClosedInterfacesOptions()
                    .suppressSingleOpenInterface(Reference.classFromClass(Serializable.class)))
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .compile();
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
