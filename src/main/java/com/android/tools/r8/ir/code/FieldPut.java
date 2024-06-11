// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.code;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.FieldResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;

public interface FieldPut {

  BasicBlock getBlock();

  DexField getField();

  Position getPosition();

  int getValueIndex();

  Value value();

  void setValue(Value value);

  FieldInstruction asFieldInstruction();

  boolean isInstancePut();

  InstancePut asInstancePut();

  boolean isStaticPut();

  StaticPut asStaticPut();

  FieldResolutionResult resolveField(
      AppView<? extends AppInfoWithClassHierarchy> appView, ProgramMethod context);
}
