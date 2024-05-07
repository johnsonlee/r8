// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RegressB339064674TestRunner extends DebugTestBase {

  static final Class CLASS = RegressB339064674Test.class;
  static final String NAME = CLASS.getCanonicalName();
  static final String FILE = CLASS.getSimpleName() + ".java";
  static final AndroidApiLevel minApi = AndroidApiLevel.B;
  static final String EXPECTED = "";

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection setup() {
    return TestParameters.builder().withAllRuntimes().build();
  }

  public RegressB339064674TestRunner(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testReference() throws Throwable {
    assumeTrue(parameters.isCfRuntime());
    testForJvm(parameters)
        .addProgramClasses(CLASS)
        .run(parameters.getRuntime(), CLASS)
        .assertSuccessWithOutput(EXPECTED)
        .debugger(this::runDebugger);
  }

  @Test
  public void testD8() throws Throwable {
    testForD8(parameters.getBackend())
        .setMinApi(minApi)
        .addProgramClasses(CLASS)
        .run(parameters.getRuntime(), CLASS)
        .assertSuccessWithOutput(EXPECTED)
        .debugger(this::runDebugger);
  }

  @Test
  public void testR8() throws Throwable {
    testForR8(parameters.getBackend())
        .debug()
        .addKeepMainRule(CLASS)
        .addProgramClasses(CLASS)
        .setMinApi(minApi)
        .run(parameters.getRuntime(), CLASS)
        .assertSuccessWithOutput(EXPECTED)
        .debugger(this::runDebugger);
  }

  public void runDebugger(DebugTestConfig config) throws Throwable {
    // The test ensures that when breaking inside a function and changing a local in the parent
    // frame, that the new value is passed to the second invocation of the function.
    // This ensures that no peephole optimizations will optimize if there is any debug information.
    runDebugTest(
        config,
        NAME,
        breakpoint(NAME, "main"),
        run(),
        checkLine(FILE, 11),
        stepOver(),
        checkLine(FILE, 12),
        stepOver(),
        checkLine(FILE, 13),
        stepOver(),
        checkLine(FILE, 14),
        stepOver(),
        checkLine(FILE, 11),
        stepOver(),
        checkLine(FILE, 12),
        stepOver(),
        checkLine(FILE, 13),
        stepOver(),
        checkLine(FILE, 16),
        checkLocals("args", "i", "a1"),
        checkNoLocal("a2"),
        stepOver(),
        checkLine(FILE, 17),
        checkLocals("args"),
        checkNoLocal("i"),
        stepOver(),
        checkLine(FILE, 11),
        stepOver(),
        checkLine(FILE, 18),
        stepOver(),
        checkLine(FILE, 19),
        run());
  }
}
