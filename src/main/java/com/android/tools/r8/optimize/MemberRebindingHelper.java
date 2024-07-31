// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize;

import static com.android.tools.r8.utils.AndroidApiLevelUtils.isApiSafeForMemberRebinding;

import com.android.tools.r8.androidapi.AndroidApiLevelCompute;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndField;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexDefinitionSupplier;
import com.android.tools.r8.graph.DexEncodedMember;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexLibraryClass;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.LibraryMethod;
import com.android.tools.r8.graph.MethodResolutionResult.SingleResolutionResult;
import com.android.tools.r8.ir.code.InvokeType;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.AndroidApiLevelUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import com.google.common.collect.Iterables;
import java.util.function.BiFunction;

public class MemberRebindingHelper {

  protected final AndroidApiLevelCompute androidApiLevelCompute;
  protected final AppView<AppInfoWithLiveness> appView;
  protected final InternalOptions options;

  public MemberRebindingHelper(AppView<AppInfoWithLiveness> appView) {
    this.androidApiLevelCompute = appView.apiLevelCompute();
    this.appView = appView;
    this.options = appView.options();
  }

  public DexMethod validMemberRebindingTargetForNonProgramMethod(
      DexClassAndMethod resolvedMethod,
      SingleResolutionResult<?> resolutionResult,
      ProgramMethodSet contexts,
      InvokeType invokeType,
      DexMethod original) {
    assert !resolvedMethod.isProgramMethod();

    if (invokeType.isDirect()) {
      return original;
    }
    if ((invokeType.isSuper() && options.canHaveSuperInvokeBug())
        || (invokeType.isVirtual()
            && options.canHaveDalvikVerifyErrorOnVirtualInvokeWithMissingClasses()
            && canBreakDalvikWithRebinding(resolutionResult))) {
      // To preserve semantics we should find the first library method on the boundary.
      DexType firstLibraryTarget =
          firstLibraryClassOrFirstInterfaceTarget(
              resolutionResult.getResolvedHolder(),
              appView,
              resolvedMethod.getReference(),
              original.getHolderType(),
              DexClass::lookupMethod);
      if (firstLibraryTarget == null) {
        return original;
      }
      DexClass libraryHolder = appView.definitionFor(firstLibraryTarget);
      if (libraryHolder == null) {
        return original;
      }
      if (libraryHolder == resolvedMethod.getHolder()) {
        return resolvedMethod.getReference();
      }
      return resolvedMethod.getReference().withHolder(libraryHolder, appView.dexItemFactory());
    }

    LibraryMethod eligibleLibraryMethod = null;
    SingleResolutionResult<?> currentResolutionResult = resolutionResult;
    while (currentResolutionResult != null) {
      DexClassAndMethod currentResolvedMethod = currentResolutionResult.getResolutionPair();
      if (canRebindDirectlyToLibraryMethod(
          currentResolvedMethod,
          currentResolutionResult.withInitialResolutionHolder(
              currentResolutionResult.getResolvedHolder()),
          contexts,
          invokeType,
          original)) {
        eligibleLibraryMethod = currentResolvedMethod.asLibraryMethod();
      }
      if (appView.getAssumeInfoCollection().contains(currentResolvedMethod)) {
        break;
      }
      DexClass currentResolvedHolder = currentResolvedMethod.getHolder();
      if (resolvedMethod.getDefinition().belongsToVirtualPool()
          && !currentResolvedHolder.isInterface()
          && currentResolvedHolder.getSuperType() != null) {
        currentResolutionResult =
            appView
                .appInfo()
                .resolveMethodOnClassLegacy(currentResolvedHolder.getSuperType(), original)
                .asSingleResolution();
      } else {
        break;
      }
    }
    if (eligibleLibraryMethod != null) {
      return eligibleLibraryMethod.getReference();
    }

    DexType newHolder =
        firstLibraryClassOrFirstInterfaceTarget(
            resolvedMethod.getHolder(),
            appView,
            resolvedMethod.getReference(),
            original.getHolderType(),
            DexClass::lookupMethod);
    return newHolder != null ? original.withHolder(newHolder, appView.dexItemFactory()) : original;
  }

  // On dalvik we can have hard verification errors if we rebind to known library classes that
  // are super classes unknown library classes.
  private boolean canBreakDalvikWithRebinding(SingleResolutionResult<?> resolutionResult) {
    assert !resolutionResult.getResolvedHolder().isProgramClass();

    DexClass current = resolutionResult.getInitialResolutionHolder();
    while (current != null && !current.isLibraryClass()) {
      current = appView.definitionFor(current.getSuperType());
    }
    if (current == null) {
      assert resolutionResult.getResolvedHolder().isInterface();
      return false;
    }

    DexLibraryClass currentLibraryClass = current.asLibraryClass();
    while (currentLibraryClass != null) {
      if (!AndroidApiLevelUtils.isApiSafeForReference(currentLibraryClass, appView)) {
        return true;
      }
      if (!currentLibraryClass.hasSuperType()) {
        // Object
        break;
      }
      currentLibraryClass =
          DexLibraryClass.asLibraryClassOrNull(
              appView.definitionFor(currentLibraryClass.getSuperType()));
    }
    return false;
  }

  private boolean canRebindDirectlyToLibraryMethod(
      DexClassAndMethod resolvedMethod,
      SingleResolutionResult<?> resolutionResult,
      ProgramMethodSet contexts,
      InvokeType invokeType,
      DexMethod original) {
    // TODO(b/194422791): It could potentially be that `original.holder` is not a subtype of
    //  `original.holder` on all API levels, in which case it is not OK to rebind to the resolved
    //  method.
    return resolvedMethod.isLibraryMethod()
        && isAccessibleInAllContexts(resolvedMethod, resolutionResult, contexts)
        && !isInvokeSuperToInterfaceMethod(resolvedMethod, invokeType)
        && !isInvokeSuperToAbstractMethod(resolvedMethod, invokeType)
        && isApiSafeForMemberRebinding(
            resolvedMethod.asLibraryMethod(), original, androidApiLevelCompute, options);
  }

  private boolean isAccessibleInAllContexts(
      DexClassAndMethod resolvedMethod,
      SingleResolutionResult<?> resolutionResult,
      ProgramMethodSet contexts) {
    if (resolvedMethod.getHolder().isPublic() && resolvedMethod.getDefinition().isPublic()) {
      return true;
    }
    return Iterables.all(
        contexts,
        context -> resolutionResult.isAccessibleFrom(context, appView, appView.appInfo()).isTrue());
  }

  private boolean isInvokeSuperToInterfaceMethod(DexClassAndMethod method, InvokeType invokeType) {
    return method.getHolder().isInterface() && invokeType.isSuper();
  }

  private boolean isInvokeSuperToAbstractMethod(DexClassAndMethod method, InvokeType invokeType) {
    return options.canHaveSuperInvokeToAbstractMethodBug()
        && method.getAccessFlags().isAbstract()
        && invokeType.isSuper();
  }

  public static DexField validMemberRebindingTargetFor(
      DexDefinitionSupplier definitions, DexClassAndField field, DexField original) {
    if (field.isProgramField()) {
      return field.getReference();
    }
    DexClass fieldHolder = field.getHolder();
    DexType newHolder =
        firstLibraryClassOrFirstInterfaceTarget(
            fieldHolder,
            definitions,
            field.getReference(),
            original.getHolderType(),
            DexClass::lookupField);
    return newHolder != null
        ? original.withHolder(newHolder, definitions.dexItemFactory())
        : original;
  }

  private static <T> DexType firstLibraryClassOrFirstInterfaceTarget(
      DexClass holder,
      DexDefinitionSupplier definitions,
      T target,
      DexType current,
      BiFunction<DexClass, T, DexEncodedMember<?, ?>> lookup) {
    return holder.isInterface()
        ? firstLibraryClassForInterfaceTarget(definitions, target, current, lookup)
        : firstLibraryClass(definitions, current);
  }

  private static <T> DexType firstLibraryClassForInterfaceTarget(
      DexDefinitionSupplier definitions,
      T target,
      DexType current,
      BiFunction<DexClass, T, DexEncodedMember<?, ?>> lookup) {
    DexClass clazz = definitions.definitionFor(current);
    if (clazz == null) {
      return null;
    }
    if (!clazz.isProgramClass()) {
      DexEncodedMember<?, ?> potential = lookup.apply(clazz, target);
      if (potential != null) {
        // Found, return type.
        return current;
      }
    }
    if (clazz.hasSuperType()) {
      DexType matchingSuper =
          firstLibraryClassForInterfaceTarget(definitions, target, clazz.getSuperType(), lookup);
      if (matchingSuper != null) {
        // Found in supertype, return first library class.
        return clazz.isNotProgramClass() ? current : matchingSuper;
      }
    }
    for (DexType iface : clazz.getInterfaces()) {
      DexType matchingIface =
          firstLibraryClassForInterfaceTarget(definitions, target, iface, lookup);
      if (matchingIface != null) {
        // Found in interface, return first library class.
        return clazz.isNotProgramClass() ? current : matchingIface;
      }
    }
    return null;
  }

  private static DexType firstLibraryClass(DexDefinitionSupplier definitions, DexType bottom) {
    DexClass searchClass = definitions.contextIndependentDefinitionFor(bottom);
    while (searchClass != null && searchClass.isProgramClass()) {
      searchClass =
          definitions.definitionFor(searchClass.getSuperType(), searchClass.asProgramClass());
    }
    return searchClass != null ? searchClass.getType() : null;
  }
}
