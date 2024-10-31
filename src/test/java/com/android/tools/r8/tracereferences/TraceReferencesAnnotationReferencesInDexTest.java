// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.tracereferences;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.diagnostic.DefinitionContext;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableSet;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class TraceReferencesAnnotationReferencesInDexTest extends TestBase {
  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  @Parameter(0)
  public TestParameters parameters;

  static class Consumer implements TraceReferencesConsumer {

    Map<ClassReference, DefinitionContext> tracedTypes = new HashMap<>();

    @Override
    public void acceptType(TracedClass tracedClass, DiagnosticsHandler handler) {
      assertFalse(tracedClass.isMissingDefinition());
      DefinitionContext prev =
          tracedTypes.put(tracedClass.getReference(), tracedClass.getReferencedFromContext());
      assert prev == null;
    }

    @Override
    public void acceptField(TracedField tracedField, DiagnosticsHandler handler) {
      fail();
    }

    @Override
    public void acceptMethod(TracedMethod tracedMethod, DiagnosticsHandler handler) {
      fail();
    }
  }

  private void runTest(Path sourceDex, TraceReferencesConsumer consumer) throws Exception {
    testForTraceReferences()
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.LATEST))
        .addSourceFiles(sourceDex)
        .addTargetClasses(
            ClassAnnotation.class,
            FieldAnnotation.class,
            MethodAnnotation.class,
            ConstructorAnnotation.class,
            ParameterAnnotation.class)
        .setConsumer(consumer)
        .trace();
  }

  private void test(Path sourceDex) throws Exception {
    Consumer consumer = new Consumer();
    runTest(sourceDex, consumer);
    assertEquals(
        ImmutableSet.of(
            Reference.classFromClass(ClassAnnotation.class),
            Reference.classFromClass(ConstructorAnnotation.class),
            Reference.classFromClass(FieldAnnotation.class),
            Reference.classFromClass(MethodAnnotation.class),
            Reference.classFromClass(ParameterAnnotation.class)),
        consumer.tracedTypes.keySet());
    assertTrue(
        consumer.tracedTypes.get(Reference.classFromClass(ClassAnnotation.class)).isClassContext());
    assertTrue(
        consumer
            .tracedTypes
            .get(Reference.classFromClass(ConstructorAnnotation.class))
            .isMethodContext());
    assertTrue(
        consumer.tracedTypes.get(Reference.classFromClass(FieldAnnotation.class)).isFieldContext());
    assertTrue(
        consumer
            .tracedTypes
            .get(Reference.classFromClass(MethodAnnotation.class))
            .isMethodContext());
    assertTrue(
        consumer
            .tracedTypes
            .get(Reference.classFromClass(ParameterAnnotation.class))
            .isMethodContext());
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
            "-keep @interface " + ClassAnnotation.class.getTypeName() + " {",
            "}",
            "-keep @interface " + ConstructorAnnotation.class.getTypeName() + " {",
            "}",
            "-keep @interface " + FieldAnnotation.class.getTypeName() + " {",
            "}",
            "-keep @interface " + MethodAnnotation.class.getTypeName() + " {",
            "}",
            "-keep @interface " + ParameterAnnotation.class.getTypeName() + " {",
            "}");
    assertEquals(expected, keepRulesBuilder.toString());
  }

  @Test
  public void testDexArchive() throws Throwable {
    Path archive = testForD8(Backend.DEX).addProgramClasses(Source.class).compile().writeToZip();
    test(archive);
    testGeneratedKeepRules(archive);
  }

  @Test
  public void testDexFile() throws Throwable {
    Path dex =
        testForD8(Backend.DEX)
            .addProgramClasses(Source.class)
            .compile()
            .writeToDirectory()
            .resolve("classes.dex");
    test(dex);
    testGeneratedKeepRules(dex);
  }

  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.TYPE)
  public @interface ClassAnnotation {}

  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.FIELD)
  public @interface FieldAnnotation {}

  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.METHOD)
  public @interface MethodAnnotation {}

  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.CONSTRUCTOR)
  public @interface ConstructorAnnotation {}

  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.PARAMETER)
  public @interface ParameterAnnotation {}

  @ClassAnnotation
  static class Source {
    @FieldAnnotation public static int field;

    @ConstructorAnnotation
    public Source() {}

    @MethodAnnotation
    public static void source(@ParameterAnnotation int param) {}
  }
}
