// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.code;

import com.android.tools.r8.ir.optimize.AffectedValues;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Linked list of instructions using Instruction.prev and Instruction.next fields.
 *
 * <pre>
 * Design notes:
 * - There is a 1:1 relationship between a BasicBlock and InstructionList.
 *   - We might want to just merge this into BasicBlock.
 * - Updates IRMetadata and sets Instruction.block fields when instructions are added.
 * - Does not implement Collection / List interfaces to better reflect that this is a
 *   special-purpose collection, where operations sometimes mutate the state of elements.
 * - Does not implement a non-assert "contains" method. Use Instruction.block to check containment.
 * - Does not clear next/prev fields upon removal.
 *   - Because there has not yet been a need to reuse instructions (except for the special case of
 *     block splitting).
 *   - |block| is cleared, and asserts prevent instructions from being added that already have a
 *     block set.
 * </pre>
 */
public class InstructionList implements Iterable<Instruction> {
  private final BasicBlock ownerBlock;
  private Instruction head;
  private Instruction tail;
  private int size;

  public InstructionList(BasicBlock ownerBlock) {
    this.ownerBlock = ownerBlock;
  }

  public int size() {
    return size;
  }

  public boolean isEmpty() {
    return head == null;
  }

  public Instruction getFirst() {
    assert !isEmpty();
    return head;
  }

  public Instruction getFirstOrNull() {
    return head;
  }

  /** Returns the instruction at the given index. This is O(n). */
  public Instruction getNth(int n) {
    assert n >= 0 && n < size : "n=" + n + " size=" + size;
    if (n > size / 2) {
      Instruction ret = tail;
      n = size - n - 1;
      for (int i = 0; i < n; ++i) {
        ret = ret.prev;
      }
      return ret;
    }
    Instruction ret = head;
    for (int i = 0; i < n; ++i) {
      ret = ret.next;
    }
    return ret;
  }

  public Instruction getLast() {
    assert !isEmpty();
    return tail;
  }

  public Instruction getLastOrNull() {
    return tail;
  }

  public void addFirst(Instruction newInstruction) {
    addBefore(newInstruction, head);
  }

  public void addLast(Instruction newInstruction) {
    addBefore(newInstruction, null);
  }

  /**
   * Inserts newInstruction before existingInstruction. If existingInstruction is null, adds at the
   * end.
   */
  public void addBefore(Instruction newInstruction, Instruction existingInstruction) {
    if (existingInstruction != null) {
      assert linearScanFinds(existingInstruction);
    }
    if (size == 0) {
      assert existingInstruction == null;
      head = newInstruction;
      tail = newInstruction;
    } else if (existingInstruction == null) {
      // Add to end.
      newInstruction.prev = tail;
      tail.next = newInstruction;
      tail = newInstruction;
    } else {
      Instruction prevInstruction = existingInstruction.prev;
      newInstruction.prev = prevInstruction;
      newInstruction.next = existingInstruction;
      existingInstruction.prev = newInstruction;
      if (prevInstruction == null) {
        assert head == existingInstruction;
        head = newInstruction;
      } else {
        prevInstruction.next = newInstruction;
      }
    }

    size += 1;
    newInstruction.block = ownerBlock;
    ownerBlock.getMetadata().record(newInstruction);
  }

  /** Adopt all instructions from another list (use this to split blocks). */
  public void severFrom(Instruction firstInstructionToMove) {
    assert isEmpty();
    BasicBlock sourceBlock = firstInstructionToMove.block;
    InstructionList sourceList = sourceBlock.getInstructions();
    // So far no need to sever between methods.
    assert sourceBlock.getMetadata() == ownerBlock.getMetadata();

    head = firstInstructionToMove;
    tail = sourceList.tail;

    Instruction lastInstructionToRemain = firstInstructionToMove.prev;
    sourceList.tail = lastInstructionToRemain;
    if (lastInstructionToRemain == null) {
      sourceList.head = null;
    } else {
      lastInstructionToRemain.next = null;
      firstInstructionToMove.prev = null;
    }

    int count = 0;
    BasicBlock newBlock = ownerBlock;
    for (Instruction current = firstInstructionToMove; current != null; current = current.next) {
      count += 1;
      current.block = newBlock;
    }
    size = count;
    sourceList.size -= count;
  }

  public void replace(Instruction oldInstruction, Instruction newInstruction) {
    replace(oldInstruction, newInstruction, null);
  }

  /**
   * Replace the oldInstruction with newInstruction.
   *
   * <p>Removes in-values from oldInstruction.
   *
   * <p>If the current instruction produces an out-value the new instruction must also produce an
   * out-value, and all uses of the current instructions out-value will be replaced by the new
   * instructions out-value.
   *
   * <p>The debug information of the current instruction will be attached to the new instruction.
   */
  public void replace(
      Instruction target, Instruction newInstruction, AffectedValues affectedValues) {
    for (Value value : target.inValues()) {
      value.removeUser(target);
    }
    if (target.hasUsedOutValue()) {
      assert newInstruction.outValue() != null;
      if (affectedValues != null && !newInstruction.getOutType().equals(target.getOutType())) {
        target.outValue().addAffectedValuesTo(affectedValues);
      }
      target.outValue().replaceUsers(newInstruction.outValue());
    }
    target.moveDebugValues(newInstruction);
    if (!newInstruction.hasPosition()) {
      newInstruction.setPosition(target.getPosition());
    }

    addBefore(newInstruction, target);
    removeIgnoreValues(target);
  }

  /**
   * Safe removal function that will insert a DebugLocalRead to take over the debug values if any
   * are associated with the current instruction.
   */
  public void removeOrReplaceByDebugLocalRead(Instruction target) {
    if (target.getDebugValues().isEmpty()) {
      removeAndDetachInValues(target);
    } else {
      replace(target, new DebugLocalRead());
    }
  }

  /**
   * Remove the given instruction.
   *
   * <p>The instruction will be removed and uses of its in-values removed.
   *
   * <p>If the current instruction produces an out-value this out value must not have any users.
   */
  public void removeAndDetachInValues(Instruction target) {
    assert target.outValue() == null || !target.outValue().isUsed();
    removeIgnoreValues(target.detachInValues());
  }

  /** Removes without doing any validation of in / out values. */
  public void removeIgnoreValues(Instruction target) {
    assert linearScanFinds(target);
    target.block = null;
    Instruction prev = target.prev;
    Instruction next = target.next;
    if (head == target) {
      head = next;
    }
    if (tail == target) {
      tail = prev;
    }
    if (prev != null) {
      prev.next = next;
    }
    if (next != null) {
      next.prev = prev;
    }
    size -= 1;
  }

  /** Removes all instructions. Instructions removed in this way may not be reused. */
  public void clear() {
    head = null;
    tail = null;
    size = 0;
  }

  // Non-assert uses should check instruction.block for containment. */
  private boolean linearScanFinds(Instruction instruction) {
    for (Instruction cur = head; cur != null; cur = cur.next) {
      if (cur == instruction) {
        return true;
      }
    }
    return false;
  }

  public Stream<Instruction> stream() {
    return StreamSupport.stream(new SpliteratorImpl(), false);
  }

  @Override
  public BasicBlockInstructionListIterator iterator() {
    return new BasicBlockInstructionListIterator(ownerBlock);
  }

  public BasicBlockInstructionListIterator iterator(Instruction firstInstructionToReturn) {
    return new BasicBlockInstructionListIterator(ownerBlock, firstInstructionToReturn);
  }

  private class SpliteratorImpl implements Spliterator<Instruction> {
    Instruction next = head;

    @Override
    public boolean tryAdvance(Consumer<? super Instruction> action) {
      if (next == null) {
        return false;
      }
      action.accept(next);
      next = next.next;
      return true;
    }

    @Override
    public Spliterator<Instruction> trySplit() {
      return null;
    }

    @Override
    public long estimateSize() {
      return size;
    }

    @Override
    public long getExactSizeIfKnown() {
      return size;
    }

    @Override
    public int characteristics() {
      return SIZED | DISTINCT | NONNULL;
    }
  }
}
