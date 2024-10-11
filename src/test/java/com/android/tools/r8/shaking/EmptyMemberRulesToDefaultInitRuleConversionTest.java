// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentIf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.R8CompatTestBuilder;
import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ThrowableConsumer;
import com.android.tools.r8.utils.BooleanUtils;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class EmptyMemberRulesToDefaultInitRuleConversionTest extends TestBase {

  @Parameter(0)
  public boolean enableEmptyMemberRulesToDefaultInitRuleConversion;

  @Parameter(1)
  public TestParameters parameters;

  @Parameters(name = "{1}, convert: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(),
        getTestParameters().withDefaultRuntimes().withMinimumApiLevel().build());
  }

  @Test
  public void testCompatDefault() throws Exception {
    assumeTrue(enableEmptyMemberRulesToDefaultInitRuleConversion);
    testCompat(R8TestBuilder::clearEnableEmptyMemberRulesToDefaultInitRuleConversion);
  }

  @Test
  public void testCompatExplicit() throws Exception {
    testCompat(
        testBuilder ->
            testBuilder.enableEmptyMemberRulesToDefaultInitRuleConversion(
                enableEmptyMemberRulesToDefaultInitRuleConversion));
  }

  private void testCompat(ThrowableConsumer<? super R8CompatTestBuilder> configuration)
      throws Exception {
    testForR8Compat(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepClassRules(Main.class)
        .apply(configuration)
        .setMinApi(parameters)
        .compile()
        .inspect(inspector -> assertThat(inspector.clazz(Main.class).init(), isPresent()));
  }

  @Test
  public void testFullDefault() throws Exception {
    assumeTrue(enableEmptyMemberRulesToDefaultInitRuleConversion);
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepClassRules(Main.class)
        .clearEnableEmptyMemberRulesToDefaultInitRuleConversion()
        .setMinApi(parameters)
        .compile()
        .inspect(inspector -> assertThat(inspector.clazz(Main.class).init(), isPresent()));
  }

  @Test
  public void testFullExplicit() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepClassRules(Main.class)
        .enableEmptyMemberRulesToDefaultInitRuleConversion(
            enableEmptyMemberRulesToDefaultInitRuleConversion)
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector ->
                assertThat(
                    inspector.clazz(Main.class).init(),
                    isPresentIf(enableEmptyMemberRulesToDefaultInitRuleConversion)));
  }

  static class Main {}
}
