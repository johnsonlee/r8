// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.tracereferences;

import static com.android.tools.r8.tracereferences.TraceReferencesTestUtils.collectMissingClassReferences;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.google.common.collect.ImmutableSet;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class TraceReferencesMissingAnnotationReferencesInDexTest extends TestBase {
  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  @Parameter(0)
  public TestParameters parameters;

  static class MissingReferencesConsumer implements TraceReferencesConsumer {

    Set<ClassReference> missingTypes = new HashSet<>();

    @Override
    public void acceptType(TracedClass tracedClass, DiagnosticsHandler handler) {
      assertTrue(tracedClass.isMissingDefinition());
      missingTypes.add(tracedClass.getReference());
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

  private void missingClassReferenced(Path sourceDex) {
    Set<ClassReference> expectedMissingClasses =
        ImmutableSet.of(
            Reference.classFromClass(ClassAnnotation.class),
            Reference.classFromClass(FieldAnnotation.class),
            Reference.classFromClass(MethodAnnotation.class),
            Reference.classFromClass(ConstructorAnnotation.class),
            Reference.classFromClass(ParameterAnnotation.class));

    MissingReferencesConsumer consumer = new MissingReferencesConsumer();
    assertThrows(
        CompilationFailedException.class,
        () ->
            testForTraceReferences()
                .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.LATEST))
                .addSourceFiles(sourceDex)
                .setConsumer(new TraceReferencesCheckConsumer(consumer))
                .traceWithExpectedDiagnostics(
                    diagnostics ->
                        assertEquals(
                            expectedMissingClasses, collectMissingClassReferences(diagnostics))));

    assertEquals(expectedMissingClasses, consumer.missingTypes);
  }

  @Test
  public void missingClassReferencedInDexArchive() throws Throwable {
    missingClassReferenced(
        testForD8(Backend.DEX).addProgramClasses(Source.class).compile().writeToZip());
  }

  @Test
  public void missingClassReferencedInDexFile() throws Throwable {
    missingClassReferenced(
        testForD8(Backend.DEX)
            .addProgramClasses(Source.class)
            .compile()
            .writeToDirectory()
            .resolve("classes.dex"));
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
