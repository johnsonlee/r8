// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.rules;

import com.android.tools.r8.graph.AccessFlags;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.DexAnnotation;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMember;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexTypeList;
import com.android.tools.r8.graph.FieldAccessFlags;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.keepanno.ast.AccessVisibility;
import com.android.tools.r8.keepanno.ast.KeepAnnotationPattern;
import com.android.tools.r8.keepanno.ast.KeepArrayTypePattern;
import com.android.tools.r8.keepanno.ast.KeepClassItemPattern;
import com.android.tools.r8.keepanno.ast.KeepClassPattern;
import com.android.tools.r8.keepanno.ast.KeepFieldAccessPattern;
import com.android.tools.r8.keepanno.ast.KeepFieldPattern;
import com.android.tools.r8.keepanno.ast.KeepInstanceOfPattern;
import com.android.tools.r8.keepanno.ast.KeepMemberAccessPattern;
import com.android.tools.r8.keepanno.ast.KeepMemberPattern;
import com.android.tools.r8.keepanno.ast.KeepMethodAccessPattern;
import com.android.tools.r8.keepanno.ast.KeepMethodParametersPattern;
import com.android.tools.r8.keepanno.ast.KeepMethodPattern;
import com.android.tools.r8.keepanno.ast.KeepMethodReturnTypePattern;
import com.android.tools.r8.keepanno.ast.KeepPackagePattern;
import com.android.tools.r8.keepanno.ast.KeepPrimitiveTypePattern;
import com.android.tools.r8.keepanno.ast.KeepQualifiedClassNamePattern;
import com.android.tools.r8.keepanno.ast.KeepStringPattern;
import com.android.tools.r8.keepanno.ast.KeepTypePattern;
import com.android.tools.r8.keepanno.ast.KeepUnqualfiedClassNamePattern;
import com.android.tools.r8.keepanno.ast.ModifierPattern;
import com.android.tools.r8.keepanno.ast.OptionalPattern;
import com.android.tools.r8.utils.TraversalContinuation;
import java.util.List;

public class KeepAnnotationMatcherPredicates {

  private final DexItemFactory factory;

  public KeepAnnotationMatcherPredicates(DexItemFactory factory) {
    this.factory = factory;
  }

  public boolean matchesClass(
      DexClass clazz, KeepClassItemPattern classPattern, AppInfoWithClassHierarchy appInfo) {
    return matchesClassName(clazz.getType(), classPattern.getClassNamePattern())
        && matchesAnnotatedBy(clazz.annotations(), classPattern.getAnnotatedByPattern())
        && matchesInstanceOfPattern(clazz, classPattern.getInstanceOfPattern(), appInfo);
  }

  public boolean matchesClassName(DexType type, KeepQualifiedClassNamePattern pattern) {
    assert type.isClassType();
    if (pattern.isAny()) {
      return true;
    }
    if (pattern.isExact()) {
      return type.toDescriptorString().equals(pattern.getExactDescriptor());
    }
    return matchesPackage(type.getPackageName(), pattern.getPackagePattern())
        && matchesSimpleName(type.getSimpleName(), pattern.getNamePattern());
  }

  private boolean matchesPackage(String packageName, KeepPackagePattern pattern) {
    if (pattern.isAny()) {
      return true;
    }
    if (pattern.isTop() && packageName.equals("")) {
      return true;
    }
    return packageName.equals(pattern.getExactPackageAsString());
  }

  public boolean matchesSimpleName(String simpleName, KeepUnqualfiedClassNamePattern pattern) {
    return matchesString(simpleName, pattern.asStringPattern());
  }

  private boolean matchesInstanceOfPattern(
      DexType type, KeepInstanceOfPattern pattern, AppInfoWithClassHierarchy appInfo) {
    DexClass dexClass = appInfo.definitionFor(type);
    if (dexClass != null) {
      return matchesInstanceOfPattern(dexClass.asProgramClass(), pattern, appInfo);
    }
    return false;
  }

  private boolean matchesInstanceOfPattern(
      DexClass clazz, KeepInstanceOfPattern pattern, AppInfoWithClassHierarchy appInfo) {
    if (pattern.isAny()) {
      return true;
    }
    // TODO(b/323816623): Consider a way to optimize instance matching to avoid repeated traversals.
    if (pattern.isInclusive()) {
      if (matchesClassName(clazz.getType(), pattern.getClassNamePattern())) {
        return true;
      }
    }
    return appInfo
        .traverseSuperTypes(
            clazz,
            (supertype, unusedSuperclass, unusedSubClass, unusedIsInterface) -> {
              if (matchesClassName(supertype, pattern.getClassNamePattern())) {
                return TraversalContinuation.doBreak();
              }
              return TraversalContinuation.doContinue();
            })
        .isBreak();
  }

  public boolean matchesGeneralMember(DexEncodedMember<?, ?> member, KeepMemberPattern pattern) {
    assert pattern.isGeneralMember();
    if (pattern.isAllMembers()) {
      return true;
    }
    return matchesAnnotatedBy(member.annotations(), pattern.getAnnotatedByPattern())
        && matchesGeneralMemberAccess(member.getAccessFlags(), pattern.getAccessPattern());
  }

  public boolean matchesMethod(
      DexEncodedMethod method, KeepMethodPattern pattern, AppInfoWithClassHierarchy appInfo) {
    if (pattern.isAnyMethod()) {
      return true;
    }
    return matchesString(method.getName(), pattern.getNamePattern().asStringPattern())
        && matchesReturnType(method.getReturnType(), pattern.getReturnTypePattern(), appInfo)
        && matchesParameters(method.getParameters(), pattern.getParametersPattern(), appInfo)
        && matchesAnnotatedBy(method.annotations(), pattern.getAnnotatedByPattern())
        && matchesMethodAccess(method.getAccessFlags(), pattern.getAccessPattern());
  }

  public boolean matchesField(
      DexEncodedField field, KeepFieldPattern pattern, AppInfoWithClassHierarchy appInfo) {
    if (pattern.isAnyField()) {
      return true;
    }
    return matchesString(field.getName(), pattern.getNamePattern().asStringPattern())
        && matchesType(field.getType(), pattern.getTypePattern().asType(), appInfo)
        && matchesAnnotatedBy(field.annotations(), pattern.getAnnotatedByPattern())
        && matchesFieldAccess(field.getAccessFlags(), pattern.getAccessPattern());
  }

  public boolean matchesGeneralMemberAccess(
      AccessFlags<?> access, KeepMemberAccessPattern pattern) {
    if (pattern.isAny()) {
      return true;
    }
    if (!pattern.isAnyVisibility() && !pattern.isVisibilityAllowed(getAccessVisibility(access))) {
      return false;
    }
    return matchesModifier(access.isStatic(), pattern.getStaticPattern())
        && matchesModifier(access.isFinal(), pattern.getFinalPattern())
        && matchesModifier(access.isSynthetic(), pattern.getSyntheticPattern());
  }

  public boolean matchesMethodAccess(MethodAccessFlags access, KeepMethodAccessPattern pattern) {
    if (pattern.isAny()) {
      return true;
    }
    return matchesGeneralMemberAccess(access, pattern)
        && matchesModifier(access.isSynchronized(), pattern.getSynchronizedPattern())
        && matchesModifier(access.isBridge(), pattern.getBridgePattern())
        && matchesModifier(access.isNative(), pattern.getNativePattern())
        && matchesModifier(access.isAbstract(), pattern.getAbstractPattern())
        && matchesModifier(access.isStrict(), pattern.getStrictFpPattern());
  }

  public boolean matchesFieldAccess(FieldAccessFlags access, KeepFieldAccessPattern pattern) {
    if (pattern.isAny()) {
      return true;
    }
    return matchesGeneralMemberAccess(access, pattern)
        && matchesModifier(access.isVolatile(), pattern.getVolatilePattern())
        && matchesModifier(access.isTransient(), pattern.getTransientPattern());
  }

  private boolean matchesModifier(boolean value, ModifierPattern pattern) {
    return pattern.isAny() || value == pattern.isOnlyPositive();
  }

  private AccessVisibility getAccessVisibility(AccessFlags<?> accessFlags) {
    if (accessFlags.isPublic()) {
      return AccessVisibility.PUBLIC;
    }
    if (accessFlags.isProtected()) {
      return AccessVisibility.PROTECTED;
    }
    if (accessFlags.isPackagePrivate()) {
      return AccessVisibility.PACKAGE_PRIVATE;
    }
    assert accessFlags.isPrivate();
    return AccessVisibility.PRIVATE;
  }

  public boolean matchesAnnotatedBy(
      DexAnnotationSet annotations, OptionalPattern<KeepQualifiedClassNamePattern> pattern) {
    if (pattern.isAbsent()) {
      // No pattern for annotations matches regardless of annotation content.
      return true;
    }
    if (annotations.isEmpty()) {
      // Fast-path if pattern is present but no annotations.
      return false;
    }
    KeepQualifiedClassNamePattern classNamePattern = pattern.get();
    if (classNamePattern.isAny()) {
      // Fast-path the "any" case.
      return true;
    }
    for (DexAnnotation annotation : annotations.getAnnotations()) {
      if (matchesClassName(annotation.getAnnotationType(), classNamePattern)) {
        return true;
      }
    }
    return false;
  }

  public boolean matchesParameters(
      DexTypeList parameters,
      KeepMethodParametersPattern pattern,
      AppInfoWithClassHierarchy appInfo) {
    if (pattern.isAny()) {
      return true;
    }
    List<KeepTypePattern> patternList = pattern.asList();
    if (parameters.size() != patternList.size()) {
      return false;
    }
    int size = parameters.size();
    for (int i = 0; i < size; i++) {
      if (!matchesType(parameters.get(i), patternList.get(i), appInfo)) {
        return false;
      }
    }
    return true;
  }

  public boolean matchesString(DexString string, KeepStringPattern pattern) {
    if (pattern.isAny()) {
      return true;
    }
    // TODO(b/323816623): Consider precompiling matches to avoid repeated string decoding.
    if (pattern.isExact()) {
      return string.isEqualTo(pattern.asExactString());
    }
    if (pattern.hasPrefix() && !string.startsWith(pattern.getPrefixString())) {
      return false;
    }
    if (pattern.hasSuffix() && !string.endsWith(pattern.getSuffixString())) {
      return false;
    }
    return true;
  }

  // TODO(b/323816623): Avoid copy of matchesString if we can avoid the decoding.
  public boolean matchesString(String string, KeepStringPattern pattern) {
    if (pattern.isAny()) {
      return true;
    }
    if (pattern.isExact()) {
      return string.equals(pattern.asExactString());
    }
    if (pattern.hasPrefix() && !string.startsWith(pattern.getPrefixString())) {
      return false;
    }
    if (pattern.hasSuffix() && !string.endsWith(pattern.getSuffixString())) {
      return false;
    }
    return true;
  }

  public boolean matchesReturnType(
      DexType returnType,
      KeepMethodReturnTypePattern returnTypePattern,
      AppInfoWithClassHierarchy appInfo) {
    if (returnTypePattern.isAny()) {
      return true;
    }
    if (returnTypePattern.isVoid()) {
      return returnType.isVoidType();
    }
    return matchesType(returnType, returnTypePattern.asType(), appInfo);
  }

  public boolean matchesType(
      DexType type, KeepTypePattern pattern, AppInfoWithClassHierarchy appInfo) {
    return pattern.apply(
        () -> true,
        p -> matchesPrimitiveType(type, p),
        p -> matchesArrayType(type, p, appInfo),
        p -> matchesClassPattern(type, p, appInfo));
  }

  public boolean matchesClassPattern(
      DexType type, KeepClassPattern pattern, AppInfoWithClassHierarchy appInfo) {
    return matchesClassType(type, pattern.getClassNamePattern())
        && matchesInstanceOfPattern(type, pattern.getInstanceOfPattern(), appInfo);
  }

  public boolean matchesClassType(DexType type, KeepQualifiedClassNamePattern pattern) {
    return type.isClassType() && matchesClassName(type, pattern);
  }

  public boolean matchesArrayType(
      DexType type, KeepArrayTypePattern pattern, AppInfoWithClassHierarchy appInfo) {
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
        type.toArrayElementAfterDimension(pattern.getDimensions(), factory),
        pattern.getBaseType(),
        appInfo);
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

  public boolean matchesAnnotation(DexAnnotation annotation, KeepAnnotationPattern pattern) {
    int visibility = annotation.getVisibility();
    if (visibility != DexAnnotation.VISIBILITY_BUILD
        && visibility != DexAnnotation.VISIBILITY_RUNTIME) {
      return false;
    }
    if (visibility == DexAnnotation.VISIBILITY_BUILD && !pattern.includesClassRetention()) {
      return false;
    }
    if (visibility == DexAnnotation.VISIBILITY_RUNTIME && !pattern.includesRuntimeRetention()) {
      return false;
    }
    return matchesClassName(annotation.getAnnotationType(), pattern.getNamePattern());
  }
}
