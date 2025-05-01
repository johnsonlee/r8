// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.reflectiveidentification;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;
import static com.android.tools.r8.naming.IdentifierNameStringUtils.identifyIdentifier;
import static com.android.tools.r8.naming.IdentifierNameStringUtils.isReflectionMethod;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.ArrayPut;
import com.android.tools.r8.ir.code.ConstantValueUtils;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.InvokeVirtual;
import com.android.tools.r8.ir.code.NewArrayEmpty;
import com.android.tools.r8.ir.code.NewArrayFilled;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.MethodConversionOptions;
import com.android.tools.r8.naming.identifiernamestring.IdentifierNameStringLookupResult;
import com.android.tools.r8.naming.identifiernamestring.IdentifierNameStringTypeLookupResult;
import com.android.tools.r8.shaking.Enqueuer;
import com.android.tools.r8.shaking.InstantiationReason;
import com.android.tools.r8.shaking.KeepClassInfo;
import com.android.tools.r8.shaking.KeepFieldInfo;
import com.android.tools.r8.shaking.KeepMethodInfo;
import com.android.tools.r8.shaking.KeepReason;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.WorkList;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import com.android.tools.r8.utils.timing.Timing;
import java.lang.reflect.InvocationHandler;
import java.util.ArrayList;
import java.util.List;

public class EnqueuerReflectiveIdentificationAnalysis {

  private final AppView<? extends AppInfoWithClassHierarchy> appView;
  private final DexItemFactory factory;
  private final Enqueuer enqueuer;
  private final InternalOptions options;

  private final ProgramMethodSet worklist = ProgramMethodSet.create();

  public EnqueuerReflectiveIdentificationAnalysis(
      AppView<? extends AppInfoWithClassHierarchy> appView, Enqueuer enqueuer) {
    this.appView = appView;
    this.factory = appView.dexItemFactory();
    this.enqueuer = enqueuer;
    this.options = appView.options();
  }

  public void enqueue(ProgramMethod method) {
    worklist.add(method);
  }

  public void processWorklist(Timing timing) {
    if (worklist.isEmpty()) {
      return;
    }
    timing.begin("Reflective identification");
    // TODO(b/414944282): Parallelize reflective identification.
    for (ProgramMethod method : worklist) {
      processMethod(method);
    }
    worklist.clear();
    timing.end();
  }

  private void processMethod(ProgramMethod method) {
    IRCode code = method.buildIR(appView, MethodConversionOptions.nonConverting());
    for (InvokeMethod invoke : code.<InvokeMethod>instructions(Instruction::isInvokeMethod)) {
      processInvoke(method, invoke);
    }
  }

  private void processInvoke(ProgramMethod method, InvokeMethod invoke) {
    DexMethod invokedMethod = invoke.getInvokedMethod();
    if (invokedMethod.isIdenticalTo(factory.classMethods.newInstance)) {
      handleJavaLangClassNewInstance(method, invoke);
      return;
    }
    if (invokedMethod.isIdenticalTo(factory.constructorMethods.newInstance)) {
      handleJavaLangReflectConstructorNewInstance(method, invoke);
      return;
    }
    if (invokedMethod.isIdenticalTo(factory.proxyMethods.newProxyInstance)) {
      handleJavaLangReflectProxyNewProxyInstance(method, invoke);
      return;
    }
    if (factory.serviceLoaderMethods.isLoadMethod(invokedMethod)) {
      handleServiceLoaderInvocation(method, invoke);
      return;
    }
    if (enqueuer.getAnalyses().handleReflectiveInvoke(method, invoke)) {
      return;
    }
    if (!isReflectionMethod(factory, invokedMethod)) {
      return;
    }
    IdentifierNameStringLookupResult<?> identifierLookupResult =
        identifyIdentifier(invoke, appView, method);
    if (identifierLookupResult == null) {
      return;
    }
    DexReference referencedItem = identifierLookupResult.getReference();
    if (referencedItem.isDexType()) {
      DexType referencedType = referencedItem.asDexType();
      if (!referencedType.isClassType()
          || appView.allMergedClasses().isMergeSource(referencedType)) {
        return;
      }
      assert identifierLookupResult.isTypeResult();
      IdentifierNameStringTypeLookupResult identifierTypeLookupResult =
          identifierLookupResult.asTypeResult();
      DexProgramClass clazz =
          enqueuer.getProgramClassOrNullFromReflectiveAccess(referencedType, method);
      if (clazz == null) {
        return;
      }
      enqueuer.markTypeAsLive(clazz, KeepReason.reflectiveUseIn(method));
      if (clazz.canBeInstantiatedByNewInstance()
          && identifierTypeLookupResult.isTypeCompatInstantiatedFromUse(options)) {
        enqueuer.markClassAsInstantiatedWithCompatRule(
            clazz, () -> KeepReason.reflectiveUseIn(method));
      } else if (identifierTypeLookupResult.isTypeInitializedFromUse()) {
        enqueuer.markDirectAndIndirectClassInitializersAsLive(clazz);
      }
      // To ensure we are not moving the class because we cannot prune it when there is a reflective
      // use of it.
      if (enqueuer.getKeepInfo().getClassInfo(clazz).isShrinkingAllowed(options)) {
        enqueuer
            .getKeepInfo()
            .joinClass(clazz, joiner -> joiner.disallowOptimization().disallowShrinking());
      }
    } else if (referencedItem.isDexField()) {
      DexField field = referencedItem.asDexField();
      DexProgramClass clazz =
          enqueuer.getProgramClassOrNullFromReflectiveAccess(field.holder, method);
      if (clazz == null) {
        return;
      }
      DexEncodedField encodedField = clazz.lookupField(field);
      if (encodedField == null) {
        return;
      }
      // Normally, we generate a -keepclassmembers rule for the field, such that the field is only
      // kept if it is a static field, or if the holder or one of its subtypes are instantiated.
      // However, if the invoked method is a field updater, then we always need to keep instance
      // fields since the creation of a field updater throws a NoSuchFieldException if the field
      // is not present.
      boolean keepClass =
          !encodedField.isStatic()
              && factory.atomicFieldUpdaterMethods.isFieldUpdater(invokedMethod);
      if (keepClass) {
        enqueuer
            .getWorklist()
            .enqueueMarkInstantiatedAction(
                clazz, null, InstantiationReason.REFLECTION, KeepReason.reflectiveUseIn(method));
      }
      if (enqueuer.getKeepInfo().getFieldInfo(encodedField, clazz).isShrinkingAllowed(options)) {
        ProgramField programField = new ProgramField(clazz, encodedField);
        enqueuer.applyMinimumKeepInfoWhenLive(
            programField,
            KeepFieldInfo.newEmptyJoiner()
                .disallowOptimization()
                .disallowShrinking()
                .addReason(KeepReason.reflectiveUseIn(method)));
      }
    } else {
      assert referencedItem.isDexMethod();
      DexMethod targetedMethodReference = referencedItem.asDexMethod();
      DexProgramClass clazz =
          enqueuer.getProgramClassOrNullFromReflectiveAccess(
              targetedMethodReference.holder, method);
      if (clazz == null) {
        return;
      }
      ProgramMethod targetedMethod = clazz.lookupProgramMethod(targetedMethodReference);
      if (targetedMethod == null) {
        return;
      }
      KeepReason reason = KeepReason.reflectiveUseIn(method);
      if (targetedMethod.getDefinition().belongsToDirectPool()) {
        enqueuer.markMethodAsTargeted(targetedMethod, reason);
        enqueuer.markDirectStaticOrConstructorMethodAsLive(targetedMethod, reason);
      } else {
        enqueuer.markVirtualMethodAsLive(targetedMethod, reason);
      }
      enqueuer.applyMinimumKeepInfoWhenLiveOrTargeted(
          targetedMethod, KeepMethodInfo.newEmptyJoiner().disallowOptimization());
    }
  }

  /** Handles reflective uses of {@link Class#newInstance()}. */
  private void handleJavaLangClassNewInstance(ProgramMethod method, InvokeMethod invoke) {
    if (!invoke.isInvokeVirtual()) {
      assert false;
      return;
    }

    DexType instantiatedType =
        ConstantValueUtils.getDexTypeRepresentedByValueForTracing(
            invoke.asInvokeVirtual().getReceiver(), appView);
    if (instantiatedType == null || !instantiatedType.isClassType()) {
      // Give up, we can't tell which class is being instantiated, or the type is not a class type.
      // The latter should not happen in practice.
      return;
    }

    DexProgramClass clazz =
        enqueuer.getProgramClassOrNullFromReflectiveAccess(instantiatedType, method);
    if (clazz == null) {
      return;
    }
    ProgramMethod defaultInitializer = clazz.getProgramDefaultInitializer();
    if (defaultInitializer != null) {
      enqueuer.traceReflectiveNewInstance(method, clazz, defaultInitializer);
    }
  }

  /** Handles reflective uses of {@link java.lang.reflect.Constructor#newInstance(Object...)}. */
  private void handleJavaLangReflectConstructorNewInstance(
      ProgramMethod method, InvokeMethod invoke) {
    if (!invoke.isInvokeVirtual()) {
      assert false;
      return;
    }

    Value constructorValue = invoke.asInvokeVirtual().getReceiver().getAliasedValue();
    if (constructorValue.isPhi() || !constructorValue.definition.isInvokeVirtual()) {
      // Give up, we can't tell which class is being instantiated.
      return;
    }

    InvokeVirtual constructorDefinition = constructorValue.definition.asInvokeVirtual();
    DexMethod invokedMethod = constructorDefinition.getInvokedMethod();
    if (invokedMethod.isNotIdenticalTo(factory.classMethods.getConstructor)
        && invokedMethod.isNotIdenticalTo(factory.classMethods.getDeclaredConstructor)) {
      // Give up, we can't tell which constructor is being invoked.
      return;
    }

    DexType instantiatedType =
        ConstantValueUtils.getDexTypeRepresentedByValueForTracing(
            constructorDefinition.getReceiver(), appView);
    if (instantiatedType == null || !instantiatedType.isClassType()) {
      // Give up, we can't tell which constructor is being invoked, or the type is not a class type.
      // The latter should not happen in practice.
      return;
    }

    DexProgramClass clazz =
        enqueuer.getProgramClassOrNullFromReflectiveAccess(instantiatedType, method);
    if (clazz == null) {
      return;
    }
    Value parametersValue = constructorDefinition.inValues().get(1);
    if (parametersValue.isPhi()) {
      // Give up, we can't tell which constructor is being invoked.
      return;
    }
    NewArrayEmpty newArrayEmpty = parametersValue.definition.asNewArrayEmpty();
    NewArrayFilled newArrayFilled = parametersValue.definition.asNewArrayFilled();
    int parametersSize =
        newArrayEmpty != null
            ? newArrayEmpty.sizeIfConst()
            : newArrayFilled != null
                ? newArrayFilled.size()
                : parametersValue.isAlwaysNull(appView) ? 0 : -1;
    if (parametersSize < 0) {
      return;
    }

    ProgramMethod initializer = null;

    if (parametersSize == 0) {
      initializer = clazz.getProgramDefaultInitializer();
    } else {
      DexType[] parameterTypes = new DexType[parametersSize];
      int missingIndices;

      if (newArrayEmpty != null) {
        missingIndices = parametersSize;
      } else {
        missingIndices = 0;
        List<Value> values = newArrayFilled.inValues();
        for (int i = 0; i < parametersSize; ++i) {
          DexType type =
              ConstantValueUtils.getDexTypeRepresentedByValueForTracing(values.get(i), appView);
          if (type == null) {
            return;
          }
          parameterTypes[i] = type;
        }
      }

      for (Instruction user : parametersValue.uniqueUsers()) {
        if (user.isArrayPut()) {
          ArrayPut arrayPutInstruction = user.asArrayPut();
          if (arrayPutInstruction.array() != parametersValue) {
            return;
          }

          int index = arrayPutInstruction.indexIfConstAndInBounds(parametersSize);
          if (index < 0) {
            return;
          }

          DexType type =
              ConstantValueUtils.getDexTypeRepresentedByValueForTracing(
                  arrayPutInstruction.value(), appView);
          if (type == null) {
            return;
          }

          if (type.isIdenticalTo(parameterTypes[index])) {
            continue;
          }
          if (parameterTypes[index] != null) {
            return;
          }
          parameterTypes[index] = type;
          missingIndices--;
        }
      }

      if (missingIndices == 0) {
        initializer = clazz.getProgramInitializer(parameterTypes);
      }
    }

    if (initializer != null) {
      enqueuer.traceReflectiveNewInstance(method, clazz, initializer);
    }
  }

  /**
   * Handles reflective uses of {@link java.lang.reflect.Proxy#newProxyInstance(ClassLoader,
   * Class[], InvocationHandler)}.
   */
  private void handleJavaLangReflectProxyNewProxyInstance(
      ProgramMethod method, InvokeMethod invoke) {
    if (!invoke.isInvokeStatic()) {
      assert false;
      return;
    }

    Value interfacesValue = invoke.arguments().get(1);
    if (interfacesValue.isPhi()) {
      // Give up, we can't tell which interfaces the proxy implements.
      return;
    }

    NewArrayFilled newArrayFilled = interfacesValue.definition.asNewArrayFilled();
    NewArrayEmpty newArrayEmpty = interfacesValue.definition.asNewArrayEmpty();
    List<Value> values;
    if (newArrayFilled != null) {
      values = newArrayFilled.inValues();
    } else if (newArrayEmpty != null) {
      values = new ArrayList<>(interfacesValue.uniqueUsers().size());
      for (Instruction user : interfacesValue.uniqueUsers()) {
        ArrayPut arrayPut = user.asArrayPut();
        if (arrayPut != null) {
          values.add(arrayPut.value());
        }
      }
    } else {
      return;
    }

    WorkList<DexProgramClass> worklist = WorkList.newIdentityWorkList();
    for (Value value : values) {
      DexType type = ConstantValueUtils.getDexTypeRepresentedByValueForTracing(value, appView);
      if (type == null || !type.isClassType()) {
        continue;
      }

      DexProgramClass clazz = enqueuer.getProgramClassOrNullFromReflectiveAccess(type, method);
      if (clazz != null && clazz.isInterface()) {
        KeepReason reason = KeepReason.reflectiveUseIn(method);
        enqueuer.markInterfaceAsInstantiated(
            clazz, enqueuer.getGraphReporter().registerClass(clazz, reason));
        worklist.addIfNotSeen(clazz);
      }
    }

    while (worklist.hasNext()) {
      DexProgramClass clazz = worklist.next();
      assert clazz.isInterface();

      // Keep this interface to ensure that we do not merge the interface into its unique subtype,
      // or merge other interfaces into it horizontally.
      enqueuer
          .getKeepInfo()
          .joinClass(clazz, joiner -> joiner.disallowOptimization().disallowShrinking());

      // Also keep all of its virtual methods to ensure that the devirtualizer does not perform
      // illegal rewritings of invoke-interface instructions into invoke-virtual instructions.
      if (enqueuer.getMode().isInitialTreeShaking()) {
        KeepReason reason = KeepReason.reflectiveUseIn(method);
        clazz.forEachProgramVirtualMethod(
            virtualMethod -> {
              enqueuer
                  .getKeepInfo()
                  .joinMethod(
                      virtualMethod, joiner -> joiner.disallowOptimization().disallowShrinking());
              enqueuer.markVirtualMethodAsReachable(
                  virtualMethod.getReference(), true, method, reason);
            });
      }

      // Repeat for all super interfaces.
      for (DexType implementedType : clazz.getInterfaces()) {
        DexProgramClass implementedClass =
            asProgramClassOrNull(enqueuer.definitionFor(implementedType, clazz));
        if (implementedClass != null && implementedClass.isInterface()) {
          worklist.addIfNotSeen(implementedClass);
        }
      }
    }
  }

  private void handleServiceLoaderInvocation(ProgramMethod method, InvokeMethod invoke) {
    if (invoke.inValues().isEmpty()) {
      // Should never happen.
      return;
    }

    Value argument = invoke.inValues().get(0).getAliasedValue();
    if (!argument.isPhi() && argument.definition.isConstClass()) {
      DexType serviceType = argument.definition.asConstClass().getType();
      if (!appView.appServices().allServiceTypes().contains(serviceType)) {
        // Should never happen.
        return;
      }

      handleServiceInstantiation(serviceType, method, KeepReason.reflectiveUseIn(method));
    } else {
      KeepReason reason = KeepReason.reflectiveUseIn(method);
      for (DexType serviceType : appView.appServices().allServiceTypes()) {
        handleServiceInstantiation(serviceType, method, reason);
      }
    }
  }

  private void handleServiceInstantiation(
      DexType serviceType, ProgramMethod context, KeepReason reason) {
    DexProgramClass serviceClass =
        enqueuer.getProgramClassOrNullFromReflectiveAccess(serviceType, context);
    if (serviceClass != null && !serviceClass.isPublic()) {
      // Package-private service types are allowed only when the load() call is made from the same
      // package.
      enqueuer.applyMinimumKeepInfoWhenLive(
          serviceClass,
          KeepClassInfo.newEmptyJoiner()
              .disallowHorizontalClassMerging()
              .disallowVerticalClassMerging()
              .disallowAccessModification());
    }

    List<DexType> serviceImplementationTypes =
        appView.appServices().serviceImplementationsFor(serviceType);
    for (DexType serviceImplementationType : serviceImplementationTypes) {
      if (!serviceImplementationType.isClassType()) {
        // Should never happen.
        continue;
      }

      DexProgramClass serviceImplementationClass =
          enqueuer.getProgramClassOrNull(serviceImplementationType, context);
      if (serviceImplementationClass == null) {
        continue;
      }

      enqueuer.markClassAsInstantiatedWithReason(serviceImplementationClass, reason);

      ProgramMethod defaultInitializer = serviceImplementationClass.getProgramDefaultInitializer();
      if (defaultInitializer != null) {
        enqueuer.applyMinimumKeepInfoWhenLiveOrTargeted(
            defaultInitializer, KeepMethodInfo.newEmptyJoiner().disallowOptimization());
      }
    }
  }
}
