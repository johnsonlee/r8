// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.doctests;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.keepanno.annotations.MemberAccessFlags;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ForApiDocumentationTest extends TestBase {

  static final String EXPECTED_1_REF =
      StringUtils.joinLines(
          "thisPackagePrivateMethodIsNotKept",
          "thisPrivateMethodIsNotKept",
          "thisProtectedMethodIsKept",
          "thisPublicMethodIsKept");

  static final String EXPECTED_1_R8 =
      StringUtils.joinLines("thisProtectedMethodIsKept", "thisPublicMethodIsKept");

  static final String EXPECTED_2_REF =
      StringUtils.joinLines(
          "thisPackagePrivateMethodIsKept",
          "thisPrivateMethodIsNotKept",
          "thisProtectedMethodIsKept",
          "thisPublicMethodIsKept");

  static final String EXPECTED_2_R8 =
      StringUtils.joinLines(
          "thisPackagePrivateMethodIsKept", "thisProtectedMethodIsKept", "thisPublicMethodIsKept");

  static final String EXPECTED_3_REF = StringUtils.joinLines("isKept", "notKept");

  static final String EXPECTED_3_R8 = StringUtils.joinLines("isKept");

  static final String EXPECTED_REF =
      StringUtils.lines(EXPECTED_1_REF, EXPECTED_2_REF, EXPECTED_3_REF);

  static final String EXPECTED_R8 = StringUtils.lines(EXPECTED_1_R8, EXPECTED_2_R8, EXPECTED_3_R8);

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultRuntimes().withMaximumApiLevel().build();
  }

  public ForApiDocumentationTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testReference() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(TestClass.class)
        .addProgramClassesAndInnerClasses(getExampleClasses())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_REF);
  }

  @Test
  public void testWithRuleExtraction() throws Exception {
    testForR8(parameters.getBackend())
        .enableExperimentalKeepAnnotations()
        .addProgramClasses(TestClass.class)
        .addProgramClassesAndInnerClasses(getExampleClasses())
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_R8);
  }

  public List<Class<?>> getExampleClasses() {
    return ImmutableList.of(Example1.class, Example2.class, Example3.class);
  }

  static class Example1 {

    /* INCLUDE DOC: ApiClass
    When annotating a class the default for `@KeepForApi` is to keep the class as well as all of its
    public and protected members:
    INCLUDE END */

    static
    // INCLUDE CODE: ApiClass
    @KeepForApi
    public class MyApi {
      public void thisPublicMethodIsKept() {
        /* ... */
      }

      protected void thisProtectedMethodIsKept() {
        /* ... */
      }

      void thisPackagePrivateMethodIsNotKept() {
        /* ... */
      }

      private void thisPrivateMethodIsNotKept() {
        /* ... */
      }
    }

    // INCLUDE END

    static void run() throws Exception {
      TestClass.printMethods(MyApi.class);
    }
  }

  static class Example2 {

    /* INCLUDE DOC: ApiClassMemberAccess
    The default can be changed using the `@KeepForApi#memberAccess` property:
    INCLUDE END */

    // INCLUDE CODE: ApiClassMemberAccess
    @KeepForApi(
        memberAccess = {
          MemberAccessFlags.PUBLIC,
          MemberAccessFlags.PROTECTED,
          MemberAccessFlags.PACKAGE_PRIVATE
        })
    // INCLUDE END
    public static class MyApi {
      public void thisPublicMethodIsKept() {
        /* ... */
      }

      protected void thisProtectedMethodIsKept() {
        /* ... */
      }

      void thisPackagePrivateMethodIsKept() {
        /* ... */
      }

      private void thisPrivateMethodIsNotKept() {
        /* ... */
      }
    }

    static void run() throws Exception {
      TestClass.printMethods(MyApi.class);
    }
  }

  static class Example3 {

    /* INCLUDE DOC: ApiMember
    The `@KeepForApi` annotation can also be placed directly on members and avoid keeping
    unannotated members. The holder class is implicitly kept. When annotating the members
    directly, the access does not matter as illustrated here by annotating a package private method:
    INCLUDE END */

    static
    // INCLUDE CODE: ApiMember
    public class MyOtherApi {

      public void notKept() {
        /* ... */
      }

      @KeepForApi
      void isKept() {
        /* ... */
      }
    }

    // INCLUDE END

    static void run() throws Exception {
      TestClass.printMethods(MyOtherApi.class);
    }
  }

  static class TestClass {

    public static void main(String[] args) throws Exception {
      Example1.run();
      Example2.run();
      Example3.run();
    }

    static void printMethods(Class<?> clazz) {
      List<String> names = new ArrayList<>();
      for (Method m : clazz.getDeclaredMethods()) {
        names.add(m.getName());
      }
      names.sort(String::compareTo);
      for (String name : names) {
        System.out.println(name);
      }
    }
  }
}
