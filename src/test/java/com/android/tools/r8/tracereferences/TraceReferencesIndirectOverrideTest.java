// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.tracereferences;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.desugar.desugaredlibrary.test.TestingKeepRuleConsumer;
import com.android.tools.r8.utils.NopDiagnosticsHandler;
import com.android.tools.r8.utils.ZipUtils.ZipBuilder;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class TraceReferencesIndirectOverrideTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public TraceReferencesIndirectOverrideTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  @Test
  public void test() throws Throwable {
    Path targetJar =
        ZipBuilder.builder(temp.newFile("target.jar").toPath())
            .addFilesRelative(
                ToolHelper.getClassPathForTests(),
                ToolHelper.getClassFileForTestClass(A.class),
                ToolHelper.getClassFileForTestClass(B.class))
            .build();
    Path sourceJar =
        ZipBuilder.builder(temp.newFile("source.jar").toPath())
            .addFilesRelative(
                ToolHelper.getClassPathForTests(), ToolHelper.getClassFileForTestClass(C.class))
            .build();
    TestingKeepRuleConsumer testingKeepRuleConsumer = new TestingKeepRuleConsumer();
    TraceReferences.run(
        TraceReferencesCommand.builder(new NopDiagnosticsHandler())
            .addLibraryFiles(ToolHelper.getMostRecentAndroidJar())
            .addSourceFiles(sourceJar)
            .addTargetFiles(targetJar)
            .setConsumer(
                TraceReferencesKeepRules.builder()
                    .setOutputConsumer(testingKeepRuleConsumer)
                    .build())
            .build());
    assertThat(testingKeepRuleConsumer.get(), containsString("shouldBeKept"));
  }

  static class A {

    public void shouldBeKept() {}
  }

  static class B extends A {}

  static class C extends B {

    @Override
    public void shouldBeKept() {}
  }
}
