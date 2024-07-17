// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.verticalclassmerging.policies;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.FieldSignatureEquivalence;
import com.android.tools.r8.verticalclassmerging.VerticalMergeGroup;
import com.google.common.base.Equivalence.Wrapper;
import java.util.HashSet;
import java.util.Set;

public class NoFieldResolutionChangesPolicy extends VerticalClassMergerPolicy {

  private final AppView<AppInfoWithLiveness> appView;

  public NoFieldResolutionChangesPolicy(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
  }

  @Override
  public boolean canMerge(VerticalMergeGroup group) {
    // Field resolution first considers the direct interfaces of [targetClass] before it proceeds
    // to the super class.
    return !fieldResolutionMayChange(group.getSource(), group.getTarget());
  }

  private boolean fieldResolutionMayChange(DexClass source, DexClass target) {
    if (source.getType().isIdenticalTo(target.getSuperType())) {
      // If there is a "iget Target.f" or "iput Target.f" instruction in target, and the class
      // Target implements an interface that declares a static final field f, this should yield an
      // IncompatibleClassChangeError.
      // TODO(christofferqa): In the following we only check if a static field from an interface
      //  shadows an instance field from [source]. We could actually check if there is an iget/iput
      //  instruction whose resolution would be affected by the merge. The situation where a static
      //  field shadows an instance field is probably not widespread in practice, though.
      FieldSignatureEquivalence equivalence = FieldSignatureEquivalence.get();
      Set<Wrapper<DexField>> staticFieldsInInterfacesOfTarget = new HashSet<>();
      for (DexType interfaceType : target.getInterfaces()) {
        DexClass itf = appView.definitionFor(interfaceType);
        if (itf == null) {
          // See b/353475583.
          return true;
        }
        for (DexEncodedField staticField : itf.staticFields()) {
          staticFieldsInInterfacesOfTarget.add(equivalence.wrap(staticField.getReference()));
        }
      }
      for (DexEncodedField instanceField : source.instanceFields()) {
        if (staticFieldsInInterfacesOfTarget.contains(
            equivalence.wrap(instanceField.getReference()))) {
          // An instruction "iget Target.f" or "iput Target.f" that used to hit a static field in an
          // interface would now hit an instance field from [source], so that an IncompatibleClass-
          // ChangeError would no longer be thrown. Abort merge.
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public String getName() {
    return "NoFieldResolutionChangesPolicy";
  }
}
