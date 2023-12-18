// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.verticalclassmerging.policies;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.LookupResult.LookupResultSuccess;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.Box;
import com.android.tools.r8.utils.ObjectUtils;
import com.android.tools.r8.verticalclassmerging.VerticalMergeGroup;
import java.util.ArrayList;
import java.util.List;

public class NoMethodResolutionChangesPolicy extends VerticalClassMergerPolicy {

  private final AppView<AppInfoWithLiveness> appView;

  public NoMethodResolutionChangesPolicy(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
  }

  @Override
  public boolean canMerge(VerticalMergeGroup group) {
    return !methodResolutionMayChange(group.getSource(), group.getTarget());
  }

  private boolean methodResolutionMayChange(DexProgramClass source, DexProgramClass target) {
    for (DexEncodedMethod virtualSourceMethod : source.virtualMethods()) {
      DexEncodedMethod directTargetMethod =
          target.lookupDirectMethod(virtualSourceMethod.getReference());
      if (directTargetMethod != null) {
        // A private method shadows a virtual method. This situation is rare, since it is not
        // allowed by javac. Therefore, we just give up in this case. (In principle, it would be
        // possible to rename the private method in the subclass, and then move the virtual method
        // to the subclass without changing its name.)
        return true;
      }
    }

    // When merging an interface into a class, all instructions on the form "invoke-interface
    // [source].m" are changed into "invoke-virtual [target].m". We need to abort the merge if this
    // transformation could hide IncompatibleClassChangeErrors.
    if (source.isInterface() && !target.isInterface()) {
      List<DexEncodedMethod> defaultMethods = new ArrayList<>();
      for (DexEncodedMethod virtualMethod : source.virtualMethods()) {
        if (!virtualMethod.accessFlags.isAbstract()) {
          defaultMethods.add(virtualMethod);
        }
      }

      // For each of the default methods, the subclass [target] could inherit another default method
      // with the same signature from another interface (i.e., there is a conflict). In such cases,
      // instructions on the form "invoke-interface [source].foo()" will fail with an Incompatible-
      // ClassChangeError.
      //
      // Example:
      //   interface I1 { default void m() {} }
      //   interface I2 { default void m() {} }
      //   class C implements I1, I2 {
      //     ... invoke-interface I1.m ... <- IncompatibleClassChangeError
      //   }
      for (DexEncodedMethod method : defaultMethods) {
        // Conservatively find all possible targets for this method.
        LookupResultSuccess lookupResult =
            appView
                .appInfo()
                .resolveMethodOnInterfaceLegacy(method.getHolderType(), method.getReference())
                .lookupVirtualDispatchTargets(target, appView)
                .asLookupResultSuccess();
        assert lookupResult != null;
        if (lookupResult == null) {
          return true;
        }
        if (lookupResult.contains(method)) {
          Box<Boolean> found = new Box<>(false);
          lookupResult.forEach(
              interfaceTarget -> {
                if (ObjectUtils.identical(interfaceTarget.getDefinition(), method)) {
                  return;
                }
                DexClass enclosingClass = interfaceTarget.getHolder();
                if (enclosingClass != null && enclosingClass.isInterface()) {
                  // Found a default method that is different from the one in [source], aborting.
                  found.set(true);
                }
              },
              lambdaTarget -> {
                // The merger should already have excluded lambda implemented interfaces.
                assert false;
              });
          if (found.get()) {
            return true;
          }
        }
      }
    }
    return false;
  }

  @Override
  public String getName() {
    return "NoMethodResolutionChangesPolicy";
  }
}
