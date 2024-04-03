// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.compilerapi.maindex;

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
import com.android.tools.r8.utils.FileUtils;
import java.nio.file.Path;
import java.util.Collections;
import org.junit.Test;

public class MainDexListTest extends CompilerApiTestRunner {

  public MainDexListTest(TestParameters parameters) {
    super(parameters);
  }

  @Override
  public Class<? extends CompilerApiTest> binaryTestClass() {
    return ApiTest.class;
  }

  static class InputClass {}

  interface Runner {
    void run(ApiTest test, Path mainDexList, DiagnosticsHandler handler) throws Exception;
  }

  private TestDiagnosticMessagesImpl runTest(Runner runner) throws Exception {
    ApiTest test = new ApiTest(ApiTest.PARAMETERS);
    Path mainDexListInput =
        FileUtils.writeTextFile(
            temp.newFile().toPath(), InputClass.class.getName().replace('.', '/') + ".class");
    TestDiagnosticMessagesImpl handler = new TestDiagnosticMessagesImpl();
    runner.run(test, mainDexListInput, handler);
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
    runTest((test, mainDexList, handler) -> test.runD8(getDexInput(), mainDexList, handler))
        .assertNoMessages();
  }

  @Test
  public void testD8CfInputs() throws Exception {
    runTest((test, mainDexList, handler) -> test.runD8(getCfInput(), mainDexList, handler))
        .assertOnlyWarnings()
        .assertAllWarningsMatch(
            DiagnosticsMatcher.diagnosticType(UnsupportedMainDexListUsageDiagnostic.class));
  }

  @Test
  public void testR8() throws Exception {
    runTest((test, mainDexList, handler) -> test.runR8(getCfInput(), mainDexList, handler))
        .assertOnlyWarnings()
        .assertAllWarningsMatch(
            DiagnosticsMatcher.diagnosticType(UnsupportedMainDexListUsageDiagnostic.class));
  }

  public static class ApiTest extends CompilerApiTest {

    public ApiTest(Object parameters) {
      super(parameters);
    }

    public void runD8(Path programInput, Path mainDexListInput, DiagnosticsHandler handler)
        throws CompilationFailedException {
      D8Command.Builder builder =
          handler == null ? D8Command.builder() : D8Command.builder(handler);
      builder
          .addLibraryFiles(getJava8RuntimeJar())
          .setProgramConsumer(DexIndexedConsumer.emptyConsumer());
      if (programInput != null) {
        builder.addProgramFiles(programInput);
      }
      if (mainDexListInput != null) {
        builder.addMainDexListFiles(mainDexListInput);
        builder.addMainDexListFiles(Collections.singletonList(mainDexListInput));
      }
      D8.run(builder.build());
    }

    public void runR8(Path programInput, Path mainDexListInput, DiagnosticsHandler handler)
        throws CompilationFailedException {
      R8Command.Builder builder =
          handler == null ? R8Command.builder() : R8Command.builder(handler);
      builder
          .addLibraryFiles(getJava8RuntimeJar())
          .setProgramConsumer(DexIndexedConsumer.emptyConsumer());
      if (programInput != null) {
        builder.addProgramFiles(programInput);
      }
      if (mainDexListInput != null) {
        builder.addMainDexListFiles(mainDexListInput);
        builder.addMainDexListFiles(Collections.singletonList(mainDexListInput));
      }
      R8.run(builder.build());
    }

    @Test
    public void testD8() throws CompilationFailedException {
      runD8(null, null, null);
    }

    @Test
    public void testR8() throws CompilationFailedException {
      runR8(null, null, null);
    }
  }
}
