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
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexValue.DexItemBasedValueString;
import com.android.tools.r8.graph.DexValue.DexValueString;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.naming.dexitembasedstring.NameComputationInfo;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.ThreadUtils;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

/** Replaces all instances of DexItemBasedValueString by DexValueString. */
public class IdentifierMinifier {

  private final AppView<? extends AppInfoWithClassHierarchy> appView;

  public IdentifierMinifier(AppView<? extends AppInfoWithClassHierarchy> appView) {
    this.appView = appView;
  }

  public void run(ExecutorService executorService) throws ExecutionException {
    adaptClassStringsInStaticFields(executorService);
    replaceDexItemBasedConstStringInStaticFields(executorService);
  }

  private void adaptClassStringsInStaticFields(ExecutorService executorService)
      throws ExecutionException {
    if (appView.options().getProguardConfiguration().getAdaptClassStrings().isEmpty()) {
      return;
    }
    ThreadUtils.processItems(
        appView.appInfo().classes(),
        clazz -> {
          if (appView.getKeepInfo(clazz).isAdaptClassStringsEnabled()) {
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
        clazz ->
            clazz.forEachProgramStaticFieldMatching(
                IdentifierMinifier::hasExplicitStaticDexItemBasedValueString,
                this::replaceDexItemBasedConstStringInStaticField),
        appView.options().getThreadingModule(),
        executorService);
  }

  private void replaceDexItemBasedConstStringInStaticField(ProgramField field) {
    DexItemBasedValueString staticValue =
        field.getDefinition().getStaticValue().asDexItemBasedValueString();
    DexString replacement =
        staticValue.getNameComputationInfo().computeNameFor(staticValue.getValue(), appView);
    field.getDefinition().setStaticValue(new DexValueString(replacement));
  }

  public void rewriteDexItemBasedConstStringInStaticFields(ExecutorService executorService)
      throws ExecutionException {
    ThreadUtils.processItems(
        appView.appInfo().classes(),
        clazz ->
            clazz.forEachProgramStaticFieldMatching(
                IdentifierMinifier::hasExplicitStaticDexItemBasedValueString,
                this::rewriteDexItemBasedConstStringInStaticFields),
        appView.options().getThreadingModule(),
        executorService);
  }

  private void rewriteDexItemBasedConstStringInStaticFields(ProgramField field) {
    DexItemBasedValueString staticValue =
        field.getDefinition().getStaticValue().asDexItemBasedValueString();
    DexReference rewrittenReference =
        appView.graphLens().getRenamedReference(staticValue.getValue(), appView.codeLens());
    NameComputationInfo<?> rewrittenNameComputationInfo =
        staticValue
            .getNameComputationInfo()
            .rewrittenWithLens(appView.graphLens(), appView.codeLens());
    field
        .getDefinition()
        .setStaticValue(
            new DexItemBasedValueString(rewrittenReference, rewrittenNameComputationInfo));
  }

  private static boolean hasExplicitStaticDexItemBasedValueString(DexEncodedField field) {
    return field.hasExplicitStaticValue() && field.getStaticValue().isDexItemBasedValueString();
  }
}
