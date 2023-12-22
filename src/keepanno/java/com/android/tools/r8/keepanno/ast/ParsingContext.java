// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.keepanno.ast;

import static com.android.tools.r8.keepanno.asm.KeepEdgeReaderUtils.getJavaTypeFromDescriptor;

import org.objectweb.asm.Type;

public abstract class ParsingContext {

  public KeepAnnotationParserException error(String message) {
    throw new KeepAnnotationParserException(this, message);
  }

  public KeepAnnotationParserException rethrow(RuntimeException e) {
    if (e instanceof KeepAnnotationParserException) {
      throw e;
    }
    throw new KeepAnnotationParserException(this, e);
  }

  public abstract String getHolderName();

  public ParsingContext getParentContext() {
    return null;
  }

  public abstract String getContextFrameAsString();

  public static class ClassParsingContext extends ParsingContext {
    private final String className;

    public ClassParsingContext(String className) {
      this.className = className;
    }

    @Override
    public String getHolderName() {
      return className;
    }

    @Override
    public String getContextFrameAsString() {
      return className;
    }
  }

  public abstract static class MemberParsingContext extends ParsingContext {
    private final ClassParsingContext classContext;

    public MemberParsingContext(ClassParsingContext classContext) {
      this.classContext = classContext;
    }

    @Override
    public String getHolderName() {
      return classContext.getHolderName();
    }

    @Override
    public ParsingContext getParentContext() {
      return classContext;
    }
  }

  public static class MethodParsingContext extends MemberParsingContext {

    private final String methodName;
    private final String methodDescriptor;

    public MethodParsingContext(
        ClassParsingContext classContext, String methodName, String methodDescriptor) {
      super(classContext);
      this.methodName = methodName;
      this.methodDescriptor = methodDescriptor;
    }

    @Override
    public String getContextFrameAsString() {
      Type methodType = Type.getMethodType(methodDescriptor);
      StringBuilder builder = new StringBuilder();
      builder
          .append("method ")
          .append(getJavaTypeFromDescriptor(methodType.getReturnType().getDescriptor()))
          .append(' ')
          .append(methodName)
          .append('(');
      boolean first = true;
      for (Type argument : methodType.getArgumentTypes()) {
        if (first) {
          first = false;
        } else {
          builder.append(", ");
        }
        builder.append(getJavaTypeFromDescriptor(argument.getDescriptor()));
      }
      return builder.append(')').toString();
    }
  }

  public static class FieldParsingContext extends MemberParsingContext {

    private final String fieldName;
    private final String fieldDescriptor;

    public FieldParsingContext(
        ClassParsingContext classContext, String fieldName, String fieldDescriptor) {
      super(classContext);
      this.fieldName = fieldName;
      this.fieldDescriptor = fieldDescriptor;
    }

    @Override
    public String getContextFrameAsString() {
      return "field " + getJavaTypeFromDescriptor(fieldDescriptor) + " " + fieldName;
    }
  }

  public static class AnnotationParsingContext extends ParsingContext {
    private final ParsingContext parentContext;
    private final String annotationDescriptor;

    public AnnotationParsingContext(ParsingContext parentContext, String annotationDescriptor) {
      this.parentContext = parentContext;
      this.annotationDescriptor = annotationDescriptor;
    }

    public String getAnnotationDescriptor() {
      return annotationDescriptor;
    }

    private String getSimpleAnnotationName() {
      int i = annotationDescriptor.lastIndexOf('/') + 1;
      return annotationDescriptor.substring(Math.max(i, 1), annotationDescriptor.length() - 1);
    }

    @Override
    public String getHolderName() {
      return parentContext.getHolderName();
    }

    @Override
    public ParsingContext getParentContext() {
      return parentContext;
    }

    @Override
    public String getContextFrameAsString() {
      return "@" + getSimpleAnnotationName();
    }
  }
}
