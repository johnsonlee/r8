// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.lambdas;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentIf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.GlobalSyntheticsTestingConsumer;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.graph.DexEncodedAnnotation;
import com.android.tools.r8.graph.DexValue;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.AnnotationSubject;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

@RunWith(Parameterized.class)
public class LambdaCallTargetAnnotationTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public CompilationMode mode;

  @Parameter(2)
  public boolean intermediate;

  @Parameter(3)
  public boolean forceLambdaAccessor;

  @Parameterized.Parameters(name = "{0}, mode = {1}, intermediate = {2}, forceLambdaAccessor = {3}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withNoneRuntime().build(),
        CompilationMode.values(),
        BooleanUtils.values(),
        BooleanUtils.values());
  }

  private void checkAnnotationField(
      DexEncodedAnnotation encodedAnnotation, int index, String name, String value) {
    assertEquals(name, encodedAnnotation.getElement(index).getName().toString());
    DexValue elementValue = encodedAnnotation.getElement(index).getValue();
    assertTrue(elementValue.isDexValueString());
    assertEquals(value, elementValue.asDexValueString().getValue().toString());
  }

  private void checkAnnotation(
      AnnotationSubject lambdaMethodAnnotation, String holder, String method, String proto) {
    assertThat(lambdaMethodAnnotation, isPresentIf(mode.isDebug()));
    if (mode.isRelease()) {
      return;
    }
    DexEncodedAnnotation encodedAnnotation = lambdaMethodAnnotation.getAnnotation();
    assertEquals(3, encodedAnnotation.getNumberOfElements());

    checkAnnotationField(encodedAnnotation, 0, "holder", holder);
    checkAnnotationField(encodedAnnotation, 1, "method", method);
    checkAnnotationField(encodedAnnotation, 2, "proto", proto);
  }

  @Test
  public void testD8() throws Exception {
    GlobalSyntheticsTestingConsumer globals =
        intermediate ? new GlobalSyntheticsTestingConsumer() : null;
    String prefix = "L" + getClass().getTypeName().replace('.', '/');
    testForD8(Backend.DEX)
        .addInnerClasses(LambdaCallTargetAnnotationTest.class)
        .setMinApi(AndroidApiLevel.L)
        .setMode(mode)
        .setIntermediate(mode.isDebug() && intermediate)
        .applyIf(intermediate, b -> b.getBuilder().setGlobalSyntheticsConsumer(globals))
        .addOptionsModification(
            options -> options.testing.forceLambdaAccessorInD8 = forceLambdaAccessor)
        .collectSyntheticItems()
        .compile()
        .inspectWithSyntheticItems(
            (inspector, syntheticItems) -> {
              checkAnnotation(
                  inspector
                      .clazz(syntheticItems.syntheticLambdaClass(LambdaMethod.class, 0))
                      .annotation("com.android.tools.r8.annotations.LambdaMethod"),
                  prefix + "$" + LambdaMethod.class.getSimpleName() + ";",
                  "lambda$testILambda$0",
                  "()V");
              checkAnnotation(
                  inspector
                      .clazz(syntheticItems.syntheticLambdaClass(LambdaMethod.class, 1))
                      .annotation("com.android.tools.r8.annotations.LambdaMethod"),
                  prefix + "$" + LambdaMethod.class.getSimpleName() + ";",
                  "lambda$testJLambda$1",
                  "(I)V");

              checkAnnotation(
                  inspector
                      .clazz(
                          syntheticItems.syntheticLambdaClass(LambdaStaticMethodReference.class, 0))
                      .annotation("com.android.tools.r8.annotations.LambdaMethod"),
                  prefix + "$" + LambdaStaticMethodReference.class.getSimpleName() + ";",
                  "methodReturningJ",
                  "(I)Lcom/android/tools/r8/desugar/lambdas/LambdaCallTargetAnnotationTest$J;");
              checkAnnotation(
                  inspector
                      .clazz(
                          syntheticItems.syntheticLambdaClass(LambdaStaticMethodReference.class, 1))
                      .annotation("com.android.tools.r8.annotations.LambdaMethod"),
                  prefix + "$" + LambdaStaticMethodReference.class.getSimpleName() + ";",
                  "methodReturningI",
                  "()Lcom/android/tools/r8/desugar/lambdas/LambdaCallTargetAnnotationTest$I;");

              checkAnnotation(
                  inspector
                      .clazz(
                          syntheticItems.syntheticLambdaClass(
                              LambdaVirtualMethodReference.class, 0))
                      .annotation("com.android.tools.r8.annotations.LambdaMethod"),
                  prefix + "$" + LambdaVirtualMethodReference.class.getSimpleName() + ";",
                  "methodReturningI",
                  "()Lcom/android/tools/r8/desugar/lambdas/LambdaCallTargetAnnotationTest$I;");
              checkAnnotation(
                  inspector
                      .clazz(
                          syntheticItems.syntheticLambdaClass(
                              LambdaVirtualMethodReference.class, 1))
                      .annotation("com.android.tools.r8.annotations.LambdaMethod"),
                  prefix + "$" + LambdaVirtualMethodReference.class.getSimpleName() + ";",
                  "methodReturningJ",
                  "(I)Lcom/android/tools/r8/desugar/lambdas/LambdaCallTargetAnnotationTest$J;");

              checkAnnotation(
                  inspector
                      .clazz(syntheticItems.syntheticLambdaClass(LambdaMethodWithCaptures.class, 0))
                      .annotation("com.android.tools.r8.annotations.LambdaMethod"),
                  prefix + "$" + LambdaMethodWithCaptures.class.getSimpleName() + ";",
                  "lambda$testILambda$0",
                  "(I)V");
              checkAnnotation(
                  inspector
                      .clazz(syntheticItems.syntheticLambdaClass(LambdaMethodWithCaptures.class, 1))
                      .annotation("com.android.tools.r8.annotations.LambdaMethod"),
                  prefix + "$" + LambdaMethodWithCaptures.class.getSimpleName() + ";",
                  "lambda$testJLambda$1",
                  "(JI)V");

              checkAnnotation(
                  inspector
                      .clazz(syntheticItems.syntheticLambdaClass(ConstructorReference.class, 0))
                      .annotation("com.android.tools.r8.annotations.LambdaMethod"),
                  prefix + "$" + IImpl.class.getSimpleName() + ";",
                  "<init>",
                  "()V");
              checkAnnotation(
                  inspector
                      .clazz(syntheticItems.syntheticLambdaClass(ConstructorReference.class, 1))
                      .annotation("com.android.tools.r8.annotations.LambdaMethod"),
                  prefix + "$" + ConstructorReference.class.getSimpleName() + ";",
                  "lambda$testInstantiateJ$0",
                  "(I)V");

              checkAnnotation(
                  inspector
                      .clazz(
                          syntheticItems.syntheticLambdaClass(
                              ConstructorReferenceWithCaptures.class, 0))
                      .annotation("com.android.tools.r8.annotations.LambdaMethod"),
                  prefix + "$" + ConstructorReferenceWithCaptures.class.getSimpleName() + ";",
                  "lambda$testInstantiateJWithCapture$0",
                  "(IJLjava/lang/String;I)V");
            });
  }

  interface I {
    void foo();
  }

  static class IImpl implements I {
    public void foo() {}
  }

  interface J {
    void foo(int x);
  }

  static class JImpl implements J {
    public JImpl(int x, long y, String z) {}

    public void foo(int x) {}
  }

  static class LambdaMethod {
    public static void methodTakingI(I i) {}

    public static void methodTakingJ(J i) {}

    public static void testILambda() {
      methodTakingI(() -> {});
    }

    public static void testJLambda() {
      methodTakingJ((p) -> {});
    }
  }

  static class LambdaStaticMethodReference {
    public static void methodTakingI(I i) {}

    public static void methodTakingJ(J i) {}

    public static I methodReturningI() {
      return null;
    }

    public static J methodReturningJ(int i) {
      return null;
    }

    public static void testILambda() {
      methodTakingI(LambdaStaticMethodReference::methodReturningI);
    }

    public static void testJLambda() {
      methodTakingJ(LambdaStaticMethodReference::methodReturningJ);
    }
  }

  static class LambdaVirtualMethodReference {
    public static void methodTakingI(I i) {}

    public static void methodTakingJ(J i) {}

    public I methodReturningI() {
      return null;
    }

    public J methodReturningJ(int i) {
      return null;
    }

    public static void testILambda() {
      LambdaVirtualMethodReference instance = new LambdaVirtualMethodReference();
      methodTakingI(instance::methodReturningI);
    }

    public static void testJLambda() {
      LambdaVirtualMethodReference instance = new LambdaVirtualMethodReference();
      methodTakingJ(instance::methodReturningJ);
    }
  }

  static class LambdaMethodWithCaptures {
    public static void methodTakingI(I i) {}

    public static void methodTakingJ(J i) {}

    public static void testILambda(int x) {
      methodTakingI(
          () -> {
            System.out.println(x);
          });
    }

    public static void testJLambda(long y) {
      methodTakingJ(
          (p) -> {
            System.out.println(y);
          });
    }
  }

  static class ConstructorReference {
    public static void methodTakingI(I i) {}

    public static void methodTakingJ(J j) {}

    public static void testInstantiateI() {
      methodTakingI(IImpl::new);
    }

    public static void testInstantiateJ() {
      methodTakingJ((p) -> new JImpl(0, 1l, ""));
    }
  }

  static class ConstructorReferenceWithCaptures {
    public static void methodTakingJ(J j) {}

    public static void testInstantiateJWithCapture(int x, long y, String z) {
      methodTakingJ((p) -> new JImpl(x, y, z));
    }
  }
}
