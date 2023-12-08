// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.lambdas;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.lang.invoke.LambdaConversionException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Type;

// Regression for b/314821730 (wide variant).
@RunWith(Parameterized.class)
public class LambdaWithPrimitiveReferenceTypeConflictWideTest extends TestBase {

  private enum Variant {
    ID,
    RETURN_BYTE("B"),
    RETURN_SHORT("S"),
    RETURN_INT("I"),
    RETURN_LONG("J"),
    RETURN_FLOAT("F"),
    RETURN_DOUBLE("D"),
    ARGUMENT;

    final String desc;

    Variant() {
      this(null);
    }

    Variant(String desc) {
      this.desc = desc;
    }
  }

  static final String EXPECTED = StringUtils.lines("1,2,3");

  private final TestParameters parameters;
  private final Variant variant;

  @Parameterized.Parameters(name = "{0}, {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters()
            .withCfRuntimes()
            .withDefaultDexRuntime()
            .withApiLevel(AndroidApiLevel.B)
            .enableApiLevelsForCf()
            .build(),
        Variant.values());
  }

  public LambdaWithPrimitiveReferenceTypeConflictWideTest(
      TestParameters parameters, Variant variant) {
    this.parameters = parameters;
    this.variant = variant;
  }

  private boolean expectCompilationError() {
    return variant == Variant.ARGUMENT || expectJvmRuntimeError();
  }

  private boolean expectJvmRuntimeError() {
    // This should be the same as what we allow compilation failure for, but JDK8 doesn't verify
    // the argument constraints so the program runs.
    return variant == Variant.RETURN_BYTE
        || variant == Variant.RETURN_SHORT
        || variant == Variant.RETURN_INT
        || (variant == Variant.ARGUMENT && !parameters.isCfRuntime(CfVm.JDK8));
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addProgramClasses(getProgramClasses())
        .addProgramClassFileData(getTransformedClasses())
        .run(parameters.getRuntime(), TestClass.class)
        .applyIf(
            expectJvmRuntimeError(),
            r -> r.assertFailureWithErrorThatThrows(LambdaConversionException.class),
            r -> r.assertSuccessWithOutput(EXPECTED));
  }

  @Test
  public void testD8() throws Exception {
    try {
      testForD8(parameters.getBackend())
          .addProgramClasses(getProgramClasses())
          .addProgramClassFileData(getTransformedClasses())
          .setMinApi(parameters)
          .run(parameters.getRuntime(), TestClass.class)
          .assertSuccessWithOutput(EXPECTED);
      assertFalse(expectCompilationError());
    } catch (CompilationFailedException e) {
      if (!expectCompilationError()) {
        throw e;
      }
    }
  }

  @Test
  public void testR8() throws Exception {
    try {
      testForR8(parameters.getBackend())
          .addProgramClasses(getProgramClasses())
          .addProgramClassFileData(getTransformedClasses())
          .addKeepMainRule(TestClass.class)
          .setMinApi(parameters)
          .run(parameters.getRuntime(), TestClass.class)
          .assertSuccessWithOutput(EXPECTED);
      assertFalse(expectCompilationError());
    } catch (CompilationFailedException e) {
      if (!expectCompilationError()) {
        throw e;
      }
    }
  }

  List<Class<?>> getProgramClasses() {
    return ImmutableList.of(F1.class, Seq.class, LSeq.class, TestClass.class);
  }

  List<byte[]> getTransformedClasses() throws Exception {
    return ImmutableList.of(getTransformedExample(variant));
  }

  private static byte[] getTransformedExample(Variant variant) throws IOException {
    return transformer(Example.class)
        .transformInvokeDynamicInsnInMethod(
            "foo",
            (invokedName,
                invokedType,
                bootstrapMethodHandle,
                bootstrapMethodArguments,
                visitor) -> {
              Type samMethodType = (Type) bootstrapMethodArguments.get(0);
              Handle implMethod = (Handle) bootstrapMethodArguments.get(1);
              Type instantiatedMethodType = (Type) bootstrapMethodArguments.get(2);
              String desc = instantiatedMethodType.getDescriptor();
              if (variant == Variant.ID) {
                // No change.
              } else if (variant == Variant.ARGUMENT) {
                instantiatedMethodType = Type.getType(desc.replace("(Ljava/lang/Long;)", "(J)"));
              } else {
                assertNotNull(variant.desc);
                instantiatedMethodType =
                    Type.getType(desc.replace(")Ljava/lang/Long;", ")" + variant.desc));
              }
              visitor.visitInvokeDynamicInsn(
                  invokedName,
                  invokedType,
                  bootstrapMethodHandle,
                  samMethodType,
                  implMethod,
                  instantiatedMethodType);
            })
        .transform();
  }

  interface F1<T, R> {
    R apply(T o);
  }

  interface Seq<T> {
    <R> Seq<R> map(F1<T, R> fn);

    String join();
  }

  static class LSeq<T> implements Seq<T> {
    private final List<T> items;

    public LSeq(List<T> items) {
      this.items = items;
    }

    @Override
    public <R> Seq<R> map(F1<T, R> fn) {
      ArrayList<R> mapped = new ArrayList<>(items.size());
      for (T item : items) {
        mapped.add(fn.apply(item));
      }
      return new LSeq<>(mapped);
    }

    @Override
    public String join() {
      StringBuilder builder = new StringBuilder();
      for (int i = 0; i < items.size(); i++) {
        if (i > 0) {
          builder.append(",");
        }
        builder.append(items.get(i));
      }
      return builder.toString();
    }
  }

  static class Example {

    // Implementation method defined on primitive types.
    static long fn(long x) {
      return x;
    }

    static String foo(Seq<Long> values) {
      // JavaC generated bootstrap method will use boxed types for arguments and return type.
      // The method reference is transformed to various primitive types.
      return values.map(Example::fn).join();
    }
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println(Example.foo(new LSeq<>(Arrays.asList((long) 1, (long) 2, (long) 3))));
    }
  }
}
