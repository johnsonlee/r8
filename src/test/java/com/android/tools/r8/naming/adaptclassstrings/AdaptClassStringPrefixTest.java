// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.adaptclassstrings;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.ProguardVersion;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestShrinkerBuilder;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanUtils;
import java.io.IOException;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class AdaptClassStringPrefixTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public boolean isCompat;

  @Parameter(2)
  public ProguardVersion proguardVersion;

  @Parameters(name = "{0}, isCompat: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDefaultCfRuntime().build(),
        BooleanUtils.values(),
        ProguardVersion.values());
  }

  @Test
  public void testProguard() throws Exception {
    assumeTrue(isCompat);
    testForProguard(proguardVersion)
        .addDontWarn(AdaptClassStringPrefixTest.class)
        .apply(this::setUpTest)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("com.android.tools.r8.naming.adaptclassstrings.a")
        .inspect(inspector -> assertThat(inspector.clazz(Foo.class), isPresentAndRenamed()));
  }

  @Test
  public void testR8() throws Exception {
    assumeTrue(proguardVersion == ProguardVersion.getLatest());
    (isCompat ? testForR8Compat(parameters.getBackend()) : testForR8(parameters.getBackend()))
        .setMinApi(AndroidApiLevel.B)
        .apply(this::setUpTest)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("a.a")
        .inspect(inspector -> assertThat(inspector.clazz(Foo.class), isPresentAndRenamed()));
  }

  private void setUpTest(TestShrinkerBuilder<?, ?, ?, ?, ?> builder) throws IOException {
    builder
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addKeepRules("-keep,allowobfuscation class " + typeName(Foo.class))
        .addKeepRules("-adaptclassstrings com.android.tools.r8.naming.**");
  }

  public static class Foo {}

  public static class Main {

    public static void main(String[] args) {
      System.out.println(
          "com.android.tools.r8.naming.adaptclassstrings.AdaptClassStringPrefixTest$Foo");
    }
  }
}
