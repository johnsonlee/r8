// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.internal.proto;

import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.ToolHelper;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;

public enum ProtoRuntime {
  EDITION2023("edition2023", 1),
  LEGACY("legacy", 0);

  private final String runtimeName;
  private final int syntheticVersionNumber;

  ProtoRuntime(String runtimeName, int syntheticVersionNumber) {
    this.runtimeName = runtimeName;
    this.syntheticVersionNumber = syntheticVersionNumber;
  }

  public void addRuntime(R8TestBuilder<?, ?, ?> testBuilder) {
    Path runtimeDir = Paths.get(ToolHelper.PROTO_RUNTIME_DIR, runtimeName);
    testBuilder
        .addProgramFiles(runtimeDir.resolve("libprotobuf_lite.jar"))
        .addKeepRuleFiles(runtimeDir.resolve("lite_proguard.pgcfg"));
  }

  public Collection<Path> getKeepRuleFiles() {
    Path runtimeDir = Paths.get(ToolHelper.PROTO_RUNTIME_DIR, runtimeName);
    return ImmutableList.of(runtimeDir.resolve("lite_proguard.pgcfg"));
  }

  public Collection<Path> getProgramFiles() {
    Path runtimeDir = Paths.get(ToolHelper.PROTO_RUNTIME_DIR, runtimeName);
    return ImmutableList.of(runtimeDir.resolve("libprotobuf_lite.jar"));
  }

  public void assumeIsNewerThanOrEqualToMinimumRequiredRuntime(ProtoTestSources protoTestSources) {
    assumeTrue(isNewerThanOrEqualTo(protoTestSources.getMinimumRequiredRuntime()));
  }

  public boolean isEdition2023() {
    return this == EDITION2023;
  }

  public boolean isLegacy() {
    return this == LEGACY;
  }

  public boolean isNewerThanOrEqualTo(ProtoRuntime protoRuntime) {
    return syntheticVersionNumber >= protoRuntime.syntheticVersionNumber;
  }

  // The class com.google.protobuf.ProtoMessage is not present in newer proto lite runtimes.
  public void workaroundProtoMessageRemoval(R8TestBuilder<?, ?, ?> testBuilder) {
    if (isNewerThanOrEqualTo(ProtoRuntime.EDITION2023)) {
      testBuilder.addDontWarn("com.google.protobuf.ProtoMessage");
    }
  }
}
