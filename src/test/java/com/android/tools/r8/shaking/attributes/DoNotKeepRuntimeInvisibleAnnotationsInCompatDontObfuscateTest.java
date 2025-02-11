// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.attributes;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DoNotKeepRuntimeInvisibleAnnotationsInCompatDontObfuscateTest extends TestBase {

  @Parameter(0)
  public boolean compat;

  @Parameter(1)
  public TestParameters parameters;

  @Parameters(name = "{1}, compat: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(),
        getTestParameters().withDefaultRuntimes().withMaximumApiLevel().build());
  }

  @Test
  public void test() throws Exception {
    testForR8Compat(parameters.getBackend(), compat)
        .addInnerClasses(getClass())
        .addKeepAllClassesRule()
        .addDontObfuscate()
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              ClassSubject mainClass = inspector.clazz(Main.class);
              assertThat(mainClass, isPresent());
              assertTrue(mainClass.annotations().isEmpty());
            });
  }

  @RuntimeInvisibleAnnotation
  static class Main {}

  @Retention(RetentionPolicy.CLASS)
  @interface RuntimeInvisibleAnnotation {}
}
