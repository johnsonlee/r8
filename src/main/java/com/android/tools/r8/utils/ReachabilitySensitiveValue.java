// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

public enum ReachabilitySensitiveValue {
  DISABLED,
  ENABLED;

  public static ReachabilitySensitiveValue fromBoolean(boolean enabled) {
    return enabled ? ENABLED : DISABLED;
  }

  public boolean isEnabled() {
    return this == ENABLED;
  }
}
