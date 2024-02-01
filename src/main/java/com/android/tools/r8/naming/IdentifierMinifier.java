// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import static com.android.tools.r8.graph.lens.GraphLens.getIdentityLens;
import static com.android.tools.r8.utils.DescriptorUtils.descriptorToJavaType;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexValue.DexItemBasedValueString;
import com.android.tools.r8.graph.DexValue.DexValueString;
import com.android.tools.r8.shaking.ProguardClassFilter;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.ThreadUtils;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

/** Replaces all instances of DexItemBasedValueString by DexValueString. */
public class IdentifierMinifier {

  private final AppView<? extends AppInfoWithClassHierarchy> appView;
  private final ProguardClassFilter adaptClassStrings;
  private final NamingLens lens;

  public IdentifierMinifier(AppView<? extends AppInfoWithClassHierarchy> appView) {
    this.appView = appView;
    this.adaptClassStrings = appView.options().getProguardConfiguration().getAdaptClassStrings();
    this.lens = appView.getNamingLens();
  }

  public void run(ExecutorService executorService) throws ExecutionException {
    adaptClassStringsInStaticFields(executorService);
    replaceDexItemBasedConstStringInStaticFields(executorService);
  }

  private void adaptClassStringsInStaticFields(ExecutorService executorService)
      throws ExecutionException {
    if (adaptClassStrings.isEmpty()) {
      return;
    }
    ThreadUtils.processItems(
        appView.appInfo().classes(),
        clazz -> {
          if (adaptClassStrings.matches(clazz.getType())) {
            for (DexEncodedField field : clazz.staticFields()) {
              adaptClassStringsInStaticField(field);
            }
          }
        },
        appView.options().getThreadingModule(),
        executorService);
  }

  private void adaptClassStringsInStaticField(DexEncodedField field) {
    assert field.isStatic();
    DexValueString staticValue = field.getStaticValue().asDexValueString();
    if (staticValue != null) {
      field.setStaticValue(
          new DexValueString(getRenamedStringLiteral(appView, staticValue.getValue())));
    }
  }

  public static DexString getRenamedStringLiteral(
      AppView<? extends AppInfoWithClassHierarchy> appView, DexString originalLiteral) {
    String descriptor =
        DescriptorUtils.javaTypeToDescriptorIfValidJavaType(originalLiteral.toString());
    if (descriptor == null) {
      return originalLiteral;
    }
    DexType type = appView.dexItemFactory().createType(descriptor);
    DexType originalType = appView.graphLens().getOriginalType(type, getIdentityLens());
    if (originalType.isNotIdenticalTo(type)) {
      // The type has changed to something clashing with the string.
      return originalLiteral;
    }
    DexType rewrittenType = appView.graphLens().lookupType(type, getIdentityLens());
    DexClass clazz = appView.appInfo().definitionForWithoutExistenceAssert(rewrittenType);
    if (clazz == null || clazz.isNotProgramClass()) {
      return originalLiteral;
    }
    DexString rewrittenString = appView.getNamingLens().lookupClassDescriptor(rewrittenType);
    return rewrittenString == null
        ? originalLiteral
        : appView.dexItemFactory().createString(descriptorToJavaType(rewrittenString.toString()));
  }

  private void replaceDexItemBasedConstStringInStaticFields(ExecutorService executorService)
      throws ExecutionException {
    ThreadUtils.processItems(
        appView.appInfo().classes(),
        clazz -> {
          // Some const strings could be moved to field's static value (from <clinit>).
          for (DexEncodedField field : clazz.staticFields()) {
            replaceDexItemBasedConstStringInStaticField(field);
          }
        },
        appView.options().getThreadingModule(),
        executorService);
  }

  private void replaceDexItemBasedConstStringInStaticField(DexEncodedField field) {
    assert field.isStatic();
    DexItemBasedValueString staticValue = field.getStaticValue().asDexItemBasedValueString();
    if (staticValue != null) {
      DexString replacement =
          staticValue
              .getNameComputationInfo()
              .computeNameFor(staticValue.getValue(), appView, appView.graphLens(), lens);
      field.setStaticValue(new DexValueString(replacement));
    }
  }
}
