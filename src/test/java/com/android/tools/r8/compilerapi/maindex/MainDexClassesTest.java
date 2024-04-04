// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.compilerapi.maindex;

import static org.junit.Assert.fail;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.DiagnosticsMatcher;
import com.android.tools.r8.R8;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestDiagnosticMessagesImpl;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.compilerapi.CompilerApiTest;
import com.android.tools.r8.compilerapi.CompilerApiTestRunner;
import com.android.tools.r8.errors.UnsupportedMainDexListUsageDiagnostic;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.Test;

public class MainDexClassesTest extends CompilerApiTestRunner {

  public MainDexClassesTest(TestParameters parameters) {
    super(parameters);
  }

  @Override
  public Class<? extends CompilerApiTest> binaryTestClass() {
    return ApiTest.class;
  }

  static class InputClass {}

  interface Runner {
    void run(ApiTest test, String[] mainDexClasses, DiagnosticsHandler handler) throws Exception;
  }

  TestDiagnosticMessagesImpl handler;

  private TestDiagnosticMessagesImpl runTest(Runner runner) throws Exception {
    ApiTest test = new ApiTest(ApiTest.PARAMETERS);
    String[] mainDexClasses = new String[] {InputClass.class.getName()};
    handler = new TestDiagnosticMessagesImpl();
    runner.run(test, mainDexClasses, handler);
    return handler;
  }

  private Path getDexInput() throws Exception {
    return testForD8().addProgramClasses(InputClass.class).compile().writeToZip();
  }

  private static Path getCfInput() {
    return ToolHelper.getClassFileForTestClass(InputClass.class);
  }

  @Test
  public void testD8DexInputs() throws Exception {
    runTest((test, mainDexClasses, handler) -> test.runD8(getDexInput(), mainDexClasses, handler))
        .assertNoMessages();
  }

  @Test
  public void testD8CfInputs() throws Exception {
    try {
      runTest((test, mainDexClasses, handler) -> test.runD8(getCfInput(), mainDexClasses, handler));
    } catch (CompilationFailedException e) {
      handler
          .assertOnlyErrors()
          .assertAllErrorsMatch(
              DiagnosticsMatcher.diagnosticType(UnsupportedMainDexListUsageDiagnostic.class));
      return;
    }
    fail("Expected compilation failure");
  }

  @Test
  public void testR8() throws Exception {
    try {
      runTest((test, mainDexClasses, handler) -> test.runR8(getCfInput(), mainDexClasses, handler));
    } catch (CompilationFailedException e) {
      handler
          .assertOnlyErrors()
          .assertAllErrorsMatch(
              DiagnosticsMatcher.diagnosticType(UnsupportedMainDexListUsageDiagnostic.class));
      return;
    }
    fail("Expected compilation failure");
  }

  public static class ApiTest extends CompilerApiTest {

    public ApiTest(Object parameters) {
      super(parameters);
    }

    public void runD8(Path programInput, String[] mainDexClassesInput, DiagnosticsHandler handler)
        throws CompilationFailedException {
      D8Command.Builder builder =
          handler == null ? D8Command.builder() : D8Command.builder(handler);
      builder
          .addLibraryFiles(getJava8RuntimeJar())
          .setProgramConsumer(DexIndexedConsumer.emptyConsumer());
      if (programInput != null) {
        builder.addProgramFiles(programInput);
      }
      if (mainDexClassesInput != null) {
        builder.addMainDexClasses(mainDexClassesInput);
        builder.addMainDexClasses(Arrays.asList(mainDexClassesInput));
      }
      D8.run(builder.build());
    }

    public void runR8(Path programInput, String[] mainDexClassesInput, DiagnosticsHandler handler)
        throws CompilationFailedException {
      R8Command.Builder builder =
          handler == null ? R8Command.builder() : R8Command.builder(handler);
      builder
          .addLibraryFiles(getJava8RuntimeJar())
          .setProgramConsumer(DexIndexedConsumer.emptyConsumer());
      if (programInput != null) {
        builder.addProgramFiles(programInput);
      }
      builder.addMainDexClasses(mainDexClassesInput);
      builder.addMainDexClasses(Arrays.asList(mainDexClassesInput));
      R8.run(builder.build());
    }

    @Test
    public void testD8() throws CompilationFailedException {
      runD8(null, new String[0], null);
    }

    @Test
    public void testR8() throws CompilationFailedException {
      runR8(null, new String[0], null);
    }
  }
}
