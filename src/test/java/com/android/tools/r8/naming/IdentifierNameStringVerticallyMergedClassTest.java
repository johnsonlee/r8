// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import com.android.tools.r8.NeverPropagateValue;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class IdentifierNameStringVerticallyMergedClassTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addKeepClassAndMembersRulesWithAllowObfuscation(A.class)
        .addKeepRules("-identifiernamestring class " + Main.class.getTypeName() + " { <fields>; }")
        .addVerticallyMergedClassesInspector(
            inspector -> inspector.assertMergedIntoSubtype(I.class).assertNoOtherClassesMerged())
        .enableMemberValuePropagationAnnotations()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        // TODO(b/344939647): Should be "A".
        .assertSuccessWithOutputLines("null");
  }

  static class Main {

    @NeverPropagateValue
    static String s = "com.android.tools.r8.naming.IdentifierNameStringVerticallyMergedClassTest$I";

    public static void main(String[] args) throws Exception {
      String key = System.currentTimeMillis() > 0 ? s : args[0];
      A a = (A) createInstanceMap().get(key);
      System.out.println(a);
    }

    static Map<String, Object> createInstanceMap() {
      Map<String, Object> map = new HashMap<>();
      map.put(I.class.getName(), new A());
      return map;
    }
  }

  interface I {}

  public static class A implements I {

    @Override
    public String toString() {
      return "A";
    }
  }
}
