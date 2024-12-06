// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.regalloc;

import static com.android.tools.r8.ir.code.IRCode.INSTRUCTION_NUMBER_DELTA;
import static com.android.tools.r8.ir.regalloc.LiveIntervals.NO_REGISTER;
import static com.google.common.base.Predicates.alwaysTrue;

import com.android.tools.r8.cf.FixedLocalValue;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DebugLocalInfo;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.Add;
import com.android.tools.r8.ir.code.And;
import com.android.tools.r8.ir.code.Argument;
import com.android.tools.r8.ir.code.ArithmeticBinop;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.CheckCast;
import com.android.tools.r8.ir.code.DebugLocalsChange;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.IRCode.LiveAtEntrySets;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionIterator;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.Invoke;
import com.android.tools.r8.ir.code.Move;
import com.android.tools.r8.ir.code.MoveException;
import com.android.tools.r8.ir.code.NumericType;
import com.android.tools.r8.ir.code.Or;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.StackValue;
import com.android.tools.r8.ir.code.StackValues;
import com.android.tools.r8.ir.code.Sub;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.code.Xor;
import com.android.tools.r8.ir.regalloc.RegisterPositions.RegisterType;
import com.android.tools.r8.utils.ArrayUtils;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.IntObjPredicate;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.IterableUtils;
import com.android.tools.r8.utils.LinkedHashSetUtils;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.SetUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.Timing;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multisets;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap.Entry;
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntRBTreeSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSortedSet;
import it.unimi.dsi.fastutil.objects.Reference2IntArrayMap;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Linear scan register allocator.
 *
 * <p>The implementation is inspired by:
 *
 * <ul>
 *   <li>"Linear Scan Register Allocation in the Context of SSA Form and Register Constraints"
 *   (ftp://ftp.ssw.uni-linz.ac.at/pub/Papers/Moe02.PDF)</li>
 *   <li>"Linear Scan Register Allocation on SSA Form"
 *   (http://www.christianwimmer.at/Publications/Wimmer10a/Wimmer10a.pdf)</li>
 *   <li>"Linear Scan Register Allocation for the Java HotSpot Client Compiler"
 *   (http://www.christianwimmer.at/Publications/Wimmer04a/Wimmer04a.pdf)</li>
 * </ul>
 */
public class LinearScanRegisterAllocator implements RegisterAllocator {

  public static final int REGISTER_CANDIDATE_NOT_FOUND = -1;
  public static final int MIN_CONSTANT_FREE_FOR_POSITIONS = 5;
  public static final int EXCEPTION_INTERVALS_OVERLAP_CUTOFF = 500;

  public enum ArgumentReuseMode {
    ALLOW_ARGUMENT_REUSE_U4BIT,
    ALLOW_ARGUMENT_REUSE_U8BIT,
    ALLOW_ARGUMENT_REUSE_U8BIT_REFINEMENT,
    ALLOW_ARGUMENT_REUSE_U8BIT_RETRY,
    ALLOW_ARGUMENT_REUSE_U16BIT;

    int getMaxRegisterNumber() {
      switch (this) {
        case ALLOW_ARGUMENT_REUSE_U4BIT:
          return Constants.U4BIT_MAX;
        case ALLOW_ARGUMENT_REUSE_U8BIT:
        case ALLOW_ARGUMENT_REUSE_U8BIT_REFINEMENT:
        case ALLOW_ARGUMENT_REUSE_U8BIT_RETRY:
          return Constants.U8BIT_MAX;
        case ALLOW_ARGUMENT_REUSE_U16BIT:
          return Constants.U16BIT_MAX;
        default:
          throw new Unreachable();
      }
    }

    boolean hasRegisterConstraint(LiveIntervals intervals) {
      return hasRegisterConstraint(intervals.getRegisterLimit());
    }

    boolean hasRegisterConstraint(LiveIntervalsUse use) {
      return hasRegisterConstraint(use.getLimit());
    }

    private boolean hasRegisterConstraint(int constraint) {
      switch (this) {
        case ALLOW_ARGUMENT_REUSE_U4BIT:
          return false;
        case ALLOW_ARGUMENT_REUSE_U8BIT:
        case ALLOW_ARGUMENT_REUSE_U8BIT_REFINEMENT:
        case ALLOW_ARGUMENT_REUSE_U8BIT_RETRY:
          return constraint == Constants.U4BIT_MAX;
        case ALLOW_ARGUMENT_REUSE_U16BIT:
          return constraint != Constants.U16BIT_MAX;
        default:
          throw new Unreachable();
      }
    }

    boolean is4Bit() {
      return this == ALLOW_ARGUMENT_REUSE_U4BIT;
    }

    boolean is8Bit() {
      return this == ALLOW_ARGUMENT_REUSE_U8BIT
          || this == ALLOW_ARGUMENT_REUSE_U8BIT_REFINEMENT
          || this == ALLOW_ARGUMENT_REUSE_U8BIT_RETRY;
    }

    boolean is8BitRefinement() {
      return this == ALLOW_ARGUMENT_REUSE_U8BIT_REFINEMENT;
    }

    boolean is8BitRetry() {
      return this == ALLOW_ARGUMENT_REUSE_U8BIT_RETRY;
    }

    boolean is16Bit() {
      return this == ALLOW_ARGUMENT_REUSE_U16BIT;
    }
  }

  private static class LocalRange implements Comparable<LocalRange> {
    final Value value;
    final DebugLocalInfo local;
    final int register;
    final int start;
    final int end;

    LocalRange(Value value, int register, int start, int end) {
      assert value.hasLocalInfo();
      this.value = value;
      this.local = value.getLocalInfo();
      this.register = register;
      this.start = start;
      this.end = end;
    }

    @Override
    public int compareTo(LocalRange o) {
      return (start != o.start)
          ? Integer.compare(start, o.start)
          : Integer.compare(end, o.end);
    }

    @Override
    public String toString() {
      return local + " @ r" + register + ": " + new LiveRange(start, end);
    }
  }

  // App view to be able to create types and access the options.
  private final AppView<?> appView;
  // The code for which to allocate registers.
  private final IRCode code;
  // Number of registers used for arguments.
  protected final int numberOfArgumentRegisters;
  // Number of argument registers that may be assumed to be in 4 bit registers. This should only be
  // used when mode is ALLOW_ARGUMENT_REUSE_U8BIT_REFINEMENT.
  private int numberOf4BitArgumentRegisters = 0;

  // Mapping from basic blocks to the set of values live at entry to that basic block.
  private Map<BasicBlock, LiveAtEntrySets> liveAtEntrySets;
  // The value of the first argument, or null if the method has no arguments.
  protected Value firstArgumentValue;

  // The current register allocation mode.
  private ArgumentReuseMode mode;
  // The set of registers that are free for allocation.
  private IntSortedSet freeRegisters = new IntRBTreeSet();
  // The max register number used.
  private int maxRegisterNumber = -1;

  // List of all top-level live intervals for all SSA values.
  private List<LiveIntervals> liveIntervals = new ArrayList<>();
  // List of active intervals.
  // TODO(b/270398965): Replace LinkedList.
  @SuppressWarnings("JdkObsolete")
  private List<LiveIntervals> active = new LinkedList<>();
  // List of intervals where the current instruction falls into one of their live range holes.
  // TODO(b/270398965): Replace LinkedList.
  @SuppressWarnings("JdkObsolete")
  protected List<LiveIntervals> inactive = new LinkedList<>();
  // List of intervals that no register has been allocated to sorted by first live range.
  protected PriorityQueue<LiveIntervals> unhandled = new PriorityQueue<>();

  // The registers that have been released as a result of advancing to the next live intervals.
  // A register is released if an active or inactive interval becomes handled.
  private IntList expiredHere = new IntArrayList();

  // List of intervals for the result of move-exception instructions.
  // Always empty in mode ALLOW_ARGUMENT_REUSE.
  private List<LiveIntervals> moveExceptionIntervals = new ArrayList<>();

  // The first register used for parallel moves. After register allocation the parallel move
  // temporary registers are [firstParallelMoveTemporary, maxRegisterNumber].
  private int firstParallelMoveTemporary = NO_REGISTER;
  // Mapping from register number to the number of unused register numbers below that register
  // number. Used for compacting the register numbers if some spill registers are not used
  // because their values can be rematerialized.
  private int[] unusedRegisters = null;

  private final Timing timing;

  Iterable<LiveIntervals> getArgumentLiveIntervals() {
    return Iterables.transform(
        code.arguments(), argument -> argument.outValue().getLiveIntervals());
  }

  // Whether or not the code has a move exception instruction. Used to pin the move exception
  // register.
  private boolean hasDedicatedMoveExceptionRegister() {
    return !moveExceptionIntervals.isEmpty();
  }

  // We allocate a dedicated move exception register right after the arguments.
  private int getMoveExceptionRegister() {
    assert hasDedicatedMoveExceptionRegister();
    return numberOfArgumentRegisters;
  }

  private int getMoveExceptionOffsetForLocalRegisters() {
    return BooleanUtils.intValue(
        hasDedicatedMoveExceptionRegister()
            && isDedicatedMoveExceptionRegisterInLastLocalRegister());
  }

  private boolean isDedicatedMoveExceptionRegister(int register) {
    return hasDedicatedMoveExceptionRegister() && register == getMoveExceptionRegister();
  }

  private boolean isDedicatedMoveExceptionRegisterInFirstLocalRegister() {
    assert hasDedicatedMoveExceptionRegister();
    if (mode.is4Bit() || mode.is16Bit()) {
      return true;
    }
    if (mode.is8BitRefinement()) {
      assert numberOf4BitArgumentRegisters > 0;
      return true;
    }
    return !options().getTestingOptions().enableUseLastLocalRegisterAsMoveExceptionRegister;
  }

  private boolean isDedicatedMoveExceptionRegisterInLastLocalRegister() {
    return !isDedicatedMoveExceptionRegisterInFirstLocalRegister();
  }

  public LinearScanRegisterAllocator(AppView<?> appView, IRCode code, Timing timing) {
    this.appView = appView;
    this.code = code;
    int argumentRegisters = 0;
    for (Instruction instruction : code.entryBlock().getInstructions()) {
      if (instruction.isArgument()) {
        argumentRegisters += instruction.outValue().requiredRegisters();
      } else {
        break;
      }
    }
    numberOfArgumentRegisters = argumentRegisters;
    this.timing = timing;
  }

  private boolean retry8BitAllocationWith4BitArgumentRegisters() {
    assert mode.is8Bit();
    assert numberOf4BitArgumentRegisters == 0;
    if (code.context().getDefinition().getNumberOfArguments() == 0) {
      return false;
    }
    numberOf4BitArgumentRegisters = computeNumberOf4BitArgumentRegisters();
    return numberOf4BitArgumentRegisters > 0;
  }

  private int computeNumberOf4BitArgumentRegisters() {
    int numberOf4BitArgumentRegisters = 0;
    Iterator<Argument> argumentIterator = code.argumentIterator();
    int currentArgumentRegisterStart = registersUsed() - numberOfArgumentRegisters;
    while (argumentIterator.hasNext()) {
      Argument argument = argumentIterator.next();
      int requiredRegisters = argument.outValue().requiredRegisters();
      int nextArgumentRegisterStart = currentArgumentRegisterStart + requiredRegisters;
      int currentArgumentRegisterEnd = nextArgumentRegisterStart - 1;
      if (currentArgumentRegisterEnd <= Constants.U4BIT_MAX) {
        currentArgumentRegisterStart = nextArgumentRegisterStart;
        numberOf4BitArgumentRegisters += requiredRegisters;
      } else {
        if (currentArgumentRegisterStart <= Constants.U4BIT_MAX) {
          numberOf4BitArgumentRegisters++;
        }
        break;
      }
    }
    return numberOf4BitArgumentRegisters;
  }

  @Override
  public ProgramMethod getProgramMethod() {
    return code.context();
  }

  /**
   * Perform register allocation for the IRCode.
   */
  @Override
  public void allocateRegisters() {
    // There are no linked values prior to register allocation.
    assert code.isConsistentSSA(appView);
    if (this.code.method().accessFlags.isBridge() && implementationIsBridge(this.code)) {
      transformBridgeMethod();
    }
    timing.begin("Setup");
    computeNeedsRegister();
    constrainArgumentIntervals();
    insertRangeInvokeMoves();
    ImmutableList<BasicBlock> blocks = computeLivenessInformation();
    timing.end();
    timing.begin("Allocate");
    performAllocation();
    timing.end();
    assert code.isConsistentGraph(appView);
    assert mode.is4Bit() || registersUsed() == 0 || unusedRegisters != null;
    // Even if the method is reachability sensitive, we do not compute debug information after
    // register allocation. We just treat the method as being in debug mode in order to keep
    // locals alive for their entire live range. In release mode the liveness is all that matters
    // and we do not actually want locals information in the output.
    if (options().debug) {
      computeDebugInfo(code, blocks, liveIntervals, this, liveAtEntrySets);
    } else if (code.context().isReachabilitySensitive()) {
      InstructionListIterator it = code.instructionListIterator();
      while (it.hasNext()) {
        Instruction instruction = it.next();
        if (instruction.isDebugLocalRead()) {
          it.remove();
        }
      }
    }
    clearUserInfo();
    clearState();
  }

  // TODO(b/270398965): Replace LinkedList.
  @SuppressWarnings("JdkObsolete")
  public static void computeDebugInfo(
      IRCode code,
      ImmutableList<BasicBlock> blocks,
      List<LiveIntervals> liveIntervals,
      RegisterAllocator allocator,
      Map<BasicBlock, LiveAtEntrySets> liveAtEntrySets) {
    // Collect live-ranges for all SSA values with local information.
    List<LocalRange> ranges = new ArrayList<>();
    for (LiveIntervals interval : liveIntervals) {
      Value value = interval.getValue();
      if (!value.hasLocalInfo()) {
        continue;
      }
      List<LiveRange> liveRanges = new ArrayList<>(interval.getRanges());
      for (LiveIntervals child : interval.getSplitChildren()) {
        assert child.getValue() == value;
        assert child.getSplitChildren() == null || child.getSplitChildren().isEmpty();
        liveRanges.addAll(child.getRanges());
      }
      liveRanges.sort(Comparator.comparingInt(r -> r.start));
      for (LiveRange liveRange : liveRanges) {
        int start = liveRange.start;
        int end = liveRange.end;
        ranges.add(
            new LocalRange(
                value, allocator.getArgumentOrAllocateRegisterForValue(value, start), start, end));
      }
    }
    if (ranges.isEmpty()) {
      return;
    }
    ranges.sort(LocalRange::compareTo);

    // At each instruction compute the changes to live locals.
    LinkedList<LocalRange> openRanges = new LinkedList<>();
    Iterator<LocalRange> rangeIterator = ranges.iterator();
    LocalRange nextStartingRange = rangeIterator.next();
    Int2ReferenceMap<DebugLocalInfo> ending = new Int2ReferenceOpenHashMap<>();
    Int2ReferenceMap<DebugLocalInfo> starting = new Int2ReferenceOpenHashMap<>();

    boolean isEntryBlock = true;
    for (BasicBlock block : blocks) {
      InstructionListIterator instructionIterator = block.listIterator();
      Set<Value> liveLocalValues =
          SetUtils.newIdentityHashSet(liveAtEntrySets.get(block).liveLocalValues);
      // Skip past arguments and open argument and phi locals.
      if (isEntryBlock) {
        isEntryBlock = false;
        assert block.getPhis().isEmpty();
        while (instructionIterator.hasNext()) {
          Instruction instruction = instructionIterator.next();
          if (!instruction.isArgument()) {
            break;
          }
          if (instruction.outValue().hasLocalInfo()) {
            liveLocalValues.add(instruction.outValue());
          }
        }
        instructionIterator.previous();
      } else {
        for (Phi phi : block.getPhis()) {
          if (phi.hasLocalInfo()) {
            liveLocalValues.add(phi);
          }
        }
      }
      // Skip past all spill moves to obtain the instruction number of the actual first instruction.
      instructionIterator.nextUntil(i -> !i.isMoveException() && !isSpillInstruction(i));
      Instruction firstInstruction = instructionIterator.previous();
      int firstIndex = firstInstruction.getNumber();

      // Close ranges up-to but excluding the first instruction. Ends are exclusive but the values
      // might be live upon entering the first instruction (if they are used by it). Since we
      // skipped move-exception this closes locals at the move exception which should close as part
      // of the exceptional transfer.
      openRanges.removeIf(
          openRange ->
              !liveLocalValues.contains(openRange.value)
                  || !isLocalLiveAtInstruction(firstInstruction, openRange));

      // Open ranges up-to but excluding the first instruction. Starts are inclusive but entry is
      // prior to the first instruction.
      while (nextStartingRange != null && nextStartingRange.start < firstIndex) {
        // If the range is live at this index open it. Again the end is inclusive here because the
        // instruction is live at block entry if it is live at entry to the first instruction.
        if (liveLocalValues.contains(nextStartingRange.value)
            && isLocalLiveAtInstruction(firstInstruction, nextStartingRange)) {
          openRanges.add(nextStartingRange);
        }
        nextStartingRange = rangeIterator.hasNext() ? rangeIterator.next() : null;
      }

      // Initialize current locals (registers after any spill instructions).
      Int2ReferenceMap<DebugLocalInfo> currentLocals =
          new Int2ReferenceOpenHashMap<>(openRanges.size());
      for (LocalRange openRange : openRanges) {
        if (liveLocalValues.contains(openRange.value)) {
          currentLocals.put(openRange.register, openRange.local);
        }
      }

      // Set locals at entry. This will adjust initial local registers in case of spilling.
      setLocalsAtEntry(block, instructionIterator, openRanges, currentLocals, allocator);

      // Iterate the block instructions and emit locals changed events.
      while (instructionIterator.hasNext()) {
        Instruction instruction = instructionIterator.next();
        if (!instructionIterator.hasNext()) {
          instruction.clearDebugValues();
          break;
        }

        if (!instruction.getDebugValues().isEmpty()) {
          for (Value endAnnotation : instruction.getDebugValues()) {
            ListIterator<LocalRange> it = openRanges.listIterator();
            while (it.hasNext()) {
              LocalRange openRange = it.next();
              if (openRange.value == endAnnotation) {
                // Don't remove the local from open-ranges as it is still technically open.
                assert openRange.local.isIdenticalTo(currentLocals.get(openRange.register));
                currentLocals.remove(openRange.register);
                ending.put(openRange.register, openRange.local);
                break;
              }
            }
          }
          // Remove the end markers now that local liveness is computed.
          instruction.clearDebugValues();
        }
        if (instruction.isDebugLocalRead()) {
          Instruction prev = instructionIterator.previous();
          assert prev == instruction;
          instructionIterator.remove();
        }

        Instruction nextInstruction = instructionIterator.peekNext();
        if (isSpillInstruction(nextInstruction)) {
          // No need to insert a DebugLocalsChange instruction before a spill instruction.
          continue;
        }
        int index = nextInstruction.getNumber();
        {
          ListIterator<LocalRange> it = openRanges.listIterator();
          while (it.hasNext()) {
            LocalRange openRange = it.next();
            // Close ranges up-to but excluding the first instruction.
            if (!isLocalLiveAtInstruction(nextInstruction, openRange)) {
              it.remove();
              // It may be that currentLocals does not contain this local because an explicit end
              // already closed the local.
              if (currentLocals.remove(openRange.register) != null) {
                ending.put(openRange.register, openRange.local);
              }
            }
          }
        }
        while (nextStartingRange != null && nextStartingRange.start < index) {
          // If the range is live at this index open it.
          if (isLocalLiveAtInstruction(nextInstruction, nextStartingRange)) {
            openRanges.add(nextStartingRange);
            assert !currentLocals.containsKey(nextStartingRange.register);
            currentLocals.put(nextStartingRange.register, nextStartingRange.local);
            starting.put(nextStartingRange.register, nextStartingRange.local);
          }
          nextStartingRange = rangeIterator.hasNext() ? rangeIterator.next() : null;
        }
        // Compute the final change in locals and insert it before nextInstruction.
        boolean localsChanged = !ending.isEmpty() || !starting.isEmpty();
        if (localsChanged) {
          DebugLocalsChange change =
              createLocalsChange(ending, starting, instruction.getPosition());
          if (change != null) {
            // Insert the DebugLocalsChange instruction before nextInstruction.
            instructionIterator.add(change);
          }
          // Create new maps for the next DebugLocalsChange instruction.
          ending = new Int2ReferenceOpenHashMap<>();
          starting = new Int2ReferenceOpenHashMap<>();
        }
      }
    }
  }

  private static boolean isLocalLiveAtInstruction(Instruction instruction, LocalRange range) {
    return isLocalLiveAtInstruction(instruction, range.start, range.end, range.value);
  }

  private static boolean isLocalLiveAtInstruction(
      Instruction instruction, int start, int end, Value value) {
    int number = instruction.getNumber();
    assert start < number;
    return number < end || (number == end && usesValue(value, instruction));
  }

  private static boolean usesValue(Value usedValue, Instruction instruction) {
    return valuesContain(usedValue, instruction.inValues())
        || valuesContain(usedValue, instruction.getDebugValues());
  }

  private static boolean valuesContain(Value value, Collection<Value> values) {
    for (Value other : values) {
      if (value == other) {
        return true;
      }
      if (value.isPhi()
          && other instanceof FixedLocalValue
          && ((FixedLocalValue) other).getPhi() == value) {
        return true;
      }
    }
    return false;
  }

  private static void setLocalsAtEntry(
      BasicBlock block,
      InstructionListIterator instructionIterator,
      List<LocalRange> openRanges,
      Int2ReferenceMap<DebugLocalInfo> finalLocals,
      RegisterAllocator allocator) {
    // If this is the graph-entry or there are no moves entry locals are current locals.
    if (block.getPredecessors().isEmpty() || block.entry() == instructionIterator.peekNext()) {
      assert !block.entry().isMoveException();
      assert !isSpillInstruction(block.entry());
      block.setLocalsAtEntry(new Int2ReferenceOpenHashMap<>(finalLocals));
      return;
    }
    // Otherwise entry locals are the registers of the predecessor, ie, prior to spill instructions.
    Int2ReferenceMap<DebugLocalInfo> initialLocals =
        new Int2ReferenceOpenHashMap<>(openRanges.size());
    int predecessorExitIndex =
        block.entry().isMoveException()
            ? block.getPredecessors().get(0).exceptionalExit().getNumber()
            : block.getPredecessors().get(0).exit().getNumber();
    for (LocalRange open : openRanges) {
      Value predecessorValue =
          open.value.isPhi() && open.value.getBlock() == block
              ? open.value.asPhi().getOperand(0)
              : open.value;
      int predecessorRegister =
          allocator.getArgumentOrAllocateRegisterForValue(predecessorValue, predecessorExitIndex);
      initialLocals.put(predecessorRegister, open.local);
    }
    block.setLocalsAtEntry(initialLocals);

    // Compute the final change in locals and insert it after the last spill instruction.
    Int2ReferenceMap<DebugLocalInfo> ending = new Int2ReferenceOpenHashMap<>();
    Int2ReferenceMap<DebugLocalInfo> starting = new Int2ReferenceOpenHashMap<>();
    for (Entry<DebugLocalInfo> initialLocal : initialLocals.int2ReferenceEntrySet()) {
      if (initialLocal.getValue().isNotIdenticalTo(finalLocals.get(initialLocal.getIntKey()))) {
        ending.put(initialLocal.getIntKey(), initialLocal.getValue());
      }
    }
    for (Entry<DebugLocalInfo> finalLocal : finalLocals.int2ReferenceEntrySet()) {
      if (finalLocal.getValue().isNotIdenticalTo(initialLocals.get(finalLocal.getIntKey()))) {
        starting.put(finalLocal.getIntKey(), finalLocal.getValue());
      }
    }
    DebugLocalsChange change = createLocalsChange(ending, starting, block.getPosition());
    if (change != null) {
      instructionIterator.add(change);
    }
  }

  private static DebugLocalsChange createLocalsChange(
      Int2ReferenceMap<DebugLocalInfo> ending,
      Int2ReferenceMap<DebugLocalInfo> starting,
      Position position) {
    assert position.isSome();
    if (ending.isEmpty() && starting.isEmpty()) {
      return null;
    }
    DebugLocalsChange localsChange;
    if (ending.isEmpty() || starting.isEmpty()) {
      localsChange = new DebugLocalsChange(ending, starting);
    } else {
      IntSet unneeded = new IntArraySet(Math.min(ending.size(), starting.size()));
      for (Entry<DebugLocalInfo> entry : ending.int2ReferenceEntrySet()) {
        if (entry.getValue().isIdenticalTo(starting.get(entry.getIntKey()))) {
          unneeded.add(entry.getIntKey());
        }
      }
      if (unneeded.size() == ending.size() && unneeded.size() == starting.size()) {
        return null;
      }
      IntIterator iterator = unneeded.iterator();
      while (iterator.hasNext()) {
        int key = iterator.nextInt();
        ending.remove(key);
        starting.remove(key);
      }
      localsChange = new DebugLocalsChange(ending, starting);
    }
    localsChange.setPosition(position);
    return localsChange;
  }

  private void clearState() {
    liveAtEntrySets = null;
    liveIntervals = null;
    active = null;
    inactive = null;
    unhandled = null;
    freeRegisters = null;
  }

  // Compute a table that for each register numbers contains the number of previous register
  // numbers that were unused. This table is then used to slide down the actual registers
  // used to fill the gaps.
  private void computeUnusedRegisters() {
    unusedRegisters = internalComputeUnusedRegisters();
  }

  private void recomputeUnusedRegisters() {
    int[] newUnusedRegisters = internalComputeUnusedRegisters();
    assert verifyNoUsesOfPreviouslyUnusedRegisters(newUnusedRegisters);
    unusedRegisters = newUnusedRegisters;
  }

  private int[] internalComputeUnusedRegisters() {
    if (mode.is4Bit() || registersUsed() == 0) {
      return null;
    }
    // Compute the table based on the set of used registers.
    IntSet usedRegisters = computeUsedRegisters();
    return computeUnusedRegistersFromUsedRegisters(usedRegisters);
  }

  private boolean verifyNoChangesToUnusedRegisters() {
    assert Arrays.equals(unusedRegisters, internalComputeUnusedRegisters());
    return true;
  }

  private boolean verifyNoUsesOfPreviouslyUnusedRegisters(int[] newUnusedRegisters) {
    // We only recompute the unused registers when at least one argument live intervals was unsplit,
    // thus we always compute a non-null unused registers result.
    assert unusedRegisters != null;
    assert newUnusedRegisters != null;
    assert unusedRegisters.length == newUnusedRegisters.length;
    int previousNumberOfUnusedRegisters = 0;
    int previousNumberOfNewUnusedRegisters = 0;
    for (int i = 0; i < unusedRegisters.length; i++) {
      boolean wasRegisterUnused = previousNumberOfUnusedRegisters != unusedRegisters[i];
      boolean isRegisterUnused = previousNumberOfNewUnusedRegisters != newUnusedRegisters[i];
      assert !wasRegisterUnused || isRegisterUnused;
      previousNumberOfUnusedRegisters = unusedRegisters[i];
      previousNumberOfNewUnusedRegisters = newUnusedRegisters[i];
    }
    return true;
  }

  private IntSet computeUsedRegisters() {
    // Compute the set of registers that is used based on all live intervals.
    IntSet usedRegisters = new IntOpenHashSet();
    for (LiveIntervals intervals : liveIntervals) {
      addRegisterIfUsed(usedRegisters, intervals);
      for (LiveIntervals childIntervals : intervals.getSplitChildren()) {
        addRegisterIfUsed(usedRegisters, childIntervals);
      }
    }
    // Additionally, we have used temporary registers for parallel move scheduling, those
    // are used as well.
    if (firstParallelMoveTemporary != NO_REGISTER) {
      for (int i = firstParallelMoveTemporary; i < maxRegisterNumber + 1; i++) {
        usedRegisters.add(i);
      }
    }
    return usedRegisters;
  }

  private static void addRegisterIfUsed(IntSet usedRegisters, LiveIntervals intervals) {
    if (!intervals.isSpilledAndRematerializable()) {
      for (int i = 0; i < intervals.requiredRegisters(); i++) {
        usedRegisters.add(intervals.getRegister() + i);
      }
    }
  }

  private int[] computeUnusedRegistersFromUsedRegisters(IntSet usedRegisters) {
    int firstLocalRegister = numberOfArgumentRegisters + getMoveExceptionOffsetForLocalRegisters();
    assert verifyRegistersBeforeFirstLocalRegisterAreUsed(firstLocalRegister, usedRegisters);
    int registersUsed = unadjustedRegistersUsed();
    int numberOfParallelMoveTemporaryRegisters =
        firstParallelMoveTemporary != NO_REGISTER ? registersUsed - firstParallelMoveTemporary : 0;
    int numberOfLocalRegisters =
        registersUsed - firstLocalRegister - numberOfParallelMoveTemporaryRegisters;
    int unused = 0;
    int[] unusedRegisters = new int[numberOfLocalRegisters];
    for (int i = 0; i < numberOfLocalRegisters; i++) {
      if (!usedRegisters.contains(firstLocalRegister + i)) {
        unused++;
      }
      unusedRegisters[i] = unused;
    }
    return unusedRegisters;
  }

  private static boolean verifyRegistersBeforeFirstLocalRegisterAreUsed(
      int firstLocalRegister, IntSet usedRegisters) {
    for (int i = 0; i < firstLocalRegister; i++) {
      assert usedRegisters.contains(i);
    }
    return true;
  }

  public int highestUsedRegister() {
    return registersUsed() - 1;
  }

  @Override
  public int registersUsed() {
    int numberOfRegister = maxRegisterNumber + 1;
    if (unusedRegisters != null) {
      return numberOfRegister - ArrayUtils.lastOrDefault(unusedRegisters, 0);
    }
    return numberOfRegister;
  }

  private int unadjustedRegistersUsed() {
    return maxRegisterNumber + 1;
  }

  @Override
  public int getRegisterForValue(Value value, int instructionNumber) {
    if (value.isFixedRegisterValue()) {
      return realRegisterNumberFromAllocated(value.asFixedRegisterValue().getRegister());
    }
    LiveIntervals intervals = value.getLiveIntervals();
    if (intervals == null) {
      throw new CompilationError(
          "Unexpected attempt to get register for a value without a register in method `"
              + code.context().toSourceString()
              + "`.",
          code.context().getOrigin());
    }
    if (intervals.hasSplits()) {
      intervals = intervals.getSplitCovering(instructionNumber);
    }
    return getRegisterForIntervals(intervals);
  }

  @Override
  public int getArgumentOrAllocateRegisterForValue(Value value, int instructionNumber) {
    if (isPinnedArgument(value)) {
      return getArgumentRegisterForValue(value);
    }
    return getRegisterForValue(value, instructionNumber);
  }

  @Override
  public int getArgumentRegisterForValue(Value value) {
    assert value.isArgument();
    return getRegisterForIntervals(value.getLiveIntervals().getSplitParent());
  }

  @Override
  public InternalOptions options() {
    return appView.options();
  }

  @Override
  public AppView<?> getAppView() {
    return appView;
  }

  private ImmutableList<BasicBlock> computeLivenessInformation() {
    ImmutableList<BasicBlock> blocks = code.numberInstructions();
    liveAtEntrySets = code.computeLiveAtEntrySets();
    computeLiveRanges();
    return blocks;
  }

  private void performAllocation() {
    // Will automatically continue to ALLOW_ARGUMENT_REUSE_U8BIT and ALLOW_ARGUMENT_REUSE_U16BIT,
    // if needed.
    int minimumRequiredRegisters = numberOfArgumentRegisters;
    ArgumentReuseMode initialMode =
        minimumRequiredRegisters <= Constants.U4BIT_MAX
            ? ArgumentReuseMode.ALLOW_ARGUMENT_REUSE_U4BIT
            : ArgumentReuseMode.ALLOW_ARGUMENT_REUSE_U8BIT;
    performAllocation(initialMode, false);
  }

  private ArgumentReuseMode retryAllocation(ArgumentReuseMode mode) {
    return performAllocation(mode, true);
  }

  private ArgumentReuseMode performAllocation(ArgumentReuseMode mode, boolean retry) {
    assert numberOf4BitArgumentRegisters == 0 || mode.is8BitRefinement();
    ArgumentReuseMode result = mode;
    this.mode = mode;
    timing.begin(mode.toString());
    timing.begin("Prepare");
    if (retry) {
      clearRegisterAssignments();
      removeSpillAndPhiMoves();
    }

    pinArgumentRegisters();
    timing.end();

    boolean succeeded = performLinearScan(mode);
    timing.end();
    if (succeeded) {
      InsertMovesResult insertMovesResult = insertMoves();

      // We only compute unused information for local registers (temporary registers for parallel
      // move scheduling and argument registers are never unused). Therefore, we can already compute
      // unused registers now. This can help lead to more aggressive argument unsplitting, since
      // this effectively lowers the real argument registers.
      computeUnusedRegisters();

      // After having finished move insertion (which can allocate temporary registers for parallel
      // move scheduling), we now know the final registers of the arguments. If we have moved some
      // arguments down to low registers, but the input argument register itself ended up being in a
      // low register, then we can avoid the move into a low register by just using the argument
      // register directly. This is achieved by updating the register assignment to argument split
      // live intervals.
      UnsplitArgumentsResult unsplitArgumentsResult = unsplitArguments();
      if (unsplitArgumentsResult != null) {
        // If any changes were made, we need to redo move insertion.
        insertMovesResult.revert();
        InsertMovesResult newInsertMovesResult = insertMoves();
        int iterations = 0;

        // In some cases, the new move insertion may lead to more temporary registers being used for
        // parallel move scheduling. This is rare (e.g., never happens when compiling JetNews). If
        // that happened, the argument registers are now in higher registers, meaning we may have
        // invalidated the argument unsplitting. We therefore (partially) revert the argument
        // unsplitting and redo move insertion.
        while (newInsertMovesResult.getNumberOfParallelMoveTemporaryRegisters()
            > insertMovesResult.getNumberOfParallelMoveTemporaryRegisters()) {
          assert iterations < 5;
          boolean changed = unsplitArgumentsResult.revertPartial();
          if (changed) {
            // We invalidated the unsplit arguments optimization (or some of it). Redo move
            // insertion and check again.
            insertMovesResult = newInsertMovesResult;
            insertMovesResult.revert();
            newInsertMovesResult = insertMoves();
          } else {
            // Although we used more parallel move temporary registers this did not invalidate the
            // unsplit arguments result.
            break;
          }
          iterations++;
        }
        if (unsplitArgumentsResult.isFullyReverted()) {
          assert verifyNoChangesToUnusedRegisters();
        } else {
          recomputeUnusedRegisters();
        }
      }
    } else {
      assert !mode.is16Bit();
    }

    switch (mode) {
      case ALLOW_ARGUMENT_REUSE_U4BIT:
        if (!succeeded
            || highestUsedRegister() > Constants.U4BIT_MAX
            || options().testing.alwaysUsePessimisticRegisterAllocation) {
          // Redo allocation in mode ALLOW_ARGUMENT_REUSE_U8BIT. This may in principle also fail.
          // It is extremely rare that a method will use more than 256 registers, though.
          result = retryAllocation(ArgumentReuseMode.ALLOW_ARGUMENT_REUSE_U8BIT);
        }
        break;

      case ALLOW_ARGUMENT_REUSE_U8BIT:
        if (highestUsedRegister() > Constants.U8BIT_MAX
            || options().getTestingOptions().alwaysUsePessimisticRegisterAllocation) {
          // Redo allocation in mode ALLOW_ARGUMENT_REUSE_U16BIT. This always succeed.
          unusedRegisters = null;
          result = retryAllocation(ArgumentReuseMode.ALLOW_ARGUMENT_REUSE_U16BIT);
          break;
        }

        if (retry8BitAllocationWith4BitArgumentRegisters()) {
          // Refine register allocation result using the knowledge that some of the argument
          // registers are 4 bit registers.
          unusedRegisters = null;
          result = retryAllocation(ArgumentReuseMode.ALLOW_ARGUMENT_REUSE_U8BIT_REFINEMENT);
        }
        break;

      case ALLOW_ARGUMENT_REUSE_U8BIT_REFINEMENT:
        if (highestUsedRegister() > Constants.U8BIT_MAX
            || numberOf4BitArgumentRegisters > computeNumberOf4BitArgumentRegisters()) {
          // Redo allocation in mode ALLOW_ARGUMENT_REUSE_U8BIT_RETRY. This always succeed.
          numberOf4BitArgumentRegisters = 0;
          unusedRegisters = null;
          result = retryAllocation(ArgumentReuseMode.ALLOW_ARGUMENT_REUSE_U8BIT_RETRY);
        }
        break;

      case ALLOW_ARGUMENT_REUSE_U8BIT_RETRY:
        assert highestUsedRegister() <= Constants.U8BIT_MAX;
        break;

      case ALLOW_ARGUMENT_REUSE_U16BIT:
        assert highestUsedRegister() <= Constants.U16BIT_MAX;
        break;
    }

    assert !result.is4Bit() || highestUsedRegister() <= Constants.U4BIT_MAX;
    assert !result.is8Bit() || highestUsedRegister() <= Constants.U8BIT_MAX;
    assert !result.is16Bit() || highestUsedRegister() <= Constants.U16BIT_MAX;

    new MoveSorter(code).sortMovesForSuffixSharing();

    return result;
  }

  // When argument register reuse is disallowed, we split argument values to make sure that
  // we can get the argument into low enough registers at uses that require low numbers. After
  // register allocation we can check if it is safe to just use the argument register itself
  // for all uses and thereby avoid moving argument values around.
  private UnsplitArgumentsResult unsplitArguments() {
    if (mode.is4Bit()) {
      return null;
    }
    Reference2IntMap<LiveIntervals> originalRegisterAssignment = new Reference2IntOpenHashMap<>();
    originalRegisterAssignment.defaultReturnValue(NO_REGISTER);
    for (LiveIntervals intervals : getArgumentLiveIntervals()) {
      int conservativeRealRegisterEnd = realRegisterNumberFromAllocated(intervals.getRegisterEnd());
      assert !mode.hasRegisterConstraint(intervals)
          || (mode.is8BitRefinement()
              && intervals.getRegisterEnd() < numberOf4BitArgumentRegisters);
      for (LiveIntervals child : intervals.getSplitChildren()) {
        if (!child.isInvokeRangeIntervals()
            && conservativeRealRegisterEnd <= child.getRegisterLimit()
            && child.getRegister() != intervals.getRegister()) {
          originalRegisterAssignment.put(child, child.getRegister());
          child.clearRegisterAssignment();
          child.setRegister(intervals.getRegister());
          // If the child could be spilled then we would need to unset it here + update
          // UnsplitArgumentsResult#revertPartial to account for this.
          assert !child.isSpilled();
        }
      }
    }
    if (!originalRegisterAssignment.isEmpty()) {
      return new UnsplitArgumentsResult(this, originalRegisterAssignment);
    }
    return null;
  }

  void removeSpillAndPhiMoves() {
    for (BasicBlock block : code.blocks) {
      InstructionListIterator it = block.listIterator();
      while (it.hasNext()) {
        Instruction instruction = it.next();
        if (isSpillInstruction(instruction)) {
          it.remove();
        }
      }
    }
  }

  void removeParallelMoveTemporaryRegisters() {
    if (firstParallelMoveTemporary != NO_REGISTER) {
      maxRegisterNumber = firstParallelMoveTemporary - 1;
      firstParallelMoveTemporary = NO_REGISTER;
    }
  }

  private static boolean isSpillInstruction(Instruction instruction) {
    Value outValue = instruction.outValue();
    if (outValue != null && outValue.isFixedRegisterValue()) {
      // Only move and const number instructions are inserted for spill and phi moves. The
      // const number instructions are for values that can be rematerialized instead of
      // spilled.
      assert instruction.getNumber() == -1;
      assert instruction.isMove() || instruction.isConstNumber();
      assert !instruction.isDebugInstruction();
      return true;
    }
    return false;
  }

  private void clearRegisterAssignments() {
    freeRegisters.clear();
    maxRegisterNumber = -1;
    active.clear();
    expiredHere.clear();
    firstParallelMoveTemporary = NO_REGISTER;
    inactive.clear();
    unhandled.clear();
    moveExceptionIntervals.clear();
    for (LiveIntervals intervals : liveIntervals) {
      intervals.undoSplits();
      if (intervals.hasRegister()) {
        intervals.setSpilled(false);
      }
      intervals.clearRegisterAssignment();
      intervals.unsetIsInvokeRangeIntervals();
    }
  }

  /**
   * Get the register allocated to a given set of live intervals.
   */
  private int getRegisterForIntervals(LiveIntervals intervals) {
    int intervalsRegister = intervals.getRegister();
    return realRegisterNumberFromAllocated(intervalsRegister);
  }

  int unadjustedRealRegisterFromAllocated(int allocated) {
    assert allocated != NO_REGISTER;
    assert allocated >= 0;
    if (isArgumentRegister(allocated)) {
      // For the |numberOfArguments| first registers map to the correct argument register.
      return maxRegisterNumber - (numberOfArgumentRegisters - allocated - 1);
    } else if (hasDedicatedMoveExceptionRegister()
        && isDedicatedMoveExceptionRegisterInLastLocalRegister()
        && allocated == getMoveExceptionRegister()) {
      // Move the move-exception register to be the highest local register. We only do this in 8 bit
      // register allocation since move-exception requires an 8 bit register.
      return maxRegisterNumber - numberOfArgumentRegisters;
    } else {
      // For everything else use the lower numbers.
      return allocated - numberOfArgumentRegisters - getMoveExceptionOffsetForLocalRegisters();
    }
  }

  int realRegisterNumberFromAllocated(int allocated) {
    int register = unadjustedRealRegisterFromAllocated(allocated);
    // Adjust for spill registers that turn out to be unused because the value can be
    // rematerialized instead of spilled.
    if (unusedRegisters != null) {
      if (register < unusedRegisters.length) {
        return register - unusedRegisters[register];
      }
      // This register is either:
      // - One of the temporary registers used for move scheduling.
      // - The dedicated move exception register (if it has been moved before the first argument).
      // - One of the argument registers.
      // In either case, the given register is after the last local register, so we subtract the
      // total number of unused local registers from the register.
      return register - ArrayUtils.lastOrDefault(unusedRegisters, 0);
    }
    return register;
  }

  private boolean performLinearScan(ArgumentReuseMode mode) {
    unhandled.addAll(liveIntervals);

    timing.begin("Prelude");
    processArgumentLiveIntervals();
    boolean hasInvokeRangeLiveIntervals = splitLiveIntervalsForInvokeRange();
    allocateRegistersForMoveExceptionIntervals(hasInvokeRangeLiveIntervals);
    timing.end();

    timing.begin("Argument linked");
    for (LiveIntervals argumentLiveIntervals : getArgumentLiveIntervals()) {
      allocateRegistersForInvokeRangeSplits(argumentLiveIntervals);
    }
    timing.end();

    // Go through each unhandled live interval and find a register for it.
    timing.begin("Process all unhandled");
    while (!unhandled.isEmpty()) {
      assert invariantsHold(mode);

      LiveIntervals unhandledInterval = unhandled.poll();
      if (unhandledInterval.isHandled()) {
        assert unhandledInterval.hasRegister();
        continue;
      }

      setHintForDestRegOfCheckCast(unhandledInterval);
      setHintToPromote2AddrInstruction(unhandledInterval);

      // If this interval value has an invoke/rangerange user, then fix the registers for the
      // consecutive arguments now and add hints to the live intervals leading up to this
      // invoke/range. This looks forward and propagate hints backwards to avoid many moves in
      // connection with ranged invokes.
      timing.begin("Linked");
      allocateRegistersForInvokeRangeSplits(unhandledInterval);
      timing.end();
      if (unhandledInterval.hasRegister()) {
        // The value itself is in the chain that has now gotten registers allocated.
        continue;
      }

      timing.begin("Advance state");
      advanceStateToLiveIntervals(unhandledInterval);
      timing.end();

      // Perform the actual allocation.
      timing.begin("Alloc single");
      if (!allocateSingleInterval(unhandledInterval)
          || maxRegisterNumber > mode.getMaxRegisterNumber()) {
        timing.end();
        timing.end();
        return false;
      }
      timing.end();
      expiredHere.clear();
    }
    timing.end();
    assert invariantsHold(mode);
    return true;
  }

  private void processArgumentLiveIntervals() {
    for (LiveIntervals argumentInterval : getArgumentLiveIntervals()) {
      assert argumentInterval.hasRegister();
      argumentInterval.setHandled();
      if (!mode.hasRegisterConstraint(argumentInterval)) {
        // All the argument intervals are active in the beginning and have preallocated registers.
        active.add(argumentInterval);
      } else if (mode.is8BitRefinement()
          && argumentInterval.getRegisterEnd() < numberOf4BitArgumentRegisters) {
        active.add(argumentInterval);
      } else {
        // Treat the argument interval as spilled which will require a load to a different
        // register for all register-constrained usages.
        inactive.add(argumentInterval);
        // Split argument live interval at its first constrained use.
        if (argumentInterval.getUses().size() > 1) {
          LiveIntervalsUse use = argumentInterval.firstUseWithConstraint();
          if (use != null) {
            LiveIntervals split;
            if (argumentInterval.numberOfUsesWithConstraint() == 1) {
              // If there is only one register-constrained use, split before that one use.
              split = argumentInterval.splitBefore(use.getPosition(), mode);
            } else {
              // If there are multiple register-constrained users, split right after the definition
              // to make it more likely that arguments get in usable registers from the start.
              // TODO(christofferqa): This is not great if there are many arguments with multiple
              // constrained uses, since we fill up all the low registers immediately, making it
              // likely that we will have to kick them back out before they are actually used.
              split =
                  argumentInterval.splitBefore(
                      argumentInterval.getValue().definition.getNumber() + 1, mode);
            }
            unhandled.add(split);
          }
        }
        // Since we are not activating the argument live intervals, we need to free their registers.
        freeOccupiedRegistersForIntervals(argumentInterval);
      }
    }
  }

  private void allocateRegistersForMoveExceptionIntervals(boolean hasInvokeRangeLiveIntervals) {
    // We have to be careful when it comes to the register allocated for a move exception
    // instruction. For move exception instructions there is no place to put spill or
    // restore moves. The move exception instruction has to be the first instruction in a
    // catch handler.
    //
    // When we allow argument reuse we do not allow any splitting, therefore we cannot get into
    // trouble with move exception registers. When argument reuse is disallowed we block a fixed
    // register to be used only by move exception instructions.
    if (mode.is4Bit() && !hasInvokeRangeLiveIntervals) {
      return;
    }
    // Force all move exception ranges to start out with the exception in a fixed register.
    for (BasicBlock block : code.blocks(block -> block.entry().isMoveException())) {
      MoveException moveException = block.entry().asMoveException();
      LiveIntervals intervals = moveException.outValue().getLiveIntervals();
      if (intervals.getValue().hasAnyUsers()) {
        LiveIntervals split = intervals.splitAfter(intervals.getValue().getDefinition(), mode);
        unhandled.add(split);
      }
      if (intervals.getStart() < moveException.getNumber()) {
        intervals = intervals.splitBefore(moveException, mode);
      } else {
        intervals.setHandled();
      }
      moveExceptionIntervals.add(intervals);
      intervals.setRegister(getMoveExceptionRegister());
    }
    if (hasDedicatedMoveExceptionRegister()) {
      int moveExceptionRegister = getMoveExceptionRegister();
      assert moveExceptionRegister == maxRegisterNumber + 1;
      increaseCapacity(moveExceptionRegister, false);
    }
  }

  private boolean splitLiveIntervalsForInvokeRange() {
    boolean hasInvokeRangeLiveIntervals = false;
    for (LiveIntervals intervals : liveIntervals) {
      Value value = intervals.getValue();
      for (Invoke invoke : value.<Invoke>uniqueUsers(this::needsInvokeRangeLiveIntervals)) {
        LiveIntervals overlappingIntervals = intervals.getSplitCovering(invoke.getNumber());
        LiveIntervals invokeRangeIntervals;
        if (overlappingIntervals.getStart() == toGapPosition(invoke.getNumber())) {
          invokeRangeIntervals = overlappingIntervals;
        } else {
          invokeRangeIntervals = overlappingIntervals.splitBefore(invoke, mode);
          unhandled.add(invokeRangeIntervals);
        }
        invokeRangeIntervals.setIsInvokeRangeIntervals(invoke);
        if (invoke.getNumber() + 1 < invokeRangeIntervals.getEnd()) {
          LiveIntervals successorIntervals = invokeRangeIntervals.splitAfter(invoke, mode);
          unhandled.add(successorIntervals);
        }
        hasInvokeRangeLiveIntervals = true;
      }
    }
    return hasInvokeRangeLiveIntervals;
  }

  private boolean needsInvokeRangeLiveIntervals(Instruction instruction) {
    Invoke invoke = instruction.asInvoke();
    if (invoke == null || invoke.requiredArgumentRegisters() <= 5) {
      return false;
    }
    if (argumentsAreAlreadyLinked(invoke)
        && Iterables.all(
            invoke.arguments(),
            argument -> isPinnedArgumentRegister(argument.getLiveIntervals()))) {
      return false;
    }
    return true;
  }

  private void advanceStateToLiveIntervals(LiveIntervals unhandledInterval) {
    int start = unhandledInterval.getStart();
    // Check for active intervals that expired or became inactive.
    Iterator<LiveIntervals> activeIterator = active.iterator();
    while (activeIterator.hasNext()) {
      LiveIntervals activeIntervals = activeIterator.next();
      if (start >= activeIntervals.getEnd()) {
        activeIterator.remove();
        freeOccupiedRegistersForIntervals(activeIntervals);
        if (start == activeIntervals.getEnd()) {
          expiredHere.add(activeIntervals.getRegister());
          if (activeIntervals.getType().isWide()) {
            expiredHere.add(activeIntervals.getRegister() + 1);
          }
        }
      } else if (!activeIntervals.overlapsPosition(start)) {
        activeIterator.remove();
        assert activeIntervals.hasRegister();
        inactive.add(activeIntervals);
        freeOccupiedRegistersForIntervals(activeIntervals);
      }
    }

    // Check for inactive intervals that expired or became reactivated.
    Iterator<LiveIntervals> inactiveIterator = inactive.iterator();
    while (inactiveIterator.hasNext()) {
      LiveIntervals inactiveIntervals = inactiveIterator.next();
      if (start >= inactiveIntervals.getEnd()) {
        inactiveIterator.remove();
        if (start == inactiveIntervals.getEnd()) {
          expiredHere.add(inactiveIntervals.getRegister());
          if (inactiveIntervals.getType().isWide()) {
            expiredHere.add(inactiveIntervals.getRegister() + 1);
          }
        }
      } else if (inactiveIntervals.overlapsPosition(start)) {
        inactiveIterator.remove();
        assert inactiveIntervals.hasRegister();
        active.add(inactiveIntervals);
        takeFreeRegistersForIntervals(inactiveIntervals);
      }
    }
  }

  private boolean invariantsHold(ArgumentReuseMode mode) {
    IntSortedSet computedFreeRegisters = new IntRBTreeSet();
    for (int register = 0; register <= maxRegisterNumber; ++register) {
      computedFreeRegisters.add(register);
    }
    for (LiveIntervals activeIntervals : active) {
      assert registersForIntervalsAreTaken(activeIntervals);
      activeIntervals.forEachRegister(
          register -> {
            assert computedFreeRegisters.contains(register);
            computedFreeRegisters.remove(register);
          });
    }
    // All active argument intervals that are pinned must be present in its original, incoming
    // argument register.
    for (LiveIntervals activeIntervals : active) {
      if (isPinnedArgumentRegister(activeIntervals)) {
        assert !mode.is4Bit() || activeIntervals.getValue().isThis();
        LiveIntervals parent = activeIntervals.getSplitParent();
        if (parent.getRegister() != activeIntervals.getRegister()) {
          parent.forEachRegister(
              register -> {
                assert computedFreeRegisters.contains(register);
                computedFreeRegisters.remove(register);
              });
        }
      }
    }
    if (hasDedicatedMoveExceptionRegister()) {
      // Relax the check, since it is not currently guaranteed that the move exception register is
      // occupied if-and-only-if there is an active live interval with the register.
      freeRegisters.remove(getMoveExceptionRegister());
      computedFreeRegisters.remove(getMoveExceptionRegister());
    }
    assert expiredHere.isEmpty();
    assert freeRegisters.equals(computedFreeRegisters);
    return true;
  }

  private boolean freePositionsAreConsistentWithFreeRegisters(
      RegisterPositions freePositions, int registerConstraint) {
    int n = Math.min(maxRegisterNumber, registerConstraint);
    for (int register = 0; register <= n; ++register) {
      if (!freePositions.isBlocked(register)) {
        assert freePositions.get(register) > 0;
        // If this register is free according to freePositions, then it should also be free
        // according to freeRegisters.
        boolean isMoveExceptionRegister =
            hasDedicatedMoveExceptionRegister() && register == getMoveExceptionRegister();
        if (!isMoveExceptionRegister) {
          assert freeRegisters.contains(register);
        }
      }
    }
    return true;
  }

  private boolean verifyRegisterAssignmentNotConflictingWithArgument(LiveIntervals interval) {
    assert interval.hasRegister();
    for (LiveIntervals argumentIntervals : getArgumentLiveIntervals()) {
      assert interval.getSplitParent() == argumentIntervals
          || !isPinnedArgumentRegister(argumentIntervals)
          || !interval.hasConflictingRegisters(argumentIntervals)
          || !argumentIntervals.anySplitOverlaps(interval);
    }
    return true;
  }

  private void setHintForDestRegOfCheckCast(LiveIntervals unhandledInterval) {
    if (unhandledInterval.hasHint()) {
      return;
    }
    Value value = unhandledInterval.getValue();
    if (value.isDefinedByInstructionSatisfying(Instruction::isCheckCast)) {
      CheckCast checkcast = value.getDefinition().asCheckCast();
      Value object = checkcast.object();
      if (!object.getLiveIntervals().overlaps(unhandledInterval)
          && object.hasSameLocalInfo(value)) {
        unhandledInterval.setHint(object.getLiveIntervals(), unhandled);
      }
    }
  }

  /*
   * This method tries to promote arithmetic binary instruction to use the 2Addr form.
   * To achieve this goal the output interval of the binary instruction is set with an hint
   * that is the left interval or the right interval if possible when intervals do not overlap.
   */
  private void setHintToPromote2AddrInstruction(LiveIntervals unhandledInterval) {
    if (unhandledInterval.hasHint()) {
      return;
    }
    Value value = unhandledInterval.getValue();
    if (value.isDefinedByInstructionSatisfying(Instruction::isArithmeticBinop)) {
      ArithmeticBinop binop = value.getDefinition().asArithmeticBinop();
      Value left = binop.leftValue();
      if (left.getLiveIntervals() != null && !left.getLiveIntervals().overlaps(unhandledInterval)) {
        unhandledInterval.setHint(left.getLiveIntervals(), unhandled);
      } else {
        Value right = binop.rightValue();
        if (binop.isCommutative()
            && right.getLiveIntervals() != null
            && !right.getLiveIntervals().overlaps(unhandledInterval)) {
          unhandledInterval.setHint(right.getLiveIntervals(), unhandled);
        }
      }
    }
  }

  /**
   * Perform look-ahead and allocate registers for linked argument chains that have the argument
   * interval as an argument move source.
   *
   * <p>The end result of calling this method is that the argument intervals have registers
   * allocated and have been moved from unhandled to inactive. The move sources have their hints
   * updated. The rest of the register allocation state is unchanged.
   */
  @SuppressWarnings("JdkObsolete")
  private void allocateRegistersForInvokeRangeSplits(LiveIntervals unhandledIntervals) {
    if (!unhandledIntervals.isSplitParent()) {
      return;
    }
    timing.begin("Extract splits");
    List<LiveIntervals> invokeRangeIntervals =
        ListUtils.filter(
            unhandledIntervals.getSplitChildren(),
            split -> split.isInvokeRangeIntervals() && !split.hasRegister());
    timing.end();
    timing.begin("Process splits");
    if (unhandledIntervals.isSplitParent() && unhandledIntervals.isInvokeRangeIntervals()) {
      allocateRegistersForInvokeRangeSplit(unhandledIntervals);
    }
    for (LiveIntervals split : invokeRangeIntervals) {
      allocateRegistersForInvokeRangeSplit(split);
    }
    timing.end();
  }

  private void allocateRegistersForInvokeRangeSplit(LiveIntervals split) {

    timing.begin("Extract list");
    Invoke invoke = split.getIsInvokeRangeIntervals();
    List<LiveIntervals> intervalsList =
        ListUtils.map(
            invoke.arguments(),
            invokeArgument -> {
              LiveIntervals overlappingInvokeArgumentIntervals =
                  invokeArgument.getLiveIntervals().getSplitCovering(invoke);
              assert !overlappingInvokeArgumentIntervals.hasRegister();
              assert overlappingInvokeArgumentIntervals.getStart() == invoke.getNumber() - 1;
              assert overlappingInvokeArgumentIntervals.getEnd() == invoke.getNumber()
                  || overlappingInvokeArgumentIntervals.getEnd() == invoke.getNumber() + 1;
              return overlappingInvokeArgumentIntervals;
            });
    timing.end();
    timing.begin("Prelude");

    // Save the current register allocation state so we can restore it at the end.
    timing.begin("Copy free registers");
    IntSortedSet savedFreeRegisters = new IntRBTreeSet(freeRegisters);
    int savedMaxRegisterNumber = maxRegisterNumber;
    timing.end();

    // Simulate adding all the active intervals to the inactive set by blocking their register if
    // they overlap with any of the invoke/range intervals.
    timing.begin("Overlaps active");
    for (LiveIntervals active : active) {
      // We could allow the use of all the currently active registers for the ranged invoke (by
      // adding the registers for all the active intervals to freeRegisters here). That could lead
      // to lower register pressure. However, it would also often mean that we cannot allocate the
      // right argument register to the current unhandled interval. Size measurements on GMSCore
      // indicate that blocking the current active registers works the best for code size.
      if (Iterables.any(intervalsList, active::overlaps)) {
        excludeRegistersForInterval(active);
      } else if (active.isArgumentInterval()) {
        // Allow the ranged invoke to use argument registers if free. This improves register
        // allocation for bridge methods that forwards all of their arguments after check-cast
        // checks on their types.
        freeOccupiedRegistersForIntervals(active);
      }
    }
    timing.end();

    timing.begin("Remove intervals from unhandled");
    intervalsList.forEach(LiveIntervals::setHandled);
    timing.end();
    timing.end();
    timing.begin("Allocate");
    allocateLinkedIntervals(intervalsList, invoke);
    timing.end();
    timing.begin("Postlude");
    // Restore the register allocation state.
    freeRegisters = savedFreeRegisters;
    // In case maxRegisterNumber has changed, update freeRegisters.
    for (int i = savedMaxRegisterNumber + 1; i <= maxRegisterNumber; i++) {
      freeRegisters.add(i);
    }
    // Move all the argument intervals to the inactive set.
    inactive.addAll(intervalsList);
    timing.end();
  }

  private void allocateLinkedIntervals(List<LiveIntervals> intervalsList, Invoke invoke) {
    LiveIntervals start = ListUtils.first(intervalsList);

    timing.begin("Prelude");
    boolean consecutiveArguments =
        IterableUtils.allWithPrevious(
            intervalsList,
            (current, previous) ->
                previous == null
                    || current.getSplitParent().getPreviousConsecutive()
                        == previous.getSplitParent());
    boolean consecutivePinnedArguments =
        consecutiveArguments && Iterables.all(intervalsList, this::isPinnedArgumentRegister);
    timing.end();

    int nextRegister;
    if (consecutivePinnedArguments) {
      // We can use the arguments from their input registers.
      nextRegister = start.getSplitParent().getRegister();
    } else {
      // Ensure that there is a free register for the out value (or two consecutive registers if
      // wide).
      timing.begin("Not consecutive pinned args");
      int numberOfRegisters = getNumberOfRequiredRegisters(intervalsList);
      int numberOfOutRegisters = invoke.hasOutValue() ? invoke.outValue().requiredRegisters() : 0;
      if (numberOfOutRegisters > 0
          && numberOfRegisters + numberOfOutRegisters - 1 > Constants.U4BIT_MAX) {
        int firstLocalRegister = numberOfArgumentRegisters;
        if (hasDedicatedMoveExceptionRegister()
            && isDedicatedMoveExceptionRegisterInFirstLocalRegister()) {
          firstLocalRegister++;
        }
        ensureCapacity(firstLocalRegister + numberOfOutRegisters - 1);
        for (int i = 0; i < numberOfOutRegisters; i++) {
          freeRegisters.remove(firstLocalRegister + i);
        }
      }

      // Exclude the registers that overlap the start of one of the live ranges we are going to
      // assign registers to now.
      timing.begin("Overlaps inactive");
      for (LiveIntervals inactiveIntervals : inactive) {
        if (inactiveIntervals.isInvokeRangeIntervals()) {
          // This is the live intervals for another invoke-range, these can never overlap.
          assert !Iterables.any(intervalsList, inactiveIntervals::overlaps);
          continue;
        }
        // All of the invoke-range live intervals usually start at the same instruction number.
        if (inactiveIntervals.overlapsAnyInvokeRangeIntervals(intervalsList)) {
          excludeRegistersForInterval(inactiveIntervals);
        }
      }
      timing.end();

      timing.begin("Register range is free");
      if (consecutiveArguments
          && registerRangeIsFree(start.getSplitParent().getRegister(), numberOfRegisters)) {
        // For consecutive arguments we always to use the input argument registers, if they are
        // free.
        timing.end();
        nextRegister = start.getSplitParent().getRegister();
      } else {
        timing.end();
        // Exclude the pinned argument registers for which there exists a split that overlaps with
        // one of the inputs to the invoke-range instruction.
        timing.begin("Exclude pinned args");
        for (LiveIntervals argumentLiveIntervals : getArgumentLiveIntervals()) {
          if (isPinnedArgumentRegister(argumentLiveIntervals)
              && liveIntervalsOverlappingAnyOf(argumentLiveIntervals, intervalsList)) {
            excludeRegistersForInterval(argumentLiveIntervals);
          }
        }
        timing.end();
        // Exclude move exception register if the first interval overlaps a move exception interval.
        // It is not necessary to check the remaining consecutive intervals, since we always use
        // register 0 (after remapping) for the argument register.
        timing.begin("Exclude move exc");
        if (hasDedicatedMoveExceptionRegister()) {
          boolean canUseMoveExceptionRegisterForLinkedIntervals =
              isDedicatedMoveExceptionRegisterInFirstLocalRegister()
                  && (!start.isLiveAtMoveExceptionEntry() || !overlapsMoveExceptionInterval(start));
          if (!canUseMoveExceptionRegisterForLinkedIntervals) {
            freeRegisters.remove(getMoveExceptionRegister());
          }
        }
        timing.end();

        // Select registers.
        nextRegister = getFreeConsecutiveRegisters(numberOfRegisters);
      }
      timing.end();
    }

    // Assign registers.
    timing.begin("Assign regs");
    for (LiveIntervals current : intervalsList) {
      current.setRegister(nextRegister);
      assert verifyRegisterAssignmentNotConflictingWithArgument(current);
      nextRegister += current.requiredRegisters();
    }
    timing.end();
  }

  private int getNumberOfRequiredRegisters(List<LiveIntervals> intervalsList) {
    int requiredRegisters = 0;
    for (LiveIntervals intervals : intervalsList) {
      requiredRegisters += intervals.requiredRegisters();
    }
    return requiredRegisters;
  }

  // Returns true if intervals has a split, which overlaps with any of the live intervals in the
  // given list.
  private boolean liveIntervalsOverlappingAnyOf(
      LiveIntervals argumentLiveIntervals, List<LiveIntervals> intervalsList) {
    assert argumentLiveIntervals == argumentLiveIntervals.getSplitParent();
    for (LiveIntervals intervals : intervalsList) {
      if (intervals.getValue() == argumentLiveIntervals.getValue()) {
        return true;
      }
    }
    for (LiveIntervals split : argumentLiveIntervals.getSplitChildren()) {
      if (split.overlapsAnyInvokeRangeIntervals(intervalsList)) {
        return true;
      }
    }
    return false;
  }

  private int getNewSpillRegister(LiveIntervals intervals) {
    if (intervals.isArgumentInterval()) {
      // Arguments are always in the argument registers, so for arguments just use that register
      // for the unconstrained prefix. For everything else, get a spill register.
      return intervals.getSplitParent().getRegister();
    }

    int register = maxRegisterNumber + 1;
    increaseCapacity(maxRegisterNumber + intervals.requiredRegisters());
    return register;
  }

  private int getSpillRegister(LiveIntervals intervals, IntList excludedRegisters) {
    if (isPinnedArgumentRegister(intervals)) {
      // Arguments are always in the argument registers, so for arguments just use that register
      // for the unconstrained prefix. For everything else, get a spill register.
      return intervals.getSplitParent().getRegister();
    }

    IntSortedSet previousFreeRegisters = new IntRBTreeSet(freeRegisters);
    int previousMaxRegisterNumber = maxRegisterNumber;
    freeRegisters.removeAll(expiredHere);
    if (excludedRegisters != null) {
      freeRegisters.removeAll(excludedRegisters);
    }

    // Check if we can use a register that was previously used as a register for intervals.
    // This could lead to fewer moves during resolution.
    int register = -1;
    for (LiveIntervals split : intervals.getSplitParent().getSplitChildren()) {
      int candidate = split.getRegister();
      if (candidate != NO_REGISTER
          && registersAreFreeAndConsecutive(candidate, intervals.getType().isWide())
          && maySpillLiveIntervalsToRegister(intervals, candidate, previousMaxRegisterNumber)) {
        register = candidate;
        break;
      }
    }

    if (register == -1) {
      do {
        // If the register needs to fit in 4 bits at the next use, then prioritize a small register.
        // If we can find a small register, we do not need to insert a move at the next use.
        boolean prioritizeSmallRegisters =
            !intervals.getUses().isEmpty()
                && intervals.getUses().first().getLimit() == Constants.U4BIT_MAX;
        register =
            getFreeConsecutiveRegisters(intervals.requiredRegisters(), prioritizeSmallRegisters);
      } while (!maySpillLiveIntervalsToRegister(intervals, register, previousMaxRegisterNumber));
    }

    // Going to spill to the register (pair).
    freeRegisters = previousFreeRegisters;
    // If getFreeConsecutiveRegisters had to increment |maxRegisterNumber|, we need to update
    // freeRegisters.
    for (int i = previousMaxRegisterNumber + 1; i <= maxRegisterNumber; ++i) {
      freeRegisters.add(i);
    }
    assert registersAreFree(register, intervals.getType().isWide());
    return register;
  }

  private boolean maySpillLiveIntervalsToRegister(
      LiveIntervals intervals, int register, int previousMaxRegisterNumber) {
    if (register > previousMaxRegisterNumber) {
      // Nothing can prevent us from spilling to an entirely fresh register.
      return true;
    }

    // If we are about to spill to an argument register, we need to be careful that the live range
    // that is being spilled does not overlap with the live range of the corresponding argument.
    //
    // Note that this is *not* guaranteed when overlapsInactiveIntervals is null, because it is
    // possible that some live ranges of the argument are still in the unhandled set.
    if (isArgumentRegister(register)) {
      // Find the first argument value that uses the given register.
      LiveIntervals argumentLiveIntervals = firstArgumentValue.getLiveIntervals();
      while (!argumentLiveIntervals.usesRegister(register, intervals.getType().isWide())) {
        argumentLiveIntervals = argumentLiveIntervals.getNextConsecutive();
        assert argumentLiveIntervals != null;
      }
      do {
        if (argumentLiveIntervals.anySplitOverlaps(intervals)) {
          // Remove so that next invocation of getFreeConsecutiveRegisters does not consider this.
          freeRegisters.remove(register);
          // We have just established that there is an overlap between the live range of the
          // current argument and the live range we need to find a register for. Therefore, if
          // the argument is wide, and the current register corresponds to the low register of the
          // argument, we know that the subsequent register will not work either.
          if (register == argumentLiveIntervals.getRegister()
              && argumentLiveIntervals.getType().isWide()) {
            freeRegisters.remove(register + 1);
          }
          return false;
        }
        // The next argument live interval may also use the register, if it is a wide register pair.
        argumentLiveIntervals = argumentLiveIntervals.getNextConsecutive();
      } while (argumentLiveIntervals != null
          && argumentLiveIntervals.usesRegister(register, intervals.getType().isWide()));
    }

    // Check for overlap with inactive intervals.
    LiveIntervals overlapsInactiveIntervals = null;
    for (LiveIntervals inactiveIntervals : inactive) {
      if (inactiveIntervals.usesRegister(register, intervals.getType().isWide())
          && intervals.overlaps(inactiveIntervals)) {
        overlapsInactiveIntervals = inactiveIntervals;
        break;
      }
    }
    if (overlapsInactiveIntervals != null) {
      // Remove so that next invocation of getFreeConsecutiveRegisters does not consider this.
      freeRegisters.remove(register);
      if (register == overlapsInactiveIntervals.getRegister()
          && overlapsInactiveIntervals.getType().isWide()) {
        freeRegisters.remove(register + 1);
      }
      return false;
    }

    // Check for overlap with the move exception interval.
    boolean overlapsMoveExceptionInterval =
        intervals.isLiveAtMoveExceptionEntry()
            && hasDedicatedMoveExceptionRegister()
            && (register == getMoveExceptionRegister()
                || (intervals.getType().isWide() && register + 1 == getMoveExceptionRegister()))
            && overlapsMoveExceptionInterval(intervals);
    if (overlapsMoveExceptionInterval) {
      // Remove so that next invocation of getFreeConsecutiveRegisters does not consider this.
      freeRegisters.remove(register);
      return false;
    }

    return true;
  }

  private int toInstructionPosition(int position) {
    return position % 2 == 0 ? position : position + 1;
  }

  private int toGapPosition(int position) {
    return position % 2 == 1 ? position : position - 1;
  }

  // Art had a bug (b/68761724) for Android N and O in the arm32 interpreter
  // where an aget-wide instruction using the same register for the array
  // and the first register of the result could lead to the wrong exception
  // being thrown on out of bounds.
  //
  // For instructions of the form 'aget-wide regA, regA, regB' where
  // regB is out of bounds of non-null array in regA, Art would throw a null
  // pointer exception instead of an ArrayIndexOutOfBounds exception.
  //
  // We work around that bug by disallowing aget-wide with the same array
  // and result register.
  private boolean needsArrayGetWideWorkaround(LiveIntervals intervals) {
    if (options().canUseSameArrayAndResultRegisterInArrayGetWide()) {
      return false;
    }
    if (intervals.requiredRegisters() == 1) {
      // Not the live range for a wide value and therefore not the output of aget-wide.
      return false;
    }
    if (intervals.getValue().isPhi()) {
      // If this writes a new register pair it will be via a move and not an aget-wide operation.
      return false;
    }
    if (intervals.getSplitParent() != intervals) {
      // This is a split of a parent interval and therefore if this leads to a write of a
      // register pair it will be via a move and not an aget-wide operation.
      return false;
    }
    Instruction definition = intervals.getValue().definition;
    return definition.isArrayGet() && definition.asArrayGet().outType().isWide();
  }

  // Is the array-get array register the same as the first register we are
  // allocating for the result?
  private boolean isArrayGetArrayRegister(int register, LiveIntervals intervals) {
    assert needsArrayGetWideWorkaround(intervals);
    Value array = intervals.getValue().definition.asArrayGet().array();
    int arrayReg =
        array.getLiveIntervals().getSplitCovering(intervals.getStart()).getRegister();
    assert arrayReg != NO_REGISTER;
    return arrayReg == register;
  }

  private boolean needsSingleResultOverlappingLongOperandsWorkaround(LiveIntervals intervals) {
    if (!options().canHaveCmpLongBug() && !options().canHaveLongToIntBug()) {
      return false;
    }
    if (intervals.requiredRegisters() == 2) {
      // Not the live range for a single value and therefore not the output of cmp-long.
      return false;
    }
    if (intervals.getValue().isPhi()) {
      // If this writes a new register pair it will be via a move and not an cmp-long operation.
      return false;
    }
    if (intervals.getSplitParent() != intervals) {
      // This is a split of a parent interval and therefore if this leads to a write of a
      // register it will be via a move and not an cmp-long operation.
      return false;
    }
    Instruction definition = intervals.getValue().definition;
    if (definition.isCmp()) {
      return definition.inValues().get(0).outType().isWide();
    }
    return definition.isNumberConversion()
        && definition.asNumberConversion().isLongToIntConversion();
  }

  private boolean singleOverlappingLong(int register1, int register2) {
    return register1 == register2 || register1 == (register2 + 1);
  }

  // Is one of the cmp-long argument registers the same as the register we are
  // allocating for the result?
  private boolean isSingleResultOverlappingLongOperands(int register, LiveIntervals intervals) {
    assert needsSingleResultOverlappingLongOperandsWorkaround(intervals);
    if (intervals.getValue().definition.isCmp()) {
      Value left = intervals.getValue().definition.asCmp().leftValue();
      Value right = intervals.getValue().definition.asCmp().rightValue();
      int leftReg =
          left.getLiveIntervals().getSplitCovering(intervals.getStart()).getRegister();
      int rightReg =
          right.getLiveIntervals().getSplitCovering(intervals.getStart()).getRegister();
      assert leftReg != NO_REGISTER;
      assert rightReg != NO_REGISTER;
      return singleOverlappingLong(register, leftReg) || singleOverlappingLong(register, rightReg);
    } else {
      assert intervals.getValue().definition.isNumberConversion();
      Value inputValue = intervals.getValue().definition.asNumberConversion().inValues().get(0);
      int inputReg
          = inputValue.getLiveIntervals().getSplitCovering(intervals.getStart()).getRegister();
      return register == inputReg;
    }
  }

  // The dalvik jit had a bug where the long operations add, sub, or, xor and and would write
  // the first part of the result long before reading the second part of the input longs.
  //
  // Therefore, on dalvik, we cannot generate code with overlapping long registers such as:
  //
  // add-long v3, v0, v2
  //
  // Dalvik would add v0 and v2 and write that to v3. It would then read v1 and v3 and produce
  // the wrong result.
  private boolean needsLongResultOverlappingLongOperandsWorkaround(LiveIntervals intervals) {
    if (!options().canHaveOverlappingLongRegisterBug()) {
      return false;
    }
    if (intervals.requiredRegisters() == 1) {
      // Not the live range for a wide value.
      return false;
    }
    if (intervals.getValue().isPhi()) {
      // If this writes a new register pair it will be via a move and not a long operation.
      return false;
    }
    if (intervals.getSplitParent() != intervals) {
      // This is a split of the parent interval and therefore if this leads to a write of a
      // register pair it will be via a move and not a long operation.
      return false;
    }
    Instruction definition = intervals.getValue().definition;
    if (definition.isArithmeticBinop() &&
        definition.asArithmeticBinop().getNumericType() == NumericType.LONG) {
      return definition instanceof Add || definition instanceof Sub;
    }
    if (definition.isLogicalBinop() &&
        definition.asLogicalBinop().getNumericType() == NumericType.LONG) {
      return definition instanceof Or || definition instanceof Xor || definition instanceof And;
    }
    return false;
  }

  // Check if the two longs are half-overlapping, that is first register of one is the second
  // register of the other.
  private boolean longHalfOverlappingLong(int register1, int register2) {
    return register1 == (register2 + 1) || (register1 + 1) == register2;
  }

  private boolean isLongResultOverlappingLongOperands(
      int register, LiveIntervals unhandledInterval) {
    assert needsLongResultOverlappingLongOperandsWorkaround(unhandledInterval);
    Value left = unhandledInterval.getValue().definition.asBinop().leftValue();
    Value right = unhandledInterval.getValue().definition.asBinop().rightValue();
    int leftReg =
        left.getLiveIntervals().getSplitCovering(unhandledInterval.getStart()).getRegister();
    int rightReg =
        right.getLiveIntervals().getSplitCovering(unhandledInterval.getStart()).getRegister();
    assert leftReg != NO_REGISTER && rightReg != NO_REGISTER;
    // The dalvik bug is actually only for overlap with the second operand, For now we
    // make sure that there is no overlap with either register of either operand. Some vendor
    // optimization have bees seen to need this more conservative check.
    return longHalfOverlappingLong(register, leftReg)
        || longHalfOverlappingLong(register, rightReg);
  }

  // Intervals overlap a move exception interval if one of the splits of the intervals does.
  // Since spill and restore moves are always put after the move exception we cannot give
  // a non-move exception interval the same register as a move exception instruction.
  //
  // For example:
  //
  // B0:
  //   const v0, 0
  //   invoke throwing_method v0 (catch handler B2)
  //   goto B1
  // B1:
  //   ...
  // B2:
  //   move-exception v1
  //   invoke method v0
  //   return
  //
  // During register allocation we could split the const number intervals into multiple
  // parts. We have to avoid assigning the same register to v1 and and v0 in B0 even
  // if v0 has a different register in B2. That is because the spill/restore move when
  // transitioning from B0 to B2 has to be after the move-exception instruction.
  //
  // Assuming that v0 has register 0 in B0 and register 4 in B2 and v1 has register 0 in B2
  // we would generate the following incorrect code:
  //
  // B0:
  //   const r0, 0
  //   invoke throwing_method r0 (catch handler B2)
  //   goto B1
  // B1:
  //   ...
  // B2:
  //   move-exception r0
  //   move r4, r0  // Whoops.
  //   invoke method r4
  //   return
  private boolean overlapsMoveExceptionInterval(LiveIntervals intervals) {
    if (!hasDedicatedMoveExceptionRegister()) {
      return false;
    }
    // If there are that many move exception intervals we don't spent the time
    // going through them all. In that case it is unlikely that we can reuse the move exception
    // register in any case.
    if (moveExceptionIntervals.size() > EXCEPTION_INTERVALS_OVERLAP_CUTOFF) {
      return true;
    }
    for (LiveIntervals moveExceptionInterval : moveExceptionIntervals) {
      if (intervals.anySplitOverlaps(moveExceptionInterval)) {
        return true;
      }
    }
    return false;
  }

  private boolean allocateSingleInterval(LiveIntervals unhandledInterval) {
    int registerConstraint = unhandledInterval.getRegisterLimit();
    assert registerConstraint <= Constants.U16BIT_MAX;

    assert unhandledInterval.requiredRegisters() <= 2;
    boolean needsRegisterPair = unhandledInterval.requiredRegisters() == 2;

    // Just use the argument register if an argument split has no register constraint. That will
    // avoid move generation for the argument.
    if (isPinnedArgumentRegister(unhandledInterval)) {
      if (registerConstraint == Constants.U16BIT_MAX
          || (mode.is8Bit() && registerConstraint == Constants.U8BIT_MAX)) {
        int argumentRegister = unhandledInterval.getSplitParent().getRegister();
        assignFreeRegisterToUnhandledInterval(unhandledInterval, argumentRegister);
        return true;
      }
    }

    if (!mode.is4Bit() && registerConstraint < Constants.U16BIT_MAX) {
      // Since we swap the argument registers and the temporary registers after register allocation,
      // we can allow the use of number of arguments more registers.
      registerConstraint += numberOfArgumentRegisters;
      // If we swap the locals and the dedicated move exception register we can allow the use of
      // one additional register.
      registerConstraint += getMoveExceptionOffsetForLocalRegisters();
    }

    RegisterPositions freePositions = computeFreePositions(unhandledInterval, registerConstraint);
    assert freePositionsAreConsistentWithFreeRegisters(freePositions, registerConstraint);

    // Attempt to use register hints.
    timing.begin("Try hint");
    if (useRegisterHint(unhandledInterval, registerConstraint, freePositions)) {
      timing.end();
      return true;
    }
    timing.end();

    // Get the register (pair) that is free the longest. That is the register with the largest
    // free position.
    int candidate =
        getLargestValidCandidate(
            unhandledInterval,
            registerConstraint,
            needsRegisterPair,
            freePositions,
            RegisterType.ANY);

    // It is not always possible to find a largest valid candidate. If none of the usable register
    // are free we typically get the last candidate. However, if that candidate has to be
    // discarded in order to workaround bugs we get REGISTER_CANDIDATE_NOT_FOUND. In both cases
    // we need to spill a valid candidate. That path is triggered when largestFreePosition is 0.
    int largestFreePosition = 0;
    if (candidate != REGISTER_CANDIDATE_NOT_FOUND) {
      largestFreePosition = freePositions.get(candidate, unhandledInterval.isWide());
    }

    // Determine what to do based on how long the selected candidate is free.
    if (largestFreePosition == 0) {
      // Not free. We need to spill.
      if (mode == ArgumentReuseMode.ALLOW_ARGUMENT_REUSE_U4BIT) {
        // No spilling is allowed when we allow argument reuse. Bailout and start over with
        // argument reuse disallowed.
        return false;
      }
      // If the first use for these intervals is unconstrained, just spill this interval instead
      // of finding another candidate to spill via allocateBlockedRegister.
      assert unhandledInterval.hasUses();
      if (!unhandledInterval.getUses().first().hasConstraint()) {
        if (mode.hasRegisterConstraint(unhandledInterval)) {
          int nextConstrainedPosition =
              unhandledInterval.firstUseWithConstraint(mode).getPosition();
          int register = getSpillRegister(unhandledInterval, null);
          LiveIntervals split = unhandledInterval.splitBefore(nextConstrainedPosition, mode);
          assignFreeRegisterToUnhandledInterval(unhandledInterval, register);
          unhandled.add(split);
        } else {
          assert unhandledInterval.firstUseWithConstraint(mode) == null;
          int register = getSpillRegister(unhandledInterval, null);
          assignFreeRegisterToUnhandledInterval(unhandledInterval, register);
        }
      } else {
        allocateBlockedRegister(unhandledInterval, registerConstraint);
      }
    } else {
      // We will use the candidate register(s) for unhandledInterval, and therefore potentially
      // need to adjust maxRegisterNumber.
      int candidateEnd = candidate + unhandledInterval.requiredRegisters() - 1;
      if (largestFreePosition >= unhandledInterval.getEnd()) {
        // Free for the entire interval. Allocate the register.
        ensureCapacity(candidateEnd);
        assignFreeRegisterToUnhandledInterval(unhandledInterval, candidate);
      } else if (mode == ArgumentReuseMode.ALLOW_ARGUMENT_REUSE_U4BIT) {
        // No splitting is allowed when we allow argument reuse. Bailout and start over with
        // argument reuse disallowed.
        return false;
      } else {
        // The candidate is free for the beginning of an interval. We split the interval
        // and use the register for as long as we can.
        int registerConstraintBeforeSplit = unhandledInterval.getRegisterLimit();
        LiveIntervals split = unhandledInterval.splitBefore(largestFreePosition, mode);
        assert split != unhandledInterval;
        unhandled.add(split);

        // After splitting the live intervals we may be able to find a more appropriate register
        // than the current candidate register. This is especially true if this is an argument that
        // is pinned in its incoming register, since if the live intervals is now unconstrained we
        // avoid a redundant move to a low register.
        if (unhandledInterval.getRegisterLimit() != registerConstraintBeforeSplit) {
          return allocateSingleInterval(unhandledInterval);
        }

        ensureCapacity(candidateEnd);
        assignFreeRegisterToUnhandledInterval(unhandledInterval, candidate);
      }
    }
    return true;
  }

  private RegisterPositions computeFreePositions(
      LiveIntervals unhandledInterval, int registerConstraint) {
    // Set all free positions for possible registers to max integer.
    RegisterPositions freePositions = new RegisterPositionsImpl(registerConstraint + 1);

    if (options().shouldCompileMethodInDebugMode(code.context())
        && !code.context().getAccessFlags().isStatic()) {
      // When compiling the method in debug mode we pin the this value register. The debugger
      // expects to be able to find it in the input register.
      assert numberOfArgumentRegisters > 0;
      assert firstArgumentValue != null && firstArgumentValue.requiredRegisters() == 1;
      freePositions.setBlocked(0);
    }

    if (mode.is4Bit()) {
      // We may block the receiver register.
      if (firstArgumentValue != null
          && isPinnedArgumentRegister(firstArgumentValue.getLiveIntervals())) {
        firstArgumentValue.getLiveIntervals().forEachRegister(freePositions::setBlocked);
      }
      // But not any of the other argument registers.
      for (LiveIntervals argumentIntervals : getArgumentLiveIntervals()) {
        assert !isPinnedArgumentRegister(argumentIntervals)
            || argumentIntervals.getValue().isThis();
      }
    } else {
      // Generally argument reuse is not allowed and we block all the argument registers so that
      // arguments are never free.
      //
      // When mode=ALLOW_ARGUMENT_REUSE_U8BIT_REFINEMENT we assume that some argument registers are
      // in 4 bits. If the current live intervals does not overlap with a 4 bit argument intervals
      // then we allow using that argument register for the current value.
      int i = 0;
      if (mode.is8BitRefinement()) {
        assert numberOf4BitArgumentRegisters > 0;
        int remainingNumberOf4BitArgumentRegisters = numberOf4BitArgumentRegisters;
        for (LiveIntervals argumentLiveIntervals : getArgumentLiveIntervals()) {
          int requiredRegisters = argumentLiveIntervals.requiredRegisters();
          remainingNumberOf4BitArgumentRegisters -= requiredRegisters;
          if (remainingNumberOf4BitArgumentRegisters < 0) {
            // Block all subsequent argument registers.
            break;
          }
          // Block this argument register if there is any overlap between the two live intervals.
          // TODO(b/374266460): Allow using the argument register even when there are overlapping
          //  live intervals.
          if (argumentLiveIntervals.anySplitOverlaps(unhandledInterval)) {
            for (int j = 0; j < requiredRegisters; j++) {
              freePositions.setBlocked(i + j);
            }
          }
          i += requiredRegisters;
        }
      }
      for (; isArgumentRegister(i) && i <= registerConstraint; i++) {
        freePositions.setBlocked(i);
      }
    }

    // If there is a move exception instruction we block register 0 as the move exception
    // register. If we cannot find a free valid register for the move exception value we have no
    // place to put a spill move (because the move exception instruction has to be the
    // first instruction in the handler block).
    if (hasDedicatedMoveExceptionRegister()) {
      if (!mode.is4Bit()
          && unhandledInterval.getRegisterLimit() == Constants.U4BIT_MAX
          && isDedicatedMoveExceptionRegisterInLastLocalRegister()) {
        freePositions.setBlocked(getMoveExceptionRegister());
      } else if (unhandledInterval.isLiveAtMoveExceptionEntry()
          && overlapsMoveExceptionInterval(unhandledInterval)) {
        int moveExceptionRegister = getMoveExceptionRegister();
        if (moveExceptionRegister <= registerConstraint) {
          freePositions.setBlocked(moveExceptionRegister);
        }
      }
    }

    // All the active intervals are not free at this point.
    for (LiveIntervals intervals : active) {
      int activeRegister = intervals.getRegister();
      if (activeRegister <= registerConstraint) {
        for (int i = 0; i < intervals.requiredRegisters(); i++) {
          if (activeRegister + i <= registerConstraint) {
            freePositions.setBlocked(activeRegister + i);
          }
        }
      }
    }

    // The register for inactive intervals that overlap with this interval are free until
    // the next overlap.
    for (LiveIntervals intervals : inactive) {
      int inactiveRegister = intervals.getRegister();
      if (inactiveRegister <= registerConstraint && unhandledInterval.overlaps(intervals)) {
        int nextOverlap = unhandledInterval.nextOverlap(intervals);
        for (int i = 0; i < intervals.requiredRegisters(); i++) {
          int register = inactiveRegister + i;
          if (register <= registerConstraint && !freePositions.isBlocked(register)) {
            int unhandledStart = toInstructionPosition(unhandledInterval.getStart());
            if (nextOverlap == unhandledStart) {
              // Don't use the register for an inactive interval that is only free until the next
              // instruction. We can get into this situation when unhandledInterval starts at a
              // gap position.
              freePositions.setBlocked(register);
            } else {
              if (nextOverlap < freePositions.get(register)) {
                freePositions.set(register, nextOverlap, intervals);
              }
            }
          }
        }
      }
    }
    return freePositions;
  }

  // Attempt to use the register hint for the unhandled interval in order to avoid generating
  // moves.
  private boolean useRegisterHint(
      LiveIntervals unhandledInterval, int registerConstraint, RegisterPositions freePositions) {
    // If the unhandled interval has a hint we give it that register if it is available without
    // spilling. For phis we also use the hint before looking at the operand registers. The
    // phi could have a hint from an argument moves which it seems more important to honor in
    // practice.
    IntSet triedHints = new IntArraySet();
    if (unhandledInterval.hasHint()
        && triedHints.add(unhandledInterval.getHint())
        && tryHint(
            unhandledInterval,
            registerConstraint,
            freePositions,
            unhandledInterval.getHint())) {
      return true;
    }

    LiveIntervals previousSplit = unhandledInterval.getPreviousSplit();
    if (previousSplit != null
        && triedHints.add(previousSplit.getRegister())
        && tryHint(
            unhandledInterval,
            registerConstraint,
            freePositions,
            previousSplit.getRegister())) {
      return true;
    }

    LiveIntervals nextSplit = unhandledInterval.getNextSplit();
    if (nextSplit != null && nextSplit.hasRegister()) {
      if (triedHints.add(nextSplit.getRegister())
          && tryHint(
              unhandledInterval, registerConstraint, freePositions, nextSplit.getRegister())) {
        return true;
      }
      if (freePositions.isBlocked(nextSplit.getRegister(), unhandledInterval.isWide())
          && tryAllocateBlockedHint(
              unhandledInterval, registerConstraint, nextSplit.getRegister())) {
        return true;
      }
    }

    // If there is no hint or it cannot be applied we search for a good register for phis using
    // the registers assigned to the operand intervals. We determine all the registers used
    // for operands and try them one by one based on frequency.
    Value value = unhandledInterval.getValue();
    if (value.isPhi()) {
      Phi phi = value.asPhi();
      Multiset<Integer> map = HashMultiset.create();
      List<Value> operands = phi.getOperands();
      for (int i = 0; i < operands.size(); i++) {
        LiveIntervals intervals = operands.get(i).getLiveIntervals();
        if (intervals.hasSplits()) {
          BasicBlock pred = phi.getBlock().getPredecessor(i);
          intervals = intervals.getSplitCovering(pred.exit().getNumber());
        }
        if (intervals.hasRegister()) {
          map.add(intervals.getRegister());
        }
      }
      for (Multiset.Entry<Integer> entry : Multisets.copyHighestCountFirst(map).entrySet()) {
        int register = entry.getElement();
        if (tryHint(unhandledInterval, registerConstraint, freePositions, register)) {
          return true;
        }
      }
    }

    return false;
  }

  // Attempt to allocate the hint register to the unhandled intervals.
  private boolean tryHint(
      LiveIntervals unhandledInterval,
      int registerConstraint,
      RegisterPositions freePositions,
      int register) {
    assert register != NO_REGISTER;
    int registerEnd = register + unhandledInterval.requiredRegisters() - 1;
    if (registerEnd > registerConstraint) {
      return false;
    }
    if (freePositions.isBlocked(register, unhandledInterval.isWide())) {
      return false;
    }
    int freePosition = freePositions.get(register, unhandledInterval.isWide());
    if (freePosition < unhandledInterval.getEnd()) {
      return false;
    }
    // Check for overlapping long registers issue.
    if (needsLongResultOverlappingLongOperandsWorkaround(unhandledInterval)
        && isLongResultOverlappingLongOperands(register, unhandledInterval)) {
      return false;
    }
    // Check for aget-wide bug in recent Art VMs.
    if (needsArrayGetWideWorkaround(unhandledInterval)
        && isArrayGetArrayRegister(register, unhandledInterval)) {
      return false;
    }
    assignFreeRegisterToUnhandledInterval(unhandledInterval, register);
    return true;
  }

  private boolean tryAllocateBlockedHint(
      LiveIntervals unhandledInterval, int registerConstraint, int candidate) {
    int registerEnd = candidate + unhandledInterval.requiredRegisters() - 1;
    if (registerEnd > registerConstraint) {
      return false;
    }
    if (needsArrayGetWideWorkaround(unhandledInterval)
        || needsLongResultOverlappingLongOperandsWorkaround(unhandledInterval)
        || needsSingleResultOverlappingLongOperandsWorkaround(unhandledInterval)) {
      return false;
    }
    if (unhandledInterval.isLiveAtMoveExceptionEntry()
        && isDedicatedMoveExceptionRegister(candidate)) {
      return false;
    }
    if (isArgumentRegister(candidate)) {
      for (LiveIntervals argumentLiveIntervals : getArgumentLiveIntervals()) {
        if (isPinnedArgumentRegister(argumentLiveIntervals)
            && argumentLiveIntervals.usesRegister(candidate, unhandledInterval.isWide())) {
          return false;
        }
      }
    }
    // Check if the current live intervals is blocked by an inactive (overlapping live intervals).
    if (!getLiveIntervalsWithRegister(
            inactive, unhandledInterval, candidate, unhandledInterval::overlaps)
        .isEmpty()) {
      return false;
    }
    // Find the value occupying the register of interest.
    Collection<LiveIntervals> blockingIntervals =
        getLiveIntervalsWithRegister(active, unhandledInterval, candidate);
    assert !blockingIntervals.isEmpty();
    for (LiveIntervals blockingInterval : blockingIntervals) {
      if (toInstructionPosition(blockingInterval.getStart())
          == toInstructionPosition(unhandledInterval.getStart())) {
        // TODO(b/302281605): Look into allowing this when the blocking interval starts at the same
        //  offset.
        return false;
      }
      if (hasConstrainedUseInRange(
          blockingInterval, unhandledInterval.getStart(), unhandledInterval.getEnd())) {
        return false;
      }
    }
    // TODO(b/302281605): Look into allowing this even when expiredHere is non-empty.
    if (!expiredHere.isEmpty()) {
      return false;
    }
    for (LiveIntervals blockingInterval : blockingIntervals) {
      LiveIntervals split = blockingInterval.splitBefore(unhandledInterval.getStart(), mode);
      freeOccupiedRegistersForIntervals(blockingInterval);
      active.remove(blockingInterval);
      unhandled.add(split);
    }
    assignFreeRegisterToUnhandledInterval(unhandledInterval, candidate);
    return true;
  }

  private static Collection<LiveIntervals> getLiveIntervalsWithRegister(
      List<LiveIntervals> intervalsList, LiveIntervals unhandledInterval, int register) {
    return getLiveIntervalsWithRegister(intervalsList, unhandledInterval, register, alwaysTrue());
  }

  private static Collection<LiveIntervals> getLiveIntervalsWithRegister(
      List<LiveIntervals> intervalsList,
      LiveIntervals unhandledInterval,
      int register,
      Predicate<LiveIntervals> predicate) {
    LiveIntervals intervalsWithRegister = null;
    boolean isWide = unhandledInterval.getType().isWide();
    for (LiveIntervals intervals : intervalsList) {
      if (!intervals.usesRegister(register, isWide) || !predicate.test(intervals)) {
        continue;
      }
      if (!isWide || intervals.usesBothRegisters(register, register + 1)) {
        return Collections.singleton(intervals);
      }
      if (intervalsWithRegister != null) {
        return ImmutableList.of(intervals, intervalsWithRegister);
      }
      intervalsWithRegister = intervals;
    }
    if (intervalsWithRegister != null) {
      return Collections.singleton(intervalsWithRegister);
    }
    return Collections.emptyList();
  }

  private boolean hasConstrainedUseInRange(LiveIntervals intervals, int start, int end) {
    for (LiveIntervalsUse use : intervals.getUses()) {
      if (use.hasConstraint(mode) && start < use.getPosition() && use.getPosition() < end) {
        return true;
      }
    }
    return false;
  }

  private void assignRegister(LiveIntervals intervals, int register) {
    assert register + intervals.requiredRegisters() - 1 <= maxRegisterNumber;
    intervals.setRegister(register);
    updateRegisterHints(intervals);
  }

  private void updateRegisterHints(LiveIntervals intervals) {
    Value value = intervals.getValue();
    // If the value flows into a phi, set the hint for all the operand splits that flow into the
    // phi and do not have hints yet.
    for (Phi phi : value.uniquePhiUsers()) {
      LiveIntervals phiIntervals = phi.getLiveIntervals();
      if (!phiIntervals.hasHint()) {
        phiIntervals.setHint(intervals, unhandled);
        for (int i = 0; i < phi.getOperands().size(); i++) {
          Value operand = phi.getOperand(i);
          LiveIntervals operandIntervals = operand.getLiveIntervals();
          BasicBlock pred = phi.getBlock().getPredecessors().get(i);
          operandIntervals = operandIntervals.getSplitCovering(pred.exit().getNumber());
          if (!operandIntervals.hasHint()) {
            operandIntervals.setHint(intervals, unhandled);
          }
        }
      }
    }
    // If the value is a phi and we are at the start of the interval, we set the register as
    // the hint for all of the operand splits flowing into the phi. We set the hint no matter
    // if there is already a hint. We know the register for the phi and want as many operands
    // as possible to be allocated the same register to avoid phi moves.
    if (value.isPhi() && intervals.getSplitParent() == intervals) {
      Phi phi = value.asPhi();
      BasicBlock block = phi.getBlock();
      for (int i = 0; i < phi.getOperands().size(); i++) {
        Value operand = phi.getOperand(i);
        BasicBlock pred = block.getPredecessors().get(i);
        LiveIntervals operandIntervals =
            operand.getLiveIntervals().getSplitCovering(pred.exit().getNumber());
        operandIntervals.setHint(intervals, unhandled);
      }
    }
  }

  private void assignFreeRegisterToUnhandledInterval(
      LiveIntervals unhandledInterval, int register) {
    assignRegister(unhandledInterval, register);
    takeFreeRegistersForIntervals(unhandledInterval);
    active.add(unhandledInterval);
  }

  private int getLargestCandidate(
      LiveIntervals unhandledInterval,
      int registerConstraint,
      RegisterPositions freePositions,
      boolean needsRegisterPair,
      RegisterType type) {
    int candidate = REGISTER_CANDIDATE_NOT_FOUND;
    int largest = -1;

    for (int i = 0; i <= registerConstraint; i++) {
      if (freePositions.isBlocked(i, needsRegisterPair) || !freePositions.hasType(i, type)) {
        continue;
      }
      if (needsRegisterPair) {
        if (i == numberOfArgumentRegisters - 1) {
          // The last register of the method is |i|, so we cannot use the pair (|i|, |i+1|).
          continue;
        }
        if (hasDedicatedMoveExceptionRegister()
            && isDedicatedMoveExceptionRegisterInLastLocalRegister()
            && i == getMoveExceptionRegister()) {
          // After register allocation we swap the dedicated move-exception register and all other
          // local registers, so we cannot use the pair (|i|, |i+1|).
          continue;
        }
        if (i >= registerConstraint) {
          break;
        }
      }
      int usePosition = freePositions.get(i, needsRegisterPair);
      if (unhandledInterval.hasUses() && usePosition == unhandledInterval.getFirstUse()) {
        // This register has a use at the same instruction as the value we are allocation a register
        // for. Find another register.
        continue;
      }
      if (usePosition > largest) {
        candidate = i;
        largest = usePosition;
        if (largest == Integer.MAX_VALUE) {
          break;
        }
      }
    }
    return candidate;
  }

  private int handleWorkaround(
      Predicate<LiveIntervals> workaroundNeeded,
      IntObjPredicate<LiveIntervals> workaroundNeededForCandidate,
      int candidate,
      LiveIntervals unhandledInterval,
      int registerConstraint,
      boolean needsRegisterPair,
      RegisterPositionsWithExtraBlockedRegisters freePositions,
      RegisterType type) {
    if (workaroundNeeded.test(unhandledInterval)) {
      int lastCandidate = candidate;
      while (workaroundNeededForCandidate.test(candidate, unhandledInterval)) {
        // Make the unusable register unavailable for allocation and try again.
        freePositions.setBlockedTemporarily(candidate);
        candidate =
            getLargestCandidate(
                unhandledInterval, registerConstraint, freePositions, needsRegisterPair, type);
        // If there are only invalid candidates of the give type we will end up with the same
        // candidate returned again once we have tried them all. In that case we didn't find a
        // valid register candidate and we need to broaden the search to other types.
        if (lastCandidate == candidate) {
          assert false
              : "Unexpected attempt to take blocked register "
                  + candidate
                  + " in "
                  + code.context().toSourceString();
          return REGISTER_CANDIDATE_NOT_FOUND;
        }
        // If we did not find a valid register, then give up, and broaden the search to other types.
        if (candidate == REGISTER_CANDIDATE_NOT_FOUND) {
          return candidate;
        }
        lastCandidate = candidate;
      }
    }
    return candidate;
  }

  private int getLargestValidCandidate(
      LiveIntervals unhandledInterval,
      int registerConstraint,
      boolean needsRegisterPair,
      RegisterPositions usePositions,
      RegisterType type) {
    int candidate =
        getLargestCandidate(
            unhandledInterval, registerConstraint, usePositions, needsRegisterPair, type);
    if (candidate == REGISTER_CANDIDATE_NOT_FOUND) {
      return candidate;
    }
    // Wrap the use positions such that registers blocked by the workarounds are only blocked until
    // the end of this method.
    RegisterPositionsWithExtraBlockedRegisters usePositionsWrapper =
        new RegisterPositionsWithExtraBlockedRegisters(usePositions);
    candidate =
        handleWorkaround(
            this::needsLongResultOverlappingLongOperandsWorkaround,
            this::isLongResultOverlappingLongOperands,
            candidate,
            unhandledInterval,
            registerConstraint,
            needsRegisterPair,
            usePositionsWrapper,
            type);
    candidate =
        handleWorkaround(
            this::needsSingleResultOverlappingLongOperandsWorkaround,
            this::isSingleResultOverlappingLongOperands,
            candidate,
            unhandledInterval,
            registerConstraint,
            needsRegisterPair,
            usePositionsWrapper,
            type);
    candidate =
        handleWorkaround(
            this::needsArrayGetWideWorkaround,
            this::isArrayGetArrayRegister,
            candidate,
            unhandledInterval,
            registerConstraint,
            needsRegisterPair,
            usePositionsWrapper,
            type);
    return candidate;
  }

  private void allocateBlockedRegister(LiveIntervals unhandledInterval, int registerConstraint) {
    // Initialize all candidate registers to Integer.MAX_VALUE.
    RegisterPositions usePositions = new RegisterPositionsImpl(registerConstraint + 1);
    RegisterPositions blockedPositions = new RegisterPositionsImpl(registerConstraint + 1);

    // Compute next use location for all currently active registers.
    for (LiveIntervals intervals : active) {
      int activeRegister = intervals.getRegister();
      if (activeRegister <= registerConstraint) {
        for (int i = 0; i < intervals.requiredRegisters(); i++) {
          if (activeRegister + i <= registerConstraint) {
            int unhandledStart = unhandledInterval.getStart();
            usePositions.set(
                activeRegister + i,
                intervals.firstUseWithConstraintAfter(unhandledStart, mode),
                intervals);
          }
        }
      }
    }

    // Compute next use location for all inactive registers that overlaps the unhandled interval.
    for (LiveIntervals intervals : inactive) {
      int inactiveRegister = intervals.getRegister();
      if (inactiveRegister <= registerConstraint && intervals.overlaps(unhandledInterval)) {
        for (int i = 0; i < intervals.requiredRegisters(); i++) {
          if (inactiveRegister + i <= registerConstraint) {
            int firstUse =
                intervals.firstUseWithConstraintAfter(unhandledInterval.getStart(), mode);
            if (firstUse < usePositions.get(inactiveRegister + i)) {
              usePositions.set(inactiveRegister + i, firstUse, intervals);
            }
          }
        }
      }
    }

    // Disallow the reuse of argument registers by always treating them as being used
    // at instruction number 0.
    for (int i = 0; isArgumentRegister(i); i++) {
      usePositions.setBlocked(i);
    }

    // Disallow reuse of the move exception register if we have reserved one.
    if (hasDedicatedMoveExceptionRegister()) {
      if (!mode.is4Bit()
          && unhandledInterval.getRegisterLimit() == Constants.U4BIT_MAX
          && isDedicatedMoveExceptionRegisterInLastLocalRegister()) {
        usePositions.setBlocked(getMoveExceptionRegister());
      } else if (overlapsMoveExceptionInterval(unhandledInterval)) {
        usePositions.setBlocked(getMoveExceptionRegister());
      }
    }

    // Treat active and inactive linked argument intervals as pinned. They cannot be given another
    // register at their uses.
    blockInvokeRangeIntervals(
        unhandledInterval, registerConstraint, usePositions, blockedPositions);

    // Get the register (pair) that has the highest use position.
    boolean needsRegisterPair = unhandledInterval.getType().isWide();

    // First look for a candidate that can be rematerialized.
    int candidate =
        getLargestValidCandidate(
            unhandledInterval,
            registerConstraint,
            needsRegisterPair,
            usePositions,
            RegisterType.CONST_NUMBER);
    // Look for a non-const, non-monitor candidate.
    int otherCandidate =
        getLargestValidCandidate(
            unhandledInterval,
            registerConstraint,
            needsRegisterPair,
            usePositions,
            RegisterType.OTHER);
    if (otherCandidate != REGISTER_CANDIDATE_NOT_FOUND) {
      // There is a potential other candidate, check if that should be used instead.
      if (candidate == REGISTER_CANDIDATE_NOT_FOUND) {
        candidate = otherCandidate;
      } else {
        int largestConstUsePosition =
            getLargestPosition(usePositions, candidate, needsRegisterPair);
        if (largestConstUsePosition - MIN_CONSTANT_FREE_FOR_POSITIONS
            < unhandledInterval.getStart()) {
          // The candidate that can be rematerialized has a live range too short to use it.
          candidate = otherCandidate;
        }
      }
    }

    // If looking at constants and non-monitor registers did not find a valid spill candidate
    // we allow ourselves to look at monitor spill candidates as well. Registers holding objects
    // used as monitors should not be spilled if we can avoid it. Spilling them can lead
    // to Art lock verification issues.
    // Also, at this point we still don't allow splitting any string new-instance instructions
    // that have been explicitly blocked. Doing so could lead to a behavioral bug on some ART
    // runtimes (b/80118070). To remove this restriction, we would need to know when the call to
    // <init> has definitely happened, and would be safe to split the value after that point.
    if (candidate == REGISTER_CANDIDATE_NOT_FOUND) {
      candidate =
          getLargestValidCandidate(
              unhandledInterval,
              registerConstraint,
              needsRegisterPair,
              usePositions,
              RegisterType.MONITOR);
    }

    int largestUsePosition = getLargestPosition(usePositions, candidate, needsRegisterPair);
    int blockedPosition = getLargestPosition(blockedPositions, candidate, needsRegisterPair);

    if (largestUsePosition < unhandledInterval.getFirstUse()) {
      // All active and inactive intervals are used before current. Therefore, it is best to spill
      // current itself.
      int splitPosition = unhandledInterval.getFirstUse();
      LiveIntervals split = unhandledInterval.splitBefore(splitPosition, mode);
      assert split != unhandledInterval;
      // Experiments show that it has a positive impact on code size to use a fresh register here.
      int registerNumber = getNewSpillRegister(unhandledInterval);
      assignFreeRegisterToUnhandledInterval(unhandledInterval, registerNumber);
      unhandledInterval.setSpilled(true);
      unhandled.add(split);
    } else {
      // We will use the candidate register(s) for unhandledInterval, and therefore potentially
      // need to adjust maxRegisterNumber.
      int candidateEnd = candidate + unhandledInterval.requiredRegisters() - 1;
      if (candidateEnd > maxRegisterNumber) {
        increaseCapacity(candidateEnd);
      }

      if (blockedPosition > unhandledInterval.getEnd()) {
        // Spilling can make a register available for the entire interval.
        assignRegisterAndSpill(unhandledInterval, candidate);
      } else {
        // Spilling only makes a register available for the first part of current.
        LiveIntervals splitChild = unhandledInterval.splitBefore(blockedPosition, mode);
        unhandled.add(splitChild);
        assignRegisterAndSpill(unhandledInterval, candidate);
      }
    }
  }

  private int getLargestPosition(
      RegisterPositions positions, int register, boolean needsRegisterPair) {
    return positions.get(register, needsRegisterPair);
  }

  private void assignRegisterAndSpill(LiveIntervals unhandledInterval, int candidate) {
    // Split and spill intersecting active intervals for this register.
    if (atLeastOneOfRegistersAreTaken(candidate, unhandledInterval.isWide())) {
      spillOverlappingActiveIntervals(unhandledInterval, candidate, unhandledInterval.isWide());
    }
    // Now that that active intervals have been spilled, we are free to take the candidate.
    assignRegister(unhandledInterval, candidate);
    takeFreeRegistersForIntervals(unhandledInterval);
    active.add(unhandledInterval);
    // Split all overlapping inactive intervals for this register. They need to have a new
    // register assigned at the next use.
    splitOverlappingInactiveIntervals(unhandledInterval, candidate, unhandledInterval.isWide());
  }

  protected void splitOverlappingInactiveIntervals(
      LiveIntervals unhandledInterval, int candidate, boolean candidateIsWide) {
    Iterator<LiveIntervals> inactiveIterator = inactive.iterator();
    while (inactiveIterator.hasNext()) {
      LiveIntervals intervals = inactiveIterator.next();
      if (intervals.usesRegister(candidate, candidateIsWide)
          && intervals.overlaps(unhandledInterval)) {
        if (intervals.getStart() > unhandledInterval.getStart()) {
          // The inactive live intervals hasn't started yet. Clear the temporary register
          // assignment and move back to unhandled for register reassignment.
          intervals.clearRegisterAssignment();
          inactiveIterator.remove();
          unhandled.add(intervals);
        } else {
          // The inactive live intervals is in a live range hole. Split the interval and
          // put the ranges after the hole into the unhandled set for register reassignment.
          LiveIntervals split = intervals.splitBefore(unhandledInterval.getStart(), mode);
          unhandled.add(split);
        }
      }
    }
  }

  private void spillOverlappingActiveIntervals(
      LiveIntervals unhandledInterval, int candidate, boolean candidateIsWide) {
    assert !unhandledInterval.hasRegister();
    // Registers that we cannot choose for spilling.
    IntList excludedRegisters = new IntArrayList(candidateIsWide ? 2 : 1);
    excludedRegisters.add(candidate);
    if (candidateIsWide) {
      excludedRegisters.add(candidate + 1);
    }
    if (unhandledInterval.isArgumentInterval()
        && unhandledInterval != unhandledInterval.getSplitParent()) {
      // This live interval will become active in its original argument register and in the
      // candidate register simultaneously.
      unhandledInterval.getSplitParent().forEachRegister(excludedRegisters::add);
    }
    // Spill overlapping active intervals.
    List<LiveIntervals> newActive = new ArrayList<>();
    Iterator<LiveIntervals> activeIterator = active.iterator();
    while (activeIterator.hasNext()) {
      LiveIntervals intervals = activeIterator.next();
      assert registersForIntervalsAreTaken(intervals);
      if (intervals.usesRegister(candidate, candidateIsWide)) {
        activeIterator.remove();
        int registerNumber = getSpillRegister(intervals, excludedRegisters);
        // Important not to free the registers for intervals before finding a spill register,
        // because we might otherwise end up spilling to the current registers of intervals,
        // depending on getSpillRegister.
        freeOccupiedRegistersForIntervals(intervals);
        LiveIntervals splitChild = intervals.splitBefore(unhandledInterval.getStart(), mode);
        assignRegister(splitChild, registerNumber);
        splitChild.setSpilled(true);
        takeFreeRegistersForIntervals(splitChild);
        assert splitChild.hasRegister();
        assert intervals.hasRegister();
        newActive.add(splitChild);
        // If the constant is split before its first actual use, mark the constant as being
        // spilled. That will allows us to remove it afterwards if it is rematerializable.
        if (intervals.getValue().isConstNumber()
            && intervals.getStart() == intervals.getValue().definition.getNumber()
            && intervals.getUses().size() == 1) {
          intervals.setSpilled(true);
        }
        if (splitChild.hasUses()) {
          if (intervals.getValue().isConstNumber()) {
            // TODO(ager): Do this for all constants. Currently we only rematerialize const
            //  number and therefore we only do it for numbers at this point.
            splitRangesForSpilledConstant(splitChild, registerNumber);
          } else if (intervals.isArgumentInterval()) {
            splitRangesForSpilledArgument(splitChild);
          } else {
            splitRangesForSpilledInterval(splitChild);
          }
        }
      }
    }
    active.addAll(newActive);
    assert registersAreFree(candidate, candidateIsWide);
  }

  private void splitRangesForSpilledArgument(LiveIntervals spilled) {
    assert spilled.isSpilled();
    assert spilled.isArgumentInterval();
    // Argument intervals are spilled to the original argument register. We don't know what that is
    // yet, and therefore we split before the next constrained use to make sure we get a usable
    // register at the next use.
    LiveIntervalsUse firstUseWithConstraint = spilled.firstUseWithConstraint(mode);
    if (firstUseWithConstraint == null
        || (mode.is8BitRefinement()
            && spilled.getSplitParent().getRegisterEnd() < numberOf4BitArgumentRegisters)) {
      spilled.setRegister(spilled.getSplitParent().getRegister());
      spilled.setSpilled(false);
      return;
    }
    LiveIntervals splitOfSplit = spilled.splitBefore(firstUseWithConstraint.getPosition(), mode);
    assert splitOfSplit != spilled;
    unhandled.add(splitOfSplit);
  }

  private void splitRangesForSpilledInterval(LiveIntervals spilled) {
    // Spilling a non-pinned, non-rematerializable value. We use the value in the spill
    // register for as long as possible to avoid further moves.
    assert spilled.isSpilled();
    assert !spilled.getValue().isConstNumber();
    LiveIntervalsUse firstUseWithConstraint = spilled.firstUseWithConstraint(mode);
    if (firstUseWithConstraint != null) {
      int register = spilled.getRegister();
      LiveIntervals splitOfSplit = spilled.splitBefore(firstUseWithConstraint.getPosition(), mode);
      if (splitOfSplit != spilled) {
        unhandled.add(splitOfSplit);
      } else {
        assert !spilled.hasRegister();
        spilled.setRegister(register);
        if (spilled.hasUses()) {
          spilled.setSpilled(false);
        }
      }
    }
  }

  private void splitRangesForSpilledConstant(LiveIntervals spilled, int spillRegister) {
    // When spilling a constant we should not keep it alive in the spill register, instead
    // we should use rematerialization. We aggressively spill the constant in all gaps
    // between uses that span more than a certain number of instructions. If we needed to
    // spill we are running low on registers and this constant should get out of the way
    // as much as possible.
    assert spilled.isSpilled();
    assert spilled.getValue().isConstNumber();
    // Do not split range if constant is reused by one of the eleven following instruction.
    int maxGapSize = 11 * INSTRUCTION_NUMBER_DELTA;
    LiveIntervalsUse firstUseWithConstraint = spilled.firstUseWithConstraint(mode);
    if (firstUseWithConstraint != null) {
      // Split at first use after the spill position and add to unhandled to get a register
      // assigned for rematerialization.
      LiveIntervals split = spilled.splitBefore(firstUseWithConstraint.getPosition(), mode);
      if (spilled.hasUses()) {
        spilled.setSpilled(false);
      }
      unhandled.add(split);
      // Now repeatedly split for each use that is more than maxGapSize away from the previous use.
      boolean changed = true;
      while (changed) {
        changed = false;
        int previousUse = split.getStart();
        for (LiveIntervalsUse use : split.getUses()) {
          if (use.getPosition() - previousUse > maxGapSize) {
            // Found a use that is more than gap size away from the previous use. Split after
            // the previous use.
            split = split.splitBefore(previousUse + INSTRUCTION_NUMBER_DELTA, mode);
            // If the next use is not at the start of the new split, we split again at the next use
            // and spill the gap.
            if (toGapPosition(use.getPosition()) > split.getStart()) {
              assignRegister(split, spillRegister);
              split.setSpilled(true);
              inactive.add(split);
              split = split.splitBefore(use.getPosition(), mode);
            }
            // |split| now starts at the next use - add it to unhandled to get a register
            // assigned for rematerialization.
            unhandled.add(split);
            // Break out of the loop to start iterating the new split uses.
            changed = true;
            break;
          }
          previousUse = use.getPosition();
        }
      }
    } else if (spilled.hasUses()) {
      spilled.setSpilled(false);
    }
  }

  private void blockInvokeRangeIntervals(
      LiveIntervals unhandledInterval,
      int registerConstraint,
      RegisterPositions usePositions,
      RegisterPositions blockedPositions) {
    // TODO(b/302281605): The only way there can be active invoke-range intervals is if we have a
    //  live intervals that have been split right before the invoke range instruction. If we had a
    //  mapping from instruction number to the invoke range instruction, we could find the invoke
    //  range live intervals directly without scanning all active intervals. Moreover, we could
    //  avoid checking if the intervals overlap, since they clearly do.
    for (LiveIntervals intervals : Iterables.concat(active, inactive)) {
      if (!intervals.isInvokeRangeIntervals()) {
        continue;
      }
      int registerStart = intervals.getRegister();
      if (registerStart <= registerConstraint && intervals.overlaps(unhandledInterval)) {
        intervals.forEachRegister(
            register -> {
              if (register <= registerConstraint) {
                int firstUse = intervals.firstUseAfter(unhandledInterval.getStart());
                if (firstUse < blockedPositions.get(register)) {
                  blockedPositions.set(register, firstUse, intervals);
                  // If we start blocking registers other than linked arguments, we might need to
                  // explicitly update the use positions as well as blocked positions.
                  assert usePositions.isBlocked(register)
                      || usePositions.get(register) <= blockedPositions.get(register);
                }
              }
            });
      }
    }
  }

  // Returns the number of added parallel move temporary registers.
  private InsertMovesResult insertMoves() {
    assert firstParallelMoveTemporary == NO_REGISTER;
    computeRematerializableBits();

    SpillMoveSet spillMoves = new SpillMoveSet(this, code, appView);
    for (LiveIntervals intervals : liveIntervals) {
      if (intervals.hasSplits()) {
        LiveIntervals current = intervals;
        PriorityQueue<LiveIntervals> sortedChildren = new PriorityQueue<>();
        sortedChildren.addAll(current.getSplitChildren());
        for (LiveIntervals split = sortedChildren.poll();
            split != null;
            split = sortedChildren.poll()) {
          int position = split.getStart();
          if (!canSkipArgumentMove(split)) {
            spillMoves.addSpillOrRestoreMove(toGapPosition(position), split, current);
          }
          current = split;
        }
      }
    }

    resolveControlFlow(spillMoves);
    int firstParallelMoveTemporaryRegister = maxRegisterNumber + 1;
    int numberOfParallelMoveTemporaryRegisters =
        spillMoves.scheduleAndInsertMoves(firstParallelMoveTemporaryRegister);
    if (numberOfParallelMoveTemporaryRegisters > 0) {
      firstParallelMoveTemporary = firstParallelMoveTemporaryRegister;
      maxRegisterNumber += numberOfParallelMoveTemporaryRegisters;
    }
    return new InsertMovesResult(this, numberOfParallelMoveTemporaryRegisters);
  }

  private void computeRematerializableBits() {
    for (LiveIntervals liveInterval : liveIntervals) {
      liveInterval.computeRematerializable(this);
    }
  }

  // Resolve control flow by inserting phi moves and by inserting moves when the live intervals
  // change for a value across block boundaries.
  private void resolveControlFlow(SpillMoveSet spillMoves) {
    // For a control-flow graph like the following where a value v is split at an instruction in
    // block C a spill move is inserted in block C to transfer the value from register r0 to
    // register r1. However, that move is not executed when taking the control-flow edge from
    // B to D and therefore resolution will insert a move from r0 to r1 on that edge.
    //
    //             r0            r1
    //   v: |----------------|--------|
    //
    //       A ----> B ----> C ----> D
    //               |               ^
    //               +---------------+
    for (BasicBlock block : code.blocks) {
      for (BasicBlock successor : block.getSuccessors()) {
        // If we are processing an exception edge, we need to use the throwing instruction
        // as the instruction we are coming from.
        int fromInstruction = block.exit().getNumber();
        boolean isCatch = block.hasCatchSuccessor(successor);
        if (isCatch) {
          for (Instruction instruction : block.getInstructions()) {
            if (instruction.instructionTypeCanThrow()) {
              fromInstruction = instruction.getNumber();
              break;
            }
          }
        }
        int toInstruction = successor.entry().getNumber();

        // Insert spill/restore moves when a value changes across a block boundary.
        Set<Value> liveAtEntry = liveAtEntrySets.get(successor).liveValues;
        for (Value value : liveAtEntry) {
          LiveIntervals parentInterval = value.getLiveIntervals();
          LiveIntervals fromIntervals = parentInterval.getSplitCovering(fromInstruction);
          LiveIntervals toIntervals = parentInterval.getSplitCovering(toInstruction);
          if (canSkipArgumentMove(toIntervals)) {
            // No need to add resolution moves to pinned argument registers.
            continue;
          }
          if (fromIntervals != toIntervals) {
            if (block.exit().isGoto() && !isCatch) {
              spillMoves.addOutResolutionMove(fromInstruction - 1, toIntervals, fromIntervals);
            } else {
              spillMoves.addInResolutionMove(toInstruction - 1, toIntervals, fromIntervals);
            }
          }
        }

        // Insert phi moves.
        int predIndex = successor.getPredecessors().indexOf(block);
        for (Phi phi : successor.getPhis()) {
          LiveIntervals toIntervals = phi.getLiveIntervals().getSplitCovering(toInstruction);
          Value operand = phi.getOperand(predIndex);
          LiveIntervals fromIntervals =
              operand.getLiveIntervals().getSplitCovering(fromInstruction);
          if (fromIntervals != toIntervals && !toIntervals.isArgumentInterval()) {
            assert block.getSuccessors().size() == 1;
            spillMoves.addPhiMove(fromInstruction - 1, toIntervals, fromIntervals);
          }
        }
      }
    }
  }

  public boolean isPinnedArgument(Value value) {
    return value.isArgument() && isPinnedArgumentRegister(value.getLiveIntervals());
  }

  boolean isPinnedArgumentRegister(LiveIntervals intervals) {
    if (!intervals.isArgumentInterval()) {
      return false;
    }
    LiveIntervals parentIntervals = intervals.getSplitParent();
    assert parentIntervals.hasRegister();
    if (mode.is4Bit()) {
      // We don't pin argument registers in 4 bit mode, unless we have to.
      if (options().shouldCompileMethodInDebugMode(code.context())
          || options().canHaveThisTypeVerifierBug()
          || options().canHaveThisJitCodeDebuggingBug()) {
        return parentIntervals.getValue().isThis();
      }
      return false;
    }
    return true;
  }

  public boolean isArgumentRegister(int register) {
    return register < numberOfArgumentRegisters;
  }

  boolean canSkipArgumentMove(LiveIntervals intervals) {
    if (!isPinnedArgumentRegister(intervals)) {
      return false;
    }
    assert intervals.hasRegister();
    if (!isArgumentRegister(intervals.getRegister())) {
      return false;
    }
    // An argument register could be moved to another argument register.
    if (intervals.getRegister() != intervals.getSplitParent().getRegister()) {
      return false;
    }
    return true;
  }

  private static void addLiveRange(
      Value value, BasicBlock block, int end, List<LiveIntervals> liveIntervals, IRCode code) {
    int firstInstructionInBlock = block.entry().getNumber();
    int instructionsSize = block.getInstructions().size() * INSTRUCTION_NUMBER_DELTA;
    int lastInstructionInBlock =
        firstInstructionInBlock + instructionsSize - INSTRUCTION_NUMBER_DELTA;
    int instructionNumber;
    if (value.isPhi()) {
      instructionNumber = firstInstructionInBlock;
    } else {
      Instruction instruction = value.definition;
      instructionNumber = instruction.getNumber();
    }
    if (value.getLiveIntervals() == null) {
      liveIntervals.add(new LiveIntervals(value));
    }
    LiveIntervals intervals = value.getLiveIntervals();
    if (firstInstructionInBlock <= instructionNumber &&
        instructionNumber <= lastInstructionInBlock) {
      if (value.isPhi()) {
        // Phis need to interfere with spill restore moves inserted before the instruction because
        // the phi value is defined on the inflowing edge.
        instructionNumber--;
      }
      intervals.addRange(new LiveRange(instructionNumber, end));
      assert unconstrainedForCf(intervals.getRegisterLimit(), code);
      if (code.getConversionOptions().isGeneratingDex() && !value.isPhi()) {
        int constraint = value.definition.maxOutValueRegister();
        intervals.addUse(new LiveIntervalsUse(instructionNumber, constraint));
      }
    } else {
      intervals.addRange(new LiveRange(firstInstructionInBlock - 1, end));
    }
  }

  private void computeLiveRanges() {
    computeLiveRanges(appView, code, liveAtEntrySets, liveIntervals);
    boolean hasMoveException = false;
    for (BasicBlock block : code.blocks(block -> block.entry().isMoveException())) {
      for (Value value : liveAtEntrySets.get(block).liveValues) {
        value.getLiveIntervals().setIsLiveAtMoveExceptionEntry();
      }
      hasMoveException = true;
    }
    // Art VMs before Android M assume that the register for the receiver never changes its value.
    // This assumption is used during verification. Allowing the receiver register to be
    // overwritten can therefore lead to verification errors. If we could be targeting one of these
    // VMs we block the receiver register throughout the method.
    if ((options().canHaveThisTypeVerifierBug() || options().canHaveThisJitCodeDebuggingBug())
        && !code.method().getAccessFlags().isStatic()) {
      LiveIntervals thisIntervals = firstArgumentValue.getLiveIntervals();
      thisIntervals.getRanges().clear();
      thisIntervals.addRange(new LiveRange(0, code.getNextInstructionNumber()));
      for (LiveAtEntrySets values : liveAtEntrySets.values()) {
        values.liveValues.add(firstArgumentValue);
      }
      if (hasMoveException) {
        thisIntervals.setIsLiveAtMoveExceptionEntry();
      }
    }
  }

  /** Compute live ranges based on liveAtEntry sets for all basic blocks. */
  public static void computeLiveRanges(
      AppView<?> appView,
      IRCode code,
      Map<BasicBlock, LiveAtEntrySets> liveAtEntrySets,
      List<LiveIntervals> liveIntervals) {
    for (BasicBlock block : code.topologicallySortedBlocks()) {
      // Linked collections to ensure determinism of liveIntervals (see also b/197643889).
      LinkedHashSet<Value> live = Sets.newLinkedHashSet();
      LinkedHashSet<Value> phiOperands = Sets.newLinkedHashSet();
      LinkedHashSet<Value> liveAtThrowingInstruction = Sets.newLinkedHashSet();
      Set<BasicBlock> exceptionalSuccessors = block.getCatchHandlers().getUniqueTargets();
      for (BasicBlock successor : block.getSuccessors()) {
        // Values live at entry to a block that is an exceptional successor are only live
        // until the throwing instruction in this block. They are live until the end of
        // the block only if they are used in normal flow as well.
        boolean isExceptionalSuccessor = exceptionalSuccessors.contains(successor);
        if (isExceptionalSuccessor) {
          LinkedHashSetUtils.addAll(
              liveAtThrowingInstruction, liveAtEntrySets.get(successor).liveValues);
        } else {
          LinkedHashSetUtils.addAll(live, liveAtEntrySets.get(successor).liveValues);
        }
        // Exception blocks should not have any phis (if an exception block has more than one
        // predecessor, then we insert a split block in-between).
        assert !isExceptionalSuccessor || successor.getPhis().isEmpty();
        for (Phi phi : successor.getPhis()) {
          live.remove(phi);
          phiOperands.add(phi.getOperand(successor.getPredecessors().indexOf(block)));
        }
      }
      LinkedHashSetUtils.addAll(live, phiOperands);
      int numInstructionsDelta = block.getInstructions().size() * INSTRUCTION_NUMBER_DELTA;
      for (Value value : live) {
        int end = block.entry().getNumber() + numInstructionsDelta;
        // Make sure that phi operands do not overlap the phi live range. The phi operand is
        // not live until the next instruction, but only until the gap before the next instruction
        // where the phi value takes over.
        if (phiOperands.contains(value)) {
          end--;
        }
        addLiveRange(value, block, end, liveIntervals, code);
      }
      for (Instruction ins = block.getLastInstruction(); ins != null; ins = ins.getPrev()) {
        Value definition = ins.outValue();
        if (definition != null) {
          // For instructions that define values which have no use create a live range covering
          // the instruction. This will typically be instructions that can have side effects even
          // if their output is not used.
          if (definition instanceof StackValues) {
            for (StackValue value : ((StackValues) definition).getStackValues()) {
              live.remove(value);
            }
          } else if (!definition.isUsed()) {
            addLiveRange(
                definition,
                block,
                ins.getNumber() + INSTRUCTION_NUMBER_DELTA - 1,
                liveIntervals,
                code);
            assert !code.getConversionOptions().isGeneratingClassFiles() || ins.isArgument()
                : "Arguments should be the only potentially unused local in CF";
          }
          live.remove(definition);
        }
        for (Value use : ins.inValues()) {
          if (use.needsRegister()) {
            assert unconstrainedForCf(ins.maxInValueRegister(), code);
            if (!live.contains(use)) {
              live.add(use);
              addLiveRange(use, block, ins.getNumber(), liveIntervals, code);
            }
            if (code.getConversionOptions().isGeneratingDex()) {
              int inConstraint = ins.maxInValueRegister();
              LiveIntervals useIntervals = use.getLiveIntervals();
              // Arguments are always kept in their original, incoming register. For every
              // unconstrained use of an argument we therefore use its incoming register.
              // As a result, we do not need to record that the argument is being used at the
              // current instruction.
              //
              // For ranged invoke instructions that use a subset of the arguments in the current
              // order, registering a use for the arguments at the invoke can cause us to run out of
              // registers. That is because all arguments are forced back into a chosen register at
              // all uses. Therefore, if we register a use of an argument where we can actually use
              // it in the argument register, the register allocator would use two registers for the
              // argument but in reality only use one.
              boolean isUnconstrainedArgumentUse =
                  use.isArgument() && inConstraint == Constants.U16BIT_MAX && !isInvokeRange(ins);
              if (!isUnconstrainedArgumentUse) {
                useIntervals.addUse(new LiveIntervalsUse(ins.getNumber(), inConstraint));
              }
            }
          }
        }
        // Deal with values that are live on the exceptional edge only. Care must be taken
        // to correctly deal with check-cast instructions. Check-cast can throw an exception
        // so values (other than the in value in the check cast) live on the exceptional edge
        // need to have their live range extended across the check-cast. This is because a
        // 'r1 <- check-cast r0' maps to 'move r1, r0; check-cast r1' and when that
        // happens r1 could be clobbered on the exceptional edge if r1 initially contained
        // a value that is used in the exceptional code.
        if (ins.instructionTypeCanThrow()) {
          for (Value use : liveAtThrowingInstruction) {
            if (use.needsRegister() && !live.contains(use)) {
              live.add(use);
              addLiveRange(
                  use, block, getLiveRangeEndOnExceptionalFlow(ins, use), liveIntervals, code);
            }
          }
        }
        if (appView.options().debug || code.context().isReachabilitySensitive()) {
          // In debug mode, or if the method is reachability sensitive, extend the live range
          // to cover the full scope of a local variable (encoded as debug values).
          int number = ins.getNumber();
          List<Value> sortedDebugValues = new ArrayList<>(ins.getDebugValues());
          sortedDebugValues.sort(Value::compareTo);
          for (Value use : sortedDebugValues) {
            assert use.needsRegister();
            if (!live.contains(use)) {
              live.add(use);
              addLiveRange(use, block, number, liveIntervals, code);
            }
          }
        }
      }
    }
  }

  private static int getLiveRangeEndOnExceptionalFlow(Instruction instruction, Value value) {
    int end = instruction.getNumber();
    if (instruction.isCheckCast() && value != instruction.asCheckCast().object()) {
      end += INSTRUCTION_NUMBER_DELTA;
    }
    return end;
  }

  private static boolean unconstrainedForCf(int constraint, IRCode code) {
    return code.getConversionOptions().isGeneratingDex() || constraint == Constants.U16BIT_MAX;
  }

  private void clearUserInfo() {
    code.blocks.forEach(BasicBlock::clearUserInfo);
  }

  // Rewrites casts on the form "lhs = (T) rhs" into "(T) rhs" and replaces the uses of lhs by rhs.
  // This transformation helps to ensure that we do not insert unnecessary moves in bridge methods
  // with an invoke-range instruction, since all the arguments to the invoke-range instruction will
  // be original, consecutive arguments of the enclosing method (and importantly, not values that
  // have been defined by a check-cast instruction).
  private void transformBridgeMethod() {
    assert implementationIsBridge(code);
    BasicBlock entry = code.entryBlock();
    InstructionIterator iterator = entry.iterator();
    // Create a mapping from argument values to their index, while scanning over the arguments.
    Reference2IntMap<Value> argumentIndices = new Reference2IntArrayMap<>();
    while (iterator.peekNext().isArgument()) {
      Value argument = iterator.next().asArgument().outValue();
      argumentIndices.put(argument, argumentIndices.size());
    }
    // Move forward until the invocation.
    while (!iterator.peekNext().isInvoke()) {
      iterator.next();
    }
    Invoke invokeInstruction = iterator.peekNext().asInvoke();
    // Determine if all of the arguments can be cast without having to move them into lower
    // registers.
    int numberOfRequiredRegisters = numberOfArgumentRegisters;
    if (invokeInstruction.outValue() != null) {
      numberOfRequiredRegisters += invokeInstruction.outValue().requiredRegisters();
    }
    if (numberOfRequiredRegisters - 1 > Constants.U8BIT_MAX) {
      return;
    }
    // Determine if the arguments are consecutive input arguments.
    List<Value> arguments = invokeInstruction.arguments();
    if (arguments.size() >= 1) {
      int previousArgumentIndex = -1;
      for (int i = 0; i < arguments.size(); ++i) {
        Value current = arguments.get(i);
        if (!current.isArgument()) {
          current = current.definition.asCheckCast().object();
        }
        assert current.isArgument();
        int currentArgumentIndex = argumentIndices.getInt(current);
        if (previousArgumentIndex >= 0 && currentArgumentIndex != previousArgumentIndex + 1) {
          return;
        }
        previousArgumentIndex = currentArgumentIndex;
      }
    } else {
      return;
    }

    // Rewrite all casts before the invocation on the form "lhs = (T) rhs" into "(T) rhs", and
    // replace the uses of lhs by rhs.
    while (iterator.peekPrevious().isCheckCast()) {
      CheckCast castInstruction = iterator.previous().asCheckCast();
      castInstruction.outValue().replaceUsers(castInstruction.object());
      castInstruction.setOutValue(null);
    }
  }

  // Returns true if the IR for this method consists of zero or more arguments, zero or more casts
  // of the arguments, a single invocation, an optional cast of the result, and a return (in this
  // particular order).
  private static boolean implementationIsBridge(IRCode code) {
    if (code.blocks.size() > 1) {
      return false;
    }
    InstructionIterator iterator = code.entryBlock().iterator();
    // Move forward to the first instruction after the definition of the arguments.
    while (iterator.hasNext() && iterator.peekNext().isArgument()) {
      iterator.next();
    }
    // Move forward to the first instruction after the casts.
    while (iterator.hasNext()
        && iterator.peekNext().isCheckCast()
        && iterator.peekNext().asCheckCast().object().isArgument()) {
      iterator.next();
    }
    // Check if there is an invoke instruction followed by an optional cast of the result,
    // and a return.
    if (!iterator.hasNext() || !iterator.next().isInvoke()) {
      return false;
    }
    if (iterator.hasNext() && iterator.peekNext().isCheckCast()) {
      iterator.next();
    }
    if (!iterator.hasNext() || !iterator.next().isReturn()) {
      return false;
    }
    return true;
  }

  private Value createValue(TypeElement typeLattice) {
    Value value = code.createValue(typeLattice, null);
    value.setNeedsRegister(true);
    return value;
  }

  private void replaceArgument(Invoke invoke, int index, Value newArgument) {
    Value argument = invoke.arguments().get(index);
    invoke.arguments().set(index, newArgument);
    argument.removeUser(invoke);
    newArgument.addUser(invoke);
  }

  private void ensureUniqueArgumentsToInvokeRangeInstructions(
      Invoke invoke, InstructionListIterator instructionIterator) {
    Set<Value> seen = Sets.newIdentityHashSet();
    for (int argumentIndex = 0; argumentIndex < invoke.arguments().size(); argumentIndex++) {
      Value argument = invoke.getArgument(argumentIndex);
      if (seen.add(argument)) {
        continue;
      }
      Value newArgument = createValue(argument.getType());
      Move move = new Move(newArgument, argument);
      move.setPosition(invoke.getPosition());
      replaceArgument(invoke, argumentIndex, newArgument);
      instructionIterator.add(move);
    }
  }

  private static boolean isInvokeRange(Instruction instruction) {
    Invoke invoke = instruction.asInvoke();
    return invoke != null
        && invoke.requiredArgumentRegisters() > 5
        && !argumentsAreAlreadyLinked(invoke);
  }

  private static boolean argumentsAreAlreadyLinked(Invoke invoke) {
    Iterator<Value> it = invoke.arguments().iterator();
    Argument current = it.next().getDefinitionOrNull(Instruction::isArgument);
    if (current == null) {
      return false;
    }
    while (it.hasNext()) {
      Argument next = it.next().getDefinitionOrNull(Instruction::isArgument);
      if (current.getNext() != next) {
        return false;
      }
      current = next;
    }
    return true;
  }

  private void createArgumentLiveIntervals(List<Value> arguments) {
    int index = 0;
    for (Value argument : arguments) {
      // Add a live range to this value from the beginning of the block up to the argument
      // instruction to avoid dead arguments without a range. This may create an actually empty
      // range like [0,0[ but that works, too.
      LiveIntervals argumentInterval = new LiveIntervals(argument);
      argumentInterval.addRange(new LiveRange(0, index));
      liveIntervals.add(argumentInterval);
      index += INSTRUCTION_NUMBER_DELTA;
    }
  }

  private void linkArgumentValuesAndIntervals(List<Value> arguments) {
    if (!arguments.isEmpty()) {
      Value last = firstArgumentValue = arguments.get(0);
      for (int i = 1; i < arguments.size(); ++i) {
        Value next = arguments.get(i);
        last.getLiveIntervals().link(next.getLiveIntervals());
        last = next;
      }
    }
  }

  private void constrainArgumentIntervals() {
    // Record the constraint that incoming arguments are in consecutive registers.
    List<Value> arguments = code.collectArguments();
    createArgumentLiveIntervals(arguments);
    linkArgumentValuesAndIntervals(arguments);
  }

  private void insertRangeInvokeMoves() {
    for (BasicBlock block : code.blocks) {
      InstructionListIterator it = block.listIterator();
      while (it.hasNext()) {
        Instruction instruction = it.next();
        if (isInvokeRange(instruction)) {
          // Rewind so moves are inserted before the invoke.
          it.previous();
          // Generate the argument moves.
          ensureUniqueArgumentsToInvokeRangeInstructions(instruction.asInvoke(), it);
          // Move past the move again.
          it.next();
        }
      }
    }
  }

  private void computeNeedsRegister() {
    for (BasicBlock block : code.topologicallySortedBlocks()) {
      for (Instruction instruction : block.getInstructions()) {
        if (instruction.outValue() != null) {
          instruction.outValue().computeNeedsRegister();
        }
      }
    }
  }

  private void pinArgumentRegisters() {
    // Special handling for arguments. Pin their register.
    if (firstArgumentValue != null) {
      increaseCapacity(numberOfArgumentRegisters - 1, true);
      int register = 0;
      for (LiveIntervals argumentLiveIntervals : getArgumentLiveIntervals()) {
        assignRegister(argumentLiveIntervals, register);
        register += argumentLiveIntervals.requiredRegisters();
      }
    }
  }

  private void ensureCapacity(int newMaxRegisterNumber) {
    if (newMaxRegisterNumber > maxRegisterNumber) {
      increaseCapacity(newMaxRegisterNumber);
    }
  }

  private void increaseCapacity(int newMaxRegisterNumber) {
    increaseCapacity(newMaxRegisterNumber, false);
  }

  private void increaseCapacity(int newMaxRegisterNumber, boolean takeRegisters) {
    if (!takeRegisters) {
      for (int register = maxRegisterNumber + 1; register <= newMaxRegisterNumber; ++register) {
        freeRegisters.add(register);
      }
    }
    maxRegisterNumber = newMaxRegisterNumber;
  }

  private int getFreeConsecutiveRegisters(int numberOfRegisters) {
    return getFreeConsecutiveRegisters(numberOfRegisters, false);
  }

  private int getFreeConsecutiveRegisters(int numberOfRegisters, boolean prioritizeSmallRegisters) {
    int oldMaxRegisterNumber = maxRegisterNumber;
    IntSortedSet freeRegistersWithDesiredOrdering = freeRegisters;
    if (prioritizeSmallRegisters) {
      freeRegistersWithDesiredOrdering =
          new IntRBTreeSet(
              (Integer x, Integer y) -> {
                boolean xIsArgument = isArgumentRegister(x);
                boolean yIsArgument = isArgumentRegister(y);
                // If x is an argument and y is not, then prioritize y.
                if (xIsArgument && !yIsArgument) {
                  return 1;
                }
                // If x is not an argument and y is, then prioritize x.
                if (!xIsArgument && yIsArgument) {
                  return -1;
                }
                // Otherwise use their normal ordering.
                return x - y;
              });
      freeRegistersWithDesiredOrdering.addAll(freeRegisters);
    }

    IntIterator freeRegistersIterator = freeRegistersWithDesiredOrdering.iterator();
    int first = getNextFreeRegister(freeRegistersIterator);
    int current = first;
    while (current - first + 1 != numberOfRegisters) {
      for (int i = 0; i < numberOfRegisters - 1; i++) {
        int next = getNextFreeRegister(freeRegistersIterator);
        // We cannot allow that some are argument registers and some or not, because they will no
        // longer be consecutive if we later decide to increment maxRegisterNumber.
        if (next != current + 1 || next == numberOfArgumentRegisters) {
          first = next;
          current = first;
          break;
        }
        current++;
      }
    }
    for (int register = oldMaxRegisterNumber + 1; register <= maxRegisterNumber; ++register) {
      boolean wasAdded = freeRegisters.add(register);
      assert wasAdded;
    }
    // Either all the consecutive registers are from the argument registers, or all are from the
    // non-argument registers.
    assert (isArgumentRegister(first) && isArgumentRegister(first + numberOfRegisters - 1))
        || (!isArgumentRegister(first) && !isArgumentRegister(first + numberOfRegisters - 1));
    return first;
  }

  private boolean registersAreFreeAndConsecutive(int register, boolean registerIsWide) {
    if (!freeRegisters.contains(register)) {
      return false;
    }
    if (registerIsWide) {
      if (!freeRegisters.contains(register + 1)) {
        return false;
      }
      if (register == numberOfArgumentRegisters - 1) {
        // Will not be consecutive after reordering the arguments and temporaries.
        return false;
      }
    }
    return true;
  }

  private int getNextFreeRegister(IntIterator freeRegistersIterator) {
    if (freeRegistersIterator.hasNext()) {
      return freeRegistersIterator.nextInt();
    }
    return ++maxRegisterNumber;
  }

  private void excludeRegistersForInterval(LiveIntervals intervals) {
    assert intervals.hasRegister();
    intervals.forEachRegister(freeRegisters::remove);
    if (isPinnedArgumentRegister(intervals) && !intervals.isSplitParent()) {
      LiveIntervals parent = intervals.getSplitParent();
      assert parent.hasRegister();
      if (parent.getRegister() != intervals.getRegister()) {
        parent.forEachRegister(freeRegisters::remove);
      }
    }
  }

  private void freeOccupiedRegistersForIntervals(LiveIntervals intervals) {
    assert registersForIntervalsAreTaken(intervals);
    int register = intervals.getRegister();
    assert register + intervals.requiredRegisters() - 1 <= maxRegisterNumber;
    freeRegisters.add(register);
    if (intervals.getType().isWide()) {
      freeRegisters.add(register + 1);
    }

    if (isPinnedArgumentRegister(intervals) && !intervals.isSplitParent()) {
      LiveIntervals parent = intervals.getSplitParent();
      if (parent.getRegister() != intervals.getRegister()) {
        freeOccupiedRegistersForIntervals(intervals.getSplitParent());
      }
    }
  }

  private void takeFreeRegisters(int register, boolean isWide) {
    assert registersAreFree(register, isWide);
    freeRegisters.remove(register);
    if (isWide) {
      freeRegisters.remove(register + 1);
    }
  }

  private void takeFreeRegistersForIntervals(LiveIntervals intervals) {
    takeFreeRegisters(intervals.getRegister(), intervals.getType().isWide());

    if (isPinnedArgumentRegister(intervals) && !intervals.isSplitParent()) {
      LiveIntervals parent = intervals.getSplitParent();
      if (parent.getRegister() != intervals.getRegister()) {
        takeFreeRegistersForIntervals(parent);
      }
    }
  }

  private boolean registerIsFree(int register) {
    return freeRegisters.contains(register) || isDedicatedMoveExceptionRegister(register);
  }

  private boolean registerRangeIsFree(int register, int requiredRegisters) {
    for (int i = 0; i < requiredRegisters; i++) {
      assert !isDedicatedMoveExceptionRegister(register + i);
      if (!freeRegisters.contains(register + i)) {
        return false;
      }
    }
    return true;
  }

  // Note: treats a register as free if it is in the set of free registers, or it is the dedicated
  // move exception register.
  private boolean registersAreFree(int register, boolean isWide) {
    return registerIsFree(register) && (!isWide || registerIsFree(register + 1));
  }

  private boolean registersAreTaken(int register, boolean isWide) {
    return !freeRegisters.contains(register) && (!isWide || !freeRegisters.contains(register + 1));
  }

  private boolean registersForIntervalsAreTaken(LiveIntervals intervals) {
    assert intervals.hasRegister();
    return registersAreTaken(intervals.getRegister(), intervals.getType().isWide());
  }

  private boolean atLeastOneOfRegistersAreTaken(int register, boolean isWide) {
    return !freeRegisters.contains(register) || (isWide && !freeRegisters.contains(register + 1));
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder("Live ranges:\n");
    for (LiveIntervals intervals : liveIntervals) {
      Value value = intervals.getValue();
      builder.append(value);
      builder.append(" ");
      builder.append(intervals);
    }
    builder.append("\nLive range ascii art: \n");
    for (LiveIntervals intervals : liveIntervals) {
      Value value = intervals.getValue();
      if (intervals.hasRegister()) {
        StringUtils.appendRightPadded(builder, value + " r" + intervals.getRegister() + ": ", 20);
      } else {
        StringUtils.appendRightPadded(builder, value + " (no reg): ", 20);
      }
      builder.append("|");
      builder.append(intervals.toAscciArtString());
      builder.append("\n");
    }
    return builder.toString();
  }

  @Override
  public void mergeBlocks(BasicBlock kept, BasicBlock removed) {
    // Intentionally empty, we don't need to track merging in this allocator.
  }

  @Override
  public boolean hasEqualTypesAtEntry(BasicBlock first, BasicBlock second) {
    return java.util.Objects.equals(first.getLocalsAtEntry(), second.getLocalsAtEntry());
  }

  @Override
  public void addNewBlockToShareIdenticalSuffix(
      BasicBlock block, int suffixSize, List<BasicBlock> predsBeforeSplit) {
    // Intentionally empty, we don't need to track suffix sharing in this allocator.
  }
}
