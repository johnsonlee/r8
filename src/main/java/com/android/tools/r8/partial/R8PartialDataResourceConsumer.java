// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.partial;

import com.android.tools.r8.DataDirectoryResource;
import com.android.tools.r8.DataEntryResource;
import com.android.tools.r8.DataResource;
import com.android.tools.r8.DataResourceConsumer;
import com.android.tools.r8.DiagnosticsHandler;
import java.util.ArrayList;
import java.util.List;

public class R8PartialDataResourceConsumer implements DataResourceConsumer {

  private final List<DataResource> dataResources = new ArrayList<>();
  boolean finished;

  public List<DataResource> getDataResources() {
    assert finished;
    return dataResources;
  }

  @Override
  public void accept(DataDirectoryResource directory, DiagnosticsHandler diagnosticsHandler) {
    dataResources.add(directory);
  }

  @Override
  public void accept(DataEntryResource file, DiagnosticsHandler diagnosticsHandler) {
    dataResources.add(file);
  }

  @Override
  public void finished(DiagnosticsHandler handler) {
    finished = true;
  }
}
