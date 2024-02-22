// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.vertical;

import com.android.tools.r8.KeepUnusedArguments;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NeverPropagateValue;
import com.android.tools.r8.NoParameterTypeStrengthening;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ConflictWasDetectedTest extends TestBase {

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
        .addHorizontallyMergedClassesInspector(
            inspector ->
                inspector
                    .assertIsCompleteMergeGroup(
                        ClassWithConflictingMethod.class, OtherClassWithConflictingMethod.class)
                    .assertNoOtherClassesMerged())
        .addVerticallyMergedClassesInspector(
            inspector -> inspector.assertMergedIntoSubtype(ConflictingInterface.class))
        .enableInliningAnnotations()
        .enableMemberValuePropagationAnnotations()
        .enableNoParameterTypeStrengtheningAnnotations()
        .enableUnusedArgumentAnnotations()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class);
  }

  static class Main {

    public static void main(String... args) {
      ConflictingInterfaceImpl impl = new ConflictingInterfaceImpl();
      callMethodOnIface(impl);

      // Ensure that the instantiations are not dead code eliminated.
      escape(impl);
    }

    @NeverInline
    @NoParameterTypeStrengthening
    private static void callMethodOnIface(ConflictingInterface iface) {
      System.out.println(iface.method());
      System.out.println(ClassWithConflictingMethod.conflict(null));
      System.out.println(OtherClassWithConflictingMethod.conflict(null));
    }

    @NeverInline
    private static void escape(Object o) {
      if (System.currentTimeMillis() < 0) {
        System.out.println(o);
      }
    }
  }

  public interface ConflictingInterface {

    String method();
  }

  public static class ConflictingInterfaceImpl implements ConflictingInterface {

    @Override
    public String method() {
      return "ConflictingInterfaceImpl::method";
    }
  }

  public static class ClassWithConflictingMethod {

    @KeepUnusedArguments
    @NeverInline
    @NeverPropagateValue
    public static int conflict(ConflictingInterface item) {
      return 123;
    }
  }

  public static class OtherClassWithConflictingMethod {

    @KeepUnusedArguments
    @NeverInline
    @NeverPropagateValue
    public static int conflict(ConflictingInterfaceImpl item) {
      return 321;
    }
  }
}
