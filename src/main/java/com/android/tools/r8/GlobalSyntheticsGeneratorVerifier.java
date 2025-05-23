// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.InternalOptions;
import java.util.function.Consumer;

public class GlobalSyntheticsGeneratorVerifier {

  public static void forEachExpectedClass(InternalOptions options, Consumer<DexType> consumer) {
    DexItemFactory dexItemFactory = options.dexItemFactory();
    consumer.accept(dexItemFactory.methodHandlesType);
    consumer.accept(dexItemFactory.methodHandlesLookupType);
    consumer.accept(dexItemFactory.recordType);
    consumer.accept(dexItemFactory.varHandleType);
    if (options.emitLambdaMethodAnnotations) {
      consumer.accept(dexItemFactory.lambdaMethodAnnotation);
    }
  }

  public static boolean verifyExpectedClassesArePresent(AppView<?> appView) {
    forEachExpectedClass(
        appView.options(),
        type -> {
          assert appView.hasDefinitionFor(type);
        });
    return true;
  }
}
