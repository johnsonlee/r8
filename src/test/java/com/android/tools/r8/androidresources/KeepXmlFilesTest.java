// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.androidresources;

import static org.junit.Assume.assumeTrue;

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
public class KeepXmlFilesTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public boolean optimized;

  @Parameters(name = "{0}, optimized: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters()
            .withDefaultDexRuntime()
            .withAllApiLevels()
            .withPartialCompilation()
            .build(),
        BooleanUtils.values());
  }

  public static AndroidTestResource getTestResources(TemporaryFolder temp) throws Exception {
    return new AndroidTestResourceBuilder()
        .withSimpleManifestAndAppNameString()
        .addKeepXmlFor("@string/bar", "@drawable/foobar")
        .addExplicitKeepRuleFileFor("@drawable/barfoo")
        .addRClassInitializeWithDefaultValues(R.string.class, R.drawable.class)
        .build(temp);
  }

  @Test
  public void testR8() throws Exception {
    // We don't support running R8Partial with non optimized resource shrinking.
    assumeTrue(optimized || parameters.getPartialCompilationTestParameters().isNone());
    testForR8(parameters)
        .addProgramClasses(FooBar.class)
        .addAndroidResources(getTestResources(temp))
        .addKeepMainRule(FooBar.class)
        .applyIf(optimized, R8TestBuilder::enableOptimizedShrinking)
        .compile()
        .inspectShrunkenResources(
            resourceTableInspector -> {
              // Referenced from keep.xml
              resourceTableInspector.assertContainsResourceWithName("string", "bar");
              // Referenced from code
              resourceTableInspector.assertContainsResourceWithName("string", "foo");
              // Referenced from keep.xml
              resourceTableInspector.assertContainsResourceWithName("drawable", "foobar");
              // Referenced from additional keep xml files
              resourceTableInspector.assertContainsResourceWithName("drawable", "barfoo");
              if (!parameters.isRandomPartialCompilation()) {
                resourceTableInspector.assertDoesNotContainResourceWithName(
                    "string", "unused_string");
                resourceTableInspector.assertDoesNotContainResourceWithName(
                    "drawable", "unused_drawable");
              }
            })
        .run(parameters.getRuntime(), FooBar.class)
        .assertSuccess();
  }

  public static class FooBar {

    public static void main(String[] args) {
      if (System.currentTimeMillis() == 0) {
        System.out.println(R.string.foo);
      }
    }
  }

  public static class R {

    public static class string {

      public static int foo;
      public static int bar;
      public static int unused_string;
    }

    public static class drawable {
      public static int foobar;
      public static int barfoo;
      public static int unused_drawable;
    }
  }
}
