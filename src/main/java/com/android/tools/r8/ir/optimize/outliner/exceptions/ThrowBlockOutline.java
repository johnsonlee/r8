// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.outliner.exceptions;

import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.lightir.LirCode;
import com.android.tools.r8.lightir.LirConstant;
import com.android.tools.r8.synthesis.SyntheticItems;
import com.android.tools.r8.utils.structural.CompareToVisitor;
import com.android.tools.r8.utils.structural.HashingVisitor;
import com.google.common.collect.ConcurrentHashMultiset;
import com.google.common.collect.Multiset;

public class ThrowBlockOutline implements LirConstant {

  @SuppressWarnings("UnusedVariable")
  private final LirCode<?> lirCode;
  private final Multiset<DexMethod> users = ConcurrentHashMultiset.create();

  private ProgramMethod materializedOutlineMethod;

  ThrowBlockOutline(LirCode<?> lirCode) {
    this.lirCode = lirCode;
  }

  public void addUser(DexMethod user) {
    users.add(user);
  }

  @Override
  public LirConstantOrder getLirConstantOrder() {
    return LirConstantOrder.THROW_BLOCK_OUTLINE;
  }

  public ProgramMethod getMaterializedOutlineMethod() {
    return materializedOutlineMethod;
  }

  public int getNumberOfUsers() {
    return users.size();
  }

  public ProgramMethod getSynthesizingContext(AppView<?> appView) {
    DexMethod shortestUser = null;
    for (DexMethod user : users) {
      if (shortestUser == null) {
        shortestUser = user;
      } else {
        int userLength = user.getHolderType().getDescriptor().length();
        int shortestUserLength = shortestUser.getHolderType().getDescriptor().length();
        if (userLength < shortestUserLength) {
          shortestUser = user;
        } else if (userLength == shortestUserLength && user.compareTo(shortestUser) < 0) {
          shortestUser = user;
        }
      }
    }
    assert shortestUser != null;
    return appView.definitionFor(shortestUser).asProgramMethod();
  }

  public Multiset<DexMethod> getUsers() {
    return users;
  }

  @Override
  public int internalLirConstantAcceptCompareTo(LirConstant other, CompareToVisitor visitor) {
    throw new Unreachable();
  }

  @Override
  public void internalLirConstantAcceptHashing(HashingVisitor visitor) {
    throw new Unreachable();
  }

  public boolean isMaterialized() {
    return materializedOutlineMethod != null;
  }

  public void materialize(AppView<?> appView, MethodProcessingContext methodProcessingContext) {
    SyntheticItems syntheticItems = appView.getSyntheticItems();
    DexProto emptyProto = appView.dexItemFactory().objectMembers.constructor.getProto();
    materializedOutlineMethod =
        syntheticItems.createMethod(
            kinds -> kinds.THROW_BLOCK_OUTLINE,
            methodProcessingContext.createUniqueContext(),
            appView,
            builder ->
                builder
                    .setAccessFlags(MethodAccessFlags.createPublicStaticSynthetic())
                    .setCode(methodSig -> lirCode)
                    .setProto(emptyProto));
  }
}
