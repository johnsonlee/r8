// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import com.android.tools.r8.MapIdEnvironment;
import com.android.tools.r8.MapIdProvider;
import com.android.tools.r8.dex.Marker.Tool;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.utils.ChainableStringConsumer;
import com.android.tools.r8.utils.ExceptionUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.threads.AwaitableFuture;
import com.android.tools.r8.utils.threads.AwaitableFutureValue;
import com.android.tools.r8.utils.threads.AwaitableFutures;
import com.android.tools.r8.utils.timing.Timing;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class ProguardMapSupplier {

  public static class ProguardMapSupplierResult {
    private final AwaitableFutureValue<ProguardMapId> proguardMapId;
    private final AwaitableFuture mapWritten;

    public static ProguardMapSupplierResult createEmpty() {
      return new ProguardMapSupplierResult(
          AwaitableFutureValue.createCompleted(null), AwaitableFuture.createCompleted());
    }

    public ProguardMapSupplierResult(
        AwaitableFutureValue<ProguardMapId> proguardMapId, AwaitableFuture mapWritten) {
      this.proguardMapId = proguardMapId;
      this.mapWritten = mapWritten;
    }

    public AwaitableFutureValue<ProguardMapId> getProguardMapId() {
      return proguardMapId;
    }

    public ProguardMapId awaitProguardMapId() throws ExecutionException {
      return proguardMapId.awaitValue();
    }

    public AwaitableFuture getMapWritten() {
      return mapWritten;
    }

    public void await() throws ExecutionException {
      AwaitableFutures.awaitAll(proguardMapId, mapWritten);
    }
  }

  public static final int PG_MAP_ID_LENGTH = 64;

  // Hash of the Proguard map (excluding the header up to and including the hash marker).
  public static class ProguardMapId {
    private final String id;
    private final String hash;

    private ProguardMapId(String id, String hash) {
      assert id != null;
      assert hash != null;
      this.id = id;
      this.hash = hash;
    }

    /** Id for the map file (user defined or a truncated prefix of the content hash). */
    public String getId() {
      return id;
    }

    /** The actual content hash. */
    public String getHash() {
      return hash;
    }
  }

  private final ClassNameMapper classNameMapper;
  private final InternalOptions options;
  private final InternalMapConsumer consumer;
  private final Reporter reporter;
  private final Tool compiler;

  private ProguardMapSupplier(ClassNameMapper classNameMapper, Tool tool, InternalOptions options) {
    assert classNameMapper != null;
    this.classNameMapper = classNameMapper.sorted();
    // TODO(b/217111432): Validate Proguard using ProguardMapChecker without building the entire
    //  Proguard map in memory.
    this.consumer = options.mapConsumer;
    this.options = options;
    this.reporter = options.reporter;
    this.compiler = tool;
  }

  public static ProguardMapSupplier create(
      ClassNameMapper classNameMapper, InternalOptions options) {
    assert options.tool != null;
    return new ProguardMapSupplier(classNameMapper, options.tool, options);
  }

  public ProguardMapSupplierResult writeProguardMap(
      AppView<?> appView, ExecutorService executorService, Timing timing)
      throws ExecutionException {
    try (Timing t0 = timing.begin("Prepare write")) {
      classNameMapper.prepareWrite(appView, executorService);
    }
    AwaitableFutureValue<ProguardMapId> proguardMapIdSupplier =
        computeProguardMapId(appView, executorService, timing);
    AwaitableFuture mapWritten;
    mapWritten =
        AwaitableFuture.create(
            () -> {
              try (Timing threadTiming =
                  timing.createThreadTiming("Write proguard map to consumer", appView.options())) {
                ProguardMapId proguardMapId = proguardMapIdSupplier.awaitValue();
                ProguardMapMarkerInfo markerInfo =
                    ProguardMapMarkerInfo.builder()
                        .setCompilerName(compiler.name())
                        .setProguardMapId(proguardMapId)
                        .setGeneratingDex(options.isGeneratingDex())
                        .setApiLevel(options.getMinApiLevel())
                        .setMapVersion(options.getMapFileVersion())
                        .build();

                // Set or compose the marker in the preamble information.
                classNameMapper.setPreamble(
                    ListUtils.concat(markerInfo.toPreamble(), classNameMapper.getPreamble()));

                consumer.accept(reporter, classNameMapper);
                consumer.acceptMapId(proguardMapId.id);
                ExceptionUtils.withConsumeResourceHandler(reporter, this.consumer::finished);
              }
              return null;
            },
            appView.options().getThreadingModule(),
            executorService);
    return new ProguardMapSupplierResult(proguardMapIdSupplier, mapWritten);
  }

  private AwaitableFutureValue<ProguardMapId> computeProguardMapId(
      AppView<?> appView, ExecutorService executorService, Timing timing)
      throws ExecutionException {
    return AwaitableFutureValue.create(
        () -> {
          try (Timing threadTiming =
              timing.createThreadTiming("Compute proguard map id", appView.options())) {
            ProguardMapIdBuilder builder = new ProguardMapIdBuilder();
            classNameMapper.write(builder);
            return builder.build(options.mapIdProvider);
          }
        },
        appView.options().getThreadingModule(),
        executorService);
  }


  static class ProguardMapIdBuilder implements ChainableStringConsumer {

    private final Hasher hasher = Hashing.sha256().newHasher();

    private MapIdProvider getProviderOrDefault(MapIdProvider provider) {
      return provider != null
          ? provider
          : environment -> {
            assert environment.getMapHash().length() == PG_MAP_ID_LENGTH : environment.getMapHash();
            return environment.getMapHash();
          };
    }

    private MapIdEnvironment getEnvironment(String hash) {
      return new MapIdEnvironment() {
        @Override
        public String getMapHash() {
          return hash;
        }
      };
    }

    @Override
    public ProguardMapIdBuilder accept(String string) {
      hasher.putString(string, StandardCharsets.UTF_8);
      return this;
    }

    public ProguardMapId build(MapIdProvider mapIdProvider) {
      String hash = hasher.hash().toString();
      String id = getProviderOrDefault(mapIdProvider).get(getEnvironment(hash));
      return new ProguardMapId(id, hash);
    }
  }

}
