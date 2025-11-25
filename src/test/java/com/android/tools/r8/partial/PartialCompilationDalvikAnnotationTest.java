// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.partial;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static junit.framework.TestCase.assertFalse;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.partial.R8PartialCompilationConfiguration.Builder;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.transformers.ClassFileTransformer.MethodPredicate;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class PartialCompilationDalvikAnnotationTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimesAndAllApiLevels().build();
  }

  @Test
  public void testD8() throws Exception {
    testForD8(parameters)
        .addProgramClassFileData(getProgramClassFileData())
        .release()
        .compile()
        .inspect(
            inspector -> {
              MethodSubject mainMethod =
                  inspector.clazz(Main.class).uniqueMethodWithOriginalName("main");
              assertThat(mainMethod, isPresent());
              assertFalse(mainMethod.annotations().isEmpty());
            });
  }

  @Test
  public void testR8Partial() throws Exception {
    testForR8Partial(parameters)
        .addProgramClassFileData(getProgramClassFileData())
        .setR8PartialConfiguration(Builder::excludeAll)
        .compile()
        .inspect(
            inspector -> {
              MethodSubject mainMethod =
                  inspector.clazz(Main.class).uniqueMethodWithOriginalName("main");
              assertThat(mainMethod, isPresent());
              assertFalse(mainMethod.annotations().isEmpty());
            });
  }

  private static byte[] getProgramClassFileData() throws IOException {
    return transformer(Main.class)
        .setAnnotation(
            MethodPredicate.onName("main"),
            annotationBuilder ->
                annotationBuilder.setAnnotationClass(
                    Reference.classFromDescriptor(
                        "Ldalvik/annotation/optimization/CriticalNative;")))
        .transform();
  }

  // Renamed to dalvik.annotation.optimization.CriticalNative.
  @Retention(RetentionPolicy.CLASS)
  @interface CriticalNative {}

  static class Main {

    @CriticalNative
    public static native void main(String[] args);
  }
}
