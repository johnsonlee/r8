// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.accessrelaxation;

import static org.hamcrest.CoreMatchers.containsString;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.resolution.virtualtargets.package_a.ViewModel;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/** Regression test for b/314984596. */
@RunWith(Parameterized.class)
public class PackagePrivateOverrideWithInterfacePublicizerTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(Main.class, ViewModel.class)
        .addProgramClassFileData(getTransformedClasses())
        .run(parameters.getRuntime(), Main.class)
        .apply(this::assertSuccessOutput);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class, ViewModel.class)
        .addProgramClassFileData(getTransformedClasses())
        .addKeepMainRule(Main.class)
        .addKeepClassRules("wtf.I")
        .allowAccessModification()
        .allowStdoutMessages()
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .apply(this::assertSuccessOutput);
  }

  public List<byte[]> getTransformedClasses() throws Exception {
    return ImmutableList.of(
        transformer(I.class).setClassDescriptor("Lwtf/I;").transform(),
        transformer(SubViewModel.class).setImplementsClassDescriptors("Lwtf/I;").transform());
  }

  private void assertSuccessOutput(TestRunResult<?> result) {
    if (parameters.isDexRuntime() && parameters.getDexRuntimeVersion().isDalvik()) {
      result.assertFailureWithErrorThatMatches(containsString("overrides final"));
    } else {
      result.assertSuccessWithOutputLines("SubViewModel.clear()", "ViewModel.clear()");
    }
  }

  public static class Main {

    public static void main(String[] args) {
      SubViewModel subViewModel = new SubViewModel();
      subViewModel.clear();
      subViewModel.clearBridge();
    }
  }

  // Repackaged to wtf.I using transformer so that it sorts higher than the ViewModel class.
  // This ensures that I is processed before ViewModel in the access modifier, which reproduces
  // the bug in b/314984596.
  public interface /*wtf.*/ I {}

  @NeverClassInline
  public static class SubViewModel extends ViewModel implements I {

    @NeverInline
    public void clear() {
      System.out.println("SubViewModel.clear()");
    }
  }
}
