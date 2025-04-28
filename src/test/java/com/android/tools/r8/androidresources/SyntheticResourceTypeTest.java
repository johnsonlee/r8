// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.androidresources;

import com.android.aapt.Resources.ResourceTable;
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
public class SyntheticResourceTypeTest extends TestBase {

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
        .addRClassInitializeWithDefaultValues(R.drawable.class)
        .build(temp)
        .mangleResourceTable(
            resourceTable -> {
              // We are adding a synthetic type that we can't easily get aapt2 to generate.
              // Explicitly build the entry using the resource table builder.
              ResourceTable.Builder resourceTableBuilder = resourceTable.toBuilder();
              resourceTableBuilder
                  .getPackageBuilderList()
                  .get(0)
                  .addTypeBuilder()
                  .setName("macro")
                  .addEntryBuilder()
                  .setName("macro_name")
                  .addConfigValueBuilder()
                  .getValueBuilder()
                  .getItemBuilder()
                  .getStrBuilder()
                  .setValue("macro_value")
                  .build();
              return resourceTableBuilder.build();
            });
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .addProgramClasses(FooBar.class)
        .addAndroidResources(getTestResources(temp))
        .addKeepMainRule(FooBar.class)
        .enableOptimizedShrinking()
        .compile()
        .inspectShrunkenResources(
            resourceTableInspector -> {
              resourceTableInspector.assertContainsResourceWithName("drawable", "foo");
              // The synthetic macro entries should not be touched by the resource shrinker.
              resourceTableInspector.assertContainsResourceWithName("macro", "macro_name");
              if (!parameters.isRandomPartialCompilation()) {
                resourceTableInspector.assertDoesNotContainResourceWithName("drawable", "bar");
              }
            });
  }

  public static class FooBar {
    public static void main(String[] args) {
      System.out.println(R.drawable.foo);
    }
  }

  public static class R {
    public static class drawable {
      public static int foo;
      public static int bar;
    }
  }
}
