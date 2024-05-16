// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.bootstrap;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.ClassFileConsumer;
import com.android.tools.r8.JdkClassFileProvider;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.DeterminismChecker;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class BootstrapDeterminismTest extends TestBase {

  private static final int ITERATIONS = 2;
  private static final Path MAIN_KEEP =
      Paths.get(ToolHelper.getProjectRoot(), "src", "main", "keep.txt");

  @Parameter public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultCfRuntime().build();
  }

  @Test
  public void test() throws Exception {
    Path logDirectory = temp.newFolder().toPath();
    Path ref = compile(1, logDirectory);
    for (int i = 2; i <= ITERATIONS; i++) {
      Path next = compile(i, logDirectory);
      assertProgramsEqual(ref, next);
    }
    // Check that setting the determinism checker wrote a log file.
    assertTrue(Files.exists(logDirectory.resolve("0.log")));
  }

  private Path compile(int iteration, Path logDirectory) throws Exception {
    System.out.println("= compiling " + iteration + "/" + ITERATIONS + " ======================");
    Path out = temp.newFolder().toPath().resolve("out.jar");
    testForR8(parameters.getBackend())
        .addProgramFiles(ToolHelper.getR8WithRelocatedDeps())
        .addLibraryProvider(JdkClassFileProvider.fromSystemJdk())
        .addKeepRuleFiles(MAIN_KEEP)
        .addOptionsModification(
            options ->
                options
                    .getTestingOptions()
                    .setDeterminismChecker(DeterminismChecker.createWithFileBacking(logDirectory)))
        .allowDiagnosticMessages()
        .allowStdoutMessages()
        .allowStderrMessages()
        .allowUnusedDontWarnPatterns()
        .setProgramConsumer(new ClassFileConsumer.ArchiveConsumer(out))
        .compile();
    return out;
  }
}
