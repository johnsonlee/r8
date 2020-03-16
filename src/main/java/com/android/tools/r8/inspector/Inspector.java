// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.inspector;

import com.android.tools.r8.Keep;
import java.util.function.Consumer;

/** Inspector providing access to various parts of an application. */
@Keep
public interface Inspector {

  /** Iterate all classes and interfaces defined by the program (order unspecified). */
  void forEachClass(Consumer<ClassInspector> inspection);
}
