// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

/** Provides immutable access to {@link FieldAccessInfoImpl}. */
public interface FieldAccessInfo {

  DexField getField();

  ProgramMethod getUniqueWriteContextForCallGraphConstruction();

  ProgramMethod getUniqueWriteContextForFieldValueAnalysis();

  boolean hasReflectiveAccess();

  boolean hasReflectiveRead();

  boolean hasReflectiveWrite();

  default boolean isAccessedFromMethodHandle() {
    return isReadFromMethodHandle() || isWrittenFromMethodHandle();
  }

  boolean isEffectivelyFinal(ProgramField field);

  boolean isRead();

  boolean isReadFromAnnotation();

  boolean isReadFromRecordInvokeDynamic();

  boolean isReadFromMethodHandle();

  default boolean isReadIndirectly() {
    return hasReflectiveRead()
        || isReadFromAnnotation()
        || isReadFromMethodHandle()
        || isReadFromRecordInvokeDynamic();
  }

  boolean isReadOnlyFromFindLiteExtensionByNumberMethod();

  boolean isWritten();

  boolean isWrittenFromMethodHandle();

  default boolean isWrittenIndirectly() {
    return hasReflectiveWrite() || isWrittenFromMethodHandle();
  }
}
