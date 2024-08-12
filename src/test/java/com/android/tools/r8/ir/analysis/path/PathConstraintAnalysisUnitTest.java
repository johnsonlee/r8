// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.analysis.path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.ir.analysis.framework.intraprocedural.DataflowAnalysisResult;
import com.android.tools.r8.ir.analysis.framework.intraprocedural.DataflowAnalysisResult.SuccessfulDataflowAnalysisResult;
import com.android.tools.r8.ir.analysis.path.state.ConcretePathConstraintAnalysisState;
import com.android.tools.r8.ir.analysis.path.state.PathConstraintAnalysisState;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.optimize.argumentpropagation.computation.ComputationTreeNode;
import com.android.tools.r8.optimize.argumentpropagation.computation.ComputationTreeUnopCompareNode;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class PathConstraintAnalysisUnitTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  @Test
  public void test() throws Exception {
    AndroidApp app =
        AndroidApp.builder()
            .addProgramFiles(ToolHelper.getClassFileForTestClass(Main.class))
            .addLibraryFile(ToolHelper.getMostRecentAndroidJar())
            .build();
    AppView<AppInfoWithLiveness> appView = computeAppViewWithLiveness(app);
    CodeInspector inspector = new CodeInspector(app);
    IRCode code =
        inspector.clazz(Main.class).uniqueMethodWithOriginalName("greet").buildIR(appView);
    PathConstraintAnalysis analysis = new PathConstraintAnalysis(appView, code);
    DataflowAnalysisResult result = analysis.run(code.entryBlock());
    assertTrue(result.isSuccessfulAnalysisResult());
    SuccessfulDataflowAnalysisResult<BasicBlock, PathConstraintAnalysisState> successfulResult =
        result.asSuccessfulAnalysisResult();

    // Inspect ENTRY state.
    PathConstraintAnalysisState entryConstraint =
        successfulResult.getBlockExitState(code.entryBlock());
    assertTrue(entryConstraint.isBottom());

    // Inspect THEN state.
    PathConstraintAnalysisState thenConstraint =
        successfulResult.getBlockExitState(code.entryBlock().exit().asIf().getTrueTarget());
    assertTrue(thenConstraint.isConcrete());

    ConcretePathConstraintAnalysisState concreteThenConstraint = thenConstraint.asConcreteState();
    assertEquals(1, concreteThenConstraint.getPathConstraints().size());
    assertEquals(0, concreteThenConstraint.getNegatedPathConstraints().size());

    ComputationTreeNode thenPathConstraint =
        concreteThenConstraint.getPathConstraints().iterator().next();
    assertTrue(thenPathConstraint instanceof ComputationTreeUnopCompareNode);

    // Inspect ELSE state.
    PathConstraintAnalysisState elseConstraint =
        successfulResult.getBlockExitState(code.entryBlock().exit().asIf().fallthroughBlock());
    assertTrue(elseConstraint.isConcrete());

    ConcretePathConstraintAnalysisState concreteElseConstraint = elseConstraint.asConcreteState();
    assertEquals(0, concreteElseConstraint.getPathConstraints().size());
    assertEquals(1, concreteElseConstraint.getNegatedPathConstraints().size());

    ComputationTreeNode elsePathConstraint =
        concreteElseConstraint.getNegatedPathConstraints().iterator().next();
    assertEquals(thenPathConstraint, elsePathConstraint);

    // Inspect RETURN state.
    PathConstraintAnalysisState returnConstraint =
        successfulResult.getBlockExitState(code.computeNormalExitBlocks().get(0));
    assertTrue(returnConstraint.isConcrete());

    ConcretePathConstraintAnalysisState concreteReturnConstraint =
        returnConstraint.asConcreteState();
    assertEquals(1, concreteReturnConstraint.getPathConstraints().size());
    assertEquals(
        concreteReturnConstraint.getPathConstraints(),
        concreteReturnConstraint.getNegatedPathConstraints());
  }

  static class Main {

    public static void greet(String greeting, int flags) {
      if ((flags & 1) != 0) {
        greeting = "Hello, world!";
      }
      System.out.println(greeting);
    }
  }
}
