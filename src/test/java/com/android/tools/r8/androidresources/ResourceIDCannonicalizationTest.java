// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.androidresources;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.androidresources.AndroidResourceTestingUtils.AndroidTestResource;
import com.android.tools.r8.androidresources.AndroidResourceTestingUtils.AndroidTestResourceBuilder;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ResourceIDCannonicalizationTest extends TestBase {
  public static final int EXPECTED_RESOURCE_NUMBER = 0x7f010001;

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection parameters() {
    return getTestParameters()
        .withDefaultDexRuntime()
        .withPartialCompilation()
        .withAllApiLevels()
        .build();
  }

  public static AndroidTestResource getTestResources(TemporaryFolder temp) throws Exception {
    return new AndroidTestResourceBuilder()
        .withSimpleManifestAndAppNameString()
        .addRClassInitializeWithDefaultValues(R.string.class)
        .build(temp);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .addProgramClasses(FooBar.class)
        .addAndroidResources(getTestResources(temp))
        .addKeepMainRule(FooBar.class)
        .enableOptimizedShrinking()
        .compile()
        .inspect(
            codeInspector -> {
              // We should canonicalize the resource numbers separately from the normal const
              // numbers. When mapping from LIR to DEX in the backend we then reapply
              // canonicalization after changing each resource number to a const number.
              long constNumbers =
                  codeInspector
                      .clazz(FooBar.class)
                      .mainMethod()
                      .streamInstructions()
                      .filter(i -> i.isConstNumber(EXPECTED_RESOURCE_NUMBER))
                      .count();
              if (!parameters.isRandomPartialCompilation()) {
                assertEquals(1, constNumbers);
              }
            })
        .inspectShrunkenResources(
            resourceTableInspector -> {
              resourceTableInspector.assertContainsResourceWithName("string", "foo");
            });
  }

  public static class FooBar {

    public static void main(String[] args) {
      if (System.currentTimeMillis() == 0) {
        System.out.println(EXPECTED_RESOURCE_NUMBER);
        System.out.println(R.string.foo);
        System.out.println(EXPECTED_RESOURCE_NUMBER);
        System.out.println(R.string.foo);
        System.out.println(EXPECTED_RESOURCE_NUMBER);
        System.out.println(EXPECTED_RESOURCE_NUMBER);
      }
    }
  }

  public static class R {
    public static class string {
      public static int foo;
    }
  }
}
