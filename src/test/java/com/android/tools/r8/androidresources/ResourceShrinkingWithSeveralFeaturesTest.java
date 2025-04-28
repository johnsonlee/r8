// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.androidresources;

import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.androidresources.AndroidResourceTestingUtils.AndroidTestResource;
import com.android.tools.r8.androidresources.AndroidResourceTestingUtils.AndroidTestResourceBuilder;
import com.android.tools.r8.androidresources.ResourceShrinkingWithSeveralFeaturesTest.FeatureSplit.FeatureSplitMain;
import com.android.tools.r8.androidresources.ResourceShrinkingWithSeveralFeaturesTest.FeatureSplit2.FeatureSplit2Main;
import com.android.tools.r8.utils.BooleanUtils;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.List;
import org.junit.Assume;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ResourceShrinkingWithSeveralFeaturesTest extends TestBase {

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
        .addRClassInitializeWithDefaultValues(R.string.class)
        .build(temp);
  }

  public AndroidTestResource getFeatureSplitTestResources(TemporaryFolder temp) throws IOException {
    return new AndroidTestResourceBuilder()
        .withSimpleManifestAndAppNameString()
        .setPackageId(0x80)
        .addRClassInitializeWithDefaultValues(FeatureSplit.R.string.class)
        .build(temp);
  }

  public AndroidTestResource getFeatureSplit2TestResources(TemporaryFolder temp)
      throws IOException {
    return new AndroidTestResourceBuilder()
        .withSimpleManifestAndAppNameString()
        .setPackageId(0x81)
        .addRClassInitializeWithDefaultValues(FeatureSplit2.R.string.class)
        .build(temp);
  }

  @Test
  public void testR8() throws Exception {
    Assume.assumeTrue(optimized || parameters.getPartialCompilationTestParameters().isNone());
    TemporaryFolder featureSplitTemp = ToolHelper.getTemporaryFolderForTest();
    featureSplitTemp.create();

    TemporaryFolder featureSplit2Temp = ToolHelper.getTemporaryFolderForTest();
    featureSplit2Temp.create();
    testForR8(parameters)
        .addProgramClasses(Base.class)
        .addFeatureSplit(FeatureSplitMain.class)
        .addFeatureSplit(FeatureSplit2Main.class)
        .addAndroidResources(getTestResources(temp))
        .addFeatureSplitAndroidResources(
            getFeatureSplitTestResources(featureSplitTemp), FeatureSplit.class.getName())
        .addFeatureSplitAndroidResources(
            getFeatureSplit2TestResources(featureSplit2Temp), FeatureSplit2.class.getName())
        .applyIf(optimized, R8TestBuilder::enableOptimizedShrinking)
        .addResourceShrinkerLogCapture()
        .addKeepMainRule(Base.class)
        .addKeepMainRule(FeatureSplitMain.class)
        .addKeepMainRule(FeatureSplit2Main.class)
        .compile()
        .inspectShrunkenResources(
            resourceTableInspector -> {
              resourceTableInspector.assertContainsResourceWithName("string", "base_used");
              if (!parameters.isRandomPartialCompilation()) {
                resourceTableInspector.assertDoesNotContainResourceWithName(
                    "string", "base_unused");
              }
            })
        .inspectShrunkenResourcesForFeature(
            resourceTableInspector -> {
              resourceTableInspector.assertContainsResourceWithName("string", "feature_used");
              if (!parameters.isRandomPartialCompilation()) {
                resourceTableInspector.assertDoesNotContainResourceWithName(
                    "string", "feature_unused");
              }
            },
            FeatureSplit.class.getName())
        .inspectShrunkenResourcesForFeature(
            resourceTableInspector -> {
              resourceTableInspector.assertContainsResourceWithName("string", "feature2_used");
              if (!parameters.isRandomPartialCompilation()) {
                resourceTableInspector.assertDoesNotContainResourceWithName(
                    "string", "feature2_unused");
              }
            },
            FeatureSplit2.class.getName())
        .inspectResourceShrinkerLog(
            inspector -> {
              for (String dexReachableString :
                  ImmutableList.of("base_used", "feature_used", "feature2_used")) {
                if (optimized) {
                  inspector.ensureReachableOptimized("string", dexReachableString);
                } else {
                  inspector.ensureDexReachable("string", dexReachableString);
                }
              }
              for (String unused :
                  ImmutableList.of("base_unused", "feature_unused", "feature2_unused")) {
                if (optimized) {
                  if (!parameters.isRandomPartialCompilation()) {
                    inspector.ensureUnreachableOptimized("string", unused);
                  }
                } else {
                  inspector.ensureUnreachable("string", unused);
                }
              }
            })
        .run(parameters.getRuntime(), Base.class)
        .assertSuccess();
  }


  public static class Base {

    public static void main(String[] args) {
      if (System.currentTimeMillis() == 0) {
        System.out.println(R.string.base_used);
      }
    }
  }

  public static class R {
    public static class string {
      public static int base_used;
      public static int base_unused;
    }
  }

  public static class FeatureSplit {
    public static class FeatureSplitMain {
      public static void main(String[] args) {
        if (System.currentTimeMillis() == 0) {
          System.out.println(R.string.feature_used);
        }
      }
    }

    public static class R {
      public static class string {
        public static int feature_used;
        public static int feature_unused;
      }
    }
  }

  public static class FeatureSplit2 {
    public static class FeatureSplit2Main {
      public static void main(String[] args) {
        if (System.currentTimeMillis() == 0) {
          System.out.println(R.string.feature2_used);
        }
      }
    }

    public static class R {
      public static class string {
        public static int feature2_used;
        public static int feature2_unused;
      }
    }
  }
}
