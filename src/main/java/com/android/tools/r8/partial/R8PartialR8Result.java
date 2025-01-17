// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.partial;

import com.android.tools.r8.DataDirectoryResource;
import com.android.tools.r8.DataEntryResource;
import com.android.tools.r8.DataResource;
import com.android.tools.r8.DataResourceConsumer;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Reporter;
import java.nio.file.Path;
import java.util.List;

public class R8PartialR8Result {

  private final List<DataResource> outputDataResources;
  private final Path outputPath;

  public R8PartialR8Result(List<DataResource> outputDataResources, Path outputPath) {
    this.outputDataResources = outputDataResources;
    this.outputPath = outputPath;
  }

  public Path getOutputPath() {
    return outputPath;
  }

  public void supplyConsumers(InternalOptions options) {
    if (options.dataResourceConsumer != null) {
      supplyDataResourceConsumer(options.dataResourceConsumer, options.reporter);
    }
  }

  private void supplyDataResourceConsumer(DataResourceConsumer consumer, Reporter reporter) {
    if (consumer == null) return;
    for (DataResource dataResource : outputDataResources) {
      if (dataResource instanceof DataDirectoryResource) {
        consumer.accept((DataDirectoryResource) dataResource, reporter);
      } else {
        assert dataResource instanceof DataEntryResource;
        consumer.accept((DataEntryResource) dataResource, reporter);
      }
    }
    consumer.finished(reporter);
  }
}
