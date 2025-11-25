// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.outliner.bottomup;

import static com.android.tools.r8.graph.DexAnnotation.VISIBILITY_BUILD;
import static com.android.tools.r8.graph.DexClassAndMethod.asProgramMethodOrNull;

import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexAnnotation;
import com.android.tools.r8.graph.DexAnnotationElement;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexEncodedAnnotation;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.InvalidCode;
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
import com.android.tools.r8.ir.desugar.itf.InterfaceDesugaringSyntheticHelper;
import com.android.tools.r8.lightir.LirCode;
import com.android.tools.r8.lightir.LirConstant;
import com.android.tools.r8.synthesis.SyntheticItems;
import com.android.tools.r8.utils.IterableUtils;
import com.android.tools.r8.utils.structural.CompareToVisitor;
import com.android.tools.r8.utils.structural.HashingVisitor;
import com.google.common.collect.ConcurrentHashMultiset;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multiset.Entry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class Outline implements LirConstant {

  private List<AbstractValue> arguments;
  private LirCode<?> lirCode;
  private DexProto proto;

  private final Multiset<DexMethod> users = ConcurrentHashMultiset.create();

  // If this is an outline in base, and there are equivalent outlines in features, then the outlines
  // from features will be removed and merged into the outline in base. This field stores the merged
  // outlines.
  //
  // This is always null in D8.
  private List<Outline> children;
  private Outline parent;

  private ProgramMethod materializedOutlineMethod;

  Outline(LirCode<?> lirCode, DexProto proto) {
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

  public void clear() {
    // Clear the state of this outline since it has been merged with its parent.
    assert parent != null;
    arguments = null;
    lirCode = null;
    proto = null;
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

  // Returns this outline and all outlines that have been merged into this outline.
  public Iterable<Outline> getAllOutlines() {
    if (children == null) {
      return Collections.singletonList(this);
    }
    return IterableUtils.append(children, this);
  }

  public List<AbstractValue> getArguments() {
    return arguments;
  }

  public List<Outline> getChildren() {
    return children != null ? children : Collections.emptyList();
  }

  public LirCode<?> getLirCode() {
    assert verifyNotMerged();
    return lirCode;
  }

  @Override
  public LirConstantOrder getLirConstantOrder() {
    return LirConstantOrder.OUTLINE;
  }

  public ProgramMethod getMaterializedOutlineMethod() {
    return materializedOutlineMethod;
  }

  public int getNumberOfUsers() {
    int result = users.size();
    if (children != null) {
      for (Outline child : children) {
        result += child.users.size();
      }
    }
    return result;
  }

  public Outline getParentOrSelf() {
    return parent != null ? parent : this;
  }

  public DexProto getProto() {
    assert verifyNotMerged();
    return proto;
  }

  public RewrittenPrototypeDescription getProtoChanges() {
    assert verifyNotMerged();
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
    assert verifyNotMerged();
    return proto.withoutParameters((i, p) -> isArgumentConstant(i), factory);
  }

  public ProgramMethod getSynthesizingContext(AppView<?> appView) {
    assert verifyNotMerged();
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

  public void updateUsers(
      AppView<?> appView, Map<DexMethod, DexMethod> forcefullyMovedLambdaMethods) {
    Iterator<Entry<DexMethod>> iterator = users.entrySet().iterator();
    Multiset<DexMethod> newUsers = null;
    while (iterator.hasNext()) {
      Entry<DexMethod> entry = iterator.next();
      DexMethod user = entry.getElement();
      DexMethod newUser = forcefullyMovedLambdaMethods.getOrDefault(user, user);
      if (newUser.isIdenticalTo(user)) {
        newUser = getUserAfterInterfaceMethodDesugaring(user, appView);
      }
      if (newUser.isNotIdenticalTo(user)) {
        iterator.remove();
        if (newUsers == null) {
          newUsers = HashMultiset.create();
        }
        newUsers.add(newUser, entry.getCount());
      }
    }
    if (newUsers != null) {
      for (Entry<DexMethod> entry : newUsers.entrySet()) {
        users.add(entry.getElement(), entry.getCount());
      }
    }
  }

  private DexMethod getUserAfterInterfaceMethodDesugaring(DexMethod user, AppView<?> appView) {
    ProgramMethod userMethod = asProgramMethodOrNull(appView.definitionFor(user));
    if (userMethod == null) {
      throw new Unreachable("Unexpected missing method: " + user.toSourceString());
    }
    if (!InvalidCode.isInvalidCode(userMethod.getDefinition().getCode())) {
      // Not moved by interface method desugaring.
      return user;
    }
    DexMethod newUser;
    if (userMethod.getAccessFlags().isStatic()) {
      newUser =
          InterfaceDesugaringSyntheticHelper.staticAsMethodOfCompanionClass(
              userMethod.getReference(), appView.dexItemFactory());
    } else if (userMethod.getAccessFlags().isPrivate()) {
      newUser =
          InterfaceDesugaringSyntheticHelper.privateAsMethodOfCompanionClass(
              userMethod.getReference(), appView.dexItemFactory());
    } else {
      newUser =
          InterfaceDesugaringSyntheticHelper.defaultAsMethodOfCompanionClass(
              userMethod.getReference(), appView.dexItemFactory());
    }
    if (appView.definitionFor(newUser) == null) {
      throw new Unreachable("Unexpected missing method: " + newUser.toSourceString());
    }
    return newUser;
  }

  public boolean hasConstantArgument() {
    assert verifyNotMerged();
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
    assert verifyNotMerged();
    return !arguments.get(index).isUnknown();
  }

  public boolean isMaterialized() {
    return materializedOutlineMethod != null;
  }

  public boolean isStringBuilderToStringOutline() {
    return !isThrowOutline();
  }

  public boolean isThrowOutline() {
    return proto.getReturnType().isVoidType();
  }

  public ProgramMethod materialize(
      AppView<?> appView, MethodProcessingContext methodProcessingContext) {
    assert verifyNotMerged();
    SyntheticItems syntheticItems = appView.getSyntheticItems();
    materializedOutlineMethod =
        syntheticItems.createMethod(
            kinds -> kinds.BOTTOM_UP_OUTLINE,
            methodProcessingContext.createUniqueContext(),
            appView,
            builder ->
                builder
                    .setAccessFlags(MethodAccessFlags.createPublicStaticSynthetic())
                    .setAnnotations(createAnnotations(appView))
                    // TODO(b/434769547): The API level of the code may be higher than the min-api.
                    //   This currently doesn't matter since outlining runs after API outlining.
                    .setApiLevelForCode(
                        appView.apiLevelCompute().computeInitialMinApiLevel(appView.options()))
                    .setCode(methodSig -> lirCode)
                    .setProto(getOptimizedProto(appView.dexItemFactory())));
    for (Outline child : getChildren()) {
      child.materializedOutlineMethod = materializedOutlineMethod;
    }
    return materializedOutlineMethod;
  }

  private DexAnnotationSet createAnnotations(AppView<?> appView) {
    if (appView.options().getBottomUpOutlinerOptions().neverCompile) {
      DexItemFactory factory = appView.dexItemFactory();
      DexEncodedAnnotation encodedAnnotation =
          new DexEncodedAnnotation(
              factory.annotationNeverCompile, DexAnnotationElement.EMPTY_ARRAY);
      DexAnnotation annotation = new DexAnnotation(VISIBILITY_BUILD, encodedAnnotation);
      return DexAnnotationSet.create(new DexAnnotation[] {annotation});
    }
    return DexAnnotationSet.empty();
  }

  public void merge(Outline outline) {
    for (int i = 0; i < arguments.size(); i++) {
      if (!arguments.get(i).equals(outline.arguments.get(i))) {
        arguments.set(i, AbstractValue.unknown());
      }
    }
    if (children == null) {
      children = new ArrayList<>();
    }
    children.add(outline);
    outline.parent = this;
    outline.clear();
  }

  public boolean verifyNotMerged() {
    assert parent == null;
    return true;
  }
}
