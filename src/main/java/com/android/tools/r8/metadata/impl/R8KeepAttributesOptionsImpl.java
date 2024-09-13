// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.metadata.impl;

import com.android.tools.r8.keepanno.annotations.AnnotationPattern;
import com.android.tools.r8.keepanno.annotations.FieldAccessFlags;
import com.android.tools.r8.keepanno.annotations.KeepConstraint;
import com.android.tools.r8.keepanno.annotations.KeepItemKind;
import com.android.tools.r8.keepanno.annotations.UsedByReflection;
import com.android.tools.r8.metadata.R8KeepAttributesOptions;
import com.android.tools.r8.shaking.ProguardKeepAttributes;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

@UsedByReflection(
    description = "Keep and preserve @SerializedName for correct (de)serialization",
    constraints = {KeepConstraint.LOOKUP},
    constrainAnnotations = @AnnotationPattern(constant = SerializedName.class),
    kind = KeepItemKind.CLASS_AND_FIELDS,
    fieldAccess = {FieldAccessFlags.PRIVATE},
    fieldAnnotatedByClassConstant = SerializedName.class)
public class R8KeepAttributesOptionsImpl implements R8KeepAttributesOptions {

  @Expose
  @SerializedName("isAnnotationDefaultKept")
  private final boolean isAnnotationDefaultKept;

  @Expose
  @SerializedName("isEnclosingMethodKept")
  private final boolean isEnclosingMethodKept;

  @Expose
  @SerializedName("isExceptionsKept")
  private final boolean isExceptionsKept;

  @Expose
  @SerializedName("isInnerClassesKept")
  private final boolean isInnerClassesKept;

  @Expose
  @SerializedName("isLocalVariableTableKept")
  private final boolean isLocalVariableTableKept;

  @Expose
  @SerializedName("isLocalVariableTypeTableKept")
  private final boolean isLocalVariableTypeTableKept;

  @Expose
  @SerializedName("isMethodParametersKept")
  private final boolean isMethodParametersKept;

  @Expose
  @SerializedName("isPermittedSubclassesKept")
  private final boolean isPermittedSubclassesKept;

  @Expose
  @SerializedName("isRuntimeInvisibleAnnotationsKept")
  private final boolean isRuntimeInvisibleAnnotationsKept;

  @Expose
  @SerializedName("isRuntimeInvisibleParameterAnnotationsKept")
  private final boolean isRuntimeInvisibleParameterAnnotationsKept;

  @Expose
  @SerializedName("isRuntimeInvisibleTypeAnnotationsKept")
  private final boolean isRuntimeInvisibleTypeAnnotationsKept;

  @Expose
  @SerializedName("isRuntimeVisibleAnnotationsKept")
  private final boolean isRuntimeVisibleAnnotationsKept;

  @Expose
  @SerializedName("isRuntimeVisibleParameterAnnotationsKept")
  private final boolean isRuntimeVisibleParameterAnnotationsKept;

  @Expose
  @SerializedName("isRuntimeVisibleTypeAnnotationsKept")
  private final boolean isRuntimeVisibleTypeAnnotationsKept;

  @Expose
  @SerializedName("isSignatureKept")
  private final boolean isSignatureKept;

  @Expose
  @SerializedName("isSourceDebugExtensionKept")
  private final boolean isSourceDebugExtensionKept;

  @Expose
  @SerializedName("isSourceDirKept")
  private final boolean isSourceDirKept;

  @Expose
  @SerializedName("isSourceFileKept")
  private final boolean isSourceFileKept;

  @Expose
  @SerializedName("isStackMapTableKept")
  private final boolean isStackMapTableKept;

  public R8KeepAttributesOptionsImpl(ProguardKeepAttributes keepAttributes) {
    this.isAnnotationDefaultKept = keepAttributes.annotationDefault;
    this.isEnclosingMethodKept = keepAttributes.enclosingMethod;
    this.isExceptionsKept = keepAttributes.exceptions;
    this.isInnerClassesKept = keepAttributes.innerClasses;
    this.isLocalVariableTableKept = keepAttributes.localVariableTable;
    this.isLocalVariableTypeTableKept = keepAttributes.localVariableTypeTable;
    this.isMethodParametersKept = keepAttributes.methodParameters;
    this.isPermittedSubclassesKept = keepAttributes.permittedSubclasses;
    this.isRuntimeInvisibleAnnotationsKept = keepAttributes.runtimeInvisibleAnnotations;
    this.isRuntimeInvisibleParameterAnnotationsKept =
        keepAttributes.runtimeInvisibleParameterAnnotations;
    this.isRuntimeInvisibleTypeAnnotationsKept = keepAttributes.runtimeInvisibleTypeAnnotations;
    this.isRuntimeVisibleAnnotationsKept = keepAttributes.runtimeVisibleAnnotations;
    this.isRuntimeVisibleParameterAnnotationsKept =
        keepAttributes.runtimeVisibleParameterAnnotations;
    this.isRuntimeVisibleTypeAnnotationsKept = keepAttributes.runtimeVisibleTypeAnnotations;
    this.isSignatureKept = keepAttributes.signature;
    this.isSourceDebugExtensionKept = keepAttributes.sourceDebugExtension;
    this.isSourceDirKept = keepAttributes.sourceDir;
    this.isSourceFileKept = keepAttributes.sourceFile;
    this.isStackMapTableKept = keepAttributes.stackMapTable;
  }

  @Override
  public boolean isAnnotationDefaultKept() {
    return isAnnotationDefaultKept;
  }

  @Override
  public boolean isEnclosingMethodKept() {
    return isEnclosingMethodKept;
  }

  @Override
  public boolean isExceptionsKept() {
    return isExceptionsKept;
  }

  @Override
  public boolean isInnerClassesKept() {
    return isInnerClassesKept;
  }

  @Override
  public boolean isLocalVariableTableKept() {
    return isLocalVariableTableKept;
  }

  @Override
  public boolean isLocalVariableTypeTableKept() {
    return isLocalVariableTypeTableKept;
  }

  @Override
  public boolean isMethodParametersKept() {
    return isMethodParametersKept;
  }

  @Override
  public boolean isPermittedSubclassesKept() {
    return isPermittedSubclassesKept;
  }

  @Override
  public boolean isRuntimeInvisibleAnnotationsKept() {
    return isRuntimeInvisibleAnnotationsKept;
  }

  @Override
  public boolean isRuntimeInvisibleParameterAnnotationsKept() {
    return isRuntimeInvisibleParameterAnnotationsKept;
  }

  @Override
  public boolean isRuntimeInvisibleTypeAnnotationsKept() {
    return isRuntimeInvisibleTypeAnnotationsKept;
  }

  @Override
  public boolean isRuntimeVisibleAnnotationsKept() {
    return isRuntimeVisibleAnnotationsKept;
  }

  @Override
  public boolean isRuntimeVisibleParameterAnnotationsKept() {
    return isRuntimeVisibleParameterAnnotationsKept;
  }

  @Override
  public boolean isRuntimeVisibleTypeAnnotationsKept() {
    return isRuntimeVisibleTypeAnnotationsKept;
  }

  @Override
  public boolean isSignatureKept() {
    return isSignatureKept;
  }

  @Override
  public boolean isSourceDebugExtensionKept() {
    return isSourceDebugExtensionKept;
  }

  @Override
  public boolean isSourceDirKept() {
    return isSourceDirKept;
  }

  @Override
  public boolean isSourceFileKept() {
    return isSourceFileKept;
  }

  @Override
  public boolean isStackMapTableKept() {
    return isStackMapTableKept;
  }
}
