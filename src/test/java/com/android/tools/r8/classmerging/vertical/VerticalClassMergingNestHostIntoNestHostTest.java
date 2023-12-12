// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.classmerging.vertical;

import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoAccessModification;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.classmerging.vertical.VerticalClassMergingNestHostIntoNestHostTest.Bar.InnerBar;
import com.android.tools.r8.classmerging.vertical.VerticalClassMergingNestHostIntoNestHostTest.Foo.InnerFoo;
import com.android.tools.r8.utils.BooleanUtils;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class VerticalClassMergingNestHostIntoNestHostTest extends TestBase {

  @Parameter(0)
  public boolean emitNestAnnotationsInDex;

  @Parameter(1)
  public TestParameters parameters;

  @Parameters(name = "{1}, nest in dex: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(), getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  @Test
  public void test() throws Exception {
    assumeTrue(parameters.isDexRuntime() || !emitNestAnnotationsInDex);
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class)
        .addProgramClassFileData(getTransformedClasses())
        .addKeepMainRule(Main.class)
        .addOptionsModification(
            options -> options.emitNestAnnotationsInDex = emitNestAnnotationsInDex)
        // TODO(b/315283663): Could allow merging two classes that do not have the same nest.
        .addVerticallyMergedClassesInspector(
            inspector -> {
              if (parameters.isCfRuntime() || emitNestAnnotationsInDex) {
                inspector.assertNoClassesMerged();
              } else {
                inspector.assertMergedIntoSubtype(Foo.class);
              }
            })
        .enableInliningAnnotations()
        .enableNoAccessModificationAnnotationsForMembers()
        .enableNoHorizontalClassMergingAnnotations()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .applyIf(
            parameters.isDexRuntime() && emitNestAnnotationsInDex,
            runResult -> runResult.assertFailureWithErrorThatThrows(IllegalAccessError.class),
            parameters.isCfRuntime() && parameters.getRuntime().asCf().isOlderThan(CfVm.JDK11),
            runResult ->
                runResult.assertFailureWithErrorThatThrows(UnsupportedClassVersionError.class),
            runResult ->
                runResult.assertSuccessWithOutputLines(
                    "InnerFoo.innerFoo()", "Foo.foo()", "InnerBar.innerBar()", "Bar.bar()"));
  }

  private Collection<byte[]> getTransformedClasses() throws Exception {
    return ImmutableList.of(
        transformer(Foo.class)
            .setNest(Foo.class, InnerFoo.class)
            .transformMethodInsnInMethod(
                "test",
                (opcode, owner, name, descriptor, isInterface, continuation) ->
                    continuation.visitMethodInsn(
                        opcode, owner, "innerFoo", descriptor, isInterface))
            .transform(),
        transformer(InnerFoo.class)
            .setNest(Foo.class, InnerFoo.class)
            .transformMethodInsnInMethod(
                "test",
                (opcode, owner, name, descriptor, isInterface, continuation) ->
                    continuation.visitMethodInsn(opcode, owner, "foo", descriptor, isInterface))
            .transform(),
        transformer(Bar.class)
            .setNest(Bar.class, InnerBar.class)
            .transformMethodInsnInMethod(
                "test",
                (opcode, owner, name, descriptor, isInterface, continuation) ->
                    continuation.visitMethodInsn(
                        opcode, owner, "innerBar", descriptor, isInterface))
            .transform(),
        transformer(InnerBar.class)
            .setNest(Bar.class, InnerBar.class)
            .transformMethodInsnInMethod(
                "test",
                (opcode, owner, name, descriptor, isInterface, continuation) ->
                    continuation.visitMethodInsn(opcode, owner, "bar", descriptor, isInterface))
            .transform());
  }

  static class Main {

    public static void main(String[] args) {
      Foo.test();
      InnerFoo.test();
      Bar.test();
      InnerBar.test();
    }
  }

  @NoHorizontalClassMerging
  static class Foo {

    @NeverInline
    @NoAccessModification
    private static void foo() {
      System.out.println("Foo.foo()");
    }

    static void test() {
      InnerFoo.innerFoo();
    }

    @NoHorizontalClassMerging
    static class InnerFoo {

      @NeverInline
      @NoAccessModification
      private static void innerFoo() {
        System.out.println("InnerFoo.innerFoo()");
      }

      static void test() {
        Foo.foo();
      }
    }
  }

  @NoHorizontalClassMerging
  static class Bar extends Foo {

    @NeverInline
    @NoAccessModification
    private static void bar() {
      System.out.println("Bar.bar()");
    }

    static void test() {
      InnerBar.innerBar();
    }

    @NoHorizontalClassMerging
    static class InnerBar {

      @NeverInline
      @NoAccessModification
      private static void innerBar() {
        System.out.println("InnerBar.innerBar()");
      }

      static void test() {
        Bar.bar();
      }
    }
  }
}
