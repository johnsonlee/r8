// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.outliner.exceptions;

import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.proto.ArgumentInfoCollection;
import com.android.tools.r8.graph.proto.RemovedArgumentInfo;
import com.android.tools.r8.graph.proto.RewrittenPrototypeDescription;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.AbstractValueFactory;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.ConstString;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.lightir.LirCode;
import com.android.tools.r8.lightir.LirConstant;
import com.android.tools.r8.synthesis.SyntheticItems;
import com.android.tools.r8.utils.structural.CompareToVisitor;
import com.android.tools.r8.utils.structural.HashingVisitor;
import com.google.common.collect.ConcurrentHashMultiset;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multiset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ThrowBlockOutline implements LirConstant {

  private final List<AbstractValue> arguments;
  private final LirCode<?> lirCode;
  private final DexProto proto;
  private final Multiset<DexMethod> users = ConcurrentHashMultiset.create();

  private ProgramMethod materializedOutlineMethod;

  ThrowBlockOutline(LirCode<?> lirCode, DexProto proto) {
    this.arguments = proto.getArity() == 0 ? Collections.emptyList() : new ArrayList<>();
    this.lirCode = lirCode;
    this.proto = proto;
  }

  public void addUser(
      DexMethod user, List<Value> userArguments, AbstractValueFactory valueFactory) {
    assert userArguments.size() == proto.getArity();
    users.add(user);
    if (!userArguments.isEmpty()) {
      synchronized (this) {
        if (arguments.isEmpty()) {
          for (Value userArgument : userArguments) {
            arguments.add(encodeArgumentValue(userArgument, valueFactory));
          }
        } else {
          for (int i = 0; i < userArguments.size(); i++) {
            AbstractValue existingArgument = arguments.get(i);
            if (existingArgument.isUnknown()) {
              continue;
            }
            Value userArgument = userArguments.get(i);
            if (!existingArgument.equals(encodeArgumentValue(userArgument, valueFactory))) {
              arguments.set(i, AbstractValue.unknown());
            }
          }
        }
      }
    }
  }

  private AbstractValue encodeArgumentValue(Value value, AbstractValueFactory valueFactory) {
    if (value.isConstNumber()) {
      ConstNumber constNumber = value.getDefinition().asConstNumber();
      TypeElement type = value.getType();
      if (type.isReferenceType()) {
        return valueFactory.createNullValue(type);
      } else {
        return valueFactory.createSingleNumberValue(constNumber.getRawValue(), type);
      }
    } else if (value.isConstString()) {
      ConstString constString = value.getDefinition().asConstString();
      return valueFactory.createSingleStringValue(constString.getValue());
    }
    return AbstractValue.unknown();
  }

  public List<AbstractValue> getArguments() {
    return arguments;
  }

  @Override
  public LirConstantOrder getLirConstantOrder() {
    return LirConstantOrder.THROW_BLOCK_OUTLINE;
  }

  public ProgramMethod getMaterializedOutlineMethod() {
    return materializedOutlineMethod;
  }

  public int getNumberOfUsers() {
    return users.size();
  }

  public DexProto getProto() {
    return proto;
  }

  public RewrittenPrototypeDescription getProtoChanges() {
    assert hasConstantArgument();
    ArgumentInfoCollection.Builder argumentsInfoBuilder =
        ArgumentInfoCollection.builder().setArgumentInfosSize(proto.getArity());
    for (int i = 0; i < arguments.size(); i++) {
      if (isArgumentConstant(i)) {
        RemovedArgumentInfo removedArgumentInfo =
            RemovedArgumentInfo.builder()
                .setSingleValue(arguments.get(i).asSingleValue())
                .setType(proto.getParameter(i))
                .build();
        argumentsInfoBuilder.addArgumentInfo(i, removedArgumentInfo);
      }
    }
    return RewrittenPrototypeDescription.createForArgumentsInfo(argumentsInfoBuilder.build());
  }

  public DexProto getOptimizedProto(DexItemFactory factory) {
    return proto.withoutParameters((i, p) -> isArgumentConstant(i), factory);
  }

  public ProgramMethod getSynthesizingContext(AppView<?> appView) {
    DexMethod shortestUser = null;
    for (DexMethod user : users) {
      if (shortestUser == null) {
        shortestUser = user;
      } else {
        int userLength = user.getHolderType().getDescriptor().length();
        int shortestUserLength = shortestUser.getHolderType().getDescriptor().length();
        if (userLength < shortestUserLength) {
          shortestUser = user;
        } else if (userLength == shortestUserLength && user.compareTo(shortestUser) < 0) {
          shortestUser = user;
        }
      }
    }
    assert shortestUser != null;
    return appView.definitionFor(shortestUser).asProgramMethod();
  }

  public Multiset<DexMethod> getUsers() {
    return users;
  }

  public boolean hasConstantArgument() {
    return Iterables.any(arguments, argument -> !argument.isUnknown());
  }

  @Override
  public int internalLirConstantAcceptCompareTo(LirConstant other, CompareToVisitor visitor) {
    throw new Unreachable();
  }

  @Override
  public void internalLirConstantAcceptHashing(HashingVisitor visitor) {
    throw new Unreachable();
  }

  public boolean isArgumentConstant(int index) {
    return !arguments.get(index).isUnknown();
  }

  public boolean isMaterialized() {
    return materializedOutlineMethod != null;
  }

  public void materialize(AppView<?> appView, MethodProcessingContext methodProcessingContext) {
    SyntheticItems syntheticItems = appView.getSyntheticItems();
    materializedOutlineMethod =
        syntheticItems.createMethod(
            kinds -> kinds.THROW_BLOCK_OUTLINE,
            methodProcessingContext.createUniqueContext(),
            appView,
            builder ->
                builder
                    .setAccessFlags(MethodAccessFlags.createPublicStaticSynthetic())
                    .setCode(methodSig -> lirCode)
                    .setProto(getOptimizedProto(appView.dexItemFactory())));
  }
}
