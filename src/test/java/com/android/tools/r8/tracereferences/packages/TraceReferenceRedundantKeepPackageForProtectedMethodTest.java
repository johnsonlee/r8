// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.tracereferences.packages;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDiagnosticMessagesImpl;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.references.PackageReference;
import com.android.tools.r8.tracereferences.TraceReferences;
import com.android.tools.r8.tracereferences.TraceReferencesCommand;
import com.android.tools.r8.tracereferences.TraceReferencesConsumer;
import com.android.tools.r8.tracereferences.packages.testclasses.TraceReferenceRedundantKeepPackageForProtectedMethodTestClasses;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.ZipUtils.ZipBuilder;
import com.android.tools.r8.utils.codeinspector.AssertUtils;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class TraceReferenceRedundantKeepPackageForProtectedMethodTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  static class TraceReferencesInspector implements TraceReferencesConsumer {

    @Override
    public void acceptPackage(PackageReference pkg, DiagnosticsHandler handler) {
      fail("Unexpected package");
    }

    @Override
    public void acceptField(TracedField tracedField, DiagnosticsHandler handler) {}

    @Override
    public void acceptMethod(TracedMethod tracedMethod, DiagnosticsHandler handler) {}

    @Override
    public void acceptType(TracedClass tracedClass, DiagnosticsHandler handler) {}
  }

  @Test
  public void test() throws Throwable {
    AssertUtils.assertFailsCompilation(
        () ->
            runTest(
                testForD8(Backend.DEX)
                    .addProgramClasses(Main.class)
                    .release()
                    .setMinApi(AndroidApiLevel.B)
                    .compile()
                    .writeToZip()),
        exception -> assertEquals("Unexpected package", exception.getCause().getMessage()));
  }

  private TraceReferencesInspector runTest(Path sourceFile) throws Exception {
    Path dir = temp.newFolder().toPath();
    Path targetJar =
        ZipBuilder.builder(dir.resolve("target.jar"))
            .addFilesRelative(
                ToolHelper.getClassPathForTests(),
                ToolHelper.getClassFilesForInnerClasses(
                    TraceReferenceRedundantKeepPackageForProtectedMethodTestClasses.class))
            .build();
    TestDiagnosticMessagesImpl diagnostics = new TestDiagnosticMessagesImpl();
    TraceReferencesInspector consumer = new TraceReferencesInspector();
    TraceReferences.run(
        TraceReferencesCommand.builder(diagnostics)
            .addLibraryFiles(ToolHelper.getMostRecentAndroidJar())
            .addSourceFiles(sourceFile)
            .addTargetFiles(targetJar)
            .setConsumer(consumer)
            .build());
    return consumer;
  }

  public static class Main
      extends TraceReferenceRedundantKeepPackageForProtectedMethodTestClasses.A {

    public static void main(String[] args) {
      TraceReferenceRedundantKeepPackageForProtectedMethodTestClasses.A.m();
    }
  }
}
