// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import com.android.tools.r8.Keep;

// TODO: This does not seem to be a "result" per say, should this rather be a RetracedSourceFile?
@Keep
public interface RetraceSourceFileResult {

  boolean isSynthesized();

  String getFilename();
}
