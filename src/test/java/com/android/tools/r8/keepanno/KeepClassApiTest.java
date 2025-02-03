// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.keepanno.annotations.MemberAccessFlags;
import com.android.tools.r8.utils.Box;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

@RunWith(Parameterized.class)
public class KeepClassApiTest extends KeepAnnoTestBase {

  static final String EXPECTED = StringUtils.lines("A::bar", "B::foo");

  @Parameter public KeepAnnoParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static List<KeepAnnoParameters> data() {
    return createParameters(
        getTestParameters()
            .withDefaultRuntimes()
            .withMaximumApiLevel()
            .enableApiLevelsForCf()
            .build());
  }

  @Test
  public void testReference() throws Exception {
    assumeTrue(parameters.isReference());
    testForRuntime(parameters.parameters())
        .addProgramClasses(getLibraryClasses())
        .addProgramClasses(getClientClasses())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testShrinker() throws Exception {
    assumeFalse(parameters.isReference());
    assertTrue(parameters.isShrinker());
    Box<Path> lib = new Box<>();
    testForKeepAnno(parameters)
        .addProgramClasses(getLibraryClasses())
        .setExcludedOuterClass(getClass())
        .applyIfShrinker(b -> lib.set(b.compile().inspect(this::checkLibraryOutput).writeToZip()));

    assertNotNull(lib.get());
    testForRuntime(parameters.parameters())
        .addProgramClasses(getClientClasses())
        .addProgramFiles(lib.get())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  public List<Class<?>> getLibraryClasses() {
    return ImmutableList.of(A.class, B.class);
  }

  public List<Class<?>> getClientClasses() {
    return ImmutableList.of(TestClass.class);
  }

  private void checkLibraryOutput(CodeInspector inspector) {
    ClassSubject aClass = inspector.clazz(A.class);
    assertThat(aClass, isPresent());
    assertThat(aClass.uniqueMethodWithOriginalName("foo"), isPresent());
    assertThat(aClass.uniqueMethodWithOriginalName("bar"), isPresent());
    assertThat(aClass.uniqueMethodWithOriginalName("baz"), isAbsent());
    ClassSubject bClass = inspector.clazz(B.class);
    assertThat(bClass, isPresent());
    assertThat(bClass.uniqueMethodWithOriginalName("foo"), isPresent());
    assertThat(bClass.uniqueMethodWithOriginalName("bar"), isAbsent());
    assertThat(bClass.uniqueMethodWithOriginalName("baz"), isAbsent());
  }

  @KeepForApi /* if members are unspecified then default is any public or protected member. */
  public static class A {

    public void foo() {
      System.out.println("A::foo");
    }

    protected void bar() {
      System.out.println("A::bar");
    }

    void baz() {
      System.out.println("A::baz");
    }
  }

  @KeepForApi(memberAccess = {MemberAccessFlags.PUBLIC})
  public static class B {

    public void foo() {
      System.out.println("B::foo");
    }

    protected void bar() {
      System.out.println("B::bar");
    }

    void baz() {
      System.out.println("B::baz");
    }
  }

  static class TestClass {

    public static void main(String[] args) {
      new A().bar();
      new B().foo();
    }
  }
}
