// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dump;

import static org.junit.Assert.fail;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.DumpInputFlags;
import com.android.tools.r8.utils.StringUtils;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class CompilerDumpTest extends TestBase {

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

  static class TestClass {

    public static void main(String[] args) {
      System.out.println("Hello, world!");
    }
  }
}
