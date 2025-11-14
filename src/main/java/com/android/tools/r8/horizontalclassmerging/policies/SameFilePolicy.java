// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.horizontalclassmerging.MultiClassSameReferencePolicy;
import com.android.tools.r8.synthesis.SyntheticItems;
import com.android.tools.r8.utils.InternalOptions.HorizontalClassMergerOptions;
import java.util.Objects;

public class SameFilePolicy extends MultiClassSameReferencePolicy<Object> {

  private final HorizontalClassMergerOptions options;
  private final SyntheticItems syntheticItems;

  public SameFilePolicy(AppView<?> appView) {
    this.options = appView.options().horizontalClassMergerOptions();
    this.syntheticItems = appView.getSyntheticItems();
  }

  @Override
  public Object getMergeKey(DexProgramClass clazz) {
    return syntheticItems.isSyntheticMethod(clazz)
        ? new SyntheticMethodMergeKey(clazz)
        : new DefaultMergeKey(clazz);
  }

  @Override
  public String getName() {
    return "SameFilePolicy";
  }

  @Override
  public boolean shouldSkipPolicy() {
    return !options.isSameFilePolicyEnabled();
  }

  abstract static class MergeKey {

    private final String key;

    MergeKey(String key) {
      this.key = key;
    }

    @SuppressWarnings("EqualsGetClass")
    @Override
    public final boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      MergeKey mergeKey = (MergeKey) o;
      return key.equals(mergeKey.key);
    }

    @Override
    public final int hashCode() {
      return Objects.hash(key, getClass());
    }
  }

  // Same file policy by default.
  private static class DefaultMergeKey extends MergeKey {

    DefaultMergeKey(DexProgramClass clazz) {
      super(clazz.getType().toDescriptorString().replaceAll("^([^$]+)\\$.*", "$1"));
    }
  }

  // Same package policy for synthetic methods.
  private static class SyntheticMethodMergeKey extends MergeKey {

    SyntheticMethodMergeKey(DexProgramClass clazz) {
      super(clazz.getType().getPackageName());
    }
  }
}
