// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import com.android.tools.r8.ProgramResource;
import com.android.tools.r8.ResourceException;

public class ProgramResourceUtils {

  public static byte[] getBytesUnchecked(ProgramResource programResource) {
    try {
      return programResource.getBytes();
    } catch (ResourceException e) {
      throw new RuntimeException(e);
    }
  }
}
