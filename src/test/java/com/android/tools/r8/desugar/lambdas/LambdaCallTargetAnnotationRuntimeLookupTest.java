// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.lambdas;

import com.android.tools.r8.D8TestBuilder;
import com.android.tools.r8.GlobalSyntheticsTestingConsumer;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanUtils;
import java.lang.annotation.Annotation;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

@RunWith(Parameterized.class)
public class LambdaCallTargetAnnotationRuntimeLookupTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public boolean intermediate;

  @Parameterized.Parameters(name = "{0}, intermediate = {1}")
  public static List<Object[]> data() {
    return buildParameters(getTestParameters().withDexRuntimes().build(), BooleanUtils.values());
  }

  @Test
  public void testD8() throws Exception {
    GlobalSyntheticsTestingConsumer globals =
        intermediate ? new GlobalSyntheticsTestingConsumer() : null;
    D8TestBuilder builder =
        testForD8(parameters.getBackend())
            .addInnerClasses(LambdaCallTargetAnnotationRuntimeLookupTest.class)
            .setMinApi(AndroidApiLevel.L)
            .debug()
            .setIntermediate(intermediate)
            .addOptionsModification(options -> options.emitLambdaMethodAnnotations = true);
    if (intermediate) {
      // Merge the global synthetics to be able to run the code.
      builder.getBuilder().setGlobalSyntheticsConsumer(globals);
      Path intermediateOutput = builder.compile().writeToZip();
      builder =
          testForD8()
              .setMinApi(AndroidApiLevel.L)
              .debug()
              .addProgramFiles(intermediateOutput)
              .apply(
                  b -> b.getBuilder().addGlobalSyntheticsResourceProviders(globals.getProviders()));
    }
    builder
        .run(
            parameters.getRuntime(),
            TestClass.class,
            SyntheticItemsTestUtils.syntheticLambdaClass(TestClass.class, 0).getTypeName())
        .assertSuccessWithOutputLines(
            "1",
            "true",
            "true",
            Reference.classFromClass(TestClass.class).getDescriptor(),
            "lambda$main$0",
            "()V");
  }

  static class TestClass {
    public static void m(Runnable r) {}

    public static void main(String[] args) throws Exception {
      m(() -> {});
      Class<?> lambdaClass = Class.forName(args[0]);
      Annotation[] annotations = lambdaClass.getAnnotations();
      System.out.println(annotations.length);
      Class<?> annotationClass = Class.forName("com.android.tools.r8.annotations.LambdaMethod");
      System.out.println(annotationClass.isAnnotation());
      Class<? extends Annotation> type = annotations[0].annotationType();
      System.out.println(annotationClass.equals(type));
      System.out.println(type.getMethod("holder").invoke(annotations[0]));
      System.out.println(type.getMethod("method").invoke(annotations[0]));
      System.out.println(type.getMethod("proto").invoke(annotations[0]));
    }
  }
}
