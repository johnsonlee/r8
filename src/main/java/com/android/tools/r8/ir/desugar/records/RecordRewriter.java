// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.records;

import static com.android.tools.r8.graph.lens.GraphLens.getIdentityLens;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.ir.desugar.records.RecordRewriterHelper.RecordInvokeDynamic;
import java.util.ArrayList;

/** Used to rewrite invokedynamic/invoke-custom when shrinking and minifying records. */
public class RecordRewriter {

  public static RecordRewriter create(AppView<?> appView) {
    if (appView.enableWholeProgramOptimizations()) {
      return new RecordRewriter();
    }
    return null;
  }

  private RecordRewriter() {}

  public DexField[] computePresentFields(
      GraphLens graphLens, RecordInvokeDynamic recordInvokeDynamic) {
    ArrayList<DexField> finalFields = new ArrayList<>();
    for (DexField field : recordInvokeDynamic.getFields()) {
      DexEncodedField dexEncodedField =
          recordInvokeDynamic
              .getRecordClass()
              .lookupInstanceField(graphLens.getRenamedFieldSignature(field, getIdentityLens()));
      if (dexEncodedField != null) {
        finalFields.add(field);
      }
    }
    DexField[] newFields = new DexField[finalFields.size()];
    for (int i = 0; i < finalFields.size(); i++) {
      newFields[i] = finalFields.get(i);
    }
    return newFields;
  }
}
