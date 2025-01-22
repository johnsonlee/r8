// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexDefinitionSupplier;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.type.ArrayTypeElement;
import com.android.tools.r8.ir.analysis.type.ClassTypeElement;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.shaking.KeepInfoCollection.MutableKeepInfoCollection;

class EnqueuerMockitoSupport {

  static boolean isReflectiveMockInvoke(DexItemFactory dexItemFactory, DexMethod invokedMethod) {
    return invokedMethod.holder.isIdenticalTo(dexItemFactory.mockitoType)
        && (invokedMethod.getName().isIdenticalTo(dexItemFactory.mockString)
            || invokedMethod.getName().isIdenticalTo(dexItemFactory.spyString));
  }

  /** Ensure classes passed to Mockito.mock() and Mockito.spy() are not marked as "final". */
  static void handleReflectiveMockInvoke(
      DexDefinitionSupplier appView,
      MutableKeepInfoCollection keepInfo,
      ProgramMethod context,
      InvokeMethod invoke) {
    DexMethod method = invoke.getInvokedMethod();
    DexItemFactory dexItemFactory = appView.dexItemFactory();

    DexType mockedType;
    if (method.getParameter(0).isIdenticalTo(dexItemFactory.classType)) {
      // Given an explicit const-cast
      Value classValue = invoke.getFirstArgument();
      if (!classValue.isConstClass()) {
        return;
      }
      mockedType = classValue.getDefinition().asConstClass().getType();
    } else if (method.getParameter(method.getArity() - 1).isArrayType()) {
      // This should always be an empty array of the mocked type.
      Value arrayValue = invoke.getLastArgument();
      ArrayTypeElement arrayType = arrayValue.getType().asArrayType();
      if (arrayType == null) {
        // Should never happen.
        return;
      }
      ClassTypeElement memberType = arrayType.getMemberType().asClassType();
      if (memberType == null) {
        return;
      }
      mockedType = memberType.getClassType();
    } else {
      // Should be Mockito.spy(Object).
      if (method.getArity() != 1
          || !method.getParameter(0).isIdenticalTo(dexItemFactory.objectType)) {
        return;
      }
      Value objectValue = invoke.getFirstArgument();
      if (objectValue == null || objectValue.isPhi()) {
        return;
      }
      ClassTypeElement classType = objectValue.getType().asClassType();
      if (classType == null) {
        return;
      }
      mockedType = classType.toDexType(dexItemFactory);
    }

    DexClass dexClass = appView.definitionFor(mockedType, context);
    if (dexClass == null || !dexClass.isProgramClass()) {
      return;
    }

    // Make sure the type is not made final so that it can still be subclassed by Mockito.
    keepInfo.joinClass(dexClass.asProgramClass(), joiner -> joiner.disallowOptimization());
  }
}
