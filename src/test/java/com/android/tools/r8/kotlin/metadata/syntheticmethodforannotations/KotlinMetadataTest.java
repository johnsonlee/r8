// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin.metadata.syntheticmethodforannotations;

import static com.android.tools.r8.ToolHelper.getFilesInTestFolderRelativeToClass;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.KotlinCompileMemoizer;
import com.android.tools.r8.KotlinCompilerTool.KotlinCompilerVersion;
import com.android.tools.r8.KotlinTestBase;
import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.KmPropertySubject;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import kotlin.metadata.internal.extensions.KmPropertyExtension;
import kotlin.metadata.jvm.JvmMethodSignature;
import kotlin.metadata.jvm.internal.JvmPropertyExtension;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class KotlinMetadataTest extends KotlinTestBase {

  private static final String PACKAGE =
      "com.android.tools.r8.kotlin.metadata.syntheticmethodforannotations.kt";
  private static final String MAIN = PACKAGE + ".MetadataKt";
  private static final List<String> EXPECTED_OUTPUT =
      ImmutableList.of("start", "All has @Test: true", "All2 has @Test: true", "end");
  private static final List<String> EXPECTED_FALSE_OUTPUT =
      ImmutableList.of("start", "All has @Test: false", "All2 has @Test: false", "end");

  private final TestParameters parameters;

  @Parameters(name = "{0}, {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(),
        getKotlinTestParameters().withAllCompilersAndLambdaGenerations().build());
  }

  public KotlinMetadataTest(TestParameters parameters, KotlinTestParameters kotlinParameters) {
    super(kotlinParameters);
    this.parameters = parameters;
  }

  private static final KotlinCompileMemoizer compilationResults =
      getCompileMemoizer(getKotlinSources());

  private static Collection<Path> getKotlinSources() {
    try {
      return getFilesInTestFolderRelativeToClass(KotlinMetadataTest.class, "kt", ".kt");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  public void testJvm() throws ExecutionException, CompilationFailedException, IOException {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addProgramFiles(compilationResults.getForConfiguration(kotlinParameters))
        .addRunClasspathFiles(
            buildOnDexRuntime(
                parameters, kotlinc.getKotlinStdlibJar(), kotlinc.getKotlinReflectJar()))
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutputLines(EXPECTED_OUTPUT);
  }

  @Test
  public void testD8() throws ExecutionException, CompilationFailedException, IOException {
    parameters.assumeDexRuntime();
    testForD8(parameters.getBackend())
        .addProgramFiles(compilationResults.getForConfiguration(kotlinParameters))
        .addProgramFiles(kotlinc.getKotlinStdlibJar())
        .addProgramFiles(kotlinc.getKotlinReflectJar())
        .addProgramFiles(kotlinc.getKotlinAnnotationJar())
        .setMinApi(parameters)
        .enableServiceLoader()
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutputLines(EXPECTED_OUTPUT);
  }

  @Test
  public void testR8() throws Exception {
    Assume.assumeFalse(
        "Kotlin reflect failure on oldest compiler",
        kotlinc.is(KotlinCompilerVersion.KOTLINC_1_3_72));
    testForR8(parameters.getBackend())
        .addProgramFiles(compilationResults.getForConfiguration(kotlinParameters))
        .addProgramFiles(kotlinc.getKotlinStdlibJar())
        .addProgramFiles(kotlinc.getKotlinReflectJar())
        .addProgramFiles(kotlinc.getKotlinAnnotationJar())
        .addKeepRules(
            "-keep class "
                + PACKAGE
                + ".**\n"
                + "-keep,allowobfuscation class "
                + PACKAGE
                + ".** { *; }")
        .addKeepEnumsRule()
        .addKeepMainRule(MAIN)
        .allowUnusedDontWarnPatterns()
        .allowDiagnosticMessages()
        .setMinApi(parameters)
        .compile()
        .inspect(this::verifyRewrittenExtension)
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutputLines(EXPECTED_OUTPUT);
  }

  static JvmMethodSignature toJvmMethodSignature(DexMethod method) {
    StringBuilder descBuilder = new StringBuilder();
    descBuilder.append("(");
    for (DexType argType : method.proto.parameters.values) {
      descBuilder.append(argType.toDescriptorString());
    }
    descBuilder.append(")");
    descBuilder.append(method.proto.returnType.toDescriptorString());
    return new JvmMethodSignature(method.name.toString(), descBuilder.toString());
  }

  private void verifyRewrittenExtension(CodeInspector i) {
    ClassSubject clazz = i.clazz(PACKAGE + ".Data$Companion");
    List<KmPropertySubject> properties = clazz.getKmClass().getProperties();
    assertEquals(2, properties.size());
    for (int i1 = 0; i1 < 2; i1++) {
      KmPropertySubject kmPropertySubject = properties.get(i1);
      assertTrue(kmPropertySubject.name().equals("All") || kmPropertySubject.name().equals("All2"));
      List<KmPropertyExtension> extensions =
          kmPropertySubject.getKmProperty().getExtensions$kotlin_metadata();
      assertEquals(1, extensions.size());
      JvmMethodSignature syntheticMethodForAnnotations =
          ((JvmPropertyExtension) extensions.get(0)).getSyntheticMethodForAnnotations();
      assertTrue(
          clazz.allMethods().stream()
              .anyMatch(
                  m ->
                      toJvmMethodSignature(m.getMethod().getReference())
                          .equals(syntheticMethodForAnnotations)));
    }
  }

  @Test
  public void testR8NoKR() throws Exception {
    Assume.assumeFalse(
        "Kotlin reflect failure on oldest compiler",
        kotlinc.is(KotlinCompilerVersion.KOTLINC_1_3_72));
    testForR8(parameters.getBackend())
        .addProgramFiles(compilationResults.getForConfiguration(kotlinParameters))
        .addProgramFiles(kotlinc.getKotlinStdlibJar())
        .addProgramFiles(kotlinc.getKotlinReflectJar())
        .addProgramFiles(kotlinc.getKotlinAnnotationJar())
        .addKeepRules(
            "-keep class "
                + PACKAGE
                + ".**\n"
                + "-keep,allowobfuscation class "
                + PACKAGE
                + ".** {  getAll(); getAll2(); }")
        .addKeepEnumsRule()
        .addKeepMainRule(MAIN)
        .allowUnusedDontWarnPatterns()
        .allowDiagnosticMessages()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutputLines(EXPECTED_FALSE_OUTPUT);
  }
}
