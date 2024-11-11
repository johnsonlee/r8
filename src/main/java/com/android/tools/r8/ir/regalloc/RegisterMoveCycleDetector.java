// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.regalloc;

import com.android.tools.r8.utils.SetUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class RegisterMoveCycleDetector {

  @SuppressWarnings("MixedMutabilityReturnType")
  static List<RegisterMoveCycle> getMoveCycles(TreeSet<RegisterMove> moveSet) {
    // Although there can be a cycle when there are two moves, we return no cycles, since the
    // default move scheduling has the same behavior as the cycle-based move scheduling in this
    // case.
    if (moveSet.size() <= 2) {
      return Collections.emptyList();
    }
    List<RegisterMoveCycle> moveCycles = new ArrayList<>();
    Int2ObjectMap<TreeSet<RegisterMove>> readBy = createReadByGraph(moveSet);
    Set<RegisterMove> finished = new HashSet<>();
    Deque<RegisterMove> stack = new ArrayDeque<>();
    TreeSet<RegisterMove> stackSet = new TreeSet<>();
    for (RegisterMove move : moveSet) {
      if (finished.contains(move)) {
        continue;
      }
      dfs(move, finished, readBy, stack, stackSet, moveCycles);
      assert finished.contains(move);
      assert stack.isEmpty();
      assert stackSet.isEmpty();
    }
    return moveCycles;
  }

  private static void dfs(
      RegisterMove move,
      Set<RegisterMove> finished,
      Int2ObjectMap<TreeSet<RegisterMove>> readBy,
      Deque<RegisterMove> stack,
      TreeSet<RegisterMove> stackSet,
      List<RegisterMoveCycle> moveCycles) {
    stack.addLast(move);
    boolean addedToStack = stackSet.add(move);
    assert addedToStack;
    // Explore all outgoing edges. The successors of this move are the moves that read the
    // destination registers of the current move.
    for (RegisterMove successor : getSuccessors(move, readBy)) {
      if (finished.contains(successor)) {
        // Already fully explored.
        continue;
      }
      if (successor.isIdentical(move)) {
        // This move is reading/writing overlapping registers (e.g., move-wide 0, 1).
        continue;
      }
      if (stackSet.contains(successor)) {
        moveCycles.add(extractCycle(stack, successor, readBy));
      } else {
        dfs(successor, finished, readBy, stack, stackSet, moveCycles);
      }
    }
    RegisterMove removedFromStack = stack.removeLast();
    assert removedFromStack.isIdentical(move);
    boolean removedFromStackSet = stackSet.remove(move);
    assert removedFromStackSet;
    boolean markedFinished = finished.add(move);
    assert markedFinished;
  }

  // Returns a one-to-many map from registers to the set of moves that read that register.
  private static Int2ObjectMap<TreeSet<RegisterMove>> createReadByGraph(
      TreeSet<RegisterMove> moveSet) {
    Int2ObjectMap<TreeSet<RegisterMove>> readBy = new Int2ObjectOpenHashMap<>();
    for (RegisterMove move : moveSet) {
      move.forEachSourceRegister(
          register -> {
            if (readBy.containsKey(register)) {
              readBy.get(register).add(move);
            } else {
              readBy.put(register, SetUtils.newTreeSet(move));
            }
          });
    }
    return readBy;
  }

  private static Set<RegisterMove> getSuccessors(
      RegisterMove move, Int2ObjectMap<TreeSet<RegisterMove>> readBy) {
    TreeSet<RegisterMove> successors = readBy.get(move.dst);
    if (move.isWide()) {
      TreeSet<RegisterMove> additionalSuccessors = readBy.get(move.dst + 1);
      if (successors == null) {
        successors = additionalSuccessors;
      } else if (additionalSuccessors != null) {
        successors = new TreeSet<>(successors);
        successors.addAll(additionalSuccessors);
      }
    }
    return successors != null ? successors : Collections.emptySet();
  }

  private static RegisterMoveCycle extractCycle(
      Deque<RegisterMove> stack,
      RegisterMove cycleEntry,
      Int2ObjectMap<TreeSet<RegisterMove>> readBy) {
    Deque<RegisterMove> cycle = new ArrayDeque<>();
    while (!cycleEntry.isIdentical(cycle.peekFirst())) {
      cycle.addFirst(stack.removeLast());
    }
    stack.addAll(cycle);
    TreeSet<RegisterMove> cycleSet = new TreeSet<>(cycle);
    return new RegisterMoveCycle(cycleSet, isClosedCycle(cycleSet, readBy));
  }

  private static boolean isClosedCycle(
      TreeSet<RegisterMove> cycle, Int2ObjectMap<TreeSet<RegisterMove>> readBy) {
    for (RegisterMove move : cycle) {
      for (int i = 0; i < move.type.requiredRegisters(); i++) {
        TreeSet<RegisterMove> successors = readBy.get(move.dst + i);
        if (successors != null) {
          for (RegisterMove successor : successors) {
            if (!cycle.contains(successor)) {
              return false;
            }
          }
        }
      }
    }
    return true;
  }
}
