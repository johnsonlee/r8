// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import com.android.tools.r8.graph.AbstractAccessContexts;
import com.android.tools.r8.graph.AbstractAccessContexts.ConcreteAccessContexts;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.FieldAccessInfoImpl;
import com.android.tools.r8.graph.MutableFieldAccessInfo;
import com.android.tools.r8.graph.MutableFieldAccessInfoCollection;
import com.android.tools.r8.graph.ProgramMethod;
import java.util.IdentityHashMap;
import java.util.Map;

public class FieldAccessInfoCollectionModifier {

  private static class FieldAccessContexts {

    private AbstractAccessContexts writesWithContexts = AbstractAccessContexts.empty();

    void addWriteContext(DexField field, ProgramMethod context) {
      if (writesWithContexts.isBottom()) {
        ConcreteAccessContexts concreteWriteContexts = new ConcreteAccessContexts();
        concreteWriteContexts.recordAccess(field, context);
        writesWithContexts = concreteWriteContexts;
      } else if (writesWithContexts.isConcrete()) {
        writesWithContexts.asConcrete().recordAccess(field, context);
      } else {
        assert writesWithContexts.isTop();
      }
    }

    void recordWriteInUnknownContext() {
      writesWithContexts = AbstractAccessContexts.unknown();
    }
  }

  private final Map<DexField, FieldAccessContexts> newFieldAccessContexts;

  private FieldAccessInfoCollectionModifier(
      Map<DexField, FieldAccessContexts> newFieldAccessContexts) {
    this.newFieldAccessContexts = newFieldAccessContexts;
  }

  public static Builder builder() {
    return new Builder();
  }

  public void modify(AppView<AppInfoWithLiveness> appView) {
    MutableFieldAccessInfoCollection<?, ? extends MutableFieldAccessInfo>
        mutableFieldAccessInfoCollection = appView.appInfo().getMutableFieldAccessInfoCollection();
    newFieldAccessContexts.forEach(
        (field, accessContexts) -> {
          FieldAccessInfoImpl fieldAccessInfo = new FieldAccessInfoImpl(field);
          fieldAccessInfo.setReadDirectly();
          fieldAccessInfo.setReadsWithContexts(AbstractAccessContexts.unknown());
          fieldAccessInfo.setWritesWithContexts(accessContexts.writesWithContexts);
          mutableFieldAccessInfoCollection.extend(field, fieldAccessInfo);
        });
  }

  public static class Builder {

    private final Map<DexField, FieldAccessContexts> newFieldAccessContexts =
        new IdentityHashMap<>();

    public Builder() {}

    private FieldAccessContexts getFieldAccessContexts(DexField field) {
      return newFieldAccessContexts.computeIfAbsent(field, ignore -> new FieldAccessContexts());
    }

    public void recordFieldWrittenInContext(DexField field, ProgramMethod context) {
      getFieldAccessContexts(field).addWriteContext(field, context);
    }

    public void recordFieldWriteInUnknownContext(DexField field) {
      getFieldAccessContexts(field).recordWriteInUnknownContext();
    }

    public FieldAccessInfoCollectionModifier build() {
      return new FieldAccessInfoCollectionModifier(newFieldAccessContexts);
    }
  }
}
