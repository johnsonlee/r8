// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import com.android.tools.r8.graph.DexAnnotation;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.shaking.KeepAnnotationCollectionInfo.RetentionInfo;
import java.util.List;

/** Immutable keep requirements for a method. */
public class KeepMethodInfo extends KeepMemberInfo<KeepMethodInfo.Builder, KeepMethodInfo> {

  // Requires all aspects of a method to be kept.
  private static final KeepMethodInfo TOP = new Builder().makeTop().build();

  // Requires no aspects of a method to be kept.
  private static final KeepMethodInfo BOTTOM = new Builder().makeBottom().build();

  public static KeepMethodInfo top() {
    return TOP;
  }

  public static KeepMethodInfo bottom() {
    return BOTTOM;
  }

  public static Joiner newEmptyJoiner() {
    return bottom().joiner();
  }

  private final boolean allowThrowsRemoval;
  private final boolean allowClassInlining;
  private final boolean allowClosedWorldReasoning;
  private final boolean allowCodeReplacement;
  private final boolean allowConstantArgumentOptimization;
  private final boolean allowInlining;
  private final boolean allowMethodStaticizing;
  private final boolean allowParameterRemoval;
  private final boolean allowParameterReordering;
  private final boolean allowParameterTypeStrengthening;
  private final boolean allowReprocessing;
  private final boolean allowReturnTypeStrengthening;
  private final boolean allowSingleCallerInlining;
  private final boolean allowUnusedArgumentOptimization;
  private final boolean allowUnusedReturnValueOptimization;
  private final boolean allowParameterNamesRemoval;
  private final KeepAnnotationCollectionInfo parameterAnnotationsInfo;

  protected KeepMethodInfo(Builder builder) {
    super(builder);
    this.allowThrowsRemoval = builder.isThrowsRemovalAllowed();
    this.allowClassInlining = builder.isClassInliningAllowed();
    this.allowClosedWorldReasoning = builder.isClosedWorldReasoningAllowed();
    this.allowCodeReplacement = builder.isCodeReplacementAllowed();
    this.allowConstantArgumentOptimization = builder.isConstantArgumentOptimizationAllowed();
    this.allowInlining = builder.isInliningAllowed();
    this.allowMethodStaticizing = builder.isMethodStaticizingAllowed();
    this.allowParameterRemoval = builder.isParameterRemovalAllowed();
    this.allowParameterReordering = builder.isParameterReorderingAllowed();
    this.allowParameterTypeStrengthening = builder.isParameterTypeStrengtheningAllowed();
    this.allowReprocessing = builder.isReprocessingAllowed();
    this.allowReturnTypeStrengthening = builder.isReturnTypeStrengtheningAllowed();
    this.allowSingleCallerInlining = builder.isSingleCallerInliningAllowed();
    this.allowUnusedArgumentOptimization = builder.isUnusedArgumentOptimizationAllowed();
    this.allowUnusedReturnValueOptimization = builder.isUnusedReturnValueOptimizationAllowed();
    this.allowParameterNamesRemoval = builder.isParameterNamesRemovalAllowed();
    this.parameterAnnotationsInfo = builder.getParameterAnnotationsInfo().build();
  }

  // This builder is not private as there are known instances where it is safe to modify keep info
  // in a non-upwards direction.
  @Override
  Builder builder() {
    return new Builder(this);
  }

  @Override
  public KeepMethodInfo asMethodInfo() {
    return this;
  }

  public boolean isParameterAnnotationRemovalAllowed(
      GlobalKeepInfoConfiguration configuration,
      DexAnnotation annotation,
      boolean isAnnotationTypeLive) {
    return internalIsAnnotationRemovalAllowed(
        configuration,
        annotation,
        isAnnotationTypeLive,
        internalParameterAnnotationsInfo(),
        configuration.isKeepRuntimeVisibleParameterAnnotationsEnabled(),
        configuration.isKeepRuntimeInvisibleParameterAnnotationsEnabled());
  }

  /**
   * True if an item may have its exception throws clause removed.
   *
   * <p>This method requires knowledge of the global configuration as that can override the concrete
   * value on a given item.
   */
  public boolean isThrowsRemovalAllowed(GlobalKeepInfoConfiguration configuration) {
    return !configuration.isForceKeepExceptionsAttributeEnabled()
        && internalIsThrowsRemovalAllowed();
  }

  boolean internalIsThrowsRemovalAllowed() {
    return allowThrowsRemoval;
  }

  KeepAnnotationCollectionInfo internalParameterAnnotationsInfo() {
    return parameterAnnotationsInfo;
  }

  public boolean isArgumentPropagationAllowed(GlobalKeepInfoConfiguration configuration) {
    return isParameterRemovalAllowed(configuration);
  }

  public boolean isClassInliningAllowed(GlobalKeepInfoConfiguration configuration) {
    return isOptimizationAllowed(configuration) && internalIsClassInliningAllowed();
  }

  boolean internalIsClassInliningAllowed() {
    return allowClassInlining;
  }

  public boolean isClosedWorldReasoningAllowed(GlobalKeepInfoConfiguration configuration) {
    return isOptimizationAllowed(configuration) && internalIsClosedWorldReasoningAllowed();
  }

  boolean internalIsClosedWorldReasoningAllowed() {
    return allowClosedWorldReasoning;
  }

  public boolean isCodeReplacementAllowed(GlobalKeepInfoConfiguration configuration) {
    return configuration.isCodeReplacementForceEnabled()
        ? !isOptimizationAllowed(configuration)
        : internalIsCodeReplacementAllowed();
  }

  boolean internalIsCodeReplacementAllowed() {
    return allowCodeReplacement;
  }

  public boolean isConstantArgumentOptimizationAllowed(GlobalKeepInfoConfiguration configuration) {
    return isOptimizationAllowed(configuration) && internalIsConstantArgumentOptimizationAllowed();
  }

  boolean internalIsConstantArgumentOptimizationAllowed() {
    return allowConstantArgumentOptimization;
  }

  public boolean isInliningAllowed(GlobalKeepInfoConfiguration configuration) {
    return isOptimizationAllowed(configuration) && internalIsInliningAllowed();
  }

  boolean internalIsInliningAllowed() {
    return allowInlining;
  }

  public boolean isMethodStaticizingAllowed(GlobalKeepInfoConfiguration configuration) {
    return isClosedWorldReasoningAllowed(configuration)
        && isOptimizationAllowed(configuration)
        && isShrinkingAllowed(configuration)
        && configuration.isMethodStaticizingEnabled()
        && internalIsMethodStaticizingAllowed();
  }

  boolean internalIsMethodStaticizingAllowed() {
    return allowMethodStaticizing;
  }

  public boolean isParameterRemovalAllowed(GlobalKeepInfoConfiguration configuration) {
    return isClosedWorldReasoningAllowed(configuration)
        && isOptimizationAllowed(configuration)
        && isShrinkingAllowed(configuration)
        && !isCheckDiscardedEnabled(configuration)
        && internalIsParameterRemovalAllowed();
  }

  boolean internalIsParameterRemovalAllowed() {
    return allowParameterRemoval;
  }

  public boolean isParameterReorderingAllowed(GlobalKeepInfoConfiguration configuration) {
    return isClosedWorldReasoningAllowed(configuration)
        && isOptimizationAllowed(configuration)
        && isShrinkingAllowed(configuration)
        && internalIsParameterReorderingAllowed();
  }

  boolean internalIsParameterReorderingAllowed() {
    return allowParameterReordering;
  }

  public boolean isParameterTypeStrengtheningAllowed(GlobalKeepInfoConfiguration configuration) {
    return isClosedWorldReasoningAllowed(configuration)
        && isOptimizationAllowed(configuration)
        && isShrinkingAllowed(configuration)
        && internalIsParameterTypeStrengtheningAllowed();
  }

  boolean internalIsParameterTypeStrengtheningAllowed() {
    return allowParameterTypeStrengthening;
  }

  public boolean isReprocessingAllowed(
      GlobalKeepInfoConfiguration configuration, ProgramMethod method) {
    return !method.getOptimizationInfo().hasBeenInlinedIntoSingleCallSite()
        && internalIsReprocessingAllowed();
  }

  boolean internalIsReprocessingAllowed() {
    return allowReprocessing;
  }

  public boolean isReturnTypeStrengtheningAllowed(GlobalKeepInfoConfiguration configuration) {
    return isClosedWorldReasoningAllowed(configuration)
        && isOptimizationAllowed(configuration)
        && isShrinkingAllowed(configuration)
        && internalIsReturnTypeStrengtheningAllowed();
  }

  boolean internalIsReturnTypeStrengtheningAllowed() {
    return allowReturnTypeStrengthening;
  }

  public boolean isSingleCallerInliningAllowed(GlobalKeepInfoConfiguration configuration) {
    return isOptimizationAllowed(configuration)
        && isShrinkingAllowed(configuration)
        && internalIsSingleCallerInliningAllowed();
  }

  boolean internalIsSingleCallerInliningAllowed() {
    return allowSingleCallerInlining;
  }

  public boolean isUnusedArgumentOptimizationAllowed(GlobalKeepInfoConfiguration configuration) {
    return isClosedWorldReasoningAllowed(configuration)
        && isOptimizationAllowed(configuration)
        && isShrinkingAllowed(configuration)
        && internalIsUnusedArgumentOptimizationAllowed();
  }

  boolean internalIsUnusedArgumentOptimizationAllowed() {
    return allowUnusedArgumentOptimization;
  }

  public boolean isUnusedReturnValueOptimizationAllowed(GlobalKeepInfoConfiguration configuration) {
    return isClosedWorldReasoningAllowed(configuration)
        && isOptimizationAllowed(configuration)
        && isShrinkingAllowed(configuration)
        && internalIsUnusedReturnValueOptimizationAllowed();
  }

  boolean internalIsUnusedReturnValueOptimizationAllowed() {
    return allowUnusedReturnValueOptimization;
  }

  public boolean isParameterNamesRemovalAllowed(GlobalKeepInfoConfiguration configuration) {
    return !configuration.isForceKeepMethodParametersAttributeEnabled()
        && internalIsParameterNamesRemovalAllowed();
  }

  boolean internalIsParameterNamesRemovalAllowed() {
    return allowParameterNamesRemoval;
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
  public boolean equalsNoAnnotations(KeepMethodInfo other) {
    assert parameterAnnotationsInfo.isTopOrBottom();
    return super.equalsNoAnnotations(other)
        && allowThrowsRemoval == other.internalIsThrowsRemovalAllowed()
        && allowClassInlining == other.internalIsClassInliningAllowed()
        && allowClosedWorldReasoning == other.internalIsClosedWorldReasoningAllowed()
        && allowCodeReplacement == other.internalIsCodeReplacementAllowed()
        && allowConstantArgumentOptimization
            == other.internalIsConstantArgumentOptimizationAllowed()
        && allowInlining == other.internalIsInliningAllowed()
        && allowMethodStaticizing == other.internalIsMethodStaticizingAllowed()
        && allowParameterRemoval == other.internalIsParameterRemovalAllowed()
        && allowParameterReordering == other.internalIsParameterReorderingAllowed()
        && allowParameterTypeStrengthening == other.internalIsParameterTypeStrengtheningAllowed()
        && allowReprocessing == other.internalIsReprocessingAllowed()
        && allowReturnTypeStrengthening == other.internalIsReturnTypeStrengtheningAllowed()
        && allowSingleCallerInlining == other.internalIsSingleCallerInliningAllowed()
        && allowUnusedArgumentOptimization == other.internalIsUnusedArgumentOptimizationAllowed()
        && allowUnusedReturnValueOptimization
            == other.internalIsUnusedReturnValueOptimizationAllowed()
        && allowParameterNamesRemoval == other.internalIsParameterNamesRemovalAllowed()
        && parameterAnnotationsInfo == other.internalParameterAnnotationsInfo();
  }

  @Override
  public int hashCodeNoAnnotations() {
    assert parameterAnnotationsInfo.isTopOrBottom();
    int hash = super.hashCodeNoAnnotations();
    int index = super.numberOfBooleans();
    hash += bit(allowThrowsRemoval, index++);
    hash += bit(allowClassInlining, index++);
    hash += bit(allowClosedWorldReasoning, index++);
    hash += bit(allowCodeReplacement, index++);
    hash += bit(allowConstantArgumentOptimization, index++);
    hash += bit(allowInlining, index++);
    hash += bit(allowMethodStaticizing, index++);
    hash += bit(allowParameterRemoval, index++);
    hash += bit(allowParameterReordering, index++);
    hash += bit(allowParameterTypeStrengthening, index++);
    hash += bit(allowReprocessing, index++);
    hash += bit(allowReturnTypeStrengthening, index++);
    hash += bit(allowSingleCallerInlining, index++);
    hash += bit(allowUnusedArgumentOptimization, index++);
    hash += bit(allowUnusedReturnValueOptimization, index++);
    hash += bit(allowParameterNamesRemoval, index++);
    hash += bit(parameterAnnotationsInfo.isTop(), index);
    return hash;
  }

  @Override
  public List<String> lines() {
    List<String> lines = super.lines();
    lines.add("allowThrowsRemoval: " + allowThrowsRemoval);
    lines.add("allowClassInlining: " + allowClassInlining);
    lines.add("allowClosedWorldReasoning: " + allowClosedWorldReasoning);
    lines.add("allowCodeReplacement: " + allowCodeReplacement);
    lines.add("allowConstantArgumentOptimization: " + allowConstantArgumentOptimization);
    lines.add("allowInlining: " + allowInlining);
    lines.add("allowMethodStaticizing: " + allowMethodStaticizing);
    lines.add("allowParameterRemoval: " + allowParameterRemoval);
    lines.add("allowParameterReordering: " + allowParameterReordering);
    lines.add("allowParameterTypeStrengthening: " + allowParameterTypeStrengthening);
    lines.add("allowReprocessing: " + allowReprocessing);
    lines.add("allowReturnTypeStrengthening: " + allowReturnTypeStrengthening);
    lines.add("allowSingleCallerInlining: " + allowSingleCallerInlining);
    lines.add("allowUnusedArgumentOptimization: " + allowUnusedArgumentOptimization);
    lines.add("allowUnusedReturnValueOptimization: " + allowUnusedReturnValueOptimization);
    lines.add("allowParameterNamesRemoval: " + allowParameterNamesRemoval);
    lines.add("parameterAnnotationsInfo: " + parameterAnnotationsInfo);
    return lines;
  }

  public static class Builder extends KeepMemberInfo.Builder<Builder, KeepMethodInfo> {

    private boolean allowThrowsRemoval;
    private boolean allowClassInlining;
    private boolean allowClosedWorldReasoning;
    private boolean allowCodeReplacement;
    private boolean allowConstantArgumentOptimization;
    private boolean allowInlining;
    private boolean allowMethodStaticizing;
    private boolean allowParameterRemoval;
    private boolean allowParameterReordering;
    private boolean allowParameterTypeStrengthening;
    private boolean allowReprocessing;
    private boolean allowReturnTypeStrengthening;
    private boolean allowSingleCallerInlining;
    private boolean allowUnusedArgumentOptimization;
    private boolean allowUnusedReturnValueOptimization;
    private boolean allowParameterNamesRemoval;
    private KeepAnnotationCollectionInfo.Builder parameterAnnotationsInfo;

    public Builder() {
      super();
    }

    protected Builder(KeepMethodInfo original) {
      super(original);
      allowThrowsRemoval = original.internalIsThrowsRemovalAllowed();
      allowClassInlining = original.internalIsClassInliningAllowed();
      allowClosedWorldReasoning = original.internalIsClosedWorldReasoningAllowed();
      allowCodeReplacement = original.internalIsCodeReplacementAllowed();
      allowConstantArgumentOptimization = original.internalIsConstantArgumentOptimizationAllowed();
      allowInlining = original.internalIsInliningAllowed();
      allowMethodStaticizing = original.internalIsMethodStaticizingAllowed();
      allowParameterRemoval = original.internalIsParameterRemovalAllowed();
      allowParameterReordering = original.internalIsParameterReorderingAllowed();
      allowParameterTypeStrengthening = original.internalIsParameterTypeStrengtheningAllowed();
      allowReprocessing = original.internalIsReprocessingAllowed();
      allowReturnTypeStrengthening = original.internalIsReturnTypeStrengtheningAllowed();
      allowSingleCallerInlining = original.internalIsSingleCallerInliningAllowed();
      allowUnusedArgumentOptimization = original.internalIsUnusedArgumentOptimizationAllowed();
      allowUnusedReturnValueOptimization =
          original.internalIsUnusedReturnValueOptimizationAllowed();
      allowParameterNamesRemoval = original.internalIsParameterNamesRemovalAllowed();
      parameterAnnotationsInfo = original.internalParameterAnnotationsInfo().toBuilder();
    }

    public boolean isThrowsRemovalAllowed() {
      return allowThrowsRemoval;
    }

    public Builder setAllowThrowsRemoval(boolean allowThrowsRemoval) {
      this.allowThrowsRemoval = allowThrowsRemoval;
      return self();
    }

    public boolean isClassInliningAllowed() {
      return allowClassInlining;
    }

    public Builder setAllowClassInlining(boolean allowClassInlining) {
      this.allowClassInlining = allowClassInlining;
      return self();
    }

    public boolean isClosedWorldReasoningAllowed() {
      return allowClosedWorldReasoning;
    }

    public Builder setAllowClosedWorldReasoning(boolean allowClosedWorldReasoning) {
      this.allowClosedWorldReasoning = allowClosedWorldReasoning;
      return self();
    }

    public boolean isCodeReplacementAllowed() {
      return allowCodeReplacement;
    }

    public Builder setAllowCodeReplacement(boolean allowCodeReplacement) {
      this.allowCodeReplacement = allowCodeReplacement;
      return self();
    }

    public boolean isConstantArgumentOptimizationAllowed() {
      return allowConstantArgumentOptimization;
    }

    public Builder setAllowConstantArgumentOptimization(boolean allowConstantArgumentOptimization) {
      this.allowConstantArgumentOptimization = allowConstantArgumentOptimization;
      return self();
    }

    public boolean isInliningAllowed() {
      return allowInlining;
    }

    public Builder setAllowInlining(boolean allowInlining) {
      this.allowInlining = allowInlining;
      return self();
    }

    public boolean isMethodStaticizingAllowed() {
      return allowMethodStaticizing;
    }

    public Builder setAllowMethodStaticizing(boolean allowMethodStaticizing) {
      this.allowMethodStaticizing = allowMethodStaticizing;
      return self();
    }

    public boolean isParameterRemovalAllowed() {
      return allowParameterRemoval;
    }

    public Builder setAllowParameterRemoval(boolean allowParameterRemoval) {
      this.allowParameterRemoval = allowParameterRemoval;
      return self();
    }

    public boolean isParameterReorderingAllowed() {
      return allowParameterReordering;
    }

    public Builder setAllowParameterReordering(boolean allowParameterReordering) {
      this.allowParameterReordering = allowParameterReordering;
      return self();
    }

    public boolean isParameterTypeStrengtheningAllowed() {
      return allowParameterTypeStrengthening;
    }

    public Builder setAllowParameterTypeStrengthening(boolean allowParameterTypeStrengthening) {
      this.allowParameterTypeStrengthening = allowParameterTypeStrengthening;
      return self();
    }

    public boolean isReprocessingAllowed() {
      return allowReprocessing;
    }

    public Builder setAllowReprocessing(boolean allowReprocessing) {
      this.allowReprocessing = allowReprocessing;
      return self();
    }

    public boolean isReturnTypeStrengtheningAllowed() {
      return allowReturnTypeStrengthening;
    }

    public Builder setAllowReturnTypeStrengthening(boolean allowReturnTypeStrengthening) {
      this.allowReturnTypeStrengthening = allowReturnTypeStrengthening;
      return self();
    }

    public boolean isSingleCallerInliningAllowed() {
      return allowSingleCallerInlining;
    }

    public Builder setAllowSingleCallerInlining(boolean allowSingleCallerInlining) {
      this.allowSingleCallerInlining = allowSingleCallerInlining;
      return self();
    }

    public boolean isUnusedArgumentOptimizationAllowed() {
      return allowUnusedArgumentOptimization;
    }

    public Builder setAllowUnusedArgumentOptimization(boolean allowUnusedArgumentOptimization) {
      this.allowUnusedArgumentOptimization = allowUnusedArgumentOptimization;
      return self();
    }

    public boolean isUnusedReturnValueOptimizationAllowed() {
      return allowUnusedReturnValueOptimization;
    }

    public Builder setAllowUnusedReturnValueOptimization(
        boolean allowUnusedReturnValueOptimization) {
      this.allowUnusedReturnValueOptimization = allowUnusedReturnValueOptimization;
      return self();
    }

    public boolean isParameterNamesRemovalAllowed() {
      return allowParameterNamesRemoval;
    }

    public Builder setAllowParameterNamesRemoval(boolean allowParameterNamesRemoval) {
      this.allowParameterNamesRemoval = allowParameterNamesRemoval;
      return self();
    }

    public KeepAnnotationCollectionInfo.Builder getParameterAnnotationsInfo() {
      return parameterAnnotationsInfo;
    }

    public Builder setParameterAnnotationInfo(KeepAnnotationCollectionInfo.Builder infoBuilder) {
      parameterAnnotationsInfo = infoBuilder;
      return self();
    }

    @Override
    public Builder self() {
      return this;
    }

    @Override
    public KeepMethodInfo getTopInfo() {
      return TOP;
    }

    @Override
    public KeepMethodInfo getBottomInfo() {
      return BOTTOM;
    }

    @Override
    public boolean isEqualTo(KeepMethodInfo other) {
      return internalIsEqualTo(other);
    }

    @Override
    boolean internalIsEqualTo(KeepMethodInfo other) {
      return super.internalIsEqualTo(other)
          && isThrowsRemovalAllowed() == other.internalIsThrowsRemovalAllowed()
          && isClassInliningAllowed() == other.internalIsClassInliningAllowed()
          && isClosedWorldReasoningAllowed() == other.internalIsClosedWorldReasoningAllowed()
          && isCodeReplacementAllowed() == other.internalIsCodeReplacementAllowed()
          && isConstantArgumentOptimizationAllowed()
              == other.internalIsConstantArgumentOptimizationAllowed()
          && isInliningAllowed() == other.internalIsInliningAllowed()
          && isMethodStaticizingAllowed() == other.internalIsMethodStaticizingAllowed()
          && isParameterRemovalAllowed() == other.internalIsParameterRemovalAllowed()
          && isParameterReorderingAllowed() == other.internalIsParameterReorderingAllowed()
          && isParameterTypeStrengtheningAllowed()
              == other.internalIsParameterTypeStrengtheningAllowed()
          && isReprocessingAllowed() == other.internalIsReprocessingAllowed()
          && isReturnTypeStrengtheningAllowed() == other.internalIsReturnTypeStrengtheningAllowed()
          && isSingleCallerInliningAllowed() == other.internalIsSingleCallerInliningAllowed()
          && isUnusedArgumentOptimizationAllowed()
              == other.internalIsUnusedArgumentOptimizationAllowed()
          && isUnusedReturnValueOptimizationAllowed()
              == other.internalIsUnusedReturnValueOptimizationAllowed()
          && isParameterNamesRemovalAllowed() == other.internalIsParameterNamesRemovalAllowed()
          && parameterAnnotationsInfo.isEqualTo(other.parameterAnnotationsInfo);
    }

    @Override
    public KeepMethodInfo doBuild() {
      return new KeepMethodInfo(this);
    }

    @Override
    public Builder makeTop() {
      return super.makeTop()
          .setAllowThrowsRemoval(false)
          .setAllowClassInlining(false)
          .setAllowClosedWorldReasoning(false)
          .setAllowCodeReplacement(true)
          .setAllowConstantArgumentOptimization(false)
          .setAllowInlining(false)
          .setAllowMethodStaticizing(false)
          .setAllowParameterRemoval(false)
          .setAllowParameterReordering(false)
          .setAllowParameterTypeStrengthening(false)
          .setAllowReprocessing(false)
          .setAllowReturnTypeStrengthening(false)
          .setAllowSingleCallerInlining(false)
          .setAllowUnusedArgumentOptimization(false)
          .setAllowUnusedReturnValueOptimization(false)
          .setAllowParameterNamesRemoval(false)
          .setParameterAnnotationInfo(KeepAnnotationCollectionInfo.Builder.createTop());
    }

    @Override
    public Builder makeBottom() {
      return super.makeBottom()
          .setAllowThrowsRemoval(true)
          .setAllowClassInlining(true)
          .setAllowClosedWorldReasoning(true)
          .setAllowCodeReplacement(false)
          .setAllowConstantArgumentOptimization(true)
          .setAllowInlining(true)
          .setAllowMethodStaticizing(true)
          .setAllowParameterRemoval(true)
          .setAllowParameterReordering(true)
          .setAllowParameterTypeStrengthening(true)
          .setAllowReprocessing(true)
          .setAllowReturnTypeStrengthening(true)
          .setAllowSingleCallerInlining(true)
          .setAllowUnusedArgumentOptimization(true)
          .setAllowUnusedReturnValueOptimization(true)
          .setAllowParameterNamesRemoval(true)
          .setParameterAnnotationInfo(KeepAnnotationCollectionInfo.Builder.createBottom());
    }
  }

  public static class Joiner extends KeepMemberInfo.Joiner<Joiner, Builder, KeepMethodInfo> {

    public Joiner(KeepMethodInfo info) {
      super(info.builder());
    }

    protected Joiner(Builder builder) {
      super(builder);
    }

    public Joiner disallowThrowsRemoval() {
      builder.setAllowThrowsRemoval(false);
      return self();
    }

    public Joiner disallowClassInlining() {
      builder.setAllowClassInlining(false);
      return self();
    }

    public Joiner disallowClosedWorldReasoning() {
      builder.setAllowClosedWorldReasoning(false);
      return self();
    }

    public Joiner allowCodeReplacement() {
      builder.setAllowCodeReplacement(true);
      return self();
    }

    public Joiner disallowConstantArgumentOptimization() {
      builder.setAllowConstantArgumentOptimization(false);
      return self();
    }

    public Joiner disallowInlining() {
      builder.setAllowInlining(false);
      return self();
    }

    public Joiner disallowMethodStaticizing() {
      builder.setAllowMethodStaticizing(false);
      return self();
    }

    public Joiner disallowParameterRemoval() {
      builder.setAllowParameterRemoval(false);
      return self();
    }

    public Joiner disallowParameterReordering() {
      builder.setAllowParameterReordering(false);
      return self();
    }

    public Joiner disallowParameterTypeStrengthening() {
      builder.setAllowParameterTypeStrengthening(false);
      return self();
    }

    public Joiner disallowReprocessing() {
      builder.setAllowReprocessing(false);
      return self();
    }

    public Joiner disallowReturnTypeStrengthening() {
      builder.setAllowReturnTypeStrengthening(false);
      return self();
    }

    public Joiner disallowSingleCallerInlining() {
      builder.setAllowSingleCallerInlining(false);
      return self();
    }

    public Joiner disallowUnusedArgumentOptimization() {
      builder.setAllowUnusedArgumentOptimization(false);
      return self();
    }

    public Joiner disallowUnusedReturnValueOptimization() {
      builder.setAllowUnusedReturnValueOptimization(false);
      return self();
    }

    public Joiner disallowParameterNamesRemoval() {
      builder.setAllowParameterNamesRemoval(false);
      return self();
    }

    public Joiner disallowParameterAnnotationsRemoval() {
      builder.setParameterAnnotationInfo(KeepAnnotationCollectionInfo.Builder.createTop());
      return self();
    }

    public Joiner disallowParameterAnnotationsRemoval(RetentionInfo retention) {
      builder.getParameterAnnotationsInfo().destructiveJoinAnyTypeInfo(retention);
      builder.self();
      return self();
    }

    @Override
    public Joiner asMethodJoiner() {
      return this;
    }

    @Override
    public Joiner merge(Joiner joiner) {
      // Should be extended to merge the fields of this class in case any are added.
      super.merge(joiner);
      builder
          .getParameterAnnotationsInfo()
          .destructiveJoin(joiner.builder.getParameterAnnotationsInfo());
      return applyIf(!joiner.builder.isThrowsRemovalAllowed(), Joiner::disallowThrowsRemoval)
          .applyIf(!joiner.builder.isClassInliningAllowed(), Joiner::disallowClassInlining)
          .applyIf(
              !joiner.builder.isClosedWorldReasoningAllowed(), Joiner::disallowClosedWorldReasoning)
          .applyIf(joiner.builder.isCodeReplacementAllowed(), Joiner::allowCodeReplacement)
          .applyIf(
              !joiner.builder.isConstantArgumentOptimizationAllowed(),
              Joiner::disallowConstantArgumentOptimization)
          .applyIf(!joiner.builder.isInliningAllowed(), Joiner::disallowInlining)
          .applyIf(!joiner.builder.isMethodStaticizingAllowed(), Joiner::disallowMethodStaticizing)
          .applyIf(!joiner.builder.isParameterRemovalAllowed(), Joiner::disallowParameterRemoval)
          .applyIf(
              !joiner.builder.isParameterReorderingAllowed(), Joiner::disallowParameterReordering)
          .applyIf(
              !joiner.builder.isParameterTypeStrengtheningAllowed(),
              Joiner::disallowParameterTypeStrengthening)
          .applyIf(!joiner.builder.isReprocessingAllowed(), Joiner::disallowReprocessing)
          .applyIf(
              !joiner.builder.isReturnTypeStrengtheningAllowed(),
              Joiner::disallowReturnTypeStrengthening)
          .applyIf(
              !joiner.builder.isSingleCallerInliningAllowed(), Joiner::disallowSingleCallerInlining)
          .applyIf(
              !joiner.builder.isUnusedArgumentOptimizationAllowed(),
              Joiner::disallowUnusedArgumentOptimization)
          .applyIf(
              !joiner.builder.isUnusedReturnValueOptimizationAllowed(),
              Joiner::disallowUnusedReturnValueOptimization)
          .applyIf(
              !joiner.builder.isParameterNamesRemovalAllowed(),
              Joiner::disallowParameterNamesRemoval);
    }

    @Override
    Joiner self() {
      return this;
    }
  }
}
