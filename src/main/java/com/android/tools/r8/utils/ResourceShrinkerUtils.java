// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import com.android.build.shrinker.NoDebugReporter;
import com.android.build.shrinker.ShrinkerDebugReporter;
import com.android.build.shrinker.r8integration.R8ResourceShrinkerState;
import com.android.tools.r8.AndroidResourceInput;
import com.android.tools.r8.AndroidResourceProvider;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.FeatureSplit;
import com.android.tools.r8.ResourceException;
import com.android.tools.r8.StringConsumer;
import com.android.tools.r8.graph.AppView;
import java.io.InputStream;
import java.util.function.Supplier;

public class ResourceShrinkerUtils {

  public static R8ResourceShrinkerState createResourceShrinkerState(AppView<?> appView) {
    InternalOptions options = appView.options();
    R8ResourceShrinkerState state =
        new R8ResourceShrinkerState(
            exception -> appView.reporter().fatalError(new ExceptionDiagnostic(exception)),
            shrinkerDebugReporterFromStringConsumer(
                options.resourceShrinkerConfiguration.getDebugConsumer(), appView.reporter()));
    if (options.isOptimizedResourceShrinking()) {
      try {
        addResources(appView, state, options.androidResourceProvider, FeatureSplit.BASE);
        if (options.hasFeatureSplitConfiguration()) {
          for (FeatureSplit featureSplit :
              options.getFeatureSplitConfiguration().getFeatureSplits()) {
            if (featureSplit.getAndroidResourceProvider() != null) {
              addResources(appView, state, featureSplit.getAndroidResourceProvider(), featureSplit);
            }
          }
        }
      } catch (ResourceException e) {
        throw appView.reporter().fatalError("Failed initializing resource table");
      }
      state.setupReferences();
    }
    return state;
  }

  private static void addResources(
      AppView<?> appView,
      R8ResourceShrinkerState state,
      AndroidResourceProvider androidResourceProvider,
      FeatureSplit featureSplit)
      throws ResourceException {
    for (AndroidResourceInput androidResource : androidResourceProvider.getAndroidResources()) {
      switch (androidResource.getKind()) {
        case MANIFEST:
          state.addManifestProvider(
              () -> wrapThrowingInputStreamResource(appView, androidResource));
          break;
        case RESOURCE_TABLE:
          state.addResourceTable(androidResource.getByteStream(), featureSplit);
          break;
        case XML_FILE:
          state.addXmlFileProvider(
              () -> wrapThrowingInputStreamResource(appView, androidResource),
              androidResource.getPath().location());
          break;
        case KEEP_RULE_FILE:
          state.addKeepRuleRileProvider(
              () -> wrapThrowingInputStreamResource(appView, androidResource));
          break;
        case RES_FOLDER_FILE:
          state.addResFileProvider(
              () -> wrapThrowingInputStreamResource(appView, androidResource),
              androidResource.getPath().location());
          break;
        case UNKNOWN:
          break;
      }
    }
  }

  public static ShrinkerDebugReporter shrinkerDebugReporterFromStringConsumer(
      StringConsumer consumer, DiagnosticsHandler diagnosticsHandler) {
    if (consumer == null) {
      return NoDebugReporter.INSTANCE;
    }
    return new ShrinkerDebugReporter() {
      @Override
      public void debug(Supplier<String> logSupplier) {
        // The default usage of shrinkerdebug in the legacy resource shrinker does not add
        // new lines. Add these to make it consistent with the normal usage of StringConsumer.
        consumer.accept(logSupplier.get() + "\n", diagnosticsHandler);
      }

      @Override
      public void info(Supplier<String> logProducer) {
        // The default usage of shrinkerdebug in the legacy resource shrinker does not add
        // new lines. Add these to make it consistent with the normal usage of StringConsumer.
        consumer.accept(logProducer.get() + "\n", diagnosticsHandler);
      }

      @Override
      public void close() throws Exception {
        consumer.finished(diagnosticsHandler);
      }
    };
  }

  private static InputStream wrapThrowingInputStreamResource(
      AppView<?> appView, AndroidResourceInput androidResource) {
    try {
      return androidResource.getByteStream();
    } catch (ResourceException ex) {
      throw appView.reporter().fatalError("Failed reading " + androidResource.getPath().location());
    }
  }
}
