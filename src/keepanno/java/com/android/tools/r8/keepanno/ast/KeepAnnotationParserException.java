// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.keepanno.ast;

public class KeepAnnotationParserException extends RuntimeException {

  private final ParsingContext context;

  public KeepAnnotationParserException(ParsingContext context, String message) {
    super(message);
    this.context = context;
  }

  public KeepAnnotationParserException(ParsingContext context, RuntimeException cause) {
    super(cause);
    this.context = context;
  }

  @Override
  public String getMessage() {
    return super.getMessage() + getContextAsString();
  }

  private String getContextAsString() {
    StringBuilder builder = new StringBuilder();
    ParsingContext current = context;
    while (current != null) {
      builder
          .append("\n  at ")
          .append(current.getContextType())
          .append(": ")
          .append(current.getContextFrameAsString());
      current = current.getParentContext();
    }
    return builder.toString();
  }
}
