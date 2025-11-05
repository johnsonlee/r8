// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.keepanno.annotations.KeepForApi;
import java.nio.file.Path;

/** Interface for retrieving the generated mapping and its id. */
@KeepForApi
public interface MapConsumer extends StringConsumer {

  /**
   * Called by R8 when the mapping id has been generated. This is always called prior to {@link
   * #finished(DiagnosticsHandler)}.
   */
  default void acceptMapId(String mapId) {}

  @KeepForApi
  class FileConsumer extends StringConsumer.FileConsumer implements MapConsumer {

    private final MapConsumer consumer;

    public FileConsumer(Path outputPath) {
      this(outputPath, null);
    }

    public FileConsumer(Path outputPath, MapConsumer consumer) {
      super(outputPath, consumer);
      this.consumer = consumer;
    }

    @Override
    public void acceptMapId(String mapId) {
      if (consumer != null) {
        consumer.acceptMapId(mapId);
      }
    }
  }

  @KeepForApi
  class ForwardingConsumer extends StringConsumer.ForwardingConsumer implements MapConsumer {

    private final MapConsumer consumer;

    /**
     * @param consumer Consumer to forward to, if null, nothing will be forwarded.
     */
    public ForwardingConsumer(MapConsumer consumer) {
      super(consumer);
      this.consumer = consumer;
    }

    @Override
    public void acceptMapId(String mapId) {
      if (consumer != null) {
        consumer.acceptMapId(mapId);
      }
    }
  }
}
