// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.compilerapi.maindex;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.R8;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestDiagnosticMessagesImpl;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.compilerapi.CompilerApiTest;
import com.android.tools.r8.compilerapi.CompilerApiTestRunner;
import com.android.tools.r8.origin.PathOrigin;
import com.android.tools.r8.utils.FileUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class MainDexRulesTest extends CompilerApiTestRunner {

  public MainDexRulesTest(TestParameters parameters) {
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
    Path mainDexRulesFile = FileUtils.writeTextFile(temp.newFile().toPath(), "# empty file");
    TestDiagnosticMessagesImpl handler = new TestDiagnosticMessagesImpl();
    runner.run(test, mainDexRulesFile, handler);
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
        .assertNoMessages();
  }

  @Test
  public void testR8() throws Exception {
    runTest((test, mainDexList, handler) -> test.runR8(getCfInput(), mainDexList, handler))
        .assertNoMessages();
  }

  public static class ApiTest extends CompilerApiTest {

    public ApiTest(Object parameters) {
      super(parameters);
    }

    public void runD8(Path programInput, Path mainDexRulesFile, DiagnosticsHandler handler)
        throws Exception {
      D8Command.Builder builder =
          handler == null ? D8Command.builder() : D8Command.builder(handler);
      builder
          .addLibraryFiles(getJava8RuntimeJar())
          .setProgramConsumer(DexIndexedConsumer.emptyConsumer());
      if (programInput != null) {
        builder.addProgramFiles(programInput);
      }
      if (mainDexRulesFile != null) {
        List<String> rules = Files.readAllLines(mainDexRulesFile);
        builder.addMainDexRules(rules, new PathOrigin(mainDexRulesFile));
        builder.addMainDexRulesFiles(mainDexRulesFile);
        builder.addMainDexRulesFiles(Collections.singletonList(mainDexRulesFile));
      }
      D8.run(builder.build());
    }

    public void runR8(Path programInput, Path mainDexRulesFile, DiagnosticsHandler handler)
        throws CompilationFailedException {
      R8Command.Builder builder =
          handler == null ? R8Command.builder() : R8Command.builder(handler);
      builder
          .addLibraryFiles(getJava8RuntimeJar())
          .setProgramConsumer(DexIndexedConsumer.emptyConsumer());
      if (programInput != null) {
        builder.addProgramFiles(programInput);
      }
      if (mainDexRulesFile != null) {
        List<String> rules;
        try {
          rules = Files.readAllLines(mainDexRulesFile);
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
        builder.addMainDexRules(rules, new PathOrigin(mainDexRulesFile));
        builder.addMainDexRulesFiles(mainDexRulesFile);
        builder.addMainDexRulesFiles(Collections.singletonList(mainDexRulesFile));
      }
      R8.run(builder.build());
    }

    @Test
    public void testD8() throws Exception {
      runD8(null, null, null);
    }

    @Test
    public void testR8() throws Exception {
      runR8(null, null, null);
    }
  }
}
