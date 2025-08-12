// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.proto.ArgumentInfo;
import com.android.tools.r8.graph.proto.ArgumentInfoCollection;
import com.android.tools.r8.graph.proto.RemovedArgumentInfo;
import com.android.tools.r8.graph.proto.RewrittenPrototypeDescription;
import com.android.tools.r8.graph.proto.RewrittenTypeInfo;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.analysis.value.SingleValue;
import com.android.tools.r8.ir.code.Argument;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.MaterializingInstructionsInfo;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.Position.SourcePosition;
import com.android.tools.r8.ir.code.UnusedArgument;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.optimize.AffectedValues;
import com.android.tools.r8.utils.ArrayUtils;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

public class LensCodeArgumentRewriter {

  private final AppView<?> appView;

  public LensCodeArgumentRewriter(AppView<?> appView) {
    this.appView = appView;
  }

  // Applies the prototype changes of the current method to the argument instructions:
  // - Replaces constant arguments by their constant value and then removes the (now unused)
  //   argument instruction
  // - Removes unused arguments
  // - Updates the type of arguments whose type has been strengthened
  // TODO(b/270398965): Replace LinkedList.
  @SuppressWarnings("JdkObsolete")
  public void rewriteArguments(
      IRCode code,
      DexMethod originalMethodReference,
      RewrittenPrototypeDescription prototypeChanges,
      Set<Phi> affectedPhis,
      Set<UnusedArgument> unusedArguments) {
    AffectedValues affectedValues = new AffectedValues();
    ArgumentInfoCollection argumentInfoCollection = prototypeChanges.getArgumentInfoCollection();
    List<Instruction> argumentPostlude = new LinkedList<>();
    int oldArgumentIndex = 0;
    int nextArgumentIndex = 0;
    int numberOfRemovedArguments = 0;
    BasicBlock basicBlock = code.entryBlock();
    InstructionListIterator instructionIterator = basicBlock.listIterator();
    while (instructionIterator.hasNext()) {
      Instruction instruction = instructionIterator.next();
      if (!instruction.isArgument()) {
        break;
      }

      Argument argument = instruction.asArgument();
      ArgumentInfo argumentInfo = argumentInfoCollection.getArgumentInfo(oldArgumentIndex);
      if (argumentInfo.isRemovedArgumentInfo()) {
        rewriteRemovedArgument(
            code,
            instructionIterator,
            originalMethodReference,
            argument,
            argumentInfo.asRemovedArgumentInfo(),
            affectedPhis,
            affectedValues,
            argumentPostlude,
            unusedArguments);
        numberOfRemovedArguments++;
      } else {
        int newArgumentIndex =
            argumentInfoCollection.getNewArgumentIndex(oldArgumentIndex, numberOfRemovedArguments);
        Argument replacement;
        if (argumentInfo.isRewrittenTypeInfo()) {
          replacement =
              rewriteArgumentType(
                  code,
                  argument,
                  argumentInfo.asRewrittenTypeInfo(),
                  affectedPhis,
                  newArgumentIndex);
          argument.outValue().replaceUsers(replacement.outValue());
        } else if (newArgumentIndex != oldArgumentIndex) {
          replacement =
              Argument.builder()
                  .setIndex(newArgumentIndex)
                  .setFreshOutValue(code, argument.getOutType(), argument.getLocalInfo())
                  .setPosition(argument.getPosition())
                  .build();
          argument.outValue().replaceUsers(replacement.outValue());
        } else {
          replacement = argument;
        }
        if (newArgumentIndex == nextArgumentIndex) {
          // This is the right position for the argument. Insert it into the code at this position.
          if (replacement != argument) {
            instructionIterator.replaceCurrentInstruction(replacement);
          }
          nextArgumentIndex++;
        } else {
          // Due the a permutation of the argument order, this argument needs to be inserted at a
          // later point. Enqueue the argument into the argument postlude.
          instructionIterator.removeInstructionIgnoreOutValue();
          ListIterator<Instruction> argumentPostludeIterator = argumentPostlude.listIterator();
          while (argumentPostludeIterator.hasNext()) {
            Instruction current = argumentPostludeIterator.next();
            if (!current.isArgument()
                || replacement.getIndexRaw() < current.asArgument().getIndexRaw()) {
              argumentPostludeIterator.previous();
              break;
            }
          }
          argumentPostludeIterator.add(replacement);
        }
      }
      oldArgumentIndex++;
    }

    instructionIterator.previous();

    if (!argumentPostlude.isEmpty()) {
      for (Instruction instruction : argumentPostlude) {
        instructionIterator.add(instruction);
      }
    }

    affectedValues.narrowingWithAssumeRemoval(appView, code);
  }

  private Argument rewriteArgumentType(
      IRCode code,
      Argument argument,
      RewrittenTypeInfo rewrittenTypeInfo,
      Set<Phi> affectedPhis,
      int newArgumentIndex) {
    TypeElement rewrittenType = rewrittenTypeInfo.getNewType().toTypeElement(appView);
    Argument replacement =
        Argument.builder()
            .setIndex(newArgumentIndex)
            .setFreshOutValue(code, rewrittenType, argument.getLocalInfo())
            .setPosition(argument.getPosition())
            .build();
    affectedPhis.addAll(argument.outValue().uniquePhiUsers());
    return replacement;
  }

  private void rewriteRemovedArgument(
      IRCode code,
      InstructionListIterator instructionIterator,
      DexMethod originalMethodReference,
      Argument argument,
      RemovedArgumentInfo removedArgumentInfo,
      Set<Phi> affectedPhis,
      AffectedValues affectedValues,
      List<Instruction> argumentPostlude,
      Set<UnusedArgument> unusedArguments) {
    Instruction[] replacement;
    if (removedArgumentInfo.hasSingleValue()) {
      SingleValue singleValue = removedArgumentInfo.getSingleValue();
      TypeElement type =
          removedArgumentInfo.getType().isReferenceType() && singleValue.isNull()
              ? TypeElement.getNull()
              : removedArgumentInfo.getType().toTypeElement(appView);
      Position position =
          SourcePosition.builder().setLine(0).setMethod(originalMethodReference).build();
      replacement =
          singleValue.createMaterializingInstructions(
              appView,
              code,
              MaterializingInstructionsInfo.create(type, argument.getLocalInfo(), position));
    } else {
      TypeElement unusedArgumentType = removedArgumentInfo.getType().toTypeElement(appView);
      UnusedArgument unusedArgument =
          UnusedArgument.builder()
              .setFreshOutValue(code, unusedArgumentType)
              .setPosition(Position.none())
              .build();
      unusedArguments.add(unusedArgument);
      replacement = new Instruction[] {unusedArgument};
    }
    Value replacementValue = ArrayUtils.last(replacement).outValue();
    argument.outValue().replaceUsers(replacementValue, affectedValues);
    affectedPhis.addAll(replacementValue.uniquePhiUsers());
    Collections.addAll(argumentPostlude, replacement);
    instructionIterator.removeOrReplaceByDebugLocalRead();
  }
}
