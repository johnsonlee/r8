// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.classmerging.horizontal;

import static com.android.tools.r8.classmerging.horizontal.EquivalentInstanceInitializerMergingWithApiUnsafeParameterTest.Main.encode;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NoFieldTypeStrengthening;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class EquivalentInstanceInitializerMergingWithApiUnsafeParameterTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    // Always use the min API level to prohibit constructor inlining so that the constructors of
    // interest are still present at the time of horizontal class merging.
    return getTestParameters().withAllRuntimes().withMinimumApiLevel().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class, Parent.class, A.class, B.class, MyLibraryClass.class)
        .addLibraryClasses(LibraryClassBase.class, LibraryClass.class)
        .addDefaultRuntimeLibrary(parameters)
        .addKeepClassAndMembersRules(Main.class, Parent.class)
        .addHorizontallyMergedClassesInspector(
            inspector ->
                inspector.assertIsCompleteMergeGroup(A.class, B.class).assertNoOtherClassesMerged())
        .enableNoFieldTypeStrengtheningAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              // Verify that the two constructors A.<init> and B.<init> have not been merged.
              ClassSubject aClassSubject = inspector.clazz(A.class);
              assertThat(aClassSubject, isPresent());
              assertEquals(
                  2, aClassSubject.allMethods(MethodSubject::isInstanceInitializer).size());
            })
        .addRunClasspathClasses(LibraryClassBase.class, LibraryClass.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("LibraryClass", "MyLibraryClass");
  }

  static class LibraryClassBase {}

  static class LibraryClass extends LibraryClassBase {

    @Override
    public String toString() {
      return "LibraryClass";
    }
  }

  // @Keep
  static class Main {

    public static void main(String[] args) {
      System.out.println(new A(new LibraryClass()));
      System.out.println(new B(new MyLibraryClass()));
    }

    public static String encode(Object o) {
      return o.toString();
    }
  }

  // @Keep
  static class Parent {

    Parent(LibraryClass lib) {}
  }

  static class A extends Parent {

    @NoFieldTypeStrengthening LibraryClassBase f;

    A(LibraryClass c) {
      super(c);
      f = c;
    }

    @Override
    public String toString() {
      // Use `f` to ensure it is not removed.
      return encode(f);
    }
  }

  static class B extends Parent {

    @NoFieldTypeStrengthening LibraryClassBase f;

    B(MyLibraryClass d) {
      super(d);
      f = d;
    }

    @Override
    public String toString() {
      // Use `f` to ensure it is not removed.
      return encode(f);
    }
  }

  static class MyLibraryClass extends LibraryClass {

    @Override
    public String toString() {
      return "MyLibraryClass";
    }
  }
}
