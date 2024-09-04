// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.jdk11.desugar.nest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.D8TestCompileResult;
import com.android.tools.r8.JdkClassFileProvider;
import com.android.tools.r8.JvmTestBuilder;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.desugar.nest.NestBasedAccessDesugaring;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class FullNestOnProgramPathTest extends TestBase {

  public static final ImmutableMap<Class<?>, String> NEST_MAIN_RESULT =
      ImmutableMap.<Class<?>, String>builder()
          .put(
              BasicNestHostWithInnerClassFields.class,
              StringUtils.lines(BasicNestHostWithInnerClassFields.getExpectedResult()))
          .put(
              BasicNestHostWithInnerClassMethods.class,
              StringUtils.lines(BasicNestHostWithInnerClassMethods.getExpectedResult()))
          .put(
              BasicNestHostWithInnerClassConstructors.class,
              StringUtils.lines(BasicNestHostWithInnerClassConstructors.getExpectedResult()))
          .put(
              BasicNestHostWithAnonymousInnerClass.class,
              StringUtils.lines(BasicNestHostWithAnonymousInnerClass.getExpectedResult()))
          .put(NestHostExample.class, StringUtils.lines(NestHostExample.getExpectedResult()))
          .build();

  public FullNestOnProgramPathTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withCfRuntimesStartingFromIncluding(CfVm.JDK11)
        .withDexRuntimes()
        .withApiLevelsStartingAtIncluding(apiLevelWithInvokeCustomSupport())
        .enableApiLevelsForCf()
        .build();
  }

  @Test
  public void testAllNestsReference() throws Exception {
    parameters.assumeJvmTestParameters();
    JvmTestBuilder jvmTestBuilder =
        testForJvm(parameters).addProgramClassesAndInnerClasses(NEST_MAIN_RESULT.keySet());
    for (Class<?> clazz : NEST_MAIN_RESULT.keySet()) {
      jvmTestBuilder
          .run(parameters.getRuntime(), clazz)
          .assertSuccessWithOutput(NEST_MAIN_RESULT.get(clazz));
    }
  }

  @Test
  public void testAllNestsD8() throws Exception {
    D8TestCompileResult compileResult =
        testForD8(parameters.getBackend())
            .addProgramClassesAndInnerClasses(NEST_MAIN_RESULT.keySet())
            .setMinApi(parameters)
            .compile()
            .applyIf(parameters.isDexRuntime(), b -> b.inspect(this::assertOnlyRequiredBridges));
    for (Class<?> clazz : NEST_MAIN_RESULT.keySet()) {
      compileResult
          .run(parameters.getRuntime(), clazz)
          .assertSuccessWithOutput(NEST_MAIN_RESULT.get(clazz));
    }
  }

  @Test
  public void testAllNestsR8() throws Exception {
    parameters.assumeR8TestParameters();
    R8TestCompileResult compileResult =
        testForR8(parameters.getBackend())
            .addDontShrink()
            .addDontObfuscate()
            .addKeepAllAttributes()
            .addOptionsModification(options -> options.enableNestReduction = false)
            .addProgramClassesAndInnerClasses(NEST_MAIN_RESULT.keySet())
            .applyIf(
                parameters.isCfRuntime(),
                b -> b.addLibraryProvider(JdkClassFileProvider.fromSystemJdk()))
            .setMinApi(parameters)
            .compile()
            .inspect(this::assertOnlyRequiredBridges)
            .applyIf(
                parameters.isCfRuntime(),
                b -> b.inspect(FullNestOnProgramPathTest::checkNestMateAttributes));
    for (Class<?> clazz : NEST_MAIN_RESULT.keySet()) {
      compileResult
          .run(parameters.getRuntime(), clazz)
          .assertSuccessWithOutput(NEST_MAIN_RESULT.get(clazz));
    }
  }

  // Single Nest test compile only the nest into dex,
  // then run the main class from this dex.
  @Test
  public void testSingleNestR8() throws Exception {
    parameters.assumeR8TestParameters();
    for (Class<?> clazz : NEST_MAIN_RESULT.keySet()) {
      testForR8(parameters.getBackend())
          .addDontShrink()
          .addDontObfuscate()
          .addKeepAllAttributes()
          .setMinApi(parameters)
          .addProgramClassesAndInnerClasses(clazz)
          .addOptionsModification(options -> options.enableNestReduction = false)
          .applyIf(
              parameters.isCfRuntime(),
              b -> b.addLibraryProvider(JdkClassFileProvider.fromSystemJdk()))
          .compile()
          .run(parameters.getRuntime(), clazz)
          .assertSuccessWithOutput(NEST_MAIN_RESULT.get(clazz));
    }
  }

  @Test
  public void testSingleNestD8() throws Exception {
    for (Class<?> clazz : NEST_MAIN_RESULT.keySet()) {
      testForD8(parameters.getBackend())
          .setMinApi(parameters)
          .addProgramClassesAndInnerClasses(clazz)
          .compile()
          .run(parameters.getRuntime(), clazz)
          .assertSuccessWithOutput(NEST_MAIN_RESULT.get(clazz));
    }
  }

  private static int minNumberOfTestClasses() {
    return NEST_MAIN_RESULT.keySet().stream()
        .mapToInt(
            c -> {
              try {
                return ToolHelper.getClassFilesForInnerClasses(c).size() + 1;
              } catch (IOException e) {
                throw new RuntimeException(e);
              }
            })
        .sum();
  }

  private static void checkNestMateAttributes(CodeInspector inspector) {
    // Interface method desugaring may add extra classes
    assertTrue(minNumberOfTestClasses() <= inspector.allClasses().size());
    List<String> outerClassNames =
        NEST_MAIN_RESULT.keySet().stream().map(Class::getSimpleName).collect(Collectors.toList());
    ImmutableList<String> nonNestClasses =
        ImmutableList.of("NeverInline", "OutsideInliningNoAccess", "OutsideInliningWithAccess");
    inspector.forAllClasses(
        classSubject -> {
          DexClass dexClass = classSubject.getDexProgramClass();
          if (!nonNestClasses.contains(dexClass.type.getName())) {
            assertTrue(dexClass.isInANest());
            if (outerClassNames.contains(dexClass.type.getName())) {
              assertNull(dexClass.getNestHostClassAttribute());
              assertFalse(dexClass.getNestMembersClassAttributes().isEmpty());
            } else {
              assertTrue(dexClass.getNestMembersClassAttributes().isEmpty());
              assertTrue(
                  outerClassNames.contains(
                      dexClass.getNestHostClassAttribute().getNestHost().getName()));
            }
          }
        });
  }

  private void assertOnlyRequiredBridges(CodeInspector inspector) {
    // The following 2 classes have an extra private member which does not require a bridge.

    // Two bridges for method and staticMethod.
    int methodNumBridges = parameters.isCfRuntime() ? 0 : 2;
    ClassSubject methodMainClass = inspector.clazz(BasicNestHostWithInnerClassMethods.class);
    assertEquals(methodNumBridges, methodMainClass.allMethods(this::isNestBridge).size());

    // Two bridges for method and staticMethod.
    int constructorNumBridges = parameters.isCfRuntime() ? 0 : 1;
    ClassSubject constructorMainClass =
        inspector.clazz(BasicNestHostWithInnerClassConstructors.class);
    assertEquals(constructorNumBridges, constructorMainClass.allMethods(this::isNestBridge).size());

    // Four bridges for field and staticField, both get & set.
    int fieldNumBridges = parameters.isCfRuntime() ? 0 : 4;
    ClassSubject fieldMainClass = inspector.clazz(BasicNestHostWithInnerClassFields.class);
    assertEquals(fieldNumBridges, fieldMainClass.allMethods(this::isNestBridge).size());
  }

  private boolean isNestBridge(FoundMethodSubject methodSubject) {
    DexEncodedMethod method = methodSubject.getMethod();
    if (method.isInstanceInitializer()) {
      if (method.getReference().proto.parameters.isEmpty()) {
        return false;
      }
      DexType[] formals = method.getReference().proto.parameters.values;
      DexType lastFormal = formals[formals.length - 1];
      return lastFormal.isClassType()
          && SyntheticItemsTestUtils.isInitializerTypeArgument(
              Reference.classFromDescriptor(lastFormal.toDescriptorString()));
    }
    return method
        .getReference()
        .name
        .toString()
        .startsWith(NestBasedAccessDesugaring.NEST_ACCESS_NAME_PREFIX);
  }
}
