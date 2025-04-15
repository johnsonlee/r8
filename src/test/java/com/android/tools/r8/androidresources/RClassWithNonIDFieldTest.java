// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.androidresources;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.androidresources.AndroidResourceTestingUtils.AndroidTestResource;
import com.android.tools.r8.androidresources.AndroidResourceTestingUtils.AndroidTestResourceBuilder;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RClassWithNonIDFieldTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection parameters() {
    return getTestParameters()
        .withDefaultDexRuntime()
        .withAllApiLevels()
        .withPartialCompilation()
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
    // We test the non id field type by simply passing the standard test resources but ignoring
    // the aapt generated R class, instead we pass directly the R class from this file, which
    // have no real resource references, but does have a non integer field.
    testForR8(parameters)
        .addProgramClasses(FooBar.class)
        .addAndroidResources(
            getTestResources(temp),
            temp.newFile("resout.zip").toPath(),
            ImmutableList.of(ToolHelper.getClassAsBytes(R.string.class)))
        .addKeepMainRule(FooBar.class)
        .enableOptimizedShrinking()
        .compile()
        .inspectShrunkenResources(
            resourceTableInspector -> {
              // We explicitly do not have any resources traced since we don't use the aapt
              // R class file.
              if (!parameters.isRandomPartialCompilation()) {
                resourceTableInspector.assertDoesNotContainResourceWithName("string", "foo");
              }
            })
        .run(parameters.getRuntime(), FooBar.class)
        .assertSuccessWithOutputLines("bar");
  }

  public static class FooBar {
    public static void main(String[] args) {
      if (System.currentTimeMillis() == 0) {
        System.out.println(R.string.foo);
      }
      if (R.string.nonResource != null) {
        System.out.println("bar");
      }
    }
  }

  public static class R {
    public static class string {
      private static Object nonResource = new Object();
      public static int foo = 0x7f110004;
    }
  }
}
