// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.jdk11.nest;

import static junit.framework.TestCase.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class NestMemberPropagatedTest extends TestBase {

  private static final Class<?> MAIN_CLASS = NestPvtFieldPropagated.class;

  public NestMemberPropagatedTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withCfRuntimesStartingFromIncluding(CfVm.JDK11).build();
  }

  @Test
  public void testPvtMemberPropagated() throws Exception {
    testForR8(parameters.getBackend())
        .addKeepMainRule(MAIN_CLASS)
        .addDontObfuscate()
        .addOptionsModification(options -> options.enableClassInlining = false)
        .addProgramClassesAndInnerClasses(MAIN_CLASS)
        .compile()
        .inspect(this::assertMemberPropagated)
        .run(parameters.getRuntime(), MAIN_CLASS)
        .assertSuccessWithOutputLines("toPropagateStatic");
  }

  private void assertMemberPropagated(CodeInspector inspector) {
    for (FoundClassSubject subj : inspector.allClasses()) {
      assertEquals(0, subj.allFields().size());
    }
  }
}
