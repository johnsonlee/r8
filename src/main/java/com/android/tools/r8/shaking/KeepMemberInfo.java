// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMember;
import com.android.tools.r8.shaking.KeepMemberInfo.Builder;

/** Immutable keep requirements for a member. */
@SuppressWarnings("BadImport")
public abstract class KeepMemberInfo<B extends Builder<B, K>, K extends KeepMemberInfo<B, K>>
    extends KeepInfo<B, K> {

  private final boolean allowValuePropagation;

  protected KeepMemberInfo(B builder) {
    super(builder);
    this.allowValuePropagation = builder.isValuePropagationAllowed();
  }

  @SuppressWarnings("BadImport")
  public boolean isKotlinMetadataRemovalAllowed(
      DexProgramClass holder, GlobalKeepInfoConfiguration configuration) {
    // Checking the holder for missing kotlin information relies on the holder being processed
    // before members.
    return holder.getKotlinInfo().isNoKotlinInformation() || !isPinned(configuration);
  }

  public boolean isValuePropagationAllowed(
      AppView<AppInfoWithLiveness> appView, ProgramMember<?, ?> member) {
    DexType type =
        member.isField() ? member.asField().getType() : member.asMethod().getReturnType();
    boolean isTypeInstantiated = !type.isAlwaysNull(appView);
    return isValuePropagationAllowed(appView.options(), isTypeInstantiated);
  }

  public boolean isValuePropagationAllowed(
      GlobalKeepInfoConfiguration configuration, boolean isTypeInstantiated) {
    if (!isOptimizationAllowed(configuration) && isTypeInstantiated) {
      return false;
    }
    return internalIsValuePropagationAllowed();
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

    public B allowValuePropagation() {
      return setAllowValuePropagation(true);
    }

    public B disallowValuePropagation() {
      return setAllowValuePropagation(false);
    }

    @Override
    boolean internalIsEqualTo(K other) {
      return super.internalIsEqualTo(other)
          && isValuePropagationAllowed() == other.internalIsValuePropagationAllowed();
    }

    @Override
    public B makeTop() {
      return super.makeTop().disallowValuePropagation();
    }

    @Override
    public B makeBottom() {
      return super.makeBottom().allowValuePropagation();
    }
  }

  public abstract static class Joiner<
          J extends Joiner<J, B, K>, B extends Builder<B, K>, K extends KeepMemberInfo<B, K>>
      extends KeepInfo.Joiner<J, B, K> {

    protected Joiner(B builder) {
      super(builder);
    }

    public J disallowValuePropagation() {
      builder.disallowValuePropagation();
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
