// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.FieldAccessInfoImpl;
import com.android.tools.r8.graph.MutableFieldAccessInfo;
import com.android.tools.r8.graph.MutableFieldAccessInfoCollection;
import com.google.common.collect.Sets;
import java.util.Set;

public class FieldAccessInfoCollectionModifier {

  private final Set<DexField> newFields;

  private FieldAccessInfoCollectionModifier(Set<DexField> newFields) {
    this.newFields = newFields;
  }

  public static Builder builder() {
    return new Builder();
  }

  public void modify(AppView<AppInfoWithLiveness> appView) {
    MutableFieldAccessInfoCollection<?, ? extends MutableFieldAccessInfo>
        mutableFieldAccessInfoCollection = appView.appInfo().getMutableFieldAccessInfoCollection();
    for (DexField field : newFields) {
      FieldAccessInfoImpl fieldAccessInfo = new FieldAccessInfoImpl(field, 0, null);
      assert !fieldAccessInfo.hasKnownReadContexts();
      assert !fieldAccessInfo.hasKnownWriteContexts();
      fieldAccessInfo.setReadDirectly();
      fieldAccessInfo.setWrittenDirectly();
      mutableFieldAccessInfoCollection.extend(field, fieldAccessInfo);
    }
  }

  public static class Builder {

    private final Set<DexField> newFields = Sets.newIdentityHashSet();

    public Builder() {}

    public void addField(DexField field) {
      newFields.add(field);
    }

    public FieldAccessInfoCollectionModifier build() {
      return new FieldAccessInfoCollectionModifier(newFields);
    }
  }
}
