// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion.passes;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItem;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.type.ArrayTypeElement;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.ArrayPut;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.BasicBlockInstructionListIterator;
import com.android.tools.r8.ir.code.BasicBlockIterator;
import com.android.tools.r8.ir.code.ConstClass;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.ConstString;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.MemberType;
import com.android.tools.r8.ir.code.NewArrayEmpty;
import com.android.tools.r8.ir.code.NewArrayFilled;
import com.android.tools.r8.ir.code.NewArrayFilledData;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.StaticGet;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.ir.conversion.passes.result.CodeRewriterResult;
import com.android.tools.r8.utils.BooleanBox;
import com.android.tools.r8.utils.InternalOptions.RewriteArrayOptions;
import com.android.tools.r8.utils.SetUtils;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FilledNewArrayRewriter extends CodeRewriterPass<AppInfo> {

  private static final Set<Instruction> NOTHING = ImmutableSet.of();

  private final RewriteArrayOptions rewriteArrayOptions;

  public FilledNewArrayRewriter(AppView<?> appView) {
    super(appView);
    this.rewriteArrayOptions = options.rewriteArrayOptions();
  }

  @Override
  protected String getRewriterId() {
    return "FilledNewArrayRemover";
  }

  @Override
  protected CodeRewriterResult rewriteCode(IRCode code) {
    return new FilledNewArrayCodeRewriter().rewriteCode(code);
  }

  @Override
  protected boolean shouldRewriteCode(IRCode code, MethodProcessor methodProcessor) {
    return code.metadata().mayHaveNewArrayFilled();
  }

  private class FilledNewArrayCodeRewriter {

    private boolean mayHaveRedundantBlocks = false;
    private Set<Instruction> toRemove = NOTHING;

    public CodeRewriterResult rewriteCode(IRCode code) {
      assert !mayHaveRedundantBlocks;
      assert toRemove == NOTHING;
      CodeRewriterResult result = noChange();
      BooleanBox pendingRewrites = new BooleanBox(true);
      while (pendingRewrites.get()) {
        pendingRewrites.set(false);
        BasicBlockIterator blockIterator = code.listIterator();
        while (blockIterator.hasNext()) {
          BasicBlock block = blockIterator.next();
          BasicBlockInstructionListIterator instructionIterator = block.listIterator(code);
          while (instructionIterator.hasNext()) {
            Instruction instruction = instructionIterator.next();
            if (instruction.isNewArrayFilled()) {
              result =
                  processInstruction(
                      code,
                      blockIterator,
                      instructionIterator,
                      instruction.asNewArrayFilled(),
                      result,
                      pendingRewrites);
            }
          }
        }
        if (!toRemove.isEmpty()) {
          Set<Instruction> additionalToRemove = SetUtils.newIdentityHashSet();
          InstructionListIterator it = code.instructionListIterator();
          while (it.hasNext()) {
            Instruction next = it.next();
            if (toRemove.contains(next)) {
              // Also remove constants used by the removed NewArrayFilled.
              if (next.isNewArrayFilled()) {
                next.inValues()
                    .forEach(
                        value -> {
                          if (value.hasSingleUniqueUser() && !value.hasPhiUsers()) {
                            additionalToRemove.add(value.getDefinition());
                          }
                        });
              }
              it.remove();
              mayHaveRedundantBlocks = true;
            }
          }
          if (!additionalToRemove.isEmpty()) {
            InstructionListIterator itAdditional = code.instructionListIterator();
            while (itAdditional.hasNext()) {
              Instruction next = itAdditional.next();
              if (additionalToRemove.contains(next)) {
                itAdditional.remove();
                mayHaveRedundantBlocks = true;
              }
            }
          }
        }
        toRemove = NOTHING;
        if (mayHaveRedundantBlocks) {
          code.removeRedundantBlocks();
        }
      }
      return result;
    }

    private boolean isNewArrayFilledOfConstants(NewArrayFilled newArrayFilled) {
      for (Value inValue : newArrayFilled.inValues()) {
        if (!inValue.isConstNumber() && !inValue.isConstString() && !inValue.isConstClass()) {
          return false;
        }
      }
      return true;
    }

    private boolean isDefinedByNewArrayFilledOfConstants(Value value) {
      if (!value.isDefinedByInstructionSatisfying(Instruction::isNewArrayFilled)) {
        return false;
      }
      return isNewArrayFilledOfConstants(value.definition.asNewArrayFilled());
    }

    public NewArrayFilled copyConstantsNewArrayFilled(IRCode code, NewArrayFilled original) {
      assert isNewArrayFilledOfConstants(original);
      Value newValue = code.createValue(original.getOutType(), original.getLocalInfo());
      List<Value> newArguments = new ArrayList<>(original.inValues().size());
      for (Value value : original.inValues()) {
        if (value.isConstNumber()) {
          newArguments.add(
              ConstNumber.copyOf(code, value.getDefinition().asConstNumber()).outValue());
        } else if (value.isConstString()) {
          newArguments.add(
              ConstString.copyOf(code, value.getDefinition().asConstString()).outValue());
        } else if (value.isConstClass()) {
          newArguments.add(
              ConstClass.copyOf(code, value.getDefinition().asConstClass()).outValue());
        } else {
          assert false;
        }
      }
      return new NewArrayFilled(original.getArrayType(), newValue, newArguments);
    }

    private CodeRewriterResult processInstruction(
        IRCode code,
        BasicBlockIterator blockIterator,
        BasicBlockInstructionListIterator instructionIterator,
        NewArrayFilled newArrayFilled,
        CodeRewriterResult result,
        BooleanBox pendingRewrites) {
      if (canUseNewArrayFilled(newArrayFilled)) {
        return result;
      }
      if (newArrayFilled.hasUnusedOutValue()) {
        instructionIterator.removeOrReplaceByDebugLocalRead();
      } else if (canUseNewArrayFilledData(newArrayFilled)) {
        rewriteToNewArrayFilledData(code, blockIterator, instructionIterator, newArrayFilled);
      } else if (newArrayFilled.outValue().hasSingleUniqueUser()
          && newArrayFilled.outValue().singleUniqueUser().isNewArrayFilled()
          && isNewArrayFilledOfConstants(newArrayFilled)) {
        if (canUseNewArrayFilled(newArrayFilled.outValue().singleUniqueUser().asNewArrayFilled())) {
          // The NewArrayFilled user is supported, so rewrite here.
          rewriteToArrayPuts(code, blockIterator, instructionIterator, newArrayFilled);
        } else {
          // The NewArrayFilled user is not supported so leave for rewriting after that.
          //
          // The effect of this is that when the user of this NewArrayFilled is rewritten to puts,
          // the NewArrayFilled construction is copied to the use site
          //
          //  Input:
          //
          //   v0 <-  Const X
          //   v1 <-  NewArrayFilled(v0)
          //   v2 <-  Const Y
          //   v3 <-  NewArrayFilled(v2)
          //   v4 <-  NewArrayFilled(v1, v3)
          //
          // After rewriting the user (v0 - v3 are unused and removed):
          //
          //   v4 <-  NewArrayEmpty(...)
          //   v5 <-  Const X
          //   v6 <-  NewArrayFilled(v5)
          //          APut v4, <Const 0>, v6
          //   v7 <-  Const Y
          //   v8 <-  NewArrayFilled(v7)
          //          APut v4, <Const 1>, v8
          //
          // Setting pending rewrites cause the copied NewArrayFilled to be rewritten in their new
          // location in the fixpoint:
          //
          //   v4 <-  NewArrayEmpty(...)
          //   v9 <-  NewArrayEmpty(...)
          //   v10 <- Const X
          //          APut v9, <Const 0>, v10
          //          APut v4, <Const 0>, v9
          //   v11 <- NewArrayEmpty(...)
          //   v12 <- Const Y
          //          APut v11, <Const 0>, v12
          //          APut v4, <Const 1>, v11
          //
          // If the NewArrayFilled which gets moved is supported then the second rewriting in the
          // fixpoint does not happen.
          pendingRewrites.set(true);
        }
      } else {
        rewriteToArrayPuts(code, blockIterator, instructionIterator, newArrayFilled);
      }
      return CodeRewriterResult.HAS_CHANGED;
    }

    private boolean canUseNewArrayFilled(NewArrayFilled newArrayFilled) {
      if (!options.isGeneratingDex()) {
        return false;
      }
      int size = newArrayFilled.size();
      if (size < rewriteArrayOptions.minSizeForFilledNewArray) {
        return false;
      }
      // filled-new-array is implemented only for int[] and Object[].
      DexType arrayType = newArrayFilled.getArrayType();
      if (arrayType.isIdenticalTo(dexItemFactory.intArrayType)) {
        // For int[], using filled-new-array is usually smaller than filled-array-data.
        // filled-new-array supports up to 5 registers before it's filled-new-array/range.
        if (size > rewriteArrayOptions.maxSizeForFilledNewArrayOfInts) {
          return false;
        }
        if (canUseNewArrayFilledData(newArrayFilled)
            && size
                > rewriteArrayOptions
                    .maxSizeForFilledNewArrayOfIntsWhenNewArrayFilledDataApplicable) {
          return false;
        }
        return true;
      }
      if (!arrayType.isPrimitiveArrayType()) {
        if (size > rewriteArrayOptions.maxSizeForFilledNewArrayOfReferences) {
          return false;
        }
        if (arrayType.isIdenticalTo(dexItemFactory.stringArrayType)) {
          return rewriteArrayOptions.canUseFilledNewArrayOfStrings();
        }
        if (!rewriteArrayOptions.canUseFilledNewArrayOfNonStringObjects()) {
          return false;
        }
        if (!rewriteArrayOptions.canUseFilledNewArrayOfArrays()
            && arrayType.getNumberOfLeadingSquareBrackets() > 1) {
          return false;
        }
        // Check that all arguments to the array is the array type or that the array is type
        // Object[].
        if (rewriteArrayOptions.canHaveSubTypesInFilledNewArrayBug()
            && arrayType.isNotIdenticalTo(dexItemFactory.objectArrayType)
            && !arrayType.isPrimitiveArrayType()) {
          DexType arrayElementType = arrayType.toArrayElementType(dexItemFactory);
          for (Value elementValue : newArrayFilled.inValues()) {
            if (!canStoreElementInNewArrayFilled(elementValue.getType(), arrayElementType)) {
              return false;
            }
          }
        }
        return true;
      }
      return false;
    }

    private boolean canStoreElementInNewArrayFilled(TypeElement valueType, DexType elementType) {
      if (elementType.isIdenticalTo(dexItemFactory.objectType)) {
        return true;
      }
      if (valueType.isNullType() && !elementType.isPrimitiveType()) {
        return true;
      }
      if (elementType.isArrayType()) {
        if (valueType.isNullType()) {
          return true;
        }
        ArrayTypeElement arrayTypeElement = valueType.asArrayType();
        if (arrayTypeElement == null
            || arrayTypeElement.getNesting() != elementType.getNumberOfLeadingSquareBrackets()) {
          return false;
        }
        valueType = arrayTypeElement.getBaseType();
        elementType = elementType.toBaseType(dexItemFactory);
      }
      assert !valueType.isArrayType();
      assert !elementType.isArrayType();
      if (valueType.isPrimitiveType() && !elementType.isPrimitiveType()) {
        return false;
      }
      if (valueType.isPrimitiveType()) {
        return true;
      }
      DexClass clazz = appView.definitionFor(elementType);
      if (clazz == null) {
        return false;
      }
      return valueType.isClassType(elementType);
    }

    private boolean canUseNewArrayFilledData(NewArrayFilled newArrayFilled) {
      // Only convert into NewArrayFilledData when compiling to DEX.
      if (!appView.options().isGeneratingDex()) {
        return false;
      }
      // If there is only one element it is typically smaller to generate the array put instruction
      // instead of fill array data.
      int size = newArrayFilled.size();
      if (size < rewriteArrayOptions.minSizeForFilledArrayData
          || size > rewriteArrayOptions.maxSizeForFilledArrayData) {
        return false;
      }
      if (!newArrayFilled.getArrayType().isPrimitiveArrayType()) {
        return false;
      }
      return Iterables.all(newArrayFilled.inValues(), Value::isConstant);
    }

    private NewArrayEmpty rewriteToNewArrayEmpty(
        IRCode code,
        BasicBlockInstructionListIterator instructionIterator,
        NewArrayFilled newArrayFilled) {
      // Load the size before the NewArrayEmpty instruction.
      ConstNumber constNumber =
          ConstNumber.builder()
              .setFreshOutValue(code, TypeElement.getInt())
              .setValue(newArrayFilled.size())
              .setPosition(options.debug ? newArrayFilled.getPosition() : Position.none())
              .build();
      instructionIterator.previous();
      instructionIterator.add(constNumber);
      Instruction next = instructionIterator.next();
      assert next == newArrayFilled;

      // Replace the InvokeNewArray instruction by a NewArrayEmpty instruction.
      NewArrayEmpty newArrayEmpty =
          new NewArrayEmpty(
              newArrayFilled.outValue(), constNumber.outValue(), newArrayFilled.getArrayType());
      instructionIterator.replaceCurrentInstruction(newArrayEmpty);
      return newArrayEmpty;
    }

    private void rewriteToNewArrayFilledData(
        IRCode code,
        BasicBlockIterator blockIterator,
        BasicBlockInstructionListIterator instructionIterator,
        NewArrayFilled newArrayFilled) {
      NewArrayEmpty newArrayEmpty =
          rewriteToNewArrayEmpty(code, instructionIterator, newArrayFilled);

      // Insert a new NewArrayFilledData instruction after the NewArrayEmpty instruction.
      short[] contents = computeArrayFilledData(newArrayFilled);
      NewArrayFilledData newArrayFilledData =
          new NewArrayFilledData(
              newArrayFilled.outValue(),
              newArrayFilled.getArrayType().elementSizeForPrimitiveArrayType(),
              newArrayFilled.size(),
              contents);
      newArrayFilledData.setPosition(newArrayFilled.getPosition());
      if (newArrayEmpty.getBlock().hasCatchHandlers()) {
        BasicBlock splitBlock =
            instructionIterator.splitCopyCatchHandlers(code, blockIterator, options);
        splitBlock.listIterator(code).add(newArrayFilledData);
      } else {
        instructionIterator.add(newArrayFilledData);
      }
    }

    private short[] computeArrayFilledData(NewArrayFilled newArrayFilled) {
      int elementSize = newArrayFilled.getArrayType().elementSizeForPrimitiveArrayType();
      int size = newArrayFilled.size();
      if (elementSize == 1) {
        short[] result = new short[(size + 1) / 2];
        for (int i = 0; i < size; i += 2) {
          ConstNumber constNumber =
              newArrayFilled.getOperand(i).getConstInstruction().asConstNumber();
          short value = (short) (constNumber.getIntValue() & 0xFF);
          if (i + 1 < size) {
            ConstNumber nextConstNumber =
                newArrayFilled.getOperand(i + 1).getConstInstruction().asConstNumber();
            value |= (short) ((nextConstNumber.getIntValue() & 0xFF) << 8);
          }
          result[i / 2] = value;
        }
        return result;
      }
      assert elementSize == 2 || elementSize == 4 || elementSize == 8;
      int shortsPerConstant = elementSize / 2;
      short[] result = new short[size * shortsPerConstant];
      for (int i = 0; i < size; i++) {
        ConstNumber constNumber =
            newArrayFilled.getOperand(i).getConstInstruction().asConstNumber();
        for (int part = 0; part < shortsPerConstant; part++) {
          result[i * shortsPerConstant + part] =
              (short) ((constNumber.getRawValue() >> (16 * part)) & 0xFFFFL);
        }
      }
      return result;
    }

    private void rewriteToArrayPuts(
        IRCode code,
        BasicBlockIterator blockIterator,
        BasicBlockInstructionListIterator instructionIterator,
        NewArrayFilled newArrayFilled) {
      NewArrayEmpty newArrayEmpty =
          rewriteToNewArrayEmpty(code, instructionIterator, newArrayFilled);

      ConstantMaterializingInstructionCache constantMaterializingInstructionCache =
          new ConstantMaterializingInstructionCache(rewriteArrayOptions, newArrayFilled);

      int index = 0;
      for (Value elementValue : newArrayFilled.inValues()) {
        if (instructionIterator.getBlock().hasCatchHandlers()) {
          BasicBlock splitBlock =
              instructionIterator.splitCopyCatchHandlers(code, blockIterator, options);
          instructionIterator = splitBlock.listIterator(code);
          Value putValue =
              getPutValue(
                  code,
                  instructionIterator,
                  newArrayEmpty,
                  elementValue,
                  constantMaterializingInstructionCache);
          blockIterator.positionAfterPreviousBlock(splitBlock);
          splitBlock = instructionIterator.splitCopyCatchHandlers(code, blockIterator, options);
          instructionIterator = splitBlock.listIterator(code);
          addArrayPut(code, instructionIterator, newArrayEmpty, index, putValue);
          blockIterator.positionAfterPreviousBlock(splitBlock);
          mayHaveRedundantBlocks = true;
        } else {
          Value putValue =
              getPutValue(
                  code,
                  instructionIterator,
                  newArrayEmpty,
                  elementValue,
                  constantMaterializingInstructionCache);
          addArrayPut(code, instructionIterator, newArrayEmpty, index, putValue);
        }
        index++;
      }

      assert constantMaterializingInstructionCache.checkAllOccurrenceProcessed();
    }

    private Value getPutValue(
        IRCode code,
        BasicBlockInstructionListIterator instructionIterator,
        NewArrayEmpty newArrayEmpty,
        Value elementValue,
        ConstantMaterializingInstructionCache constantMaterializingInstructionCache) {
      // If the value was only used by the NewArrayFilled instruction it now has no normal users.
      if (elementValue.hasAnyUsers()
          || !(elementValue.isConstString()
              || elementValue.isConstNumber()
              || elementValue.isConstClass()
              || elementValue.isDefinedByInstructionSatisfying(Instruction::isStaticGet)
              || (isDefinedByNewArrayFilledOfConstants(elementValue)
                  && !instructionIterator.getBlock().hasCatchHandlers()))) {
        return elementValue;
      }

      Value existingValue = constantMaterializingInstructionCache.getValue(elementValue);
      if (existingValue != null) {
        addToRemove(elementValue.getDefinition());
        return existingValue;
      }

      Instruction copy;
      if (elementValue.isConstNumber()) {
        copy = ConstNumber.copyOf(code, elementValue.getDefinition().asConstNumber());
      } else if (elementValue.isConstString()) {
        copy = ConstString.copyOf(code, elementValue.getDefinition().asConstString());
        constantMaterializingInstructionCache.putNewValue(copy.asConstString().outValue());
      } else if (elementValue.isConstClass()) {
        copy = ConstClass.copyOf(code, elementValue.getDefinition().asConstClass());
        constantMaterializingInstructionCache.putNewValue(copy.asConstClass().outValue());
      } else if (elementValue.isDefinedByInstructionSatisfying(Instruction::isStaticGet)) {
        copy = StaticGet.copyOf(code, elementValue.getDefinition().asStaticGet());
        constantMaterializingInstructionCache.putNewValue(copy.asStaticGet().outValue());
      } else if (isDefinedByNewArrayFilledOfConstants(elementValue)) {
        copy = copyConstantsNewArrayFilled(code, elementValue.getDefinition().asNewArrayFilled());
        assert !instructionIterator.getBlock().hasCatchHandlers();
        for (Value inValue : copy.asNewArrayFilled().inValues()) {
          instructionIterator.add(inValue.getDefinition());
          inValue.getDefinition().setBlock(instructionIterator.getBlock());
          inValue.getDefinition().setPosition(newArrayEmpty.getPosition());
        }
      } else {
        assert false;
        return elementValue;
      }
      copy.setBlock(instructionIterator.getBlock());
      copy.setPosition(newArrayEmpty.getPosition());
      instructionIterator.add(copy);
      addToRemove(elementValue.getDefinition());
      return copy.outValue();
    }

    private void addToRemove(Instruction instruction) {
      if (toRemove == NOTHING) {
        toRemove = SetUtils.newIdentityHashSet();
      }
      toRemove.add(instruction);
    }

    private void addArrayPut(
        IRCode code,
        BasicBlockInstructionListIterator instructionIterator,
        NewArrayEmpty newArrayEmpty,
        int index,
        Value elementValue) {
      // Load the array index before the ArrayPut instruction.
      ConstNumber constNumber =
          ConstNumber.builder()
              .setFreshOutValue(code, TypeElement.getInt())
              .setValue(index)
              .setPosition(options.debug ? newArrayEmpty.getPosition() : Position.none())
              .build();
      instructionIterator.add(constNumber);

      // Add the ArrayPut instruction.
      DexType arrayElementType = newArrayEmpty.getArrayType().toArrayElementType(dexItemFactory);
      MemberType memberType = MemberType.fromDexType(arrayElementType);
      ArrayPut arrayPut =
          ArrayPut.create(
              memberType, newArrayEmpty.outValue(), constNumber.outValue(), elementValue);
      arrayPut.setPosition(newArrayEmpty.getPosition());
      instructionIterator.add(arrayPut);
    }
  }

  private static class ConstantMaterializingInstructionCache {

    private final RewriteArrayOptions rewriteArrayOptions;

    // Track constants as DexItems, DexString for string constants and DexType for class constants.
    private final Map<DexItem, Integer> constantOccurrences = new IdentityHashMap<>();
    private final Map<DexItem, Value> constantValue = new IdentityHashMap<>();

    private ConstantMaterializingInstructionCache(
        RewriteArrayOptions rewriteArrayOptions, NewArrayFilled newArrayFilled) {
      this.rewriteArrayOptions = rewriteArrayOptions;
      for (Value elementValue : newArrayFilled.inValues()) {
        if (elementValue.hasAnyUsers()) {
          continue;
        }
        if (elementValue.isConstString()) {
          addOccurrence(elementValue.getDefinition().asConstString().getValue());
        } else if (elementValue.isConstClass()) {
          addOccurrence(elementValue.getDefinition().asConstClass().getValue());
        } else if (elementValue.isDefinedByInstructionSatisfying(Instruction::isStaticGet)) {
          addOccurrence(elementValue.getDefinition().asStaticGet().getField());
        }
        // Don't canonicalize numbers, as on DEX FilledNewArray is supported for primitives
        // on all versions.
      }
    }

    private Value getValue(Value elementValue) {
      if (elementValue.isConstString()) {
        DexString string = elementValue.getDefinition().asConstString().getValue();
        Value value = constantValue.get(string);
        if (value != null) {
          seenOcourence(string);
          return value;
        }
      } else if (elementValue.isConstClass()) {
        DexType type = elementValue.getDefinition().asConstClass().getValue();
        Value value = constantValue.get(type);
        if (value != null) {
          seenOcourence(type);
          return value;
        }
      } else if (elementValue.isDefinedByInstructionSatisfying(Instruction::isStaticGet)) {
        DexField field = elementValue.getDefinition().asStaticGet().getField();
        Value value = constantValue.get(field);
        if (value != null) {
          seenOcourence(field);
          return value;
        }
      }
      return null;
    }

    // Order: String > field > class.
    private DexItem smallestConstant(DexItem c1, DexItem c2) {
      if (c1 instanceof DexString) {
        if (c2 instanceof DexString) {
          return ((DexString) c1).compareTo((DexString) c2) < 0 ? c1 : c2;
        } else {
          assert c2 instanceof DexField || c2 instanceof DexType;
          return c2; // String larger than field and class.
        }
      } else if (c1 instanceof DexField) {
        if (c2 instanceof DexField) {
          return ((DexField) c1).compareTo((DexField) c2) < 0 ? c1 : c2;
        } else {
          // Field less than string, larger than class
          if (c2 instanceof DexString) {
            return c1;
          } else {
            assert c2 instanceof DexType;
            return c2;
          }
        }
      } else {
        assert c1 instanceof DexType;
        if (c2 instanceof DexType) {
          return ((DexType) c1).compareTo((DexType) c2) < 0 ? c1 : c2;
        } else {
          assert c2 instanceof DexString || c2 instanceof DexField;
          return c1; // Class less than string and field.
        }
      }
    }

    private DexItem getConstant(Value value) {
      Instruction instruction = value.getDefinition();
      if (instruction.isConstString()) {
        return instruction.asConstString().getValue();
      } else if (instruction.isStaticGet()) {
        return instruction.asStaticGet().getField();
      } else {
        assert instruction.isConstClass();
        return instruction.asConstClass().getValue();
      }
    }

    private void putNewValue(Value value) {
      DexItem constant = getConstant(value);
      assert constantOccurrences.containsKey(constant);
      assert !constantValue.containsKey(constant);
      if (constantValue.size() < rewriteArrayOptions.maxMaterializingConstants) {
        constantValue.put(constant, value);
      } else {
        assert constantValue.size() == rewriteArrayOptions.maxMaterializingConstants;
        // Find the least valuable active constant.
        int leastOccurrences = Integer.MAX_VALUE;
        DexItem valueWithLeastOccurrences = null;
        for (DexItem key : constantValue.keySet()) {
          int remainingOccurrences = constantOccurrences.get(key);
          if (remainingOccurrences < leastOccurrences) {
            leastOccurrences = remainingOccurrences;
            valueWithLeastOccurrences = key;
          } else if (remainingOccurrences == leastOccurrences) {
            assert valueWithLeastOccurrences
                != null; // Will always be set before the else branch is ever hit.
            valueWithLeastOccurrences = smallestConstant(valueWithLeastOccurrences, key);
          }
        }
        // Replace the new constant with the current least valuable one if more valuable.
        int newConstantOccurrences = constantOccurrences.get(constant);
        if (newConstantOccurrences > leastOccurrences
            || (newConstantOccurrences == leastOccurrences
                && smallestConstant(valueWithLeastOccurrences, constant)
                    == valueWithLeastOccurrences)) {
          constantValue.remove(valueWithLeastOccurrences);
          constantValue.put(constant, value);
        }
        assert constantValue.size() == rewriteArrayOptions.maxMaterializingConstants;
      }
      seenOcourence(constant);
    }

    private void addOccurrence(DexItem constant) {
      constantOccurrences.compute(constant, (k, v) -> (v == null) ? 1 : ++v);
    }

    private void seenOcourence(DexItem constant) {
      int remainingOccourences =
          constantOccurrences.compute(constant, (k, v) -> (v == null) ? Integer.MAX_VALUE : --v);
      // Remove from sets after last occurrence.
      if (remainingOccourences == 0) {
        constantOccurrences.remove(constant);
        constantValue.remove(constant);
      }
    }

    private boolean checkAllOccurrenceProcessed() {
      assert constantOccurrences.size() == 0;
      assert constantValue.size() == 0;
      return true;
    }
  }
}
