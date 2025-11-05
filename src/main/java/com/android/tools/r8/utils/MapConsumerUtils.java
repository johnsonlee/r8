// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.MapConsumer;
import com.android.tools.r8.StringConsumer;
import com.android.tools.r8.naming.ClassNameMapper;
import com.android.tools.r8.naming.InternalMapConsumer;
import java.util.function.Function;

public class MapConsumerUtils {

  public static MapConsumer createFromStringConsumer(StringConsumer consumer) {
    if (consumer == null) {
      return null;
    }
    if (consumer instanceof MapConsumer) {
      return (MapConsumer) consumer;
    }
    return new MapConsumer() {

      @Override
      public void accept(String string, DiagnosticsHandler handler) {
        consumer.accept(string, handler);
      }

      @Override
      public void finished(DiagnosticsHandler handler) {
        consumer.finished(handler);
      }
    };
  }

  public static MapConsumer createStandardOutConsumer(MapConsumer consumer) {
    return new MapConsumer.ForwardingConsumer(consumer) {

      @Override
      public void accept(String string, DiagnosticsHandler handler) {
        super.accept(string, handler);
        System.out.print(string);
      }
    };
  }

  public static InternalMapConsumer wrapExistingInternalMapConsumer(
      InternalMapConsumer existingMapConsumer, InternalMapConsumer newConsumer) {
    if (existingMapConsumer == null) {
      return newConsumer;
    }
    return new InternalMapConsumer() {
      @Override
      public void accept(DiagnosticsHandler diagnosticsHandler, ClassNameMapper classNameMapper) {
        existingMapConsumer.accept(diagnosticsHandler, classNameMapper);
        newConsumer.accept(diagnosticsHandler, classNameMapper);
      }

      @Override
      public void finished(DiagnosticsHandler handler) {
        existingMapConsumer.finished(handler);
        newConsumer.finished(handler);
      }
    };
  }

  public static <T> InternalMapConsumer wrapExistingInternalMapConsumerIfNotNull(
      InternalMapConsumer existingMapConsumer,
      T object,
      Function<T, InternalMapConsumer> producer) {
    if (object == null) {
      return existingMapConsumer;
    }
    return wrapExistingInternalMapConsumer(existingMapConsumer, producer.apply(object));
  }
}
