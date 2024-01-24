// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.examples;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.shaking.TreeShakingTest;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class TreeShaking9Test extends TreeShakingTest {

  @Parameters(name = "{0} minify:{1}")
  public static List<Object[]> data() {
    return defaultTreeShakingParameters();
  }

  public TreeShaking9Test(TestParameters parameters, MinifyMode minify) {
    super(parameters, minify);
  }

  @Override
  protected String getName() {
    return "examples/shaking9";
  }

  @Override
  protected String getMainClass() {
    return "shaking9.Shaking";
  }

  @Test
  public void testKeeprules() throws Exception {
    runTest(
        this::inspect, null, null, ImmutableList.of("src/test/examples/shaking9/keep-rules.txt"));
  }

  @Test
  public void testKeeprulesprintusage() throws Exception {
    runTest(
        null, null, null, ImmutableList.of("src/test/examples/shaking9/keep-rules-printusage.txt"));
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject superclass = inspector.clazz("shaking9.Superclass");
    assertThat(superclass, isAbsent());

    ClassSubject subclass = inspector.clazz("shaking9.Subclass");
    assertThat(subclass, isPresent());
    assertThat(subclass.method("void", "aMethod", ImmutableList.of()), isAbsent());
  }
}
