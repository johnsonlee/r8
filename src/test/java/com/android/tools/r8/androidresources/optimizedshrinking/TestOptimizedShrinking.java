// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.androidresources.optimizedshrinking;

import com.android.tools.r8.ResourceShrinkerConfiguration;
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
public class TestOptimizedShrinking extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public boolean debug;

  @Parameter(2)
  public boolean optimized;

  @Parameters(name = "{0}, debug: {1}, optimized: {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDefaultDexRuntime().withAllApiLevels().build(),
        BooleanUtils.values(),
        BooleanUtils.values());
  }

  public static AndroidTestResource getTestResources(TemporaryFolder temp) throws Exception {
    return new AndroidTestResourceBuilder()
        .withSimpleManifestAndAppNameString()
        .addRClassInitializeWithDefaultValues(R.string.class, R.drawable.class, R.styleable.class)
        .build(temp);
  }

  @Test
  public void testR8() throws Exception {
    AndroidTestResource testResources = getTestResources(temp);

    testForR8(parameters.getBackend())
        .setMinApi(parameters)
        .addProgramClasses(FooBar.class)
        .addAndroidResources(testResources)
        .addKeepMainRule(FooBar.class)
        .applyIf(
            optimized,
            builder ->
                builder.addOptionsModification(
                    internalOptions ->
                        internalOptions.resourceShrinkerConfiguration =
                            ResourceShrinkerConfiguration.builder(null)
                                .enableOptimizedShrinkingWithR8()
                                .build()))
        .applyIf(debug, builder -> builder.debug())
        .compile()
        .inspectShrunkenResources(
            resourceTableInspector -> {
              resourceTableInspector.assertContainsResourceWithName("string", "bar");
              resourceTableInspector.assertContainsResourceWithName("string", "foo");
              resourceTableInspector.assertContainsResourceWithName("drawable", "foobar");
              // In debug mode legacy shrinker will not attribute our $R inner class as an R class
              // (this is only used for testing, _real_ R classes are not inner classes.
              if (!debug) {
                resourceTableInspector.assertDoesNotContainResourceWithName(
                    "string", "unused_string");
                resourceTableInspector.assertDoesNotContainResourceWithName(
                    "drawable", "unused_drawable");
              }
              if (!optimized) {
                resourceTableInspector.assertContainsResourceWithName("styleable", "our_styleable");
                // The resource shrinker is currently keeping all styleables,
                // so we don't remove this even when it is unused.
                resourceTableInspector.assertContainsResourceWithName(
                    "styleable", "unused_styleable");
              } else {
                // The styleable works by inlining the resource attr values into the array in the R
                // class.
                // public static final class styleable {
                //   public static final int[] our_styleable={
                //     0x7f010000, 0x7f010001, 0x7f010002, 0x7f010003
                //   };
                // ....
                // This means there are no actual references to the styleable in the resource
                // table from code, only to the attributes that are used (0x7f01000{0,1,2,3} are
                // attr references, not styleable references)
                resourceTableInspector.assertDoesNotContainResourceWithName(
                    "styleable", "our_styleable");
                resourceTableInspector.assertDoesNotContainResourceWithName(
                    "styleable", "unused_styleable");
              }
              // We do remove the attributes pointed at by the unreachable styleable.
              for (int i = 0; i < 4; i++) {

                resourceTableInspector.assertContainsResourceWithName(
                    "attr", "attr_our_styleable" + i);

                // In optimized mode we track these correctly, so we should not unconditionally keep
                // all attributes.
                if (optimized) {
                  resourceTableInspector.assertDoesNotContainResourceWithName(
                      "attr", "attr_unused_styleable" + i);
                } else {
                  resourceTableInspector.assertContainsResourceWithName(
                      "attr", "attr_unused_styleable" + i);
                }
              }
            })
        .run(parameters.getRuntime(), FooBar.class)
        .assertSuccess();
  }

  public static class FooBar {

    public static void main(String[] args) {
      if (System.currentTimeMillis() == 0) {
        System.out.println(R.drawable.foobar);
        System.out.println(R.string.bar);
        System.out.println(R.string.foo);
        System.out.println(R.styleable.our_styleable);
      }
    }
  }
}
