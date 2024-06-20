// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.keepanno.asm;

import static com.android.tools.r8.keepanno.ast.KeepTypePattern.fromDescriptor;

import com.android.tools.r8.keepanno.asm.KeepEdgeReader.UserBindingsHelper;
import com.android.tools.r8.keepanno.ast.KeepBindingReference;
import com.android.tools.r8.keepanno.ast.KeepClassBindingReference;
import com.android.tools.r8.keepanno.ast.KeepClassItemPattern;
import com.android.tools.r8.keepanno.ast.KeepEdgeMetaInfo;
import com.android.tools.r8.keepanno.ast.KeepFieldNamePattern;
import com.android.tools.r8.keepanno.ast.KeepFieldPattern;
import com.android.tools.r8.keepanno.ast.KeepFieldTypePattern;
import com.android.tools.r8.keepanno.ast.KeepMemberItemPattern;
import com.android.tools.r8.keepanno.ast.KeepMemberPattern;
import com.android.tools.r8.keepanno.ast.KeepMethodNamePattern;
import com.android.tools.r8.keepanno.ast.KeepMethodParametersPattern;
import com.android.tools.r8.keepanno.ast.KeepMethodPattern;
import com.android.tools.r8.keepanno.ast.KeepMethodReturnTypePattern;
import com.android.tools.r8.keepanno.ast.KeepQualifiedClassNamePattern;
import com.android.tools.r8.keepanno.ast.ParsingContext;
import org.objectweb.asm.Type;

public class ContextDescriptor {

  public static ContextDescriptor parse(String descriptor, ParsingContext parsingContext) {
    int classDescriptorEnd = descriptor.indexOf(';') + 1;
    if (classDescriptorEnd <= 0) {
      throw parsingContext.error("Invalid descriptor: " + descriptor);
    }
    String classDescriptor = descriptor.substring(0, classDescriptorEnd);
    if (classDescriptorEnd == descriptor.length()) {
      return new ContextDescriptor(classDescriptor);
    }
    int memberNameEnd = descriptor.indexOf('(', classDescriptorEnd);
    if (memberNameEnd < 0) {
      memberNameEnd = descriptor.indexOf(':', classDescriptorEnd);
    }
    if (memberNameEnd < 0) {
      throw parsingContext.error("Invalid descriptor: " + descriptor);
    }
    String memberName = descriptor.substring(classDescriptorEnd, memberNameEnd);
    String memberDescriptor = descriptor.substring(memberNameEnd);
    return new ContextDescriptor(classDescriptor, memberName, memberDescriptor);
  }

  private final String classDescriptor;
  private final String memberName;
  private final String memberDescriptor;

  private ContextDescriptor(String classDescriptor) {
    this(classDescriptor, null, null);
  }

  private ContextDescriptor(String classDescriptor, String memberName, String memberDescriptor) {
    this.classDescriptor = classDescriptor;
    this.memberName = memberName;
    this.memberDescriptor = memberDescriptor;
  }

  public boolean isClassContext() {
    return memberDescriptor == null;
  }

  public boolean isMethodContext() {
    return memberDescriptor != null && memberDescriptor.charAt(0) == '(';
  }

  public boolean isFieldContext() {
    return memberDescriptor != null && memberDescriptor.charAt(0) == ':';
  }

  public String getClassDescriptor() {
    return classDescriptor;
  }

  public String getMemberName() {
    return memberName;
  }

  public String getMethodDescriptor() {
    assert isMethodContext();
    return memberDescriptor;
  }

  public String getFieldType() {
    assert isFieldContext();
    return memberDescriptor.substring(1);
  }

  public KeepEdgeMetaInfo.Builder applyToMetadata(KeepEdgeMetaInfo.Builder metaInfoBuilder) {
    if (isClassContext()) {
      metaInfoBuilder.setContextFromClassDescriptor(getClassDescriptor());
    } else if (isMethodContext()) {
      metaInfoBuilder.setContextFromMethodDescriptor(
          getClassDescriptor(), getMemberName(), getMethodDescriptor());
    } else {
      assert isFieldContext();
      metaInfoBuilder.setContextFromFieldDescriptor(
          getClassDescriptor(), getMemberName(), getFieldType());
    }
    return metaInfoBuilder;
  }

  public KeepBindingReference defineBindingReference(UserBindingsHelper bindingsHelper) {
    KeepQualifiedClassNamePattern className =
        KeepQualifiedClassNamePattern.exactFromDescriptor(getClassDescriptor());
    KeepClassItemPattern classItem =
        KeepClassItemPattern.builder().setClassNamePattern(className).build();
    KeepClassBindingReference classBinding = bindingsHelper.defineFreshClassBinding(classItem);
    if (isClassContext()) {
      return classBinding;
    }
    KeepMemberPattern memberPattern;
    if (isMethodContext()) {
      memberPattern = createMethodPatternFromDescriptor();
    } else {
      assert isFieldContext();
      memberPattern = createFieldPatternFromDescriptor();
    }
    KeepMemberItemPattern memberItem =
        KeepMemberItemPattern.builder()
            .setClassBindingReference(classBinding)
            .setMemberPattern(memberPattern)
            .build();
    return bindingsHelper.defineFreshMemberBinding(memberItem);
  }

  private KeepFieldPattern createFieldPatternFromDescriptor() {
    return KeepFieldPattern.builder()
        .setNamePattern(KeepFieldNamePattern.exact(getMemberName()))
        .setTypePattern(KeepFieldTypePattern.fromType(fromDescriptor(getFieldType())))
        .build();
  }

  private KeepMethodPattern createMethodPatternFromDescriptor() {
    Type methodType = Type.getMethodType(getMethodDescriptor());

    String returnTypeDescriptor = methodType.getReturnType().getDescriptor();
    KeepMethodReturnTypePattern returnType =
        returnTypeDescriptor.equals("V")
            ? KeepMethodReturnTypePattern.voidType()
            : KeepMethodReturnTypePattern.fromType(fromDescriptor(returnTypeDescriptor));

    KeepMethodParametersPattern.Builder paramBuilder = KeepMethodParametersPattern.builder();
    for (Type argumentType : methodType.getArgumentTypes()) {
      paramBuilder.addParameterTypePattern(fromDescriptor(argumentType.getDescriptor()));
    }

    return KeepMethodPattern.builder()
        .setNamePattern(KeepMethodNamePattern.exact(getMemberName()))
        .setReturnTypePattern(returnType)
        .setParametersPattern(paramBuilder.build())
        .build();
  }
}
