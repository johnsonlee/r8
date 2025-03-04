// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.backports;

import static com.android.tools.r8.desugar.AutoCloseableAndroidLibraryFileData.compileAutoCloseableAndroidLibraryClasses;
import static com.android.tools.r8.desugar.AutoCloseableAndroidLibraryFileData.getAutoCloseableAndroidClassData;
import static org.hamcrest.CoreMatchers.containsString;

import com.android.tools.r8.D8TestCompileResult;
import com.android.tools.r8.TestBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.desugar.AutoCloseableAndroidLibraryFileData.DrmManagerClient;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.DescriptorUtils;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DrmManagerClientBackportTest extends AbstractBackportTest {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withCfRuntimesStartingFromIncluding(CfVm.JDK11)
        .withDexRuntimes()
        .withAllApiLevelsAlsoForCf()
        .build();
  }

  public DrmManagerClientBackportTest(TestParameters parameters) throws IOException {
    super(
        parameters,
        DescriptorUtils.descriptorToJavaType(
            DexItemFactory.androidDrmDrmManagerClientDescriptorString),
        ImmutableList.of(DrmManagerClientBackportTest.getTestRunner()));

    // The constructor is used by the test and release has been available since API 5 and is the
    // method close is rewritten to.
    ignoreInvokes("<init>");
    ignoreInvokes("release");

    // android.drm.DrmManagerClient.close added in API 24.
    registerTarget(AndroidApiLevel.N, 1);
  }

  @Override
  protected void configureProgram(TestBuilder<?, ?> builder) throws Exception {
    super.configureProgram(builder);
    if (builder.isJvmTestBuilder()) {
      builder.addProgramClassFileData(getAutoCloseableAndroidClassData(parameters));
    } else {
      builder
          .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.BAKLAVA))
          .addLibraryClassFileData(getAutoCloseableAndroidClassData(parameters));
    }
  }

  @Override
  protected void configure(D8TestCompileResult builder) throws Exception {
    if (parameters.isDexRuntime()) {
      builder.addBootClasspathFiles(compileAutoCloseableAndroidLibraryClasses(this, parameters));
    } else {
      builder.addRunClasspathClassFileData(getAutoCloseableAndroidClassData(parameters));
    }
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .apply(this::configureProgram)
        .run(parameters.getRuntime(), getTestClassName())
        // Fails when not desugared.
        .assertFailureWithErrorThatMatches(containsString("close should not be called"));
  }

  private static byte[] getTestRunner() throws IOException {
    return transformer(TestRunner.class)
        .replaceClassDescriptorInMethodInstructions(
            descriptor(DrmManagerClient.class),
            DexItemFactory.androidDrmDrmManagerClientDescriptorString)
        .transform();
  }

  public static class TestRunner extends MiniAssert {

    public static void main(String[] args) {
      DrmManagerClient drmManagerClient = new DrmManagerClient();
      MiniAssert.assertFalse(drmManagerClient.wasClosed);
      drmManagerClient.close();
      MiniAssert.assertTrue(drmManagerClient.wasClosed);
    }

    // Forwards to MiniAssert to avoid having to make it public.
    public static void doFail(String message) {
      MiniAssert.fail(message);
    }
  }
}
