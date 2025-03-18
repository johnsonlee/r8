// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.retargeter;

import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.desugaredlibrary.LibraryDesugaringOptions;
import com.android.tools.r8.synthesis.SyntheticItems;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * This holds specific rewritings when using desugared library and specific libraries such as
 * androidx.
 */
public abstract class DesugaredLibraryLibRewriter {

  final AppView<?> appView;
  final Map<DexMethod, BiFunction<DexItemFactory, DexMethod, CfCode>> rewritings;

  DesugaredLibraryLibRewriter(
      AppView<?> appView,
      Map<DexMethod, BiFunction<DexItemFactory, DexMethod, CfCode>> rewritings) {
    this.appView = appView;
    this.rewritings = rewritings;
  }

  public static CfToCfDesugaredLibraryLibRewriter createCfToCf(AppView<?> appView) {
    LibraryDesugaringOptions libraryDesugaringOptions =
        appView.options().getLibraryDesugaringOptions();
    if (libraryDesugaringOptions.isLirToLirLibraryDesugaringEnabled()
        || libraryDesugaringOptions
            .getMachineDesugaredLibrarySpecification()
            .getRewriteType()
            .isEmpty()) {
      return null;
    }
    Map<DexMethod, BiFunction<DexItemFactory, DexMethod, CfCode>> rewritings = computeMap(appView);
    if (rewritings.isEmpty()) {
      return null;
    }
    return new CfToCfDesugaredLibraryLibRewriter(appView, rewritings);
  }

  public static LirToLirDesugaredLibraryLibRewriter createLirToLir(
      AppView<?> appView, CfInstructionDesugaringEventConsumer eventConsumer) {
    LibraryDesugaringOptions libraryDesugaringOptions =
        appView.options().getLibraryDesugaringOptions();
    if (libraryDesugaringOptions.isCfToCfLibraryDesugaringEnabled()
        || libraryDesugaringOptions
            .getMachineDesugaredLibrarySpecification()
            .getRewriteType()
            .isEmpty()) {
      return null;
    }
    Map<DexMethod, BiFunction<DexItemFactory, DexMethod, CfCode>> rewritings = computeMap(appView);
    if (rewritings.isEmpty()) {
      return null;
    }
    return new LirToLirDesugaredLibraryLibRewriter(appView, eventConsumer, rewritings);
  }

  public static Map<DexMethod, BiFunction<DexItemFactory, DexMethod, CfCode>> computeMap(
      AppView<?> appView) {
    DexItemFactory factory = appView.dexItemFactory();
    DexType navType = factory.createType("Landroidx/navigation/NavType;");
    if (!appView.appInfo().hasDefinitionForWithoutExistenceAssert(navType)) {
      return ImmutableMap.of();
    }
    DexMethod from =
        factory.createMethod(
            factory.createType("Landroidx/navigation/NavType$Companion;"),
            factory.createProto(navType, factory.stringType, factory.stringType),
            "fromArgType");
    if (!appView.appInfo().hasDefinitionFor(from)) {
      appView
          .reporter()
          .warning(
              "The class "
                  + navType
                  + " is present but not the method "
                  + from
                  + " which suggests some unsupported set-up where androidx is pre-shrunk without"
                  + " keeping the method "
                  + from
                  + ".");
      return ImmutableMap.of();
    }
    BiFunction<DexItemFactory, DexMethod, CfCode> cfCodeProvider =
        DesugaredLibraryCfMethods::DesugaredLibraryBridge_fromArgType;
    return ImmutableMap.of(from, cfCodeProvider);
  }

  boolean isApplicableToContext(ProgramMethod context) {
    SyntheticItems syntheticItems = appView.getSyntheticItems();
    return !syntheticItems.isSyntheticOfKind(
        context.getHolderType(), kinds -> kinds.DESUGARED_LIBRARY_BRIDGE);
  }

  DexMethod getRetargetMethod(
      DexMethod source,
      CfInstructionDesugaringEventConsumer eventConsumer,
      ProgramMethod context,
      MethodProcessingContext methodProcessingContext) {
    assert isApplicableToContext(context);
    BiFunction<DexItemFactory, DexMethod, CfCode> target = rewritings.get(source);
    if (target == null) {
      return null;
    }
    ProgramMethod newMethod =
        appView
            .getSyntheticItems()
            .createMethod(
                kinds -> kinds.DESUGARED_LIBRARY_BRIDGE,
                methodProcessingContext.createUniqueContext(),
                appView,
                builder ->
                    builder
                        .disableAndroidApiLevelCheck()
                        .setProto(appView.dexItemFactory().prependHolderToProto(source))
                        .setAccessFlags(MethodAccessFlags.createPublicStaticSynthetic())
                        .setCode(methodSig -> target.apply(appView.dexItemFactory(), methodSig)));
    eventConsumer.acceptDesugaredLibraryBridge(newMethod, context);
    return newMethod.getReference();
  }
}
