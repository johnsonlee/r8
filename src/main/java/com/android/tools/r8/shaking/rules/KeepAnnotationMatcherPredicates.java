// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.rules;

import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexTypeList;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.keepanno.ast.KeepArrayTypePattern;
import com.android.tools.r8.keepanno.ast.KeepClassItemPattern;
import com.android.tools.r8.keepanno.ast.KeepInstanceOfPattern;
import com.android.tools.r8.keepanno.ast.KeepMethodAccessPattern;
import com.android.tools.r8.keepanno.ast.KeepMethodParametersPattern;
import com.android.tools.r8.keepanno.ast.KeepMethodPattern;
import com.android.tools.r8.keepanno.ast.KeepMethodReturnTypePattern;
import com.android.tools.r8.keepanno.ast.KeepPrimitiveTypePattern;
import com.android.tools.r8.keepanno.ast.KeepQualifiedClassNamePattern;
import com.android.tools.r8.keepanno.ast.KeepStringPattern;
import com.android.tools.r8.keepanno.ast.KeepTypePattern;
import com.android.tools.r8.keepanno.ast.OptionalPattern;
import java.util.List;

public class KeepAnnotationMatcherPredicates {

  private final DexItemFactory factory;

  public KeepAnnotationMatcherPredicates(DexItemFactory factory) {
    this.factory = factory;
  }

  public boolean matchesClass(DexProgramClass clazz, KeepClassItemPattern classPattern) {
    return matchesClassName(clazz.getType(), classPattern.getClassNamePattern())
        && matchesInstanceOfPattern(clazz, classPattern.getInstanceOfPattern())
        && matchesAnnotatedBy(clazz.annotations(), classPattern.getAnnotatedByPattern());
  }

  public boolean matchesClassName(DexType type, KeepQualifiedClassNamePattern pattern) {
    if (pattern.isAny()) {
      return true;
    }
    if (pattern.isExact()) {
      return type.toDescriptorString().equals(pattern.getExactDescriptor());
    }
    throw new Unimplemented();
  }

  private boolean matchesInstanceOfPattern(
      DexProgramClass unusedClazz, KeepInstanceOfPattern pattern) {
    if (pattern.isAny()) {
      return true;
    }
    throw new Unimplemented();
  }

  public boolean matchesMethod(KeepMethodPattern methodPattern, ProgramMethod method) {
    if (methodPattern.isAnyMethod()) {
      return true;
    }
    return matchesName(method.getName(), methodPattern.getNamePattern().asStringPattern())
        && matchesReturnType(method.getReturnType(), methodPattern.getReturnTypePattern())
        && matchesParameters(method.getParameters(), methodPattern.getParametersPattern())
        && matchesAnnotatedBy(method.getAnnotations(), methodPattern.getAnnotatedByPattern())
        && matchesAccess(method.getAccessFlags(), methodPattern.getAccessPattern());
  }

  public boolean matchesAccess(MethodAccessFlags access, KeepMethodAccessPattern pattern) {
    if (pattern.isAny()) {
      return true;
    }
    throw new Unimplemented();
  }

  public boolean matchesAnnotatedBy(
      DexAnnotationSet annotations, OptionalPattern<KeepQualifiedClassNamePattern> pattern) {
    if (pattern.isAbsent()) {
      return true;
    }
    throw new Unimplemented();
  }

  public boolean matchesParameters(DexTypeList parameters, KeepMethodParametersPattern pattern) {
    if (pattern.isAny()) {
      return true;
    }
    List<KeepTypePattern> patternList = pattern.asList();
    if (parameters.size() != patternList.size()) {
      return false;
    }
    int size = parameters.size();
    for (int i = 0; i < size; i++) {
      if (!matchesType(parameters.get(i), patternList.get(i))) {
        return false;
      }
    }
    return true;
  }

  public boolean matchesName(DexString name, KeepStringPattern namePattern) {
    if (namePattern.isAny()) {
      return true;
    }
    if (namePattern.isExact()) {
      return namePattern.asExactString().equals(name.toString());
    }
    throw new Unimplemented();
  }

  public boolean matchesReturnType(
      DexType returnType, KeepMethodReturnTypePattern returnTypePattern) {
    if (returnTypePattern.isAny()) {
      return true;
    }
    if (returnTypePattern.isVoid() && returnType.isVoidType()) {
      return true;
    }
    return matchesType(returnType, returnTypePattern.asType());
  }

  public boolean matchesType(DexType type, KeepTypePattern pattern) {
    return pattern.apply(
        () -> true,
        p -> matchesPrimitiveType(type, p),
        p -> matchesArrayType(type, p),
        p -> matchesClassType(type, p));
  }

  public boolean matchesClassType(DexType type, KeepQualifiedClassNamePattern pattern) {
    if (!type.isClassType()) {
      return false;
    }
    if (pattern.isAny()) {
      return true;
    }
    if (pattern.isExact()) {
      return pattern.getExactDescriptor().equals(type.toDescriptorString());
    }
    throw new Unimplemented();
  }

  public boolean matchesArrayType(DexType type, KeepArrayTypePattern pattern) {
    if (!type.isArrayType()) {
      return false;
    }
    if (pattern.isAny()) {
      return true;
    }
    if (type.getArrayTypeDimensions() < pattern.getDimensions()) {
      return false;
    }
    return matchesType(
        type.toArrayElementAfterDimension(pattern.getDimensions(), factory), pattern.getBaseType());
  }

  public boolean matchesPrimitiveType(DexType type, KeepPrimitiveTypePattern pattern) {
    if (!type.isPrimitiveType()) {
      return false;
    }
    if (pattern.isAny()) {
      return true;
    }
    return type.asPrimitiveTypeDescriptorChar() == pattern.getDescriptorChar();
  }
}
