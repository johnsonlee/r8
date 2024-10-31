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
public class TraceReferencesThrowsInDexTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  @Parameter(0)
  public TestParameters parameters;

  private static List<Class<?>> SOURCE_CLASSES = ImmutableList.of(Source.class);

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
        .addTargetClasses(E1.class, E2.class, E3.class)
        .setConsumer(consumer)
        .trace();
  }

  private void test(Path sourceDex) throws Exception {
    Consumer consumer = new Consumer();
    runTest(sourceDex, consumer);
    assertEquals(
        ImmutableSet.of(
            Reference.classFromClass(E1.class),
            Reference.classFromClass(E2.class),
            Reference.classFromClass(E3.class)),
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
            "-keep class " + E1.class.getTypeName() + " {",
            "}",
            "-keep class " + E2.class.getTypeName() + " {",
            "}",
            "-keep class " + E3.class.getTypeName() + " {",
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

  public static class E1 extends Exception {}

  public static class E2 extends Exception {}

  public static class E3 extends Exception {}

  static class Source {
    public void m1() throws E1 {}

    public void m2() throws E2, E3 {}
  }
}
