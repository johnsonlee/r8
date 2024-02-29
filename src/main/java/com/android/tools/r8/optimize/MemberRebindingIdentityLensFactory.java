// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMember;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.FieldAccessInfoCollection;
import com.android.tools.r8.graph.MethodAccessInfoCollection;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.ReferencedMembersCollector;
import com.android.tools.r8.graph.ReferencedMembersCollector.ReferencedMembersConsumer;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class MemberRebindingIdentityLensFactory {

  public static MemberRebindingIdentityLens createFromAppInfo(
      AppView<AppInfoWithLiveness> appView) {
    FieldAccessInfoCollection<?> fieldAccessInfoCollection =
        appView.appInfo().getFieldAccessInfoCollection();
    MethodAccessInfoCollection methodAccessInfoCollection =
        appView.appInfo().getMethodAccessInfoCollection();
    return create(appView, fieldAccessInfoCollection, methodAccessInfoCollection);
  }

  public static MemberRebindingIdentityLens createFromLir(
      AppView<? extends AppInfoWithClassHierarchy> appView, ExecutorService executorService)
      throws ExecutionException {
    MemberRebindingIdentityLens.Builder builder =
        MemberRebindingIdentityLens.concurrentBuilder(appView);
    Set<DexMember<?, ?>> seen = ConcurrentHashMap.newKeySet();
    ReferencedMembersConsumer consumer =
        new ReferencedMembersConsumer() {
          @Override
          public void onFieldReference(DexField field, ProgramMethod context) {
            if (seen.add(field)) {
              builder.recordFieldAccess(field);
            }
          }

          @Override
          public void onMethodReference(DexMethod method, ProgramMethod context) {
            if (seen.add(method)) {
              builder.recordMethodAccess(method);
            }
          }
        };
    new ReferencedMembersCollector(appView, consumer).run(executorService);
    return builder.build();
  }

  public static MemberRebindingIdentityLens create(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      FieldAccessInfoCollection<?> fieldAccessInfoCollection,
      MethodAccessInfoCollection methodAccessInfoCollection) {
    MemberRebindingIdentityLens.Builder builder = MemberRebindingIdentityLens.builder(appView);
    fieldAccessInfoCollection.forEach(builder::recordNonReboundFieldAccesses);
    methodAccessInfoCollection.forEachMethodReference(builder::recordMethodAccess);
    return builder.build();
  }
}
