// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.retrace;

import com.android.tools.r8.naming.retrace.StackTrace.StackTraceLine;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.retrace.RetraceStackTraceElementProxy;
import com.android.tools.r8.retrace.RetracedMethodReference;
import com.android.tools.r8.retrace.RetracedMethodReference.KnownRetracedMethodReference;
import com.android.tools.r8.retrace.StackTraceElementProxy;

public class StackTraceLineProxy
    extends StackTraceElementProxy<StackTraceLine, StackTraceLineProxy> {

  private final StackTraceLine stackTraceLine;

  public StackTraceLineProxy(StackTraceLine stackTraceLine) {
    this.stackTraceLine = stackTraceLine;
  }

  @Override
  public boolean hasClassName() {
    return true;
  }

  @Override
  public boolean hasMethodName() {
    return true;
  }

  @Override
  public boolean hasSourceFile() {
    return true;
  }

  @Override
  public boolean hasLineNumber() {
    return true;
  }

  @Override
  public boolean hasFieldName() {
    return false;
  }

  @Override
  public boolean hasFieldOrReturnType() {
    return false;
  }

  @Override
  public boolean hasMethodArguments() {
    return false;
  }

  @Override
  public ClassReference getClassReference() {
    return Reference.classFromTypeName(stackTraceLine.className);
  }

  @Override
  public String getMethodName() {
    return stackTraceLine.methodName;
  }

  @Override
  public String getSourceFile() {
    return stackTraceLine.fileName;
  }

  @Override
  public int getLineNumber() {
    return stackTraceLine.lineNumber;
  }

  @Override
  public String getFieldName() {
    return null;
  }

  @Override
  public String getFieldOrReturnType() {
    return null;
  }

  @Override
  public String getMethodArguments() {
    return null;
  }

  @Override
  public StackTraceLine toRetracedItem(
      RetraceStackTraceElementProxy<StackTraceLine, StackTraceLineProxy> retracedProxy,
      boolean verbose) {
    RetracedMethodReference retracedMethod = retracedProxy.getRetracedMethod();
    if (retracedMethod == null) {
      return new StackTraceLine(
          stackTraceLine.toString(),
          stackTraceLine.className,
          stackTraceLine.methodName,
          stackTraceLine.fileName,
          stackTraceLine.lineNumber);
    } else {
      KnownRetracedMethodReference knownRetracedMethodReference = retracedMethod.asKnown();
      return new StackTraceLine(
          stackTraceLine.toString(),
          knownRetracedMethodReference.getMethodReference().getHolderClass().getTypeName(),
          knownRetracedMethodReference.getMethodName(),
          retracedProxy.getSourceFile(),
          knownRetracedMethodReference.getOriginalPositionOrDefault(0));
    }
  }
}
