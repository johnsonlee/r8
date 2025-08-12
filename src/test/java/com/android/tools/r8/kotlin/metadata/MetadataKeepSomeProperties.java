// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.metadata;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.KotlinCompileMemoizer;
import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.kotlin.metadata.metadata_pruned_fields.Main;
import com.android.tools.r8.shaking.ProguardKeepAttributes;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * This was a reproduction of b/161230424. With b/435327947 we instead keep all members if there is
 * a keep for `kotlin.Metadata` making this test trivial.
 */
@RunWith(Parameterized.class)
public class MetadataKeepSomeProperties extends KotlinMetadataTestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}, {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(),
        getKotlinTestParameters().withAllCompilersAndLambdaGenerations().build());
  }

  public MetadataKeepSomeProperties(
      TestParameters parameters, KotlinTestParameters kotlinParameters) {
    super(kotlinParameters);
    this.parameters = parameters;
  }

  private static final KotlinCompileMemoizer libJars =
      getCompileMemoizer(getKotlinFileInTest(PKG_PREFIX + "/metadata_pruned_fields", "Methods"));

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramFiles(kotlinc.getKotlinStdlibJar())
        .addProgramFiles(libJars.getForConfiguration(kotlinParameters))
        .addProgramClassFileData(Main.dump())
        .addKeepRules("-keep class " + PKG + ".metadata_pruned_fields.MethodsKt { *; }")
        .addKeepRules("-keep class kotlin.Metadata { *** pn(); }")
        .addKeepMainRule(Main.class)
        .setMinApi(parameters)
        .addKeepAttributes(ProguardKeepAttributes.RUNTIME_VISIBLE_ANNOTATIONS)
        .compile()
        .inspect(
            codeInspector -> {
              final ClassSubject clazz = codeInspector.clazz("kotlin.Metadata");
              assertThat(clazz, isPresent());
              assertThat(clazz.uniqueMethodWithOriginalName("pn"), isPresent());
              assertThat(clazz.uniqueMethodWithOriginalName("d1"), isPresent());
              assertThat(clazz.uniqueMethodWithOriginalName("d2"), isPresent());
              assertThat(clazz.uniqueMethodWithOriginalName("bv"), isPresent());
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("", "Hello World!");
  }
}
