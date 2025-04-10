// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.itf;

import static com.android.tools.r8.ir.desugar.itf.InterfaceMethodDesugaringMode.LIBRARY_DESUGARING_N_PLUS;

import com.android.tools.r8.cf.CfVersion;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.graph.MethodResolutionResult.SingleResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.InvokeType;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaring;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.DesugarDescription;
import com.android.tools.r8.ir.desugar.DesugarDescription.ScanCallback;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.DerivedMethod;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.EmulatedDispatchMethodDescriptor;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.MachineDesugaredLibrarySpecification;
import com.android.tools.r8.ir.desugar.icce.AlwaysThrowingInstructionDesugaring;
import com.android.tools.r8.ir.synthetic.ForwardMethodBuilder;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.MethodPosition;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.StringDiagnostic;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import com.android.tools.r8.utils.structural.Ordered;
import com.google.common.collect.Sets;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

//
// Default and static interface method desugaring rewriter (note that lambda
// desugaring should have already processed the code before this rewriter).
//
// In short, during default and static interface method desugaring
// the following actions are performed:
//
//   (1) All static interface methods are moved into companion classes. All calls
//       to these methods are redirected appropriately. All references to these
//       methods from method handles are reported as errors.
//
// Companion class is a synthesized class (<interface-name>-CC) created to host
// static and former default interface methods (see below) from the interface.
//
//   (2) All default interface methods are made static and moved into companion
//       class.
//
//   (3) All calls to default interface methods made via 'super' are changed
//       to directly call appropriate static methods in companion classes.
//
//   (4) All other calls or references to default interface methods are not changed.
//
//   (5) For all program classes explicitly implementing interfaces we analyze the
//       set of default interface methods missing and add them, the created methods
//       forward the call to an appropriate method in interface companion class.
//
public abstract class InterfaceMethodRewriter {

  final AppView<?> appView;
  private final InternalOptions options;
  final DexItemFactory factory;
  // If this is true, this will desugar only default methods from emulated interfaces present in
  // the emulated interface descriptor.
  final InterfaceMethodDesugaringMode desugaringMode;
  private final InterfaceDesugaringSyntheticHelper helper;
  private final InterfaceDesugaringSyntheticHelper subCompilationHelper;
  // The emulatedMethod set is there to avoid doing the emulated look-up too often.
  private final Set<DexString> emulatedMethods = Sets.newIdentityHashSet();

  // All forwarding methods and all throwing methods generated during desugaring.
  private final ProgramMethodSet synthesizedMethods = ProgramMethodSet.createConcurrent();

  // Caches default interface method info for already processed interfaces.
  private final Map<DexType, DefaultMethodsHelper.Collection> cache = new ConcurrentHashMap<>();

  InterfaceMethodRewriter(AppView<?> appView, InterfaceMethodDesugaringMode desugaringMode) {
    assert desugaringMode.isSome();
    assert desugaringMode == LIBRARY_DESUGARING_N_PLUS
        || appView.options().isInterfaceMethodDesugaringEnabled();
    this.appView = appView;
    this.desugaringMode = desugaringMode;
    this.options = appView.options();
    this.factory = appView.dexItemFactory();
    this.helper = new InterfaceDesugaringSyntheticHelper(appView, desugaringMode);
    if (options.partialSubCompilationConfiguration != null
        && options.partialSubCompilationConfiguration.isD8()) {
      this.subCompilationHelper =
          InterfaceDesugaringSyntheticHelper.createSubCompilationHelper(appView, desugaringMode);
    } else {
      this.subCompilationHelper = null;
    }

    if (desugaringMode.isLibraryDesugaring()) {
      initializeEmulatedInterfaceVariables();
    }
  }

  public static CfToCfInterfaceMethodRewriter createCfToCf(
      AppView<?> appView,
      Set<CfInstructionDesugaring> precedingDesugaringsForInvoke,
      Set<CfInstructionDesugaring> precedingDesugaringsForInvokeDynamic) {
    return create(
        appView,
        InterfaceMethodDesugaringMode.createCfToCf(appView.options()),
        precedingDesugaringsForInvoke,
        precedingDesugaringsForInvokeDynamic);
  }

  public static LirToLirInterfaceMethodRewriter createLirToLir(
      AppView<?> appView, CfInstructionDesugaringEventConsumer eventConsumer) {
    InterfaceMethodDesugaringMode desugaringMode =
        InterfaceMethodDesugaringMode.createLirToLir(appView.options());
    return desugaringMode.isSome()
        ? new LirToLirInterfaceMethodRewriter(appView, desugaringMode, eventConsumer)
        : null;
  }

  private static CfToCfInterfaceMethodRewriter create(
      AppView<?> appView,
      InterfaceMethodDesugaringMode desugaringMode,
      Set<CfInstructionDesugaring> precedingDesugaringsForInvoke,
      Set<CfInstructionDesugaring> precedingDesugaringsForInvokeDynamic) {
    return desugaringMode.isSome()
        ? new CfToCfInterfaceMethodRewriter(
            appView,
            desugaringMode,
            precedingDesugaringsForInvoke,
            precedingDesugaringsForInvokeDynamic)
        : null;
  }

  public static void checkForAssumedLibraryTypes(AppInfo appInfo, InternalOptions options) {
    MachineDesugaredLibrarySpecification machineDesugaredLibrarySpecification =
        options.getLibraryDesugaringOptions().getMachineDesugaredLibrarySpecification();
    machineDesugaredLibrarySpecification
        .getEmulatedInterfaces()
        .forEach(
            (ei, descriptor) -> {
              registerType(appInfo, ei);
              registerType(appInfo, descriptor.getRewrittenType());
            });
    machineDesugaredLibrarySpecification
        .getCustomConversions()
        .forEach(
            (type, descriptor) -> {
              registerType(appInfo, type);
              registerType(appInfo, descriptor.getTo().getHolderType());
              registerType(appInfo, descriptor.getFrom().getHolderType());
            });
  }

  private static void registerType(AppInfo appInfo, DexType type) {
    appInfo.dexItemFactory().registerTypeNeededForDesugaring(type);
    DexClass clazz = appInfo.definitionFor(type);
    if (clazz != null && clazz.isLibraryClass() && clazz.isInterface()) {
      clazz.forEachMethod(
          m -> {
            if (m.isDefaultMethod()) {
              appInfo
                  .dexItemFactory()
                  .registerTypeNeededForDesugaring(m.getReference().proto.returnType);
              for (DexType param : m.getReference().proto.parameters.values) {
                appInfo.dexItemFactory().registerTypeNeededForDesugaring(param);
              }
            }
          });
    }
  }

  public Set<DexString> getEmulatedMethods() {
    return emulatedMethods;
  }

  private void initializeEmulatedInterfaceVariables() {
    Set<DexType> emulateLibraryInterface =
        options
            .getLibraryDesugaringOptions()
            .getMachineDesugaredLibrarySpecification()
            .getEmulatedInterfaces()
            .keySet();
    for (DexType interfaceType : emulateLibraryInterface) {
      DexClass emulatedInterfaceClass = appView.definitionFor(interfaceType);
      if (emulatedInterfaceClass != null) {
        for (DexEncodedMethod encodedMethod :
            emulatedInterfaceClass.methods(DexEncodedMethod::isDefaultMethod)) {
          emulatedMethods.add(encodedMethod.getReference().name);
        }
      }
    }
  }

  RetargetMethodSupplier getRetargetMethodSupplier(
      DexMethod invokedMethod, InvokeType invokeType, boolean isInterface, ProgramMethod context) {
    if (isSyntheticMethodThatShouldNotBeDoubleProcessed(context)) {
      return RetargetMethodSupplier.none();
    }
    if (invokedMethod.getHolderType().isArrayType() || factory.isConstructor(invokedMethod)) {
      return RetargetMethodSupplier.none();
    }
    // Continue with invoke type logic. If the invoke is not an interface invoke, then there should
    // generally not be any desugaring. However, there are some cases where the insertion of
    // forwarding methods can change behavior so we need to identify them at the various call sites
    // here.
    DexClass holder = appView.definitionForHolder(invokedMethod, context);
    if (invokeType.isInterface()) {
      return computeInvokeInterface(holder, invokedMethod, invokeType, isInterface, context);
    } else if (invokeType.isDirectOrSuper()) {
      return computeInvokeSpecial(holder, invokedMethod, invokeType, isInterface, context);
    } else if (invokeType.isStatic()) {
      return computeInvokeStatic(holder, invokedMethod, invokeType, isInterface, context);
    } else {
      assert invokeType.isVirtual() || invokeType.isPolymorphic();
      return computeInvokeVirtual(holder, invokedMethod, invokeType, isInterface, context);
    }
  }

  private RetargetMethodSupplier computeInvokeSpecial(
      DexClass holder,
      DexMethod invokedMethod,
      InvokeType invokeType,
      boolean isInterface,
      ProgramMethod context) {
    assert invokeType.isDirectOrSuper();
    if (desugaringMode == LIBRARY_DESUGARING_N_PLUS) {
      return computeInvokeSpecialEmulatedInterfaceForwardingMethod(holder, invokedMethod, context);
    }
    if (!isInterface) {
      return computeEmulatedInterfaceInvokeSpecial(holder, invokedMethod, context);
    }
    if (holder == null) {
      return new WarningRetargetMethodSupplier(
          () -> warnMissingType(context, invokedMethod.getHolderType()));
    }
    MethodResolutionResult resolutionResult =
        appView.appInfoForDesugaring().resolveMethodLegacy(invokedMethod, isInterface);
    if (invokeType.isSuper()) {
      return rewriteInvokeSuper(
          holder, invokedMethod, invokeType, context, resolutionResult.asSingleResolution());
    } else {
      return computeInvokeDirect(holder, invokedMethod, invokeType, context, resolutionResult);
    }
  }

  private RetargetMethodSupplier computeInvokeStatic(
      DexClass holder,
      DexMethod invokedMethod,
      InvokeType invokeType,
      boolean isInterface,
      ProgramMethod context) {
    assert invokeType.isStatic();
    if (desugaringMode == LIBRARY_DESUGARING_N_PLUS || !isInterface) {
      return RetargetMethodSupplier.none();
    }
    if (holder == null) {
      return new WarningRetargetMethodSupplier(
          () -> {
            leavingStaticInvokeToInterface(context);
            warnMissingType(context, invokedMethod.getHolderType());
          });
    }
    if (!holder.isInterface()) {
      return new WarningRetargetMethodSupplier(() -> leavingStaticInvokeToInterface(context));
    }
    // TODO(b/199135051): This should not be needed. Targeted synthetics should be in place.
    if (appView.getSyntheticItems().isPendingSynthetic(invokedMethod.getHolderType())) {
      // We did not create this code yet, but it will not require rewriting.
      return RetargetMethodSupplier.none();
    }
    if (isNonDesugaredLibraryClass(holder)) {
      // NOTE: we intentionally don't desugar static calls into static interface
      // methods coming from android.jar since it is only possible in case v24+
      // version of android.jar is provided.
      //
      // We assume such calls are properly guarded by if-checks like
      //    'if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.XYZ) { ... }'
      //
      // WARNING: This may result in incorrect code on older platforms!
      // Retarget call to an appropriate method of companion class.
      if (options.canLeaveStaticInterfaceMethodInvokes()) {
        // When leaving static interface method invokes upgrade the class file version.
        return new WarningRetargetMethodSupplier(() -> leavingStaticInvokeToInterface(context));
      }
      // On pre-L devices static calls to interface methods result in verifier
      // rejecting the whole class. We have to create special dispatch classes,
      // so the user class is not rejected because it make this call directly.
      // TODO(b/166247515): If this an incorrect invoke-static without the interface bit
      //  we end up "fixing" the code and remove and ICCE error.
      if (synthesizedMethods.contains(context)) {
        // When reprocessing the method generated below, the desugaring asserts this method
        // does not need any new desugaring, while the interface method rewriter tries
        // to outline again the invoke-static. Just do nothing instead.
        return RetargetMethodSupplier.none();
      }
      return new StaticRetargetMethodSupplier() {
        @Override
        public DexMethod getRetargetMethod(
            CfInstructionDesugaringEventConsumer eventConsumer,
            MethodProcessingContext methodProcessingContext) {
          ProgramMethod newProgramMethod =
              appView
                  .getSyntheticItems()
                  .createMethod(
                      kind -> kind.STATIC_INTERFACE_CALL,
                      methodProcessingContext.createUniqueContext(),
                      appView,
                      syntheticMethodBuilder ->
                          syntheticMethodBuilder
                              .setProto(invokedMethod.getProto())
                              .setAccessFlags(MethodAccessFlags.createPublicStaticSynthetic())
                              .setCode(
                                  m ->
                                      ForwardMethodBuilder.builder(factory)
                                          .setStaticTarget(invokedMethod, true)
                                          .setStaticSource(m)
                                          .buildCf()));
          synthesizedMethods.add(newProgramMethod);
          eventConsumer.acceptInvokeStaticInterfaceOutliningMethod(newProgramMethod, context);
          // The synthetic dispatch class has static interface method invokes, so set
          // the class file version accordingly.
          leavingStaticInvokeToInterface(newProgramMethod);
          return newProgramMethod.getReference();
        }
      };
    }

    SingleResolutionResult<?> resolutionResult =
        appView
            .appInfoForDesugaring()
            .resolveMethodOnInterfaceLegacy(holder, invokedMethod)
            .asSingleResolution();
    if (holder.isInterface() && shouldRewriteToInvokeToThrow(resolutionResult, true)) {
      return computeInvokeAsThrowRewrite(invokedMethod, invokeType, resolutionResult);
    }

    assert resolutionResult != null;
    assert resolutionResult.getResolvedMethod().isStatic();
    DexClassAndMethod method = resolutionResult.getResolutionPair();
    return new StaticRetargetMethodSupplier() {
      @Override
      public DexMethod getRetargetMethod(
          CfInstructionDesugaringEventConsumer eventConsumer,
          MethodProcessingContext methodProcessingContext) {
        return helper
            .ensureStaticAsMethodOfCompanionClassStub(method, eventConsumer)
            .getReference();
      }
    };
  }

  private RetargetMethodSupplier computeInvokeInterface(
      DexClass holder,
      DexMethod invokedMethod,
      InvokeType invokeType,
      boolean isInterface,
      ProgramMethod context) {
    assert invokeType.isInterface();
    if (holder == null) {
      // For virtual targets we should not report anything as any virtual dispatch just remains.
      return RetargetMethodSupplier.none();
    }
    AppInfoWithClassHierarchy appInfoForDesugaring = appView.appInfoForDesugaring();
    SingleResolutionResult<?> resolutionResult =
        appInfoForDesugaring.resolveMethodLegacy(invokedMethod, isInterface).asSingleResolution();
    if (resolutionResult == null) {
      return RetargetMethodSupplier.none();
    }
    if (desugaringMode == LIBRARY_DESUGARING_N_PLUS) {
      return computeEmulatedInterfaceVirtualDispatch(resolutionResult);
    }
    if (resolutionResult.getResolvedMethod().isPrivate()
        && resolutionResult.isAccessibleFrom(context, appView, appInfoForDesugaring).isTrue()) {
      // TODO(b/198267586): What about the private in-accessible case?
      return computeInvokeDirect(holder, invokedMethod, invokeType, context, resolutionResult);
    }
    if (resolutionResult.getResolvedMethod().isStatic()) {
      return computeInvokeAsThrowRewrite(invokedMethod, invokeType, resolutionResult);
    }
    return computeEmulatedInterfaceVirtualDispatch(resolutionResult);
  }

  private RetargetMethodSupplier computeInvokeVirtual(
      DexClass holder,
      DexMethod invokedMethod,
      InvokeType invokeType,
      boolean isInterface,
      ProgramMethod context) {
    assert invokeType.isVirtual() || invokeType.isPolymorphic();
    if (holder == null) {
      // For virtual targets we should not report anything as any virtual dispatch just remains.
      return RetargetMethodSupplier.none();
    }
    AppInfoWithClassHierarchy appInfo = appView.appInfoForDesugaring();
    SingleResolutionResult<?> resolutionResult =
        appInfo.resolveMethodLegacy(invokedMethod, isInterface).asSingleResolution();
    if (resolutionResult == null) {
      return RetargetMethodSupplier.none();
    }
    if (desugaringMode == LIBRARY_DESUGARING_N_PLUS) {
      if (resolutionResult.getResolvedMethod().isPrivate()
          && resolutionResult.isAccessibleFrom(context, appView, appInfo).isTrue()) {
        return RetargetMethodSupplier.none();
      }
      if (resolutionResult.getResolvedMethod().isStatic()) {
        return RetargetMethodSupplier.none();
      }
      return computeEmulatedInterfaceVirtualDispatch(resolutionResult);
    }
    RetargetMethodSupplier description = computeEmulatedInterfaceVirtualDispatch(resolutionResult);
    if (description != RetargetMethodSupplier.none()) {
      return description;
    }
    // It may be the case that a virtual invoke resolves to a static method. In such a case, if
    // a default method could give rise to a forwarding method in the resolution path, the program
    // would change behavior from throwing ICCE to dispatching to the companion class method.
    if (resolutionResult.getResolvedMethod().isStatic()) {
      DexClassAndMethod target = appInfo.lookupMaximallySpecificMethod(holder, invokedMethod);
      if (target != null && target.isDefaultMethod()) {
        // Rewrite the invoke to a throw ICCE as the default method forward would otherwise hide the
        // static / virtual mismatch.
        return computeInvokeAsThrowRewrite(
            invokedMethod, invokeType, resolutionResult.asSingleResolution());
      }
    }
    return RetargetMethodSupplier.none();
  }

  private RetargetMethodSupplier computeEmulatedInterfaceVirtualDispatch(
      SingleResolutionResult<?> resolutionResult) {
    assert resolutionResult != null;
    EmulatedDispatchMethodDescriptor emulatedDispatchMethodDescriptor =
        helper.getEmulatedDispatchDescriptor(
            resolutionResult.getInitialResolutionHolder(), resolutionResult.getResolutionPair());
    if (emulatedDispatchMethodDescriptor == null) {
      return RetargetMethodSupplier.none();
    }
    return new StaticRetargetMethodSupplier() {

      @Override
      public DexMethod getRetargetMethod(
          CfInstructionDesugaringEventConsumer eventConsumer,
          MethodProcessingContext methodProcessingContext) {
        return helper
            .ensureEmulatedInterfaceDispatchMethod(emulatedDispatchMethodDescriptor, eventConsumer)
            .getReference();
      }
    };
  }

  private RetargetMethodSupplier computeInvokeDirect(
      DexClass clazz,
      DexMethod invokedMethod,
      InvokeType invokeType,
      ProgramMethod context,
      MethodResolutionResult resolutionResult) {
    if (!clazz.isInterface()) {
      return RetargetMethodSupplier.none();
    }

    if (clazz.isLibraryClass()) {
      throw new CompilationError(
          "Unexpected call to a private method "
              + "defined in library class "
              + clazz.toSourceString(),
          getMethodOrigin(context.getReference()));
    }

    if (resolutionResult.isFailedResolution()) {
      return computeInvokeAsThrowRewrite(invokedMethod, invokeType, null);
    }

    SingleResolutionResult<?> singleResolution = resolutionResult.asSingleResolution();
    if (singleResolution == null) {
      return RetargetMethodSupplier.none();
    }

    DexClassAndMethod directTarget = clazz.lookupClassMethod(invokedMethod);
    if (directTarget != null) {
      // TODO(b/199135051): Replace this by use of the resolution result.
      assert directTarget.getDefinition() == singleResolution.getResolutionPair().getDefinition();
      return new StaticRetargetMethodSupplier() {
        @Override
        public DexMethod getRetargetMethod(
            CfInstructionDesugaringEventConsumer eventConsumer,
            MethodProcessingContext methodProcessingContext) {
          // This can be a private instance method call. Note that the referenced
          // method is expected to be in the current class since it is private, but desugaring
          // may move some methods or their code into other classes.
          DexClassAndMethod companionMethodDefinition;
          if (directTarget.getDefinition().isPrivateMethod()) {
            if (directTarget.isProgramMethod()) {
              companionMethodDefinition =
                  helper.ensurePrivateAsMethodOfProgramCompanionClassStub(
                      directTarget.asProgramMethod(), eventConsumer);
              return companionMethodDefinition.getReference();
            } else {
              // TODO(b/200938617): Why does this not create a stub on the class path?
              return helper.privateAsMethodOfCompanionClass(directTarget);
            }
          } else {
            companionMethodDefinition =
                helper.ensureDefaultAsMethodOfCompanionClassStub(directTarget, eventConsumer);
            return companionMethodDefinition.getReference();
          }
        }
      };
    } else {
      // The method can be a default method in the interface hierarchy.
      DexClassAndMethod virtualTarget =
          appView.appInfoForDesugaring().lookupMaximallySpecificMethod(clazz, invokedMethod);
      if (virtualTarget != null) {
        // TODO(b/199135051): Replace this by use of the resolution result.
        assert virtualTarget.getDefinition()
            == singleResolution.getResolutionPair().getDefinition();
        return new StaticRetargetMethodSupplier() {
          @Override
          public DexMethod getRetargetMethod(
              CfInstructionDesugaringEventConsumer eventConsumer,
              MethodProcessingContext methodProcessingContext) {
            // This is a invoke-direct call to a virtual method.
            return helper
                .ensureDefaultAsMethodOfCompanionClassStub(virtualTarget, eventConsumer)
                .getReference();
          }
        };
      } else {
        // The below assert is here because a well-type program should have a target, but we
        // cannot throw a compilation error, since we have no knowledge about the input.
        assert false;
      }
    }
    return RetargetMethodSupplier.none();
  }

  private RetargetMethodSupplier computeInvokeAsThrowRewrite(
      DexMethod invokedMethod, InvokeType invokeType, SingleResolutionResult<?> resolution) {
    return new ThrowingRetargetMethodSupplier() {

      @Override
      public DesugarDescription toDesugarDescription(ProgramMethod context) {
        return AlwaysThrowingInstructionDesugaring.computeInvokeAsThrowRewrite(
            appView, invokedMethod, invokeType, resolution);
      }
    };
  }

  void leavingStaticInvokeToInterface(ProgramMethod method) {
    // When leaving static interface method invokes possibly upgrade the class file
    // version, but don't go above the initial class file version. If the input was
    // 1.7 or below, this will make a VerificationError on the input a VerificationError
    // on the output. If the input was 1.8 or above the runtime behaviour (potential ICCE)
    // will remain the same.
    upgradeCfVersionToSupportInterfaceMethodInvoke(method);
  }

  private void leavingSuperInvokeToInterface(ProgramMethod method) {
    // When leaving interface method invokes possibly upgrade the class file
    // version, but don't go above the initial class file version. If the input was
    // 1.7 or below, this will make a VerificationError on the input a VerificationError
    // on the output. If the input was 1.8 or above the runtime behaviour (potential ICCE)
    // will remain the same.
    upgradeCfVersionToSupportInterfaceMethodInvoke(method);
  }

  private void upgradeCfVersionToSupportInterfaceMethodInvoke(ProgramMethod method) {
    if (method.getHolder().hasClassFileVersion()) {
      method
          .getDefinition()
          .upgradeClassFileVersion(
              Ordered.min(CfVersion.V1_8, method.getHolder().getInitialClassFileVersion()));
    } else {
      method.getDefinition().upgradeClassFileVersion(CfVersion.V1_8);
    }
  }

  boolean isSyntheticMethodThatShouldNotBeDoubleProcessed(ProgramMethod method) {
    return appView.getSyntheticItems().isSyntheticMethodThatShouldNotBeDoubleProcessed(method);
  }

  @SuppressWarnings("ReferenceEquality")
  private RetargetMethodSupplier rewriteInvokeSuper(
      DexClass holder,
      DexMethod invokedMethod,
      InvokeType invokeType,
      ProgramMethod context,
      SingleResolutionResult<?> resolutionResult) {
    if (holder.isInterface() && shouldRewriteToInvokeToThrow(resolutionResult, false)) {
      return computeInvokeAsThrowRewrite(invokedMethod, invokeType, resolutionResult);
    }

    if (holder.isInterface()
        && !resolutionResult.getResolutionPair().getHolder().isLibraryClass()) {
      // NOTE: we intentionally don't desugar super calls into interface methods
      // coming from android.jar since it is only possible in case v24+ version
      // of android.jar is provided.
      //
      // We assume such calls are properly guarded by if-checks like
      //    'if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.XYZ) { ... }'
      //
      // WARNING: This may result in incorrect code on older platforms!
      // Retarget call to an appropriate method of companion class.
      if (resolutionResult.getResolvedMethod().isPrivateMethod()) {
        if (resolutionResult
            .isAccessibleFrom(context, appView, appView.appInfoForDesugaring())
            .isFalse()) {
          // TODO(b/145775365): This should throw IAE.
          return computeInvokeAsThrowRewrite(invokedMethod, invokeType, null);
        }
        return new StaticRetargetMethodSupplier() {
          @Override
          public DexMethod getRetargetMethod(
              CfInstructionDesugaringEventConsumer eventConsumer,
              MethodProcessingContext methodProcessingContext) {
            DexClassAndMethod method = resolutionResult.getResolutionPair();
            if (method.isProgramMethod()) {
              ProgramMethod companionMethodDefinition =
                  helper.ensurePrivateAsMethodOfProgramCompanionClassStub(
                      method.asProgramMethod(), eventConsumer);
              return companionMethodDefinition.getReference();
            } else {
              return helper.privateAsMethodOfCompanionClass(method);
            }
          }
        };
      } else {
        DexClassAndMethod method = resolutionResult.getResolutionPair();
        if (method.getAccessFlags().isAbstract()) {
          return computeInvokeAsThrowRewrite(invokedMethod, invokeType, resolutionResult);
        }
        return new StaticRetargetMethodSupplier() {
          @Override
          public DexMethod getRetargetMethod(
              CfInstructionDesugaringEventConsumer eventConsumer,
              MethodProcessingContext methodProcessingContext) {
            // TODO(b/199135051): Why do this amend routine. We have done resolution, so would
            //  that not be the correct target!? I think this is just legacy from before
            //  resolution was implemented in full.
            DexMethod amendedMethod = amendDefaultMethod(context.getHolder(), invokedMethod);
            assert method.getReference() == amendedMethod;
            DexClassAndMethod companionMethod =
                helper.ensureDefaultAsMethodOfCompanionClassStub(method, eventConsumer);
            return companionMethod.getReference();
          }
        };
      }
    }

    RetargetMethodSupplier emulatedInterfaceDesugaring =
        computeEmulatedInterfaceInvokeSpecial(holder, invokedMethod, context);
    if (emulatedInterfaceDesugaring == RetargetMethodSupplier.none()
        && subCompilationHelper != null) {
      emulatedInterfaceDesugaring =
          computeEmulatedInterfaceInvokeSpecial(
              holder, invokedMethod, context, subCompilationHelper);
    }
    if (emulatedInterfaceDesugaring == RetargetMethodSupplier.none()) {
      if (context.isDefaultMethod()) {
        return new ThrowingRetargetMethodSupplier() {
          @Override
          public DesugarDescription toDesugarDescription(ProgramMethod context) {
            return AlwaysThrowingInstructionDesugaring.computeInvokeAsThrowNSMERewrite(
                appView,
                invokedMethod,
                invokeType,
                () ->
                    appView
                        .reporter()
                        .warning(
                            new StringDiagnostic(
                                "Interface method desugaring has inserted NoSuchMethodError"
                                    + " replacing a super call in "
                                    + context.toSourceString(),
                                context.getOrigin())));
          }
        };
      } else {
        return new WarningRetargetMethodSupplier(() -> leavingSuperInvokeToInterface(context));
      }
    }

    return emulatedInterfaceDesugaring;
  }

  private RetargetMethodSupplier computeEmulatedInterfaceInvokeSpecial(
      DexClass clazz, DexMethod invokedMethod, ProgramMethod context) {
    return computeEmulatedInterfaceInvokeSpecial(clazz, invokedMethod, context, helper);
  }

  private RetargetMethodSupplier computeEmulatedInterfaceInvokeSpecial(
      DexClass clazz,
      DexMethod invokedMethod,
      ProgramMethod context,
      InterfaceDesugaringSyntheticHelper helper) {
    assert desugaringMode != LIBRARY_DESUGARING_N_PLUS;
    if (clazz == null) {
      return RetargetMethodSupplier.none();
    }
    AppInfoWithClassHierarchy appInfo = appView.appInfoForDesugaring();
    DexClassAndMethod superTarget =
        appInfo.lookupSuperTarget(invokedMethod, context, appView, appInfo);
    if (superTarget == null) {
      return RetargetMethodSupplier.none();
    }
    if (clazz.isInterface()
        && clazz.isLibraryClass()
        && helper.isInDesugaredLibrary(clazz)
        && !helper.isEmulatedInterface(clazz.type)
        && superTarget.isDefaultMethod()
        && superTarget.isLibraryMethod()) {
      return new StaticRetargetMethodSupplier() {
        @Override
        public DexMethod getRetargetMethod(
            CfInstructionDesugaringEventConsumer eventConsumer,
            MethodProcessingContext methodProcessingContext) {
          return helper
              .ensureDefaultAsMethodOfCompanionClassStub(superTarget, eventConsumer)
              .getReference();
        }
      };
    } else {
      return computeInvokeSpecialEmulatedInterfaceForwardingMethod(clazz, superTarget, helper);
    }
  }

  private RetargetMethodSupplier computeInvokeSpecialEmulatedInterfaceForwardingMethod(
      DexClass clazz, DexMethod invokedMethod, ProgramMethod context) {
    return computeInvokeSpecialEmulatedInterfaceForwardingMethod(
        clazz, invokedMethod, context, helper);
  }

  private RetargetMethodSupplier computeInvokeSpecialEmulatedInterfaceForwardingMethod(
      DexClass clazz,
      DexMethod invokedMethod,
      ProgramMethod context,
      InterfaceDesugaringSyntheticHelper helper) {
    if (clazz == null) {
      return RetargetMethodSupplier.none();
    }
    AppInfoWithClassHierarchy appInfo = appView.appInfoForDesugaring();
    DexClassAndMethod superTarget =
        appInfo.lookupSuperTarget(invokedMethod, context, appView, appInfo);
    return computeInvokeSpecialEmulatedInterfaceForwardingMethod(clazz, superTarget, helper);
  }

  private RetargetMethodSupplier computeInvokeSpecialEmulatedInterfaceForwardingMethod(
      DexClass clazz, DexClassAndMethod superTarget, InterfaceDesugaringSyntheticHelper helper) {
    // That invoke super may not resolve since the super method may not be present since it's in the
    // emulated interface. We need to force resolution. If it resolves to a library method, then it
    // needs to be rewritten. If it resolves to a program overrides, the invoke-super can remain.
    DerivedMethod forwardingMethod =
        helper.computeEmulatedInterfaceForwardingMethod(clazz, superTarget);
    if (forwardingMethod != null) {
      return new StaticRetargetMethodSupplier() {
        @Override
        public DexMethod getRetargetMethod(
            CfInstructionDesugaringEventConsumer eventConsumer,
            MethodProcessingContext methodProcessingContext) {
          return helper.ensureEmulatedInterfaceForwardingMethod(forwardingMethod, eventConsumer);
        }
      };
    }
    return RetargetMethodSupplier.none();
  }

  private boolean shouldRewriteToInvokeToThrow(
      SingleResolutionResult<?> resolutionResult, boolean isInvokeStatic) {
    return resolutionResult == null
        || resolutionResult.getResolvedMethod().isStatic() != isInvokeStatic;
  }

  private boolean isNonDesugaredLibraryClass(DexClass clazz) {
    return clazz.isLibraryClass() && !helper.isInDesugaredLibrary(clazz);
  }

  // It is possible that referenced method actually points to an interface which does
  // not define this default methods, but inherits it. We are making our best effort
  // to find an appropriate method, but still use the original one in case we fail.
  private DexMethod amendDefaultMethod(DexClass classToDesugar, DexMethod method) {
    DexMethod singleCandidate =
        getOrCreateInterfaceInfo(classToDesugar, classToDesugar, method.holder)
            .getSingleCandidate(method);
    return singleCandidate != null ? singleCandidate : method;
  }

  public InterfaceMethodProcessorFacade getPostProcessingDesugaringD8(
      InterfaceProcessor interfaceProcessor) {
    return new InterfaceMethodProcessorFacade(appView, interfaceProcessor);
  }

  public InterfaceMethodProcessorFacade getPostProcessingDesugaringR8(
      Predicate<ProgramMethod> isLiveMethod,
      InterfaceProcessor interfaceProcessor) {
    return new InterfaceMethodProcessorFacade(appView, interfaceProcessor, isLiveMethod);
  }

  private Origin getMethodOrigin(DexMethod method) {
    DexType holder = method.holder;
    if (InterfaceDesugaringSyntheticHelper.isCompanionClassType(holder)) {
      holder = helper.getInterfaceClassType(holder);
    }
    DexClass clazz = appView.definitionFor(holder);
    return clazz == null ? Origin.unknown() : clazz.getOrigin();
  }

  DefaultMethodsHelper.Collection getOrCreateInterfaceInfo(
      DexClass classToDesugar, DexClass implementing, DexType iface) {
    DefaultMethodsHelper.Collection collection = cache.get(iface);
    if (collection != null) {
      return collection;
    }
    collection = createInterfaceInfo(classToDesugar, implementing, iface);
    DefaultMethodsHelper.Collection existing = cache.putIfAbsent(iface, collection);
    return existing != null ? existing : collection;
  }

  private DefaultMethodsHelper.Collection createInterfaceInfo(
      DexClass classToDesugar, DexClass implementing, DexType iface) {
    DefaultMethodsHelper helper = new DefaultMethodsHelper();
    DexClass definedInterface = appView.definitionFor(iface);
    if (definedInterface == null) {
      this.helper.warnMissingInterface(classToDesugar, implementing, iface);
      return helper.wrapInCollection();
    }
    if (!definedInterface.isInterface()) {
      throw new CompilationError(
          "Type "
              + iface.toSourceString()
              + " is referenced as an interface from `"
              + implementing.toString()
              + "`.");
    }

    if (isNonDesugaredLibraryClass(definedInterface)) {
      // NOTE: We intentionally ignore all candidates coming from android.jar
      // since it is only possible in case v24+ version of android.jar is provided.
      // WARNING: This may result in incorrect code if something else than Android bootclasspath
      // classes are given as libraries!
      return helper.wrapInCollection();
    }

    // At this point we likely have a non-library type that may depend on default method information
    // from its interfaces and the dependency should be reported.
    if (implementing.isProgramClass() && !definedInterface.isLibraryClass()) {
      appView.appInfo().reportDependencyEdge(implementing.asProgramClass(), definedInterface);
    }

    // Merge information from all superinterfaces.
    for (DexType superinterface : definedInterface.interfaces.values) {
      helper.merge(getOrCreateInterfaceInfo(classToDesugar, definedInterface, superinterface));
    }

    // Hide by virtual methods of this interface.
    for (DexEncodedMethod virtual : definedInterface.virtualMethods()) {
      helper.hideMatches(virtual.getReference());
    }

    // Add all default methods of this interface.
    for (DexEncodedMethod encoded : definedInterface.virtualMethods()) {
      if (this.helper.isCompatibleDefaultMethod(encoded)) {
        helper.addDefaultMethod(encoded);
      }
    }

    return helper.wrapInCollection();
  }

  void warnMissingType(ProgramMethod context, DexType missing) {
    // Companion/Emulated interface/Conversion classes for desugared library won't be missing,
    // they are in the desugared library.
    if (helper.shouldIgnoreFromReports(missing)) {
      return;
    }
    DexMethod method = appView.graphLens().getOriginalMethodSignature(context.getReference());
    Origin origin = getMethodOrigin(method);
    MethodPosition position = new MethodPosition(method);
    options.warningMissingTypeForDesugar(origin, position, missing, method);
  }

  interface RetargetMethodSupplier {

    static RetargetMethodSupplier none() {
      return null;
    }

    DesugarDescription toDesugarDescription(ProgramMethod context);
  }

  abstract static class StaticRetargetMethodSupplier implements RetargetMethodSupplier {

    public abstract DexMethod getRetargetMethod(
        CfInstructionDesugaringEventConsumer eventConsumer,
        MethodProcessingContext methodProcessingContext);

    @Override
    public final DesugarDescription toDesugarDescription(ProgramMethod context) {
      return DesugarDescription.builder()
          .setDesugarRewrite(
              (position,
                  freshLocalProvider,
                  localStackAllocator,
                  desugaringInfo,
                  eventConsumer,
                  context1,
                  methodProcessingContext,
                  desugaringCollection,
                  dexItemFactory) -> {
                DexMethod retargetMethod =
                    getRetargetMethod(eventConsumer, methodProcessingContext);
                return Collections.singletonList(
                    new CfInvoke(org.objectweb.asm.Opcodes.INVOKESTATIC, retargetMethod, false));
              })
          .build();
    }
  }

  private abstract static class ThrowingRetargetMethodSupplier implements RetargetMethodSupplier {}

  private static class WarningRetargetMethodSupplier implements RetargetMethodSupplier {

    private final ScanCallback generator;

    WarningRetargetMethodSupplier(ScanCallback generator) {
      this.generator = generator;
    }

    @Override
    public DesugarDescription toDesugarDescription(ProgramMethod context) {
      return DesugarDescription.builder().addScanEffect(generator).build();
    }
  }
}
