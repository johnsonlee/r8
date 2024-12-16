// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsentIf;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.references.TypeReference;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/** This is a regression test for b/289361079. */
@RunWith(Parameterized.class)
public class ClInitMergeSuperTypeApiLevelTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private boolean canUseExecutable() {
    return parameters.isDexRuntime()
        && parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.O);
  }

  private TypeReference getMergeReferenceForApiLevel() {
    return Reference.typeFromTypeName(
        typeName(canUseExecutable() ? Executable.class : AccessibleObject.class));
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        // Emulate a standard AGP setup where we compile with a new android jar on boot classpath.
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.LATEST))
        .addInnerClasses(getClass())
        .setMinApi(parameters)
        .addKeepClassAndMembersRules(Main.class)
        .addHorizontallyMergedClassesInspector(
            inspector ->
                inspector.assertIsCompleteMergeGroup(A.class, B.class).assertNoOtherClassesMerged())
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .compile()
        .inspect(
            inspector -> {
              ClassSubject clazz = inspector.clazz(A.class);
              assertThat(clazz, isPresent());

              assertTrue(
                  clazz.allFields().stream()
                      .anyMatch(
                          f ->
                              f.getFinalReference()
                                  .getFieldType()
                                  .equals(getMergeReferenceForApiLevel())));

              assertEquals(
                  canUseExecutable() ? 1 : 2,
                  clazz.allMethods(MethodSubject::isInstanceInitializer).size());
              if (canUseExecutable()) {
                MethodSubject constructorInit = clazz.init(Executable.class.getTypeName(), "int");
                assertThat(
                    constructorInit,
                    isAbsentIf(parameters.canUseJavaLangInvokeVarHandleStoreStoreFence()));
              } else {
                MethodSubject constructorInit = clazz.init(Constructor.class.getTypeName());
                assertThat(constructorInit, isPresent());

                MethodSubject methodInit = clazz.init(Method.class.getTypeName());
                assertThat(methodInit, isPresent());
              }
            })
        .run(parameters.getRuntime(), Main.class)
        // The test succeeds for some unknown reason.
        .assertSuccessWithOutputLines(typeName(Main.class));
  }

  public static class Main {

    public static void main(String[] args) throws Exception {
      if (System.currentTimeMillis() > 0) {
        System.out.println(
            new A(Main.class.getDeclaredConstructor()).newInstance().getClass().getName());
      } else {
        System.out.println(
            new B(Main.class.getDeclaredMethod("main", String[].class))
                .newInstance()
                .getClass()
                .getName());
      }
    }
  }

  @NoVerticalClassMerging
  public abstract static class Factory {

    abstract Object newInstance() throws Exception;
  }

  @NeverClassInline
  public static class A extends Factory {

    public final Constructor<?> constructor;

    public A(Constructor<?> constructor) {
      this.constructor = constructor;
    }

    @Override
    @NeverInline
    Object newInstance() throws Exception {
      return constructor.newInstance();
    }
  }

  @NeverClassInline
  public static class B extends Factory {

    public final Method method;

    public B(Method method) {
      this.method = method;
    }

    @Override
    @NeverInline
    Object newInstance() throws Exception {
      return method.invoke(null);
    }
  }
}
