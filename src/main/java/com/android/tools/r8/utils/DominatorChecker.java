// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import com.android.tools.r8.ir.code.BasicBlock;
import com.google.common.collect.Sets;
import java.util.Collections;
import java.util.Set;

/**
 * Given a subgraph defined by sourceBlock / destBlock, where sourceBlock dominates destBlock,
 * allows querying whether other blocks within this subgraph dominate destBlock.
 */
public interface DominatorChecker {
  boolean check(BasicBlock targetBlock);

  DominatorChecker FALSE_CHECKER = targetBlock -> false;

  class PrecomputedDominatorChecker implements DominatorChecker {
    private final Set<BasicBlock> dominators;

    public PrecomputedDominatorChecker(Set<BasicBlock> dominators) {
      this.dominators = dominators;
    }

    @Override
    public boolean check(BasicBlock targetBlock) {
      return dominators.contains(targetBlock);
    }
  }

  class TraversingDominatorChecker implements DominatorChecker {
    private final BasicBlock subgraphEntryBlock;
    private final BasicBlock subgraphExitBlock;
    private final Set<BasicBlock> knownDominators;
    private final WorkList<BasicBlock> workList = WorkList.newIdentityWorkList();
    private BasicBlock prevTargetBlock;

    private TraversingDominatorChecker(
        BasicBlock subgraphEntryBlock,
        BasicBlock subgraphExitBlock,
        Set<BasicBlock> knownDominators) {
      this.subgraphEntryBlock = subgraphEntryBlock;
      this.subgraphExitBlock = subgraphExitBlock;
      this.knownDominators = knownDominators;
      prevTargetBlock = subgraphExitBlock;
    }

    @Override
    public boolean check(BasicBlock targetBlock) {
      assert prevTargetBlock != null : "DominatorChecker cannot be used after returning false.";
      Set<BasicBlock> knownDominators = this.knownDominators;
      if (knownDominators.contains(targetBlock)) {
        return true;
      }
      // See if a block on the same linear path has already been checked.
      BasicBlock firstSplittingBlock = targetBlock;
      if (firstSplittingBlock.hasUniqueSuccessorWithUniquePredecessor()) {
        do {
          // knownDominators prevents firstSplittingBlock from being destBlock.
          assert firstSplittingBlock != subgraphExitBlock;
          firstSplittingBlock = firstSplittingBlock.getUniqueSuccessor();
        } while (firstSplittingBlock.hasUniqueSuccessorWithUniquePredecessor());

        if (knownDominators.contains(firstSplittingBlock)) {
          knownDominators.add(targetBlock);
          return true;
        }
      }

      boolean ret;
      // Since we know the previously checked block is a dominator, narrow the check by using it for
      // either sourceBlock or destBlock.
      if (workList.isSeen(targetBlock)) {
        workList.clearSeen();
        ret = checkWithTraversal(prevTargetBlock, subgraphExitBlock, firstSplittingBlock, workList);
        prevTargetBlock = firstSplittingBlock;
      } else {
        ret = checkWithTraversal(subgraphEntryBlock, prevTargetBlock, targetBlock, workList);
        prevTargetBlock = targetBlock;
      }
      if (ret) {
        knownDominators.add(targetBlock);
        if (firstSplittingBlock != targetBlock) {
          knownDominators.add(firstSplittingBlock);
        }
      } else {
        prevTargetBlock = null;
      }
      return ret;
    }

    /**
     * Within the subgraph defined by the given entry/exit blocks, returns whether targetBlock
     * dominates the exit block.
     */
    private static boolean checkWithTraversal(
        BasicBlock subgraphEntryBlock,
        BasicBlock subgraphExitBlock,
        BasicBlock targetBlock,
        WorkList<BasicBlock> workList) {
      assert workList.isEmpty();

      workList.markAsSeen(targetBlock);
      workList.addIfNotSeen(subgraphExitBlock.getPredecessors());
      while (!workList.isEmpty()) {
        BasicBlock curBlock = workList.removeLast();
        if (curBlock == subgraphEntryBlock) {
          // There is a path from subgraphExitBlock -> subgraphEntryBlock that does not go through
          // targetBlock.
          return false;
        }
        assert !curBlock.isEntry() : "subgraphEntryBlock did not dominate subgraphExitBlock";
        workList.addIfNotSeen(curBlock.getPredecessors());
      }

      return true;
    }
  }

  static DominatorChecker create(BasicBlock subgraphEntryBlock, BasicBlock subgraphExitBlock) {
    // Fast-path: blocks are the same.
    // As of Nov 2023: in Chrome for String.format() optimization, this covers 77% of cases.
    if (subgraphEntryBlock == subgraphExitBlock) {
      return new PrecomputedDominatorChecker(Collections.singleton(subgraphEntryBlock));
    }

    // Shrink the subgraph by moving subgraphEntryBlock forward to the first block with multiple
    // successors.
    Set<BasicBlock> headAndTailDominators = Sets.newIdentityHashSet();
    headAndTailDominators.add(subgraphEntryBlock);
    while (subgraphEntryBlock.hasUniqueSuccessorWithUniquePredecessor()) {
      subgraphEntryBlock = subgraphEntryBlock.getUniqueSuccessor();
      if (!headAndTailDominators.add(subgraphEntryBlock)) {
        // Hit an infinite loop. Code would not verify in this case.
        assert false;
        return FALSE_CHECKER;
      }
      if (subgraphEntryBlock == subgraphExitBlock) {
        // As of Nov 2023: in Chrome for String.format() optimization, a linear path from
        // source->dest was 14% of cases.
        return new PrecomputedDominatorChecker(headAndTailDominators);
      }
    }
    if (subgraphEntryBlock.getSuccessors().isEmpty()) {
      return FALSE_CHECKER;
    }

    // Shrink the subgraph by moving subgraphExitBlock back to the first block with multiple
    // predecessors.
    headAndTailDominators.add(subgraphExitBlock);
    while (subgraphExitBlock.hasUniquePredecessorWithUniqueSuccessor()) {
      subgraphExitBlock = subgraphExitBlock.getUniquePredecessor();
      if (!headAndTailDominators.add(subgraphExitBlock)) {
        if (subgraphEntryBlock == subgraphExitBlock) {
          // This normally happens when moving subgraphEntryBlock forwards, but can also occur when
          // moving subgraphExitBlock backwards when subgraphEntryBlock has multiple successors.
          return new PrecomputedDominatorChecker(headAndTailDominators);
        }
        // Hit an infinite loop. Code would not verify in this case.
        assert false;
        return FALSE_CHECKER;
      }
    }

    if (subgraphExitBlock.isEntry()) {
      return FALSE_CHECKER;
    }

    return new TraversingDominatorChecker(
        subgraphEntryBlock, subgraphExitBlock, headAndTailDominators);
  }

  /**
   * Returns whether targetBlock dominates subgraphExitBlock by performing a depth-first traversal
   * from subgraphExitBlock to subgraphEntryBlock with targetBlock removed from the graph.
   */
  @SuppressWarnings("InconsistentOverloads")
  static boolean check(
      BasicBlock subgraphEntryBlock, BasicBlock subgraphExitBlock, BasicBlock targetBlock) {
    if (targetBlock == subgraphExitBlock) {
      return true;
    }
    if (subgraphEntryBlock == subgraphExitBlock) {
      return false;
    }
    return TraversingDominatorChecker.checkWithTraversal(
        subgraphEntryBlock, subgraphExitBlock, targetBlock, WorkList.newIdentityWorkList());
  }
}
