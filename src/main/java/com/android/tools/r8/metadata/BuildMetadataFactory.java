// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.metadata;

import com.android.tools.r8.Version;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;

public class BuildMetadataFactory {

  @SuppressWarnings("UnusedVariable")
  public static R8BuildMetadata create(AppView<? extends AppInfoWithClassHierarchy> appView) {
    return R8BuildMetadataImpl.builder().setVersion(Version.LABEL).build();
  }
}
