// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.rewrite.arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.Keep;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.dex.code.DexFillArrayData;
import com.android.tools.r8.dex.code.DexFilledNewArray;
import com.android.tools.r8.dex.code.DexFilledNewArrayRange;
import com.android.tools.r8.dex.code.DexNewArray;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.transformers.ClassFileTransformer;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.Lists;
import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SimplifyArrayConstructionTest extends TestBase {
  @Parameters(name = "{0}, mode = {1}")
  public static Iterable<?> data() {
    return buildParameters(
        getTestParameters().withDefaultCfRuntime().withDexRuntimesAndAllApiLevels().build(),
        CompilationMode.values());
  }

  private final TestParameters parameters;
  private final CompilationMode compilationMode;

  public SimplifyArrayConstructionTest(TestParameters parameters, CompilationMode compilationMode) {
    this.parameters = parameters;
    this.compilationMode = compilationMode;
  }

  private static final Class<?>[] DEX_ARRAY_INSTRUCTIONS = {
    DexNewArray.class, DexFilledNewArray.class, DexFillArrayData.class
  };

  private static final String[] EXPECTED_OUTPUT = {
    "[a]",
    "[a, 1, null]",
    "[1, null]",
    "[1, null, 2]",
    "[1, null, 2]",
    "[1]",
    "[1, 2, 3]",
    "[2, 2, 2]",
    "[6, 7]",
    "[7]",
    "[3, 4]",
    "[99]",
    "[0, 1]",
    "[0, 1]",
    "[0, 1]",
    "[0, 1]",
    "[0, 1]",
    "[0, 1]",
    "[1, 2, 3, 4, 5]",
    "[1]",
    "[a, 1, null, d, e, f]",
    "[1, null, 3, null, null, 6]",
    "[1, 2, 3, 4, 5, 6]",
    "[true, false]",
    "[1, 2]",
    "[1, 2]",
    "[1, 2]",
    "[1.0, 2.0]",
    "[1.0, 2.0]",
    "[]",
    "[]",
    "[true]",
    "[1]",
    "[1]",
    "[1]",
    "[1.0]",
    "[1.0]",
    "[0, 1]",
    "[1, null]",
    "[a]",
    "[0, 1]",
    "200",
    "[0, 1, 2, 3, 4]",
    "[4, 0, 0, 0, 0]",
    "[4, 1, 2, 3, 4]",
    "[9]",
    "[*]",
    "[*]",
    "finally: [1, 2]",
    "[1, 2]",
    "[1, 2]",
    "[1, 2]",
    "[1, 2]",
    "[0, 1, 2]",
    "[0]",
    "[null, null]",
  };

  private static final byte[] TRANSFORMED_MAIN = transformedMain();

  @Test
  public void testRuntime() throws Exception {
    assumeFalse(compilationMode == CompilationMode.DEBUG);
    testForRuntime(
            parameters.getRuntime(),
            d8TestBuilder -> d8TestBuilder.setMinApi(parameters).setMode(compilationMode))
        .addProgramClassFileData(TRANSFORMED_MAIN)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED_OUTPUT)
        .inspect(inspector -> inspect(inspector, false));
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .setMinApi(parameters)
        .addOptionsModification(
            options ->
                options
                    .getOpenClosedInterfacesOptions()
                    .suppressSingleOpenInterface(Reference.classFromClass(Serializable.class)))
        .setMode(compilationMode)
        .addProgramClassFileData(TRANSFORMED_MAIN)
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .addKeepAnnotation()
        .addKeepRules("-keepclassmembers class * { @com.android.tools.r8.Keep *; }")
        .addKeepRules("-assumenosideeffects class * { *** assumedNullField return null; }")
        .addKeepRules("-assumenosideeffects class * { *** assumedNonNullField return _NONNULL_; }")
        .addDontObfuscate()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED_OUTPUT)
        .inspect(inspector -> inspect(inspector, true));
  }

  private static byte[] transformedMain() {
    try {
      return transformer(Main.class)
          .transformMethodInsnInMethod(
              "interfaceArrayWithRawObject",
              (opcode, owner, name, descriptor, isInterface, visitor) -> {
                if (name.equals("getObjectThatImplementsSerializable")) {
                  visitor.visitMethodInsn(opcode, owner, name, "()Ljava/lang/Object;", isInterface);
                } else {
                  visitor.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                }
              })
          .setReturnType(
              ClassFileTransformer.MethodPredicate.onName("getObjectThatImplementsSerializable"),
              Object.class.getTypeName())
          .transform();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void inspect(CodeInspector inspector, boolean isR8) {
    if (parameters.isCfRuntime()) {
      return;
    }
    ClassSubject mainClass = inspector.clazz(Main.class);
    assertTrue(mainClass.isPresent());

    MethodSubject stringArrays = mainClass.uniqueMethodWithOriginalName("stringArrays");
    MethodSubject referenceArraysNoCasts =
        mainClass.uniqueMethodWithOriginalName("referenceArraysNoCasts");
    MethodSubject referenceArraysWithSubclasses =
        mainClass.uniqueMethodWithOriginalName("referenceArraysWithSubclasses");
    MethodSubject referenceArraysWithInterfaceImplementations =
        mainClass.uniqueMethodWithOriginalName("referenceArraysWithInterfaceImplementations");
    MethodSubject interfaceArrayWithRawObject =
        mainClass.uniqueMethodWithOriginalName("interfaceArrayWithRawObject");

    MethodSubject phiFilledNewArray = mainClass.uniqueMethodWithOriginalName("phiFilledNewArray");
    MethodSubject phiFilledNewArrayBlocks =
        mainClass.uniqueMethodWithOriginalName("phiFilledNewArrayBlocks");
    MethodSubject arrayWithDominatingPhiUsers =
        mainClass.uniqueMethodWithOriginalName("arrayWithDominatingPhiUsers");
    MethodSubject arrayWithNonDominatingPhiUsers =
        mainClass.uniqueMethodWithOriginalName("arrayWithNonDominatingPhiUsers");
    MethodSubject phiWithExceptionalPhiUser =
        mainClass.uniqueMethodWithOriginalName("phiWithExceptionalPhiUser");
    MethodSubject phiWithNestedCatchHandler =
        mainClass.uniqueMethodWithOriginalName("phiWithNestedCatchHandler");
    MethodSubject multiUseArray = mainClass.uniqueMethodWithOriginalName("multiUseArray");
    MethodSubject arrayWithHole = mainClass.uniqueMethodWithOriginalName("arrayWithHole");
    MethodSubject reassignmentDoesNotOptimize =
        mainClass.uniqueMethodWithOriginalName("reassignmentDoesNotOptimize");
    MethodSubject intsThatUseFilledNewArray =
        mainClass.uniqueMethodWithOriginalName("intsThatUseFilledNewArray");
    MethodSubject twoDimensionalArrays =
        mainClass.uniqueMethodWithOriginalName("twoDimensionalArrays");
    MethodSubject objectArraysFilledNewArrayRange =
        mainClass.uniqueMethodWithOriginalName("objectArraysFilledNewArrayRange");
    MethodSubject arraysThatUseFilledData =
        mainClass.uniqueMethodWithOriginalName("arraysThatUseFilledData");
    MethodSubject arraysThatUseNewArrayEmpty =
        mainClass.uniqueMethodWithOriginalName("arraysThatUseNewArrayEmpty");
    MethodSubject reversedArray = mainClass.uniqueMethodWithOriginalName("reversedArray");
    MethodSubject arrayWithCorrectCountButIncompleteCoverage =
        mainClass.uniqueMethodWithOriginalName("arrayWithCorrectCountButIncompleteCoverage");
    MethodSubject arrayWithExtraInitialPuts =
        mainClass.uniqueMethodWithOriginalName("arrayWithExtraInitialPuts");
    MethodSubject catchHandlerWithoutSideeffects =
        mainClass.uniqueMethodWithOriginalName("catchHandlerWithoutSideeffects");
    MethodSubject allocationWithCatchHandler =
        mainClass.uniqueMethodWithOriginalName("allocationWithCatchHandler");
    MethodSubject allocationWithoutCatchHandler =
        mainClass.uniqueMethodWithOriginalName("allocationWithoutCatchHandler");
    MethodSubject catchHandlerWithFinally =
        mainClass.uniqueMethodWithOriginalName("catchHandlerWithFinally");
    MethodSubject simpleSynchronized1 =
        mainClass.uniqueMethodWithOriginalName("simpleSynchronized1");
    MethodSubject simpleSynchronized2 =
        mainClass.uniqueMethodWithOriginalName("simpleSynchronized2");
    MethodSubject simpleSynchronized3 =
        mainClass.uniqueMethodWithOriginalName("simpleSynchronized3");
    MethodSubject simpleSynchronized4 =
        mainClass.uniqueMethodWithOriginalName("simpleSynchronized4");
    MethodSubject arrayInsideCatchHandler =
        mainClass.uniqueMethodWithOriginalName("arrayInsideCatchHandler");
    MethodSubject assumedValues = mainClass.uniqueMethodWithOriginalName("assumedValues");

    // The explicit assignments can't be collapsed without breaking the debugger's ability to
    // visit each line.
    Class<?> filledNewArrayInRelease =
        compilationMode == CompilationMode.DEBUG ? DexNewArray.class : DexFilledNewArray.class;

    assertArrayTypes(arraysThatUseNewArrayEmpty, DexNewArray.class);
    assertArrayTypes(intsThatUseFilledNewArray, DexFilledNewArray.class);
    assertFilledArrayData(arraysThatUseFilledData);

    // Algorithm does not support out-of-order assignment.
    assertArrayTypes(reversedArray, DexNewArray.class);
    // Algorithm does not support assigning to array elements multiple times.
    assertArrayTypes(reassignmentDoesNotOptimize, DexNewArray.class);
    // Algorithm does not support default-initialized array elements.
    assertArrayTypes(arrayWithHole, DexNewArray.class);
    // ArrayPuts not dominated by return statement.
    assertArrayTypes(phiFilledNewArrayBlocks, DexNewArray.class);
    assertArrayTypes(arrayWithDominatingPhiUsers, filledNewArrayInRelease);
    assertArrayTypes(arrayWithNonDominatingPhiUsers, DexNewArray.class);
    assertArrayTypes(phiWithNestedCatchHandler, DexNewArray.class);
    assertArrayTypes(phiWithExceptionalPhiUser, DexFilledNewArray.class, filledNewArrayInRelease);
    // Not safe to change catch handlers.
    assertArrayTypes(allocationWithoutCatchHandler, DexNewArray.class);
    // Not safe to change catch handlers.
    assertArrayTypes(allocationWithCatchHandler, DexNewArray.class);
    assertArrayTypes(catchHandlerWithFinally, DexNewArray.class);
    assertArrayTypes(simpleSynchronized1, DexFilledNewArray.class);
    assertArrayTypes(simpleSynchronized2, DexFilledNewArray.class);
    assertArrayTypes(simpleSynchronized3, DexNewArray.class);
    assertArrayTypes(simpleSynchronized4, DexNewArray.class);
    // Could be optimized if we had side-effect analysis of exceptional blocks.
    assertArrayTypes(catchHandlerWithoutSideeffects, DexNewArray.class);
    assertArrayTypes(arrayInsideCatchHandler, filledNewArrayInRelease);
    assertArrayTypes(multiUseArray, filledNewArrayInRelease);

    if (!canUseFilledNewArrayOfStringObjects(parameters)) {
      assertArrayTypes(stringArrays, DexNewArray.class);
    } else {
      assertArrayTypes(stringArrays, DexFilledNewArray.class);
    }
    if (!canUseFilledNewArrayOfNonStringObjects(parameters)) {
      assertArrayTypes(referenceArraysNoCasts, DexNewArray.class);
      assertArrayTypes(referenceArraysWithSubclasses, DexNewArray.class);
      assertArrayTypes(referenceArraysWithInterfaceImplementations, DexNewArray.class);
      assertArrayTypes(phiFilledNewArray, DexNewArray.class);
      assertArrayTypes(objectArraysFilledNewArrayRange, DexNewArray.class);
      assertArrayTypes(twoDimensionalArrays, DexNewArray.class);
      assertArrayTypes(assumedValues, DexNewArray.class);
    } else {
      if (parameters.canUseSubTypesInFilledNewArray()) {
        assertArrayTypes(referenceArraysNoCasts, DexFilledNewArray.class);
      } else {
        assertArrayTypes(referenceArraysNoCasts, DexNewArray.class, DexFilledNewArray.class);
      }
      if (isR8 && parameters.canUseSubTypesInFilledNewArray()) {
        assertArrayTypes(referenceArraysWithSubclasses, DexFilledNewArray.class);
        assertArrayTypes(referenceArraysWithInterfaceImplementations, DexFilledNewArray.class);
      } else {
        assertArrayTypes(referenceArraysWithSubclasses, DexNewArray.class);
        assertArrayTypes(referenceArraysWithInterfaceImplementations, DexNewArray.class);
      }

      assertArrayTypes(phiFilledNewArray, DexFilledNewArray.class);
      assertArrayTypes(
          objectArraysFilledNewArrayRange, DexFilledNewArrayRange.class, DexNewArray.class);

      if (parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.L)) {
        assertArrayTypes(twoDimensionalArrays, DexFilledNewArray.class);
      } else {
        // No need to assert this case. If it's wrong, Dalvik verify errors cause test failures.
      }

      assertArrayTypes(assumedValues, DexFilledNewArray.class);
    }
    // filled-new-array fails verification when types of parameters are not subclasses (aput-object
    // does not).
    assertArrayTypes(interfaceArrayWithRawObject, DexNewArray.class);

    // These could be optimized to use InvokeNewArray, but they seem like rare code patterns so we
    // haven't bothered.
    assertArrayTypes(arrayWithExtraInitialPuts, DexNewArray.class);
    assertArrayTypes(arrayWithCorrectCountButIncompleteCoverage, DexNewArray.class);
  }

  private static Predicate<InstructionSubject> isInstruction(List<Class<?>> allowlist) {
    return (ins) -> allowlist.contains(ins.asDexInstruction().getInstruction().getClass());
  }

  private static Predicate<InstructionSubject> isInstruction(Class<?> clazz) {
    return isInstruction(Arrays.asList(clazz));
  }

  private static void assertArrayTypes(MethodSubject method, Class<?>... allowedArrayInst) {
    assertTrue(method.isPresent());
    List<Class<?>> allowedClasses = Lists.newArrayList(allowedArrayInst);
    if (allowedClasses.contains(DexFilledNewArray.class)) {
      allowedClasses.add(DexFilledNewArrayRange.class);
    }
    List<Class<?>> disallowedClasses = Lists.newArrayList(DEX_ARRAY_INSTRUCTIONS);
    for (Class<?> allowedArr : allowedArrayInst) {
      disallowedClasses.remove(allowedArr);
    }
    assertTrue(method.streamInstructions().anyMatch(isInstruction(allowedClasses)));
    assertTrue(method.streamInstructions().noneMatch(isInstruction(disallowedClasses)));
  }

  private static void assertFilledArrayData(MethodSubject method) {
    assertTrue(method.isPresent());
    List<Class<?>> disallowedClasses = Lists.newArrayList(DEX_ARRAY_INSTRUCTIONS);
    disallowedClasses.remove(DexFillArrayData.class);
    disallowedClasses.remove(DexNewArray.class);

    assertTrue(method.streamInstructions().noneMatch(isInstruction(disallowedClasses)));
    assertTrue(method.streamInstructions().noneMatch(InstructionSubject::isArrayPut));
    long numNewArray = method.streamInstructions().filter(InstructionSubject::isNewArray).count();
    long numFillArray =
        method.streamInstructions().filter(isInstruction(DexFillArrayData.class)).count();
    assertEquals(numNewArray, numFillArray);
  }

  public static final class Main {
    static final String assumedNonNullField = null;
    static final String assumedNullField = null;

    public static void main(String[] args) {
      stringArrays();
      referenceArraysNoCasts();
      referenceArraysWithSubclasses();
      referenceArraysWithInterfaceImplementations();
      interfaceArrayWithRawObject();
      phiFilledNewArray();
      phiFilledNewArrayBlocks();
      arrayWithDominatingPhiUsers();
      arrayWithNonDominatingPhiUsers();
      phiWithNestedCatchHandler();
      phiWithExceptionalPhiUser();
      multiUseArray();
      arrayWithHole();
      reassignmentDoesNotOptimize();
      intsThatUseFilledNewArray();
      twoDimensionalArrays();
      objectArraysFilledNewArrayRange();
      arraysThatUseFilledData();
      arraysThatUseNewArrayEmpty(args.length);
      reversedArray();
      arrayWithCorrectCountButIncompleteCoverage();
      arrayWithExtraInitialPuts();
      arrayInsideCatchHandler();
      allocationWithCatchHandler();
      allocationWithoutCatchHandler();
      catchHandlerWithFinally();
      simpleSynchronized1();
      simpleSynchronized2();
      simpleSynchronized3();
      simpleSynchronized4();
      catchHandlerWithoutSideeffects();
      arrayIntoAnotherArray();
      assumedValues();
    }

    @NeverInline
    private static void stringArrays() {
      // Test exact class, no null.
      String[] stringArr = {"a"};
      System.out.println(Arrays.toString(stringArr));
    }

    @NeverInline
    private static void referenceArraysNoCasts() {
      // Tests that no type info is needed when array type is Object[].
      Object[] objectArr = {"a", 1, null};
      System.out.println(Arrays.toString(objectArr));
      // Test that interface arrays work when we have the exact interface already.
      Serializable[] interfaceArr = {getSerializable(1), null};
      System.out.println(Arrays.toString(interfaceArr));
    }

    @Keep
    private static Serializable getSerializable(Integer value) {
      return value;
    }

    @NeverInline
    private static void referenceArraysWithInterfaceImplementations() {
      Serializable[] interfaceArr = {1, null, 2};
      System.out.println(Arrays.toString(interfaceArr));
    }

    @NeverInline
    private static void referenceArraysWithSubclasses() {
      Number[] objArray = {1, null, 2};
      System.out.println(Arrays.toString(objArray));
    }

    @NeverInline
    private static void interfaceArrayWithRawObject() {
      // Interfaces can use filled-new-array only when we know types implement the interface.
      Serializable[] arr = new Serializable[1];
      // Transformed from `I get()` to `Object get()`.
      arr[0] = getObjectThatImplementsSerializable();
      System.out.println(Arrays.toString(arr));
    }

    @Keep
    private static /*Object*/ Serializable getObjectThatImplementsSerializable() {
      return 1;
    }

    @NeverInline
    private static void reversedArray() {
      int[] arr = new int[5];
      arr[4] = 4;
      arr[3] = 3;
      arr[2] = 2;
      arr[1] = 1;
      arr[0] = 0;
      System.out.println(Arrays.toString(arr));
    }

    @NeverInline
    private static void arrayWithCorrectCountButIncompleteCoverage() {
      int[] arr = new int[5];
      arr[0] = 0;
      arr[0] = 1;
      arr[0] = 2;
      arr[0] = 3;
      arr[0] = 4;
      System.out.println(Arrays.toString(arr));
    }

    @NeverInline
    private static void arrayWithExtraInitialPuts() {
      int[] arr = new int[5];
      arr[0] = 0;
      arr[0] = 1;
      arr[0] = 2;
      arr[0] = 3;
      arr[0] = 4;
      arr[1] = 1;
      arr[2] = 2;
      arr[3] = 3;
      arr[4] = 4;
      System.out.println(Arrays.toString(arr));
    }

    @NeverInline
    private static void arrayInsideCatchHandler() {
      try {
        int[] arr;
        // Use nested try to test hasEquivalentCatchHandlers() with multiple targets.
        try {
          // Test filled-new-array with a throwing instruction before the last array-put.
          arr = new int[1];
          System.currentTimeMillis();
          arr[0] = 9;
        } catch (RuntimeException r) {
          throw new RuntimeException(r);
        }
        System.out.println(Arrays.toString(arr));
      } catch (Throwable t) {
        throw new RuntimeException(t);
      }
    }

    @NeverInline
    private static void allocationWithCatchHandler() {
      Object[] arr;
      try {
        arr = new Object[1];
      } catch (NoClassDefFoundError | OutOfMemoryError t) {
        throw new RuntimeException(t);
      }

      // new-array-empty dominates this, but catch handlers are relevant
      arr[0] = "*";
      System.out.println(Arrays.toString(arr));
    }

    @NeverInline
    private static void allocationWithoutCatchHandler() {
      Object[] arr = new Object[1];
      try {
        // new-array-empty dominates this, but catch handlers are relevant.
        arr[0] = "*";
      } catch (NoClassDefFoundError | OutOfMemoryError t) {
        throw new RuntimeException(t);
      }
      System.out.println(Arrays.toString(arr));
    }

    @NeverInline
    private static void catchHandlerWithFinally() {
      Object[] arr = new Object[2];
      try {
        System.out.print("finally: ");
      } finally {
        // This will be duplicated into the throwing and non-throwing blocks.
        arr[0] = "1";
      }
      arr[1] = "2";
      System.out.println(Arrays.toString(arr));
    }

    @NeverInline
    private static void simpleSynchronized1() {
      // Should optimize since array is contained within a try block.
      synchronized (Main.class) {
        int[] arr = new int[] {1, 2};
        System.out.println(Arrays.toString(arr));
      }
    }

    @NeverInline
    private static synchronized void simpleSynchronized2() {
      // Should optimize since array is contained within a try block.
      try {
        try {
          int[] arr = new int[] {1, 2};
          System.out.println(Arrays.toString(arr));
        } catch (Throwable t) {
          throw new RuntimeException(t);
        } finally {
          System.currentTimeMillis();
        }
      } catch (Exception e) {
        // Ignore.
      }
    }

    @NeverInline
    private static void simpleSynchronized3() {
      // Does not optimize because allocation has different catch handlers.
      int[] arr = new int[2];
      synchronized (Main.class) {
        arr[0] = 1;
        arr[1] = 2;
      }
      System.out.println(Arrays.toString(arr));
    }

    @NeverInline
    private static void simpleSynchronized4() {
      // Does not optimize because allocation has different catch handlers.
      int[] arr;
      synchronized (Main.class) {
        arr = new int[2];
      }
      arr[0] = 1;
      arr[1] = 2;
      System.out.println(Arrays.toString(arr));
    }

    @NeverInline
    private static void catchHandlerWithoutSideeffects() {
      // If we added logic to show that catch handlers exit without side-effects, we could optimize
      // this case.z
      int[] arr1;
      try {
        arr1 = new int[3];
      } catch (Throwable t) {
        throw new RuntimeException("1");
      }
      try {
        arr1[0] = 0;
      } catch (Throwable t) {
        throw new RuntimeException("2");
      }
      arr1[1] = 1;
      try {
        arr1[2] = 2;
      } catch (Throwable t) {
        throw new RuntimeException("3");
      }
      System.out.println(Arrays.toString(arr1));
    }

    @NeverInline
    private static void arrayIntoAnotherArray() {
      // Tests that our computeValues() method does not assume all array-put-object users have
      // the array as the target.
      int[] intArr = new int[1];
      Object[] objArr = new Object[2];
      objArr[0] = intArr;
      System.out.println(Arrays.toString((int[]) objArr[0]));
    }

    @NeverInline
    private static void assumedValues() {
      Object[] arr = {assumedNonNullField, assumedNullField};
      System.out.println(Arrays.toString(arr));
    }

    @NeverInline
    private static void phiFilledNewArray() {
      // The presence of ? should not affect use of filled-new-array.
      Integer[] phiArray = {1, System.nanoTime() > 0 ? 2 : 3, 3};
      System.out.println(Arrays.toString(phiArray));
    }

    @NeverInline
    private static void phiFilledNewArrayBlocks() {
      int[] phiArray = new int[3];
      if (System.currentTimeMillis() > 0) {
        phiArray[0] = 2;
        phiArray[1] = 2;
        phiArray[2] = 2;
      }
      System.out.println(Arrays.toString(phiArray));
    }

    @NeverInline
    private static void arrayWithDominatingPhiUsers() {
      int[] phiArray = null;
      try {
        phiArray = new int[2];
        phiArray[0] = 6;
        phiArray[1] = 7;
      } catch (Throwable t) {
        System.out.println("Not reached");
      }
      System.out.println(Arrays.toString(phiArray));
    }

    @NeverInline
    private static void arrayWithNonDominatingPhiUsers() {
      int[] phiArray = null;
      try {
        phiArray = new int[1];
        // If currentTimeMillis() throws, phiArray will have value of [0].
        phiArray[0] = System.currentTimeMillis() > 0 ? 7 : 0;
      } catch (Throwable t) {
        System.out.println("Not reached");
      }
      System.out.println(Arrays.toString(phiArray));
    }

    @NeverInline
    private static void phiWithNestedCatchHandler() {
      int[] phiArray = null;
      try {
        phiArray = new int[2];
        // If currentTimeMillis() throws, phiArray will have value of [0, 0].
        try {
          System.currentTimeMillis();
        } catch (RuntimeException r) {
          throw new RuntimeException(r);
        }
        phiArray[0] = 3;
        phiArray[1] = 4;
      } catch (Throwable t) {
        System.out.println("Not reached");
      }
      System.out.println(Arrays.toString(phiArray));
    }

    @NeverInline
    private static void phiWithExceptionalPhiUser() {
      int[] arr = null;
      try {
        // Both of these should optimize, but care must be taken to ensure the phiUsers are properly
        // dominated post-optimization.
        if (System.currentTimeMillis() > 0) {
          arr = new int[1];
          arr[0] = 99;
        } else {
          arr = new int[] {1, 2};
        }
      } catch (RuntimeException e) {
        // fall through
      }
      System.out.println(Arrays.toString(arr));
    }

    @NeverInline
    private static void multiUseArray() {
      int[] arr = new int[2];
      arr[0] = 0;
      arr[1] = System.nanoTime() > 0 ? 1 : 2;
      System.out.println(Arrays.toString(arr));
      System.out.println(Arrays.toString(arr));
      // Usage in a different basic block.
      if (System.nanoTime() > 0) {
        System.nanoTime();
      }
      System.out.println(Arrays.toString(arr));
    }

    @NeverInline
    private static void arrayWithHole() {
      int[] arr = new int[2];
      arr[1] = 1;
      System.out.println(Arrays.toString(arr));
    }

    @NeverInline
    private static void reassignmentDoesNotOptimize() {
      // Reassignment in same block, and of last index.
      Integer[] arr = new Integer[2];
      arr[0] = 0;
      arr[1] = 2;
      arr[1] = 1;
      System.out.println(Arrays.toString(arr));

      // Reassignment across blocks, of non-last index.
      arr = new Integer[2];
      arr[0] = 3;
      arr[1] = System.nanoTime() > 0 ? 1 : 2;
      arr[0] = 0;
      System.out.println(Arrays.toString(arr));
    }

    @NeverInline
    private static void intsThatUseFilledNewArray() {
      // Up to 5 ints uses filled-new-array rather than filled-array-data.
      int[] intArr = {1, 2, 3, 4, 5};
      System.out.println(Arrays.toString(intArr));
    }

    @NeverInline
    private static void twoDimensionalArrays() {
      Integer[][] twoDimensions = {new Integer[] {1}, null};
      System.out.println(Arrays.toString(Arrays.asList(twoDimensions).get(0)));
    }

    @NeverInline
    private static void objectArraysFilledNewArrayRange() {
      // 6 or more elements use /range variant.
      Object[] objectArr = {"a", 1, null, "d", "e", "f"};
      System.out.println(Arrays.toString(objectArr));
      Serializable[] interfaceArr = {
        getSerializable(1), null, getSerializable(3), null, null, getSerializable(6)
      };
      System.out.println(Arrays.toString(interfaceArr));
    }

    @NeverInline
    private static void arraysThatUseFilledData() {
      // For int[], <= 5 elements should use NewArrayFilledData (otherwise NewFilledArray is used).
      int[] intArr = {1, 2, 3, 4, 5, 6};
      // For other primitives, > 1 element leads to fill-array-data.
      System.out.println(Arrays.toString(intArr));
      boolean[] boolArr = {true, false};
      System.out.println(Arrays.toString(boolArr));
      byte[] byteArr = {1, 2};
      System.out.println(Arrays.toString(byteArr));
      char[] charArr = {'1', '2'};
      System.out.println(Arrays.toString(charArr));
      long[] longArr = {1, 2};
      System.out.println(Arrays.toString(longArr));
      float[] floatArr = {1, 2};
      System.out.println(Arrays.toString(floatArr));
      double[] doubleArr = {1, 2};
      System.out.println(Arrays.toString(doubleArr));
    }

    @NeverInline
    private static void arraysThatUseNewArrayEmpty(int trickyZero) {
      // int/object of size zero should not use filled-new-array.
      int[] intArr = {};
      System.out.println(Arrays.toString(intArr));
      String[] strArr = {};
      System.out.println(Arrays.toString(strArr));

      // Other primitives with size <= 1 should not use filled-array-data.
      boolean[] boolArr = {true};
      System.out.println(Arrays.toString(boolArr));
      byte[] byteArr = {1};
      System.out.println(Arrays.toString(byteArr));
      char[] charArr = {'1'};
      System.out.println(Arrays.toString(charArr));
      long[] longArr = {1};
      System.out.println(Arrays.toString(longArr));
      float[] floatArr = {1};
      System.out.println(Arrays.toString(floatArr));
      double[] doubleArr = {1};
      System.out.println(Arrays.toString(doubleArr));

      // Array used before all members are set.
      int[] readArray = new int[2];
      readArray[0] = (int) (System.currentTimeMillis() / System.nanoTime());
      readArray[1] = readArray[0] + 1;
      System.out.println(Arrays.toString(readArray));

      // Array does not have all elements set (we could make this work, but likely this is rare).
      Integer[] partialArray = new Integer[2];
      partialArray[0] = 1;
      System.out.println(Arrays.toString(partialArray));

      // Non-constant array size.
      Object[] nonConstSize = new Object[trickyZero + 1];
      nonConstSize[0] = "a";
      System.out.println(Arrays.toString(nonConstSize));

      // Non-constant index.
      Object[] nonConstIndex = new Object[2];
      nonConstIndex[trickyZero] = 0;
      nonConstIndex[trickyZero + 1] = 1;
      System.out.println(Arrays.toString(nonConstIndex));

      // Exceeds our (arbitrary) size limit for /range.
      String[] bigArr = new String[201];
      bigArr[0] = "0";
      bigArr[1] = "1";
      bigArr[2] = "2";
      bigArr[3] = "3";
      bigArr[4] = "4";
      bigArr[5] = "5";
      bigArr[6] = "6";
      bigArr[7] = "7";
      bigArr[8] = "8";
      bigArr[9] = "9";
      bigArr[10] = "10";
      bigArr[11] = "11";
      bigArr[12] = "12";
      bigArr[13] = "13";
      bigArr[14] = "14";
      bigArr[15] = "15";
      bigArr[16] = "16";
      bigArr[17] = "17";
      bigArr[18] = "18";
      bigArr[19] = "19";
      bigArr[20] = "20";
      bigArr[21] = "21";
      bigArr[22] = "22";
      bigArr[23] = "23";
      bigArr[24] = "24";
      bigArr[25] = "25";
      bigArr[26] = "26";
      bigArr[27] = "27";
      bigArr[28] = "28";
      bigArr[29] = "29";
      bigArr[30] = "30";
      bigArr[31] = "31";
      bigArr[32] = "32";
      bigArr[33] = "33";
      bigArr[34] = "34";
      bigArr[35] = "35";
      bigArr[36] = "36";
      bigArr[37] = "37";
      bigArr[38] = "38";
      bigArr[39] = "39";
      bigArr[40] = "40";
      bigArr[41] = "41";
      bigArr[42] = "42";
      bigArr[43] = "43";
      bigArr[44] = "44";
      bigArr[45] = "45";
      bigArr[46] = "46";
      bigArr[47] = "47";
      bigArr[48] = "48";
      bigArr[49] = "49";
      bigArr[50] = "50";
      bigArr[51] = "51";
      bigArr[52] = "52";
      bigArr[53] = "53";
      bigArr[54] = "54";
      bigArr[55] = "55";
      bigArr[56] = "56";
      bigArr[57] = "57";
      bigArr[58] = "58";
      bigArr[59] = "59";
      bigArr[60] = "60";
      bigArr[61] = "61";
      bigArr[62] = "62";
      bigArr[63] = "63";
      bigArr[64] = "64";
      bigArr[65] = "65";
      bigArr[66] = "66";
      bigArr[67] = "67";
      bigArr[68] = "68";
      bigArr[69] = "69";
      bigArr[70] = "70";
      bigArr[71] = "71";
      bigArr[72] = "72";
      bigArr[73] = "73";
      bigArr[74] = "74";
      bigArr[75] = "75";
      bigArr[76] = "76";
      bigArr[77] = "77";
      bigArr[78] = "78";
      bigArr[79] = "79";
      bigArr[80] = "80";
      bigArr[81] = "81";
      bigArr[82] = "82";
      bigArr[83] = "83";
      bigArr[84] = "84";
      bigArr[85] = "85";
      bigArr[86] = "86";
      bigArr[87] = "87";
      bigArr[88] = "88";
      bigArr[89] = "89";
      bigArr[90] = "90";
      bigArr[91] = "91";
      bigArr[92] = "92";
      bigArr[93] = "93";
      bigArr[94] = "94";
      bigArr[95] = "95";
      bigArr[96] = "96";
      bigArr[97] = "97";
      bigArr[98] = "98";
      bigArr[99] = "99";
      bigArr[100] = "100";
      bigArr[101] = "101";
      bigArr[102] = "102";
      bigArr[103] = "103";
      bigArr[104] = "104";
      bigArr[105] = "105";
      bigArr[106] = "106";
      bigArr[107] = "107";
      bigArr[108] = "108";
      bigArr[109] = "109";
      bigArr[110] = "110";
      bigArr[111] = "111";
      bigArr[112] = "112";
      bigArr[113] = "113";
      bigArr[114] = "114";
      bigArr[115] = "115";
      bigArr[116] = "116";
      bigArr[117] = "117";
      bigArr[118] = "118";
      bigArr[119] = "119";
      bigArr[120] = "120";
      bigArr[121] = "121";
      bigArr[122] = "122";
      bigArr[123] = "123";
      bigArr[124] = "124";
      bigArr[125] = "125";
      bigArr[126] = "126";
      bigArr[127] = "127";
      bigArr[128] = "128";
      bigArr[129] = "129";
      bigArr[130] = "130";
      bigArr[131] = "131";
      bigArr[132] = "132";
      bigArr[133] = "133";
      bigArr[134] = "134";
      bigArr[135] = "135";
      bigArr[136] = "136";
      bigArr[137] = "137";
      bigArr[138] = "138";
      bigArr[139] = "139";
      bigArr[140] = "140";
      bigArr[141] = "141";
      bigArr[142] = "142";
      bigArr[143] = "143";
      bigArr[144] = "144";
      bigArr[145] = "145";
      bigArr[146] = "146";
      bigArr[147] = "147";
      bigArr[148] = "148";
      bigArr[149] = "149";
      bigArr[150] = "150";
      bigArr[151] = "151";
      bigArr[152] = "152";
      bigArr[153] = "153";
      bigArr[154] = "154";
      bigArr[155] = "155";
      bigArr[156] = "156";
      bigArr[157] = "157";
      bigArr[158] = "158";
      bigArr[159] = "159";
      bigArr[160] = "160";
      bigArr[161] = "161";
      bigArr[162] = "162";
      bigArr[163] = "163";
      bigArr[164] = "164";
      bigArr[165] = "165";
      bigArr[166] = "166";
      bigArr[167] = "167";
      bigArr[168] = "168";
      bigArr[169] = "169";
      bigArr[170] = "170";
      bigArr[171] = "171";
      bigArr[172] = "172";
      bigArr[173] = "173";
      bigArr[174] = "174";
      bigArr[175] = "175";
      bigArr[176] = "176";
      bigArr[177] = "177";
      bigArr[178] = "178";
      bigArr[179] = "179";
      bigArr[180] = "180";
      bigArr[181] = "181";
      bigArr[182] = "182";
      bigArr[183] = "183";
      bigArr[184] = "184";
      bigArr[185] = "185";
      bigArr[186] = "186";
      bigArr[187] = "187";
      bigArr[188] = "188";
      bigArr[189] = "189";
      bigArr[190] = "190";
      bigArr[191] = "191";
      bigArr[192] = "192";
      bigArr[193] = "193";
      bigArr[194] = "194";
      bigArr[195] = "195";
      bigArr[196] = "196";
      bigArr[197] = "197";
      bigArr[198] = "198";
      bigArr[199] = "199";
      bigArr[200] = "200";
      System.out.println(Arrays.asList(bigArr).get(200));
    }
  }
}
