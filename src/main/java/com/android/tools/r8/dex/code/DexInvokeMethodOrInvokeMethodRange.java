// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.code;

import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.ir.code.InvokeType;

public interface DexInvokeMethodOrInvokeMethodRange {

  DexMethod getMethod();

  InvokeType getType();

  boolean isInvokeMethodOrInvokeMethodRange();

  DexInstruction asDexInstruction();

  DexInvokeMethodOrInvokeMethodRange withMethod(DexMethod method);
}
