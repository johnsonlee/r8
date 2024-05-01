// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.ifrule;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsentIf;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NoUnusedInterfaceRemoval;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SubsequentExtendsRemovedClassIfRuleTest extends TestBase {

  @Parameter(0)
  public boolean enableUnusedInterfaceRemoval;

  @Parameter(1)
  public boolean enableVerticalClassMerging;

  @Parameter(2)
  public TestParameters parameters;

  @Parameters(name = "{2}, unused: {0}, vertical: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(),
        BooleanUtils.values(),
        getTestParameters().withDefaultRuntimes().withMaximumApiLevel().build());
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addKeepRules("-if interface *", "-keep class * implements <1> {", "  void unused();", "}")
        .applyIf(
            enableUnusedInterfaceRemoval,
            R8TestBuilder::addNoUnusedInterfaceRemovalAnnotations,
            R8TestBuilder::enableNoUnusedInterfaceRemovalAnnotations)
        .applyIf(
            enableVerticalClassMerging,
            R8TestBuilder::addNoVerticalClassMergingAnnotations,
            R8TestBuilder::enableNoVerticalClassMergingAnnotations)
        .allowUnusedProguardConfigurationRules(enableUnusedInterfaceRemoval)
        .setMinApi(parameters)
        .compile()
        // TODO(b/337905171): Should keep unused.
        .inspect(
            inspector ->
                assertThat(
                    inspector.clazz(A.class).uniqueMethodWithOriginalName("unused"),
                    isAbsentIf(enableUnusedInterfaceRemoval)));
  }

  public static class Main {

    public static void main(String[] args) {
      System.out.println(A.class);
    }
  }

  @NoUnusedInterfaceRemoval
  @NoVerticalClassMerging
  public interface I {}

  public static class A implements I {

    // Kept by -if.
    void unused() {}
  }
}
