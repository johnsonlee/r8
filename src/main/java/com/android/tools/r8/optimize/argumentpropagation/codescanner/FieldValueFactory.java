// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation.codescanner;

import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.ProgramField;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FieldValueFactory {

  private final Map<DexField, FieldValue> fieldValues = new ConcurrentHashMap<>();

  public FieldValue create(ProgramField field) {
    return fieldValues.computeIfAbsent(field.getReference(), FieldValue::new);
  }
}
