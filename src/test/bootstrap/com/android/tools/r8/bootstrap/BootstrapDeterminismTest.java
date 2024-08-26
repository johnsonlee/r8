// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.bootstrap;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.ClassFileConsumer;
import com.android.tools.r8.DexIndexedConsumer.ArchiveConsumer;
import com.android.tools.r8.JdkClassFileProvider;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.DeterminismChecker;
import com.google.common.collect.ImmutableList;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class BootstrapDeterminismTest extends TestBase {

  private static final int ITERATIONS = 2;
  private static final List<Path> KEEP_RULES_FILES =
      ImmutableList.of(
          Paths.get(ToolHelper.getProjectRoot(), "src", "main", "keep.txt"),
          Paths.get(ToolHelper.getProjectRoot(), "src", "main", "discard.txt"));

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

  @Test
  public void testD8DeterminismWithChecksums() throws Exception {
    Path logDirectory = temp.newFolder().toPath();
    Path ref = compileD8WithChecksums(1, logDirectory);
    for (int i = 2; i <= ITERATIONS; i++) {
      Path next = compileD8WithChecksums(i, logDirectory);
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
        .addKeepRuleFiles(KEEP_RULES_FILES)
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

  private Path compileD8WithChecksums(int iteration, Path logDirectory) throws Exception {
    System.out.println("= compiling d8 " + iteration + "/" + ITERATIONS + " =====================");
    Path out = temp.newFolder().toPath().resolve("out.jar");
    testForD8(Backend.DEX)
        .addProgramFiles(ToolHelper.getR8WithRelocatedDeps())
        .addLibraryProvider(JdkClassFileProvider.fromSystemJdk())
        .addOptionsModification(
            options -> {
              options
                  .getTestingOptions()
                  .setDeterminismChecker(DeterminismChecker.createWithFileBacking(logDirectory));
              // Ensure that we generate the same check sums, see b/359616078
              options.encodeChecksums = true;
            })
        .allowStdoutMessages()
        .allowStderrMessages()
        .setProgramConsumer(new ArchiveConsumer(out))
        .compile();
    return out;
  }
}
