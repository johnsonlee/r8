// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForClass;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ApiModelConstantCanonicalizationTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimesAndAllApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    int sdkInt = parameters.isCfRuntime() ? 0 : parameters.getApiLevel().getLevel();
    List<String> outputLines = new ArrayList<>();
    if (sdkInt < 22) {
      outputLines.add("No cigar!");
    } else if (sdkInt == 22) {
      outputLines.add("apiLevel22");
    } else {
      outputLines.add("apiLevel23");
    }
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class, A.class, Version.class)
        .addLibraryClasses(ApiLevel22.class)
        .addDefaultRuntimeLibrary(parameters)
        .addKeepMainRule(Main.class)
        .addKeepRules(
            "-assumevalues class " + Version.class.getTypeName() + " {",
            "  public static int getSdkInt(int) return " + sdkInt + "..42;",
            "}")
        .apply(setMockApiLevelForClass(ApiLevel22.class, AndroidApiLevel.L_MR1))
        .apply(ApiModelingTestHelper::enableApiCallerIdentification)
        .apply(ApiModelingTestHelper::disableOutliningAndStubbing)
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              List<InstructionSubject> filteredInstructions =
                  inspector
                      .clazz(A.class)
                      .uniqueMethodWithOriginalName("m")
                      .streamInstructions()
                      .filter(i -> i.isInstanceGet() | i.isIf())
                      .collect(Collectors.toList());
              // The instance get can only be moved before the if, when the type of the field is
              // safe to reference on all supported API levels.
              if (sdkInt < 22) {
                assertTrue(filteredInstructions.get(0).isIf());
                assertTrue(filteredInstructions.get(1).isInstanceGet());
              } else {
                assertTrue(filteredInstructions.get(0).isInstanceGet());
                assertTrue(filteredInstructions.get(1).isIf());
              }
            })
        .addRunClasspathClasses(ApiLevel22.class)
        .run(parameters.getRuntime(), Main.class, Integer.toString(sdkInt))
        .assertSuccessWithOutputLines(outputLines);
  }

  public static class ApiLevel22 {

    public void apiLevel22() {
      System.out.println("apiLevel22");
    }

    public void apiLevel23() {
      System.out.println("apiLevel23");
    }
  }

  public static class A {

    private final ApiLevel22 f;

    A(int sdk) {
      f = Version.getSdkInt(sdk) >= 22 ? new ApiLevel22() : null;
    }

    public void m(int sdk) {
      if (sdk >= 23) {
        if (f != null) {
          f.apiLevel23();
        }
      } else if (sdk == 22) {
        if (f != null) {
          f.apiLevel22();
        }
      } else {
        System.out.println("No cigar!");
      }
    }
  }

  public static class Main {

    public static void main(String[] args) {
      int sdk = Integer.parseInt(args[0]);
      A a = new A(sdk);
      a.m(sdk);
    }
  }

  public static class Version {

    // -assumevalues ...
    public static int getSdkInt(int sdk) {
      return sdk;
    }
  }
}
