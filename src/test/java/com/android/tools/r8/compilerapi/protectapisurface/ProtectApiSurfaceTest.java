// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.compilerapi.protectapisurface;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPrivate;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPublic;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.R8;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.compilerapi.CompilerApiTest;
import com.android.tools.r8.compilerapi.CompilerApiTestRunner;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.OptionalBool;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.nio.file.Path;
import java.util.Collections;
import org.junit.Test;

public class ProtectApiSurfaceTest extends CompilerApiTestRunner {

  private static final int SOME_API_LEVEL = 24;

  public ProtectApiSurfaceTest(TestParameters parameters) {
    super(parameters);
  }

  @Override
  public Class<? extends CompilerApiTest> binaryTestClass() {
    return ApiTest.class;
  }

  @Test
  public void testDefault() throws Exception {
    runTest(OptionalBool.UNKNOWN);
  }

  @Test
  public void testEnabled() throws Exception {
    runTest(OptionalBool.TRUE);
  }

  @Test
  public void testDisabled() throws Exception {
    runTest(OptionalBool.FALSE);
  }

  private void runTest(OptionalBool protectApiSurface) throws Exception {
    Path out = temp.newFolder().toPath().resolve("out.jar");
    ApiTest test = new ApiTest(ApiTest.PARAMETERS);
    test.runR8(new DexIndexedConsumer.ArchiveConsumer(out), protectApiSurface);
    inspect(new CodeInspector(out), protectApiSurface);
  }

  private void inspect(CodeInspector inspector, OptionalBool protectApiSurface) {
    MethodSubject method =
        inspector.allClasses().iterator().next().uniqueMethodWithFinalName("greet");
    assertThat(method, isPresent());
    assertThat(method, protectApiSurface.isTrue() ? isPrivate() : isPublic());
  }

  public static class ApiTest extends CompilerApiTest {

    public ApiTest(Object parameters) {
      super(parameters);
    }

    public void runR8(DexIndexedConsumer programConsumer, OptionalBool protectApiSurface)
        throws Exception {
      R8Command.Builder commandBuilder =
          R8Command.builder()
              .addClassProgramData(
                  getBytesForClass(getMockClassWithPrivateMethod()), Origin.unknown())
              .addProguardConfiguration(
                  Collections.singletonList("-keep,allowaccessmodification class * { *; }"),
                  Origin.unknown())
              .addLibraryFiles(getAndroidJar())
              .setMinApiLevel(SOME_API_LEVEL)
              .setProgramConsumer(programConsumer);
      if (!protectApiSurface.isUnknown()) {
        commandBuilder.setProtectApiSurface(protectApiSurface.isTrue());
      }
      R8.run(commandBuilder.build());
    }

    @Test
    public void testEnabled() throws Exception {
      runR8(DexIndexedConsumer.emptyConsumer(), OptionalBool.TRUE);
    }
  }
}
