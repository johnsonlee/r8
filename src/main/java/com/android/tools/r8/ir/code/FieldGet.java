// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.code;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.FieldResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.type.TypeElement;

public interface FieldGet {

  DexField getField();

  TypeElement getOutType();

  FieldInstruction asFieldInstruction();

  boolean isInstanceGet();

  InstanceGet asInstanceGet();

  boolean isStaticGet();

  boolean hasUsedOutValue();

  Value outValue();

  FieldResolutionResult resolveField(
      AppView<? extends AppInfoWithClassHierarchy> appView, ProgramMethod context);
}
