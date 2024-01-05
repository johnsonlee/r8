// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.androidresources;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.androidresources.AndroidResourceTestingUtils.AndroidTestResource;
import com.android.tools.r8.androidresources.AndroidResourceTestingUtils.AndroidTestResourceBuilder;
import com.android.tools.r8.utils.DescriptorUtils;
import java.io.IOException;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RClassStaticValuesIgnoreTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  private static final String RClassDescriptor =
      descriptor(RClassStaticValuesIgnoreTest.class)
          .replace(RClassStaticValuesIgnoreTest.class.getSimpleName(), "R$string");

  @Parameters(name = "{0}")
  public static TestParametersCollection parameters() {
    return getTestParameters().withDefaultDexRuntime().withAllApiLevels().build();
  }

  public static AndroidTestResource getTestResources(TemporaryFolder temp) throws Exception {
    return new AndroidTestResourceBuilder()
        .withSimpleManifestAndAppNameString()
        .addRClassInitializeWithDefaultValues(R.string.class)
        .build(temp);
  }

  private byte[] getRClassWithReferenceToUnused() throws IOException {
    return transformer(ToBeRenamedToRDollarString.class)
        .setClassDescriptor(RClassDescriptor)
        .transform();
  }

  @Test
  public void testR8() throws Exception {
    AndroidTestResource testResources = getTestResources(temp);
    testForR8(parameters.getBackend())
        .setMinApi(parameters)
        .addProgramClasses(FooBar.class)
        .addProgramClassFileData(getRClassWithReferenceToUnused())
        .addKeepClassAndMembersRulesWithAllowObfuscation(
            DescriptorUtils.descriptorToJavaType(RClassDescriptor))
        .addAndroidResources(testResources)
        .addKeepMainRule(FooBar.class)
        .compile()
        .inspectShrunkenResources(
            resourceTableInspector -> {
              resourceTableInspector.assertContainsResourceWithName("string", "bar");
              resourceTableInspector.assertDoesNotContainResourceWithName(
                  "string", "unused_string");
            })
        .run(parameters.getRuntime(), FooBar.class)
        .assertSuccess();
  }

  public static class FooBar {

    public static void main(String[] args) {
      if (System.currentTimeMillis() == 0) {
        System.out.println(R.string.bar);
      }
    }
  }

  // Simulate R class usage of unused_string
  public static class ToBeRenamedToRDollarString {
    public static int use_the_unused = RClassStaticValuesIgnoreTest.R.string.unused_string;
  }

  public static class R {
    public static class string {

      public static int bar;
      public static int unused_string;
    }
  }
}
