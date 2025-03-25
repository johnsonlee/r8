// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.androidresources;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.androidresources.AndroidResourceTestingUtils.AndroidTestResource;
import com.android.tools.r8.androidresources.AndroidResourceTestingUtils.AndroidTestResourceBuilder;
import com.android.tools.r8.androidresources.DebugConsumerUtils.TestDebugConsumer;
import com.android.tools.r8.utils.BooleanUtils;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ResourceShrinkerLoggingTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public boolean optimized;

  @Parameters(name = "{0}, optimized: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDefaultDexRuntime().withAllApiLevels().build(),
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
    TestDebugConsumer testDebugConsumer = new TestDebugConsumer();
    testForR8(parameters.getBackend())
        .setMinApi(parameters)
        .addProgramClasses(FooBar.class)
        .apply(
            b ->
                b.getBuilder()
                    .setResourceShrinkerConfiguration(
                        configurationBuilder -> {
                          if (optimized) {
                            configurationBuilder.enableOptimizedShrinkingWithR8();
                          }
                          configurationBuilder.setDebugConsumer(testDebugConsumer);
                          return configurationBuilder.build();
                        }))
        .addAndroidResources(getTestResources(temp))
        .addKeepMainRule(FooBar.class)
        .compile()
        .inspectShrunkenResources(
            resourceTableInspector -> {
              resourceTableInspector.assertContainsResourceWithName("string", "bar");
              resourceTableInspector.assertContainsResourceWithName("string", "foo");
              resourceTableInspector.assertContainsResourceWithName("drawable", "foobar");
              resourceTableInspector.assertDoesNotContainResourceWithName(
                  "string", "unused_string");
              resourceTableInspector.assertDoesNotContainResourceWithName(
                  "drawable", "unused_drawable");
            })
        .run(parameters.getRuntime(), FooBar.class)
        .assertSuccess();
    if (!optimized) {
      assertTrue(testDebugConsumer.getFinished());
      // Consistent with the old AGP embedded shrinker
      List<String> logLines = testDebugConsumer.getLogLines();
      // string:bar reachable from code
      for (String dexReachableString : ImmutableList.of("bar", "foo")) {
        DebugConsumerUtils.ensureDexReachable(logLines, "string", dexReachableString);
      }
      // The app name is only reachable from the manifest, not dex
      DebugConsumerUtils.ensureResourceRoot(logLines, "string", "app_name");
      DebugConsumerUtils.ensureUnreachable(logLines, "drawable", "unused_drawable");
      DebugConsumerUtils.ensureDexReachable(logLines, "drawable", "foobar");
    } else {
      assertTrue(testDebugConsumer.getFinished());
      List<String> logLines = testDebugConsumer.getLogLines();
      DebugConsumerUtils.ensureReachableOptimized(logLines, "string", "bar", true);
      DebugConsumerUtils.ensureReachableOptimized(logLines, "string", "foo", true);
      DebugConsumerUtils.ensureReachableOptimized(logLines, "drawable", "foobar", true);
      DebugConsumerUtils.ensureReachableOptimized(logLines, "drawable", "unused_drawable", false);
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
