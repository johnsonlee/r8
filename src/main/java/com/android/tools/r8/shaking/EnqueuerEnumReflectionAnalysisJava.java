// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.InvokeMethod;

/**
 * Finds Enum.values() methods that must be kept due to uses of OS APIs that use it via reflection.
 *
 * <p>Looks for:
 *
 * <ul>
 *   <li>Enum.valueOf()
 *   <li>EnumSet.allOf()
 *   <li>EnumSet.noneOf()
 *   <li>EnumSet.range()
 *   <li>new EnumMap()
 * </ul>
 *
 * <p>Similar to non-enum reflection calls, tracing is best-effort. E.g.:
 *
 * <ul>
 *   <li>For the overloads that accept a Class, it must be a const-class
 *   <li>For others, an instance of the Enum subclass class must be used.
 * </ul>
 */
public class EnqueuerEnumReflectionAnalysisJava extends EnqueuerEnumReflectionAnalysisBase {
  private final DexString enumSetAllOfString;
  private final DexString enumSetNoneOfString;
  private final DexString enumSetRangeString;
  private final DexString enumSetOfString;
  private final DexType javaUtilEnumMapType;
  private final DexType javaUtilEnumSetType;
  private final DexMethod enumValueOfMethod;
  private final DexMethod enumMapConstructor;

  public EnqueuerEnumReflectionAnalysisJava(
      AppView<? extends AppInfoWithClassHierarchy> appView, Enqueuer enqueuer) {
    super(appView, enqueuer);

    DexItemFactory dexItemFactory = appView.dexItemFactory();
    enumSetAllOfString = dexItemFactory.createString("allOf");
    enumSetNoneOfString = dexItemFactory.createString("noneOf");
    enumSetRangeString = dexItemFactory.createString("range");
    enumSetOfString = dexItemFactory.ofMethodName;
    javaUtilEnumMapType = dexItemFactory.createType("Ljava/util/EnumMap;");
    javaUtilEnumSetType = dexItemFactory.createType("Ljava/util/EnumSet;");
    enumValueOfMethod = dexItemFactory.enumMembers.valueOf;
    enumMapConstructor =
        dexItemFactory.createInstanceInitializer(javaUtilEnumMapType, dexItemFactory.classType);
  }

  private boolean isEnumSetFactoryMethod(DexMethod invokedMethod) {
    if (!invokedMethod.getHolderType().isIdenticalTo(javaUtilEnumSetType)) {
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
      enqueuer.getReflectiveIdentification().enqueue(context);
    }
  }

  @Override
  public void traceInvokeDirect(
      DexMethod invokedMethod, MethodResolutionResult resolutionResult, ProgramMethod context) {
    // EnumMap uses reflection.
    if (invokedMethod.isIdenticalTo(enumMapConstructor)) {
      enqueuer.getReflectiveIdentification().enqueue(context);
    }
  }

  @Override
  public boolean handleReflectiveInvoke(ProgramMethod method, InvokeMethod invoke) {
    DexMethod invokedMethod = invoke.getInvokedMethod();
    if (invokedMethod.isIdenticalTo(enumValueOfMethod)
        || invokedMethod.isIdenticalTo(enumMapConstructor)
        || isEnumSetFactoryMethod(invokedMethod)) {
      handleReflectiveEnumInvoke(method, invoke, 0);
      return true;
    }
    return false;
  }
}
