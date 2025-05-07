// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.partial;

import static com.android.tools.r8.MarkerMatcher.assertMarkersMatch;
import static com.android.tools.r8.MarkerMatcher.markerTool;
import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.dex.Marker;
import com.android.tools.r8.dex.Marker.Tool;
import com.android.tools.r8.utils.ExtractMarkerUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import java.util.Collection;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class PartialCompilationSystemPropertyTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  @After
  public void clearR8PartialSystemProperties() {
    System.clearProperty(R8PartialCompilationConfiguration.RANDOMIZE_PROPERTY_NAME);
    System.clearProperty(R8PartialCompilationConfiguration.RANDOMIZE_SEED_PROPERTY_NAME);
    System.clearProperty(R8PartialCompilationConfiguration.INCLUDE_PROPERTY_NAME);
    System.clearProperty(R8PartialCompilationConfiguration.EXCLUDE_PROPERTY_NAME);
  }

  @Test
  public void test() throws Exception {
    try {
      System.setProperty(
          R8PartialCompilationConfiguration.INCLUDE_PROPERTY_NAME,
          IncludedMain.class.getTypeName());
      testForR8(Backend.DEX)
          .addInnerClasses(getClass())
          .addKeepMainRule(IncludedMain.class)
          .setMinApi(apiLevelWithNativeMultiDexSupport())
          .allowStdoutMessages()
          .compile()
          .inspect(
              inspector -> {
                ClassSubject includedClass = inspector.clazz(IncludedMain.class);
                assertThat(includedClass, isPresent());
                assertThat(includedClass.uniqueMethodWithOriginalName("test"), isAbsent());

                ClassSubject excludedClass = inspector.clazz(ExcludedMain.class);
                assertThat(excludedClass, isPresent());
                assertThat(excludedClass.uniqueMethodWithOriginalName("test"), isPresent());
              });
    } finally {
      System.clearProperty(R8PartialCompilationConfiguration.INCLUDE_PROPERTY_NAME);
    }
  }

  @Test
  public void testRandomize() throws Exception {
    try {
      System.setProperty(R8PartialCompilationConfiguration.RANDOMIZE_PROPERTY_NAME, "1");
      R8TestCompileResult compileResult =
          testForR8(Backend.DEX)
              .addInnerClasses(getClass())
              .addKeepMainRule(IncludedMain.class)
              .collectStdout()
              .setMinApi(apiLevelWithNativeMultiDexSupport())
              .compile()
              .assertStdoutThatMatches(containsString("Partial compilation seed:"));

      Collection<Marker> markers =
          ExtractMarkerUtils.extractMarkersFromFile(compileResult.writeToZip());
      assertMarkersMatch(markers, markerTool(Tool.R8Partial));
    } finally {
      System.clearProperty(R8PartialCompilationConfiguration.RANDOMIZE_PROPERTY_NAME);
    }
  }

  @Test
  public void randomizeWithSeed() throws Exception {
    try {
      System.setProperty(R8PartialCompilationConfiguration.RANDOMIZE_PROPERTY_NAME, "1");
      System.setProperty(R8PartialCompilationConfiguration.RANDOMIZE_SEED_PROPERTY_NAME, "42");
      R8TestCompileResult compileResult =
          testForR8(Backend.DEX)
              .addInnerClasses(getClass())
              .addKeepMainRule(IncludedMain.class)
              .allowUnusedProguardConfigurationRules()
              .collectStdout()
              .setMinApi(apiLevelWithNativeMultiDexSupport())
              .compile()
              .assertStdoutThatMatches(containsString("Partial compilation seed: 42."));

      Collection<Marker> markers =
          ExtractMarkerUtils.extractMarkersFromFile(compileResult.writeToZip());
      assertMarkersMatch(markers, markerTool(Tool.R8Partial));
    } finally {
      System.clearProperty(R8PartialCompilationConfiguration.RANDOMIZE_PROPERTY_NAME);
    }
  }

  static class IncludedMain {

    public static void main(String[] args) {
      test();
    }

    // Should be inlined.
    static void test() {}
  }

  static class ExcludedMain {

    public static void main(String[] args) {
      test();
    }

    // Should not be inlined.
    static void test() {}
  }
}
