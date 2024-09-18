// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMember;
import com.android.tools.r8.shaking.KeepMemberInfo.Builder;
import com.android.tools.r8.utils.InternalOptions;

/** Immutable keep requirements for a member. */
@SuppressWarnings("BadImport")
public abstract class KeepMemberInfo<B extends Builder<B, K>, K extends KeepMemberInfo<B, K>>
    extends KeepInfo<B, K> {

  private final boolean allowValuePropagation;

  protected KeepMemberInfo(B builder) {
    super(builder);
    this.allowValuePropagation = builder.isValuePropagationAllowed();
  }

  public boolean isKotlinMetadataRemovalAllowed(
      DexProgramClass holder, GlobalKeepInfoConfiguration configuration) {
    // Checking the holder for missing kotlin information relies on the holder being processed
    // before members.
    return holder.getKotlinInfo().isNoKotlinInformation() || !isPinned(configuration);
  }

  public boolean isValuePropagationAllowed(
      AppView<AppInfoWithLiveness> appView, ProgramMember<?, ?> member) {
    InternalOptions options = appView.options();
    if (!internalIsValuePropagationAllowed()) {
      return false;
    }
    if (member.isMethod() && !asMethodInfo().isCodeReplacementAllowed(options)) {
      return true;
    }
    DexType type =
        member.isField() ? member.asField().getType() : member.asMethod().getReturnType();
    boolean isTypeInstantiated = !type.isAlwaysNull(appView);
    return isOptimizationAllowed(options) || !isTypeInstantiated;
  }

  boolean internalIsValuePropagationAllowed() {
    return allowValuePropagation;
  }

  public abstract static class Builder<B extends Builder<B, K>, K extends KeepMemberInfo<B, K>>
      extends KeepInfo.Builder<B, K> {

    private boolean allowValuePropagation;

    protected Builder() {
      super();
    }

    protected Builder(K original) {
      super(original);
      allowValuePropagation = original.internalIsValuePropagationAllowed();
    }

    // Value propagation.

    public boolean isValuePropagationAllowed() {
      return allowValuePropagation;
    }

    public B setAllowValuePropagation(boolean allowValuePropagation) {
      this.allowValuePropagation = allowValuePropagation;
      return self();
    }

    @Override
    boolean internalIsEqualTo(K other) {
      return super.internalIsEqualTo(other)
          && isValuePropagationAllowed() == other.internalIsValuePropagationAllowed();
    }

    @Override
    public B makeTop() {
      return super.makeTop().setAllowValuePropagation(false);
    }

    @Override
    public B makeBottom() {
      return super.makeBottom().setAllowValuePropagation(true);
    }
  }

  @Override
  public boolean equalsNoAnnotations(K other) {
    return super.equalsNoAnnotations(other)
        && (allowValuePropagation == other.internalIsValuePropagationAllowed());
  }

  @Override
  public int hashCodeNoAnnotations() {
    int hash = super.hashCodeNoAnnotations();
    int index = super.numberOfBooleans();
    hash += bit(allowValuePropagation, index);
    return hash;
  }

  @Override
  protected int numberOfBooleans() {
    return super.numberOfBooleans() + 1;
  }

  public abstract static class Joiner<
          J extends Joiner<J, B, K>, B extends Builder<B, K>, K extends KeepMemberInfo<B, K>>
      extends KeepInfo.Joiner<J, B, K> {

    protected Joiner(B builder) {
      super(builder);
    }

    public J disallowValuePropagation() {
      builder.setAllowValuePropagation(false);
      return self();
    }

    @Override
    public Joiner<?, ?, ?> asMemberJoiner() {
      return this;
    }

    @Override
    public J merge(J joiner) {
      // Should be extended to merge the fields of this class in case any are added.
      return super.merge(joiner)
          .applyIf(!joiner.builder.isValuePropagationAllowed(), Joiner::disallowValuePropagation);
    }
  }
}
