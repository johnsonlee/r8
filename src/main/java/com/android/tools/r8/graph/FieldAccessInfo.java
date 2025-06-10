// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import com.android.tools.r8.ir.analysis.proto.ProtoReferences;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Provides immutable access to {@link FieldAccessInfoImpl}. */
public interface FieldAccessInfo {

  DexField getField();

  int getNumberOfWriteContexts();

  boolean hasKnownReadContexts();

  boolean hasKnownWriteContexts();

  void forEachIndirectAccess(Consumer<DexField> consumer);

  void forEachAccessContext(Consumer<ProgramMethod> consumer);

  void forEachWriteContext(Consumer<ProgramMethod> consumer);

  boolean hasReflectiveAccess();

  boolean hasReflectiveWrite();

  default boolean isAccessedFromMethodHandle() {
    return isReadFromMethodHandle() || isWrittenFromMethodHandle();
  }

  boolean isRead();

  boolean isReadFromAnnotation();

  boolean isReadFromRecordInvokeDynamic();

  boolean isReadFromMethodHandle();

  boolean isReadOnlyInFindLiteExtensionByNumberMethod(ProtoReferences references);

  boolean isWritten();

  boolean isWrittenFromMethodHandle();

  boolean isWrittenOnlyInMethodSatisfying(Predicate<ProgramMethod> predicate);

  boolean isWrittenOutside(DexEncodedMethod method);
}
