// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.outliner.exceptions;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.lightir.LirCode;
import com.android.tools.r8.lightir.LirConstant;
import com.android.tools.r8.utils.structural.CompareToVisitor;
import com.android.tools.r8.utils.structural.HashingVisitor;
import com.google.common.collect.ConcurrentHashMultiset;
import com.google.common.collect.Multiset;

public class ThrowBlockOutline implements LirConstant {

  @SuppressWarnings("UnusedVariable")
  private final LirCode<?> lirCode;

  private final Multiset<DexMethod> users = ConcurrentHashMultiset.create();

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

  public int getNumberOfUsers() {
    return users.size();
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
}
