// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.androidresources;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.androidresources.AndroidResourceTestingUtils.AndroidTestResource;
import com.android.tools.r8.androidresources.AndroidResourceTestingUtils.AndroidTestResourceBuilder;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ResourcesInFilledNewArrayDataTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

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

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .setMinApi(parameters)
        .addProgramClasses(FooBar.class)
        .addAndroidResources(getTestResources(temp))
        .addKeepMainRule(FooBar.class)
        .compile()
        // Ensure that we have the fillednewarraydata
        .inspect(
            inspector -> {
              assertTrue(
                  inspector
                      .clazz(FooBar.class)
                      .mainMethod()
                      .streamInstructions()
                      .anyMatch(InstructionSubject::isFilledNewArrayData));
            })
        .inspectShrunkenResources(
            resourceTableInspector -> {
              resourceTableInspector.assertContainsResourceWithName("string", "used");
              resourceTableInspector.assertContainsResourceWithName("string", "used1");
              resourceTableInspector.assertContainsResourceWithName("string", "used2");
              resourceTableInspector.assertContainsResourceWithName("string", "used3");
              resourceTableInspector.assertContainsResourceWithName("string", "used4");
              resourceTableInspector.assertContainsResourceWithName("string", "used5");
              resourceTableInspector.assertDoesNotContainResourceWithName(
                  "string", "unused_string");
            })
        .run(parameters.getRuntime(), FooBar.class)
        .assertSuccess();
  }

  public static class FooBar {

    public static void main(String[] args) {
      if (System.currentTimeMillis() == 0) {
        int[] theValues =
            new int[] {
              R.string.used,
              R.string.used1,
              R.string.used2,
              R.string.used3,
              R.string.used4,
              R.string.used5
            };
        for (int theValue : theValues) {
          System.out.println(theValue);
        }
      }
    }
  }

  public static class R {
    public static class string {
      public static int unused_string;
      public static int used;
      public static int used1;
      public static int used2;
      public static int used3;
      public static int used4;
      public static int used5;
    }
  }
}
