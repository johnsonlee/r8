// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.MapConsumer;
import com.android.tools.r8.utils.ChainableStringConsumer;
import com.android.tools.r8.utils.StringUtils;

/***
 * Default implementation of an {@link InternalMapConsumer} that wraps around a {@link
 * com.android.tools.r8.MapConsumer} for streamed string output.
 */
public class InternalMapConsumerImpl implements InternalMapConsumer, ChainableStringConsumer {

  private final MapConsumer mapConsumer;
  private DiagnosticsHandler diagnosticsHandler;

  private InternalMapConsumerImpl(MapConsumer mapConsumer) {
    assert mapConsumer != null;
    this.mapConsumer = mapConsumer;
  }

  @Override
  public void accept(DiagnosticsHandler diagnosticsHandler, ClassNameMapper classNameMapper) {
    this.diagnosticsHandler = diagnosticsHandler;
    accept(StringUtils.unixLines(classNameMapper.getPreamble()));
    classNameMapper.write(this);
  }

  @Override
  public void acceptMapId(String mapId) {
    mapConsumer.acceptMapId(mapId);
  }

  @Override
  public ChainableStringConsumer accept(String string) {
    assert diagnosticsHandler != null;
    mapConsumer.accept(string, diagnosticsHandler);
    return this;
  }

  public MapConsumer getMapConsumer() {
    return mapConsumer;
  }

  @Override
  public void finished(DiagnosticsHandler handler) {
    mapConsumer.finished(handler);
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {

    private MapConsumer mapConsumer;

    public Builder setMapConsumer(MapConsumer mapConsumer) {
      this.mapConsumer = mapConsumer;
      return this;
    }

    public InternalMapConsumerImpl build() {
      return new InternalMapConsumerImpl(mapConsumer);
    }
  }
}
