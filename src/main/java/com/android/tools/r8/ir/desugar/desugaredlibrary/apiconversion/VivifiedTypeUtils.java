// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.desugar.desugaredlibrary.apiconversion;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.desugar.desugaredlibrary.DesugaredLibraryTypeRewriter;
import com.android.tools.r8.utils.DescriptorUtils;

public class VivifiedTypeUtils {

  private static final String VIVIFIED_PREFIX = "$-vivified-$.";
  public static final String DESCRIPTOR_VIVIFIED_PREFIX = "L$-vivified-$/";

  public static boolean isVivifiedType(DexType type) {
    return type.descriptor.toString().startsWith(DESCRIPTOR_VIVIFIED_PREFIX);
  }

  public static DexMethod methodWithVivifiedTypeInSignature(
      DexMethod originalMethod, DexType holder, AppView<?> appView) {
    DexType[] newParameters = originalMethod.getParameters().getBacking().clone();
    DesugaredLibraryTypeRewriter typeRewriter =
        appView.options().getLibraryDesugaringOptions().getTypeRewriter();
    int index = 0;
    for (DexType param : originalMethod.getParameters()) {
      if (typeRewriter.hasRewrittenType(param)) {
        newParameters[index] = vivifiedTypeFor(param, appView);
      }
      index++;
    }
    DexType returnType = originalMethod.getReturnType();
    DexType newReturnType =
        typeRewriter.hasRewrittenType(returnType)
            ? vivifiedTypeFor(returnType, appView)
            : returnType;
    DexProto newProto = appView.dexItemFactory().createProto(newReturnType, newParameters);
    return appView.dexItemFactory().createMethod(holder, newProto, originalMethod.name);
  }

  public static DexType vivifiedTypeFor(DexType type, AppView<?> appView) {
    DexType vivifiedType =
        appView
            .dexItemFactory()
            .createSynthesizedType(
                DescriptorUtils.javaTypeToDescriptor(VIVIFIED_PREFIX + type.toString()));
    // We would need to ensure a classpath class for each type to remove this rewriteType call.
    appView
        .options()
        .getLibraryDesugaringOptions()
        .getTypeRewriter()
        .rewriteType(vivifiedType, type);
    return vivifiedType;
  }
}
