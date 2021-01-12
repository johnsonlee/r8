// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.synthesis;

import static org.hamcrest.CoreMatchers.containsString;

import com.android.tools.r8.ir.desugar.InterfaceMethodRewriter;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.synthesis.SyntheticNaming.Phase;
import com.android.tools.r8.synthesis.SyntheticNaming.SyntheticKind;
import java.lang.reflect.Method;
import org.hamcrest.Matcher;

public class SyntheticItemsTestUtils {

  public static ClassReference syntheticCompanionClass(Class<?> clazz) {
    return Reference.classFromDescriptor(
        InterfaceMethodRewriter.getCompanionClassDescriptor(
            Reference.classFromClass(clazz).getDescriptor()));
  }

  private static ClassReference syntheticClass(Class<?> clazz, SyntheticKind kind, int id) {
    return SyntheticNaming.makeSyntheticReferenceForTest(
        Reference.classFromClass(clazz), kind, "" + id);
  }

  public static MethodReference syntheticBackportMethod(Class<?> clazz, int id, Method method) {
    ClassReference syntheticHolder =
        syntheticClass(clazz, SyntheticNaming.SyntheticKind.BACKPORT, id);
    MethodReference originalMethod = Reference.methodFromMethod(method);
    return Reference.methodFromDescriptor(
        syntheticHolder.getDescriptor(),
        SyntheticNaming.INTERNAL_SYNTHETIC_METHOD_PREFIX,
        originalMethod.getMethodDescriptor());
  }

  public static ClassReference syntheticLambdaClass(Class<?> clazz, int id) {
    return syntheticClass(clazz, SyntheticNaming.SyntheticKind.LAMBDA, id);
  }

  public static MethodReference syntheticLambdaMethod(Class<?> clazz, int id, Method method) {
    ClassReference syntheticHolder = syntheticLambdaClass(clazz, id);
    MethodReference originalMethod = Reference.methodFromMethod(method);
    return Reference.methodFromDescriptor(
        syntheticHolder.getDescriptor(),
        originalMethod.getMethodName(),
        originalMethod.getMethodDescriptor());
  }

  public static boolean isInternalLambda(ClassReference reference) {
    return SyntheticNaming.isSynthetic(reference, Phase.INTERNAL, SyntheticKind.LAMBDA);
  }

  public static boolean isExternalLambda(ClassReference reference) {
    return SyntheticNaming.isSynthetic(reference, Phase.EXTERNAL, SyntheticKind.LAMBDA);
  }

  public static boolean isExternalStaticInterfaceCall(ClassReference reference) {
    return SyntheticNaming.isSynthetic(
        reference, Phase.EXTERNAL, SyntheticKind.STATIC_INTERFACE_CALL);
  }

  public static boolean isExternalTwrCloseMethod(ClassReference reference) {
    return SyntheticNaming.isSynthetic(reference, Phase.EXTERNAL, SyntheticKind.TWR_CLOSE_RESOURCE);
  }

  public static Matcher<String> containsInternalSyntheticReference() {
    return containsString(SyntheticNaming.getPhaseSeparator(Phase.INTERNAL));
  }

  public static Matcher<String> containsExternalSyntheticReference() {
    return containsString(SyntheticNaming.getPhaseSeparator(Phase.EXTERNAL));
  }
}