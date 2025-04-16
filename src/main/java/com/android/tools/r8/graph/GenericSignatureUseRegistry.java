// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.android.tools.r8.graph.GenericSignature.ClassTypeSignature;
import com.android.tools.r8.graph.GenericSignature.FieldTypeSignature;
import com.android.tools.r8.graph.GenericSignature.FormalTypeParameter;
import com.android.tools.r8.graph.GenericSignature.MethodTypeSignature;
import com.android.tools.r8.graph.GenericSignature.ReturnType;
import com.android.tools.r8.graph.GenericSignature.TypeSignature;
import java.util.List;

public interface GenericSignatureUseRegistry {

  void registerType(DexType type);

  default Visitor toVisitor() {
    return new Visitor(this);
  }

  class Visitor implements GenericSignatureVisitor {

    private final GenericSignatureUseRegistry registry;

    public Visitor(GenericSignatureUseRegistry registry) {
      this.registry = registry;
    }

    @Override
    public FieldTypeSignature visitClassBound(FieldTypeSignature fieldSignature) {
      return visitFieldTypeSignature(fieldSignature);
    }

    @Override
    public ClassTypeSignature visitEnclosing(
        ClassTypeSignature enclosingSignature, ClassTypeSignature enclosedSignature) {
      return enclosingSignature.visit(this);
    }

    @Override
    public FieldTypeSignature visitFieldTypeSignature(FieldTypeSignature fieldSignature) {
      if (fieldSignature.isStar() || fieldSignature.isTypeVariableSignature()) {
        return fieldSignature;
      } else if (fieldSignature.isArrayTypeSignature()) {
        return fieldSignature.asArrayTypeSignature().visit(this);
      } else {
        assert fieldSignature.isClassTypeSignature();
        return fieldSignature.asClassTypeSignature().visit(this);
      }
    }

    public List<FieldTypeSignature> visitFieldTypeSignatures(
        List<FieldTypeSignature> fieldTypeSignatures) {
      fieldTypeSignatures.forEach(this::visitFieldTypeSignature);
      return fieldTypeSignatures;
    }

    @Override
    public FormalTypeParameter visitFormalTypeParameter(FormalTypeParameter formalTypeParameter) {
      return formalTypeParameter.visit(this);
    }

    @Override
    public List<FormalTypeParameter> visitFormalTypeParameters(
        List<FormalTypeParameter> formalTypeParameters) {
      formalTypeParameters.forEach(this::visitFormalTypeParameter);
      return formalTypeParameters;
    }

    @Override
    public FieldTypeSignature visitInterfaceBound(FieldTypeSignature fieldSignature) {
      return visitFieldTypeSignature(fieldSignature);
    }

    @Override
    public List<FieldTypeSignature> visitInterfaceBounds(List<FieldTypeSignature> fieldSignatures) {
      fieldSignatures.forEach(this::visitInterfaceBound);
      return fieldSignatures;
    }

    @Override
    public MethodTypeSignature visitMethodSignature(MethodTypeSignature methodSignature) {
      return methodSignature.visit(this);
    }

    public TypeSignature visitMethodTypeSignature(TypeSignature typeSignature) {
      return visitTypeSignature(typeSignature);
    }

    @Override
    public List<TypeSignature> visitMethodTypeSignatures(List<TypeSignature> typeSignatures) {
      typeSignatures.forEach(this::visitMethodTypeSignature);
      return typeSignatures;
    }

    @Override
    public ReturnType visitReturnType(ReturnType returnType) {
      if (!returnType.isVoidDescriptor()) {
        visitTypeSignature(returnType.typeSignature());
      }
      return returnType;
    }

    @Override
    public ClassTypeSignature visitSuperClass(
        ClassTypeSignature classTypeSignatureOrNullForObject) {
      if (classTypeSignatureOrNullForObject != null) {
        return classTypeSignatureOrNullForObject.visit(this);
      } else {
        return classTypeSignatureOrNullForObject;
      }
    }

    @Override
    public ClassTypeSignature visitSuperInterface(ClassTypeSignature classTypeSignature) {
      return classTypeSignature.visit(this);
    }

    @Override
    public List<ClassTypeSignature> visitSuperInterfaces(
        List<ClassTypeSignature> interfaceSignatures) {
      interfaceSignatures.forEach(this::visitSuperInterface);
      return interfaceSignatures;
    }

    @Override
    public List<TypeSignature> visitThrowsSignatures(List<TypeSignature> typeSignatures) {
      return visitTypeSignatures(typeSignatures);
    }

    @Override
    public DexType visitType(DexType type) {
      registry.registerType(type);
      return type;
    }

    @Override
    public List<FieldTypeSignature> visitTypeArguments(
        DexType originalType, DexType lookedUpType, List<FieldTypeSignature> typeArguments) {
      return visitFieldTypeSignatures(typeArguments);
    }

    @Override
    public TypeSignature visitTypeSignature(TypeSignature typeSignature) {
      if (typeSignature.isBaseTypeSignature()) {
        assert typeSignature.asBaseTypeSignature().getType().isPrimitiveType();
        return typeSignature;
      } else {
        assert typeSignature.isFieldTypeSignature();
        return visitFieldTypeSignature(typeSignature.asFieldTypeSignature());
      }
    }

    public List<TypeSignature> visitTypeSignatures(List<TypeSignature> typeSignatures) {
      typeSignatures.forEach(this::visitTypeSignature);
      return typeSignatures;
    }
  }
}
