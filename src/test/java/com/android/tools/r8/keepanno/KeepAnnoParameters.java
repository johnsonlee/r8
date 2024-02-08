// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.keepanno;

import com.android.tools.r8.TestBase.Backend;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime;

public class KeepAnnoParameters {

  public enum KeepAnnoConfig {
    REFERENCE,
    R8_DIRECT,
    R8_EXTRACT,
    R8_LEGACY,
    PG;
  }

  private final TestParameters parameters;
  private final KeepAnnoConfig config;

  KeepAnnoParameters(TestParameters parameters, KeepAnnoConfig config) {
    this.parameters = parameters;
    this.config = config;
  }

  @Override
  public String toString() {
    return config.name() + ", " + parameters;
  }

  public TestParameters parameters() {
    return parameters;
  }

  public KeepAnnoConfig config() {
    return config;
  }

  public Backend getBackend() {
    return parameters.getBackend();
  }

  public TestRuntime getRuntime() {
    return parameters.getRuntime();
  }

  public boolean isReference() {
    return config == KeepAnnoConfig.REFERENCE;
  }

  public boolean isShrinker() {
    return !isReference();
  }

  public boolean isR8() {
    return config == KeepAnnoConfig.R8_DIRECT
        || config == KeepAnnoConfig.R8_EXTRACT
        || config == KeepAnnoConfig.R8_LEGACY;
  }

  public boolean isPG() {
    return config == KeepAnnoConfig.PG;
  }

  public boolean isNative() {
    return config == KeepAnnoConfig.R8_DIRECT || config == KeepAnnoConfig.R8_EXTRACT;
  }

  public boolean isExtract() {
    return config == KeepAnnoConfig.R8_EXTRACT
        || config == KeepAnnoConfig.R8_LEGACY
        || config == KeepAnnoConfig.PG;
  }
}
