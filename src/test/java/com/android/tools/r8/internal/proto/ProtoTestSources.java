// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.internal.proto;

import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;

public enum ProtoTestSources {
  EDITION2023("edition2023"),
  PROTO2("proto2"),
  PROTO3("proto3");

  private final String testName;

  ProtoTestSources(String testName) {
    this.testName = testName;
  }

  public static Collection<ProtoTestSources> getEdition2023AndProto2() {
    return ImmutableList.of(EDITION2023, PROTO2);
  }

  public ProtoRuntime getCorrespondingRuntime() {
    switch (this) {
      case EDITION2023:
        return ProtoRuntime.EDITION2023;
      case PROTO2:
      case PROTO3:
        return ProtoRuntime.LEGACY;
      default:
        throw new Unreachable();
    }
  }

  public CodeInspector getInspector() throws IOException {
    return new CodeInspector(getProgramFiles());
  }

  public ProtoRuntime getMinimumRequiredRuntime() {
    switch (this) {
      case EDITION2023:
        return ProtoRuntime.EDITION2023;
      case PROTO2:
      case PROTO3:
        return ProtoRuntime.LEGACY;
      default:
        throw new Unreachable();
    }
  }

  public Collection<Path> getProgramFiles() {
    Path testDir = getTestDir();
    ImmutableList.Builder<Path> builder =
        ImmutableList.<Path>builder()
            .add(testDir.resolve("proto.jar"), testDir.resolve("test.jar"));
    Path registryJar = testDir.resolve("registry.jar");
    if (Files.exists(registryJar)) {
      builder.add(registryJar);
    }
    return builder.build();
  }

  public Path getTestDir() {
    return Paths.get(ToolHelper.PROTO_TEST_DIR, testName);
  }

  public boolean isProto2() {
    return this == PROTO2;
  }
}
