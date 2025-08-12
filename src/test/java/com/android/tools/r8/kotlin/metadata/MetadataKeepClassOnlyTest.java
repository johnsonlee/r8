// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.metadata;

import com.android.tools.r8.KotlinCompileMemoizer;
import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.kotlin.metadata.metadata_pruned_fields.Main;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MetadataKeepClassOnlyTest extends KotlinMetadataTestBase {

  public TestParameters parameters;

  @Parameterized.Parameters(name = "{0}, {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(),
        getKotlinTestParameters().withAllCompilersAndLambdaGenerations().build());
  }

  public MetadataKeepClassOnlyTest(
      TestParameters parameters, KotlinTestParameters kotlinParameters) {
    super(kotlinParameters);
    this.parameters = parameters;
  }

  private static final KotlinCompileMemoizer code =
      getCompileMemoizer(getKotlinFileInTest(PKG_PREFIX + "/keep_class_only", "A"));

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .addProgramFiles(kotlinc.getKotlinStdlibJar())
        .addProgramFiles(code.getForConfiguration(kotlinParameters))
        .addProgramClassFileData(Main.dump())
        .addKeepRules("-keep class A { *; }")
        .addKeepRules("-keep class kotlin.Metadata")
        .addKeepRuntimeVisibleAnnotations()
        .compile();
  }
}
