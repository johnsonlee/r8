// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.tracereferences;

import com.android.tools.r8.ResourceException;
import java.io.IOException;

// Provide access to some package private APIs.
public class TraceReferencesBridge {

  public static TraceReferencesCommand makeCommand(TraceReferencesCommand.Builder builder) {
    return builder.makeCommand();
  }

  public static void runInternal(TraceReferencesCommand command)
      throws IOException, ResourceException {
    TraceReferences.runInternal(command, command.getInternalOptions());
  }
}
