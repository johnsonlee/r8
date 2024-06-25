// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.verticalclassmerging;

import com.android.tools.r8.classmerging.Policy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.verticalclassmerging.policies.NoAbstractMethodsOnAbstractClassesPolicy;
import com.android.tools.r8.verticalclassmerging.policies.NoAnnotationClassesPolicy;
import com.android.tools.r8.verticalclassmerging.policies.NoClassInitializationChangesPolicy;
import com.android.tools.r8.verticalclassmerging.policies.NoDirectlyInstantiatedClassesPolicy;
import com.android.tools.r8.verticalclassmerging.policies.NoEnclosingMethodAttributesPolicy;
import com.android.tools.r8.verticalclassmerging.policies.NoFieldResolutionChangesPolicy;
import com.android.tools.r8.verticalclassmerging.policies.NoIllegalAccessesPolicy;
import com.android.tools.r8.verticalclassmerging.policies.NoInnerClassAttributesPolicy;
import com.android.tools.r8.verticalclassmerging.policies.NoInterfacesWithInvokeSpecialToDefaultMethodIntoClassPolicy;
import com.android.tools.r8.verticalclassmerging.policies.NoInterfacesWithUnknownSubtypesPolicy;
import com.android.tools.r8.verticalclassmerging.policies.NoInvokeSuperNoSuchMethodErrorsPolicy;
import com.android.tools.r8.verticalclassmerging.policies.NoKeptClassesPolicy;
import com.android.tools.r8.verticalclassmerging.policies.NoLockMergingPolicy;
import com.android.tools.r8.verticalclassmerging.policies.NoMethodResolutionChangesPolicy;
import com.android.tools.r8.verticalclassmerging.policies.NoNestedMergingPolicy;
import com.android.tools.r8.verticalclassmerging.policies.NoNonSerializableClassIntoSerializableClassPolicy;
import com.android.tools.r8.verticalclassmerging.policies.NoServiceInterfacesPolicy;
import com.android.tools.r8.verticalclassmerging.policies.NoShadowedFieldsPolicy;
import com.android.tools.r8.verticalclassmerging.policies.SameApiReferenceLevelPolicy;
import com.android.tools.r8.verticalclassmerging.policies.SameFeatureSplitPolicy;
import com.android.tools.r8.verticalclassmerging.policies.SameMainDexGroupPolicy;
import com.android.tools.r8.verticalclassmerging.policies.SameNestPolicy;
import com.android.tools.r8.verticalclassmerging.policies.SameStartupPartitionPolicy;
import com.android.tools.r8.verticalclassmerging.policies.SuccessfulVirtualMethodResolutionInTargetPolicy;
import java.util.List;

public class VerticalClassMergerPolicyScheduler {

  public static List<Policy> getPolicies(AppView<AppInfoWithLiveness> appView) {
    return List.of(
        new NoDirectlyInstantiatedClassesPolicy(appView),
        new NoInterfacesWithUnknownSubtypesPolicy(appView),
        new NoKeptClassesPolicy(appView),
        new SameFeatureSplitPolicy(appView),
        new SameStartupPartitionPolicy(appView),
        new NoServiceInterfacesPolicy(appView),
        new NoAnnotationClassesPolicy(),
        new NoNonSerializableClassIntoSerializableClassPolicy(appView),
        new NoEnclosingMethodAttributesPolicy(),
        new NoInnerClassAttributesPolicy(),
        new SameNestPolicy(),
        new SameMainDexGroupPolicy(appView),
        new NoLockMergingPolicy(appView),
        new SameApiReferenceLevelPolicy(appView),
        new NoFieldResolutionChangesPolicy(appView),
        new NoMethodResolutionChangesPolicy(appView),
        new NoIllegalAccessesPolicy(appView),
        new NoClassInitializationChangesPolicy(appView),
        new NoInterfacesWithInvokeSpecialToDefaultMethodIntoClassPolicy(appView),
        new NoInvokeSuperNoSuchMethodErrorsPolicy(appView),
        new SuccessfulVirtualMethodResolutionInTargetPolicy(appView),
        new NoAbstractMethodsOnAbstractClassesPolicy(appView),
        new NoShadowedFieldsPolicy(appView),
        new NoNestedMergingPolicy());
  }
}
