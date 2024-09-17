// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.analysis;

import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;

public interface EnqueuerCheckCastAnalysis {

  void traceCheckCast(DexType type, DexClass clazz, ProgramMethod context);

  void traceSafeCheckCast(DexType type, DexClass clazz, ProgramMethod context);
}
