// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dump;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.R8PartialTestCompileResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.internal.CompilationTestBase;
import com.android.tools.r8.utils.DumpInputFlags;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.Lists;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class CompilerDumpTest extends CompilationTestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private static final String EXPECTED_OUTPUT = StringUtils.lines("Hello, world!");

  @Test
  public void testD8() throws Exception {
    // Create a D8 dump.
    parameters.assumeDexRuntime();
    Path dumpFile = temp.newFile("dump.zip").toPath();
    try {
      testForD8(parameters.getBackend())
          .addInnerClasses(getClass())
          .setMinApi(parameters)
          .addOptionsModification(
              options -> options.setDumpInputFlags(DumpInputFlags.dumpToFile(dumpFile)))
          .compile();
      fail("Expected to fail compilation when creating a duump");
    } catch (CompilationFailedException ignored) {
    }

    // Compile the dump.
    CompilerDump dump = CompilerDump.fromArchive(dumpFile, temp.newFolder().toPath());
    testForD8(parameters.getBackend())
        .applyCompilerDump(dump)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testR8() throws Exception {
    // Create an R8 dump.
    Path dumpFile = temp.newFile("dump.zip").toPath();
    try {
      testForR8(parameters.getBackend())
          .addInnerClasses(getClass())
          .addKeepMainRule(TestClass.class)
          .setMinApi(parameters)
          .addOptionsModification(
              options -> options.setDumpInputFlags(DumpInputFlags.dumpToFile(dumpFile)))
          .compile();
      fail("Expected to fail compilation when creating a duump");
    } catch (CompilationFailedException ignored) {
    }

    // Compile the dump.
    CompilerDump dump = CompilerDump.fromArchive(dumpFile, temp.newFolder().toPath());
    testForR8(parameters.getBackend())
        .applyCompilerDump(dump)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testR8Partial() throws Exception {
    parameters.assumeCanUseR8Partial();
    // Create an R8 partial dump.
    Path dumpDirectory = temp.newFolder().toPath();
    DumpInputFlags dumpInputFlags = DumpInputFlags.dumpToDirectory(dumpDirectory);
    R8PartialTestCompileResult compileResult =
        testForR8Partial(parameters.getBackend())
            .addR8IncludedClasses(IncludedMain.class)
            .addR8ExcludedClasses(ExcludedMain.class)
            .addKeepMainRule(IncludedMain.class)
            .allowDiagnosticInfoMessages()
            .setMinApi(parameters)
            .addR8PartialOptionsModification(options -> options.setDumpInputFlags(dumpInputFlags))
            .addR8PartialD8OptionsModification(options -> options.setDumpInputFlags(dumpInputFlags))
            .addR8PartialR8OptionsModification(options -> options.setDumpInputFlags(dumpInputFlags))
            .compileWithExpectedDiagnostics(
                diagnostics ->
                    diagnostics
                        .assertInfosMatch(
                            diagnosticMessage(startsWith("Dumped compilation inputs to:")))
                        .assertNoWarnings()
                        .assertNoErrors());

    // Verify that only one archive was dumped.
    List<Path> dumpArchives = Files.list(dumpDirectory).collect(Collectors.toList());
    assertEquals(1, dumpArchives.size());

    Path dumpArchive = dumpArchives.iterator().next();

    // Inspect the dump.
    CompilerDump dump = CompilerDump.fromArchive(dumpArchive, temp.newFolder().toPath());
    assertEquals(
        Lists.newArrayList(IncludedMain.class.getTypeName()), dump.getR8PartialIncludePatterns());
    assertEquals(
        Lists.newArrayList(ExcludedMain.class.getTypeName()),
        dump.getR8PartialExcludePatternsOrDefault(null));

    // Compile the dump.
    testForR8Partial(parameters.getBackend())
        .applyCompilerDump(dump)
        .compile()
        .apply(
            recompileResult ->
                assertIdenticalApplications(compileResult.getApp(), recompileResult.getApp()))
        .run(parameters.getRuntime(), IncludedMain.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println("Hello, world!");
    }
  }

  static class IncludedMain {

    public static void main(String[] args) {
      System.out.println("Hello, world!");
    }
  }

  static class ExcludedMain {

    public static void main(String[] args) {
      System.out.println("Hello, world!");
    }
  }
}
