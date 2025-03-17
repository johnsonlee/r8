// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.ByteDataView;
import com.android.tools.r8.ClassFileConsumer.ArchiveConsumer;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.StringUtils;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InstantiatedLambdasTestRunner extends TestBase {

  static final Class<?> CLASS = InstantiatedLambdasTest.class;
  static final Class<?>[] CLASSES = InstantiatedLambdasTest.CLASSES;
  static final String EXPECTED = StringUtils.lines("Class implementation", "Lambda implementation");

  private final TestParameters parameters;

  private Path inputJar;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public InstantiatedLambdasTestRunner(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Before
  public void writeAndRunInputJar() throws IOException {
    inputJar = temp.getRoot().toPath().resolve("input.jar");
    ArchiveConsumer buildInput = new ArchiveConsumer(inputJar);
    for (Class<?> clazz : CLASSES) {
      buildInput.accept(
          ByteDataView.of(ToolHelper.getClassAsBytes(clazz)),
          DescriptorUtils.javaTypeToDescriptor(clazz.getName()),
          null);
    }
    buildInput.finished(null);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramFiles(inputJar)
        .addKeepMainRule(CLASS)
        .debug()
        .setMinApi(parameters)
        .compile()
        .apply(
            compileResult ->
                compileResult.run(parameters.getRuntime(), CLASS).assertSuccessWithOutput(EXPECTED))
        .applyIf(
            parameters.isDexRuntime(),
            compileResult ->
                compileResult.runDex2Oat(parameters.getRuntime()).assertNoVerificationErrors());
  }
}
