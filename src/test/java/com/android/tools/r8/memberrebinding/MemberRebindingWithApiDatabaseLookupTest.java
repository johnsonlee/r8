// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.memberrebinding;

import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForClass;
import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForMethod;
import static com.android.tools.r8.utils.codeinspector.CodeMatchers.invokesMethodWithHolderAndName;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.KeepConstantArguments;
import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoMethodStaticizing;
import com.android.tools.r8.NoParameterTypeStrengthening;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MemberRebindingWithApiDatabaseLookupTest extends TestBase {

  static final String EXPECTED = "foobar";

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  public MemberRebindingWithApiDatabaseLookupTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .setMinApi(parameters)
        .addProgramClasses(Foo.class, Bar.class, ProgramClassExtendsLibrary.class)
        .addLibraryClasses(LibraryClass.class, LibraryBaseClass.class)
        .addDefaultRuntimeLibrary(parameters)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoMethodStaticizingAnnotations()
        .enableConstantArgumentAnnotations()
        .enableNoParameterTypeStrengtheningAnnotations()
        .addKeepMainRule(Foo.class)
        .addKeepClassRules(ProgramClassExtendsLibrary.class)
        .apply(setMockApiLevelForClass(LibraryBaseClass.class, AndroidApiLevel.B))
        .apply(setMockApiLevelForClass(LibraryClass.class, AndroidApiLevel.L))
        .apply(
            setMockApiLevelForMethod(
                LibraryBaseClass.class.getDeclaredMethod("z"), AndroidApiLevel.B))
        .compile()
        .addRunClasspathClasses(LibraryBaseClass.class)
        .run(parameters.getRuntime(), Foo.class)
        .assertSuccessWithOutputLines(EXPECTED)
        .inspect(
            codeInspector -> {
              // We should not have rewritten the invoke virtual in X to the base most library class
              // if we are not on dalvik, otherwise the first library class.
              Class expectedInvokeClass =
                  parameters.getApiLevel().isLessThan(AndroidApiLevel.L)
                      ? LibraryClass.class
                      : LibraryBaseClass.class;
              MethodSubject xMethod =
                  codeInspector.clazz(Bar.class).uniqueMethodWithOriginalName("x");
              assertThat(
                  xMethod, invokesMethodWithHolderAndName(expectedInvokeClass.getTypeName(), "z"));
            });
  }

  public static class Foo {
    public static void main(String[] args) {
      if (System.currentTimeMillis() == 0) {
        new Bar().x(null);
      } else {
        new Bar().y("foobar");
      }
    }
  }

  @NeverClassInline
  public static class Bar {
    @NeverInline
    @NoMethodStaticizing
    @KeepConstantArguments
    @NoParameterTypeStrengthening
    public void x(ProgramClassExtendsLibrary value) {
      // We need to ensure that we don't rebind this to LibraryBaseClass as that will make dalvik
      // (incorrectly) hard fail verification even when we don't call this method.
      // Since the superclass of value is not available at runtime dalvik will lower value to
      // type object, which we can't use for the invoke virtual on the known library class
      // LibraryBaseClass.
      value.z();
    }

    @NeverInline
    public void y(String s) {
      System.out.println(s);
    }
  }

  public static class ProgramClassExtendsLibrary extends LibraryClass {}

  public static class LibraryClass extends LibraryBaseClass {}

  public static class LibraryBaseClass {
    public void z() {
      System.out.println("z()");
    }
  }
}
