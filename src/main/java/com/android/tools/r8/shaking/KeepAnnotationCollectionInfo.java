// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.graph.DexType;
import java.util.HashSet;
import java.util.Set;

public abstract class KeepAnnotationCollectionInfo {

  public static class KeepAnnotationInfo {
    public static final int RETAIN_NONE = 0;
    public static final int RETAIN_VISIBLE = 1;
    public static final int RETAIN_INVISIBLE = 2;
    public static final int RETAIN_ALL = RETAIN_VISIBLE | RETAIN_INVISIBLE;

    private static KeepAnnotationInfo BOTTOM = new KeepAnnotationInfo(null, RETAIN_NONE);
    private static KeepAnnotationInfo TOP = new KeepAnnotationInfo(null, RETAIN_ALL);

    public static KeepAnnotationInfo getTop() {
      return TOP;
    }

    public static KeepAnnotationInfo getBottom() {
      return BOTTOM;
    }

    // A concrete annotation type or null if applicable to any type.
    private final DexType type;

    // The retention set for which this info is applicable. A RETAIN_NONE value implies bottom.
    private final int retention;

    private KeepAnnotationInfo(DexType type, int retention) {
      this.type = type;
      this.retention = retention;
    }

    @Override
    public String toString() {
      return "KeepAnnotationInfo{" + "type=" + type + ", retention=" + retention + '}';
    }
  }

  // Singleton representing all possible collections of annotations.
  private static final class TopKeepAnnotationCollectionInfo extends KeepAnnotationCollectionInfo {

    private static final KeepAnnotationCollectionInfo INSTANCE =
        new TopKeepAnnotationCollectionInfo();

    public static KeepAnnotationCollectionInfo getInstance() {
      return INSTANCE;
    }

    @Override
    public boolean isTop() {
      return true;
    }

    @Override
    public String toString() {
      return "top";
    }
  }

  // Singleton class representing no collections of annotations.
  private static final class BottomKeepAnnotationCollectionInfo
      extends KeepAnnotationCollectionInfo {

    private static final KeepAnnotationCollectionInfo INSTANCE =
        new BottomKeepAnnotationCollectionInfo();

    public static KeepAnnotationCollectionInfo getInstance() {
      return INSTANCE;
    }

    @Override
    public boolean isBottom() {
      return true;
    }

    @Override
    public String toString() {
      return "bottom";
    }
  }

  public Builder toBuilder() {
    return new Builder(this);
  }

  public boolean isTop() {
    return false;
  }

  public boolean isBottom() {
    return false;
  }

  public boolean isLessThanOrEquals(KeepAnnotationCollectionInfo other) {
    if (this == other) {
      return true;
    }
    if (isBottom() || other.isTop()) {
      return true;
    }
    if (isTop() || other.isBottom()) {
      return false;
    }
    throw new Unimplemented();
  }

  public static class Builder {

    public static Builder makeTop() {
      return new Builder();
    }

    public static Builder makeBottom() {
      Builder builder = new Builder();
      builder.items = new HashSet<>(1);
      return builder;
    }

    // Set of retained items.
    // The null value implies any item.
    // The empty set implies no items.
    private Set<KeepAnnotationInfo> items = null;

    private Builder() {
      assert isTop();
    }

    private Builder(KeepAnnotationCollectionInfo original) {
      if (original.isTop()) {
        assert isTop();
        return;
      }
      if (original.isBottom()) {
        items = new HashSet<>(1);
        assert isBottom();
        return;
      }
      throw new Unimplemented();
    }

    public boolean isTop() {
      return items == null;
    }

    public boolean isBottom() {
      return items != null && items.isEmpty();
    }

    public boolean isEqualTo(KeepAnnotationCollectionInfo other) {
      // TODO(b/319474935): Consider checking directly on the builder and avoid the build.
      KeepAnnotationCollectionInfo self = build();
      return self.isLessThanOrEquals(other) && other.isLessThanOrEquals(self);
    }

    public void join(Builder other) {
      // Joining mutates 'this' with the join of settings from 'other'.
      if (other.isBottom()) {
        // The empty collection is bottom which joins as identity.
        return;
      }
      if (other.isTop()) {
        // The null item set represents top and joins to top.
        items = null;
        return;
      }
      throw new Unimplemented();
    }

    public KeepAnnotationCollectionInfo build() {
      if (isTop()) {
        return TopKeepAnnotationCollectionInfo.getInstance();
      }
      if (isBottom()) {
        return BottomKeepAnnotationCollectionInfo.getInstance();
      }
      throw new Unimplemented();
    }
  }
}
