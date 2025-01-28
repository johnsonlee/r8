// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.ArrayPut;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InvokeVirtual;
import com.android.tools.r8.ir.code.NewArrayEmpty;
import com.android.tools.r8.ir.code.NewArrayFilled;
import com.android.tools.r8.ir.code.NewInstance;
import com.android.tools.r8.ir.code.Value;
import java.util.Arrays;
import java.util.List;

public class ValueUtils {
  // We allocate an array of this size, so guard against it getting too big.
  private static int MAX_ARRAY_SIZE = 100000;

  @SuppressWarnings("ReferenceEquality")
  public static boolean isStringBuilder(Value value, DexItemFactory dexItemFactory) {
    TypeElement type = value.getType();
    return type.isClassType()
        && type.asClassType().getClassType() == dexItemFactory.stringBuilderType;
  }

  @SuppressWarnings("ReferenceEquality")
  public static boolean isNonNullStringBuilder(Value value, DexItemFactory dexItemFactory) {
    while (true) {
      if (value.isPhi()) {
        return false;
      }

      Instruction definition = value.getDefinition();
      if (definition.isNewInstance()) {
        NewInstance newInstance = definition.asNewInstance();
        return newInstance.clazz == dexItemFactory.stringBuilderType;
      }

      if (definition.isInvokeVirtual()) {
        InvokeVirtual invoke = definition.asInvokeVirtual();
        if (dexItemFactory.stringBuilderMethods.isAppendMethod(invoke.getInvokedMethod())) {
          value = invoke.getReceiver();
          continue;
        }
      }

      // Unhandled definition.
      return false;
    }
  }

  public static final class ArrayValues {
    private List<Value> elementValues;
    private ArrayPut[] arrayPutsByIndex;
    private final Value arrayValue;

    private ArrayValues(Value arrayValue, List<Value> elementValues) {
      this.arrayValue = arrayValue;
      this.elementValues = elementValues;
    }

    private ArrayValues(Value arrayValue, ArrayPut[] arrayPutsByIndex) {
      this.arrayValue = arrayValue;
      this.arrayPutsByIndex = arrayPutsByIndex;
    }

    /** May contain null entries when array has null entries. */
    public List<Value> getElementValues() {
      if (elementValues == null) {
        ArrayPut[] puts = arrayPutsByIndex;
        Value[] elementValuesArr = new Value[puts.length];
        for (int i = 0; i < puts.length; ++i) {
          ArrayPut arrayPut = puts[i];
          elementValuesArr[i] = arrayPut == null ? null : arrayPut.value();
        }
        elementValues = Arrays.asList(elementValuesArr);
      }
      return elementValues;
    }

    public int size() {
      return elementValues != null ? elementValues.size() : arrayPutsByIndex.length;
    }

    public boolean containsHoles() {
      for (ArrayPut arrayPut : arrayPutsByIndex) {
        if (arrayPut == null) {
          return true;
        }
      }
      return false;
    }

    public ArrayPut[] getArrayPutsByIndex() {
      assert arrayPutsByIndex != null;
      return arrayPutsByIndex;
    }

    public Value getArrayValue() {
      return arrayValue;
    }

    public Instruction getDefinition() {
      return arrayValue.definition;
    }
  }

  /**
   * Attempts to determine all values for the given array. This will work only when:
   *
   * <pre>
   * 1) The Array has a single users (other than array-puts)
   *   * This constraint is to ensure other users do not modify the array.
   *   * When users are in different blocks, their order is hard to know.
   * 2) The array size is a constant and non-negative.
   * 3) All array-put instructions have constant and unique indices.
   * 4) The array-put instructions are guaranteed to be executed before singleUser.
   * </pre>
   *
   * @param arrayValue The Value for the array.
   * @param singleUser The only non-array-put user, or null to auto-detect.
   * @return The computed array values, or null if they could not be determined.
   */
  public static ArrayValues computeSingleUseArrayValues(Value arrayValue, Instruction singleUser) {
    TypeElement arrayType = arrayValue.getType();
    if (!arrayType.isArrayType() || arrayValue.hasDebugUsers() || arrayValue.isPhi()) {
      return null;
    }

    Instruction definition = arrayValue.definition;
    NewArrayEmpty newArrayEmpty = definition.asNewArrayEmpty();
    NewArrayFilled newArrayFilled = definition.asNewArrayFilled();
    if (newArrayFilled != null) {
      // It would be possible to have new-array-filled followed by aput-array, but that sequence of
      // instructions does not commonly occur, so we don't support it here.
      if (!arrayValue.hasSingleUniqueUser() || arrayValue.hasPhiUsers()) {
        return null;
      }
      return new ArrayValues(arrayValue, newArrayFilled.inValues());
    } else if (newArrayEmpty == null) {
      return null;
    }

    int arraySize = newArrayEmpty.sizeIfConst();
    if (arraySize < 0 || arraySize > MAX_ARRAY_SIZE) {
      // Array is non-const size.
      return null;
    }

    return computeArrayValuesInternal(newArrayEmpty, arraySize, singleUser, false);
  }

  /**
   * Determines the values for the given array at the point the last element is assigned.
   *
   * <pre>
   * Returns null under the following conditions:
   *  * The array has a non-const, negative, or abnormally large size.
   *  * An array-put with non-constant index exists.
   *  * An array-put with an out-of-bounds index exists.
   *  * An array-put for the last index is not found.
   *  * An array-put is found after the last-index array-put.
   *  * An array-put is found where the array and value are the same: arr[index] = arr;
   *  * There are multiple array-put instructions for the same index.
   *  * An array-put exists that does not dominate the array-put of the highest index.
   * </pre>
   */
  public static ArrayValues computeInitialArrayValues(NewArrayEmpty newArrayEmpty) {
    int arraySize = newArrayEmpty.sizeIfConst();
    if (arraySize < 0 || arraySize > MAX_ARRAY_SIZE) {
      // Array is non-const size.
      return null;
    }

    Value arrayValue = newArrayEmpty.outValue();
    // Find array-put for the last element, as well as blocks for other array users.
    ArrayPut lastArrayPut = null;
    int lastIndex = arraySize - 1;
    for (Instruction user : arrayValue.uniqueUsers()) {
      ArrayPut arrayPut = user.asArrayPut();
      if (arrayPut != null && arrayPut.array() == arrayValue) {
        int index = arrayPut.indexOrDefault(-1);
        if (index == lastIndex) {
          lastArrayPut = arrayPut;
          break;
        }
      }
    }
    if (lastArrayPut == null) {
      return null;
    }

    // Find all array-puts up until the last one.
    // Also checks that no array-puts appear after the last one.
    ArrayValues ret = computeArrayValuesInternal(newArrayEmpty, arraySize, lastArrayPut, true);
    if (ret == null) {
      return null;
    }
    // Since the last array-put is used as firstUser, it will not already be in arrayPutsByIndex.
    if (ret.arrayPutsByIndex[lastIndex] != null) {
      return null;
    }
    ret.arrayPutsByIndex[lastIndex] = lastArrayPut;
    return ret;
  }

  private static ArrayValues computeArrayValuesInternal(
      NewArrayEmpty newArrayEmpty, int arraySize, Instruction firstUser, boolean allowOtherUsers) {
    ArrayPut[] arrayPutsByIndex = new ArrayPut[arraySize];
    Value arrayValue = newArrayEmpty.outValue();
    BasicBlock usageBlock = firstUser.getBlock();

    // Collect array-puts from non-usage blocks, and (optionally) check for multiple users.
    for (Instruction user : arrayValue.uniqueUsers()) {
      ArrayPut arrayPut = user.asArrayPut();
      if (arrayPut == null || arrayPut.array() != arrayValue) {
        if (user == firstUser) {
          continue;
        }
        // Found a second non-array-put user.
        if (allowOtherUsers) {
          continue;
        }
        return null;
      } else if (arrayPut.value() == arrayValue) {
        // An array that contains itself is uncommon and hard to reason about.
        // e.g.: arr[0] = arr;
        return null;
      }
      // Process same-block instructions later.
      if (user.getBlock() == usageBlock) {
        continue;
      }
      int index = arrayPut.indexIfConstAndInBounds(arraySize);
      // We do not know what order blocks are in, so do not allow re-assignment.
      if (index < 0 || arrayPutsByIndex[index] != null) {
        return null;
      }
      arrayPutsByIndex[index] = arrayPut;
    }

    // Ensure that all paths from new-array-empty's block to |usage|'s block contain all array-put
    // instructions.
    DominatorChecker dominatorChecker =
        DominatorChecker.create(newArrayEmpty.getBlock(), usageBlock);
    // Visit in reverse order because array-puts generally appear in order, and DominatorChecker's
    // cache is more effective when visiting in reverse order.
    for (int i = arraySize - 1; i >= 0; --i) {
      ArrayPut arrayPut = arrayPutsByIndex[i];
      if (arrayPut != null && !dominatorChecker.check(arrayPut.getBlock())) {
        return null;
      }
    }

    // Collect array-puts from the usage block, and ensure no array-puts come after the first user.
    boolean seenFirstUser = false;
    for (Instruction inst : usageBlock.getInstructions()) {
      if (inst == firstUser) {
        seenFirstUser = true;
        continue;
      }
      ArrayPut arrayPut = inst.asArrayPut();
      if (arrayPut == null || arrayPut.array() != arrayValue) {
        continue;
      }
      if (seenFirstUser) {
        // Found an array-put after the array was used. This is too uncommon of a thing to support.
        return null;
      }
      int index = arrayPut.indexIfConstAndInBounds(arraySize);
      if (index < 0) {
        return null;
      }
      // Do not allow re-assignment so that we can use arrayPutsByIndex to find all array-put
      // instructions.
      if (arrayPutsByIndex[index] != null) {
        return null;
      }
      arrayPutsByIndex[index] = arrayPut;
    }

    return new ArrayValues(arrayValue, arrayPutsByIndex);
  }
}
