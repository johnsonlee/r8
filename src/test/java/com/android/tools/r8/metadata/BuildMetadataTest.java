// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.metadata;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.Version;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class BuildMetadataTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    R8BuildMetadata buildMetadata =
        testForR8(parameters.getBackend())
            .addInnerClasses(getClass())
            .addKeepMainRule(Main.class)
            .collectBuildMetadata()
            .setMinApi(parameters)
            .compile()
            .getBuildMetadata();
    String json = buildMetadata.toJson();
    // Inspecting the exact contents is not important here, but it *is* important to test that the
    // property names are unobfuscated when testing with R8lib (!).
    assertEquals("{\"version\":\"" + Version.LABEL + "\"}", json);
    buildMetadata = R8BuildMetadata.fromJson(json);
    assertEquals(Version.LABEL, buildMetadata.getVersion());
  }

  static class Main {

    public static void main(String[] args) {}
  }
}
