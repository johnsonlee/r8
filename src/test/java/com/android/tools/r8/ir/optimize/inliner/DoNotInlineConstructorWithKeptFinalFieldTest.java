// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.inliner;

import static com.android.tools.r8.utils.codeinspector.Matchers.isFinal;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.onlyIf;
import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DoNotInlineConstructorWithKeptFinalFieldTest extends TestBase {

  @Parameter(0)
  public boolean keep;

  @Parameter(1)
  public TestParameters parameters;

  @Parameters(name = "{1}, keep: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(), getTestParameters().withDexRuntimesAndAllApiLevels().build());
  }

  @Test
  public void test() throws Exception {
    assumeTrue(parameters.canUseJavaLangInvokeVarHandleStoreStoreFence());
    assertTrue(parameters.canInitNewInstanceUsingSuperclassConstructor());
    testForR8(parameters)
        .addInnerClasses(getClass())
        // Use most recent android.jar so that VarHandle is present.
        .applyIf(
            parameters.isDexRuntime(),
            testBuilder -> testBuilder.addLibraryFiles(ToolHelper.getMostRecentAndroidJar()))
        .addKeepMainRule(Main.class)
        .applyIf(keep, b -> b.addKeepRules("-keepclassmembers class * { final int f; }"))
        .compile()
        .inspect(this::inspect);
  }

  private void inspect(CodeInspector inspector) {
    // When the field is kept we should not change its modifiers, since the app may, for example,
    // reflect on whether the final flag is set.
    FieldSubject fieldSubject = inspector.clazz(Main.class).uniqueFieldWithOriginalName("f");
    assertThat(fieldSubject, isPresent());
    assertThat(fieldSubject, onlyIf(keep, isFinal()));
  }

  static class Main {

    public static void main(String[] args) {
      Main main = new Main(args.length);
      System.out.println(main);
    }

    final int f;

    Main(int f) {
      this.f = f;
    }

    @Override
    public String toString() {
      return Integer.toString(f);
    }
  }
}
