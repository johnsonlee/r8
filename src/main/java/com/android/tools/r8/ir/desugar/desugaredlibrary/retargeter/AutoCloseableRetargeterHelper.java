// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.retargeter;

import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.desugar.backports.BackportedMethods;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.google.common.collect.ImmutableSet;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.function.Consumer;

public class AutoCloseableRetargeterHelper {

  private final AndroidApiLevel minApiLevel;
  private final DexItemFactory factory;
  private final DexString close;
  private final Set<DexMethod> methodsToEmulate;

  public AutoCloseableRetargeterHelper(AndroidApiLevel minApiLevel, DexItemFactory factory) {
    this.minApiLevel = minApiLevel;
    this.factory = factory;
    this.close = factory.createString("close");
    this.methodsToEmulate = methodsToEmulate();
  }

  static DexMethod createCloseMethod(DexItemFactory factory, DexType holderType) {
    return factory.createMethod(holderType, factory.createProto(factory.voidType), "close");
  }

  public boolean hasCloseMethodName(DexMethod method) {
    return method.getName().isIdenticalTo(close);
  }

  public boolean shouldEmulateMethod(DexMethod method) {
    return methodsToEmulate.contains(method);
  }

  // This includes all library types which implements AutoCloseable#close() and their subtypes.
  // We exclude android.media.MediaDrm which is final and rewritten by the backportedMethodRewriter.
  private Set<DexMethod> methodsToEmulate() {
    ImmutableSet.Builder<DexMethod> builder = ImmutableSet.builder();
    forEachAutoCloseableSubimplementation(
        type -> {
          if (!type.isIdenticalTo(factory.androidMediaMediaDrmType)) {
            builder.add(createCloseMethod(factory, type));
          }
        });
    builder.add(createCloseMethod(factory, factory.autoCloseableType));
    return builder.build();
  }

  // This includes all library types which implements directly AutoCloseable#close() including
  // android.media.MediaDrm.
  private void forEachAutoCloseableSubimplementation(Consumer<DexType> consumer) {
    if (minApiLevel.isLessThanOrEqualTo(AndroidApiLevel.V)) {
      consumer.accept(factory.javaUtilConcurrentExecutorServiceType);
      consumer.accept(factory.javaUtilConcurrentForkJoinPoolType);
    }
    if (minApiLevel.isLessThanOrEqualTo(AndroidApiLevel.R)) {
      consumer.accept(factory.androidContentResTypedArrayType);
    }
    if (minApiLevel.isLessThanOrEqualTo(AndroidApiLevel.P)) {
      consumer.accept(factory.androidMediaMediaMetadataRetrieverType);
    }
    if (minApiLevel.isLessThanOrEqualTo(AndroidApiLevel.O_MR1)) {
      consumer.accept(factory.androidMediaMediaDrmType);
    }
    if (minApiLevel.isLessThanOrEqualTo(AndroidApiLevel.M)) {
      consumer.accept(factory.androidDrmDrmManagerClientType);
      consumer.accept(factory.androidContentContentProviderClientType);
    }
  }

  // This includes all library types which implements directly AutoCloseable#close() including
  // android.media.MediaDrm, however, android.media.MediaDrm is final and rewritten if called
  // directly by the backported method rewriter.
  public LinkedHashMap<DexType, DexMethod> synthesizeDispatchCases(
      AppView<?> appView,
      ProgramMethod context,
      AutoCloseableRetargeterEventConsumer eventConsumer,
      MethodProcessingContext methodProcessingContext) {
    LinkedHashMap<DexType, DexMethod> map = new LinkedHashMap<>();
    forEachAutoCloseableSubimplementation(
        type -> {
          // ForkJoinPool has an optimized version of ExecutorService.close. ForkJoinPool is not
          // present in 19 (added in 21) so R8 cannot use instanceof ForkJoinPool in the emulated
          // dispatch. We rely on ForkJoinPool implementing ExecutorService and use that path.
          if (type.isNotIdenticalTo(factory.javaUtilConcurrentForkJoinPoolType)) {
            map.put(
                type,
                synthesizeDispatchCase(
                    appView, type, context, eventConsumer, methodProcessingContext));
          }
        });
    return map;
  }

  public Set<DexType> superTargetsToRewrite() {
    ImmutableSet.Builder<DexType> builder = ImmutableSet.builder();
    forEachAutoCloseableSubimplementation(builder::add);
    return builder.build();
  }

  public DexMethod synthesizeDispatchCase(
      AppView<?> appView,
      DexType type,
      ProgramMethod context,
      AutoCloseableRetargeterEventConsumer eventConsumer,
      MethodProcessingContext methodProcessingContext) {
    assert superTargetsToRewrite().contains(type);
    if (type.isIdenticalTo(factory.javaUtilConcurrentExecutorServiceType)
        || type.isIdenticalTo(factory.javaUtilConcurrentForkJoinPoolType)) {
      // For ForkJoinPool.close R8 uses the less efficient ExecutorService.close.
      // ExecutorService.close does not however use unreachable apis and ExecutorService is present
      // at Android api 19.
      return synthesizeExecutorServiceDispatchCase(
          appView, context, eventConsumer, methodProcessingContext);
    }
    if (type.isIdenticalTo(factory.androidContentResTypedArrayType)) {
      return factory.createMethod(type, factory.createProto(factory.voidType), "recycle");
    }
    if (type.isIdenticalTo(factory.androidContentContentProviderClientType)) {
      return factory.createMethod(type, factory.createProto(factory.booleanType), "release");
    }
    assert ImmutableSet.of(
            factory.androidMediaMediaMetadataRetrieverType,
            factory.androidMediaMediaDrmType,
            factory.androidDrmDrmManagerClientType)
        .contains(type);
    return factory.createMethod(type, factory.createProto(factory.voidType), "release");
  }

  private DexMethod synthesizeExecutorServiceDispatchCase(
      AppView<?> appView,
      ProgramMethod context,
      AutoCloseableRetargeterEventConsumer eventConsumer,
      MethodProcessingContext methodProcessingContext) {
    ProgramMethod method =
        appView
            .getSyntheticItems()
            .createMethod(
                kinds -> kinds.AUTOCLOSEABLE_DISPATCHER,
                methodProcessingContext.createUniqueContext(),
                appView,
                methodBuilder ->
                    methodBuilder
                        .setAccessFlags(MethodAccessFlags.createPublicStaticSynthetic())
                        .setProto(
                            factory.createProto(
                                factory.voidType, factory.javaUtilConcurrentExecutorServiceType))
                        .setCode(
                            methodSig ->
                                BackportedMethods.ExecutorServiceMethods_closeExecutorService(
                                    factory, methodSig)));
    eventConsumer.acceptAutoCloseableDispatchMethod(method, context);
    return method.getReference();
  }
}
