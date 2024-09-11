// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.metadata;

import com.android.tools.r8.Version;
import com.android.tools.r8.dex.VirtualFile;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.utils.InternalOptions;
import java.util.List;

public class BuildMetadataFactory {

  @SuppressWarnings("UnusedParameter")
  public static D8BuildMetadata create(AppView<AppInfo> appView) {
    return D8BuildMetadataImpl.builder().setVersion(Version.LABEL).build();
  }

  public static R8BuildMetadata create(
      AppView<? extends AppInfoWithClassHierarchy> appView, List<VirtualFile> virtualFiles) {
    InternalOptions options = appView.options();
    return R8BuildMetadataImpl.builder()
        .setOptions(new R8OptionsImpl(options))
        .setBaselineProfileRewritingOptions(R8BaselineProfileRewritingOptionsImpl.create(options))
        .setResourceOptimizationOptions(R8ResourceOptimizationOptionsImpl.create(options))
        .setStartupOptimizationOptions(
            R8StartupOptimizationOptionsImpl.create(options, virtualFiles))
        .setVersion(Version.LABEL)
        .build();
  }
}
