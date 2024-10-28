// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.metadata;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.android.tools.r8.LibraryDesugaringTestConfiguration;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.Version;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class D8BuildMetadataTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimesAndAllApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    D8BuildMetadata buildMetadata =
        testForD8(parameters.getBackend())
            .addInnerClasses(getClass())
            .addLibraryFiles(ToolHelper.getMostRecentAndroidJar())
            .collectBuildMetadata()
            .enableCoreLibraryDesugaring(
                LibraryDesugaringTestConfiguration.forSpecification(
                    LibraryDesugaringSpecification.JDK11.getSpecification()))
            .release()
            .setMinApi(parameters)
            .compile()
            .getBuildMetadata();
    String json = buildMetadata.toJson();
    System.out.println(json);
    // Inspecting the exact contents is not important here, but it *is* important to test that the
    // property names are unobfuscated when testing with R8lib (!).
    assertThat(json, containsString("\"version\":\"" + Version.LABEL + "\""));
    buildMetadata = D8BuildMetadata.fromJson(json);
    inspectDeserializedBuildMetadata(buildMetadata);
  }

  private void inspectDeserializedBuildMetadata(D8BuildMetadata buildMetadata) {
    // Options metadata.
    assertNotNull(buildMetadata.getOptionsMetadata());
    assertEquals(
        parameters.isCfRuntime() ? null : Integer.toString(parameters.getApiLevel().getLevel()),
        buildMetadata.getOptionsMetadata().getMinApiLevel());
    assertFalse(buildMetadata.getOptionsMetadata().isDebugModeEnabled());
    // Options metadata (library desugaring).
    D8LibraryDesugaringMetadata libraryDesugaringMetadata =
        buildMetadata.getOptionsMetadata().getLibraryDesugaringMetadata();
    if (parameters.isDexRuntime()) {
      assertNotNull(libraryDesugaringMetadata);
      assertEquals(
          "com.tools.android:desugar_jdk_libs_configuration:2.1.2",
          libraryDesugaringMetadata.getIdentifier());
    } else {
      assertNull(libraryDesugaringMetadata);
    }
    // Version metadata.
    assertEquals(Version.LABEL, buildMetadata.getVersion());
  }

  static class Main {

    public static void main(String[] args) {
      testStream(args);
    }

    static void testStream(String[] args) {
      List<String> stringList = Arrays.asList(args);
      List<Integer> integerList =
          stringList.stream().map(Integer::parseInt).collect(Collectors.toList());
      System.out.println(integerList.size());
    }
  }
}
