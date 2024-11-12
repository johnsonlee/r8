// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.androidresources;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.StringConsumer;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.androidresources.AndroidResourceTestingUtils.AndroidTestResource;
import com.android.tools.r8.androidresources.AndroidResourceTestingUtils.AndroidTestResourceBuilder;
import com.android.tools.r8.utils.BooleanBox;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
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
    StringBuilder log = new StringBuilder();
    BooleanBox finished = new BooleanBox(false);
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
                          configurationBuilder.setDebugConsumer(
                              new StringConsumer() {
                                @Override
                                public void accept(String string, DiagnosticsHandler handler) {
                                  log.append(string);
                                }

                                @Override
                                public void finished(DiagnosticsHandler handler) {
                                  finished.set(true);
                                }
                              });
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
    // TODO(b/360284664): Add (non compatible) logging for optimized shrinking
    if (!optimized) {
      assertTrue(finished.get());
      // Consistent with the old AGP embedded shrinker
      List<String> strings = StringUtils.splitLines(log.toString());
      // string:bar reachable from code
      for (String dexReachableString : ImmutableList.of("bar", "foo")) {
        ensureDexReachableResourcesState(strings, "string", dexReachableString, true);
        ensureResourceReachabilityState(strings, "string", dexReachableString, true);
        ensureRootResourceState(strings, "string", dexReachableString, true);
        ensureUnusedState(strings, "string", dexReachableString, false);
      }
      // The app name is only reachable from the manifest, not dex
      ensureDexReachableResourcesState(strings, "string", "app_name", false);
      ensureResourceReachabilityState(strings, "string", "app_name", true);
      ensureRootResourceState(strings, "string", "app_name", true);
      ensureUnusedState(strings, "string", "app_name", false);

      ensureDexReachableResourcesState(strings, "drawable", "unused_drawable", false);
      ensureResourceReachabilityState(strings, "drawable", "unused_drawable", false);
      ensureRootResourceState(strings, "drawable", "unused_drawable", false);
      ensureUnusedState(strings, "drawable", "unused_drawable", true);

      ensureDexReachableResourcesState(strings, "drawable", "foobar", true);
      ensureResourceReachabilityState(strings, "drawable", "foobar", true);
      ensureRootResourceState(strings, "drawable", "foobar", true);
      ensureUnusedState(strings, "drawable", "foobar", false);
    } else {
      assertTrue(finished.get());
      List<String> strings = StringUtils.splitLines(log.toString());
      ensureReachableOptimized(strings, "string", "bar", true);
      ensureReachableOptimized(strings, "string", "foo", true);
      ensureReachableOptimized(strings, "drawable", "foobar", true);
      ensureReachableOptimized(strings, "drawable", "unused_drawable", false);
    }
  }

  private void ensureReachableOptimized(
      List<String> logStrings, String type, String name, boolean reachable) {
    assertEquals(
        logStrings.stream().anyMatch(s -> s.startsWith(type + ":" + name + ":")), reachable);
  }

  private void ensureDexReachableResourcesState(
      List<String> logStrings, String type, String name, boolean reachable) {
    // Example line:
    // Marking drawable:foobar:2130771968 reachable: referenced from classes.dex
    assertEquals(
        logStrings.stream()
            .anyMatch(
                s ->
                    s.contains("Marking " + type + ":" + name)
                        && s.contains("reachable: referenced from")),
        reachable);
  }

  private void ensureResourceReachabilityState(
      List<String> logStrings, String type, String name, boolean reachable) {
    // Example line:
    // @packagename:string/bar : reachable=true
    assertTrue(
        logStrings.stream()
            .anyMatch(s -> s.contains(type + "/" + name + " : reachable=" + reachable)));
  }

  private void ensureRootResourceState(
      List<String> logStrings, String type, String name, boolean isRoot) {
    assertEquals(isInSection(logStrings, type, name, "The root reachable resources are:"), isRoot);
  }

  private void ensureUnusedState(
      List<String> logStrings, String type, String name, boolean isUnused) {
    assertEquals(isInSection(logStrings, type, name, "Unused resources are: "), isUnused);
  }

  private static boolean isInSection(
      List<String> logStrings, String type, String name, String sectionHeader) {
    // Example for roots
    // "The root reachable resources are:"
    // " drawable:foobar:2130771968"
    boolean isInSection = false;
    for (String logString : logStrings) {
      if (logString.equals(sectionHeader)) {
        isInSection = true;
        continue;
      }
      if (isInSection) {
        if (!logString.startsWith(" ")) {
          return false;
        }
        if (logString.startsWith(" " + type + ":" + name)) {
          return true;
        }
      }
    }
    return false;
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
