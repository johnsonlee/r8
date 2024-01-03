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

  public abstract String getContextType();

  public abstract String getContextFrameAsString();

  public boolean isSynthetic() {
    return false;
  }

  private ParsingContext nonSyntheticParent() {
    // We don't want to maintain nested property groups as they are "synthetic" and only the
    // inner-most group is useful when diagnosing an error.
    if (isSynthetic()) {
      ParsingContext parent = getParentContext();
      assert !parent.isSynthetic();
      return parent;
    }
    return this;
  }

  public GroupParsingContext group(String propertyGroupDescription) {
    return new GroupParsingContext(this, propertyGroupDescription);
  }

  public AnnotationParsingContext annotation(String annotationDescriptor) {
    return new AnnotationParsingContext(this, annotationDescriptor);
  }

  public PropertyParsingContext property(String propertyName) {
    return new PropertyParsingContext(this, propertyName);
  }

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
    public String getContextType() {
      return "class";
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
    public String getContextType() {
      return "method";
    }

    @Override
    public String getContextFrameAsString() {
      Type methodType = Type.getMethodType(methodDescriptor);
      StringBuilder builder = new StringBuilder();
      builder
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
    public String getContextType() {
      return "field";
    }

    @Override
    public String getContextFrameAsString() {
      return getJavaTypeFromDescriptor(fieldDescriptor) + " " + fieldName;
    }
  }

  public static class AnnotationParsingContext extends ParsingContext {
    private final ParsingContext parentContext;
    private final String annotationDescriptor;

    public AnnotationParsingContext(ParsingContext parentContext, String annotationDescriptor) {
      this.parentContext = parentContext.nonSyntheticParent();
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
    public String getContextType() {
      return "annotation";
    }

    @Override
    public String getContextFrameAsString() {
      return "@" + getSimpleAnnotationName();
    }
  }

  public static class GroupParsingContext extends ParsingContext {
    private final ParsingContext parentContext;
    private final String propertyGroupDescription;

    public GroupParsingContext(ParsingContext parentContext, String propertyGroupDescription) {
      this.parentContext = parentContext.nonSyntheticParent();
      this.propertyGroupDescription = propertyGroupDescription;
    }

    public String getPropertyGroupDescription() {
      return propertyGroupDescription;
    }

    @Override
    public boolean isSynthetic() {
      // The property "groups" are not actual source info and should only be used in top-level
      // reporting.
      return true;
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
    public String getContextType() {
      return "property-group";
    }

    @Override
    public String getContextFrameAsString() {
      return getPropertyGroupDescription();
    }
  }

  public static class PropertyParsingContext extends ParsingContext {
    private final ParsingContext parentContext;
    private final String propertyName;

    public PropertyParsingContext(ParsingContext parentContext, String propertyName) {
      this.parentContext = parentContext.nonSyntheticParent();
      this.propertyName = propertyName;
    }

    public String getPropertyName() {
      return propertyName;
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
    public String getContextType() {
      return "property";
    }

    @Override
    public String getContextFrameAsString() {
      return getPropertyName();
    }
  }
}
