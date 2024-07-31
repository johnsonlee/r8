// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.argumentpropagation.propagation;

import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.NewInstance;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.utils.collections.ProgramFieldSet;
import com.google.common.collect.Sets;
import java.util.Set;

/**
 * The state we track during the field-maybe-read-before-write/field-never-read-before-written
 * analysis.
 */
public class FieldReadBeforeWriteDfsAnalysisState {

  // The current allocation we are analyzing.
  private final NewInstance newInstance;

  // The first instruction on the current program path starting from the `newInstance` instruction
  // from which the `newInstance` value escapes.
  private Instruction escape = null;

  // The set of values that *may* be aliases of the `newInstance` value.
  private final Set<Value> instanceAliases = Sets.newIdentityHashSet();

  // The set of blocks on the current program path.
  private final Set<BasicBlock> stack = Sets.newIdentityHashSet();

  // The set of fields that are guaranteed to be written before they are read on the current program
  // path.
  private final ProgramFieldSet writtenBeforeRead = ProgramFieldSet.create();

  FieldReadBeforeWriteDfsAnalysisState(NewInstance newInstance) {
    this.newInstance = newInstance;
  }

  void addInstanceAlias(Value instanceAlias) {
    boolean changed = instanceAliases.add(instanceAlias);
    assert changed;
  }

  void addBlockToStack(BasicBlock block) {
    boolean changed = stack.add(block);
    assert changed;
  }

  void addWrittenBeforeRead(ProgramField field) {
    writtenBeforeRead.add(field);
  }

  NewInstance getNewInstance() {
    return newInstance;
  }

  boolean isBlockOnStack(BasicBlock block) {
    return stack.contains(block);
  }

  boolean isEscaped() {
    return escape != null;
  }

  boolean isDefinitelyInstance(Value value) {
    return value.getAliasedValue() == newInstance.outValue();
  }

  boolean isMaybeInstance(Value value) {
    return instanceAliases.contains(value);
  }

  boolean isWrittenBeforeRead(ProgramField field) {
    return writtenBeforeRead.contains(field);
  }

  void setEscaped(Instruction escape) {
    assert !isEscaped();
    this.escape = escape;
  }
}
