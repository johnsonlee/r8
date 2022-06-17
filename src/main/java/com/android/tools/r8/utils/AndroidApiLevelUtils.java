// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import static com.android.tools.r8.graph.DexLibraryClass.asLibraryClassOrNull;

import com.android.tools.r8.androidapi.AndroidApiLevelCompute;
import com.android.tools.r8.androidapi.ComputedApiLevel;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.LibraryClass;
import com.android.tools.r8.graph.LibraryDefinition;
import com.android.tools.r8.graph.LibraryMethod;
import com.android.tools.r8.graph.ProgramMethod;

public class AndroidApiLevelUtils {

  public static boolean isApiSafeForInlining(
      ProgramMethod caller, ProgramMethod inlinee, InternalOptions options) {
    if (!options.apiModelingOptions().enableApiCallerIdentification) {
      return true;
    }
    if (caller.getHolderType() == inlinee.getHolderType()) {
      return true;
    }
    // For inlining we only measure if the code has invokes into the library.
    return caller
        .getDefinition()
        .getApiLevelForCode()
        .isGreaterThanOrEqualTo(inlinee.getDefinition().getApiLevelForCode());
  }

  public static ComputedApiLevel getApiReferenceLevelForMerging(
      AppView<?> appView, AndroidApiLevelCompute apiLevelCompute, DexProgramClass clazz) {
    // The api level of a class is the max level of it's members, super class and interfaces.
    return getMembersApiReferenceLevelForMerging(
        clazz,
        apiLevelCompute.computeApiLevelForDefinition(
            clazz.allImmediateSupertypes(), apiLevelCompute.getPlatformApiLevelOrUnknown(appView)));
  }

  private static ComputedApiLevel getMembersApiReferenceLevelForMerging(
      DexProgramClass clazz, ComputedApiLevel memberLevel) {
    // Based on b/138781768#comment57 there is almost no penalty for having an unknown reference
    // as long as we are not invoking or accessing a field on it. Therefore we can disregard static
    // types of fields and only consider method code api levels.
    for (DexEncodedMethod method : clazz.methods()) {
      if (method.hasCode()) {
        memberLevel = memberLevel.max(method.getApiLevelForCode());
      }
      if (memberLevel.isUnknownApiLevel()) {
        return memberLevel;
      }
    }
    return memberLevel;
  }

  public static boolean isApiSafeForMemberRebinding(
      LibraryMethod method,
      AndroidApiLevelCompute androidApiLevelCompute,
      InternalOptions options) {
    ComputedApiLevel apiLevel =
        androidApiLevelCompute.computeApiLevelForLibraryReference(
            method.getReference(), ComputedApiLevel.unknown());
    if (apiLevel.isUnknownApiLevel()) {
      return false;
    }
    assert options.apiModelingOptions().enableApiCallerIdentification;
    return apiLevel.isLessThanOrEqualTo(options.getMinApiLevel()).isTrue();
  }

  private static boolean isApiSafeForReference(
      LibraryDefinition definition,
      AndroidApiLevelCompute androidApiLevelCompute,
      InternalOptions options) {
    assert options.apiModelingOptions().enableApiCallerIdentification;
    ComputedApiLevel apiLevel =
        androidApiLevelCompute.computeApiLevelForLibraryReference(
            definition.getReference(), ComputedApiLevel.unknown());
    return apiLevel.isLessThanOrEqualTo(options.getMinApiLevel()).isTrue();
  }

  private static boolean isApiSafeForReference(
      LibraryDefinition newDefinition,
      LibraryDefinition oldDefinition,
      AndroidApiLevelCompute androidApiLevelCompute,
      InternalOptions options) {
    assert options.apiModelingOptions().enableApiCallerIdentification;
    assert !isApiSafeForReference(newDefinition, androidApiLevelCompute, options)
        : "Clients should first check if the definition is present on all apis since the min api";
    ComputedApiLevel apiLevel =
        androidApiLevelCompute.computeApiLevelForLibraryReference(
            newDefinition.getReference(), ComputedApiLevel.unknown());
    if (apiLevel.isUnknownApiLevel()) {
      return false;
    }
    ComputedApiLevel apiLevelOfOriginal =
        androidApiLevelCompute.computeApiLevelForLibraryReference(
            oldDefinition.getReference(), ComputedApiLevel.unknown());
    return apiLevel.isLessThanOrEqualTo(apiLevelOfOriginal).isTrue();
  }

  public static boolean isApiSafeForTypeStrengthening(
      DexType newType,
      DexType oldType,
      AppView<? extends AppInfoWithClassHierarchy> appView,
      AndroidApiLevelCompute androidApiLevelCompute) {
    // Type strengthening only applies to reference types.
    assert newType.isReferenceType();
    assert oldType.isReferenceType();
    assert newType != oldType;
    DexItemFactory dexItemFactory = appView.dexItemFactory();
    DexType newBaseType = newType.toBaseType(dexItemFactory);
    if (newBaseType.isPrimitiveType()) {
      // Array of primitives is available on all api levels.
      return true;
    }
    assert newBaseType.isClassType();
    DexClass newBaseClass = appView.definitionFor(newBaseType);
    if (newBaseClass == null) {
      // This could be a library class that is only available on newer api levels.
      return false;
    }
    if (!newBaseClass.isLibraryClass()) {
      // Program and classpath classes are not api level dependent.
      return true;
    }
    InternalOptions options = appView.options();
    if (!options.apiModelingOptions().enableApiCallerIdentification) {
      // Conservatively bail out if we don't have api modeling.
      return false;
    }
    LibraryClass newBaseLibraryClass = newBaseClass.asLibraryClass();
    if (isApiSafeForReference(newBaseLibraryClass, androidApiLevelCompute, options)) {
      // Library class is present on all api levels since min api.
      return true;
    }
    // Check if the new library class is present since the api level of the old type.
    DexType oldBaseType = oldType.toBaseType(dexItemFactory);
    assert oldBaseType.isClassType();
    LibraryClass oldBaseLibraryClass = asLibraryClassOrNull(appView.definitionFor(oldBaseType));
    return oldBaseLibraryClass != null
        && isApiSafeForReference(
            newBaseLibraryClass, oldBaseLibraryClass, androidApiLevelCompute, options);
  }
}
