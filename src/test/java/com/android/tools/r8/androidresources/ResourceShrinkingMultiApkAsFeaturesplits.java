// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.androidresources;

import com.android.tools.r8.R8TestCompileResultBase;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
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
public class ResourceShrinkingMultiApkAsFeaturesplits extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public boolean optimized;

  public static String VIEW =
      "<view xmlns:android=\"http://schemas.android.com/apk/res/android\"/>\n";

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

  public static AndroidTestResource getTestResources(TemporaryFolder temp, boolean base, String xml)
      throws Exception {
    AndroidTestResourceBuilder androidTestResourceBuilder =
        new AndroidTestResourceBuilder()
            .withSimpleManifestAndAppNameString()
            .addRClassInitializeWithDefaultValues(base, R.string.class, R.xml.class)
            .addXml("both_used.xml", VIEW)
            .addXml("both_unused.xml", VIEW);
    if (base) {
      androidTestResourceBuilder.addXml("only_in_base.xml", VIEW);
    }
    return androidTestResourceBuilder.build(temp);
  }

  @Test
  public void test() throws Exception {
    Assume.assumeTrue(optimized || parameters.getPartialCompilationTestParameters().isNone());
    TemporaryFolder featureSplitTemp = ToolHelper.getTemporaryFolderForTest();
    featureSplitTemp.create();
    String featureSplitName = "featuresplit";
    R8TestCompileResultBase<?> compileResult =
        testForR8(parameters)
            .addProgramClasses(Base.class)
            .addAndroidResources(getTestResources(temp, true, VIEW))
            .addFeatureSplitAndroidResources(
                // For the feature, we don't add the R class (we already have it in the base)
                // and to test we add one less xml file.
                getTestResources(featureSplitTemp, false, VIEW), featureSplitName)
            .applyIf(optimized, b -> b.enableOptimizedShrinking())
            .addKeepMainRule(Base.class)
            .compile()
            .inspectShrunkenResources(
                resourceTableInspector -> {
                  resourceTableInspector.assertContainsResourceWithName("string", "used");
                  resourceTableInspector.assertContainsResourceWithName("xml", "both_used");
                  resourceTableInspector.assertContainsResourceWithName("xml", "only_in_base");
                  if (!parameters.isRandomPartialCompilation()) {
                    resourceTableInspector.assertDoesNotContainResourceWithName("string", "unused");
                    resourceTableInspector.assertDoesNotContainResourceWithName(
                        "xml", "both_unused");
                  }
                })
            .inspectShrunkenResourcesForFeature(
                resourceTableInspector -> {
                  resourceTableInspector.assertContainsResourceWithName("string", "used");
                  resourceTableInspector.assertContainsResourceWithName("xml", "both_used");
                  if (!parameters.isRandomPartialCompilation()) {
                    resourceTableInspector.assertDoesNotContainResourceWithName("string", "unused");
                    resourceTableInspector.assertDoesNotContainResourceWithName(
                        "xml", "both_unused");
                    resourceTableInspector.assertDoesNotContainResourceWithName(
                        "xml", "only_in_base");
                  }
                },
                featureSplitName)
            .assertResourceFile("res/xml/both_used.xml", true)
            .assertResourceFile("res/xml/only_in_base.xml", true)
            .assertFeatureResourceFile("res/xml/both_used.xml", true, featureSplitName);
    if (!parameters.isRandomPartialCompilation()) {
      compileResult
          .assertResourceFile("res/xml/both_unused.xml", false)
          .assertFeatureResourceFile("res/xml/both_unused.xml", false, featureSplitName)
          .assertFeatureResourceFile("res/xml/only_in_base.xml", false, featureSplitName);
    }
    compileResult.run(parameters.getRuntime(), Base.class).assertSuccess();
  }

  public static class Base {

    public static void main(String[] args) {
      if (System.currentTimeMillis() == 0) {
        System.out.println(R.string.used);
        System.out.println(R.xml.both_used);
        System.out.println(R.xml.only_in_base);
      }
    }
  }

  public static class R {

    public static class string {
      public static int used;
      public static int unused;
    }

    public static class xml {
      public static int both_used;
      public static int both_unused;
      public static int only_in_base;
    }
  }
}
