// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.switchpatternmatching;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;

public class SwitchTestHelper {

  public static boolean hasJdk21TypeSwitch(MethodSubject methodSubject) {
    return hasJdk21Switch(methodSubject, "typeSwitch");
  }

  public static boolean hasJdk21EnumSwitch(MethodSubject methodSubject) {
    return hasJdk21Switch(methodSubject, "enumSwitch");
  }

  private static boolean hasJdk21Switch(MethodSubject methodSubject, String switchMethod) {
    assert methodSubject.isPresent();
    // javac generated an invokedynamic using bootstrap method
    // java.lang.runtime.SwitchBootstraps.typeSwitch|enumSwitch.
    return methodSubject
        .streamInstructions()
        .filter(InstructionSubject::isInvokeDynamic)
        .map(
            instruction ->
                instruction
                    .asCfInstruction()
                    .getInstruction()
                    .asInvokeDynamic()
                    .getCallSite()
                    .getBootstrapMethod()
                    .member
                    .asDexMethod())
        .filter(
            method ->
                method.getHolderType().toString().contains("java.lang.runtime.SwitchBootstraps"))
        .anyMatch(method -> method.toString().contains(switchMethod));
  }

  public static String matchException(TestParameters parameters) {
    return parameters.isCfRuntime() ? matchException() : desugarMatchException();
  }

  public static String matchException() {
    return "java.lang.MatchException";
  }

  public static String desugarMatchException() {
    return "java.lang.RuntimeException";
  }
}
