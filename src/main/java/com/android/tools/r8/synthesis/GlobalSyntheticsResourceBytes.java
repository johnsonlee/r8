// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.synthesis;

import com.android.tools.r8.GlobalSyntheticsResourceProvider;
import com.android.tools.r8.ResourceException;
import com.android.tools.r8.origin.Origin;
import java.io.ByteArrayInputStream;
import java.io.InputStream;

public class GlobalSyntheticsResourceBytes implements GlobalSyntheticsResourceProvider {

  private final Origin origin;
  private final byte[] bytes;

  public GlobalSyntheticsResourceBytes(Origin origin, byte[] bytes) {
    this.origin = origin;
    this.bytes = bytes;
  }

  @Override
  public Origin getOrigin() {
    return origin;
  }

  @Override
  public InputStream getByteStream() throws ResourceException {
    return new ByteArrayInputStream(bytes);
  }
}
