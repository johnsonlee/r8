// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.argumentpropagation.computation;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.AbstractValueFactory;
import com.android.tools.r8.ir.analysis.value.UnknownValue;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionOrValue;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.FieldValueFactory;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.MethodParameterFactory;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.IdentityHashMap;
import java.util.Map;

public abstract class ComputationTreeBuilder {

  final AppView<AppInfoWithLiveness> appView;
  final IRCode code;
  final ProgramMethod method;
  final FieldValueFactory fieldValueFactory;
  final MethodParameterFactory methodParameterFactory;

  private final Map<InstructionOrValue, ComputationTreeNode> cache = new IdentityHashMap<>();

  public ComputationTreeBuilder(
      AppView<AppInfoWithLiveness> appView,
      IRCode code,
      ProgramMethod method,
      FieldValueFactory fieldValueFactory,
      MethodParameterFactory methodParameterFactory) {
    this.appView = appView;
    this.code = code;
    this.method = method;
    this.fieldValueFactory = fieldValueFactory;
    this.methodParameterFactory = methodParameterFactory;
  }

  AbstractValueFactory factory() {
    return appView.abstractValueFactory();
  }

  // TODO(b/302281503): "Long lived" computation trees (i.e., the ones that survive past the IR
  //  conversion of the current method) should be canonicalized.
  public final ComputationTreeNode getOrBuildComputationTree(
      InstructionOrValue instructionOrValue) {
    ComputationTreeNode existing = cache.get(instructionOrValue);
    if (existing != null) {
      return existing;
    }
    ComputationTreeNode result = buildComputationTree(instructionOrValue);
    cache.put(instructionOrValue, result);
    return result;
  }

  private ComputationTreeNode buildComputationTree(InstructionOrValue instructionOrValue) {
    if (instructionOrValue.isInstruction()) {
      return buildComputationTree(instructionOrValue.asInstruction());
    } else {
      Value value = instructionOrValue.asValue();
      if (value.isPhi()) {
        return buildComputationTree(value.asPhi());
      } else {
        return buildComputationTree(value.getDefinition());
      }
    }
  }

  abstract ComputationTreeNode buildComputationTree(Instruction instruction);

  abstract ComputationTreeNode buildComputationTree(Phi phi);

  static UnknownValue unknown() {
    return AbstractValue.unknown();
  }
}
