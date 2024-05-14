// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.determinism;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.dump.CompilerDump;
import com.android.tools.r8.dump.ProguardConfigSanitizer;
import com.android.tools.r8.utils.DeterminismChecker;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public abstract class DumpDeterminismTestBase extends TestBase {

  private static final int ITERATIONS = 2;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public DumpDeterminismTestBase(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  @Test
  public void test() throws Exception {
    CompilerDump dump = CompilerDump.fromArchive(getDumpFile(), temp.newFolder().toPath());
    Path logDirectory = temp.newFolder().toPath();
    Path ref = compile(1, dump, logDirectory);
    for (int i = 2; i <= ITERATIONS; i++) {
      Path next = compile(i, dump, logDirectory);
      assertProgramsEqual(ref, next);
    }
    // Check that setting the determinism checker wrote a log file.
    assertTrue(Files.exists(logDirectory.resolve("0.log")));
  }

  private Path compile(int iteration, CompilerDump dump, Path logDirectory) throws Exception {
    System.out.println("= compiling " + iteration + "/" + ITERATIONS + " ======================");
    return testForR8(Backend.DEX)
        .allowStdoutMessages()
        .allowStderrMessages()
        .addProgramFiles(dump.getProgramArchive())
        .addLibraryFiles(dump.getLibraryArchive())
        .apply(
            b ->
                dump.sanitizeProguardConfig(
                    ProguardConfigSanitizer.createDefaultForward(b::addKeepRules)
                        .onPrintDirective(
                            directive -> System.out.println("Stripping directive: " + directive))))
        .allowUnusedDontWarnPatterns()
        .allowUnusedProguardConfigurationRules()
        .allowUnnecessaryDontWarnWildcards()
        .allowDiagnosticMessages()
        // TODO(b/222228826): Disallow open interfaces.
        .addOptionsModification(
            options -> options.getOpenClosedInterfacesOptions().suppressAllOpenInterfaces())
        .addOptionsModification(
            options ->
                options.testing.setDeterminismChecker(
                    DeterminismChecker.createWithFileBacking(logDirectory)))
        .compile()
        .writeToZip();
  }

  abstract Path getDumpFile();
}
