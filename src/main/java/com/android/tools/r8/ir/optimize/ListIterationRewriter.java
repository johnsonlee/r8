// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize;

import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexItemFactory.JavaUtilListMembers;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ImmediateAppSubtypingInfo;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.ir.analysis.type.DynamicType;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.Add;
import com.android.tools.r8.ir.code.Assume;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.If;
import com.android.tools.r8.ir.code.IfType;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InvokeInterface;
import com.android.tools.r8.ir.code.InvokeMethodWithReceiver;
import com.android.tools.r8.ir.code.InvokeVirtual;
import com.android.tools.r8.ir.code.NumericType;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.ir.conversion.passes.CodeRewriterPass;
import com.android.tools.r8.ir.conversion.passes.result.CodeRewriterResult;
import com.android.tools.r8.utils.InternalOptions.TestingOptions;
import com.android.tools.r8.utils.TraversalContinuation;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Rewrites iterator-based for-each loops into indexed loops for random access lists.
 *
 * <pre>
 * For lists that can be determined to be random access (e.g. ArrayList), rewrites:
 *   for (var x : list) { ... }
 * to:
 *   int len = list.size();
 *   int i = 0;
 *   while (i < len){
 *     var x = list.get(i);
 *     i++;
 *     ...
 *   }
 *
 * IR version: From:
 * 0000: invoke-virtual {v2}, Ljava/util/ArrayList;.iterator:()Ljava/util/Iterator;
 * 0003: move-result-object v2
 * 0004: invoke-interface {v2}, Ljava/util/Iterator;.hasNext:()Z
 * 0007: move-result v0
 * 0008: if-eqz v0, 0016 // +000e
 * 000a: invoke-interface {v2}, Ljava/util/Iterator;.next:()Ljava/lang/Object;
 * 000d: move-result-object v0
 * 000e: ...
 * 0015: goto 0004 // -0011
 *
 * To:
 * 0000: invoke-virtual {v4}, Ljava/util/ArrayList;.size:()I
 * 0003: move-result v0
 * 0004: const/4 v1, #int 0 // #0
 * 0005: if-ge v1, v0, 0015 // +0010
 * 0007: invoke-virtual {v4, v1}, Ljava/util/ArrayList;.get:(I)Ljava/lang/Object;
 * 000a: move-result-object v2
 * 000b: add-int/lit8 v1, v1, #int 1
 * 000d: ...
 * 0014: goto 0005 // -000f
 *
 * The reason for the transformation is that the code runs ~3x faster and saves an allocation.
 * This transformation requires 2 extra registers and saves 2 bytes.
 */
public class ListIterationRewriter extends CodeRewriterPass<AppInfo> {
  private final DexString iteratorName;
  private final DexProto iteratorProto;
  private final DexType listType;
  private final DexType copyOnWriteArrayListType;
  private final DexType linkedListType;
  private final DexString hasNextName;
  private final DexProto hasNextProto;
  private final DexString nextName;
  private final DexMethod sizeMethod;
  private final DexMethod getMethod;
  private final DexType arrayListType;
  private final DexType immutableListType;

  public ListIterationRewriter(AppView<?> appView) {
    super(appView);
    // Do not match using Iterator DexMethods since InvokeVirtual may be used if the list or
    // iterator is a concrete class.
    this.iteratorName = dexItemFactory.iteratorName;
    this.iteratorProto = dexItemFactory.javaUtilIteratorProto;
    this.hasNextName = dexItemFactory.hasNextName;
    this.hasNextProto = dexItemFactory.iteratorMethods.hasNext.getProto();
    this.nextName = dexItemFactory.nextName;
    this.listType = dexItemFactory.javaUtilListType;
    this.copyOnWriteArrayListType = dexItemFactory.javaUtilConcurrentCopyOnWriteArrayListType;
    this.linkedListType = dexItemFactory.javaUtilLinkedListType;
    this.sizeMethod = dexItemFactory.javaUtilListMembers.size;
    this.getMethod = dexItemFactory.javaUtilListMembers.get;
    this.arrayListType = dexItemFactory.javaUtilArrayListType;
    this.immutableListType = dexItemFactory.comGoogleCommonCollectImmutableListType;
  }

  public static boolean shouldEnableForD8(AppView<AppInfo> appView) {
    return appView.options().isRelease()
        && appView.testing().listIterationRewritingRewriteCustomIterators;
  }

  /**
   * Returns whether to enable the optimization.
   *
   * @param subtypingInfo May contain pruned items, but must not be missing any types.
   */
  public static boolean shouldEnableForR8(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      ImmediateAppSubtypingInfo subtypingInfo) {
    TestingOptions opts = appView.options().testing;
    if (!appView.hasLiveness()) {
      return false;
    }
    if (opts.listIterationRewritingRewriteCustomIterators) {
      return true;
    }

    // Enable only if there are no ArrayList subclasses that override iterator() / get() / size().
    DexClass arrayListClass = appView.definitionFor(appView.dexItemFactory().javaUtilArrayListType);
    if (arrayListClass == null) {
      return false;
    }

    JavaUtilListMembers listMembers = appView.dexItemFactory().javaUtilListMembers;
    return subtypingInfo
        .traverseTransitiveSubclasses(
            arrayListClass,
            subclass ->
                TraversalContinuation.breakIf(
                    subclass.isProgramClass()
                        && subclass
                            .getMethodCollection()
                            .hasVirtualMethods(
                                m ->
                                    listMembers.iterator.match(m)
                                        || listMembers.get.match(m)
                                        || listMembers.size.match(m))))
        .shouldContinue();
  }

  @Override
  protected AppInfoWithClassHierarchy appInfo() {
    return appView.appInfoForDesugaring();
  }

  @Override
  protected String getRewriterId() {
    return "ListIterationRewriter";
  }

  @Override
  protected boolean shouldRewriteCode(IRCode code, MethodProcessor methodProcessor) {
    return code.metadata().mayHaveIf();
  }

  @Override
  protected CodeRewriterResult rewriteCode(
      IRCode code,
      MethodProcessor methodProcessor,
      MethodProcessingContext methodProcessingContext) {
    List<AnalysisResult> results = new ArrayList<>();
    for (Instruction instruction : code.instructions()) {
      InvokeMethodWithReceiver iteratorInstr = instruction.asInvokeMethodWithReceiver();
      if (iteratorInstr == null) {
        continue;
      }
      DexMethod invokedMethod = iteratorInstr.getInvokedMethod();
      if (!invokedMethod.match(iteratorProto, iteratorName)) {
        continue;
      }
      Value listValue = iteratorInstr.getReceiver();
      if (!isWellBehavedList(listValue)) {
        continue;
      }

      // See if it can be optimized.
      AnalysisResult result = analyzeIterator(iteratorInstr);
      if (result != null) {
        results.add(result);
      }
    }

    if (!results.isEmpty()) {
      for (AnalysisResult result : results) {
        rewriteInstance(code, result);
      }

      code.removeRedundantBlocks();
      return CodeRewriterResult.HAS_CHANGED;
    }
    return CodeRewriterResult.NO_CHANGE;
  }

  private boolean isWellBehavedList(Value listValue) {
    if (!listValue.getType().isClassType()) {
      return false;
    }
    DexType valueType = listValue.getType().asClassType().toDexType(dexItemFactory);
    if (options.testing.listIterationRewritingRewriteInterfaces) {
      // In Chrome in Nov 2024, enabling for all list types causes the number of iteration rewrites
      // go to from 1010 to 1731.
      // While this would be safe for lists like: List.of(), singletonList(), etc, it would require
      // expensive intra-procedural analysis to track when it is safe.
      return appInfo().isSubtype(valueType, listType)
          // LinkedList.get() is not O(1).
          && valueType.isNotIdenticalTo(linkedListType)
          // CopyOnWriteArrayList.iterator() provides a snapshot of the list.
          && valueType.isNotIdenticalTo(copyOnWriteArrayListType);
    }
    // TODO(b/145280859): Add support for kotlin.collections.ArrayList.
    return appInfo().isSubtype(valueType, arrayListType)
        || valueType.isIdenticalTo(immutableListType);
  }

  private static class InstructionAndOptionalAssume {
    public final Instruction instruction;
    public final Assume assume;

    InstructionAndOptionalAssume(Instruction instruction, Assume assume) {
      this.instruction = instruction;
      this.assume = assume;
    }
  }

  private static InstructionAndOptionalAssume findNextMeaningfulInstruction(
      Instruction instruction) {
    Assume assume = null;
    instruction = instruction.getNext();
    while (instruction != null) {
      if (instruction.isGoto()) {
        instruction = instruction.asGoto().getTarget().entry();
        continue;
      } else if (instruction.isAssume()) {
        if (assume != null) {
          return null;
        }
        assume = instruction.asAssume();
      } else if (!instruction.isConstNumber()) {
        // Constant hoisting can cause const instructions to appear between iterator() and
        // hasNext().
        return new InstructionAndOptionalAssume(instruction, assume);
      }
      instruction = instruction.getNext();
    }
    return null;
  }

  private AnalysisResult analyzeIterator(InvokeMethodWithReceiver iteratorInstr) {
    Value iteratorValue = iteratorInstr.outValue();

    if (iteratorValue == null || iteratorValue.hasPhiUsers() || iteratorValue.hasDebugUsers()) {
      return null;
    }

    InstructionAndOptionalAssume instructionAndAssume =
        findNextMeaningfulInstruction(iteratorInstr);
    if (instructionAndAssume == null) {
      return null;
    }

    InvokeMethodWithReceiver hasNextInstr =
        instructionAndAssume.instruction.asInvokeMethodWithReceiver();
    if (hasNextInstr == null
        || hasNextInstr.getReceiver().getAliasedValue() != iteratorValue
        || !isHasNextMethod(hasNextInstr.getInvokedMethod())) {
      return null;
    }

    // Ensure there is a loop.
    if (hasNextInstr.getBlock().hasUniquePredecessor()) {
      return null;
    }
    // Ensure hasNext() is the loop condition.
    if (hasNextInstr.getBlock().entry() != hasNextInstr) {
      return null;
    }

    // If .iterator() is the first call on the list, it will be followed by an Assume, which we'll
    // need to update when rewriting.
    Assume listAssumeInstr = null;
    if (instructionAndAssume.assume != null) {
      if (instructionAndAssume.assume.getFirstOperand() == iteratorInstr.getFirstOperand()) {
        listAssumeInstr = instructionAndAssume.assume;
      }
    }

    Value hasNextValue = hasNextInstr.outValue();
    if (hasNextValue.hasPhiUsers()
        || !hasNextValue.hasSingleUniqueUser()
        || hasNextValue.hasDebugUsers()) {
      return null;
    }

    instructionAndAssume = findNextMeaningfulInstruction(hasNextInstr);
    if (instructionAndAssume == null) {
      return null;
    }
    If ifInstr = instructionAndAssume.instruction.asIf();
    // Do not need to check ifInstr for unique predecessors since we already check that it's inValue
    // is the outValue of hasNext().
    if (ifInstr == null || hasNextValue.singleUniqueUser() != ifInstr || !ifInstr.isZeroTest()) {
      return null;
    }

    // Since .hasNext() is the first call on the iterator, it will be followed by a not-null Assume.
    Assume iteratorAssumeInstr = null;
    if (instructionAndAssume.assume != null) {
      if (instructionAndAssume.assume.getFirstOperand() == hasNextInstr.getFirstOperand()) {
        iteratorAssumeInstr = instructionAndAssume.assume;
      }
    }

    BasicBlock relevantBlock =
        ifInstr.getType() == IfType.EQ ? ifInstr.fallthroughBlock() : ifInstr.getTrueTarget();
    InvokeMethodWithReceiver nextInstr = relevantBlock.entry().asInvokeMethodWithReceiver();
    if (nextInstr == null
        || nextInstr.getReceiver().getAliasedValue() != iteratorValue
        || !isNextMethod(nextInstr.getInvokedMethod())
        || !relevantBlock.hasUniquePredecessor()) {
      return null;
    }

    // Ensure there are only 2 users of the iterator: hasNext() and next().
    int numNonAliasIteratorUsers = iteratorValue.numberOfUsers();
    if (iteratorAssumeInstr != null) {
      if (iteratorAssumeInstr.outValue().hasPhiUsers()) {
        return null;
      }
      numNonAliasIteratorUsers += iteratorAssumeInstr.outValue().numberOfUsers() - 1;
    }
    if (numNonAliasIteratorUsers != 2) {
      return null;
    }
    return new AnalysisResult(
        iteratorInstr, hasNextInstr, ifInstr, nextInstr, listAssumeInstr, iteratorAssumeInstr);
  }

  private boolean isHasNextMethod(DexMethod method) {
    return method.match(hasNextProto, hasNextName);
  }

  private boolean isNextMethod(DexMethod method) {
    return method.getName().isIdenticalTo(nextName)
        && method.getProto().getParameters().isEmpty()
        && method.getReturnType().isClassType();
  }

  private void rewriteInstance(IRCode code, AnalysisResult analysisResult) {
    InvokeMethodWithReceiver iteratorInstr = analysisResult.iteratorInstr;
    InvokeMethodWithReceiver hasNextInstr = analysisResult.hasNextInstr;
    If ifInstr = analysisResult.ifInstr;
    InvokeMethodWithReceiver nextInstr = analysisResult.nextInstr;
    Assume listAssumeInstr = analysisResult.listAssumeInstr;
    Assume iteratorAssumeInstr = analysisResult.iteratorAssumeInstr;
    Value listValue = iteratorInstr.getReceiver();

    // Add the "int i = 0;".
    // Add it before size() since it's a non-throwing instruction (no need to split blocks).
    ConstNumber initIndexInstr = code.createIntConstant(0);
    initIndexInstr.setPosition(iteratorInstr.getPosition());
    iteratorInstr.getBlock().getInstructions().addBefore(initIndexInstr, iteratorInstr);

    // Replace: "list.iterator()" with "list.size()".
    DexType listType = listValue.getType().asClassType().toDexType(dexItemFactory);
    DexClass listClass = appView().definitionFor(listType);
    Value sizeValue = code.createValue(TypeElement.getInt());
    List<Value> sizeArgs = Collections.singletonList(listValue);
    // Use invoke-virtual when list is a non-abstract concrete class.
    MethodResolutionResult resolvedSizeMethod =
        listClass == null || listClass.isInterface()
            ? null
            : appInfo().resolveMethodOnClass(listClass, sizeMethod);
    InvokeMethodWithReceiver sizeInstr =
        resolvedSizeMethod == null || resolvedSizeMethod.getResolvedHolder().isInterface()
            ? new InvokeInterface(sizeMethod, sizeValue, sizeArgs)
            : new InvokeVirtual(
                resolvedSizeMethod.getResolvedMethod().getReference(), sizeValue, sizeArgs);
    iteratorInstr.replace(sizeInstr);

    // Update List's assume-not-null to update its origin.
    if (listAssumeInstr != null) {
      Assume newAssume =
          Assume.create(
              DynamicType.definitelyNotNull(),
              listAssumeInstr.outValue(),
              listValue,
              sizeInstr,
              appView,
              code.context());
      listAssumeInstr.replace(newAssume);
      listValue = newAssume.outValue();
    }

    // Figure out which block comes before the loop target, for Phi purposes.
    BasicBlock phiBlock = hasNextInstr.getBlock();
    BasicBlock blockBeforePhi = sizeInstr.getBlock();
    while (true) {
      // Walk through assume block and possible empty block.
      BasicBlock nextBlock = blockBeforePhi.getUniqueNormalSuccessor();
      if (nextBlock == phiBlock) {
        break;
      }
      blockBeforePhi = nextBlock;
    }

    // Replace if-eqz with if-ge.
    Phi indexPhi = code.createPhi(phiBlock, TypeElement.getInt());
    IfType newIfType = ifInstr.getType() == IfType.EQ ? IfType.GE : IfType.LT;
    If newIf = new If(newIfType, ImmutableList.of(indexPhi, sizeValue));
    ifInstr.replace(newIf);

    // Replace "x = iterator.next()" with "x = list.get(i)".
    Value elementValue = nextInstr.outValue();
    ImmutableList<Value> getArgs = ImmutableList.of(listValue, indexPhi);
    // Use invoke-virtual when list is a non-abstract concrete class.
    MethodResolutionResult resolvedGetMethod =
        listClass == null || listClass.isInterface()
            ? null
            : appInfo().resolveMethodOnClass(listClass, getMethod);
    InvokeMethodWithReceiver getInstr =
        resolvedGetMethod == null || resolvedGetMethod.getResolvedHolder().isInterface()
            ? new InvokeInterface(getMethod, elementValue, getArgs)
            : new InvokeVirtual(
                resolvedGetMethod.getResolvedMethod().getReference(), elementValue, getArgs);
    nextInstr.replace(getInstr);

    // Add "i = i + 1" right after "x = iterator.next()".
    Instruction toInsertBefore = nextInstr.getNext();
    if (toInsertBefore.isGoto()) {
      // Split in the case of a subsequent loop or catch handlers.
      toInsertBefore.getBlock().split(code, true, toInsertBefore);
    }
    ConstNumber one = code.createIntConstant(1);
    one.setPosition(nextInstr.getPosition());
    toInsertBefore.getBlock().getInstructions().addBefore(one, toInsertBefore);
    Add incrementIndexInstr =
        Add.createNonNormalized(
            NumericType.INT, code.createValue(TypeElement.getInt()), indexPhi, one.outValue());
    incrementIndexInstr.setPosition(nextInstr.getPosition());
    toInsertBefore.getBlock().getInstructions().addBefore(incrementIndexInstr, toInsertBefore);

    // Delete iterator assume now that .next() has been removed.
    if (iteratorAssumeInstr != null) {
      iteratorAssumeInstr.removeOrReplaceByDebugLocalRead();
    }

    // Delete iterator.hasNext() now that old If was removed.
    hasNextInstr.removeOrReplaceByDebugLocalRead();

    // Populate the index phi.
    for (BasicBlock b : phiBlock.getPredecessors()) {
      if (b == blockBeforePhi) {
        indexPhi.appendOperand(initIndexInstr.outValue());
      } else {
        indexPhi.appendOperand(incrementIndexInstr.outValue());
      }
    }
  }

  private static class AnalysisResult {

    public final InvokeMethodWithReceiver iteratorInstr;
    public final InvokeMethodWithReceiver hasNextInstr;
    public final If ifInstr;
    public final InvokeMethodWithReceiver nextInstr;
    private final Assume listAssumeInstr;
    private final Assume iteratorAssumeInstr;

    public AnalysisResult(
        InvokeMethodWithReceiver iteratorInstr,
        InvokeMethodWithReceiver hasNextInstr,
        If ifInstr,
        InvokeMethodWithReceiver nextInstr,
        Assume listAssumeInstr,
        Assume iteratorAssumeInstr) {
      this.iteratorInstr = iteratorInstr;
      this.hasNextInstr = hasNextInstr;
      this.ifInstr = ifInstr;
      this.nextInstr = nextInstr;
      this.listAssumeInstr = listAssumeInstr;
      this.iteratorAssumeInstr = iteratorAssumeInstr;
    }
  }
}
