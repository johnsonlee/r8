// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.partial;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class PartialCompilationSignatureRewritingTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    parameters.assumeNoPartialCompilation();
    testForD8(parameters)
        .addInnerClasses(getClass())
        .release()
        .run(parameters.getRuntime(), ExcludedClass.class)
        .assertSuccessWithOutputLines(
            "class " + IncludedClassA.class.getTypeName(),
            "class " + IncludedClassB.class.getTypeName(),
            "class " + IncludedClassC.class.getTypeName());
  }

  @Test
  public void testR8() throws Exception {
    testForR8Partial(parameters)
        .addR8ExcludedClasses(ExcludedClass.class)
        .addR8IncludedClasses(IncludedClassA.class, IncludedClassB.class, IncludedClassC.class)
        .compile()
        .apply(
            compileResult -> {
              ClassSubject aClassSubject = compileResult.inspector().clazz(IncludedClassA.class);
              assertThat(aClassSubject, isPresentAndRenamed());

              ClassSubject bClassSubject = compileResult.inspector().clazz(IncludedClassB.class);
              assertThat(bClassSubject, isPresentAndRenamed());

              ClassSubject cClassSubject = compileResult.inspector().clazz(IncludedClassC.class);
              assertThat(cClassSubject, isPresentAndRenamed());

              compileResult
                  .run(parameters.getRuntime(), ExcludedClass.class)
                  .assertSuccessWithOutputLines(
                      "class " + aClassSubject.getFinalName(),
                      "class " + bClassSubject.getFinalName(),
                      "class " + cClassSubject.getFinalName());
            });
  }

  static class ExcludedClass<T extends IncludedClassA> {

    static ExcludedClass<IncludedClassB> f;

    @SuppressWarnings("rawtypes")
    public static <T extends IncludedClassC> void main(String[] args)
        throws NoSuchFieldException, NoSuchMethodException {
      // Retrieve and print IncludedClassA.
      for (TypeVariable<Class<ExcludedClass>> typeParameter :
          ExcludedClass.class.getTypeParameters()) {
        for (Type bound : typeParameter.getBounds()) {
          System.out.println(bound);
        }
      }
      // Retrieve and print IncludedClassB.
      ParameterizedType genericType =
          (ParameterizedType) ExcludedClass.class.getDeclaredField("f").getGenericType();
      for (Type actualTypeArgument : genericType.getActualTypeArguments()) {
        System.out.println(actualTypeArgument);
      }
      // Retrieve and print IncludedClassC.
      for (TypeVariable<Method> typeParameter :
          ExcludedClass.class.getDeclaredMethod("main", String[].class).getTypeParameters()) {
        for (Type bound : typeParameter.getBounds()) {
          System.out.println(bound);
        }
      }
    }
  }

  static class IncludedClassA {}

  static class IncludedClassB extends IncludedClassA {}

  static class IncludedClassC {}
}
