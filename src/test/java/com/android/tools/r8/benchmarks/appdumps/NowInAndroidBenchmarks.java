// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.benchmarks.appdumps;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.benchmarks.BenchmarkBase;
import com.android.tools.r8.benchmarks.BenchmarkConfig;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
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
            .setFromRevision(16017)
            .buildBatchD8(),
        AppDumpBenchmarkBuilder.builder()
            .setName("NowInAndroidAppNoJ$")
            .setDumpDependencyPath(dump)
            .setEnableLibraryDesugaring(false)
            .setFromRevision(16017)
            .buildBatchD8(),
        AppDumpBenchmarkBuilder.builder()
            .setName("NowInAndroidApp")
            .setDumpDependencyPath(dump)
            .setFromRevision(16017)
            .buildR8(),
        AppDumpBenchmarkBuilder.builder()
            .setName("NowInAndroidAppWithResourceShrinking")
            .setDumpDependencyPath(dump)
            .setFromRevision(16017)
            .buildR8WithResourceShrinking());
  }
}
