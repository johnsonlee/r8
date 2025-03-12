// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.jdk11.nest.b169045091;

import static com.android.tools.r8.references.Reference.INT;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.jdk11.nest.b169045091.NestHost.NestMember;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.AndroidApp;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class NestMemberAccessibilityTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public NestMemberAccessibilityTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  @Test
  public void testAccessibility() throws Exception {
    AppView<AppInfoWithLiveness> appView =
        computeAppViewWithLiveness(
            AndroidApp.builder()
                .addClassProgramData(ToolHelper.getClassAsBytes(NestHost.NestMember.class))
                .addClassProgramData(ToolHelper.getClassAsBytes(NonNestMember.class))
                .addClassProgramData(getNestHostClassTransformed())
                .build(),
            TestClass.class);
    AppInfoWithLiveness appInfo = appView.appInfo();
    DexItemFactory dexItemFactory = appView.dexItemFactory();

    DexProgramClass hostContext =
        appView
            .contextIndependentDefinitionFor(buildType(NestHost.class, dexItemFactory))
            .asProgramClass();

    DexProgramClass memberContext =
        appView
            .contextIndependentDefinitionFor(buildType(NestMember.class, dexItemFactory))
            .asProgramClass();

    DexProgramClass nonMemberContext =
        appView
            .contextIndependentDefinitionFor(buildType(NonNestMember.class, dexItemFactory))
            .asProgramClass();

    // Test that NestHost.f is accessible to NestHost and NestMember but not NonNestMember.
    DexField hostFieldReference =
        buildField(
            Reference.field(Reference.classFromClass(NestHost.class), "f", INT), dexItemFactory);
    assertTrue(
        appInfo.resolveField(hostFieldReference).isAccessibleFrom(hostContext, appView).isTrue());
    assertTrue(
        appInfo.resolveField(hostFieldReference).isAccessibleFrom(memberContext, appView).isTrue());
    assertTrue(
        appInfo
            .resolveField(hostFieldReference)
            .isAccessibleFrom(nonMemberContext, appView)
            .isFalse());

    // Test that NestMember.f is accessible to NestMember but not NonNestMember.
    DexField memberFieldReference =
        buildField(
            Reference.field(Reference.classFromClass(NestMember.class), "f", INT), dexItemFactory);
    assertTrue(
        appInfo
            .resolveField(memberFieldReference)
            .isAccessibleFrom(memberContext, appView)
            .isTrue());
    assertTrue(
        appInfo
            .resolveField(memberFieldReference)
            .isAccessibleFrom(nonMemberContext, appView)
            .isFalse());

    // Test that NonNestMember.f is inaccessible to NonNestMember.
    DexField nonMemberFieldReference =
        buildField(
            Reference.field(Reference.classFromClass(NonNestMember.class), "f", INT),
            dexItemFactory);
    assertTrue(
        appInfo
            .resolveField(nonMemberFieldReference)
            .isAccessibleFrom(nonMemberContext, appView)
            .isFalse());
  }

  private byte[] getNestHostClassTransformed() throws Exception {
    return transformer(NestHost.class).setPrivate(NestHost.class.getDeclaredField("f")).transform();
  }

  public static class TestClass {

    public static void main(String[] args) {}
  }
}
