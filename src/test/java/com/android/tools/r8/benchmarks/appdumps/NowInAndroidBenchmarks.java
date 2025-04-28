// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.benchmarks.appdumps;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.benchmarks.BenchmarkBase;
import com.android.tools.r8.benchmarks.BenchmarkConfig;
import com.android.tools.r8.benchmarks.BenchmarkTarget;
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
public class NowInAndroidBenchmarks extends BenchmarkBase {

  private static final Path dump =
      Paths.get(ToolHelper.THIRD_PARTY_DIR, "opensource-apps", "android", "nowinandroid");

  public NowInAndroidBenchmarks(BenchmarkConfig config, TestParameters parameters) {
    super(config, parameters);
  }

  @Parameters(name = "{0}")
  public static List<Object[]> data() {
    return parametersFromConfigs(configs());
  }

  public static List<BenchmarkConfig> configs() {
    return ImmutableList.of(
        AppDumpBenchmarkBuilder.builder()
            .setName("NowInAndroidApp")
            .setDumpDependencyPath(dump)
            .setCompilationMode(CompilationMode.DEBUG)
            .setFromRevision(16017)
            .buildBatchD8(),
        AppDumpBenchmarkBuilder.builder()
            .setName("NowInAndroidAppRelease")
            .setDumpDependencyPath(dump)
            .setCompilationMode(CompilationMode.RELEASE)
            .setFromRevision(16017)
            .buildBatchD8(),
        AppDumpBenchmarkBuilder.builder()
            .setName("NowInAndroidAppNoJ$")
            .setDumpDependencyPath(dump)
            .setCompilationMode(CompilationMode.DEBUG)
            .setEnableLibraryDesugaring(false)
            .setFromRevision(16017)
            .buildBatchD8(),
        AppDumpBenchmarkBuilder.builder()
            .setName("NowInAndroidAppNoJ$Release")
            .setDumpDependencyPath(dump)
            .setCompilationMode(CompilationMode.RELEASE)
            .setEnableLibraryDesugaring(false)
            .setFromRevision(16017)
            .buildBatchD8(),
        AppDumpBenchmarkBuilder.builder()
            .setName("NowInAndroidAppIncremental")
            .setDumpDependencyPath(dump)
            .setCompilationMode(CompilationMode.DEBUG)
            .setFromRevision(16017)
            .addProgramPackages("com/google/samples/apps/nowinandroid")
            .buildIncrementalD8(),
        AppDumpBenchmarkBuilder.builder()
            .setName("NowInAndroidAppNoJ$Incremental")
            .setDumpDependencyPath(dump)
            .setCompilationMode(CompilationMode.DEBUG)
            .setEnableLibraryDesugaring(false)
            .setFromRevision(16017)
            .addProgramPackages("com/google/samples/apps/nowinandroid")
            .buildIncrementalD8(),
        AppDumpBenchmarkBuilder.builder()
            .setName("NowInAndroidApp")
            .setDumpDependencyPath(dump)
            .setFromRevision(16017)
            .buildR8(),
        AppDumpBenchmarkBuilder.builder()
            .setName("NowInAndroidAppPartial")
            .setDumpDependencyPath(dump)
            .setFromRevision(16017)
            .buildR8WithPartialShrinking(),
        AppDumpBenchmarkBuilder.builder()
            .setName("NowInAndroidAppWithResourceShrinking")
            .setDumpDependencyPath(dump)
            .setEnableResourceShrinking(true)
            .setFromRevision(16017)
            .buildR8());
  }

  @Ignore
  @Test
  @Override
  public void testBenchmarks() throws Exception {
    super.testBenchmarks();
  }

  @Test
  public void testNowInAndroidAppD8() throws Exception {
    testBenchmarkWithNameAndTarget("NowInAndroidApp", BenchmarkTarget.D8);
  }

  @Test
  public void testNowInAndroidAppRelease() throws Exception {
    testBenchmarkWithName("NowInAndroidAppRelease");
  }

  @Test
  public void testNowInAndroidAppNoJ$() throws Exception {
    testBenchmarkWithName("NowInAndroidAppNoJ$");
  }

  @Test
  public void testNowInAndroidAppNoJ$Release() throws Exception {
    testBenchmarkWithName("NowInAndroidAppNoJ$Release");
  }

  @Test
  public void testNowInAndroidAppIncremental() throws Exception {
    testBenchmarkWithName("NowInAndroidAppIncremental");
  }

  @Test
  public void testNowInAndroidAppNoJ$Incremental() throws Exception {
    testBenchmarkWithName("NowInAndroidAppNoJ$Incremental");
  }

  @Test
  public void testNowInAndroidAppR8() throws Exception {
    testBenchmarkWithNameAndTarget("NowInAndroidApp", BenchmarkTarget.R8);
  }

  @Test
  public void testNowInAndroidAppPartial() throws Exception {
    testBenchmarkWithName("NowInAndroidAppPartial");
  }

  @Test
  public void testNowInAndroidAppWithResourceShrinking() throws Exception {
    testBenchmarkWithName("NowInAndroidAppWithResourceShrinking");
  }
}
