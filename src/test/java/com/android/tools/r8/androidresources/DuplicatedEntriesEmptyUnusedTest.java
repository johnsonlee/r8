// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.androidresources;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.androidresources.AndroidResourceTestingUtils.AndroidTestResource;
import com.android.tools.r8.androidresources.AndroidResourceTestingUtils.AndroidTestResourceBuilder;
import com.android.tools.r8.androidresources.DuplicatedEntriesEmptyUnusedTest.FeatureSplit.FeatureSplitMain;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.utils.BooleanUtils;
import java.io.IOException;
import java.util.List;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DuplicatedEntriesEmptyUnusedTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  private static final String SMALL_XML = "<Z/>";

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
        .addRClassInitializeWithDefaultValues(R.xml.class, R.string.class)
        .addXml("duplicated_xml.xml", SMALL_XML)
        .build(temp);
  }

  public AndroidTestResource getFeatureSplitTestResources(TemporaryFolder temp) throws IOException {
    return new AndroidTestResourceBuilder()
        .withSimpleManifestAndAppNameString()
        .setPackageId(0x7e)
        .addRClassInitializeWithDefaultValues(FeatureSplit.R.xml.class, FeatureSplit.R.string.class)
        .addXml("duplicated_xml.xml", SMALL_XML)
        .build(temp);
  }

  @Test
  public void testR8() throws Exception {
    TemporaryFolder featureSplitTemp = ToolHelper.getTemporaryFolderForTest();
    featureSplitTemp.create();
    R8FullTestBuilder testBuilder =
        testForR8(parameters.getBackend())
            .setMinApi(parameters)
            .addProgramClasses(Base.class)
            .addFeatureSplit(FeatureSplitMain.class)
            .addAndroidResources(getTestResources(temp))
            .addFeatureSplitAndroidResources(
                getFeatureSplitTestResources(featureSplitTemp), FeatureSplit.class.getName())
            .applyIf(optimized, R8FullTestBuilder::enableOptimizedShrinking)
            .addKeepMainRule(Base.class)
            .addKeepMainRule(FeatureSplitMain.class);
    if (optimized) {
      testBuilder
          .compile()
          .inspectShrunkenResources(
              resourceTableInspector -> {
                resourceTableInspector.assertContainsResourceWithName("xml", "duplicated_xml");
                resourceTableInspector.assertContainsResourceWithName(
                    "string", "duplicated_string");
              })
          .inspectShrunkenResourcesForFeature(
              resourceTableInspector -> {
                resourceTableInspector.assertContainsResourceWithName("xml", "duplicated_xml");
                resourceTableInspector.assertContainsResourceWithName(
                    "string", "duplicated_string");
              },
              FeatureSplit.class.getName())
          .run(parameters.getRuntime(), Base.class)
          .assertSuccess();
    } else {
      // TODO(b/401546693): This should compile without failure (and keep all resources)
      try {
        testBuilder.compile();
      } catch (CompilationFailedException e) {
        assertThat(
            e.getCause().getMessage(),
            containsString("Operation is not supported for read-only collection"));
        return;
      }
      throw new Unreachable("Problem fixed?");
    }
  }

  public static class Base {

    public static void main(String[] args) {
      if (System.currentTimeMillis() == 0) {
        System.out.println(R.xml.duplicated_xml);
        System.out.println(R.string.duplicated_string);
      }
    }
  }

  public static class R {
    public static class string {
      public static int duplicated_string;
    }

    public static class xml {
      public static int duplicated_xml;
    }
  }

  public static class FeatureSplit {
    public static class FeatureSplitMain {
      public static void main(String[] args) {
        if (System.currentTimeMillis() == 0) {
          System.out.println(R.xml.duplicated_xml);
          System.out.println(R.string.duplicated_string);
        }
      }
    }

    public static class R {
      public static class string {
        public static int duplicated_string;
      }

      public static class xml {
        public static int duplicated_xml;
      }
    }
  }
}
