// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.metadata;

import com.android.tools.r8.utils.InternalOptions;

public class R8BaselineProfileRewritingOptionsImpl implements R8BaselineProfileRewritingOptions {

  private R8BaselineProfileRewritingOptionsImpl() {}

  public static R8BaselineProfileRewritingOptionsImpl create(InternalOptions options) {
    if (options.getArtProfileOptions().getArtProfilesForRewriting().isEmpty()) {
      return null;
    }
    return new R8BaselineProfileRewritingOptionsImpl();
  }
}
