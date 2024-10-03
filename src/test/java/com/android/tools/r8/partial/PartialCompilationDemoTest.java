// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.partial;

import static com.android.tools.r8.ToolHelper.DESUGARED_JDK_11_LIB_JAR;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.L8;
import com.android.tools.r8.L8Command;
import com.android.tools.r8.LibraryDesugaringTestConfiguration;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.StringConsumer.FileConsumer;
import com.android.tools.r8.StringResource;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestCompilerBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.desugar.desugaredlibrary.jdk11.DesugaredLibraryJDK11Undesugarer;
import com.android.tools.r8.dump.CompilerDump;
import com.android.tools.r8.tracereferences.TraceReferences;
import com.android.tools.r8.tracereferences.TraceReferencesCommand;
import com.android.tools.r8.tracereferences.TraceReferencesKeepRules;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.Box;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.Pair;
import com.android.tools.r8.utils.ZipUtils;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Predicate;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

// This test is currently for demonstrating R8 partial compilation, and for now all tests should
// be @Ignore(d) and only enabled for local experiments.
@RunWith(Parameterized.class)
public class PartialCompilationDemoTest extends TestBase {

  private static final Path TIVI_DUMP_PATH =
      Paths.get(ToolHelper.THIRD_PARTY_DIR, "opensource-apps", "tivi", "dump_app.zip");
  private static final Path NOWINANDROID_DUMP_PATH =
      Paths.get(
          ToolHelper.THIRD_PARTY_DIR, "opensource-apps", "android", "nowinandroid", "dump_app.zip");
  // When using with the desugar_jdk_libs.jar in third_party (DESUGARED_JDK_11_LIB_JAR) for L8
  // compilation then the configuration from the dump cannot be used for L8, as the configuration
  // in the dump is the "machine specification" which only works with the specific version it was
  // built for.
  private static final boolean useDesugaredLibraryConfigurationFromDump = false;

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  // Test with min API level 24 where default interface methods are supported instead fo using
  // dump.getBuildProperties().getMinApi(). Tivi has min API 23 and there are currently trace
  // references issues with CC classes for default interface methods.
  public static TestParametersCollection data() {
    return getTestParameters()
        .withDexRuntime(DexVm.Version.V7_0_0)
        .withApiLevel(AndroidApiLevel.N)
        .build();
  }

  private void configureDesugaredLibrary(
      TestCompilerBuilder<?, ?, ?, ?, ?> builder, CompilerDump dump) {
    if (useDesugaredLibraryConfigurationFromDump) {
      builder.enableCoreLibraryDesugaring(
          LibraryDesugaringTestConfiguration.forSpecification(dump.getDesugaredLibraryFile()));
    } else {
      builder.enableCoreLibraryDesugaring(
          LibraryDesugaringTestConfiguration.forSpecification(
              Paths.get(ToolHelper.LIBRARY_DESUGAR_SOURCE_DIR, "jdk11", "desugar_jdk_libs.json")));
    }
  }

  private void configureDesugaredLibrary(L8Command.Builder builder, CompilerDump dump) {
    if (useDesugaredLibraryConfigurationFromDump) {
      builder.addDesugaredLibraryConfiguration(
          StringResource.fromFile(dump.getDesugaredLibraryFile()));
    } else {
      builder.addDesugaredLibraryConfiguration(
          StringResource.fromFile(
              Paths.get(ToolHelper.LIBRARY_DESUGAR_SOURCE_DIR, "jdk11", "desugar_jdk_libs.json")));
    }
  }

  @Test
  @Ignore("Will be removed, only present to easily compare with partial compilation")
  public void testD8() throws Exception {
    Path tempDir = temp.newFolder().toPath();

    CompilerDump dump = CompilerDump.fromArchive(TIVI_DUMP_PATH, temp.newFolder().toPath());
    Path output = tempDir.resolve("tivid8.zip");
    testForD8(parameters.getBackend())
        .setMinApi(parameters)
        .addLibraryFiles(dump.getLibraryArchive())
        .addClasspathFiles(dump.getClasspathArchive())
        .addProgramFiles(dump.getProgramArchive())
        .setMode(CompilationMode.RELEASE)
        .apply(b -> configureDesugaredLibrary(b, dump))
        .compile()
        .writeToZip(output);

    Path l8Output = tempDir.resolve("tivid8l8.zip");
    runL8(tempDir, dump, output, l8Output);
  }

  @Test
  @Ignore("Will be removed, only present to easily compare with partial compilation")
  public void testR8() throws Exception {
    Path tempDir = temp.newFolder().toPath();

    CompilerDump dump = CompilerDump.fromArchive(TIVI_DUMP_PATH, temp.newFolder().toPath());
    Path output = tempDir.resolve("tivir8.zip");
    testForR8(parameters.getBackend())
        .setMinApi(parameters)
        .addLibraryFiles(dump.getLibraryArchive())
        .addClasspathFiles(dump.getClasspathArchive())
        .addProgramFiles(dump.getProgramArchive())
        .addKeepRuleFiles(dump.getProguardConfigFile())
        .apply(b -> configureDesugaredLibrary(b, dump))
        .setMode(CompilationMode.RELEASE)
        .addOptionsModification(
            options -> options.getOpenClosedInterfacesOptions().suppressAllOpenInterfaces())
        .allowDiagnosticMessages()
        .allowUnnecessaryDontWarnWildcards()
        .allowUnusedDontWarnPatterns()
        .allowUnusedProguardConfigurationRules()
        .compile()
        .writeToZip(output);

    Path l8Output = tempDir.resolve("tivir8l8.zip");
    runL8(tempDir, dump, output, l8Output);
  }

  private void testDump(CompilerDump dump, String appNamespace) throws Exception {
    assert !appNamespace.endsWith(".");
    Path tempDir = temp.newFolder().toPath();

    // Different sets of namespaces to shrink.
    ImmutableMap<String, Predicate<String>> splits =
        ImmutableMap.of(
            "androidx", name -> name.startsWith("androidx."),
            "androidx_kotlin_and_kotlinx",
                name ->
                    name.startsWith("androidx.")
                        || name.startsWith("kotlin.")
                        || name.startsWith("kotlinx."),
            "more_libraries",
                name ->
                    name.startsWith("androidx.")
                        || name.startsWith("kotlin.")
                        || name.startsWith("kotlinx.")
                        || name.startsWith("android.support.")
                        || name.startsWith("io.ktor.")
                        || name.startsWith("com.google.android.gms.")
                        || name.startsWith("com.google.firebase."),
            "all_but_app namespace", name -> !name.startsWith(appNamespace + "."));

    // Compile with each set of namespaces to shrink and collect DEX size.
    Map<String, Pair<Long, Long>> dexSize = new LinkedHashMap<>();
    for (Entry<String, Predicate<String>> entry : splits.entrySet()) {
      long size = runR8PartialAndL8(tempDir, dump, entry.getKey(), entry.getValue());
      dexSize.put(entry.getKey(), new Pair<>(size, 0L));
    }
    dexSize.forEach(
        (name, size) ->
            System.out.println(name + ": " + size.getFirst() + ", " + size.getSecond()));

    // Check that sizes for increased namespaces to shrink does not increase size.
    Pair<Long, Long> previousSize = null;
    for (Entry<String, Pair<Long, Long>> entry : dexSize.entrySet()) {
      if (previousSize != null) {
        assertTrue(entry.getKey(), entry.getValue().getFirst() <= previousSize.getFirst());
        assertTrue(entry.getKey(), entry.getValue().getSecond() <= previousSize.getSecond());
      }
      previousSize = entry.getValue();
    }
  }

  @Test
  @Ignore("Still experimental, do not run this test by default")
  public void testTivi() throws Exception {
    Path tempDir = temp.newFolder().toPath();
    Path dumpDir = tempDir.resolve("dump");
    testDump(CompilerDump.fromArchive(TIVI_DUMP_PATH, dumpDir), "app.tivi");
  }

  @Test
  @Ignore("Still experimental, do not run this test by default")
  public void testNowinandroid() throws Exception {
    Path tempDir = temp.newFolder().toPath();
    Path dumpDir = tempDir.resolve("dump");
    testDump(
        CompilerDump.fromArchive(NOWINANDROID_DUMP_PATH, dumpDir),
        "com.google.samples.apps.nowinandroid");
  }

  private long runR8PartialAndL8(
      Path tempDir, CompilerDump dump, String name, Predicate<String> isR8) throws Exception {
    Path tmp = tempDir.resolve(name);
    Files.createDirectory(tmp);
    Path output = tmp.resolve("tivix8.zip");
    runR8Partial(tempDir, dump, output, isR8);
    Path l8Output = tmp.resolve("tivix8l8.zip");
    runL8(tmp, dump, output, l8Output);
    Box<Long> size = new Box<>(0L);
    for (Path zipWithDex : new Path[] {output, l8Output}) {
      ZipUtils.iter(
          zipWithDex,
          (zipEntry, input) ->
              size.set(
                  size.get()
                      + (zipEntry.getName().endsWith(FileUtils.DEX_EXTENSION)
                          ? zipEntry.getSize()
                          : 0L)));
    }
    return size.get();
  }

  private void runR8Partial(Path tempDir, CompilerDump dump, Path output, Predicate<String> isR8)
      throws IOException, CompilationFailedException {
    testForR8Partial(parameters.getBackend())
        .setR8PartialConfigurationJavaTypePredicate(isR8)
        .addOptionsModification(
            options -> {
              options.partialCompilationConfiguration.setTempDir(tempDir);

              // For compiling nowonandroid.
              options.testing.allowUnnecessaryDontWarnWildcards = true;
              options.testing.allowUnusedDontWarnRules = true;
              options.getOpenClosedInterfacesOptions().suppressAllOpenInterfaces();
            })
        .setMinApi(parameters)
        .addLibraryFiles(dump.getLibraryArchive())
        .addClasspathFiles(dump.getClasspathArchive())
        .addProgramFiles(dump.getProgramArchive())
        .addKeepRuleFiles(dump.getProguardConfigFile())
        // Add this keep rules as trace references does not trace annotations. The consumer rules
        // explicitly leaves out the annotation class Navigator.Name:
        //
        // # A -keep rule for the Navigator.Name annotation class is not required
        // # since the annotation is referenced from the code.
        //
        // As this code reference is not in the D8 part of the code Navigator.Name is removed.
        .addKeepRules("-keep class androidx.navigation.Navigator$Name { *; }")
        .apply(b -> configureDesugaredLibrary(b, dump))
        .setMode(CompilationMode.RELEASE)
        .allowStdoutMessages()
        .allowStderrMessages()
        .allowUnusedProguardConfigurationRules() // nowonandroid
        .enableEmptyMemberRulesToDefaultInitRuleConversion(true) // avoid warnings
        .allowDiagnosticInfoMessages()
        .compile()
        .writeToZip(output);
  }

  private void runL8(Path tempDir, CompilerDump dump, Path appDexCode, Path output)
      throws Exception {
    Path desugaredLibraryRules = tempDir.resolve("desugared_library.rules");
    TraceReferencesKeepRules keepRulesConsumer =
        TraceReferencesKeepRules.builder()
            .setOutputConsumer(new FileConsumer(desugaredLibraryRules))
            .build();

    AndroidApiLevel apiLevel = AndroidApiLevel.N;
    Path path = tempDir.resolve("desugared_library.jar");
    Path dd =
        DesugaredLibraryJDK11Undesugarer.undesugaredJarJDK11(tempDir, DESUGARED_JDK_11_LIB_JAR);
    L8Command.Builder commandBuilder =
        L8Command.builder()
            .setMinApiLevel(apiLevel.getLevel())
            .addLibraryFiles(dump.getLibraryArchive())
            .addProgramFiles(dd)
            .setOutput(path, OutputMode.ClassFile);
    configureDesugaredLibrary(commandBuilder, dump);
    L8.run(commandBuilder.build());

    TraceReferencesCommand.Builder traceReferencesCommandBuilder =
        TraceReferencesCommand.builder()
            .addLibraryFiles(dump.getLibraryArchive())
            .addSourceFiles(appDexCode)
            .addTargetFiles(path)
            .setConsumer(keepRulesConsumer);
    TraceReferences.run(traceReferencesCommandBuilder.build());

    testForR8(Backend.DEX)
        .addLibraryFiles(dump.getLibraryArchive())
        .addProgramFiles(path)
        .addKeepRuleFiles(desugaredLibraryRules)
        .compile()
        .writeToZip(output);
  }
}
