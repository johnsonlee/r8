// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.desugar.records;

import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramMethod;

public interface RecordDesugaringEventConsumer {

  RecordDesugaringEventConsumer EMPTY =
      new RecordDesugaringEventConsumer() {
        @Override
        public void acceptRecordClass(DexProgramClass recordTagClass) {}
      };

  void acceptRecordClass(DexProgramClass recordTagClass);

  static RecordDesugaringEventConsumer empty() {
    return EMPTY;
  }

  interface RecordClassSynthesizerDesugaringEventConsumer extends RecordDesugaringEventConsumer {

    void acceptRecordClassContext(DexProgramClass recordTagClass, DexProgramClass recordClass);
  }

  interface RecordInstructionDesugaringEventConsumer extends RecordDesugaringEventConsumer {

    void acceptRecordClassContext(DexProgramClass recordTagClass, ProgramMethod context);

    void acceptRecordEqualsHelperMethod(ProgramMethod method, ProgramMethod context);

    void acceptRecordGetFieldsAsObjectsHelperMethod(ProgramMethod method, ProgramMethod context);

    void acceptRecordHashCodeHelperMethod(ProgramMethod method, ProgramMethod context);

    void acceptRecordToStringHelperMethod(ProgramMethod method, ProgramMethod context);
  }
}
