// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.ast;

import static com.android.tools.r8.keepanno.ast.KeepSpecUtils.desc;

import com.android.tools.r8.keepanno.keeprules.RulePrintingUtils;
import com.android.tools.r8.keepanno.proto.KeepSpecProtos.Context;
import com.android.tools.r8.keepanno.proto.KeepSpecProtos.FieldDesc;
import com.android.tools.r8.keepanno.proto.KeepSpecProtos.MetaInfo;
import com.android.tools.r8.keepanno.proto.KeepSpecProtos.MethodDesc;
import com.android.tools.r8.keepanno.proto.KeepSpecProtos.TypeDesc;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.objectweb.asm.Type;

public class KeepEdgeMetaInfo {

  private static final KeepEdgeMetaInfo NONE =
      new KeepEdgeMetaInfo(
          KeepSpecVersion.UNKNOWN, KeepEdgeContext.none(), KeepEdgeDescription.empty());

  public static KeepEdgeMetaInfo none() {
    return NONE;
  }

  public static Builder builder() {
    return new Builder();
  }

  private final KeepSpecVersion version;
  private final KeepEdgeContext context;
  private final KeepEdgeDescription description;

  private KeepEdgeMetaInfo(
      KeepSpecVersion version, KeepEdgeContext context, KeepEdgeDescription description) {
    this.version = version;
    this.context = context;
    this.description = description;
  }

  public boolean hasDescription() {
    return !KeepEdgeDescription.empty().equals(description);
  }

  public String getDescriptionString() {
    return description.description;
  }

  public String getContextDescriptorString() {
    return context.getDescriptorString();
  }

  public boolean hasContext() {
    return !KeepEdgeContext.none().equals(context);
  }

  public boolean hasVersion() {
    return version != KeepSpecVersion.UNKNOWN;
  }

  public KeepSpecVersion getVersion() {
    return version;
  }

  public String toString() {
    List<String> props = new ArrayList<>(3);
    if (hasVersion()) {
      props.add("version=" + version);
    }
    if (hasContext()) {
      props.add("context=" + context.getDescriptorString());
    }
    if (hasDescription()) {
      props.add(
          "description=\"" + RulePrintingUtils.escapeLineBreaks(description.description) + "\"");
    }
    return "MetaInfo{" + String.join(", ", props) + "}";
  }

  public static KeepEdgeMetaInfo fromProto(MetaInfo proto, KeepSpecVersion version) {
    return builder().applyProto(proto, version).build();
  }

  public MetaInfo.Builder buildProto() {
    MetaInfo.Builder builder = MetaInfo.newBuilder();
    builder.setContext(context.buildProto());
    if (!description.isEmpty()) {
      builder.setDescription(description.description);
    }
    return builder;
  }

  public static class Builder {
    private KeepSpecVersion version = KeepSpecVersion.UNKNOWN;
    private KeepEdgeContext context = KeepEdgeContext.none();
    private KeepEdgeDescription description = KeepEdgeDescription.empty();

    public Builder applyProto(MetaInfo proto, KeepSpecVersion version) {
      // Version is a non-optional value (not part of the proto MetaInfo).
      setVersion(version);
      // The proto MetaInfo is optional so guard for the null case here.
      if (proto != null) {
        if (proto.hasContext()) {
          setContext(KeepEdgeContext.fromProto(proto.getContext()));
        }
        setDescription(proto.getDescription());
      }
      return this;
    }

    public Builder setVersion(KeepSpecVersion version) {
      this.version = version;
      return this;
    }

    public Builder setDescription(String description) {
      this.description =
          description.isEmpty() ? KeepEdgeDescription.EMPTY : new KeepEdgeDescription(description);
      return this;
    }

    public Builder setContext(KeepEdgeContext context) {
      this.context = context;
      return this;
    }

    public Builder setContextFromClassDescriptor(String classDescriptor) {
      return setContext(new KeepEdgeClassContext(classDescriptor));
    }

    public Builder setContextFromMethodDescriptor(
        String classDescriptor, String methodName, String methodDescriptor) {
      Type methodType = Type.getMethodType(methodDescriptor);
      Type[] argumentTypes = methodType.getArgumentTypes();
      List<String> parameters = new ArrayList<>(argumentTypes.length);
      for (Type argumentType : argumentTypes) {
        parameters.add(argumentType.getDescriptor());
      }
      return setContext(
          new KeepEdgeMethodContext(
              classDescriptor, methodName, methodType.getReturnType().getDescriptor(), parameters));
    }

    public Builder setContextFromFieldDescriptor(
        String classDescriptor, String fieldName, String fieldType) {
      return setContext(new KeepEdgeFieldContext(classDescriptor, fieldName, fieldType));
    }

    public KeepEdgeMetaInfo build() {
      if (context.equals(KeepEdgeContext.none())
          && description.equals(KeepEdgeDescription.empty())) {
        return none();
      }
      return new KeepEdgeMetaInfo(version, context, description);
    }
  }

  private static class KeepEdgeContext {
    private static final KeepEdgeContext NONE = new KeepEdgeContext();

    public static KeepEdgeContext none() {
      return NONE;
    }

    private KeepEdgeContext() {}

    public static KeepEdgeContext fromProto(Context context) {
      if (context.hasClassDesc()) {
        return new KeepEdgeClassContext(context.getClassDesc().getDesc());
      }
      if (context.hasMethodDesc()) {
        MethodDesc methodDesc = context.getMethodDesc();
        List<String> parameters = new ArrayList<>(methodDesc.getParameterTypesCount());
        for (TypeDesc typeDesc : methodDesc.getParameterTypesList()) {
          parameters.add(typeDesc.getDesc());
        }
        return new KeepEdgeMethodContext(
            methodDesc.getHolder().getDesc(),
            methodDesc.getName(),
            methodDesc.getReturnType().getDesc(),
            parameters);
      }
      if (context.hasFieldDesc()) {
        FieldDesc fieldDesc = context.getFieldDesc();
        return new KeepEdgeFieldContext(
            fieldDesc.getHolder().getDesc(),
            fieldDesc.getName(),
            fieldDesc.getFieldType().getDesc());
      }
      return none();
    }

    public String getDescriptorString() {
      throw new KeepEdgeException("Invalid attempt to get descriptor string from none context");
    }

    @Override
    public boolean equals(Object obj) {
      return this == obj;
    }

    @Override
    public int hashCode() {
      return System.identityHashCode(this);
    }

    public final Context.Builder buildProto() {
      return buildProto(Context.newBuilder());
    }

    public Context.Builder buildProto(Context.Builder builder) {
      assert this == none();
      return builder;
    }
  }

  private static class KeepEdgeClassContext extends KeepEdgeContext {
    private final String classDescriptor;

    public KeepEdgeClassContext(String classDescriptor) {
      assert classDescriptor != null;
      this.classDescriptor = classDescriptor;
    }

    @Override
    public String getDescriptorString() {
      return classDescriptor;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof KeepEdgeClassContext)) {
        return false;
      }
      KeepEdgeClassContext that = (KeepEdgeClassContext) o;
      return classDescriptor.equals(that.classDescriptor);
    }

    @Override
    public int hashCode() {
      return classDescriptor.hashCode();
    }

    @Override
    public Context.Builder buildProto(Context.Builder builder) {
      return builder.setClassDesc(desc(classDescriptor));
    }
  }

  private static class KeepEdgeMethodContext extends KeepEdgeContext {
    private final String classDescriptor;
    private final String methodName;
    private final String methodReturnType;
    private final List<String> methodParameters;

    public KeepEdgeMethodContext(
        String classDescriptor,
        String methodName,
        String methodReturnType,
        List<String> methodParameters) {
      assert classDescriptor != null;
      assert methodName != null;
      assert methodParameters != null;
      this.classDescriptor = classDescriptor;
      this.methodName = methodName;
      this.methodReturnType = methodReturnType;
      this.methodParameters = methodParameters;
    }

    @Override
    public String getDescriptorString() {
      StringBuilder builder = new StringBuilder();
      builder.append(classDescriptor).append(methodName).append('(');
      for (String parameter : methodParameters) {
        builder.append(parameter);
      }
      return builder.append(')').append(methodReturnType).toString();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof KeepEdgeMethodContext)) {
        return false;
      }
      KeepEdgeMethodContext that = (KeepEdgeMethodContext) o;
      return classDescriptor.equals(that.classDescriptor)
          && methodName.equals(that.methodName)
          && methodReturnType.equals(that.methodReturnType)
          && methodParameters.equals(that.methodParameters);
    }

    @Override
    public int hashCode() {
      return Objects.hash(classDescriptor, methodName, methodReturnType, methodParameters);
    }

    @Override
    public Context.Builder buildProto(Context.Builder builder) {
      MethodDesc.Builder methodBuilder =
          MethodDesc.newBuilder()
              .setHolder(desc(classDescriptor))
              .setName(methodName)
              .setReturnType(desc(methodReturnType));
      for (String methodParameter : methodParameters) {
        methodBuilder.addParameterTypes(desc(methodParameter));
      }
      return builder.setMethodDesc(methodBuilder.build());
    }
  }

  private static class KeepEdgeFieldContext extends KeepEdgeContext {
    private final String classDescriptor;
    private final String fieldName;
    private final String fieldType;

    public KeepEdgeFieldContext(String classDescriptor, String fieldName, String fieldType) {
      this.classDescriptor = classDescriptor;
      this.fieldName = fieldName;
      this.fieldType = fieldType;
    }

    @Override
    public String getDescriptorString() {
      return classDescriptor + fieldName + ":" + fieldType;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof KeepEdgeFieldContext)) {
        return false;
      }
      KeepEdgeFieldContext that = (KeepEdgeFieldContext) o;
      return classDescriptor.equals(that.classDescriptor)
          && fieldName.equals(that.fieldName)
          && fieldType.equals(that.fieldType);
    }

    @Override
    public int hashCode() {
      return Objects.hash(classDescriptor, fieldName, fieldType);
    }

    @Override
    public Context.Builder buildProto(Context.Builder builder) {
      return builder.setFieldDesc(
          FieldDesc.newBuilder()
              .setHolder(desc(classDescriptor))
              .setName(fieldName)
              .setFieldType(desc(fieldType))
              .build());
    }
  }

  private static class KeepEdgeDescription {
    private static final KeepEdgeDescription EMPTY = new KeepEdgeDescription("");

    public static KeepEdgeDescription empty() {
      return EMPTY;
    }

    private final String description;

    public KeepEdgeDescription(String description) {
      assert description != null;
      this.description = description;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof KeepEdgeDescription)) {
        return false;
      }
      KeepEdgeDescription that = (KeepEdgeDescription) o;
      return description.equals(that.description);
    }

    @Override
    public int hashCode() {
      return description.hashCode();
    }

    @Override
    public String toString() {
      return description;
    }

    public boolean isEmpty() {
      return description.isEmpty();
    }
  }
}
