// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.internal.benchmarks.appdumps;

import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.benchmarks.BenchmarkBase;
import com.android.tools.r8.benchmarks.BenchmarkConfig;
import com.android.tools.r8.benchmarks.appdumps.AbortBenchmarkException;
import com.android.tools.r8.benchmarks.appdumps.AppDumpBenchmarkBuilder;
import com.android.tools.r8.utils.timing.Timing;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class AGSABenchmarks extends BenchmarkBase {

  private static final Path dir =
      Paths.get(ToolHelper.THIRD_PARTY_DIR, "closedsource-apps/agsa/20250412-v16.14.47");

  public AGSABenchmarks(BenchmarkConfig config, TestParameters parameters) {
    super(config, parameters);
  }

  @Parameters(name = "{0}")
  public static List<Object[]> data() {
    return parametersFromConfigs(configs());
  }

  public static List<BenchmarkConfig> configs() {
    return ImmutableList.of(
        AppDumpBenchmarkBuilder.builder()
            .setName("AGSA")
            .setDumpDependencyPath(dir)
            .setWarmupIterations(0)
            .buildR8(AGSABenchmarks::configure),
        AppDumpBenchmarkBuilder.builder()
            .setName("AGSATreeShaking")
            .setDumpDependencyPath(dir)
            .setRuntimeOnly()
            .setWarmupIterations(0)
            .buildR8(AGSABenchmarks::configureTreeShaking));
  }

  private static void configure(R8FullTestBuilder testBuilder) {
    testBuilder
        .addOptionsModification(
            options -> {
              options.getOpenClosedInterfacesOptions().suppressAllOpenInterfaces();
              options.getTestingOptions().dontReportFailingCheckDiscarded = true;
            })
        .allowDiagnosticMessages()
        .allowUnusedDontWarnPatterns()
        .allowUnusedProguardConfigurationRules()
        .allowUnnecessaryDontWarnWildcards();
  }

  private static void configureTreeShaking(R8FullTestBuilder testBuilder) {
    testBuilder
        .apply(AGSABenchmarks::configure)
        .addOptionsModification(
            options ->
                options.getTestingOptions().enqueuerInspector =
                    (appInfo, enqueuerMode) -> {
                      if (appInfo.options().printTimes) {
                        Timing timing = appInfo.app().timing;
                        timing.end(); // End "Create result"
                        timing.end(); // End "Trace application"
                        timing.end(); // End "Enqueuer"
                        timing.end(); // End "Strip unused code"
                        timing.report(); // Report "R8 main"
                      }
                      throw new AbortBenchmarkException();
                    });
  }

  @Ignore
  @Test
  @Override
  public void testBenchmarks() throws Exception {
    super.testBenchmarks();
  }

  @Test
  public void testAGSA() throws Exception {
    assumeTrue(ToolHelper.isLocalDevelopment());
    testBenchmarkWithName("AGSA");
  }

  @Test
  public void testAGSAPartial() throws Exception {
    assumeTrue(ToolHelper.isLocalDevelopment());
    testBenchmarkWithName("AGSATreeShaking");
  }
}
