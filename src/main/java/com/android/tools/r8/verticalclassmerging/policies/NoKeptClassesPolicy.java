// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.verticalclassmerging.policies;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.profile.art.ArtProfile;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.KeepInfoCollection;
import com.android.tools.r8.utils.ConsumerUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.verticalclassmerging.VerticalMergeGroup;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class NoKeptClassesPolicy extends VerticalClassMergerPolicy {

  private final AppView<AppInfoWithLiveness> appView;
  private final InternalOptions options;
  private final Set<DexProgramClass> keptClasses;

  public NoKeptClassesPolicy(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
    this.options = appView.options();
    this.keptClasses = getPinnedClasses();
  }

  @Override
  public boolean canMerge(VerticalMergeGroup group) {
    DexProgramClass sourceClass = group.getSource();
    return appView.getKeepInfo(sourceClass).isVerticalClassMergingAllowed(options)
        && !keptClasses.contains(sourceClass);
  }

  @Override
  public String getName() {
    return "NoKeptClassesPolicy";
  }

  // Returns a set of types that must not be merged into other types.
  private Set<DexProgramClass> getPinnedClasses() {
    Set<DexProgramClass> pinnedClasses = Sets.newIdentityHashSet();

    // For all pinned fields, also pin the type of the field (because changing the type of the field
    // implicitly changes the signature of the field). Similarly, for all pinned methods, also pin
    // the return type and the parameter types of the method.
    // TODO(b/156715504): Compute referenced-by-pinned in the keep info objects.
    List<DexReference> pinnedReferences = new ArrayList<>();
    KeepInfoCollection keepInfo = appView.getKeepInfo();
    keepInfo.forEachPinnedType(pinnedReferences::add, options);
    keepInfo.forEachPinnedMethod(pinnedReferences::add, options);
    keepInfo.forEachPinnedField(pinnedReferences::add, options);
    extractPinnedClasses(pinnedReferences, pinnedClasses);

    for (DexProgramClass clazz : appView.appInfo().classes()) {
      if (Iterables.any(clazz.methods(), method -> method.getAccessFlags().isNative())) {
        markClassAsPinned(clazz, pinnedClasses);
      }
    }

    // It is valid to have an invoke-direct instruction in a default interface method that targets
    // another default method in the same interface (see InterfaceMethodDesugaringTests.testInvoke-
    // SpecialToDefaultMethod). However, in a class, that would lead to a verification error.
    // Therefore, we disallow merging such interfaces into their subtypes.
    for (DexMethod signature : appView.appInfo().getVirtualMethodsTargetedByInvokeDirect()) {
      markTypeAsPinned(signature.getHolderType(), pinnedClasses);
    }

    // The set of targets that must remain for proper resolution error cases should not be merged.
    // TODO(b/192821424): Can be removed if handled.
    extractPinnedClasses(appView.appInfo().getFailedMethodResolutionTargets(), pinnedClasses);

    // The ART profiles may contain method rules that do not exist in the app. These method may
    // refer to classes that will be vertically merged into their unique subtype, but the vertical
    // class merger lens will not contain any mappings for the missing methods in the ART profiles.
    // Therefore, trying to perform a lens lookup on these methods will fail.
    for (ArtProfile artProfile : appView.getArtProfileCollection()) {
      artProfile.forEachRule(
          ConsumerUtils.emptyThrowingConsumer(),
          methodRule -> {
            DexMethod method = methodRule.getMethod();
            if (method.getHolderType().isArrayType()) {
              return;
            }
            DexClass holder =
                appView.appInfo().definitionForWithoutExistenceAssert(method.getHolderType());
            if (method.lookupOnClass(holder) == null) {
              extractPinnedClasses(methodRule.getMethod(), pinnedClasses);
            }
          });
    }

    return pinnedClasses;
  }

  private <T extends DexReference> void extractPinnedClasses(
      Iterable<T> items, Set<DexProgramClass> pinnedClasses) {
    for (DexReference item : items) {
      extractPinnedClasses(item, pinnedClasses);
    }
  }

  private void extractPinnedClasses(DexReference reference, Set<DexProgramClass> pinnedClasses) {
    markTypeAsPinned(reference.getContextType(), pinnedClasses);
    reference.accept(
        ConsumerUtils.emptyConsumer(),
        field -> {
          // Pin the type of the field.
          markTypeAsPinned(field.getType(), pinnedClasses);
        },
        method -> {
          // Pin the return type and the parameter types of the method. If we were to merge any of
          // these types into their sub classes, then we would implicitly change the signature of
          // this method.
          for (DexType type : method.getReferencedTypes()) {
            markTypeAsPinned(type, pinnedClasses);
          }
        });
  }

  private void markTypeAsPinned(DexType type, Set<DexProgramClass> pinnedClasses) {
    DexType baseType = type.toBaseType(appView.dexItemFactory());
    if (!baseType.isClassType()) {
      return;
    }

    DexProgramClass clazz =
        asProgramClassOrNull(appView.appInfo().definitionForWithoutExistenceAssert(baseType));
    if (clazz != null && !appView.getKeepInfo(clazz).isPinned(options)) {
      // We check for the case where the type is pinned according to its keep info, so we only need
      // to add it here if it is not the case.
      markClassAsPinned(clazz, pinnedClasses);
    }
  }

  private void markClassAsPinned(DexProgramClass clazz, Set<DexProgramClass> pinnedClasses) {
    pinnedClasses.add(clazz);
  }
}
