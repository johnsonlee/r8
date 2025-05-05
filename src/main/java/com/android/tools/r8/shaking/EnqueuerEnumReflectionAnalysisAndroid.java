// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.CheckCast;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.Value;

/**
 * Finds Enum.values() methods that must be kept due to uses of common Android serialization APIs.
 *
 * <p>Looks for:
 *
 * <ul>
 *   <li>Parcel.readSerializable()
 *   <li>Bundle.getSerializable()
 *   <li>Intent.getSerializableExtra()
 * </ul>
 *
 * <p>Similar to non-enum reflection calls, tracing is best-effort. E.g.:
 *
 * <ul>
 *   <li>For the overloads that accept a Class, it must be a const-class
 *   <li>For others, the return value must be directly used by a checked-cast of the Enum type.
 * </ul>
 */
public class EnqueuerEnumReflectionAnalysisAndroid extends EnqueuerEnumReflectionAnalysisBase {
  private final DexType androidContentIntentType;
  private final DexType androidOsParcelType;
  private final DexMethod intentGetSerializableExtra1;
  private final DexMethod intentGetSerializableExtra2;
  private final DexMethod bundleGetSerializable1;
  private final DexMethod bundleGetSerializable2;
  private final DexMethod parcelReadSerializable1;
  private final DexMethod parcelReadSerializable2;

  public EnqueuerEnumReflectionAnalysisAndroid(
      AppView<? extends AppInfoWithClassHierarchy> appView, Enqueuer enqueuer) {
    super(appView, enqueuer);
    DexItemFactory dexItemFactory = appView.dexItemFactory();

    androidContentIntentType = dexItemFactory.createType("Landroid/content/Intent;");
    androidOsParcelType = dexItemFactory.createType("Landroid/os/Parcel;");
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
  }

  @Override
  public void traceInvokeVirtual(
      DexMethod invokedMethod, MethodResolutionResult resolutionResult, ProgramMethod context) {
    if (invokedMethod.isIdenticalTo(bundleGetSerializable1)
        || invokedMethod.isIdenticalTo(bundleGetSerializable2)
        || invokedMethod.isIdenticalTo(parcelReadSerializable1)
        || invokedMethod.isIdenticalTo(parcelReadSerializable2)
        || invokedMethod.isIdenticalTo(intentGetSerializableExtra1)
        || invokedMethod.isIdenticalTo(intentGetSerializableExtra2)) {
      enqueuer.getReflectiveIdentification().enqueue(context);
    }
  }

  @Override
  public boolean handleReflectiveInvoke(ProgramMethod method, InvokeMethod invoke) {
    DexMethod invokedMethod = invoke.getInvokedMethod();
    if (invokedMethod.isIdenticalTo(bundleGetSerializable2)
        || invokedMethod.isIdenticalTo(parcelReadSerializable2)
        || invokedMethod.isIdenticalTo(intentGetSerializableExtra2)) {
      handleReflectiveEnumInvoke(method, invoke, 1);
      return true;
    }
    if (invokedMethod.isIdenticalTo(bundleGetSerializable1)
        || invokedMethod.isIdenticalTo(parcelReadSerializable1)
        || invokedMethod.isIdenticalTo(intentGetSerializableExtra1)) {
      handleEnumCastFromSerializable(method, invoke.outValue());
      return true;
    }
    return false;
  }

  private void handleEnumCastFromSerializable(ProgramMethod context, Value value) {
    // These forms do not take a Class parameter, but are often followed by a checked-cast to their
    // concrete type.
    // There is no checked-cast when the return value is used as an Object (e.g. toString(), or
    // inserted into a List<Object>).
    // Does not identify when the above methods are wrapped in a helper method
    // (e.g. a "IntentUtils.safeGetSerializableExtra()").
    // Does not find enums nested in Serializable types. E.g., and ArrayList of enums.
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
        enqueuer.markEnumValuesAsReachable(enumClass, KeepReason.invokedFrom(context));
      }
    }
  }
}
