// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.utils.AndroidApiLevel;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class StepOutOfMethodWithUnusedReturnValueTestRunner extends DebugTestBase {

  private static final Class<?> CLASS = StepOutOfMethodWithUnusedReturnValueTest.class;
  private static final String FILE = CLASS.getSimpleName() + ".java";
  private static final String NAME = CLASS.getCanonicalName();

  @Parameter public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return TestParameters.builder()
        .withDefaultCfRuntime()
        .withDexRuntimesStartingFromIncluding(Version.V5_1_1)
        .withApiLevel(AndroidApiLevel.B)
        .enableApiLevelsForCf()
        .build();
  }

  @Test
  public void testHitOnEntryOnly() throws Throwable {
    DebugTestConfig config =
        testForD8(parameters.getBackend())
            .setMinApi(parameters)
            .addProgramClasses(CLASS)
            .addOptionsModification(o -> o.ensureJvmCompatibleStepOutBehavior = true)
            .debugConfig(parameters.getRuntime());

    runDebugTest(
        config,
        CLASS,
        breakpoint(NAME, "main"),
        run(),
        checkLine(FILE, 18),
        stepInto(),
        checkLine(FILE, 10),
        stepOut(),
        checkLine(FILE, 18),
        stepOver(),
        checkLine(FILE, 19),
        stepInto(),
        checkLine(FILE, 14),
        stepOut(),
        // Notice that stepping out of the void method will go to next line.
        checkLine(FILE, 20),
        stepInto(),
        checkLine(FILE, 10),
        stepOut(),
        // Notice also that we step to the same line even when the continuation is a 'return' that
        // would not need to pop the value.
        checkLine(FILE, 20),
        stepInto(),
        checkLine(FILE, 21),
        run());
  }
}
