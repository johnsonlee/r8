// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.androidresources;

import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.androidresources.AndroidResourceTestingUtils.AndroidTestResource;
import com.android.tools.r8.androidresources.AndroidResourceTestingUtils.AndroidTestResourceBuilder;
import com.android.tools.r8.utils.BooleanUtils;
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
public class ResourceShrinkingWithFeaturesAndDuplicatedResEntryTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  private static final String SMALL_XML = "<Z/>";

  @Parameter(1)
  public boolean optimized;

  @Parameters(name = "{0}, optimized: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters()
            .withDefaultDexRuntime()
            .withPartialCompilation()
            .withAllApiLevels()
            .build(),
        BooleanUtils.values());
  }

  public static AndroidTestResource getTestResources(TemporaryFolder temp) throws Exception {
    return new AndroidTestResourceBuilder()
        .withSimpleManifestAndAppNameString()
        .addRClassInitializeWithDefaultValues(R.xml.class, R.string.class)
        .addXml("base_used_xml.xml", SMALL_XML)
        .addXml("base_unused_xml.xml", SMALL_XML)
        .addXml("duplicated_xml.xml", SMALL_XML)
        .build(temp);
  }

  public AndroidTestResource getFeatureSplitTestResources(TemporaryFolder temp) throws IOException {
    return new AndroidTestResourceBuilder()
        .withSimpleManifestAndAppNameString()
        .setPackageId(0x7e)
        .addRClassInitializeWithDefaultValues(FeatureSplit.R.xml.class, FeatureSplit.R.string.class)
        .addXml("feature_used_xml.xml", SMALL_XML)
        .addXml("feature_unused_xml.xml", SMALL_XML)
        .addXml("duplicated_xml.xml", SMALL_XML)
        .build(temp);
  }

  @Test
  public void testR8() throws Exception {
    Assume.assumeTrue(optimized || parameters.getPartialCompilationTestParameters().isNone());
    TemporaryFolder featureSplitTemp = ToolHelper.getTemporaryFolderForTest();
    featureSplitTemp.create();
    testForR8(parameters)
        .addProgramClasses(Base.class)
        .addFeatureSplit(FeatureSplit.FeatureSplitMain.class)
        .addAndroidResources(getTestResources(temp))
        .addFeatureSplitAndroidResources(
            getFeatureSplitTestResources(featureSplitTemp), FeatureSplit.class.getName())
        .applyIf(optimized, R8TestBuilder::enableOptimizedShrinking)
        .addKeepMainRule(Base.class)
        .addKeepMainRule(FeatureSplit.FeatureSplitMain.class)
        .compile()
        .inspectShrunkenResources(
            resourceTableInspector -> {
              resourceTableInspector.assertContainsResourceWithName("xml", "base_used_xml");
              resourceTableInspector.assertContainsResourceWithName("xml", "duplicated_xml");
              if (!parameters.isRandomPartialCompilation()) {
                resourceTableInspector.assertDoesNotContainResourceWithName(
                    "xml", "base_unused_xml");
                resourceTableInspector.assertDoesNotContainResourceWithName(
                    "string", "duplicated_xml");
              }
            })
        .inspectShrunkenResourcesForFeature(
            resourceTableInspector -> {
              resourceTableInspector.assertContainsResourceWithName("xml", "feature_used_xml");
              resourceTableInspector.assertContainsResourceWithName("xml", "duplicated_xml");
              if (!parameters.isRandomPartialCompilation()) {
                resourceTableInspector.assertDoesNotContainResourceWithName(
                    "xml", "feature_unused_xml");
                resourceTableInspector.assertDoesNotContainResourceWithName(
                    "string", "duplicated_xml");
              }
            },
            FeatureSplit.class.getName())
        .run(parameters.getRuntime(), Base.class)
        .assertSuccess();
  }

  public static class Base {

    public static void main(String[] args) {
      if (System.currentTimeMillis() == 0) {
        System.out.println(R.xml.base_used_xml);
        System.out.println(R.xml.duplicated_xml);
      }
    }
  }

  public static class R {
    public static class string {
      // We should still remove unreachable entries with overlapping name if the type is different
      public static int duplicated_xml;
    }

    public static class xml {
      public static int base_used_xml;
      public static int base_unused_xml;
      public static int duplicated_xml;
    }
  }

  public static class FeatureSplit {
    public static class FeatureSplitMain {
      public static void main(String[] args) {
        if (System.currentTimeMillis() == 0) {
          System.out.println(R.xml.feature_used_xml);
        }
      }
    }

    public static class R {
      public static class string {
        // We should still remove unreachable entries with overlapping name if the type is different
        public static int duplicated_xml;
      }

      public static class xml {
        public static int feature_used_xml;
        public static int feature_unused_xml;
        // Unused in the feature
        public static int duplicated_xml;
      }
    }
  }
}
