// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
//  for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.rewrite.enums;

import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestShrinkerBuilder;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.enumunboxing.EnumUnboxingTestBase.EnumKeepRules;
import com.android.tools.r8.utils.BooleanUtils;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

// This is testing various edge cases of the Enum.valueOf(Class, String) optimization, such as
// passing an enum subclass constant as the first argument to Enum.valueOf. The implementation of
// Enum.valueOf reflects on the user program and keep rules are therefore needed for the program
// to produce the expected result after shrinking.
@RunWith(Parameterized.class)
public class EnumValueOfOptimizationTest extends TestBase {

  @Parameter(0)
  public boolean enableNoVerticalClassMergingAnnotations;

  @Parameter(1)
  public EnumKeepRules enumKeepRules;

  @Parameter(2)
  public TestParameters parameters;

  @Parameters(name = "{2}, annotations: {0}, keep: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(),
        EnumKeepRules.values(),
        getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  @Test
  public void testValueOf() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(EnumValueOfOptimizationTest.class)
        .addKeepMainRule(Main.class)
        .addKeepRules(enumKeepRules.getKeepRules())
        .addOptionsModification(
            opt -> {
              if (enumKeepRules == EnumKeepRules.NONE) {
                // Look for Enum.valueOf() when tracing rather than rely on -keeps.
                opt.experimentalTraceEnumReflection = true;
              }
            })
        .applyIf(
            enableNoVerticalClassMergingAnnotations,
            R8TestBuilder::enableNoVerticalClassMergingAnnotations,
            TestShrinkerBuilder::addNoVerticalClassMergingAnnotations)
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .applyIf(
            parameters.isCfRuntime()
                || enableNoVerticalClassMergingAnnotations
                || enumKeepRules.isStudio(),
            runResult ->
                runResult.assertSuccessWithOutputLines(
                    "npe OK", "iae1 OK", "iae2 OK", "iae3 OK", "iae4 OK"),
            parameters.isDexRuntime() && parameters.getDexRuntimeVersion().isDalvik(),
            runResult ->
                runResult.assertSuccessWithOutputLines(
                    "npe OK", "iae1 OK", "iae2 OK", "iae3 OK", "npe OK"),
            parameters.isDexRuntime()
                && parameters.getDexRuntimeVersion().isNewerThanOrEqual(Version.V10_0_0),
            runResult ->
                runResult.assertSuccessWithOutputLines(
                    "npe OK", "iae1 OK", "iae2 OK", "iae3 OK", "nsme->re OK"),
            runResult ->
                runResult.assertSuccessWithOutputLines(
                    "npe OK", "iae1 OK", "iae2 OK", "iae3 OK", "nsme->ae OK"));
  }

  enum MyEnum {
    A,
    B
  }

  @NoVerticalClassMerging
  enum ComplexEnum {
    A {
      @Override
      public String toString() {
        return "a0";
      }
    },
    B
  }

  @SuppressWarnings({"unchecked", "ConstantConditions"})
  static class Main {
    public static void main(String[] args) {
      myEnumTest();
      complexEnumTest();
    }

    private static void complexEnumTest() {
      Enum<?> e = null;
      try {
        e = subtypeError();
      } catch (AssertionError ae) {
        if (ae.getCause() instanceof NoSuchMethodException) {
          System.out.println("nsme->ae OK");
        } else {
          System.out.println(ae.getClass().getName());
          System.out.println(ae.getCause().getClass().getName());
        }
      } catch (IllegalArgumentException iae) {
        System.out.println("iae4 OK");
      } catch (NullPointerException npe) {
        System.out.println("npe OK");
      } catch (RuntimeException re) {
        if (re.getClass() == RuntimeException.class
            && re.getCause() instanceof NoSuchMethodException) {
          System.out.println("nsme->re OK");
        } else {
          System.out.println(re.getClass().getName());
          System.out.println(re.getCause().getClass().getName());
        }
      }
      if (e != null) {
        throw new Error("enum set");
      }
    }

    private static Enum<?> subtypeError() {
      return Enum.valueOf((Class) ComplexEnum.A.getClass(), "A");
    }

    private static void myEnumTest() {
      Enum<?> e = null;
      try {
        e = nullClassError();
        System.out.println("npe KO");
      } catch (NullPointerException ignored) {
        System.out.println("npe OK");
      }
      if (e != null) {
        throw new Error("enum set");
      }
      try {
        e = invalidNameError();
        System.out.println("iae1 KO");
      } catch (IllegalArgumentException iae) {
        System.out.println("iae1 OK");
      }
      if (e != null) {
        throw new Error("enum set");
      }
      try {
        e = enumClassError();
        System.out.println("iae2 KO");
      } catch (IllegalArgumentException iae) {
        System.out.println("iae2 OK");
      }
      if (e != null) {
        throw new Error("enum set");
      }
      try {
        e = voidError();
        System.out.println("iae3 KO");
      } catch (IllegalArgumentException iae) {
        System.out.println("iae3 OK");
      }
      if (e != null) {
        throw new Error("enum set");
      }
    }

    private static Enum<?> voidError() {
      return Enum.valueOf((Class) Void.class, "TYPE");
    }

    private static Enum<?> enumClassError() {
      return Enum.valueOf(Enum.class, "smth");
    }

    private static Enum<?> invalidNameError() {
      return Enum.valueOf(MyEnum.class, "curly");
    }

    private static Enum<?> nullClassError() {
      return Enum.valueOf((Class<MyEnum>) null, "a string");
    }
  }
}
