// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming;

import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndField;
import com.android.tools.r8.graph.DexClassAndMember;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.ProgramField;
import java.util.function.BiPredicate;

public interface MemberNamingStrategy {

  DexString next(
      DexClassAndMethod method,
      InternalNamingState internalState,
      BiPredicate<DexString, DexMethod> isAvailable);

  DexString next(
      ProgramField field,
      InternalNamingState internalState,
      BiPredicate<DexString, ProgramField> isAvailable);

  DexString getReservedName(DexClassAndMethod method);

  DexString getReservedName(DexClassAndField field);

  boolean allowMemberRenaming(DexClass clazz);

  default boolean allowMemberRenaming(DexClassAndMember<?, ?> member) {
    return allowMemberRenaming(member.getHolder());
  }
}
