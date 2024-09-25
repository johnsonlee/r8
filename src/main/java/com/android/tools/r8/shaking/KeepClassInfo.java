// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.InternalOptions;
import java.util.List;
import java.util.function.Function;

/** Immutable keep requirements for a class. */
public class KeepClassInfo extends KeepInfo<KeepClassInfo.Builder, KeepClassInfo> {

  // Requires all aspects of a class to be kept.
  private static final KeepClassInfo TOP =
      new Builder()
          .makeTop()
          .disallowClassInlining()
          .disallowHorizontalClassMerging()
          .disallowPermittedSubclassesRemoval()
          .disallowRepackaging()
          .disallowUnusedInterfaceRemoval()
          .disallowVerticalClassMerging()
          .build();

  // Requires no aspects of a class to be kept.
  private static final KeepClassInfo BOTTOM =
      new Builder()
          .makeBottom()
          .allowClassInlining()
          .allowHorizontalClassMerging()
          .allowPermittedSubclassesRemoval()
          .allowRepackaging()
          .allowUnusedInterfaceRemoval()
          .allowVerticalClassMerging()
          .build();

  public static KeepClassInfo top() {
    return TOP;
  }

  public static KeepClassInfo bottom() {
    return BOTTOM;
  }

  public static Joiner newEmptyJoiner() {
    return bottom().joiner();
  }

  private final boolean allowClassInlining;
  private final boolean allowHorizontalClassMerging;
  private final boolean allowPermittedSubclassesRemoval;
  private final boolean allowRepackaging;
  private final boolean allowSyntheticSharing;
  private final boolean allowUnusedInterfaceRemoval;
  private final boolean allowVerticalClassMerging;
  private final boolean checkEnumUnboxed;

  KeepClassInfo(Builder builder) {
    super(builder);
    this.allowClassInlining = builder.isClassInliningAllowed();
    this.allowHorizontalClassMerging = builder.isHorizontalClassMergingAllowed();
    this.allowPermittedSubclassesRemoval = builder.isPermittedSubclassesRemovalAllowed();
    this.allowRepackaging = builder.isRepackagingAllowed();
    this.allowSyntheticSharing = builder.isSyntheticSharingAllowed();
    this.allowUnusedInterfaceRemoval = builder.isUnusedInterfaceRemovalAllowed();
    this.allowVerticalClassMerging = builder.isVerticalClassMergingAllowed();
    this.checkEnumUnboxed = builder.isCheckEnumUnboxedEnabled();
  }

  @Override
  Builder builder() {
    return new Builder(this);
  }

  public boolean isCheckEnumUnboxedEnabled(GlobalKeepInfoConfiguration configuration) {
    return internalIsCheckEnumUnboxedEnabled();
  }

  boolean internalIsCheckEnumUnboxedEnabled() {
    return checkEnumUnboxed;
  }

  public boolean isClassInliningAllowed(GlobalKeepInfoConfiguration configuration) {
    return isOptimizationAllowed(configuration)
        && isShrinkingAllowed(configuration)
        && internalIsClassInliningAllowed();
  }

  boolean internalIsClassInliningAllowed() {
    return allowClassInlining;
  }

  public boolean isHorizontalClassMergingAllowed(GlobalKeepInfoConfiguration configuration) {
    return isOptimizationAllowed(configuration)
        && isShrinkingAllowed(configuration)
        && internalIsHorizontalClassMergingAllowed();
  }

  boolean internalIsHorizontalClassMergingAllowed() {
    return allowHorizontalClassMerging;
  }

  public boolean isKotlinMetadataRemovalAllowed(
      GlobalKeepInfoConfiguration configuration, boolean kotlinMetadataKept) {
    // TODO(b/323816623): Refactor this predicate so that the PG attribute config does not
    //  overrule keep info and also so that it specifically checks the kotlin metadata annotation.
    return !kotlinMetadataKept
        || !isPinned(configuration)
        || !configuration.isKeepRuntimeVisibleAnnotationsEnabled()
        || (!configuration.isForceProguardCompatibilityEnabled()
            && internalAnnotationsInfo().isBottom());
  }

  public static boolean isKotlinMetadataClassKept(
      DexItemFactory factory,
      InternalOptions options,
      Function<DexType, DexClass> definitionForWithoutExistenceAssert,
      Function<DexProgramClass, KeepClassInfo> getClassInfo) {
    DexType kotlinMetadataType = factory.kotlinMetadataType;
    DexClass kotlinMetadataClass = definitionForWithoutExistenceAssert.apply(kotlinMetadataType);
    return kotlinMetadataClass == null
        || kotlinMetadataClass.isNotProgramClass()
        || !getClassInfo.apply(kotlinMetadataClass.asProgramClass()).isShrinkingAllowed(options);
  }

  public boolean isPermittedSubclassesRemovalAllowed(GlobalKeepInfoConfiguration configuration) {
    return !configuration.isKeepPermittedSubclassesEnabled()
        && internalIsPermittedSubclassesRemovalAllowed();
  }

  boolean internalIsPermittedSubclassesRemovalAllowed() {
    return allowPermittedSubclassesRemoval;
  }

  /**
   * True if an item may be repackaged.
   *
   * <p>This method requires knowledge of the global configuration as that can override the concrete
   * value on a given item.
   */
  public boolean isRepackagingAllowed(GlobalKeepInfoConfiguration configuration) {
    return configuration.isRepackagingEnabled() && internalIsRepackagingAllowed();
  }

  boolean internalIsRepackagingAllowed() {
    return allowRepackaging;
  }

  public boolean isSyntheticSharingAllowed() {
    return internalIsSyntheticSharingAllowed();
  }

  private boolean internalIsSyntheticSharingAllowed() {
    return allowSyntheticSharing;
  }

  boolean internalIsUnusedInterfaceRemovalAllowed() {
    return allowUnusedInterfaceRemoval;
  }

  public boolean isVerticalClassMergingAllowed(GlobalKeepInfoConfiguration configuration) {
    return isOptimizationAllowed(configuration)
        && isShrinkingAllowed(configuration)
        && internalIsVerticalClassMergingAllowed();
  }

  boolean internalIsVerticalClassMergingAllowed() {
    return allowVerticalClassMerging;
  }

  public Joiner joiner() {
    assert !isTop();
    return new Joiner(this);
  }

  @Override
  public boolean isTop() {
    return this.equals(top());
  }

  @Override
  public boolean isBottom() {
    return this.equals(bottom());
  }

  @Override
  public boolean equalsNoAnnotations(KeepClassInfo other) {
    return super.equalsNoAnnotations(other)
        && allowClassInlining == other.internalIsClassInliningAllowed()
        && allowHorizontalClassMerging == other.internalIsHorizontalClassMergingAllowed()
        && allowPermittedSubclassesRemoval == other.internalIsPermittedSubclassesRemovalAllowed()
        && allowRepackaging == other.internalIsRepackagingAllowed()
        && allowSyntheticSharing == other.internalIsSyntheticSharingAllowed()
        && allowUnusedInterfaceRemoval == other.internalIsUnusedInterfaceRemovalAllowed()
        && allowVerticalClassMerging == other.internalIsVerticalClassMergingAllowed()
        && checkEnumUnboxed == other.internalIsCheckEnumUnboxedEnabled();
  }

  @Override
  public int hashCodeNoAnnotations() {
    int hash = super.hashCodeNoAnnotations();
    int index = super.numberOfBooleans();
    hash += bit(allowClassInlining, index++);
    hash += bit(allowHorizontalClassMerging, index++);
    hash += bit(allowPermittedSubclassesRemoval, index++);
    hash += bit(allowRepackaging, index++);
    hash += bit(allowSyntheticSharing, index++);
    hash += bit(allowUnusedInterfaceRemoval, index++);
    hash += bit(allowVerticalClassMerging, index++);
    hash += bit(checkEnumUnboxed, index);
    return hash;
  }

  @Override
  public List<String> lines() {
    List<String> lines = super.lines();
    lines.add("allowClassInlining: " + allowClassInlining);
    lines.add("allowHorizontalClassMerging: " + allowHorizontalClassMerging);
    lines.add("allowPermittedSubclassesRemoval: " + allowPermittedSubclassesRemoval);
    lines.add("allowRepackaging: " + allowRepackaging);
    lines.add("allowSyntheticSharing: " + allowSyntheticSharing);
    lines.add("allowUnusedInterfaceRemoval: " + allowUnusedInterfaceRemoval);
    lines.add("allowVerticalClassMerging: " + allowVerticalClassMerging);
    lines.add("checkEnumUnboxed: " + checkEnumUnboxed);
    return lines;
  }

  public static class Builder extends KeepInfo.Builder<Builder, KeepClassInfo> {

    private boolean allowClassInlining;
    private boolean allowHorizontalClassMerging;
    private boolean allowPermittedSubclassesRemoval;
    private boolean allowRepackaging;
    private boolean allowSyntheticSharing;
    private boolean allowUnusedInterfaceRemoval;
    private boolean allowVerticalClassMerging;
    private boolean checkEnumUnboxed;

    Builder() {
      super();
    }

    Builder(KeepClassInfo original) {
      super(original);
      allowClassInlining = original.internalIsClassInliningAllowed();
      allowHorizontalClassMerging = original.internalIsHorizontalClassMergingAllowed();
      allowPermittedSubclassesRemoval = original.internalIsPermittedSubclassesRemovalAllowed();
      allowRepackaging = original.internalIsRepackagingAllowed();
      allowSyntheticSharing = original.internalIsSyntheticSharingAllowed();
      allowUnusedInterfaceRemoval = original.internalIsUnusedInterfaceRemovalAllowed();
      allowVerticalClassMerging = original.internalIsVerticalClassMergingAllowed();
      checkEnumUnboxed = original.internalIsCheckEnumUnboxedEnabled();
    }

    // Check enum unboxed.

    public boolean isCheckEnumUnboxedEnabled() {
      return checkEnumUnboxed;
    }

    public Builder setCheckEnumUnboxed(boolean checkEnumUnboxed) {
      this.checkEnumUnboxed = checkEnumUnboxed;
      return self();
    }

    public Builder setCheckEnumUnboxed() {
      return setCheckEnumUnboxed(true);
    }

    public Builder unsetCheckEnumUnboxed() {
      return setCheckEnumUnboxed(false);
    }

    // Class inlining.

    public Builder allowClassInlining() {
      return setAllowClassInlining(true);
    }

    public boolean isClassInliningAllowed() {
      return allowClassInlining;
    }

    private Builder setAllowClassInlining(boolean allowClassInlining) {
      this.allowClassInlining = allowClassInlining;
      return self();
    }

    public Builder disallowClassInlining() {
      return setAllowClassInlining(false);
    }

    // Horizontal class merging.

    public Builder allowHorizontalClassMerging() {
      return setAllowHorizontalClassMerging(true);
    }

    public boolean isHorizontalClassMergingAllowed() {
      return allowHorizontalClassMerging;
    }

    private Builder setAllowHorizontalClassMerging(boolean allowHorizontalClassMerging) {
      this.allowHorizontalClassMerging = allowHorizontalClassMerging;
      return self();
    }

    public Builder disallowHorizontalClassMerging() {
      return setAllowHorizontalClassMerging(false);
    }

    // Permitted subclasses removal.

    public Builder allowPermittedSubclassesRemoval() {
      return setAllowPermittedSubclassesRemoval(true);
    }

    public boolean isPermittedSubclassesRemovalAllowed() {
      return allowPermittedSubclassesRemoval;
    }

    private Builder setAllowPermittedSubclassesRemoval(boolean allowPermittedSubclassesRemoval) {
      this.allowPermittedSubclassesRemoval = allowPermittedSubclassesRemoval;
      return self();
    }

    public Builder disallowPermittedSubclassesRemoval() {
      return setAllowPermittedSubclassesRemoval(false);
    }

    // Repackaging.

    public boolean isRepackagingAllowed() {
      return allowRepackaging;
    }

    private Builder setAllowRepackaging(boolean allowRepackaging) {
      this.allowRepackaging = allowRepackaging;
      return self();
    }

    public Builder allowRepackaging() {
      return setAllowRepackaging(true);
    }

    public Builder disallowRepackaging() {
      return setAllowRepackaging(false);
    }

    // Synthetic sharing.

    public boolean isSyntheticSharingAllowed() {
      return allowSyntheticSharing;
    }

    private Builder setAllowSyntheticSharing(boolean allowSyntheticSharing) {
      this.allowSyntheticSharing = allowSyntheticSharing;
      return self();
    }

    // Unused interface removal.

    public Builder allowUnusedInterfaceRemoval() {
      return setAllowUnusedInterfaceRemoval(true);
    }

    public boolean isUnusedInterfaceRemovalAllowed() {
      return allowUnusedInterfaceRemoval;
    }

    private Builder setAllowUnusedInterfaceRemoval(boolean allowUnusedInterfaceRemoval) {
      this.allowUnusedInterfaceRemoval = allowUnusedInterfaceRemoval;
      return self();
    }

    public Builder disallowUnusedInterfaceRemoval() {
      return setAllowUnusedInterfaceRemoval(false);
    }

    // Vertical class merging.

    public Builder allowVerticalClassMerging() {
      return setAllowVerticalClassMerging(true);
    }

    public boolean isVerticalClassMergingAllowed() {
      return allowVerticalClassMerging;
    }

    private Builder setAllowVerticalClassMerging(boolean allowVerticalClassMerging) {
      this.allowVerticalClassMerging = allowVerticalClassMerging;
      return self();
    }

    public Builder disallowVerticalClassMerging() {
      return setAllowVerticalClassMerging(false);
    }

    @Override
    public KeepClassInfo getTopInfo() {
      return TOP;
    }

    @Override
    public KeepClassInfo getBottomInfo() {
      return BOTTOM;
    }

    @Override
    public Builder self() {
      return this;
    }

    @Override
    public boolean isEqualTo(KeepClassInfo other) {
      return internalIsEqualTo(other);
    }

    @Override
    boolean internalIsEqualTo(KeepClassInfo other) {
      return super.internalIsEqualTo(other)
          && isCheckEnumUnboxedEnabled() == other.internalIsCheckEnumUnboxedEnabled()
          && isClassInliningAllowed() == other.internalIsClassInliningAllowed()
          && isHorizontalClassMergingAllowed() == other.internalIsHorizontalClassMergingAllowed()
          && isPermittedSubclassesRemovalAllowed()
              == other.internalIsPermittedSubclassesRemovalAllowed()
          && isRepackagingAllowed() == other.internalIsRepackagingAllowed()
          && isSyntheticSharingAllowed() == other.internalIsSyntheticSharingAllowed()
          && isUnusedInterfaceRemovalAllowed() == other.internalIsUnusedInterfaceRemovalAllowed()
          && isVerticalClassMergingAllowed() == other.internalIsVerticalClassMergingAllowed();
    }

    @Override
    public KeepClassInfo doBuild() {
      return new KeepClassInfo(this);
    }

    @Override
    public Builder makeTop() {
      return super.makeTop()
          .disallowClassInlining()
          .disallowHorizontalClassMerging()
          .disallowPermittedSubclassesRemoval()
          .disallowRepackaging()
          // Synthetic sharing is always allowed, unless explicitly set to false.
          .setAllowSyntheticSharing(true)
          .disallowUnusedInterfaceRemoval()
          .disallowVerticalClassMerging()
          .unsetCheckEnumUnboxed();
    }

    @Override
    public Builder makeBottom() {
      return super.makeBottom()
          .allowClassInlining()
          .allowHorizontalClassMerging()
          .allowPermittedSubclassesRemoval()
          .allowRepackaging()
          .setAllowSyntheticSharing(true)
          .allowUnusedInterfaceRemoval()
          .allowVerticalClassMerging()
          .unsetCheckEnumUnboxed();
    }
  }

  public static class Joiner extends KeepInfo.Joiner<Joiner, Builder, KeepClassInfo> {

    public Joiner(KeepClassInfo info) {
      super(info.builder());
    }

    protected Joiner(KeepClassInfo.Builder builder) {
      super(builder);
    }

    public Joiner disallowClassInlining() {
      builder.disallowClassInlining();
      return self();
    }

    public Joiner disallowHorizontalClassMerging() {
      builder.disallowHorizontalClassMerging();
      return self();
    }

    public Joiner disallowPermittedSubclassesRemoval() {
      builder.disallowPermittedSubclassesRemoval();
      return self();
    }

    public Joiner disallowRepackaging() {
      builder.disallowRepackaging();
      return self();
    }

    public Joiner disallowSyntheticSharing() {
      builder.setAllowSyntheticSharing(false);
      return self();
    }

    public Joiner disallowUnusedInterfaceRemoval() {
      builder.disallowUnusedInterfaceRemoval();
      return self();
    }

    public Joiner disallowVerticalClassMerging() {
      builder.disallowVerticalClassMerging();
      return self();
    }

    public boolean isUnusedInterfaceRemovalAllowed() {
      return builder.isUnusedInterfaceRemovalAllowed();
    }

    public Joiner setCheckEnumUnboxed() {
      builder.setCheckEnumUnboxed();
      return self();
    }

    @Override
    public Joiner asClassJoiner() {
      return this;
    }

    @Override
    public Joiner merge(Joiner joiner) {
      // Should be extended to merge the fields of this class in case any are added.
      return super.merge(joiner)
          .applyIf(joiner.builder.isCheckEnumUnboxedEnabled(), Joiner::setCheckEnumUnboxed)
          .applyIf(!joiner.builder.isClassInliningAllowed(), Joiner::disallowClassInlining)
          .applyIf(
              !joiner.builder.isHorizontalClassMergingAllowed(),
              Joiner::disallowHorizontalClassMerging)
          .applyIf(
              !joiner.builder.isPermittedSubclassesRemovalAllowed(),
              Joiner::disallowPermittedSubclassesRemoval)
          .applyIf(!joiner.builder.isRepackagingAllowed(), Joiner::disallowRepackaging)
          .applyIf(!joiner.builder.isSyntheticSharingAllowed(), Joiner::disallowSyntheticSharing)
          .applyIf(
              !joiner.builder.isUnusedInterfaceRemovalAllowed(),
              Joiner::disallowUnusedInterfaceRemoval)
          .applyIf(
              !joiner.builder.isVerticalClassMergingAllowed(),
              Joiner::disallowVerticalClassMerging);
    }

    @Override
    Joiner self() {
      return this;
    }
  }
}
