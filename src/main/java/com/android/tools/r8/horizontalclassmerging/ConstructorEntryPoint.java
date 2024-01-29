// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging;

import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.InvokeType;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.ir.conversion.IRBuilder;
import com.android.tools.r8.ir.synthetic.SyntheticSourceCode;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.IntBox;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap.Entry;
import it.unimi.dsi.fastutil.ints.Int2ReferenceSortedMap;
import java.util.ArrayList;
import java.util.List;

/**
 * Generate code of the form: <code>
 *   MyClass(int constructorId, [args]) {
 *     switch (constructorId) {
 *       case 1:
 *         this.Constructor$B([args]);
 *         return;
 *       ...
 *       default:
 *         this.Constructor$A([args]);
 *         return;
 *     }
 *   }
 * </code>
 */
public class ConstructorEntryPoint extends SyntheticSourceCode {
  private final DexField classIdField;
  private final int extraNulls;
  private final Int2ReferenceSortedMap<DexMethod> typeConstructors;

  public ConstructorEntryPoint(
      Int2ReferenceSortedMap<DexMethod> typeConstructors,
      DexMethod newConstructor,
      DexField classIdField,
      int extraNulls,
      Position position) {
    super(newConstructor.holder, newConstructor, position);

    this.typeConstructors = typeConstructors;
    this.classIdField = classIdField;
    this.extraNulls = extraNulls;
  }

  private boolean hasClassIdField() {
    return classIdField != null;
  }

  void addConstructorInvoke(DexMethod typeConstructor) {
    add(
        builder -> {
          int originalNumberOfNonReceiverArguments =
              builder.hasArgumentValues()
                  ? (builder.getArgumentValues().size()
                      - BooleanUtils.intValue(typeConstructors.size() > 1)
                      - extraNulls)
                  : 0;
          int newNumberOfNonReceiverArguments = typeConstructor.getArity();
          List<Value> arguments = new ArrayList<>(newNumberOfNonReceiverArguments + 1);
          arguments.add(builder.getReceiverValue());
          if (originalNumberOfNonReceiverArguments >= newNumberOfNonReceiverArguments) {
            for (int i = 0; i < newNumberOfNonReceiverArguments; i++) {
              arguments.add(builder.getArgumentValues().get(i));
            }
          } else {
            // Exclude the last argument if it is the synthetic class id parameter, since the
            // original constructor we are calling does not have it.
            for (int i = 0; i < originalNumberOfNonReceiverArguments; i++) {
              arguments.add(builder.getArgumentValues().get(i));
            }
            int extraRegister = nextRegister(ValueType.INT);
            ConstNumber constNumber = builder.addIntConst(extraRegister, 0);
            while (arguments.size() <= newNumberOfNonReceiverArguments) {
              assert ValueType.fromDexType(
                      typeConstructor.getArgumentTypeForNonStaticMethod(arguments.size()))
                  == ValueType.INT;
              arguments.add(constNumber.outValue());
            }
          }
          assert arguments.size() == typeConstructor.getNumberOfArgumentsForNonStaticMethod();
          builder.addInvoke(
              InvokeType.DIRECT, typeConstructor, typeConstructor.getProto(), arguments, false);
        });
  }

  /** Assign the given register to the class id field. */
  void addRegisterClassIdAssignment(int idRegister) {
    assert hasClassIdField();
    add(builder -> builder.addInstancePut(idRegister, getReceiverRegister(), classIdField));
  }

  /** Assign the given constant integer value to the class id field. */
  void addConstantRegisterClassIdAssignment(int classId) {
    assert hasClassIdField();
    int idRegister = nextRegister(ValueType.INT);
    add(builder -> builder.addIntConst(idRegister, classId));
    addRegisterClassIdAssignment(idRegister);
  }

  protected void prepareMultiConstructorInstructions() {
    int typeConstructorCount = typeConstructors.size();
    // The class id register is always the first synthetic argument.
    int idRegister = getParamRegister(method.getArity() - 1 - extraNulls);
    if (hasClassIdField()) {
      addRegisterClassIdAssignment(idRegister);
    }

    int[] keys = new int[typeConstructorCount - 1];
    int[] offsets = new int[typeConstructorCount - 1];
    IntBox fallthrough = new IntBox();
    int switchIndex = lastInstructionIndex();
    add(
        builder -> builder.addSwitch(idRegister, keys, fallthrough.get(), offsets),
        builder -> endsSwitch(builder, switchIndex, fallthrough.get(), offsets));

    int index = 0;
    for (Entry<DexMethod> entry : typeConstructors.int2ReferenceEntrySet()) {
      int classId = entry.getIntKey();
      DexMethod typeConstructor = entry.getValue();

      if (index == 0) {
        // The first constructor is the fallthrough case.
        fallthrough.set(nextInstructionIndex());
      } else {
        // All subsequent constructors are matched on a specific case.
        keys[index - 1] = classId;
        offsets[index - 1] = nextInstructionIndex();
      }

      addConstructorInvoke(typeConstructor);
      add(IRBuilder::addReturn, endsBlock);

      index++;
    }
  }

  protected void prepareSingleConstructorInstructions() {
    Entry<DexMethod> entry = typeConstructors.int2ReferenceEntrySet().first();
    if (hasClassIdField()) {
      addConstantRegisterClassIdAssignment(entry.getIntKey());
    }
    addConstructorInvoke(entry.getValue());
    add(IRBuilder::addReturn, endsBlock);
  }

  @Override
  protected void prepareInstructions() {
    if (typeConstructors.size() > 1) {
      prepareMultiConstructorInstructions();
    } else {
      prepareSingleConstructorInstructions();
    }
  }
}
