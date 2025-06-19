// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.KotlinCompilerTool.KotlinCompiler;
import com.android.tools.r8.KotlinCompilerTool.KotlinLambdaGeneration;
import com.android.tools.r8.KotlinCompilerTool.KotlinTargetVersion;
import com.android.tools.r8.TestRuntime.CfRuntime;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import org.junit.rules.TemporaryFolder;

public class KotlinCompileMemoizer {

  static class CompilerConfigurationKey {

    private final KotlinTargetVersion targetVersion;
    private final KotlinLambdaGeneration lambdaGeneration;

    private CompilerConfigurationKey(
        KotlinTargetVersion targetVersion, KotlinLambdaGeneration lambdaGeneration) {
      this.targetVersion = targetVersion;
      this.lambdaGeneration = lambdaGeneration;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof CompilerConfigurationKey)) {
        return false;
      }
      CompilerConfigurationKey other = (CompilerConfigurationKey) o;
      return targetVersion == other.targetVersion && lambdaGeneration == other.lambdaGeneration;
    }

    @Override
    public int hashCode() {
      return Objects.hash(targetVersion, lambdaGeneration);
    }
  }

  private final Collection<Path> sources;
  private final CfRuntime runtime;
  private final TemporaryFolder temporaryFolder;

  private Consumer<KotlinCompilerTool> kotlinCompilerToolConsumer = x -> {};
  private final Map<KotlinCompiler, Map<CompilerConfigurationKey, Path>> compiledPaths =
      new IdentityHashMap<>();

  public KotlinCompileMemoizer(Collection<Path> sources) {
    this(sources, CfRuntime.getCheckedInJdk9(), null);
  }

  public KotlinCompileMemoizer(
      Collection<Path> sources, CfRuntime runtime, TemporaryFolder temporaryFolder) {
    this.sources = sources;
    this.runtime = runtime;
    this.temporaryFolder = temporaryFolder;
  }

  public KotlinCompileMemoizer configure(Consumer<KotlinCompilerTool> consumer) {
    this.kotlinCompilerToolConsumer = consumer;
    return this;
  }

  public Path getForConfiguration(KotlinTestParameters kotlinParameters) {
    return getForConfiguration(
        kotlinParameters.getCompiler(),
        kotlinParameters.getTargetVersion(),
        kotlinParameters.getLambdaGeneration());
  }

  public Path getForConfiguration(
      KotlinCompiler compiler,
      KotlinTargetVersion targetVersion,
      KotlinLambdaGeneration lambdaGeneration) {
    Map<CompilerConfigurationKey, Path> kotlinTargetVersionPathMap = compiledPaths.get(compiler);
    if (kotlinTargetVersionPathMap == null) {
      kotlinTargetVersionPathMap = new IdentityHashMap<>();
      compiledPaths.put(compiler, kotlinTargetVersionPathMap);
    }
    return kotlinTargetVersionPathMap.computeIfAbsent(
        new CompilerConfigurationKey(targetVersion, lambdaGeneration),
        ignored -> {
          try {
            KotlinCompilerTool kotlinc =
                temporaryFolder == null
                    ? TestBase.kotlinc(runtime, compiler, targetVersion, lambdaGeneration)
                    : TestBase.kotlinc(
                        runtime, temporaryFolder, compiler, targetVersion, lambdaGeneration);
            return kotlinc.addSourceFiles(sources).apply(kotlinCompilerToolConsumer).compile();
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        });
  }
}
