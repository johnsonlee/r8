// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.jdk11.nest;

import com.android.tools.r8.Jdk9TestUtils;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.utils.StringUtils;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class NestClassMergingTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  private final Class<?> NEST_MAIN_CLASS = NestHostInlining.class;
  private final Class<?> NEST_SUBCLASS_MAIN_CLASS = NestHostInliningSubclasses.class;
  private final Class<?> OUTSIDE_WITH_ACCESS_MAIN_CLASS = OutsideInliningWithAccess.class;
  private final Class<?> OUTSIDE_NO_ACCESS_MAIN_CLASS = OutsideInliningNoAccess.class;
  private final String NEST_MAIN_EXPECTED_RESULT =
      StringUtils.lines("inlining", "InnerNoPrivAccess");
  private final String NEST_SUBCLASS_MAIN_EXPECTED_RESULT =
      StringUtils.lines("inliningSubclass", "InnerNoPrivAccessSubclass");
  private final String OUTSIDE_WITH_ACCESS_MAIN_EXPECTED_RESULT =
      StringUtils.lines("OutsideInliningNoAccess", "inlining");
  private final String OUTSIDE_NO_ACCESS_MAIN_EXPECTED_RESULT =
      StringUtils.lines("OutsideInliningNoAccess");

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withCfRuntimesStartingFromIncluding(CfVm.JDK11).build();
  }

  @Test
  public void testClassMergeAcrossTwoNests() throws Exception {
    // Potentially merge classes from one nest with classes from another nest.
    testClassMergeAcrossNest(
        new Class<?>[] {NEST_MAIN_CLASS}, new String[] {NEST_MAIN_EXPECTED_RESULT});
    testClassMergeAcrossNest(
        new Class<?>[] {NEST_SUBCLASS_MAIN_CLASS},
        new String[] {NEST_SUBCLASS_MAIN_EXPECTED_RESULT});
    testClassMergeAcrossNest(
        new Class<?>[] {NEST_MAIN_CLASS, NEST_SUBCLASS_MAIN_CLASS},
        new String[] {NEST_MAIN_EXPECTED_RESULT, NEST_SUBCLASS_MAIN_EXPECTED_RESULT});
  }

  @Test
  public void testClassMergeAcrossNestAndNonNest() throws Exception {
    // Potentially merge classes from a nest with non nest classes.
    testClassMergeAcrossNest(
        new Class<?>[] {
          NEST_MAIN_CLASS, OUTSIDE_NO_ACCESS_MAIN_CLASS, OUTSIDE_WITH_ACCESS_MAIN_CLASS
        },
        new String[] {
          NEST_MAIN_EXPECTED_RESULT,
          OUTSIDE_NO_ACCESS_MAIN_EXPECTED_RESULT,
          OUTSIDE_WITH_ACCESS_MAIN_EXPECTED_RESULT
        });
    testClassMergeAcrossNest(
        new Class<?>[] {OUTSIDE_NO_ACCESS_MAIN_CLASS},
        new String[] {OUTSIDE_NO_ACCESS_MAIN_EXPECTED_RESULT});
    testClassMergeAcrossNest(
        new Class<?>[] {OUTSIDE_WITH_ACCESS_MAIN_CLASS},
        new String[] {OUTSIDE_WITH_ACCESS_MAIN_EXPECTED_RESULT});
  }

  public void testClassMergeAcrossNest(Class<?>[] mainClasses, String[] expectedResults)
      throws Exception {
    R8TestCompileResult compileResult =
        testForR8(parameters.getBackend())
            .apply(
                b -> {
                  for (Class<?> clazz : mainClasses) {
                    b.addKeepMainRule(clazz);
                  }
                })
            .addOptionsModification(
                options -> {
                  // Disable optimizations else additional classes are removed since they become
                  // unused.
                  options.enableClassInlining = false;
                  options.enableNestReduction = false;
                })
            .enableInliningAnnotations()
            .addProgramClassesAndInnerClasses(
                List.of(
                    NEST_MAIN_CLASS,
                    NEST_SUBCLASS_MAIN_CLASS,
                    OUTSIDE_WITH_ACCESS_MAIN_CLASS,
                    OUTSIDE_NO_ACCESS_MAIN_CLASS))
            .applyIf(parameters.isCfRuntime(), Jdk9TestUtils.addJdk9LibraryFiles(temp))
            .addKeepPackageNamesRule("nesthostexample")
            .compile()
            .inspect(NestAttributesUpdateTest::assertNestAttributesCorrect);
    for (int i = 0; i < mainClasses.length; i++) {
      compileResult
          .run(parameters.getRuntime(), mainClasses[i])
          .assertSuccessWithOutput(expectedResults[i]);
    }
  }
}
