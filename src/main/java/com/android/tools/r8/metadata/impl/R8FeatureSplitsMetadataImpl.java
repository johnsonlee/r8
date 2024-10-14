// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.metadata.impl;

import com.android.tools.r8.FeatureSplit;
import com.android.tools.r8.dex.VirtualFile;
import com.android.tools.r8.features.FeatureSplitConfiguration;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.keepanno.annotations.AnnotationPattern;
import com.android.tools.r8.keepanno.annotations.FieldAccessFlags;
import com.android.tools.r8.keepanno.annotations.KeepConstraint;
import com.android.tools.r8.keepanno.annotations.KeepItemKind;
import com.android.tools.r8.keepanno.annotations.UsedByReflection;
import com.android.tools.r8.metadata.R8DexFileMetadata;
import com.android.tools.r8.metadata.R8FeatureSplitMetadata;
import com.android.tools.r8.metadata.R8FeatureSplitsMetadata;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ListUtils;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@UsedByReflection(
    description = "Keep and preserve @SerializedName for correct (de)serialization",
    constraints = {KeepConstraint.LOOKUP},
    constrainAnnotations = @AnnotationPattern(constant = SerializedName.class),
    kind = KeepItemKind.CLASS_AND_FIELDS,
    fieldAccess = {FieldAccessFlags.PRIVATE},
    fieldAnnotatedByClassConstant = SerializedName.class)
public class R8FeatureSplitsMetadataImpl implements R8FeatureSplitsMetadata {

  @Expose
  @SerializedName("featureSplits")
  private final List<R8FeatureSplitMetadata> featureSplitsMetadata;

  @Expose
  @SerializedName("isolatedSplits")
  private final boolean isolatedSplits;

  public R8FeatureSplitsMetadataImpl(
      FeatureSplitConfiguration configuration, List<R8FeatureSplitMetadata> featureSplitsMetadata) {
    this.featureSplitsMetadata = featureSplitsMetadata;
    this.isolatedSplits = configuration.isIsolatedSplitsEnabled();
  }

  public static R8FeatureSplitsMetadataImpl create(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      Map<FeatureSplit, List<VirtualFile>> virtualFilesForFeatureSplit) {
    InternalOptions options = appView.options();
    assert options.hasFeatureSplitConfiguration();
    FeatureSplitConfiguration configuration = options.getFeatureSplitConfiguration();
    List<FeatureSplit> featureSplits = configuration.getFeatureSplits();
    List<R8FeatureSplitMetadata> featureSplitsMetadata = new ArrayList<>();
    for (FeatureSplit featureSplit : featureSplits) {
      List<VirtualFile> featureSplitVirtualFiles =
          virtualFilesForFeatureSplit.getOrDefault(featureSplit, Collections.emptyList());
      List<R8DexFileMetadata> dexFilesMetadata =
          ListUtils.map(featureSplitVirtualFiles, R8DexFileMetadataImpl::create);
      featureSplitsMetadata.add(new R8FeatureSplitMetadataImpl(dexFilesMetadata));
    }
    return new R8FeatureSplitsMetadataImpl(configuration, featureSplitsMetadata);
  }

  @Override
  public List<R8FeatureSplitMetadata> getFeatureSplits() {
    return featureSplitsMetadata;
  }

  @Override
  public boolean isIsolatedSplitsEnabled() {
    return isolatedSplits;
  }
}
