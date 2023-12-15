// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.classmerging.vertical;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoAccessModification;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.classmerging.vertical.VerticalClassMergingInvokeSuperToNestMemberTest.Foo.Bar;
import com.android.tools.r8.classmerging.vertical.VerticalClassMergingInvokeSuperToNestMemberTest.Foo.Baz;
import com.android.tools.r8.utils.BooleanUtils;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class VerticalClassMergingInvokeSuperToNestMemberTest extends TestBase {

  @Parameter(0)
  public boolean emitNestAnnotationsInDex;

  @Parameter(1)
  public boolean enableVerticalClassMerging;

  @Parameter(2)
  public TestParameters parameters;

  @Parameters(name = "{2}, nest in dex: {0}, vertical: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(),
        BooleanUtils.values(),
        getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    assumeFalse(emitNestAnnotationsInDex);
    assumeFalse(enableVerticalClassMerging);
    testForJvm(parameters)
        .addProgramClasses(Main.class)
        .addProgramClassFileData(getTransformedClasses())
        .run(parameters.getRuntime(), Main.class)
        .applyIf(
            parameters.getCfRuntime().isOlderThan(CfVm.JDK11),
            runResult ->
                runResult.assertFailureWithErrorThatThrows(UnsupportedClassVersionError.class),
            runResult -> runResult.assertSuccessWithOutputLines("Bar.bar()"));
  }

  @Test
  public void testR8() throws Exception {
    assumeTrue(parameters.isDexRuntime() || !emitNestAnnotationsInDex);
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class)
        .addProgramClassFileData(getTransformedClasses())
        .addKeepMainRule(Main.class)
        .addOptionsModification(
            options -> options.emitNestAnnotationsInDex = emitNestAnnotationsInDex)
        .addVerticallyMergedClassesInspector(
            inspector -> {
              if (enableVerticalClassMerging) {
                inspector.assertMergedIntoSubtype(Bar.class);
              } else {
                inspector.assertNoClassesMerged();
              }
            })
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoAccessModificationAnnotationsForMembers()
        .enableNoHorizontalClassMergingAnnotations()
        .applyIf(
            enableVerticalClassMerging,
            R8TestBuilder::addNoVerticalClassMergingAnnotations,
            R8TestBuilder::enableNoVerticalClassMergingAnnotations)
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .applyIf(
            parameters.canUseNestBasedAccesses()
                || (parameters.isDexRuntime() && !emitNestAnnotationsInDex),
            runResult -> runResult.assertSuccessWithOutputLines("Bar.bar()"),
            parameters.isCfRuntime(),
            runResult ->
                runResult.assertFailureWithErrorThatThrows(UnsupportedClassVersionError.class),
            runResult -> runResult.assertFailureWithErrorThatThrows(IllegalAccessError.class));
  }

  private Collection<byte[]> getTransformedClasses() throws Exception {
    return ImmutableList.of(
        transformer(Foo.class).setNest(Foo.class, Bar.class, Baz.class).transform(),
        transformer(Bar.class)
            .setNest(Foo.class, Bar.class, Baz.class)
            .setPrivate(Bar.class.getDeclaredMethod("bar"))
            .transform(),
        transformer(Baz.class)
            .setNest(Foo.class, Bar.class, Baz.class)
            .transformMethodInsnInMethod(
                "test",
                (opcode, owner, name, descriptor, isInterface, continuation) -> {
                  assertEquals(Opcodes.INVOKEVIRTUAL, opcode);
                  assertEquals(binaryName(Baz.class), owner);
                  assertEquals("bar", name);
                  continuation.visitMethodInsn(
                      Opcodes.INVOKESPECIAL, binaryName(Bar.class), name, descriptor, isInterface);
                })
            .transform());
  }

  static class Main {

    public static void main(String[] args) {
      Foo.test();
    }
  }

  @NoHorizontalClassMerging
  static class Foo {

    @NeverInline
    static void test() {
      new Baz().test();
    }

    @NoHorizontalClassMerging
    @NoVerticalClassMerging
    static class Bar {

      @NeverInline
      @NoAccessModification
      /*private*/ void bar() {
        System.out.println("Bar.bar()");
      }
    }

    @NeverClassInline
    @NoHorizontalClassMerging
    static class Baz extends Bar {

      void test() {
        bar();
      }
    }
  }
}
