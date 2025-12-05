// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.processkeeprules.api;

import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.compilerapi.BinaryCompatibilityTestCollection;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.rules.TemporaryFolder;

public class ProcessKeepRulesApiTestCollection
    extends BinaryCompatibilityTestCollection<ProcessKeepRulesApiBinaryTest> {

  private static final Path BINARY_COMPATIBILITY_JAR =
      Paths.get(
          ToolHelper.THIRD_PARTY_DIR, "processkeeprules", "binary_compatibility", "tests.jar");

  public static List<Class<? extends ProcessKeepRulesApiBinaryTest>>
      CLASSES_FOR_BINARY_COMPATIBILITY = ImmutableList.of(ProcessKeepRulesTest.ApiTest.class);

  public static List<Class<? extends ProcessKeepRulesApiBinaryTest>>
      CLASSES_PENDING_BINARY_COMPATIBILITY = ImmutableList.of();

  private final TemporaryFolder temp;

  public ProcessKeepRulesApiTestCollection(TemporaryFolder temp) {
    this.temp = temp;
  }

  @Override
  public TemporaryFolder getTemp() {
    return temp;
  }

  @Override
  public List<Path> getTargetClasspath() {
    return ToolHelper.getBuildPropProcessKeepRulesRuntimePath();
  }

  @Override
  public Path getCheckedInTestJar() {
    return BINARY_COMPATIBILITY_JAR;
  }

  @Override
  public List<Class<? extends ProcessKeepRulesApiBinaryTest>> getCheckedInTestClasses() {
    return CLASSES_FOR_BINARY_COMPATIBILITY;
  }

  @Override
  public List<Class<? extends ProcessKeepRulesApiBinaryTest>> getPendingTestClasses() {
    return CLASSES_PENDING_BINARY_COMPATIBILITY;
  }

  @Override
  public List<Class<?>> getAdditionalClassesForTests() {
    return ImmutableList.of(ProcessKeepRulesApiBinaryTest.class);
  }

  @Override
  public List<Class<?>> getPendingAdditionalClassesForTests() {
    return ImmutableList.of();
  }

  @Override
  public List<String> getVmArgs() {
    return ImmutableList.of();
  }
}
