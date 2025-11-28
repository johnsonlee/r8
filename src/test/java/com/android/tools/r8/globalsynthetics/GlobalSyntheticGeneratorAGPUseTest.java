// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.globalsynthetics;

import static com.android.tools.r8.ToolHelper.getAndroidJar;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNull;

import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.GlobalSyntheticsGenerator;
import com.android.tools.r8.GlobalSyntheticsGeneratorCommand;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class GlobalSyntheticGeneratorAGPUseTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public boolean emitLambdaMethodAnnotations;

  @Parameters(name = "{0}, emitLambdaMethodAnnotations = {1}")
  public static List<Object[]> data() {
    return buildParameters(getTestParameters().withNoneRuntime().build(), BooleanUtils.values());
  }

  @Test
  public void test() throws Exception {
    try {
      if (emitLambdaMethodAnnotations) {
        assertNull(System.getProperty("com.android.tools.r8.emitLambdaMethodAnnotations"));
        System.setProperty("com.android.tools.r8.emitLambdaMethodAnnotations", "");
      }
      Path globals = temp.newFile("all.globals").toPath();
      GlobalSyntheticsGenerator.run(
          GlobalSyntheticsGeneratorCommand.builder()
              .addLibraryFiles(getAndroidJar(36))
              .setGlobalSyntheticsOutput(globals)
              .build());

      Path globalsDex = temp.newFile("globals.zip").toPath();
      D8.run(
          D8Command.builder()
              .addLibraryFiles(getAndroidJar(36))
              .setMinApiLevel(21)
              .addGlobalSyntheticsFiles(globals)
              .setOutput(globalsDex, OutputMode.DexIndexed)
              .build());

      CodeInspector inspector = new CodeInspector(globalsDex);
      assertThat(inspector.clazz("java.lang.Record"), isPresent());
      // Added in API level 24.
      assertThat(inspector.clazz("android.os.HardwarePropertiesManager"), isPresent());
      // Added in API level 36.
      assertThat(inspector.clazz("android.os.Build$VERSION_CODES_FULL"), isPresent());
      // Class com.android.tools.r8.annotations.LambdaMethod is always generated.
      assertThat(inspector.clazz("com.android.tools.r8.annotations.LambdaMethod"), isPresent());
    } finally {
      System.clearProperty("com.android.tools.r8.emitLambdaMethodAnnotations");
    }
  }
}
