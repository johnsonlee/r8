// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.androidresources;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.R8TestCompileResultBase;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.androidresources.AndroidResourceTestingUtils.AndroidTestResource;
import com.android.tools.r8.androidresources.AndroidResourceTestingUtils.AndroidTestResourceBuilder;
import com.android.tools.r8.utils.BooleanUtils;
import java.util.List;
import org.junit.Assume;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class TestNameRemovalInResourceTable extends TestBase {

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
        .addRClassInitializeWithDefaultValues(R.string.class, R.drawable.class)
        .build(temp);
  }

  @Test
  public void testR8() throws Exception {
    Assume.assumeTrue(optimized || parameters.getPartialCompilationTestParameters().isNone());
    R8TestCompileResultBase<?> r8TestCompileResult =
        testForR8(parameters)
            .addProgramClasses(FooBar.class)
            .addAndroidResources(getTestResources(temp))
            .applyIf(optimized, R8TestBuilder::enableOptimizedShrinking)
            .addKeepMainRule(FooBar.class)
            .compile();
    r8TestCompileResult
        .inspectShrunkenResources(
            resourceTableInspector -> {
              resourceTableInspector.assertContainsResourceWithName("string", "bar");
              resourceTableInspector.assertContainsResourceWithName("string", "foo");
              resourceTableInspector.assertContainsResourceWithName("drawable", "foobar");
              if (!parameters.isRandomPartialCompilation()) {
                resourceTableInspector.assertDoesNotContainResourceWithName(
                    "string", "unused_string");
                resourceTableInspector.assertDoesNotContainResourceWithName(
                    "drawable", "unused_drawable");
              }
            })
        .run(parameters.getRuntime(), FooBar.class)
        .assertSuccess();
    String dumpResources = r8TestCompileResult.dumpResources();
    if (optimized) {
      if (!parameters.isRandomPartialCompilation()) {
        assertFalse(dumpResources.contains("unused_string"));
        assertFalse(dumpResources.contains("unused_drawable"));
      }
    } else {
      assertTrue(dumpResources.contains("unused_string"));
      assertTrue(dumpResources.contains("unused_drawable"));
    }
  }

  public static class FooBar {

    public static void main(String[] args) {
      if (System.currentTimeMillis() == 0) {
        System.out.println(R.drawable.foobar);
        System.out.println(R.string.bar);
        System.out.println(R.string.foo);
      }
    }
  }

  public static class R {

    public static class string {

      public static int bar;
      public static int foo;
      public static int unused_string;
    }

    public static class drawable {

      public static int foobar;
      public static int unused_drawable;
    }
  }
}
