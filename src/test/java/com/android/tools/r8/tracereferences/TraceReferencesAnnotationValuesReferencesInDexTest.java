// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.tracereferences;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class TraceReferencesAnnotationValuesReferencesInDexTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  @Parameter(0)
  public TestParameters parameters;

  private static List<Class<?>> SOURCE_CLASSES =
      ImmutableList.of(
          Source.class,
          SourceAnnotationWithClassConstant.class,
          SourceAnnotationWithClassConstantArray.class);

  static class Consumer implements TraceReferencesConsumer {

    Set<ClassReference> tracedTypes = new HashSet<>();
    Set<MethodReference> tracedMethods = new HashSet<>();

    @Override
    public void acceptType(TracedClass tracedClass, DiagnosticsHandler handler) {
      assertFalse(tracedClass.isMissingDefinition());
      tracedTypes.add(tracedClass.getReference());
    }

    @Override
    public void acceptField(TracedField tracedField, DiagnosticsHandler handler) {
      fail();
    }

    @Override
    public void acceptMethod(TracedMethod tracedMethod, DiagnosticsHandler handler) {
      assertFalse(tracedMethod.isMissingDefinition());
      tracedMethods.add(tracedMethod.getReference());
    }
  }

  private void runTest(Path sourceDex, TraceReferencesConsumer consumer) throws Exception {
    testForTraceReferences()
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.LATEST))
        .addSourceFiles(sourceDex)
        .addTargetClasses(
            TargetAnnotationWithInt.class,
            TargetAnnotationWithLongArray.class,
            TargetAnnotationWithClassConstant.class,
            TargetAnnotationWithClassConstantArray.class,
            A.class,
            B.class,
            C.class,
            D.class,
            E.class,
            F.class)
        .setConsumer(consumer)
        .trace();
  }

  private void test(Path sourceDex) throws Exception {
    Consumer consumer = new Consumer();
    runTest(sourceDex, consumer);
    assertEquals(
        ImmutableSet.of(
            Reference.classFromClass(TargetAnnotationWithInt.class),
            Reference.classFromClass(TargetAnnotationWithLongArray.class),
            Reference.classFromClass(TargetAnnotationWithClassConstant.class),
            Reference.classFromClass(TargetAnnotationWithClassConstantArray.class),
            Reference.classFromClass(A.class),
            Reference.classFromClass(B.class),
            Reference.classFromClass(C.class),
            Reference.classFromClass(D.class),
            Reference.classFromClass(E.class),
            Reference.classFromClass(F.class)),
        consumer.tracedTypes);
  }

  private void testGeneratedKeepRules(Path sourceDex) throws Exception {
    StringBuilder keepRulesBuilder = new StringBuilder();
    runTest(
        sourceDex,
        TraceReferencesKeepRules.builder()
            .setOutputConsumer((string, handler) -> keepRulesBuilder.append(string))
            .build());
    String expected =
        StringUtils.lines(
            "-keep class " + A.class.getTypeName() + " {",
            "}",
            "-keep class " + B.class.getTypeName() + " {",
            "}",
            "-keep class " + C.class.getTypeName() + " {",
            "}",
            "-keep class " + D.class.getTypeName() + " {",
            "}",
            "-keep class " + E.class.getTypeName() + " {",
            "}",
            "-keep class " + F.class.getTypeName() + " {",
            "}",
            "-keep @interface " + TargetAnnotationWithClassConstant.class.getTypeName() + " {",
            "  public java.lang.Class value();",
            "}",
            "-keep @interface " + TargetAnnotationWithClassConstantArray.class.getTypeName() + " {",
            "  public java.lang.Class[] value();",
            "}",
            "-keep @interface " + TargetAnnotationWithInt.class.getTypeName() + " {",
            "  public int value();",
            "}",
            "-keep @interface " + TargetAnnotationWithLongArray.class.getTypeName() + " {",
            "  public long[] value();",
            "}");
    assertEquals(expected, keepRulesBuilder.toString());
  }

  @Test
  public void testDexArchive() throws Throwable {
    Path archive = testForD8(Backend.DEX).addProgramClasses(SOURCE_CLASSES).compile().writeToZip();
    test(archive);
    testGeneratedKeepRules(archive);
  }

  @Test
  public void testDexFile() throws Throwable {
    Path dex =
        testForD8(Backend.DEX)
            .addProgramClasses(SOURCE_CLASSES)
            .compile()
            .writeToDirectory()
            .resolve("classes.dex");
    test(dex);
    testGeneratedKeepRules(dex);
  }

  public class A {}

  public class B {}

  public class C {}

  public class D {}

  public class E {}

  public class F {}

  @Retention(RetentionPolicy.RUNTIME)
  public @interface TargetAnnotationWithInt {
    int value() default 0;
  }

  @Retention(RetentionPolicy.RUNTIME)
  public @interface TargetAnnotationWithLongArray {
    long[] value() default {0L, 1L};
  }

  @Retention(RetentionPolicy.RUNTIME)
  public @interface TargetAnnotationWithClassConstant {
    Class<?> value();
  }

  @Retention(RetentionPolicy.RUNTIME)
  public @interface TargetAnnotationWithClassConstantArray {
    Class<?>[] value();
  }

  @Retention(RetentionPolicy.RUNTIME)
  public @interface SourceAnnotationWithClassConstant {
    Class<?> value() default D.class;
  }

  @Retention(RetentionPolicy.RUNTIME)
  public @interface SourceAnnotationWithClassConstantArray {
    Class<?>[] value() default {E.class, F.class};
  }

  @TargetAnnotationWithInt(1)
  @TargetAnnotationWithLongArray({2L, 3L})
  @TargetAnnotationWithClassConstant(A.class)
  @TargetAnnotationWithClassConstantArray({B.class, C.class})
  @SourceAnnotationWithClassConstant
  @SourceAnnotationWithClassConstantArray
  static class Source {}
}
