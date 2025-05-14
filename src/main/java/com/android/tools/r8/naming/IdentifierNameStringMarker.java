// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import static com.android.tools.r8.naming.IdentifierNameStringUtils.getPositionOfFirstConstString;
import static com.android.tools.r8.naming.IdentifierNameStringUtils.identifyIdentifier;
import static com.android.tools.r8.naming.IdentifierNameStringUtils.inferMemberOrTypeFromNameString;
import static com.android.tools.r8.naming.IdentifierNameStringUtils.isClassNameComparison;
import static com.android.tools.r8.naming.IdentifierNameStringUtils.isReflectionMethod;

import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.Definition;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMember;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexValue.DexItemBasedValueString;
import com.android.tools.r8.graph.DexValue.DexValueString;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.ConstString;
import com.android.tools.r8.ir.code.DexItemBasedConstString;
import com.android.tools.r8.ir.code.FieldPut;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InstancePut;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.Invoke;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.StaticPut;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.ir.conversion.passes.CodeRewriterPass;
import com.android.tools.r8.ir.conversion.passes.result.CodeRewriterResult;
import com.android.tools.r8.naming.dexitembasedstring.ClassNameComputationInfo;
import com.android.tools.r8.naming.identifiernamestring.IdentifierNameStringLookupResult;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.TextPosition;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.ArrayUtils;
import com.android.tools.r8.utils.StringDiagnostic;
import com.android.tools.r8.utils.ThreadUtils;
import com.google.common.collect.Streams;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

public class IdentifierNameStringMarker extends CodeRewriterPass<AppInfoWithLiveness> {

  private final Object2BooleanMap<DexMember<?, ?>> identifierNameStrings;

  public IdentifierNameStringMarker(AppView<AppInfoWithLiveness> appView) {
    super(appView);
    this.identifierNameStrings = appView.appInfo().identifierNameStrings;
  }

  @Override
  protected CodeRewriterResult rewriteCode(
      IRCode code,
      MethodProcessor methodProcessor,
      MethodProcessingContext methodProcessingContext) {
    return decoupleIdentifierNameStringsInBlocks(code, null);
  }

  @Override
  protected boolean shouldRewriteCode(IRCode code, MethodProcessor methodProcessor) {
    return code.metadata().mayHaveConstString();
  }

  @Override
  protected String getRewriterId() {
    return "IdentifierNameStringMarker";
  }

  public void decoupleIdentifierNameStringsInFields(
      ExecutorService executorService) throws ExecutionException {
    ThreadUtils.processItems(
        appView.appInfo().classes(),
        clazz -> {
          for (DexEncodedField field : clazz.staticFields()) {
            decoupleIdentifierNameStringInStaticField(field);
          }
        },
        appView.options().getThreadingModule(),
        executorService);
  }

  private void decoupleIdentifierNameStringInStaticField(DexEncodedField encodedField) {
    assert encodedField.accessFlags.isStatic();
    if (!identifierNameStrings.containsKey(encodedField.getReference())) {
      return;
    }
    DexValueString staticValue = encodedField.getStaticValue().asDexValueString();
    if (staticValue == null) {
      return;
    }
    DexString original = staticValue.getValue();
    Definition itemBasedString = inferMemberOrTypeFromNameString(appView(), original);
    if (itemBasedString != null) {
      encodedField.setStaticValue(
          new DexItemBasedValueString(
              itemBasedString.getReference(), ClassNameComputationInfo.none()));
    }
  }

  public CodeRewriterResult decoupleIdentifierNameStringsInBlocks(
      IRCode code, Set<BasicBlock> blocks) {
    CodeRewriterResult result = CodeRewriterResult.NO_CHANGE;
    if (!code.metadata().mayHaveConstString()) {
      return result;
    }
    ListIterator<BasicBlock> blockIterator = code.listIterator();
    while (blockIterator.hasNext()) {
      BasicBlock block = blockIterator.next();
      if (blocks != null && !blocks.contains(block)) {
        continue;
      }
      InstructionListIterator iterator = block.listIterator();
      while (iterator.hasNext()) {
        Instruction instruction = iterator.next();
        // v_n <- "x.y.z" // in.definition
        // ...
        // ... <- ... v_n ..
        // ...
        // this.fld <- v_n // fieldPut
        //
        //   ~>
        //
        // ...
        // v_n' <- DexItemBasedString("Lx/y/z;") // decoupled
        // this.fld <- v_n' // fieldPut
        if (instruction.isFieldPut()) {
          FieldPut fieldPut = instruction.asFieldPut();
          DexReference itemBasedString = getItemBasedStringForFieldPut(code, fieldPut);
          if (itemBasedString != null) {
            iterator =
                decoupleIdentifierNameStringForFieldPutInstruction(
                    code, blockIterator, iterator, fieldPut, itemBasedString);
            result = CodeRewriterResult.HAS_CHANGED;
          }
        } else if (instruction.isInvokeMethod()) {
          InvokeMethod invoke = instruction.asInvokeMethod();
          iterator =
              decoupleIdentifierNameStringForInvokeInstruction(
                  code, blockIterator, iterator, invoke);
          result = CodeRewriterResult.HAS_CHANGED;
        }
      }
    }
    if (result.hasChanged().isPossiblyTrue()) {
      code.removeRedundantBlocks();
    }
    return result;
  }

  private DexReference getItemBasedStringForFieldPut(IRCode code, FieldPut fieldPut) {
    DexField field = fieldPut.getField();
    if (!identifierNameStrings.containsKey(field)) {
      return null;
    }
    Value in = fieldPut.value();
    if (in.isDexItemBasedConstString()) {
      return null;
    }
    if (!in.isConstString()) {
      warnUndeterminedIdentifierIfNecessary(
          field, code.context(), fieldPut.asFieldInstruction(), null);
      return null;
    }
    DexString original = in.getConstInstruction().asConstString().getValue();
    Definition itemBasedString = inferMemberOrTypeFromNameString(appView(), original);
    if (itemBasedString == null) {
      warnUndeterminedIdentifierIfNecessary(
          field, code.context(), fieldPut.asFieldInstruction(), original);
      return null;
    }
    return itemBasedString.getReference();
  }

  private InstructionListIterator decoupleIdentifierNameStringForFieldPutInstruction(
      IRCode code,
      ListIterator<BasicBlock> blocks,
      InstructionListIterator iterator,
      FieldPut fieldPut,
      DexReference itemBasedString) {
    // Move the cursor back to $fieldPut
    assert iterator.peekPrevious() == fieldPut;
    iterator.previous();
    // Prepare $decoupled just before $fieldPut
    Value newIn = code.createValue(fieldPut.value().getType(), fieldPut.value().getLocalInfo());
    DexItemBasedConstString decoupled =
        new DexItemBasedConstString(newIn, itemBasedString, ClassNameComputationInfo.none());
    decoupled.setPosition(fieldPut.getPosition());
    // If the current block has catch handler, split into two blocks.
    // Because const-string we're about to add is also a throwing instr, we need to split
    // before adding it.
    BasicBlock block = fieldPut.getBlock();
    BasicBlock blockWithFieldInstruction =
        block.hasCatchHandlers() ? iterator.split(code, blocks) : block;
    if (blockWithFieldInstruction != block) {
      // If we split, add const-string at the end of the currently visiting block.
      iterator = block.listIterator(block.getInstructions().size() - 1);
      iterator.add(decoupled);
      // Restore the cursor and block.
      iterator = blockWithFieldInstruction.listIterator();
      assert iterator.peekNext() == fieldPut;
      iterator.next();
    } else {
      // Otherwise, just add it to the current block at the position of the iterator.
      iterator.add(decoupled);
      // Restore the cursor.
      assert iterator.peekNext() == fieldPut;
      iterator.next();
    }
    if (fieldPut.isStaticPut()) {
      iterator.replaceCurrentInstruction(new StaticPut(newIn, fieldPut.getField()));
    } else {
      assert fieldPut.isInstancePut();
      InstancePut instancePut = fieldPut.asInstancePut();
      iterator.replaceCurrentInstruction(
          new InstancePut(fieldPut.getField(), instancePut.object(), newIn));
    }
    return iterator;
  }

  private InstructionListIterator decoupleIdentifierNameStringForInvokeInstruction(
      IRCode code,
      ListIterator<BasicBlock> blocks,
      InstructionListIterator iterator,
      InvokeMethod invoke) {
    DexMethod invokedMethod = invoke.getInvokedMethod();
    boolean isClassNameComparison = isClassNameComparison(invoke, appView.dexItemFactory());
    if (!identifierNameStrings.containsKey(invokedMethod) && !isClassNameComparison) {
      return iterator;
    }
    List<Value> ins = invoke.arguments();
    Value[] changes = new Value[ins.size()];
    if (isReflectionMethod(appView.dexItemFactory(), invokedMethod) || isClassNameComparison) {
      IdentifierNameStringLookupResult<?> identifierLookupResult =
          identifyIdentifier(invoke, appView(), code.context());
      if (identifierLookupResult == null) {
        warnUndeterminedIdentifierIfNecessary(invokedMethod, code.context(), invoke, null);
        return iterator;
      }

      int identifierPosition = getIdentifierPositionInArguments(invoke);
      assert identifierPosition >= 0;

      Value in = invoke.arguments().get(identifierPosition);
      if (in.definition.isDexItemBasedConstString()) {
        return iterator;
      }

      // Prepare $decoupled just before $invoke
      Value newIn = code.createValue(in.getType(), in.getLocalInfo());
      DexItemBasedConstString decoupled =
          new DexItemBasedConstString(
              newIn, identifierLookupResult.getReference(), ClassNameComputationInfo.none());
      changes[identifierPosition] = newIn;

      if (in.numberOfAllUsers() == 1) {
        // Simply replace the existing ConstString by a DexItemBasedConstString. No need to check
        // for catch handlers, as this is replacing one throwing instruction with another.
        ConstString constString = in.definition.asConstString();
        if (constString.getBlock() == invoke.getBlock()) {
          iterator.previousUntil(instruction -> instruction == constString);
          Instruction current = iterator.next();
          assert current == constString;
          iterator.replaceCurrentInstruction(decoupled);
          iterator.nextUntil(instruction -> instruction == invoke);
        } else {
          in.definition.replace(decoupled);
        }
      } else {
        decoupled.setPosition(invoke.getPosition());

        // Move the cursor back to $invoke
        assert iterator.peekPrevious() == invoke;
        iterator.previous();
        // If the current block has catch handler, split into two blocks.
        // Because const-string we're about to add is also a throwing instr, we need to split
        // before adding it.
        BasicBlock block = invoke.getBlock();
        BasicBlock blockWithInvoke =
            block.hasCatchHandlers() ? iterator.split(code, blocks) : block;
        if (blockWithInvoke != block) {
          // If we split, add const-string at the end of the currently visiting block.
          iterator = block.listIterator(block.getInstructions().size());
          iterator.previous();
          iterator.add(decoupled);
          // Restore the cursor and block.
          iterator = blockWithInvoke.listIterator();
          assert iterator.peekNext() == invoke;
          iterator.next();
        } else {
          // Otherwise, just add it to the current block at the position of the iterator.
          iterator.add(decoupled);
          // Restore the cursor.
          assert iterator.peekNext() == invoke;
          iterator.next();
        }
      }
    } else {
      // For general invoke. Multiple arguments can be string literals to be renamed.
      for (int i = 0; i < ins.size(); i++) {
        Value in = ins.get(i);
        if (in.getType().isNullType()) {
          continue;
        }
        if (!in.isConstString()) {
          warnUndeterminedIdentifierIfNecessary(invokedMethod, code.context(), invoke, null);
          continue;
        }
        DexString original = in.getConstInstruction().asConstString().getValue();
        Definition itemBasedString = inferMemberOrTypeFromNameString(appView(), original);
        if (itemBasedString == null) {
          warnUndeterminedIdentifierIfNecessary(invokedMethod, code.context(), invoke, original);
          continue;
        }
        // Move the cursor back to $invoke
        assert iterator.peekPrevious() == invoke;
        iterator.previous();
        // Prepare $decoupled just before $invoke
        Value newIn = code.createValue(in.getType(), in.getLocalInfo());
        DexItemBasedConstString decoupled =
            new DexItemBasedConstString(
                newIn, itemBasedString.getReference(), ClassNameComputationInfo.none());
        decoupled.setPosition(invoke.getPosition());
        changes[i] = newIn;
        // If the current block has catch handler, split into two blocks.
        // Because const-string we're about to add is also a throwing instr, we need to split
        // before adding it.
        BasicBlock block = invoke.getBlock();
        BasicBlock blockWithInvoke =
            block.hasCatchHandlers() ? iterator.split(code, blocks) : block;
        if (blockWithInvoke != block) {
          // If we split, add const-string at the end of the currently visiting block.
          iterator = block.listIterator(block.getInstructions().size());
          iterator.previous();
          iterator.add(decoupled);
          // Restore the cursor and block.
          iterator = blockWithInvoke.listIterator();
          assert iterator.peekNext() == invoke;
          iterator.next();
        } else {
          // Otherwise, just add it to the current block at the position of the iterator.
          iterator.add(decoupled);
          // Restore the cursor.
          assert iterator.peekNext() == invoke;
          iterator.next();
        }
      }
    }
    if (ArrayUtils.none(changes, Objects::nonNull)) {
      return iterator;
    }
    List<Value> newIns =
        Streams.mapWithIndex(
                ins.stream(),
                (in, index) -> changes[(int) index] != null ? changes[(int) index] : in)
            .collect(Collectors.toList());
    iterator.replaceCurrentInstruction(
        Invoke.create(
            invoke.getType(), invokedMethod, invokedMethod.proto, invoke.outValue(), newIns));
    return iterator;
  }

  @SuppressWarnings("ReferenceEquality")
  private int getIdentifierPositionInArguments(InvokeMethod invoke) {
    DexType returnType = invoke.getReturnType();
    if (isClassNameComparison(invoke, appView.dexItemFactory())) {
      return getPositionOfFirstConstString(invoke);
    }

    boolean isClassForName = returnType == appView.dexItemFactory().classType;
    if (isClassForName) {
      assert appView.dexItemFactory().classMethods
          .isReflectiveClassLookup(invoke.getInvokedMethod());
      return 0;
    }

    boolean isReferenceFieldUpdater =
        returnType == appView.dexItemFactory().referenceFieldUpdaterType;
    if (isReferenceFieldUpdater) {
      assert invoke.getInvokedMethod()
          == appView.dexItemFactory().atomicFieldUpdaterMethods.referenceUpdater;
      return 2;
    }

    return 1;
  }

  private void warnUndeterminedIdentifierIfNecessary(
      DexReference member, ProgramMethod method, Instruction instruction, DexString original) {
    assert member.isDexField() || member.isDexMethod();
    // Only issue warnings for -identifiernamestring rules explicitly added by the user.
    boolean matchedByExplicitRule = identifierNameStrings.getBoolean(member);
    if (!matchedByExplicitRule) {
      return;
    }
    // Undetermined identifiers matter only if minification is enabled.
    if (!appView.options().isMinifying()) {
      return;
    }
    Origin origin = method.getOrigin();
    String kind = member.isDexField() ? "field" : "method";
    String originalMessage =
        original == null
            ? "what identifier string flows to "
            : "what '" + original + "' refers to, which flows to ";
    String message =
        "Cannot determine " + originalMessage + member.toSourceString()
            + " that is specified in -identifiernamestring rules."
            + " Thus, not all identifier strings flowing to that " + kind
            + " are renamed, which can cause resolution failures at runtime.";
    StringDiagnostic diagnostic =
        instruction.getPosition().getLine() >= 1
            ? new StringDiagnostic(
                message, origin, new TextPosition(0, instruction.getPosition().getLine(), 1))
            : new StringDiagnostic(message, origin);
    appView.options().reporter.warning(diagnostic);
  }
}
