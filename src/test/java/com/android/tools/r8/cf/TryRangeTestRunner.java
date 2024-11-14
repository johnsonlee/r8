// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfLabel;
import com.android.tools.r8.cf.code.CfLoad;
import com.android.tools.r8.cf.code.CfPosition;
import com.android.tools.r8.cf.code.CfStackInstruction;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/**
 * This tests that we produce valid code when having normal-flow with exceptional edges in blocks.
 * We might perform optimizations that add operations (dup, swap, etc.) before and after
 * instructions that lie on the boundary of the exception table that is generated for a basic block.
 * If live-ranges are minimized this could produce VerifyErrors.
 */
@RunWith(Parameterized.class)
public class TryRangeTestRunner extends TestBase {

  @Parameters
  public static TestParametersCollection data() {
    return TestParameters.builder().withDefaultCfRuntime().build();
  }

  @Parameter public TestParameters parameters;

  @Test
  public void testRegisterAllocationLimitTrailingRange() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(TryRangeTest.class)
        .addKeepMainRule(TryRangeTest.class)
        .setMode(CompilationMode.RELEASE)
        .addDontObfuscate()
        .addDontShrink()
        .enableInliningAnnotations()
        .addOptionsModification(o -> o.enableLoadStoreOptimization = false)
        .run(parameters.getRuntime(), TryRangeTest.class)
        .assertSuccessWithOutput(StringUtils.lines("10", "7.0"));
  }

  @Test
  public void testRegisterAllocationLimitLeadingRange() throws Exception {
    CodeInspector inspector =
        testForR8(parameters.getBackend())
            .addProgramClasses(TryRangeTestLimitRange.class)
            .addKeepMainRule(TryRangeTestLimitRange.class)
            .setMode(CompilationMode.RELEASE)
            .addDontObfuscate()
            .addDontShrink()
            .enableInliningAnnotations()
            .addOptionsModification(
                o -> {
                  o.enableLoadStoreOptimization = false;
                  o.testing.irModifier = this::processIR;
                })
            .run(parameters.getRuntime(), TryRangeTestLimitRange.class)
            .assertSuccessWithOutput("")
            .inspector();
    // Assert that we do not have any register-modifying instructions in the throwing block:
    // L0: ; locals:
    // iload 1;
    // invokestatic com.android.tools.r8.cf.TryRangeTestLimitRange.doSomething(I)F
    // L1: ; locals:
    // 11:   pop
    ClassSubject clazz = inspector.clazz("com.android.tools.r8.cf.TryRangeTestLimitRange");
    CfCode cfCode = clazz.uniqueMethodWithOriginalName("main").getMethod().getCode().asCfCode();
    List<CfInstruction> instructions = cfCode.getInstructions();
    CfLabel startLabel = cfCode.getTryCatchRanges().get(0).start;
    int index = 0;
    while (instructions.get(index) != startLabel) {
      index++;
    }
    assert instructions.get(index + 1) instanceof CfLoad;
    assert instructions.get(index + 2) instanceof CfLabel;
    assert instructions.get(index + 3) instanceof CfPosition;
    assert instructions.get(index + 4) instanceof CfInvoke;
    assert instructions.get(index + 5) == cfCode.getTryCatchRanges().get(0).end;
    assert instructions.get(index + 6) instanceof CfStackInstruction;
  }

  private void processIR(IRCode code, AppView<?> appView) {
    if (!code.method().qualifiedName().equals(TryRangeTestLimitRange.class.getName() + ".main")) {
      return;
    }
    BasicBlock entryBlock = code.entryBlock();
    BasicBlock tryBlock = code.blocks.get(1);
    assertTrue(tryBlock.hasCatchHandlers());
    InstructionListIterator it = entryBlock.listIterator();
    Instruction constNumber = it.next();
    while (!constNumber.isConstNumber()) {
      constNumber = it.next();
    }
    it.removeInstructionIgnoreOutValue();
    Instruction add = it.next();
    while (!add.isAdd()) {
      add = it.next();
    }
    it.removeInstructionIgnoreOutValue();
    tryBlock.getInstructions().addFirst(add);
    tryBlock.getInstructions().addFirst(constNumber);
  }
}
