// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.positions;

import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.utils.Pair;

public interface MethodPositionRemapper {

  Pair<Position, Position> createRemappedPosition(Position position);
}
