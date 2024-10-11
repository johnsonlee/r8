// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.metadata;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.Version;
import com.android.tools.r8.androidresources.AndroidResourceTestingUtils.AndroidTestResource;
import com.android.tools.r8.androidresources.AndroidResourceTestingUtils.AndroidTestResourceBuilder;
import com.android.tools.r8.profile.art.model.ExternalArtProfile;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.startup.profile.ExternalStartupClass;
import com.android.tools.r8.startup.profile.ExternalStartupItem;
import com.android.tools.r8.startup.utils.StartupTestingUtils;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class R8BuildMetadataTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    ClassReference mainReference = Reference.classFromClass(Main.class);
    List<ExternalStartupItem> startupProfile =
        ImmutableList.of(ExternalStartupClass.builder().setClassReference(mainReference).build());
    R8BuildMetadata buildMetadata =
        testForR8(parameters.getBackend())
            .addProgramClasses(Main.class)
            .addKeepMainRule(Main.class)
            .addArtProfileForRewriting(
                ExternalArtProfile.builder().addClassRule(mainReference).build())
            .applyIf(
                parameters.isDexRuntime(),
                testBuilder ->
                    testBuilder
                        .addAndroidResources(getTestResources())
                        .addFeatureSplit(FeatureSplitMain.class)
                        .addKeepMainRule(FeatureSplitMain.class)
                        .apply(StartupTestingUtils.addStartupProfile(startupProfile))
                        .enableIsolatedSplits(true)
                        .enableOptimizedShrinking())
            .allowDiagnosticInfoMessages(parameters.canUseNativeMultidex())
            .collectBuildMetadata()
            .setMinApi(parameters)
            .compileWithExpectedDiagnostics(
                diagnostics -> {
                  if (parameters.canUseNativeMultidex()) {
                    diagnostics.assertInfosMatch(
                        diagnosticMessage(containsString("Startup DEX files contains")));
                  } else {
                    diagnostics.assertNoMessages();
                  }
                })
            .getBuildMetadata();
    String json = buildMetadata.toJson();
    // Inspecting the exact contents is not important here, but it *is* important to test that the
    // property names are unobfuscated when testing with R8lib (!).
    assertThat(json, containsString("\"version\":\"" + Version.LABEL + "\""));
    buildMetadata = R8BuildMetadata.fromJson(json);
    inspectDeserializedBuildMetadata(buildMetadata);
  }

  private AndroidTestResource getTestResources() throws IOException {
    return new AndroidTestResourceBuilder().withSimpleManifestAndAppNameString().build(temp);
  }

  private void inspectDeserializedBuildMetadata(R8BuildMetadata buildMetadata) {
    assertNotNull(buildMetadata.getBaselineProfileRewritingMetadata());
    assertNotNull(buildMetadata.getCompilationMetadata());
    R8FeatureSplitsMetadata featureSplitsMetadata = buildMetadata.getFeatureSplitsMetadata();
    if (parameters.isDexRuntime()) {
      assertNotNull(featureSplitsMetadata);
      assertTrue(featureSplitsMetadata.isIsolatedSplitsEnabled());
      assertEquals(1, featureSplitsMetadata.getFeatureSplits().size());
      R8FeatureSplitMetadata featureSplitMetadata = featureSplitsMetadata.getFeatureSplits().get(0);
      assertNotNull(featureSplitMetadata);
      assertEquals(1, featureSplitMetadata.getDexFilesMetadata().size());
      R8DexFileMetadata featureSplitDexFile = featureSplitMetadata.getDexFilesMetadata().get(0);
      assertNotNull(featureSplitDexFile);
      assertNotNull(featureSplitDexFile.getChecksum());
    } else {
      assertNull(featureSplitsMetadata);
    }
    assertNotNull(buildMetadata.getOptionsMetadata());
    assertNotNull(buildMetadata.getOptionsMetadata().getKeepAttributesMetadata());
    assertEquals(
        parameters.isCfRuntime() ? -1 : parameters.getApiLevel().getLevel(),
        buildMetadata.getOptionsMetadata().getMinApiLevel());
    assertFalse(buildMetadata.getOptionsMetadata().isDebugModeEnabled());
    if (parameters.isDexRuntime()) {
      R8ResourceOptimizationMetadata resourceOptimizationOptions =
          buildMetadata.getResourceOptimizationMetadata();
      assertNotNull(resourceOptimizationOptions);
      assertTrue(resourceOptimizationOptions.isOptimizedShrinkingEnabled());
    } else {
      assertNull(buildMetadata.getResourceOptimizationMetadata());
    }
    R8StartupOptimizationMetadata startupOptimizationOptions =
        buildMetadata.getStartupOptizationOptions();
    if (parameters.isDexRuntime()) {
      assertNotNull(startupOptimizationOptions);
      assertEquals(
          parameters.isDexRuntime() && parameters.canUseNativeMultidex() ? 1 : 0,
          startupOptimizationOptions.getNumberOfStartupDexFiles());
    } else {
      assertNull(startupOptimizationOptions);
    }
    assertNotNull(buildMetadata.getStatsMetadata());
    assertEquals(Version.LABEL, buildMetadata.getVersion());
  }

  static class Main {

    public static void main(String[] args) {}
  }

  static class FeatureSplitMain {

    public static void main(String[] args) {}
  }
}
