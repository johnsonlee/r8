// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import static com.android.tools.r8.shaking.KeepInfoCollectionExported.KeepAnnotationCollectionInfoExported.createExported;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexAnnotation;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.ObjectUtils;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public abstract class KeepAnnotationCollectionInfo {

  public static class RetentionInfo {

    private static final RetentionInfo RETAIN_NONE = new RetentionInfo("none");
    private static final RetentionInfo RETAIN_VISIBLE = new RetentionInfo("visible");
    private static final RetentionInfo RETAIN_INVISIBLE = new RetentionInfo("invisible");
    private static final RetentionInfo RETAIN_ALL = new RetentionInfo("all");

    public static RetentionInfo getRetainNone() {
      return RETAIN_NONE;
    }

    public static RetentionInfo getRetainAll() {
      return RETAIN_ALL;
    }

    public static RetentionInfo getRetainVisible() {
      return RETAIN_VISIBLE;
    }

    public static RetentionInfo getRetainInvisible() {
      return RETAIN_INVISIBLE;
    }

    private final String name;

    private RetentionInfo(String name) {
      this.name = name;
    }

    @Override
    public boolean equals(Object obj) {
      return this == obj;
    }

    @Override
    public int hashCode() {
      return System.identityHashCode(this);
    }

    @Override
    public String toString() {
      return name;
    }

    public boolean isLessThanOrEqualTo(RetentionInfo other) {
      return equals(other) || isNone() || other.isAll();
    }

    public RetentionInfo join(RetentionInfo other) {
      if (other.isLessThanOrEqualTo(this)) {
        return this;
      }
      if (isLessThanOrEqualTo(other)) {
        return other;
      }
      return RETAIN_ALL;
    }

    public boolean isNone() {
      return equals(RETAIN_NONE);
    }

    public boolean isAll() {
      return equals(RETAIN_ALL);
    }

    public boolean isVisible() {
      return equals(RETAIN_VISIBLE);
    }

    public boolean isInvisible() {
      return equals(RETAIN_INVISIBLE);
    }

    public boolean matches(DexAnnotation annotation) {
      if (isNone()) {
        return false;
      }
      if (isAll()) {
        return true;
      }
      int visibility = annotation.getVisibility();
      return (isVisible() && visibility == DexAnnotation.VISIBILITY_RUNTIME)
          || (isInvisible() && visibility == DexAnnotation.VISIBILITY_BUILD);
    }
  }

  public static class KeepAnnotationInfo {

    private static KeepAnnotationInfo BOTTOM =
        new KeepAnnotationInfo(null, RetentionInfo.RETAIN_NONE);
    private static KeepAnnotationInfo TOP = new KeepAnnotationInfo(null, RetentionInfo.RETAIN_ALL);
    private static KeepAnnotationInfo VISIBLE =
        new KeepAnnotationInfo(null, RetentionInfo.RETAIN_VISIBLE);
    private static KeepAnnotationInfo INVISIBLE =
        new KeepAnnotationInfo(null, RetentionInfo.RETAIN_INVISIBLE);

    public static KeepAnnotationInfo getTop() {
      return TOP;
    }

    public static KeepAnnotationInfo getBottom() {
      return BOTTOM;
    }

    public static KeepAnnotationInfo create(DexType typeOrNullForAny, RetentionInfo retention) {
      return typeOrNullForAny == null
          ? createForAnyType(retention)
          : new KeepAnnotationInfo(typeOrNullForAny, retention);
    }

    public static KeepAnnotationInfo createForAnyType(RetentionInfo retention) {
      if (retention.isAll()) {
        return getTop();
      }
      if (retention.isVisible()) {
        return VISIBLE;
      }
      if (retention.isInvisible()) {
        return INVISIBLE;
      }
      assert retention.isNone();
      return getBottom();
    }

    // A concrete annotation type or null if applicable to any type.
    private final DexType type;

    // The retention set for which this info is applicable. A RETAIN_NONE value implies bottom.
    private final RetentionInfo retention;

    private KeepAnnotationInfo(DexType type, RetentionInfo retention) {
      this.type = type;
      this.retention = retention;
    }

    public boolean isAnyType() {
      return type == null;
    }

    public boolean isTop() {
      return isAnyType() && retention.isAll();
    }

    public boolean isBottom() {
      // The bottom is independent of the type.
      return retention.isNone();
    }

    public boolean isLessThanOrEqualTo(KeepAnnotationInfo other) {
      if (isBottom()) {
        return true;
      }
      return (other.isAnyType() || type.isIdenticalTo(other.type))
          && retention.isLessThanOrEqualTo(other.retention);
    }

    public KeepAnnotationInfo joinForSameType(KeepAnnotationInfo other) {
      // For now, we only want to support joining the info on the same types.
      assert ObjectUtils.identical(type, other.type);
      if (other.retention.isLessThanOrEqualTo(retention)) {
        return this;
      }
      if (retention.isLessThanOrEqualTo(other.retention)) {
        return other;
      }
      return create(type, retention.join(other.retention));
    }

    public boolean matches(DexAnnotation annotation) {
      if (type != null && type.isNotIdenticalTo(annotation.getAnnotationType())) {
        return false;
      }
      return retention.matches(annotation);
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
    public boolean isRemovalAllowed(DexAnnotation annotation) {
      return false;
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
    public boolean isRemovalAllowed(DexAnnotation annotation) {
      return true;
    }

    @Override
    public String toString() {
      return "bottom";
    }
  }

  private static final class IntermediateKeepAnnotationCollectionInfo
      extends KeepAnnotationCollectionInfo {
    private final KeepAnnotationInfo anyTypeInfo;
    private final Map<DexType, KeepAnnotationInfo> specificTypeInfo;

    private IntermediateKeepAnnotationCollectionInfo(
        KeepAnnotationInfo anyTypeInfo, Map<DexType, KeepAnnotationInfo> specificTypeInfo) {
      assert anyTypeInfo != null;
      assert anyTypeInfo.isAnyType();
      assert !anyTypeInfo.isTop() || specificTypeInfo == null;
      assert specificTypeInfo == null || !specificTypeInfo.isEmpty();
      this.anyTypeInfo = anyTypeInfo;
      this.specificTypeInfo = specificTypeInfo;
    }

    @Override
    IntermediateKeepAnnotationCollectionInfo asIntermediate() {
      return this;
    }

    @Override
    public boolean isRemovalAllowed(DexAnnotation annotation) {
      if (anyTypeInfo.matches(annotation)) {
        return false;
      }
      if (specificTypeInfo != null) {
        KeepAnnotationInfo info = specificTypeInfo.get(annotation.getAnnotationType());
        if (info != null) {
          assert info.type.isIdenticalTo(annotation.getAnnotationType());
          return !info.retention.matches(annotation);
        }
      }
      return true;
    }

    public boolean internalIsLessThanOrEqualTo(IntermediateKeepAnnotationCollectionInfo other) {
      if (!anyTypeInfo.isLessThanOrEqualTo(other.anyTypeInfo)) {
        return false;
      }
      if (specificTypeInfo == null) {
        // Our specific types are "bottom" so this is less than.
        return true;
      }
      // Check that each specific type is less than the content of the type in other or its
      // any-type when not present.
      for (DexType type : specificTypeInfo.keySet()) {
        KeepAnnotationInfo otherInfo = null;
        if (other.specificTypeInfo != null) {
          otherInfo = other.specificTypeInfo.get(type);
        }
        if (otherInfo == null) {
          otherInfo = other.anyTypeInfo;
        }
        if (!specificTypeInfo.get(type).isLessThanOrEqualTo(otherInfo)) {
          return false;
        }
      }
      return true;
    }

    @Override
    public String toString() {
      StringBuilder stringBuilder = new StringBuilder();
      stringBuilder
          .append("IntermediateKeepAnnotationCollection{ ")
          .append(anyTypeInfo.toString())
          .append("; ");
      specificTypeInfo.forEach(
          (t, info) -> stringBuilder.append(t).append(": ").append(info.toString()));
      stringBuilder.append("}");
      return stringBuilder.toString();
    }
  }

  public static Builder builder() {
    return Builder.createBottom();
  }

  public Builder toBuilder() {
    return Builder.createFrom(this);
  }

  public boolean isTop() {
    return false;
  }

  public boolean isBottom() {
    return false;
  }

  public boolean isTopOrBottom() {
    return isTop() || isBottom();
  }

  IntermediateKeepAnnotationCollectionInfo asIntermediate() {
    throw new Unreachable();
  }

  public abstract boolean isRemovalAllowed(DexAnnotation annotation);

  public boolean isLessThanOrEqualTo(KeepAnnotationCollectionInfo other) {
    if (this == other) {
      return true;
    }
    if (isBottom() || other.isTop()) {
      return true;
    }
    if (isTop() || other.isBottom()) {
      return false;
    }
    return asIntermediate().internalIsLessThanOrEqualTo(other.asIntermediate());
  }

  public static class Builder {

    public static Builder createTop() {
      return new Builder(KeepAnnotationInfo.getTop());
    }

    public static Builder createBottom() {
      return new Builder(KeepAnnotationInfo.getBottom());
    }

    public void destructiveMakeTop() {
      anyTypeInfo = KeepAnnotationInfo.getTop();
      specificTypeInfo = null;
      assert isTop();
    }

    // Info applicable to any type.
    private KeepAnnotationInfo anyTypeInfo;

    // Info applicable to only specific types. Null if no type specific info is present.
    private Map<DexType, KeepAnnotationInfo> specificTypeInfo = null;
    private boolean export = false;

    private Builder(KeepAnnotationInfo anyTypeInfo) {
      assert anyTypeInfo != null;
      assert anyTypeInfo.isAnyType();
      this.anyTypeInfo = anyTypeInfo;
    }

    void setExport() {
      this.export = true;
    }

    private static Builder createFrom(KeepAnnotationCollectionInfo original) {
      if (original.isTop()) {
        return createTop();
      }
      if (original.isBottom()) {
        return createBottom();
      }
      IntermediateKeepAnnotationCollectionInfo intermediate = original.asIntermediate();
      Builder builder = builder();
      builder.anyTypeInfo = intermediate.anyTypeInfo;
      if (intermediate.specificTypeInfo != null) {
        builder.specificTypeInfo = new IdentityHashMap<>(intermediate.specificTypeInfo);
      }
      return builder;
    }

    public boolean isTop() {
      return anyTypeInfo.isTop();
    }

    public boolean isBottom() {
      return specificTypeInfo == null && anyTypeInfo.isBottom();
    }

    public boolean isEqualTo(KeepAnnotationCollectionInfo other) {
      if (other.isBottom()) {
        return isBottom();
      }
      if (other.isTop()) {
        return isTop();
      }
      IntermediateKeepAnnotationCollectionInfo intermediate = other.asIntermediate();
      return anyTypeInfo.equals(intermediate.anyTypeInfo)
          && Objects.equals(specificTypeInfo, intermediate.specificTypeInfo);
    }

    public void destructiveJoin(Builder other) {
      // The empty collection is bottom which joins as identity.
      if (other.isBottom()) {
        return;
      }
      if (other.isTop()) {
        destructiveMakeTop();
        return;
      }
      // Always join the any-type info. If the any-type becomes top, then it applies to all
      // specific types too, and we can simply update the info to top.
      KeepAnnotationInfo oldAnyTypeInfo = anyTypeInfo;
      anyTypeInfo = anyTypeInfo.joinForSameType(other.anyTypeInfo);
      if (anyTypeInfo.isTop()) {
        destructiveMakeTop();
        return;
      }
      if (other.specificTypeInfo != null) {
        if (specificTypeInfo == null) {
          specificTypeInfo = new IdentityHashMap<>(other.specificTypeInfo);
        } else {
          other.specificTypeInfo.forEach(
              (type, info) ->
                  specificTypeInfo.compute(
                      type,
                      (t, existing) -> existing == null ? info : existing.joinForSameType(info)));
        }
        pruneSubsumedSpecificTypeInfos();
      } else if (anyTypeInfo != oldAnyTypeInfo) {
        pruneSubsumedSpecificTypeInfos();
      }
    }

    private void pruneSubsumedSpecificTypeInfos() {
      if (specificTypeInfo == null) {
        return;
      }
      assert !specificTypeInfo.isEmpty();
      // If the any-type does not retain anything then nothing needs to be pruned.
      if (anyTypeInfo.isBottom()) {
        return;
      }
      List<DexType> typesToPrune = null;
      for (DexType type : specificTypeInfo.keySet()) {
        KeepAnnotationInfo info = specificTypeInfo.get(type);
        if (info.retention.isLessThanOrEqualTo(anyTypeInfo.retention)) {
          if (typesToPrune == null) {
            typesToPrune = new ArrayList<>(specificTypeInfo.size());
          }
          typesToPrune.add(type);
        }
      }
      if (typesToPrune != null) {
        if (typesToPrune.size() == specificTypeInfo.size()) {
          specificTypeInfo = null;
        } else {
          typesToPrune.forEach(specificTypeInfo::remove);
        }
      }
    }

    public void destructiveJoinAnyTypeInfo(RetentionInfo retention) {
      if (retention.isLessThanOrEqualTo(anyTypeInfo.retention)) {
        return;
      }
      anyTypeInfo = KeepAnnotationInfo.createForAnyType(anyTypeInfo.retention.join(retention));
      pruneSubsumedSpecificTypeInfos();
    }

    public void destructiveJoinTypeInfo(DexType type, RetentionInfo retention) {
      assert type != null;
      if (retention.isLessThanOrEqualTo(anyTypeInfo.retention)) {
        return;
      }
      if (specificTypeInfo == null) {
        // We expect the number of specific annotations to keep to be small, so we use a small
        // initial capacity.
        specificTypeInfo = new IdentityHashMap<>(3);
      }
      specificTypeInfo.compute(
          type,
          (t, prev) -> {
            KeepAnnotationInfo info = new KeepAnnotationInfo(t, retention);
            return prev == null ? info : info.joinForSameType(prev);
          });
    }

    public KeepAnnotationCollectionInfo build() {
      if (isTop()) {
        assert specificTypeInfo == null;
        return TopKeepAnnotationCollectionInfo.getInstance();
      }
      if (isBottom()) {
        return BottomKeepAnnotationCollectionInfo.getInstance();
      }
      IntermediateKeepAnnotationCollectionInfo intermediate =
          new IntermediateKeepAnnotationCollectionInfo(anyTypeInfo, specificTypeInfo);
      return export ? createExported(intermediate) : intermediate;
    }
  }
}
