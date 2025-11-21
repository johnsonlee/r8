// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.tracereferences;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.ZipUtils.ZipBuilder;
import com.google.common.collect.ImmutableSet;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

// From b/319190998.
@RunWith(Parameterized.class)
public class TraceReferencesDefaultMethodInSubInterfaceTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withAllRuntimesAndApiLevels()
        .withAllApiLevelsAlsoForCf()
        .withNoneRuntime()
        .build();
  }

  static Path targetJar;
  static Path sourceJar;
  private static final String EXPECTED_OUTPUT = StringUtils.lines("Hello, world!");

  @BeforeClass
  public static void setUp() throws Exception {
    Path dir = getStaticTemp().newFolder().toPath();
    targetJar =
        ZipBuilder.builder(dir.resolve("target.jar"))
            .addFilesRelative(
                ToolHelper.getClassPathForTests(),
                ToolHelper.getClassFileForTestClass(I.class),
                ToolHelper.getClassFileForTestClass(J.class))
            .build();
    sourceJar =
        ZipBuilder.builder(dir.resolve("source.jar"))
            .addFilesRelative(
                ToolHelper.getClassPathForTests(),
                ToolHelper.getClassFileForTestClass(JImpl.class),
                ToolHelper.getClassFileForTestClass(Main.class))
            .build();
  }

  static class SeenReferencesConsumer implements TraceReferencesConsumer {

    private final Set<MethodReference> seenMethods = new HashSet<>();

    @Override
    public void acceptType(TracedClass tracedClass, DiagnosticsHandler handler) {}

    @Override
    public void acceptField(TracedField tracedField, DiagnosticsHandler handler) {}

    @Override
    public void acceptMethod(TracedMethod tracedMethod, DiagnosticsHandler handler) {
      seenMethods.add(tracedMethod.getReference());
    }
  }

  @Test
  public void testTracedReferences() throws Exception {
    assumeTrue(parameters.isNoneRuntime());
    SeenReferencesConsumer consumer = new SeenReferencesConsumer();
    TraceReferences.run(
        TraceReferencesCommand.builder()
            .addLibraryFiles(ToolHelper.getMostRecentAndroidJar())
            .addSourceFiles(sourceJar)
            .addTargetFiles(targetJar)
            .setConsumer(consumer)
            .build());

    // TODO(b/319190998): Just tracing I.m is not enough.
    ImmutableSet<MethodReference> expectedSet =
        ImmutableSet.of(
            Reference.method(
                Reference.classFromClass(I.class),
                "m",
                Collections.emptyList(),
                Reference.classFromClass(Object.class)));
    assertEquals(expectedSet, consumer.seenMethods);
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addProgramFiles(sourceJar)
        .addProgramFiles(targetJar)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    Path targetDex =
        testForD8().setMinApi(parameters).addProgramFiles(targetJar).compile().writeToZip();

    testForD8()
        .setMinApi(parameters)
        .addClasspathFiles(targetJar)
        .addProgramFiles(sourceJar)
        .addRunClasspathFiles(targetDex)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testGeneratedKeepRulesFollowedByR8() throws Exception {
    parameters.assumeR8TestParameters();

    Path generatedKeepRules = temp.newFile("keep.rules").toPath();
    TraceReferencesKeepRules keepRulesConsumer =
        TraceReferencesKeepRules.builder()
            // The use of keeper in b/319190998 disables obfuscation of generated keep rules.
            .setAllowObfuscation(false)
            .setOutputPath(generatedKeepRules)
            .build();
    TraceReferences.run(
        TraceReferencesCommand.builder()
            .addLibraryFiles(ToolHelper.getMostRecentAndroidJar())
            .addSourceFiles(sourceJar)
            .addTargetFiles(targetJar)
            .setConsumer(keepRulesConsumer)
            .build());

    Path r8CompiledTarget =
        testForR8(parameters.getBackend())
            .setMinApi(parameters)
            .addProgramFiles(targetJar)
            .addKeepRuleFiles(generatedKeepRules)
            .compile()
            .writeToZip();

    testForR8(parameters.getBackend())
        .setMinApi(parameters)
        .addClasspathFiles(targetJar)
        .addProgramFiles(sourceJar)
        .addRunClasspathFiles(r8CompiledTarget)
        .addKeepMainRule(Main.class)
        .run(parameters.getRuntime(), Main.class)
        .applyIf(
            parameters.hasDefaultInterfaceMethodsSupport(),
            r -> r.assertSuccessWithOutputLines("Hello, world!"),
            // TODO(b/319190998): This should not fail.
            r -> r.assertFailureWithErrorThatThrows(NoClassDefFoundError.class));
  }

  @Test
  public void testGeneratedKeepRulesWithMissingRuleFollowedByR8() throws Exception {
    parameters.assumeR8TestParameters();

    Path generatedKeepRules = temp.newFile("keep.rules").toPath();
    TraceReferencesKeepRules keepRulesConsumer =
        TraceReferencesKeepRules.builder()
            .setAllowObfuscation(true)
            .setOutputPath(generatedKeepRules)
            .build();
    TraceReferences.run(
        TraceReferencesCommand.builder()
            .addLibraryFiles(ToolHelper.getMostRecentAndroidJar())
            .addSourceFiles(sourceJar)
            .addTargetFiles(targetJar)
            .setConsumer(keepRulesConsumer)
            .build());

    Path proguardMap = temp.newFolder().toPath().resolve("mapping.txt");
    Path r8CompiledTarget =
        testForR8(parameters.getBackend())
            .setMinApi(parameters)
            .addProgramFiles(targetJar)
            .addKeepRuleFiles(generatedKeepRules)
            .addKeepRules("-keep class " + J.class.getTypeName() + " { m(); }")
            .compile()
            .apply(r -> r.writeProguardMap(proguardMap))
            .writeToZip();

    testForR8(parameters.getBackend())
        .setMinApi(parameters)
        .addClasspathFiles(targetJar)
        .addProgramFiles(sourceJar)
        .addApplyMapping(proguardMap)
        .addKeepMainRule(Main.class)
        .addRunClasspathFiles(r8CompiledTarget)
        .run(parameters.getRuntime(), Main.class)
        .applyIf(
            parameters.isCfRuntime() || parameters.canUseDefaultAndStaticInterfaceMethods(),
            rr -> rr.assertSuccessWithOutput(EXPECTED_OUTPUT),
            parameters.isDexRuntime() && parameters.getDexRuntimeVersion().isDalvik(),
            rr -> rr.assertFailureWithErrorThatThrows(NoClassDefFoundError.class),
            rr -> rr.assertFailureWithErrorThatThrows(ClassNotFoundException.class));
  }

  @Test
  public void testGeneratedKeepRulesWithMissingRuleFollowedByD8() throws Exception {
    parameters.assumeDexRuntime();

    Path generatedKeepRules = temp.newFile("keep.rules").toPath();
    TraceReferencesKeepRules keepRulesConsumer =
        TraceReferencesKeepRules.builder()
            // Don't obfuscate as D8 does not support apply mapping.
            .setAllowObfuscation(false)
            .setOutputPath(generatedKeepRules)
            .build();
    TraceReferences.run(
        TraceReferencesCommand.builder()
            .addLibraryFiles(ToolHelper.getMostRecentAndroidJar())
            .addSourceFiles(sourceJar)
            .addTargetFiles(targetJar)
            .setConsumer(keepRulesConsumer)
            .build());

    Path r8CompiledTarget =
        testForR8(Backend.DEX)
            .setMinApi(parameters)
            .addProgramFiles(targetJar)
            .addKeepRuleFiles(generatedKeepRules)
            .addKeepRules("-keep class " + J.class.getTypeName() + " { m(); }")
            .compile()
            .writeToZip();

    testForD8(Backend.DEX)
        .setMinApi(parameters)
        .addClasspathFiles(targetJar)
        .addProgramFiles(sourceJar)
        .addRunClasspathFiles(r8CompiledTarget)
        .run(parameters.getRuntime(), Main.class)
        .applyIf(
            parameters.hasDefaultInterfaceMethodsSupport(),
            r -> r.assertSuccessWithOutput(EXPECTED_OUTPUT),
            // TODO(b/319190998): This should not fail.
            r -> r.assertFailureWithErrorThatThrows(NoClassDefFoundError.class));
  }

  @Test
  public void testGeneratedKeepRulesWithMissingRuleAndDontObfuscateFollowedByD8() throws Exception {
    parameters.assumeDexRuntime();

    Path generatedKeepRules = temp.newFile("keep.rules").toPath();
    TraceReferencesKeepRules keepRulesConsumer =
        TraceReferencesKeepRules.builder()
            // Don't obfuscate as D8 does not support apply mapping.
            .setAllowObfuscation(false)
            .setOutputPath(generatedKeepRules)
            .build();
    TraceReferences.run(
        TraceReferencesCommand.builder()
            .addLibraryFiles(ToolHelper.getMostRecentAndroidJar())
            .addSourceFiles(sourceJar)
            .addTargetFiles(targetJar)
            .setConsumer(keepRulesConsumer)
            .build());

    Path r8CompiledTarget =
        testForR8(Backend.DEX)
            .setMinApi(parameters)
            .addProgramFiles(targetJar)
            .addKeepRuleFiles(generatedKeepRules)
            .addKeepRules("-keep class " + J.class.getTypeName() + " { m(); }")
            // TODO(b/319190998): Adding dont obfuscate should not be needed as trace references is
            //  already asked to not allow obfuscation. Hwing this will cause the CC class to not
            //  get renamed.
            .addDontObfuscate()
            .compile()
            .writeToZip();

    testForD8(Backend.DEX)
        .setMinApi(parameters)
        .addClasspathFiles(targetJar)
        .addProgramFiles(sourceJar)
        .addRunClasspathFiles(r8CompiledTarget)
        .run(parameters.getRuntime(), Main.class)
        .applyIf(
            parameters.canUseDefaultAndStaticInterfaceMethods(),
            rr -> rr.assertSuccessWithOutput(EXPECTED_OUTPUT),
            parameters.isDexRuntime() && parameters.getDexRuntimeVersion().isDalvik(),
            rr -> rr.assertFailureWithErrorThatThrows(NoClassDefFoundError.class),
            rr -> rr.assertFailureWithErrorThatThrows(ClassNotFoundException.class));
  }

  // Interfaces I and J are in the target set for trace references.
  interface I {
    Object m();
  }

  interface J extends I {
    default Object m() {
      return "Hello, world!";
    }
  }

  // Interfaces JImpl and Main are in the source set for trace references.
  public static class JImpl implements J {}

  public static class Main {

    public static void m(I i) {
      System.out.println(i.m());
    }

    public static void main(String[] args) {
      m(new JImpl());
    }
  }
}
