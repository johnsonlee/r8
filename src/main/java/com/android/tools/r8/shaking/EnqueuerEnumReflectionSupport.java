// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.analysis.EnqueuerAnalysisCollection;
import com.android.tools.r8.graph.analysis.FixpointEnqueuerAnalysis;
import com.android.tools.r8.graph.analysis.TraceInvokeEnqueuerAnalysis;
import com.android.tools.r8.ir.analysis.type.ClassTypeElement;
import com.android.tools.r8.ir.code.CheckCast;
import com.android.tools.r8.ir.code.ConstClass;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionIterator;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.MethodConversionOptions;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;

/**
 * Finds Enum.valueOf() methods that must be kept due to uses of OS APIs that use it via reflection.
 *
 * <p>Looks for:
 *
 * <ul>
 *   <li>Enum.valueOf()
 *   <li>EnumSet.allOf()
 *   <li>EnumSet.noneOf()
 *   <li>EnumSet.range()
 *   <li>new EnumMap()
 *   <li>Parcel.readSerializable()
 *   <li>Bundle.getSerializable()
 *   <li>Intent.getSerializableExtra()
 *   <li>ObjectInputStream.readObject()
 * </ul>
 *
 * <p>Similar to non-enum reflection calls, tracing is best-effort. E.g.:
 *
 * <ul>
 *   <li>For the overloads that accept a Class, it must be a const-class
 *   <li>For others, the return value must be directly used by a checked-cast of the Enum type.
 * </ul>
 */
public class EnqueuerEnumReflectionSupport
    implements TraceInvokeEnqueuerAnalysis, FixpointEnqueuerAnalysis {
  private final AppView<? extends AppInfoWithClassHierarchy> appView;
  private final BiConsumer<DexProgramClass, KeepReason> markAsReachableCallback;

  private final DexString enumSetAllOfString;
  private final DexString enumSetNoneOfString;
  private final DexString enumSetRangeString;
  private final DexString enumSetOfString;
  private final DexType androidContentIntentType;
  private final DexType androidOsParcelType;
  private final DexType javaIoObjectInputStreamType;
  private final DexType javaUtilEnumMapType;
  private final DexType javaUtilEnumSetType;

  private final DexMethod intentGetSerializableExtra1;
  private final DexMethod intentGetSerializableExtra2;
  private final DexMethod bundleGetSerializable1;
  private final DexMethod bundleGetSerializable2;
  private final DexMethod parcelReadSerializable1;
  private final DexMethod parcelReadSerializable2;

  private final DexMethod objectInputStreamReadObject;
  private final DexMethod enumValueOfMethod;
  private final DexMethod enumMapConstructor;
  private final ProgramMethodSet pendingReflectiveUses = ProgramMethodSet.createLinked();

  public EnqueuerEnumReflectionSupport(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      BiConsumer<DexProgramClass, KeepReason> markAsReachableCallback) {
    this.appView = appView;
    this.markAsReachableCallback = markAsReachableCallback;

    DexItemFactory dexItemFactory = appView.dexItemFactory();

    enumSetAllOfString = dexItemFactory.createString("allOf");
    enumSetNoneOfString = dexItemFactory.createString("noneOf");
    enumSetRangeString = dexItemFactory.createString("range");
    enumSetOfString = dexItemFactory.ofMethodName;

    androidContentIntentType = dexItemFactory.createType("Landroid/content/Intent;");
    androidOsParcelType = dexItemFactory.createType("Landroid/os/Parcel;");
    javaIoObjectInputStreamType = dexItemFactory.createType("Ljava/io/ObjectInputStream;");
    javaUtilEnumMapType = dexItemFactory.createType("Ljava/util/EnumMap;");
    javaUtilEnumSetType = dexItemFactory.createType("Ljava/util/EnumSet;");

    intentGetSerializableExtra1 =
        dexItemFactory.createMethod(
            androidContentIntentType,
            dexItemFactory.createProto(dexItemFactory.serializableType, dexItemFactory.stringType),
            "getSerializableExtra");
    intentGetSerializableExtra2 =
        dexItemFactory.createMethod(
            androidContentIntentType,
            dexItemFactory.createProto(
                dexItemFactory.serializableType,
                dexItemFactory.stringType,
                dexItemFactory.classType),
            "getSerializableExtra");
    bundleGetSerializable1 =
        dexItemFactory.createMethod(
            dexItemFactory.androidOsBundleType,
            dexItemFactory.createProto(dexItemFactory.serializableType, dexItemFactory.stringType),
            "getSerializable");
    bundleGetSerializable2 =
        dexItemFactory.createMethod(
            dexItemFactory.androidOsBundleType,
            dexItemFactory.createProto(
                dexItemFactory.serializableType,
                dexItemFactory.stringType,
                dexItemFactory.classType),
            "getSerializable");
    parcelReadSerializable1 =
        dexItemFactory.createMethod(
            androidOsParcelType,
            dexItemFactory.createProto(dexItemFactory.serializableType),
            "readSerializable");
    parcelReadSerializable2 =
        dexItemFactory.createMethod(
            androidOsParcelType,
            dexItemFactory.createProto(
                dexItemFactory.serializableType,
                dexItemFactory.classLoaderType,
                dexItemFactory.classType),
            "readSerializable");
    objectInputStreamReadObject =
        dexItemFactory.createMethod(
            javaIoObjectInputStreamType,
            dexItemFactory.createProto(dexItemFactory.objectType),
            "readObject");
    enumValueOfMethod = dexItemFactory.enumMembers.valueOf;
    enumMapConstructor =
        dexItemFactory.createMethod(
            javaUtilEnumMapType,
            dexItemFactory.createProto(dexItemFactory.voidType, dexItemFactory.classType),
            dexItemFactory.constructorMethodName);
  }

  public static void register(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      BiConsumer<DexProgramClass, KeepReason> markAsReachableCallback,
      EnqueuerAnalysisCollection.Builder builder) {
    EnqueuerEnumReflectionSupport instance =
        new EnqueuerEnumReflectionSupport(appView, markAsReachableCallback);
    builder.addTraceInvokeAnalysis(instance);
    builder.addFixpointAnalysis(instance);
  }

  private DexProgramClass maybeGetProgramEnumType(DexType type, boolean checkSuper) {
    // Arrays can be used for serialization-related methods.
    if (type.isArrayType()) {
      type = type.toBaseType(appView.dexItemFactory());
      if (!type.isClassType()) {
        return null;
      }
    }

    DexClass dexClass = appView.definitionFor(type);
    if (dexClass == null || !dexClass.isProgramClass() || !dexClass.hasSuperType()) {
      return null;
    }
    DexType superType = dexClass.getSuperType();
    if (superType.isIdenticalTo(appView.dexItemFactory().enumType)) {
      return dexClass.asProgramClass();
    }
    // Cannot have a sub-sub-type of an Enum.
    return checkSuper ? maybeGetProgramEnumType(superType, false) : null;
  }

  private void handleEnumCastFromSerializable(ProgramMethod context, Value value) {
    if (value == null || value.isPhi()) {
      return;
    }
    for (Instruction user : value.aliasedUsers()) {
      CheckCast castInstr = user.asCheckCast();
      if (castInstr == null) {
        continue;
      }
      DexProgramClass enumClass = maybeGetProgramEnumType(castInstr.getType(), true);
      if (enumClass != null) {
        markAsReachableCallback.accept(enumClass, KeepReason.invokedFrom(context));
      }
    }
  }

  private void handleReflectiveEnumInvoke(
      ProgramMethod context, InvokeMethod invoke, int classParamIndex) {
    if (invoke.inValues().size() <= classParamIndex) {
      // Should never happen.
      return;
    }

    // The use of java.lang.Enum.valueOf(java.lang.Class, java.lang.String) will indirectly
    // access the values() method of the enum class passed as the first argument. The method
    // SomeEnumClass.valueOf(java.lang.String) which is generated by javac for all enums will
    // call this method.
    // Likewise, EnumSet and EnumMap call values() on the passed in Class.
    Value classArg = invoke.getArgumentForParameter(classParamIndex);
    if (classArg.isPhi()) {
      return;
    }
    DexType type;
    if (invoke
        .getInvokedMethod()
        .getParameter(classParamIndex)
        .isIdenticalTo(appView.dexItemFactory().classType)) {
      // EnumMap.<init>(), EnumSet.noneOf(), EnumSet.allOf(), Enum.valueOf().
      ConstClass constClass = classArg.definition.asConstClass();
      if (constClass == null) {
        return;
      }
      type = constClass.getType();
    } else {
      // EnumSet.of(), EnumSet.range()
      ClassTypeElement typeElement = classArg.getType().asClassType();
      if (typeElement == null) {
        return;
      }
      type = typeElement.getClassType();
    }
    // Arrays can be used for serialization-related methods.
    if (type.isArrayType()) {
      type = type.toBaseType(appView.dexItemFactory());
    }
    DexClass clazz = appView.definitionFor(type, context);
    if (clazz != null && clazz.isProgramClass() && clazz.isEnum()) {
      markAsReachableCallback.accept(clazz.asProgramClass(), KeepReason.invokedFrom(context));
    }
  }

  private void handleReflectiveBehavior(ProgramMethod method) {
    IRCode code = method.buildIR(appView, MethodConversionOptions.nonConverting());
    InstructionIterator iterator = code.instructionIterator();
    while (iterator.hasNext()) {
      Instruction instruction = iterator.next();
      if (!instruction.isInvokeMethod()) {
        continue;
      }
      InvokeMethod invoke = instruction.asInvokeMethod();
      DexMethod invokedMethod = invoke.getInvokedMethod();
      if (invokedMethod.isIdenticalTo(enumValueOfMethod)
          || invokedMethod.isIdenticalTo(enumMapConstructor)
          || isEnumSetFactoryMethod(invokedMethod)) {
        handleReflectiveEnumInvoke(method, invoke, 0);
      } else if (invokedMethod.isIdenticalTo(bundleGetSerializable2)
          || invokedMethod.isIdenticalTo(parcelReadSerializable2)
          || invokedMethod.isIdenticalTo(intentGetSerializableExtra2)) {
        handleReflectiveEnumInvoke(method, invoke, 1);
      } else if (invokedMethod.isIdenticalTo(bundleGetSerializable1)
          || invokedMethod.isIdenticalTo(parcelReadSerializable1)
          || invokedMethod.isIdenticalTo(intentGetSerializableExtra1)
          || invokedMethod.isIdenticalTo(objectInputStreamReadObject)) {
        // These forms do not take a Class parameter, but are often followed by a checked-cast.
        // Get the enum type from the checked-cast.
        // There is no checked-cast when the return value is used as an Object (e.g. toString(), or
        // inserted into a List<Object>).
        // This also fails to identify when the above methods are wrapped in a helper method
        // (e.g. a "IntentUtils.safeGetSerializableExtra()").
        handleEnumCastFromSerializable(method, invoke.outValue());
      }
    }
  }

  private boolean isEnumSetFactoryMethod(DexMethod invokedMethod) {
    if (!invokedMethod.getHolderType().equals(javaUtilEnumSetType)) {
      return false;
    }
    DexString name = invokedMethod.getName();
    return name.isIdenticalTo(enumSetAllOfString)
        || name.isIdenticalTo(enumSetNoneOfString)
        || name.isIdenticalTo(enumSetOfString)
        || name.isIdenticalTo(enumSetRangeString);
  }

  @Override
  public void traceInvokeStatic(
      DexMethod invokedMethod, MethodResolutionResult resolutionResult, ProgramMethod context) {
    if (invokedMethod.isIdenticalTo(enumValueOfMethod) || isEnumSetFactoryMethod(invokedMethod)) {
      pendingReflectiveUses.add(context);
    }
  }

  @Override
  public void traceInvokeDirect(
      DexMethod invokedMethod, MethodResolutionResult resolutionResult, ProgramMethod context) {
    // EnumMap uses reflection.
    if (invokedMethod.isIdenticalTo(enumMapConstructor)) {
      pendingReflectiveUses.add(context);
    }
  }

  @Override
  public void traceInvokeVirtual(
      DexMethod invokedMethod, MethodResolutionResult resolutionResult, ProgramMethod context) {
    if (invokedMethod.isIdenticalTo(bundleGetSerializable1)
        || invokedMethod.isIdenticalTo(bundleGetSerializable2)
        || invokedMethod.isIdenticalTo(parcelReadSerializable1)
        || invokedMethod.isIdenticalTo(parcelReadSerializable2)
        || invokedMethod.isIdenticalTo(intentGetSerializableExtra1)
        || invokedMethod.isIdenticalTo(intentGetSerializableExtra2)
        || invokedMethod.isIdenticalTo(objectInputStreamReadObject)) {
      pendingReflectiveUses.add(context);
    }
  }

  @Override
  public void notifyFixpoint(
      Enqueuer enqueuer,
      EnqueuerWorklist worklist,
      ExecutorService executorService,
      Timing timing) {
    if (!pendingReflectiveUses.isEmpty()) {
      timing.begin("Handle reflective Enum operations");
      pendingReflectiveUses.forEach(this::handleReflectiveBehavior);
      pendingReflectiveUses.clear();
      timing.end();
    }
  }
}
