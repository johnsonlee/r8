// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.startup.optimization;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.AlwaysInline;
import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestShrinkerBuilder;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.startup.profile.ExternalStartupClass;
import com.android.tools.r8.startup.profile.ExternalStartupItem;
import com.android.tools.r8.startup.profile.ExternalStartupMethod;
import com.android.tools.r8.startup.utils.StartupTestingUtils;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.MethodReferenceUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class StartupBoundaryOptimizationsWithAlwaysInlineTest extends TestBase {

  @Parameter(0)
  public boolean alwaysInline;

  @Parameter(1)
  public TestParameters parameters;

  @Parameters(name = "{1}, always inline: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(), getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  @Test
  public void test() throws Exception {
    List<ExternalStartupItem> startupProfile =
        ImmutableList.of(
            ExternalStartupClass.builder()
                .setClassReference(Reference.classFromClass(Main.class))
                .build(),
            ExternalStartupMethod.builder()
                .setMethodReference(MethodReferenceUtils.mainMethod(Main.class))
                .build());
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .apply(StartupTestingUtils.addStartupProfile(startupProfile))
        .applyIf(
            alwaysInline,
            R8TestBuilder::enableAlwaysInliningAnnotations,
            TestShrinkerBuilder::addAlwaysInliningAnnotations)
        .enableStartupLayoutOptimization(false)
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Click!");
  }

  private void inspect(CodeInspector inspector) {
    if (alwaysInline) {
      assertEquals(1, inspector.allClasses().size());
      assertEquals(1, inspector.clazz(Main.class).allMethods().size());
    } else {
      assertEquals(2, inspector.allClasses().size());
    }
  }

  static class Main {

    public static void main(String[] args) {
      onClick();
    }

    static void onClick() {
      OnClickHandler.handle();
    }
  }

  static class OnClickHandler {

    @AlwaysInline
    static void handle() {
      System.out.println("Click!");
    }
  }
}
