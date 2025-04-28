// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.examples.floating_point_annotations;

import static org.junit.Assume.assumeFalse;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.examples.ExamplesTestBase;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class FloatingPointValuedAnnotationTestRunner extends ExamplesTestBase {

  public FloatingPointValuedAnnotationTestRunner(TestParameters parameters) {
    super(parameters);
  }

  @Override
  public Class<?> getMainClass() {
    return FloatingPointValuedAnnotationTest.class;
  }

  @Override
  public List<Class<?>> getTestClasses() {
    return ImmutableList.of(
        FloatingPointValuedAnnotation.class,
        FloatingPointValuedAnnotationTest.class,
        FloatingPointValuedAnnotationTest.A.class,
        FloatingPointValuedAnnotationTest.B.class,
        FloatingPointValuedAnnotationTest.C.class,
        FloatingPointValuedAnnotationTest.D.class);
  }

  @Override
  public String getExpected() {
    return StringUtils.lines("false", "false");
  }

  @Test
  @Override
  public void testR8() throws Exception {
    runTestR8(
        builder ->
            builder
                .addKeepRuntimeVisibleAnnotations()
                .addKeepClassAndMembersRules(
                    FloatingPointValuedAnnotation.class,
                    FloatingPointValuedAnnotationTest.A.class,
                    FloatingPointValuedAnnotationTest.B.class,
                    FloatingPointValuedAnnotationTest.C.class,
                    FloatingPointValuedAnnotationTest.D.class));
  }

  @Test
  @Override
  public void testDebug() throws Exception {
    assumeFalse(
        "VMs from 13 step-out to the continuation (line 28) and not the call-site (line 25).",
        parameters.isDexRuntimeVersionNewerThanOrEqual(Version.V13_0_0));
    super.testDebug();
  }
}
