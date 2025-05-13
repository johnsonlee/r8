// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.retargeter;

import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.contexts.CompilationContext.UniqueContext;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ProgramDefinition;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.desugar.backports.BackportedMethods;
import com.android.tools.r8.ir.synthetic.EmulateDispatchSyntheticCfCodeProvider;
import com.android.tools.r8.ir.synthetic.EmulateDispatchSyntheticCfCodeProvider.EmulateDispatchType;
import com.android.tools.r8.ir.synthetic.ThrowCfCodeProvider;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.google.common.collect.ImmutableSet;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class AutoCloseableRetargeterHelper {

  private final AndroidApiLevel minApiLevel;
  private final DexItemFactory factory;
  private final Set<DexMethod> methodsToEmulate;
  private final Set<DexType> superTargetsToRewrite;

  public AutoCloseableRetargeterHelper(AndroidApiLevel minApiLevel, DexItemFactory factory) {
    this.minApiLevel = minApiLevel;
    this.factory = factory;
    this.methodsToEmulate = initializeMethodsToEmulate();
    this.superTargetsToRewrite = initializeSuperTargetsToRewrite();
  }

  private DexMethod createCloseMethod(DexType holderType) {
    return factory.createMethod(
        holderType, factory.createProto(factory.voidType), factory.closeMethodName);
  }

  public boolean shouldEmulateMethod(DexMethod method) {
    return methodsToEmulate.contains(method);
  }

  public boolean isSuperTargetToRewrite(DexType type) {
    return superTargetsToRewrite.contains(type);
  }

  private boolean isFinalClassImplementingAutoCloseable(DexType type) {
    return type.isIdenticalTo(factory.androidMediaMediaDrmType);
  }

  // This includes AutoCloseable#close() and the close method in all library types which override
  // AutoCloseable#close(). We exclude android.media.MediaDrm which is final and rewritten by the
  // backportedMethodRewriter.
  private Set<DexMethod> initializeMethodsToEmulate() {
    ImmutableSet.Builder<DexMethod> builder = ImmutableSet.builder();
    forEachAutoCloseableMissingSubimplementation(
        type -> {
          if (!isFinalClassImplementingAutoCloseable(type)) {
            builder.add(createCloseMethod(type));
          }
        });
    builder.add(createCloseMethod(factory.autoCloseableType));
    return builder.build();
  }

  // This includes all library types which implements AutoCloseable#close(). We exclude
  // android.media.MediaDrm which is final and rewritten by the backportedMethodRewriter.
  private Set<DexType> initializeSuperTargetsToRewrite() {
    ImmutableSet.Builder<DexType> builder = ImmutableSet.builder();
    forEachAutoCloseableMissingSubimplementation(
        type -> {
          if (!isFinalClassImplementingAutoCloseable(type)) {
            builder.add(type);
          }
        });
    return builder.build();
  }

  // This includes all library types which implements directly AutoCloseable#close() including
  // android.media.MediaDrm.
  public static void forEachAutoCloseableMissingSubimplementation(
      Consumer<DexType> consumer,
      AndroidApiLevel minApiLevel,
      DexItemFactory factory,
      boolean withSubtypes) {
    if (minApiLevel.isLessThanOrEqualTo(AndroidApiLevel.V)) {
      consumer.accept(factory.javaUtilConcurrentExecutorServiceType);
      consumer.accept(factory.javaUtilConcurrentForkJoinPoolType);
      if (withSubtypes) {
        consumer.accept(factory.createType("Ljava/util/concurrent/ScheduledExecutorService;"));
        consumer.accept(factory.createType("Ljava/util/concurrent/AbstractExecutorService;"));
        consumer.accept(factory.createType("Ljava/util/concurrent/ThreadPoolExecutor;"));
        consumer.accept(factory.createType("Ljava/util/concurrent/ScheduledThreadPoolExecutor;"));
      }
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

  private void forEachAutoCloseableMissingSubimplementation(Consumer<DexType> consumer) {
    forEachAutoCloseableMissingSubimplementation(consumer, minApiLevel, factory, false);
  }

  // This includes all library types which implements directly AutoCloseable#close() including
  // android.media.MediaDrm, however, android.media.MediaDrm is final and rewritten if called
  // directly by the backported method rewriter.
  private LinkedHashMap<DexType, DexMethod> synthesizeDispatchCases(
      AppView<?> appView,
      ProgramMethod context,
      AutoCloseableRetargeterEventConsumer eventConsumer,
      MethodProcessingContext methodProcessingContext) {
    LinkedHashMap<DexType, DexMethod> map = new LinkedHashMap<>();
    forEachAutoCloseableMissingSubimplementation(
        type -> {
          // ForkJoinPool has an optimized version of ExecutorService.close. ForkJoinPool is not
          // present in 19 (added in 21) so R8 cannot use instanceof ForkJoinPool in the emulated
          // dispatch. We rely on ForkJoinPool implementing ExecutorService and use that path.
          if (type.isNotIdenticalTo(factory.javaUtilConcurrentForkJoinPoolType)) {
            map.put(
                type,
                synthesizeDispatchCase(
                    appView,
                    type,
                    context,
                    eventConsumer,
                    methodProcessingContext::createUniqueContext));
          }
        });
    return map;
  }

  public DexMethod synthesizeDispatchCase(
      AppView<?> appView,
      DexType type,
      ProgramDefinition context,
      AutoCloseableRetargeterEventConsumer eventConsumer,
      Supplier<UniqueContext> contextSupplier) {
    assert isSuperTargetToRewrite(type)
        || isFinalClassImplementingAutoCloseable(factory.androidMediaMediaDrmType);
    if (type.isIdenticalTo(factory.javaUtilConcurrentExecutorServiceType)
        || type.isIdenticalTo(factory.javaUtilConcurrentForkJoinPoolType)) {
      // For ForkJoinPool.close R8 uses the less efficient ExecutorService.close.
      // ExecutorService.close does not however use unreachable apis and ExecutorService is present
      // at Android api 19.
      return synthesizeExecutorServiceDispatchCase(
          appView, context, eventConsumer, contextSupplier);
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
      ProgramDefinition context,
      AutoCloseableRetargeterEventConsumer eventConsumer,
      Supplier<UniqueContext> contextSupplier) {
    ProgramMethod method =
        appView
            .getSyntheticItems()
            .createMethod(
                kinds -> kinds.AUTOCLOSEABLE_FORWARDER,
                contextSupplier.get(),
                appView,
                methodBuilder ->
                    methodBuilder
                        .setAccessFlags(MethodAccessFlags.createPublicStaticSynthetic())
                        .setProto(
                            factory.createProto(
                                factory.voidType, factory.javaUtilConcurrentExecutorServiceType))
                        .setCode(
                            methodSig ->
                                appView
                                        .options()
                                        .getMinApiLevel()
                                        .isGreaterThanOrEqualTo(AndroidApiLevel.N)
                                    ? BackportedMethods
                                        .ExecutorServiceMethods_closeExecutorServiceNPlus(
                                            factory, methodSig)
                                    : BackportedMethods.ExecutorServiceMethods_closeExecutorService(
                                        factory, methodSig)));
    eventConsumer.acceptAutoCloseableForwardingMethod(method, context);
    return method.getReference();
  }

  ProgramMethod createThrowUnsupportedException(
      AppView<?> appView,
      ProgramDefinition context,
      AutoCloseableRetargeterEventConsumer eventConsumer,
      Supplier<UniqueContext> contextSupplier) {
    ProgramMethod method =
        appView
            .getSyntheticItems()
            .createMethod(
                kinds -> kinds.THROW_IAE,
                contextSupplier.get(),
                appView,
                methodBuilder ->
                    methodBuilder
                        .setAccessFlags(MethodAccessFlags.createPublicStaticSynthetic())
                        .setProto(factory.createProto(factory.voidType, factory.objectType))
                        .setCode(
                            methodSig ->
                                new ThrowCfCodeProvider(
                                        appView,
                                        methodSig.getHolderType(),
                                        factory.illegalArgumentExceptionType,
                                        null)
                                    .generateCfCode()));
    eventConsumer.acceptAutoCloseableDispatchMethod(method, context);
    return method;
  }

  public DexMethod synthesizeDispatcher(
      AppView<?> appView,
      ProgramMethod context,
      AutoCloseableRetargeterEventConsumer eventConsumer,
      MethodProcessingContext methodProcessingContext) {
    DexItemFactory factory = appView.dexItemFactory();
    LinkedHashMap<DexType, DexMethod> dispatchCases =
        synthesizeDispatchCases(appView, context, eventConsumer, methodProcessingContext);
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
                        .setProto(factory.createProto(factory.voidType, factory.objectType))
                        .setCode(
                            methodSig ->
                                new EmulateDispatchSyntheticCfCodeProvider(
                                        methodSig.getHolderType(),
                                        createThrowUnsupportedException(
                                                appView,
                                                context,
                                                eventConsumer,
                                                methodProcessingContext::createUniqueContext)
                                            .getReference(),
                                        createCloseMethod(factory.autoCloseableType),
                                        dispatchCases,
                                        EmulateDispatchType.AUTO_CLOSEABLE,
                                        appView)
                                    .generateCfCode()));
    eventConsumer.acceptAutoCloseableDispatchMethod(method, context);
    return method.getReference();
  }

  static DexClassAndMethod lookupSuperIncludingInterfaces(
      AppView<?> appView, DexMethod target, DexProgramClass context) {
    DexClassAndMethod superMethod =
        appView
            .appInfoForDesugaring()
            .lookupSuperTarget(target, context, appView, appView.appInfoForDesugaring());
    if (superMethod != null) {
      return superMethod;
    }
    return appView
        .appInfoForDesugaring()
        .lookupMaximallySpecificMethod(context.getContextClass(), target);
  }

  public void visit(Consumer<DexMethod> methods) {
    if (minApiLevel.isLessThan(AndroidApiLevel.K)) {
      return;
    }
    forEachAutoCloseableMissingSubimplementation(
        type -> {
          if (!isFinalClassImplementingAutoCloseable(type)) {
            methods.accept(createCloseMethod(type));
          }
        });
  }
}
