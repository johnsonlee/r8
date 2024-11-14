// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexAnnotation;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.EnclosingMethodAttribute;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.shaking.KeepAnnotationCollectionInfo.RetentionInfo;
import com.android.tools.r8.shaking.KeepInfo.Builder;
import com.android.tools.r8.shaking.KeepReason.ReflectiveUseFrom;
import com.android.tools.r8.utils.InternalOptions;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** Keep information that can be associated with any item, i.e., class, method or field. */
public abstract class KeepInfo<B extends Builder<B, K>, K extends KeepInfo<B, K>> {

  private final boolean allowAccessModification;
  private final boolean allowAccessModificationForTesting;
  private final boolean allowMinification;
  private final boolean allowOptimization;
  private final boolean allowShrinking;
  private final boolean allowSignatureRemoval;
  private final boolean checkDiscarded;
  private final KeepAnnotationCollectionInfo annotationsInfo;
  private final KeepAnnotationCollectionInfo typeAnnotationsInfo;

  private KeepInfo(
      boolean allowAccessModification,
      boolean allowAccessModificationForTesting,
      boolean allowMinification,
      boolean allowOptimization,
      boolean allowShrinking,
      boolean allowSignatureRemoval,
      boolean checkDiscarded,
      KeepAnnotationCollectionInfo annotationsInfo,
      KeepAnnotationCollectionInfo typeAnnotationsInfo) {
    this.allowAccessModification = allowAccessModification;
    this.allowAccessModificationForTesting = allowAccessModificationForTesting;
    this.allowMinification = allowMinification;
    this.allowOptimization = allowOptimization;
    this.allowShrinking = allowShrinking;
    this.allowSignatureRemoval = allowSignatureRemoval;
    this.checkDiscarded = checkDiscarded;
    this.annotationsInfo = annotationsInfo;
    this.typeAnnotationsInfo = typeAnnotationsInfo;
  }

  KeepInfo(B builder) {
    this(
        builder.isAccessModificationAllowed(),
        builder.isAccessModificationAllowedForTesting(),
        builder.isMinificationAllowed(),
        builder.isOptimizationAllowed(),
        builder.isShrinkingAllowed(),
        builder.isSignatureRemovalAllowed(),
        builder.isCheckDiscardedEnabled(),
        builder.getAnnotationsInfo().build(),
        builder.getTypeAnnotationsInfo().build());
  }

  public static Joiner<?, ?, ?> newEmptyJoinerFor(DexReference reference) {
    return reference.apply(
        clazz -> KeepClassInfo.newEmptyJoiner(),
        field -> KeepFieldInfo.newEmptyJoiner(),
        method -> KeepMethodInfo.newEmptyJoiner());
  }

  abstract B builder();

  public KeepMethodInfo asMethodInfo() {
    return null;
  }

  static boolean internalIsAnnotationRemovalAllowed(
      GlobalKeepInfoConfiguration configuration,
      DexAnnotation annotation,
      boolean isAnnotationTypeLive,
      KeepAnnotationCollectionInfo keepAnnotationInfo,
      boolean compatKeepVisible,
      boolean compatKeepInvisible) {
    // In all cases the annotation type must be live for references to it to be kept.
    if (!isAnnotationTypeLive) {
      return true;
    }
    // If the keep info specifies the item as keeping its annotations then it must be kept.
    if (!keepAnnotationInfo.isRemovalAllowed(annotation)) {
      return false;
    }
    // In compatibility mode, annotations are globally kept if live and the attribute is kept.
    if (configuration.isForceProguardCompatibilityEnabled()) {
      if (annotation.getVisibility() == DexAnnotation.VISIBILITY_RUNTIME) {
        return !compatKeepVisible;
      }
      if (annotation.getVisibility() == DexAnnotation.VISIBILITY_BUILD) {
        return !compatKeepInvisible;
      }
    }
    return true;
  }

  public boolean isAnnotationRemovalAllowed(
      GlobalKeepInfoConfiguration configuration,
      DexAnnotation annotation,
      boolean isAnnotationTypeLive) {
    return internalIsAnnotationRemovalAllowed(
        configuration,
        annotation,
        isAnnotationTypeLive,
        internalAnnotationsInfo(),
        configuration.isKeepRuntimeVisibleAnnotationsEnabled(),
        configuration.isKeepRuntimeInvisibleAnnotationsEnabled());
  }

  public boolean isTypeAnnotationRemovalAllowed(
      GlobalKeepInfoConfiguration configuration,
      DexAnnotation annotation,
      boolean isAnnotationTypeLive) {
    return internalIsAnnotationRemovalAllowed(
        configuration,
        annotation,
        isAnnotationTypeLive,
        internalTypeAnnotationsInfo(),
        configuration.isKeepRuntimeVisibleTypeAnnotationsEnabled(),
        configuration.isKeepRuntimeInvisibleTypeAnnotationsEnabled());
  }

  KeepAnnotationCollectionInfo internalAnnotationsInfo() {
    return annotationsInfo;
  }

  KeepAnnotationCollectionInfo internalTypeAnnotationsInfo() {
    return typeAnnotationsInfo;
  }

  public boolean isCheckDiscardedEnabled(GlobalKeepInfoConfiguration configuration) {
    return internalIsCheckDiscardedEnabled();
  }

  boolean internalIsCheckDiscardedEnabled() {
    return checkDiscarded;
  }

  /**
   * True if an item must be present in the output.
   *
   * @deprecated Prefer task dependent predicates.
   */
  @Deprecated
  public boolean isPinned(GlobalKeepInfoConfiguration configuration) {
    return !isOptimizationAllowed(configuration) || !isShrinkingAllowed(configuration);
  }

  public boolean isPinned(GlobalKeepInfoConfiguration configuration, ProgramMethod method) {
    if (method.getOptimizationInfo().shouldSingleCallerInlineIntoSyntheticLambdaAccessor()) {
      return false;
    }
    return isPinned(configuration);
  }

  /**
   * True if an item may have its name minified/changed.
   *
   * <p>This method requires knowledge of the global configuration as that can override the concrete
   * value on a given item.
   */
  public boolean isMinificationAllowed(GlobalKeepInfoConfiguration configuration) {
    return configuration.isMinificationEnabled() && internalIsMinificationAllowed();
  }

  boolean internalIsMinificationAllowed() {
    return allowMinification;
  }

  /**
   * True if an item may be optimized (i.e., the item is not soft pinned).
   *
   * <p>This method requires knowledge of the global configuration as that can override the concrete
   * value on a given item.
   */
  public boolean isOptimizationAllowed(GlobalKeepInfoConfiguration configuration) {
    return configuration.isOptimizationEnabled() && internalIsOptimizationAllowed();
  }

  boolean internalIsOptimizationAllowed() {
    return allowOptimization;
  }

  /**
   * True if an item is subject to shrinking (i.e., tree shaking).
   *
   * <p>This method requires knowledge of the global configuration as that can override the concrete
   * value on a given item.
   */
  public boolean isShrinkingAllowed(GlobalKeepInfoConfiguration configuration) {
    return configuration.isTreeShakingEnabled() && internalIsShrinkingAllowed();
  }

  boolean internalIsShrinkingAllowed() {
    return allowShrinking;
  }

  /**
   * True if an item may have its generic signature removed.
   *
   * <p>This method requires knowledge of the global configuration as that can override the concrete
   * value on a given item.
   */
  public boolean isSignatureRemovalAllowed(GlobalKeepInfoConfiguration configuration) {
    return !configuration.isForceKeepSignatureAttributeEnabled()
        && internalIsSignatureRemovalAllowed();
  }

  boolean internalIsSignatureRemovalAllowed() {
    return allowSignatureRemoval;
  }

  /**
   * True if an item may have its access flags modified.
   *
   * <p>This method requires knowledge of the global access modification as that will override the
   * concrete value on a given item.
   *
   * @param configuration Global configuration object to determine access modification.
   */
  public boolean isAccessModificationAllowed(GlobalKeepInfoConfiguration configuration) {
    return configuration.isAccessModificationEnabled() && internalIsAccessModificationAllowed();
  }

  // Internal accessor for the items access-modification bit.
  boolean internalIsAccessModificationAllowed() {
    return allowAccessModification;
  }

  public boolean isAccessModificationAllowedForTesting(GlobalKeepInfoConfiguration configuration) {
    return internalIsAccessModificationAllowedForTesting();
  }

  // Internal accessor for the items access-modification bit.
  boolean internalIsAccessModificationAllowedForTesting() {
    return allowAccessModificationForTesting;
  }

  public boolean isEnclosingMethodAttributeRemovalAllowed(
      GlobalKeepInfoConfiguration configuration,
      EnclosingMethodAttribute enclosingMethodAttribute,
      AppView<AppInfoWithLiveness> appView) {
    if (!configuration.isKeepEnclosingMethodAttributeEnabled()) {
      return true;
    }
    if (configuration.isForceProguardCompatibilityEnabled()) {
      return false;
    }
    return !isPinned(configuration) || !enclosingMethodAttribute.isEnclosingPinned(appView);
  }

  public boolean isInnerClassesAttributeRemovalAllowed(GlobalKeepInfoConfiguration configuration) {
    if (!configuration.isKeepInnerClassesAttributeEnabled()) {
      return true;
    }
    return !(configuration.isForceProguardCompatibilityEnabled() || isPinned(configuration));
  }

  public boolean isInnerClassesAttributeRemovalAllowed(
      GlobalKeepInfoConfiguration configuration,
      EnclosingMethodAttribute enclosingMethodAttribute) {
    if (!configuration.isKeepInnerClassesAttributeEnabled()) {
      return true;
    }
    if (configuration.isForceProguardCompatibilityEnabled()) {
      return false;
    }
    // The inner class is dependent on the enclosingMethodAttribute and since it has been pruned
    // we can also remove this inner class relationship.
    return enclosingMethodAttribute == null || !isPinned(configuration);
  }

  public abstract boolean isTop();

  public abstract boolean isBottom();

  public boolean isLessThanOrEquals(K other) {
    // An item is less, aka, lower in the lattice, if each of its attributes is at least as
    // permissive of that on other.
    return (allowAccessModification || !other.internalIsAccessModificationAllowed())
        && (allowAccessModificationForTesting
            || !other.internalIsAccessModificationAllowedForTesting())
        && (allowMinification || !other.internalIsMinificationAllowed())
        && (allowOptimization || !other.internalIsOptimizationAllowed())
        && (allowShrinking || !other.internalIsShrinkingAllowed())
        && (allowSignatureRemoval || !other.internalIsSignatureRemovalAllowed())
        && (!checkDiscarded || other.internalIsCheckDiscardedEnabled())
        && annotationsInfo.isLessThanOrEqualTo(other.internalAnnotationsInfo())
        && typeAnnotationsInfo.isLessThanOrEqualTo(other.internalTypeAnnotationsInfo());
  }

  public boolean equalsNoAnnotations(K other) {
    assert annotationsInfo.isTopOrBottom();
    assert typeAnnotationsInfo.isTopOrBottom();
    return getClass() == other.getClass()
        && (allowAccessModification == other.internalIsAccessModificationAllowed())
        && (allowAccessModificationForTesting
            == other.internalIsAccessModificationAllowedForTesting())
        && (allowMinification == other.internalIsMinificationAllowed())
        && (allowOptimization == other.internalIsOptimizationAllowed())
        && (allowShrinking == other.internalIsShrinkingAllowed())
        && (allowSignatureRemoval == other.internalIsSignatureRemovalAllowed())
        && (checkDiscarded == other.internalIsCheckDiscardedEnabled())
        && (annotationsInfo == other.internalAnnotationsInfo())
        && (typeAnnotationsInfo == other.internalTypeAnnotationsInfo());
  }

  public int hashCodeNoAnnotations() {
    assert annotationsInfo.isTopOrBottom();
    assert typeAnnotationsInfo.isTopOrBottom();
    int hash = 0;
    int index = 0;
    hash += bit(allowAccessModification, index++);
    hash += bit(allowAccessModificationForTesting, index++);
    hash += bit(allowMinification, index++);
    hash += bit(allowOptimization, index++);
    hash += bit(allowShrinking, index++);
    hash += bit(allowSignatureRemoval, index++);
    hash += bit(checkDiscarded, index++);
    hash += bit(annotationsInfo.isTop(), index++);
    hash += bit(typeAnnotationsInfo.isTop(), index);
    return hash;
  }

  public List<String> lines() {
    List<String> lines = new ArrayList<>();
    lines.add("allowAccessModification: " + allowAccessModification);
    lines.add("allowAccessModificationForTesting: " + allowAccessModificationForTesting);
    lines.add("allowMinification: " + allowMinification);
    lines.add("allowOptimization: " + allowOptimization);
    lines.add("allowShrinking: " + allowShrinking);
    lines.add("allowSignatureRemoval: " + allowSignatureRemoval);
    lines.add("checkDiscarded: " + checkDiscarded);
    lines.add("annotationsInfo: " + annotationsInfo);
    lines.add("typeAnnotationsInfo: " + typeAnnotationsInfo);
    return lines;
  }

  protected int numberOfBooleans() {
    return 9;
  }

  protected int bit(boolean bool, int index) {
    return bool ? 1 << index : 0;
  }

  /** Builder to construct an arbitrary keep info object. */
  public abstract static class Builder<B extends Builder<B, K>, K extends KeepInfo<B, K>> {

    abstract B self();

    abstract K doBuild();

    abstract K getTopInfo();

    abstract K getBottomInfo();

    abstract boolean isEqualTo(K other);

    protected K original;
    private boolean allowAccessModification;
    private boolean allowAccessModificationForTesting;
    private boolean allowMinification;
    private boolean allowOptimization;
    private boolean allowShrinking;
    private boolean allowSignatureRemoval;
    private boolean checkDiscarded;
    private KeepAnnotationCollectionInfo.Builder annotationsInfo;
    private KeepAnnotationCollectionInfo.Builder typeAnnotationsInfo;

    Builder() {
      // Default initialized. Use should be followed by makeTop/makeBottom.
    }

    Builder(K original) {
      this.original = original;
      allowAccessModification = original.internalIsAccessModificationAllowed();
      allowAccessModificationForTesting = original.internalIsAccessModificationAllowedForTesting();
      allowMinification = original.internalIsMinificationAllowed();
      allowOptimization = original.internalIsOptimizationAllowed();
      allowShrinking = original.internalIsShrinkingAllowed();
      allowSignatureRemoval = original.internalIsSignatureRemovalAllowed();
      checkDiscarded = original.internalIsCheckDiscardedEnabled();
      annotationsInfo = original.internalAnnotationsInfo().toBuilder();
      typeAnnotationsInfo = original.internalTypeAnnotationsInfo().toBuilder();
    }

    B makeTop() {
      setAllowAccessModification(false);
      setAllowAccessModificationForTesting(false);
      setAnnotationInfo(KeepAnnotationCollectionInfo.Builder.createTop());
      setTypeAnnotationsInfo(KeepAnnotationCollectionInfo.Builder.createTop());
      setAllowMinification(false);
      setAllowOptimization(false);
      setAllowShrinking(false);
      setAllowSignatureRemoval(false);
      setCheckDiscarded(false);
      return self();
    }

    B makeBottom() {
      setAllowAccessModification(true);
      setAllowAccessModificationForTesting(true);
      setAnnotationInfo(KeepAnnotationCollectionInfo.Builder.createBottom());
      setTypeAnnotationsInfo(KeepAnnotationCollectionInfo.Builder.createBottom());
      setAllowMinification(true);
      setAllowOptimization(true);
      setAllowShrinking(true);
      setAllowSignatureRemoval(true);
      setCheckDiscarded(false);
      return self();
    }

    public K build() {
      if (original != null) {
        if (internalIsEqualTo(original)) {
          return original;
        }
        if (internalIsEqualTo(getTopInfo())) {
          return getTopInfo();
        }
        if (internalIsEqualTo(getBottomInfo())) {
          return getBottomInfo();
        }
      }
      return doBuild();
    }

    boolean internalIsEqualTo(K other) {
      return isAccessModificationAllowed() == other.internalIsAccessModificationAllowed()
          && isAccessModificationAllowedForTesting()
              == other.internalIsAccessModificationAllowedForTesting()
          && isMinificationAllowed() == other.internalIsMinificationAllowed()
          && isOptimizationAllowed() == other.internalIsOptimizationAllowed()
          && isShrinkingAllowed() == other.internalIsShrinkingAllowed()
          && isSignatureRemovalAllowed() == other.internalIsSignatureRemovalAllowed()
          && isCheckDiscardedEnabled() == other.internalIsCheckDiscardedEnabled()
          && annotationsInfo.isEqualTo(other.internalAnnotationsInfo())
          && typeAnnotationsInfo.isEqualTo(other.internalTypeAnnotationsInfo());
    }

    public boolean isCheckDiscardedEnabled() {
      return checkDiscarded;
    }

    public B setCheckDiscarded(boolean checkDiscarded) {
      this.checkDiscarded = checkDiscarded;
      return self();
    }

    public boolean isMinificationAllowed() {
      return allowMinification;
    }

    public B setAllowMinification(boolean allowMinification) {
      this.allowMinification = allowMinification;
      return self();
    }

    public boolean isOptimizationAllowed() {
      return allowOptimization;
    }

    public B setAllowOptimization(boolean allowOptimization) {
      this.allowOptimization = allowOptimization;
      return self();
    }

    public boolean isShrinkingAllowed() {
      return allowShrinking;
    }

    public B setAllowShrinking(boolean allowShrinking) {
      this.allowShrinking = allowShrinking;
      return self();
    }

    public boolean isAccessModificationAllowed() {
      return allowAccessModification;
    }

    public B setAllowAccessModification(boolean allowAccessModification) {
      this.allowAccessModification = allowAccessModification;
      return self();
    }

    public boolean isAccessModificationAllowedForTesting() {
      return allowAccessModificationForTesting;
    }

    public B setAllowAccessModificationForTesting(boolean allowAccessModificationForTesting) {
      this.allowAccessModificationForTesting = allowAccessModificationForTesting;
      return self();
    }

    public KeepAnnotationCollectionInfo.Builder getAnnotationsInfo() {
      return annotationsInfo;
    }

    public B setAnnotationInfo(KeepAnnotationCollectionInfo.Builder infoBuilder) {
      annotationsInfo = infoBuilder;
      return self();
    }

    public KeepAnnotationCollectionInfo.Builder getTypeAnnotationsInfo() {
      return typeAnnotationsInfo;
    }

    public B setTypeAnnotationsInfo(KeepAnnotationCollectionInfo.Builder infoBuilder) {
      typeAnnotationsInfo = infoBuilder;
      return self();
    }

    public boolean isSignatureRemovalAllowed() {
      return allowSignatureRemoval;
    }

    public B setAllowSignatureRemoval(boolean allowSignatureRemoval) {
      this.allowSignatureRemoval = allowSignatureRemoval;
      return self();
    }
  }

  /** Joiner to construct monotonically increasing keep info object. */
  public abstract static class Joiner<
      J extends Joiner<J, B, K>, B extends Builder<B, K>, K extends KeepInfo<B, K>> {

    abstract J self();

    final B builder;

    /**
     * The set of reasons and rules that have contributed to setting {@link Builder#allowShrinking}
     * to false on this joiner. These are needed to report the correct -whyareyoukeeping reasons for
     * rooted items.
     *
     * <p>An item should only have allowShrinking set to false if it is kept by a -keep rule or the
     * {@link Enqueuer} detects a reflective access to the item (hence the {@link
     * Set<ReflectiveUseFrom>}).
     *
     * <p>These are only needed for the interpretation of keep rules into keep info, and is
     * therefore not stored in the keep info builder above.
     */
    final Set<ReflectiveUseFrom> reasons = new HashSet<>();

    final Set<ProguardKeepRuleBase> rules = Sets.newIdentityHashSet();

    Joiner(B builder) {
      this.builder = builder;
    }

    public J applyIf(boolean condition, Consumer<J> thenConsumer) {
      if (condition) {
        thenConsumer.accept(self());
      }
      return self();
    }

    public KeepClassInfo.Joiner asClassJoiner() {
      return null;
    }

    public static KeepClassInfo.Joiner asClassJoinerOrNull(Joiner<?, ?, ?> joiner) {
      return joiner != null ? joiner.asClassJoiner() : null;
    }

    public KeepFieldInfo.Joiner asFieldJoiner() {
      return null;
    }

    public static KeepFieldInfo.Joiner asFieldJoinerOrNull(Joiner<?, ?, ?> joiner) {
      return joiner != null ? joiner.asFieldJoiner() : null;
    }

    public KeepMemberInfo.Joiner<?, ?, ?> asMemberJoiner() {
      return null;
    }

    public KeepMethodInfo.Joiner asMethodJoiner() {
      return null;
    }

    public Set<ReflectiveUseFrom> getReasons() {
      return reasons;
    }

    public Set<ProguardKeepRuleBase> getRules() {
      return rules;
    }

    public boolean isBottom() {
      return builder.isEqualTo(builder.getBottomInfo());
    }

    public boolean isCheckDiscardedEnabled() {
      return builder.isCheckDiscardedEnabled();
    }

    public boolean isMinificationAllowed() {
      return builder.isMinificationAllowed();
    }

    public boolean isOptimizationAllowed() {
      return builder.isOptimizationAllowed();
    }

    public boolean isShrinkingAllowed() {
      return builder.isShrinkingAllowed();
    }

    public boolean isTop() {
      return builder.isEqualTo(builder.getTopInfo());
    }

    public J top() {
      builder.makeTop();
      return self();
    }

    public J addReason(ReflectiveUseFrom reason) {
      reasons.add(reason);
      return self();
    }

    public J addRule(ProguardKeepRuleBase rule) {
      rules.add(rule);
      return self();
    }

    public J disallowAccessModification() {
      builder.setAllowAccessModification(false);
      return self();
    }

    public J disallowAccessModificationForTesting() {
      builder.setAllowAccessModificationForTesting(false);
      return self();
    }

    public J disallowAnnotationRemoval(RetentionInfo retention) {
      builder.getAnnotationsInfo().destructiveJoinAnyTypeInfo(retention);
      return self();
    }

    public J disallowAnnotationRemoval(RetentionInfo retention, DexType type) {
      builder.getAnnotationsInfo().destructiveJoinTypeInfo(type, retention);
      return self();
    }

    public J disallowTypeAnnotationRemoval(RetentionInfo retention) {
      builder.getTypeAnnotationsInfo().destructiveJoinAnyTypeInfo(retention);
      return self();
    }

    public J disallowMinification() {
      builder.setAllowMinification(false);
      return self();
    }

    public J disallowOptimization() {
      builder.setAllowOptimization(false);
      return self();
    }

    public J disallowShrinking() {
      builder.setAllowShrinking(false);
      return self();
    }

    public J disallowSignatureRemoval() {
      builder.setAllowSignatureRemoval(false);
      return self();
    }

    public J setCheckDiscarded() {
      builder.setCheckDiscarded(true);
      return self();
    }

    public J merge(J joiner) {
      Builder<B, K> otherBuilder = joiner.builder;
      applyIf(!otherBuilder.isAccessModificationAllowed(), Joiner::disallowAccessModification);
      applyIf(
          !otherBuilder.isAccessModificationAllowedForTesting(),
          Joiner::disallowAccessModificationForTesting);
      applyIf(!otherBuilder.isMinificationAllowed(), Joiner::disallowMinification);
      applyIf(!otherBuilder.isOptimizationAllowed(), Joiner::disallowOptimization);
      applyIf(!otherBuilder.isShrinkingAllowed(), Joiner::disallowShrinking);
      applyIf(!otherBuilder.isSignatureRemovalAllowed(), Joiner::disallowSignatureRemoval);
      applyIf(otherBuilder.isCheckDiscardedEnabled(), Joiner::setCheckDiscarded);
      builder.getAnnotationsInfo().destructiveJoin(otherBuilder.getAnnotationsInfo());
      builder.getTypeAnnotationsInfo().destructiveJoin(otherBuilder.getTypeAnnotationsInfo());
      reasons.addAll(joiner.reasons);
      rules.addAll(joiner.rules);
      return self();
    }

    @SuppressWarnings("unchecked")
    public J mergeUnsafe(Joiner<?, ?, ?> joiner) {
      return merge((J) joiner);
    }

    public K join() {
      K joined = builder.build();
      K original = builder.original;
      assert original.isLessThanOrEquals(joined);
      return joined;
    }

    public boolean verifyShrinkingDisallowedWithRule(InternalOptions options) {
      assert !isShrinkingAllowed();
      assert !getReasons().isEmpty() || !getRules().isEmpty() || !options.isShrinking();
      return true;
    }
  }
}
