// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion.passes;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexTypeUtils;
import com.android.tools.r8.ir.code.ArrayPut;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.NewArrayEmpty;
import com.android.tools.r8.ir.code.NewArrayFilled;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.ir.conversion.passes.result.CodeRewriterResult;
import com.android.tools.r8.utils.ArrayUtils;
import com.android.tools.r8.utils.DominatorChecker;
import com.android.tools.r8.utils.InternalOptions.RewriteArrayOptions;
import com.android.tools.r8.utils.ValueUtils;
import com.android.tools.r8.utils.ValueUtils.ArrayValues;
import com.android.tools.r8.utils.WorkList;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Replace new-array followed by stores of constants to all entries with new-array and
 * fill-array-data / filled-new-array.
 *
 * <p>The format of the new-array and its puts must be of the form:
 *
 * <pre>
 *   v0 <- new-array T vSize
 *   ...
 *   array-put v0 vValue1 vIndex1
 *   ...
 *   array-put v0 vValueN vIndexN
 * </pre>
 *
 * <p>The flow between the array v0 and its puts must be linear with no other uses of v0 besides the
 * array-put instructions, thus any no intermediate instruction (... above) must use v0 and also
 * cannot have catch handlers that would transfer out control (those could then have uses of v0).
 *
 * <p>The allocation of the new-array can itself have catch handlers, in which case, those are to
 * remain active on the translated code. Translated code can have two forms.
 *
 * <p>The first is using the original array allocation and filling in its data if it can be encoded:
 *
 * <pre>
 *   v0 <- new-array T vSize
 *   filled-array-data v0
 *   ...
 *   ...
 * </pre>
 *
 * The data payload is encoded directly in the instruction so no dependencies are needed for filling
 * the data array. Thus, the fill is inserted at the point of the allocation. If the allocation has
 * catch handlers its block must be split and the handlers put on the fill instruction too. This is
 * correct only because there are no exceptional transfers in (...) that could observe the early
 * initialization of the data.
 *
 * <p>The second is using filled-new-array and has the form:
 *
 * <pre>
 * ...
 * ...
 * v0 <- filled-new-array T vValue1 ... vValueN
 * </pre>
 *
 * Here the correctness relies on no exceptional transfers in (...) that could observe the missing
 * allocation of the array. The late allocation ensures that the values are available at allocation
 * time. If the original allocation has catch handlers then the new allocation needs to link those
 * too. In general that may require splitting the block twice so that the new allocation is the
 * single throwing instruction in its block.
 */
public class ArrayConstructionSimplifier extends CodeRewriterPass<AppInfo> {

  private final RewriteArrayOptions rewriteArrayOptions;

  public ArrayConstructionSimplifier(AppView<?> appView) {
    super(appView);
    rewriteArrayOptions = options.rewriteArrayOptions();
  }

  @Override
  protected String getRewriterId() {
    return "ArrayConstructionSimplifier";
  }

  @Override
  protected CodeRewriterResult rewriteCode(IRCode code) {
    ArrayList<ArrayValues> candidates = findOptimizableArrays(code);

    if (candidates.isEmpty()) {
      return CodeRewriterResult.NO_CHANGE;
    }
    applyChanges(code, candidates);
    return CodeRewriterResult.HAS_CHANGED;
  }

  @Override
  protected boolean shouldRewriteCode(IRCode code, MethodProcessor methodProcessor) {
    return code.metadata().mayHaveNewArrayEmpty();
  }

  private ArrayList<ArrayValues> findOptimizableArrays(IRCode code) {
    ArrayList<ArrayValues> candidates = new ArrayList<>();
    for (Instruction instruction : code.instructions()) {
      NewArrayEmpty newArrayEmpty = instruction.asNewArrayEmpty();
      if (newArrayEmpty != null) {
        ArrayValues arrayValues = analyzeCandidate(newArrayEmpty, code);
        if (arrayValues != null) {
          candidates.add(arrayValues);
        }
      }
    }
    return candidates;
  }

  private ArrayValues analyzeCandidate(NewArrayEmpty newArrayEmpty, IRCode code) {
    if (newArrayEmpty.getLocalInfo() != null) {
      return null;
    }
    if (!rewriteArrayOptions.isPotentialSize(newArrayEmpty.sizeIfConst())) {
      return null;
    }

    ArrayValues arrayValues = ValueUtils.computeInitialArrayValues(newArrayEmpty);
    // Holes (default-initialized entries) could be supported, but they are rare and would
    // complicate the logic.
    if (arrayValues == null || arrayValues.containsHoles()) {
      return null;
    }

    // See if all instructions are in the same try/catch.
    ArrayPut lastArrayPut = ArrayUtils.last(arrayValues.getArrayPutsByIndex());
    if (!newArrayEmpty.getBlock().hasEquivalentCatchHandlers(lastArrayPut.getBlock())) {
      // Possible improvements:
      // 1) Ignore catch handlers that do not catch OutOfMemoryError / NoClassDefFoundError.
      // 2) Use the catch handlers from the new-array-empty if all exception blocks exit without
      //    side effects (e.g. no method calls & no monitor instructions).
      return null;
    }

    if (!checkTypesAreCompatible(arrayValues, code)) {
      return null;
    }
    if (!checkDominance(arrayValues)) {
      return null;
    }
    return arrayValues;
  }

  private boolean checkTypesAreCompatible(ArrayValues arrayValues, IRCode code) {
    // aput-object allows any object for arrays of interfaces, but new-filled-array fails to verify
    // if types require a cast.
    // TODO(b/246971330): Check if adding a checked-cast would have the same observable result. E.g.
    //   if aput-object throws a ClassCastException if given an object that does not implement the
    //   desired interface, then we could add check-cast instructions for arguments we're not sure
    //   about.
    NewArrayEmpty newArrayEmpty = arrayValues.getDefinition().asNewArrayEmpty();
    DexType elementType = newArrayEmpty.type.toArrayElementType(dexItemFactory);
    boolean needsTypeCheck =
        !elementType.isPrimitiveType() && elementType.isNotIdenticalTo(dexItemFactory.objectType);
    if (!needsTypeCheck) {
      return true;
    }

    // Not safe to move allocation if NoClassDefError is possible.
    // TODO(b/246971330): Make this work for D8 where it ~always returns false by checking that
    // all instructions between new-array-empty and the last array-put report
    // !instruction.instructionMayHaveSideEffects(). Alternatively, we could replace the
    // new-array-empty with a const-class instruction in this case.
    if (!DexTypeUtils.isTypeAccessibleInMethodContext(
        appView, elementType.toBaseType(dexItemFactory), code.context())) {
      return false;
    }

    for (ArrayPut arrayPut : arrayValues.getArrayPutsByIndex()) {
      Value value = arrayPut.value();
      if (value.isAlwaysNull(appView)) {
        continue;
      }
      DexType valueDexType = value.getType().asReferenceType().toDexType(dexItemFactory);
      if (elementType.isArrayType()) {
        if (elementType.isNotIdenticalTo(valueDexType)) {
          return false;
        }
      } else if (valueDexType.isArrayType()) {
        // isSubtype asserts for this case.
        return false;
      } else if (valueDexType.isNullValueType()) {
        // Assume instructions can cause value.isAlwaysNull() == false while the DexType is
        // null.
        // TODO(b/246971330): Figure out how to write a test in SimplifyArrayConstructionTest
        //   that hits this case.
      } else {
        // TODO(b/246971330): When in d8 mode, we might still be able to see if this is true for
        //   library types (which this helper does not do).
        if (appView.isSubtype(valueDexType, elementType).isPossiblyFalse()) {
          return false;
        }
      }
    }
    return true;
  }

  private static boolean checkDominance(ArrayValues arrayValues) {
    Value arrayValue = arrayValues.getArrayValue();

    Set<BasicBlock> usageBlocks = Sets.newIdentityHashSet();
    Set<Instruction> uniqueUsers = arrayValue.uniqueUsers();
    for (Instruction user : uniqueUsers) {
      ArrayPut arrayPut = user.asArrayPut();
      if (arrayPut == null || arrayPut.array() != arrayValue) {
        usageBlocks.add(user.getBlock());
      }
    }

    // Ensure that the array-put assigning the highest index is dominated by all other array-puts.
    ArrayPut lastArrayPut = ArrayUtils.last(arrayValues.getArrayPutsByIndex());
    BasicBlock lastArrayPutBlock = lastArrayPut.getBlock();
    BasicBlock subgraphEntryBlock = arrayValue.definition.getBlock();

    // Ensure all blocks for users of the array are dominated by the last array-put's block.
    for (BasicBlock usageBlock : usageBlocks) {
      if (!DominatorChecker.check(subgraphEntryBlock, usageBlock, lastArrayPutBlock)) {
        return false;
      }
    }

    // Ensure all array users in the same block appear after the last array-put
    for (Instruction inst : lastArrayPutBlock.getInstructions()) {
      if (inst == lastArrayPut) {
        break;
      }
      if (uniqueUsers.contains(inst)) {
        ArrayPut arrayPut = inst.asArrayPut();
        if (arrayPut == null || arrayPut.array() != arrayValue) {
          return false;
        }
      }
    }

    // It will not be the case that the newArrayEmpty dominates the phi user (or else it would
    // just be a normal user). It is safe to optimize if all paths from the new-array-empty to the
    // phi user include the last array-put (where the filled-new-array will end up).
    if (anyPhiUsersReachableWhenOptimized(arrayValues)) {
      return false;
    }
    return true;
  }

  /**
   * Determines if there are any paths from the new-array-empty to any of its phi users that do not
   * go through the last array-put, and that do not go through exceptional edges for new-array-empty
   * / array-puts that will be removed.
   */
  private static boolean anyPhiUsersReachableWhenOptimized(ArrayValues arrayValues) {
    Value arrayValue = arrayValues.getArrayValue();
    if (!arrayValue.hasPhiUsers()) {
      return false;
    }
    Set<BasicBlock> phiUserBlocks = arrayValue.uniquePhiUserBlocks();
    WorkList<BasicBlock> workList = WorkList.newIdentityWorkList();
    // Mark the last array-put as seen in order to find paths that do not contain it.
    workList.markAsSeen(ArrayUtils.last(arrayValues.getArrayPutsByIndex()).getBlock());
    // Start with normal successors since if optimized, the new-array-empty block will have no
    // throwing instructions.
    workList.addIfNotSeen(arrayValue.definition.getBlock().getNormalSuccessors());
    while (workList.hasNext()) {
      BasicBlock current = workList.removeLast();
      if (phiUserBlocks.contains(current)) {
        return true;
      }
      if (current.hasCatchHandlers()) {
        Instruction throwingInstruction = current.exceptionalExit();
        if (throwingInstruction != null) {
          ArrayPut arrayPut = throwingInstruction.asArrayPut();
          // Ignore exceptional edges that will be remove if optimized.
          if (arrayPut != null && arrayPut.array() == arrayValue) {
            workList.addIfNotSeen(current.getNormalSuccessors());
            continue;
          }
        }
      }
      workList.addIfNotSeen(current.getSuccessors());
    }
    return false;
  }

  private void applyChanges(IRCode code, List<ArrayValues> candidates) {
    Set<BasicBlock> relevantBlocks = Sets.newIdentityHashSet();
    // All keys instructionsToChange are removed, and also maps lastArrayPut -> newArrayFilled.
    Map<Instruction, Instruction> instructionsToChange = new IdentityHashMap<>();
    boolean needToRemoveUnreachableBlocks = false;

    for (ArrayValues arrayValues : candidates) {
      NewArrayEmpty newArrayEmpty = arrayValues.getDefinition().asNewArrayEmpty();
      instructionsToChange.put(newArrayEmpty, newArrayEmpty);
      BasicBlock allocationBlock = newArrayEmpty.getBlock();
      relevantBlocks.add(allocationBlock);

      ArrayPut[] arrayPutsByIndex = arrayValues.getArrayPutsByIndex();
      int lastArrayPutIndex = arrayPutsByIndex.length - 1;
      for (int i = 0; i < lastArrayPutIndex; ++i) {
        ArrayPut arrayPut = arrayPutsByIndex[i];
        instructionsToChange.put(arrayPut, arrayPut);
        relevantBlocks.add(arrayPut.getBlock());
      }
      ArrayPut lastArrayPut = arrayPutsByIndex[lastArrayPutIndex];
      BasicBlock lastArrayPutBlock = lastArrayPut.getBlock();
      relevantBlocks.add(lastArrayPutBlock);

      // newArrayEmpty's outValue must be cleared before trying to remove newArrayEmpty. Rather than
      // store the outValue for later, create and store newArrayFilled.
      Value arrayValue = newArrayEmpty.clearOutValue();
      NewArrayFilled newArrayFilled =
          new NewArrayFilled(newArrayEmpty.type, arrayValue, arrayValues.getElementValues());
      newArrayFilled.setPosition(lastArrayPut.getPosition());
      instructionsToChange.put(lastArrayPut, newArrayFilled);

      if (arrayValue.hasPhiUsers() && allocationBlock.hasCatchHandlers()) {
        // When phi users exist, the phis belong to the exceptional successors of the allocation
        // block. In order to preserve them, move them to the new allocation block.
        // This is safe because we've already checked hasEquivalentCatchHandlers().
        lastArrayPutBlock.removeAllExceptionalSuccessors();
        lastArrayPutBlock.moveCatchHandlers(allocationBlock);
        needToRemoveUnreachableBlocks = true;
      }
    }

    for (BasicBlock block : relevantBlocks) {
      boolean hasCatchHandlers = block.hasCatchHandlers();
      InstructionListIterator it = block.listIterator();
      while (it.hasNext()) {
        Instruction possiblyNewArray = instructionsToChange.get(it.next());
        if (possiblyNewArray != null) {
          if (possiblyNewArray.isNewArrayFilled()) {
            // Change the last array-put to the new-array-filled.
            it.replaceCurrentInstruction(possiblyNewArray);
          } else {
            it.removeOrReplaceByDebugLocalRead();
            if (hasCatchHandlers) {
              // Removing these catch handlers shrinks their ranges to be only that where the
              // allocation occurs.
              needToRemoveUnreachableBlocks = true;
              assert !block.canThrow();
              block.removeAllExceptionalSuccessors();
            }
          }
        }
      }
    }

    if (needToRemoveUnreachableBlocks) {
      code.removeUnreachableBlocks();
    }
    code.removeRedundantBlocks();
  }
}
