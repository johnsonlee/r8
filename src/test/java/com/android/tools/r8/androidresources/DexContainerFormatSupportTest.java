// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.androidresources;

import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.fail;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.androidresources.AndroidResourceTestingUtils.AndroidTestResource;
import com.android.tools.r8.androidresources.AndroidResourceTestingUtils.AndroidTestResourceBuilder;
import com.android.tools.r8.utils.BooleanUtils;
import java.util.List;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DexContainerFormatSupportTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public boolean optimize;

  @Parameters(name = "{0}, optimize: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDefaultDexRuntime().withAllApiLevels().build(),
        BooleanUtils.values());
  }

  public static AndroidTestResource getTestResources(TemporaryFolder temp) throws Exception {
    return new AndroidTestResourceBuilder()
        .withSimpleManifestAndAppNameString()
        .addRClassInitializeWithDefaultValues(R.string.class)
        .build(temp);
  }

  @Test
  public void testR8Optimized() throws Exception {
    R8FullTestBuilder r8FullTestBuilder =
        testForR8(parameters.getBackend())
            .setMinApi(parameters)
            .addProgramClasses(FooBar.class)
            .addOptionsModification(options -> options.testing.dexContainerExperiment = true)
            .addAndroidResources(getTestResources(temp))
            .addKeepMainRule(FooBar.class)
            .applyIf(optimize, R8TestBuilder::enableOptimizedShrinking);
    if (optimize) {
      // TODO(b/383061093): We can't compile this without hitting an assertion error in the writer.
    } else {
      try {
        r8FullTestBuilder.compileWithExpectedDiagnostics(
            diagnotics ->
                diagnotics.assertErrorMessageThatMatches(
                    containsString(
                        "Dex container experiment is not compatible with legacy resource"
                            + " shrinking")));
        fail();
      } catch (CompilationFailedException e) {
        // Expected
      }
    }
  }

  public static class FooBar {
    public static void main(String[] args) {
      System.out.println(R.string.foo);
    }
  }

  public static class R {

    public static class string {
      public static int foo;
      public static int bar;
    }
  }
}
