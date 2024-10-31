// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.tracereferences;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticType;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.diagnostic.MissingDefinitionsDiagnostic;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.FieldReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class TraceReferencesMissingReferencesInDexTest extends TestBase {
  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  @Parameter(0)
  public TestParameters parameters;

  static class MissingReferencesConsumer implements TraceReferencesConsumer {

    Set<ClassReference> missingTypes = new HashSet<>();
    Set<FieldReference> missingFields = new HashSet<>();
    Set<MethodReference> missingMethods = new HashSet<>();

    boolean hasMissingTypes() {
      return missingTypes.size() > 0;
    }

    boolean hasMissingFields() {
      return missingFields.size() > 0;
    }

    boolean hasMissingMethods() {
      return missingMethods.size() > 0;
    }

    @Override
    public void acceptType(TracedClass tracedClass, DiagnosticsHandler handler) {
      assertTrue(tracedClass.isMissingDefinition());
      missingTypes.add(tracedClass.getReference());
    }

    @Override
    public void acceptField(TracedField tracedField, DiagnosticsHandler handler) {
      assertTrue(tracedField.isMissingDefinition());
      missingFields.add(tracedField.getReference());
      assertEquals(
          Reference.classFromClass(Target.class), tracedField.getReference().getHolderClass());
      assertEquals("field", tracedField.getReference().getFieldName());
    }

    @Override
    public void acceptMethod(TracedMethod tracedMethod, DiagnosticsHandler handler) {
      assertTrue(tracedMethod.isMissingDefinition());
      missingMethods.add(tracedMethod.getReference());
      assertEquals(
          Reference.classFromClass(Target.class), tracedMethod.getReference().getHolderClass());
      assertEquals("target", tracedMethod.getReference().getMethodName());
    }
  }

  private void missingClassReferenced(Path sourceDex) {
    Set<ClassReference> expectedMissingClasses =
        ImmutableSet.of(Reference.classFromClass(Target.class));

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
                            expectedMissingClasses,
                            TraceReferencesTestUtils.filterAndCollectMissingClassReferences(
                                diagnostics))));

    assertTrue(consumer.hasMissingTypes());
    assertEquals(expectedMissingClasses, consumer.missingTypes);
    assertTrue(consumer.hasMissingFields());
    assertTrue(consumer.hasMissingMethods());
  }

  @Test
  public void missingClassReferencedInDexArchive() throws Throwable {
    missingClassReferenced(
        testForD8(Backend.DEX)
            .addProgramClasses(Source.class)
            .compile()
            .writeToZip());
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

  private void missingFieldAndMethodReferenced(Path sourceDex) {
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
                        diagnostics.assertAllDiagnosticsMatch(
                            diagnosticType(MissingDefinitionsDiagnostic.class))));

    assertFalse(consumer.hasMissingTypes());
    assertTrue(consumer.hasMissingFields());
    assertTrue(consumer.hasMissingMethods());
  }

  @Test
  public void missingFieldAndMethodReferencedInDexArchive() throws Throwable {
    missingFieldAndMethodReferenced(
        testForD8(Backend.DEX)
            .addProgramClasses(Source.class)
            .addProgramClassFileData(getClassWithTargetRemoved())
            .compile()
            .writeToZip());
  }

  @Test
  public void missingFieldAndMethodReferencedInDexFile() throws Throwable {
    missingFieldAndMethodReferenced(
        testForD8(Backend.DEX)
            .addProgramClasses(Source.class)
            .addProgramClassFileData(getClassWithTargetRemoved())
            .compile()
            .writeToDirectory()
            .resolve("classes.dex"));
  }

  private byte[] getClassWithTargetRemoved() throws IOException {
    return transformer(Target.class)
        .removeMethods((access, name, descriptor, signature, exceptions) -> name.equals("target"))
        .removeFields((access, name, descriptor, signature, value) -> name.equals("field"))
        .transform();
  }

  static class Target {
    public static int field;

    public static void target(int i) {}
  }

  static class Source {
    public static void source() {
      Target.target(Target.field);
    }
  }
}
