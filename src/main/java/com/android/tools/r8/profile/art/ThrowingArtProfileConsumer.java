// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.profile.art;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.TextOutputStream;
import com.android.tools.r8.errors.Unreachable;

public class ThrowingArtProfileConsumer implements ArtProfileConsumer {

  @Override
  public TextOutputStream getHumanReadableArtProfileConsumer() {
    throw new Unreachable();
  }

  @Override
  public ArtProfileRuleConsumer getRuleConsumer() {
    throw new Unreachable();
  }

  @Override
  public void finished(DiagnosticsHandler handler) {
    throw new Unreachable();
  }
}
