// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.jdk17.enumStatic;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.enumunboxing.EnumUnboxingTestBase;
import com.android.tools.r8.utils.StringUtils;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class StaticEnumMergingTest extends EnumUnboxingTestBase {

  private static final String EXPECTED_RESULT = StringUtils.lines("-17");
  private final TestParameters parameters;
  private final boolean enumValueOptimization;
  private final EnumKeepRules enumKeepRules;

  @Parameters(name = "{0} valueOpt: {1} keep: {2}")
  public static List<Object[]> data() {
    return EnumUnboxingTestBase.enumUnboxingTestParameters();
  }

  public StaticEnumMergingTest(
      TestParameters parameters, boolean enumValueOptimization, EnumKeepRules enumKeepRules) {
    this.parameters = parameters;
    this.enumValueOptimization = enumValueOptimization;
    this.enumKeepRules = enumKeepRules;
  }

  @Test
  public void testEnumUnboxing() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClassesAndStrippedOuter(getClass())
        .addKeepMainRule(EnumStaticMain.class)
        .addKeepRules(enumKeepRules.getKeepRules())
        .addOptionsModification(opt -> enableEnumOptions(opt, enumValueOptimization))
        .setMinApi(parameters)
        .run(parameters.getRuntime(), EnumStaticMain.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  public class EnumStaticMain {

    enum EnumStatic {
      A,
      B {
        static int i = 17;

        static void print() {
          System.out.println("-" + i);
        }

        public void virtualPrint() {
          print();
        }
      };

      public void virtualPrint() {}
    }

    public static void main(String[] args) throws Throwable {
      EnumStatic.B.virtualPrint();
    }
  }
}
