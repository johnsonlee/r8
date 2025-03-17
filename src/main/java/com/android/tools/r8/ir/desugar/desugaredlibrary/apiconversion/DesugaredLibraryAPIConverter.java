// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.desugar.desugaredlibrary.apiconversion;

import static com.android.tools.r8.graph.LibraryMethod.asLibraryMethodOrNull;

import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.LibraryMethod;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.InvokeType;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaring;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.desugaredlibrary.DesugaredLibraryTypeRewriter;
import com.android.tools.r8.ir.desugar.desugaredlibrary.LibraryDesugaringOptions;
import com.android.tools.r8.ir.desugar.desugaredlibrary.apiconversion.DesugaredLibraryWrapperSynthesizerEventConsumer.DesugaredLibraryAPIConverterEventConsumer;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.MachineDesugaredLibrarySpecification;
import com.android.tools.r8.ir.desugar.itf.InterfaceMethodRewriter;
import com.android.tools.r8.utils.SetUtils;
import com.android.tools.r8.utils.StringDiagnostic;
import java.util.Collections;
import java.util.Set;

// I convert library calls with desugared parameters/return values so they can work normally.
// In the JSON of the desugared library, one can specify conversions between desugared and
// non-desugared types. If no conversion is specified, D8/R8 simply generate wrapper classes around
// the types. Wrappers induce both memory and runtime performance overhead. Wrappers overload
// all potential called APIs.
// Since many types are going to be rewritten, I also need to change the signature of the method
// called so that they are still called with the original types. Hence the vivified types.
// Given a type from the library, the prefix rewriter rewrites (->) as follow:
// vivifiedType -> type;
// type -> desugarType;
// No vivified types can be present in the compiled program (will necessarily be rewritten).
// DesugarType is only a rewritten type (generated through rewriting of type).
// The type, from the library, may either be rewritten to the desugarType,
// or be a rewritten type (generated through rewriting of vivifiedType).
public abstract class DesugaredLibraryAPIConverter {

  final AppView<?> appView;
  private final Set<DexString> emulatedMethods;
  private final MachineDesugaredLibrarySpecification libraryDesugaringSpecification;
  private final DesugaredLibraryTypeRewriter typeRewriter;

  private final DesugaredLibraryWrapperSynthesizer wrapperSynthesizor;
  private final Set<DexMethod> trackedAPIs;

  DesugaredLibraryAPIConverter(
      AppView<?> appView, InterfaceMethodRewriter interfaceMethodRewriter) {
    this.appView = appView;
    this.emulatedMethods =
        interfaceMethodRewriter != null
            ? interfaceMethodRewriter.getEmulatedMethods()
            : Collections.emptySet();
    LibraryDesugaringOptions libraryDesugaringOptions =
        appView.options().getLibraryDesugaringOptions();
    this.libraryDesugaringSpecification =
        libraryDesugaringOptions.getMachineDesugaredLibrarySpecification();
    this.typeRewriter = libraryDesugaringOptions.getTypeRewriter();
    this.wrapperSynthesizor = new DesugaredLibraryWrapperSynthesizer(appView);
    if (appView.options().testing.trackDesugaredApiConversions) {
      trackedAPIs = SetUtils.newConcurrentHashSet();
    } else {
      trackedAPIs = null;
    }
  }

  public static CfToCfDesugaredLibraryApiConverter createForCfToCf(
      AppView<?> appView,
      Set<CfInstructionDesugaring> precedingDesugarings,
      InterfaceMethodRewriter interfaceMethodRewriter) {
    LibraryDesugaringOptions libraryDesugaringOptions =
        appView.options().getLibraryDesugaringOptions();
    if (libraryDesugaringOptions.isCfToCfLibraryDesugaringEnabled()
        && libraryDesugaringOptions.hasTypeRewriter()) {
      return new CfToCfDesugaredLibraryApiConverter(
          appView, interfaceMethodRewriter, precedingDesugarings);
    }
    return null;
  }

  public static LirToLirDesugaredLibraryApiConverter createForLirToLir(
      AppView<?> appView,
      CfInstructionDesugaringEventConsumer eventConsumer,
      InterfaceMethodRewriter interfaceMethodRewriter) {
    LibraryDesugaringOptions libraryDesugaringOptions =
        appView.options().getLibraryDesugaringOptions();
    if (libraryDesugaringOptions.isLirToLirLibraryDesugaringEnabled()
        && libraryDesugaringOptions.hasTypeRewriter()) {
      return new LirToLirDesugaredLibraryApiConverter(
          appView, eventConsumer, interfaceMethodRewriter);
    }
    return null;
  }

  boolean invokeNeedsDesugaring(
      DexMethod invokedMethod, InvokeType invokeType, boolean isInterface, ProgramMethod context) {
    if (isAPIConversionSyntheticType(context.getHolderType(), wrapperSynthesizor, appView)) {
      return false;
    }
    if (appView.dexItemFactory().multiDexTypes.contains(context.getHolderType())
        || invokedMethod.getHolderType().isArrayType()) {
      return false;
    }
    LibraryMethod resolvedMethod =
        getMethodForDesugaring(invokedMethod, invokeType, isInterface, context);
    if (resolvedMethod == null
        || typeRewriter.hasRewrittenType(resolvedMethod.getHolderType())
        || isEmulatedInterfaceOverride(resolvedMethod)) {
      return false;
    }
    return libraryDesugaringSpecification
            .getApiGenericConversion()
            .containsKey(resolvedMethod.getReference())
        || typeRewriter.hasRewrittenTypeInSignature(invokedMethod.getProto());
  }

  static boolean isAPIConversionSyntheticType(
      DexType type, DesugaredLibraryWrapperSynthesizer wrapperSynthesizor, AppView<?> appView) {
    return wrapperSynthesizor.isSyntheticWrapper(type)
        || appView.getSyntheticItems().isSyntheticOfKind(type, kinds -> kinds.API_CONVERSION);
  }

  private LibraryMethod getMethodForDesugaring(
      DexMethod invokedMethod, InvokeType invokeType, boolean isInterface, ProgramMethod context) {
    // TODO(b/191656218): Use lookupInvokeSpecial instead when this is all to Cf.
    AppInfoWithClassHierarchy appInfoForDesugaring = appView.appInfoForDesugaring();
    DexClassAndMethod resolvedMethod =
        invokeType.isSuper()
            ? appInfoForDesugaring.lookupSuperTarget(
                invokedMethod, context, appView, appInfoForDesugaring)
            : appInfoForDesugaring
                .resolveMethodLegacy(invokedMethod, isInterface)
                .getResolutionPair();
    return asLibraryMethodOrNull(resolvedMethod);
  }

  // The problem is that a method can resolve into a library method which is not present at runtime,
  // the code relies in that case on emulated interface dispatch. We should not convert such API.
  private boolean isEmulatedInterfaceOverride(DexClassAndMethod invokedMethod) {
    if (!emulatedMethods.contains(invokedMethod.getName())) {
      return false;
    }
    DexClassAndMethod interfaceResult =
        appView
            .appInfoForDesugaring()
            .lookupMaximallySpecificMethod(invokedMethod.getHolder(), invokedMethod.getReference());
    return interfaceResult != null
        && libraryDesugaringSpecification
            .getEmulatedInterfaces()
            .containsKey(interfaceResult.getHolderType());
  }

  public void generateTrackingWarnings() {
    generateTrackDesugaredAPIWarnings(trackedAPIs, "", appView);
  }

  static void generateTrackDesugaredAPIWarnings(
      Set<DexMethod> tracked, String inner, AppView<?> appView) {
    if (!appView.options().testing.trackDesugaredApiConversions) {
      return;
    }
    StringBuilder sb = new StringBuilder();
    sb.append("Tracked ").append(inner).append("desugared API conversions: ");
    for (DexMethod method : tracked) {
      sb.append("\n");
      sb.append(method);
    }
    appView.options().reporter.warning(new StringDiagnostic(sb.toString()));
    tracked.clear();
  }

  public DesugaredLibraryConversionCfProvider getConversionCfProvider() {
    return wrapperSynthesizor.getConversionCfProvider();
  }

  public DexMethod getRetargetMethod(
      DexMethod invokedMethod,
      InvokeType invokeType,
      boolean isInterface,
      DesugaredLibraryAPIConverterEventConsumer eventConsumer,
      ProgramMethod method,
      MethodProcessingContext methodProcessingContext) {
    if (shouldOutlineAPIConversion(invokedMethod, invokeType, isInterface, method)) {
      return getConversionCfProvider()
          .generateOutlinedAPIConversion(
              invokedMethod,
              invokeType,
              isInterface,
              eventConsumer,
              method,
              methodProcessingContext)
          .getReference();
    }
    return null;
  }

  // If the option is set, we try to outline API conversions as much as possible to reduce the
  // number of soft verification failures. We cannot outline API conversions through super invokes,
  // to instance initializers and to non public methods.
  public boolean shouldOutlineAPIConversion(
      DexMethod invokedMethod, InvokeType invokeType, boolean isInterface, ProgramMethod context) {
    if (trackedAPIs != null) {
      trackedAPIs.add(invokedMethod);
    }
    if (appView.testing().forceInlineApiConversions) {
      return false;
    }
    if (invokeType.isSuper()) {
      return false;
    }
    if (invokedMethod.isInstanceInitializer(appView.dexItemFactory())) {
      return false;
    }
    DexClassAndMethod methodForDesugaring =
        getMethodForDesugaring(invokedMethod, invokeType, isInterface, context);
    assert methodForDesugaring != null;
    // Specific apis that we never want to outline, namely, apis for stack introspection since it
    // confuses developers in debug mode.
    if (libraryDesugaringSpecification
        .getNeverOutlineApi()
        .contains(methodForDesugaring.getReference())) {
      return false;
    }
    return methodForDesugaring.getAccessFlags().isPublic();
  }
}
