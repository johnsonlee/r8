// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.fieldaccess;

import static com.android.tools.r8.ir.analysis.type.Nullability.definitelyNotNull;
import static com.android.tools.r8.utils.MapUtils.ignoreKey;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.FieldAccessInfo;
import com.android.tools.r8.graph.FieldAccessInfoCollection;
import com.android.tools.r8.graph.ObjectAllocationInfoCollection;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.type.ClassTypeElement;
import com.android.tools.r8.ir.analysis.type.DynamicType;
import com.android.tools.r8.ir.analysis.type.DynamicTypeWithUpperBound;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.AbstractValueFactory;
import com.android.tools.r8.ir.analysis.value.BottomValue;
import com.android.tools.r8.ir.analysis.value.SingleValue;
import com.android.tools.r8.ir.analysis.value.UnknownValue;
import com.android.tools.r8.ir.code.FieldInstruction;
import com.android.tools.r8.ir.code.InvokeDirect;
import com.android.tools.r8.ir.code.NewInstance;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.optimize.ClassInitializerDefaultsOptimization.ClassInitializerDefaultsResult;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedbackDelayed;
import com.android.tools.r8.ir.optimize.info.field.InstanceFieldArgumentInitializationInfo;
import com.android.tools.r8.ir.optimize.info.field.InstanceFieldInitializationInfo;
import com.android.tools.r8.ir.optimize.info.field.InstanceFieldInitializationInfoCollection;
import com.android.tools.r8.lightir.LirCode;
import com.android.tools.r8.lightir.LirInstructionView;
import com.android.tools.r8.lightir.LirOpcodes;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcreteArrayTypeValueState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcreteClassTypeValueState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcretePrimitiveTypeValueState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ValueState;
import com.android.tools.r8.optimize.argumentpropagation.utils.WideningUtils;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.Reference2IntMapUtils;
import com.android.tools.r8.utils.SetUtils;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.TraversalContinuation;
import com.android.tools.r8.utils.collections.ProgramFieldMap;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntMap.Entry;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

// TODO(b/330674939): Remove legacy field optimizations.
public class FieldAssignmentTracker {

  private final AbstractValueFactory abstractValueFactory;
  private final AppView<AppInfoWithLiveness> appView;
  private final DexItemFactory dexItemFactory;

  // A field access graph with edges from methods to the fields that they access. Edges are removed
  // from the graph as we process methods, such that we can conclude that all field writes have been
  // processed when a field no longer has any incoming edges.
  private final FieldAccessGraph fieldAccessGraph;

  // An object allocation graph with edges from methods to the classes they instantiate. Edges are
  // removed from the graph as we process methods, such that we can conclude that all allocation
  // sites have been seen when a class no longer has any incoming edges.
  private final ObjectAllocationGraph objectAllocationGraph;

  // Information about the fields in the program. If a field is not a key in the map then no writes
  // has been seen to the field.
  private final Map<DexEncodedField, ValueState> fieldStates = new ConcurrentHashMap<>();

  private final Map<DexProgramClass, ProgramFieldMap<AbstractValue>>
      abstractFinalInstanceFieldValues = new ConcurrentHashMap<>();

  private final Set<DexProgramClass> classesWithPrunedInstanceInitializers =
      ConcurrentHashMap.newKeySet();

  FieldAssignmentTracker(AppView<AppInfoWithLiveness> appView) {
    this.abstractValueFactory = appView.abstractValueFactory();
    this.appView = appView;
    this.dexItemFactory = appView.dexItemFactory();
    this.fieldAccessGraph = new FieldAccessGraph();
    this.objectAllocationGraph = new ObjectAllocationGraph();
  }

  public void initialize(ExecutorService executorService) throws ExecutionException {
    fieldAccessGraph.initialize(appView, executorService);
    objectAllocationGraph.initialize(appView);
    initializeAbstractInstanceFieldValues();
  }

  /**
   * For each class with known allocation sites, adds a mapping from clazz -> instance field ->
   * bottom.
   *
   * <p>If an entry (clazz, instance field) is missing in {@link #abstractFinalInstanceFieldValues},
   * it is interpreted as if we known nothing about the value of the field.
   */
  private void initializeAbstractInstanceFieldValues() {
    FieldAccessInfoCollection<?> fieldAccessInfos =
        appView.appInfo().getFieldAccessInfoCollection();
    ObjectAllocationInfoCollection objectAllocationInfos =
        appView.appInfo().getObjectAllocationInfoCollection();
    objectAllocationInfos.forEachClassWithKnownAllocationSites(
        (clazz, allocationSites) -> {
          if (appView.appInfo().isInstantiatedIndirectly(clazz)) {
            // TODO(b/147652121): Handle classes that are instantiated indirectly.
            return;
          }
          List<DexEncodedField> instanceFields = clazz.instanceFields();
          if (instanceFields.isEmpty()) {
            // No instance fields to track.
            return;
          }
          ProgramFieldMap<AbstractValue> abstractFinalInstanceFieldValuesForClass =
              ProgramFieldMap.create();
          clazz.forEachProgramInstanceField(
              field -> {
                if (field.isFinalOrEffectivelyFinal(appView)) {
                  FieldAccessInfo fieldAccessInfo = fieldAccessInfos.get(field.getReference());
                  if (fieldAccessInfo != null && !fieldAccessInfo.hasReflectiveAccess()) {
                    abstractFinalInstanceFieldValuesForClass.put(field, BottomValue.getInstance());
                  }
                }
              });
          if (!abstractFinalInstanceFieldValuesForClass.isEmpty()) {
            abstractFinalInstanceFieldValues.put(clazz, abstractFinalInstanceFieldValuesForClass);
          }
        });
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      clazz.forEachProgramField(
          field -> {
            FieldAccessInfo accessInfo = fieldAccessInfos.get(field.getReference());
            if (!appView.getKeepInfo(field).isValuePropagationAllowed(appView, field)
                || (accessInfo != null && accessInfo.isWrittenFromMethodHandle())) {
              fieldStates.put(field.getDefinition(), ValueState.unknown());
            }
          });
    }
  }

  @SuppressWarnings("ReferenceEquality")
  void acceptClassInitializerDefaultsResult(
      ClassInitializerDefaultsResult classInitializerDefaultsResult) {
    classInitializerDefaultsResult.forEachOptimizedField(
        (field, value) -> {
          DexType fieldType = field.getType();
          if (value.isDefault(field.getType())) {
            return;
          }
          assert fieldType.isClassType() || fieldType.isPrimitiveType();
          fieldStates.compute(
              field,
              (f, fieldState) -> {
                if (fieldState == null) {
                  AbstractValue abstractValue = value.toAbstractValue(abstractValueFactory);
                  if (fieldType.isClassType()) {
                    assert abstractValue.isSingleStringValue()
                        || abstractValue.isSingleDexItemBasedStringValue();
                    if (fieldType == dexItemFactory.stringType) {
                      return ConcreteClassTypeValueState.create(
                          abstractValue, DynamicType.definitelyNotNull());
                    } else {
                      ClassTypeElement nonNullableStringType =
                          dexItemFactory
                              .stringType
                              .toTypeElement(appView, definitelyNotNull())
                              .asClassType();
                      return ConcreteClassTypeValueState.create(
                          abstractValue, DynamicType.createExact(nonNullableStringType));
                    }
                  } else {
                    assert fieldType.isPrimitiveType();
                    return ConcretePrimitiveTypeValueState.create(abstractValue);
                  }
                }
                // If the field is already assigned outside the class initializer then just give up.
                return ValueState.unknown();
              });
        });
  }

  void recordFieldAccess(FieldInstruction instruction, ProgramField field) {
    if (instruction.isFieldPut()) {
      recordFieldPut(field, instruction.value());
    }
  }

  private void recordFieldPut(ProgramField field, Value value) {
    // For now only attempt to prove that fields are definitely null. In order to prove a single
    // value for fields that are not definitely null, we need to prove that the given field is never
    // read before it is written.
    AbstractValue abstractValue =
        value.isZero()
            ? abstractValueFactory.createDefaultValue(field.getType())
            : AbstractValue.unknown();
    Nullability nullability = value.getType().nullability();
    fieldStates.compute(
        field.getDefinition(),
        (f, fieldState) -> {
          if (fieldState == null || fieldState.isBottom()) {
            DexType fieldType = field.getType();
            if (fieldType.isArrayType()) {
              return ConcreteArrayTypeValueState.create(nullability);
            }
            if (fieldType.isPrimitiveType()) {
              return ConcretePrimitiveTypeValueState.create(abstractValue);
            }
            assert fieldType.isClassType();
            DynamicType dynamicType =
                WideningUtils.widenDynamicNonReceiverType(
                    appView,
                    value.getDynamicType(appView).withNullability(Nullability.maybeNull()),
                    field.getType());
            return ConcreteClassTypeValueState.create(abstractValue, dynamicType);
          }

          if (fieldState.isUnknown()) {
            return fieldState;
          }

          assert fieldState.isConcrete();

          if (fieldState.isArrayState()) {
            ConcreteArrayTypeValueState arrayFieldState = fieldState.asArrayState();
            return arrayFieldState.mutableJoin(appView, field, nullability);
          }

          if (fieldState.isPrimitiveState()) {
            ConcretePrimitiveTypeValueState primitiveFieldState = fieldState.asPrimitiveState();
            return primitiveFieldState.mutableJoin(appView, field, abstractValue);
          }

          assert fieldState.isClassState();

          ConcreteClassTypeValueState classFieldState = fieldState.asClassState();
          DexType inStaticType = null;
          return classFieldState.mutableJoin(
              appView, abstractValue, value.getDynamicType(appView), inStaticType, field);
        });
  }

  void recordAllocationSite(NewInstance instruction, DexProgramClass clazz, ProgramMethod context) {
    ProgramFieldMap<AbstractValue> abstractInstanceFieldValuesForClass =
        abstractFinalInstanceFieldValues.get(clazz);
    if (abstractInstanceFieldValuesForClass == null) {
      // We are not tracking the value of any of clazz' instance fields.
      return;
    }

    InvokeDirect invoke = instruction.getUniqueConstructorInvoke(dexItemFactory);
    if (invoke == null) {
      // We just lost track.
      abstractFinalInstanceFieldValues.remove(clazz);
      return;
    }

    DexClassAndMethod singleTarget = invoke.lookupSingleTarget(appView, context);
    if (singleTarget == null) {
      // We just lost track.
      abstractFinalInstanceFieldValues.remove(clazz);
      return;
    }

    InstanceFieldInitializationInfoCollection initializationInfoCollection =
        singleTarget
            .getDefinition()
            .getOptimizationInfo()
            .getInstanceInitializerInfo(invoke)
            .fieldInitializationInfos();

    // Synchronize on the lattice element (abstractInstanceFieldValuesForClass) in case we process
    // another allocation site of `clazz` concurrently.
    synchronized (abstractInstanceFieldValuesForClass) {
      abstractInstanceFieldValuesForClass.removeIf(
          (field, abstractValue, entry) -> {
            InstanceFieldInitializationInfo initializationInfo =
                initializationInfoCollection.get(field);
            if (initializationInfo.isArgumentInitializationInfo()) {
              InstanceFieldArgumentInitializationInfo argumentInitializationInfo =
                  initializationInfo.asArgumentInitializationInfo();
              Value argument =
                  invoke.arguments().get(argumentInitializationInfo.getArgumentIndex());
              AbstractValue argumentAbstractValue = argument.getAbstractValue(appView, context);
              abstractValue =
                  appView
                      .getAbstractValueFieldJoiner()
                      .join(abstractValue, argumentAbstractValue, field);
            } else if (initializationInfo.isSingleValue()) {
              SingleValue singleValueInitializationInfo = initializationInfo.asSingleValue();
              abstractValue =
                  appView
                      .getAbstractValueFieldJoiner()
                      .join(abstractValue, singleValueInitializationInfo, field);
            } else if (initializationInfo.isTypeInitializationInfo()) {
              // TODO(b/149732532): Not handled, for now.
              abstractValue = UnknownValue.getInstance();
            } else {
              assert initializationInfo.isUnknown();
              abstractValue = UnknownValue.getInstance();
            }

            assert !abstractValue.isBottom();
            entry.setValue(abstractValue);
            return abstractValue.isUnknown();
          });
    }
  }

  private void recordAllFieldPutsProcessed(
      ProgramField field, OptimizationFeedbackDelayed feedback) {
    ValueState fieldState =
        fieldStates.getOrDefault(field.getDefinition(), ValueState.bottom(field));
    AbstractValue abstractValue =
        fieldState.isBottom()
            ? appView.abstractValueFactory().createDefaultValue(field.getType())
            : fieldState.getAbstractValue(appView);
    if (abstractValue.isNonTrivial()) {
      feedback.recordFieldHasAbstractValue(field, appView, abstractValue);
    }

    if (fieldState.isClassState() && field.getOptimizationInfo().getDynamicType().isUnknown()) {
      ConcreteClassTypeValueState classFieldState = fieldState.asClassState();
      DynamicType dynamicType = classFieldState.getDynamicType();
      if (!dynamicType.isUnknown()) {
        assert WideningUtils.widenDynamicNonReceiverType(appView, dynamicType, field.getType())
            .equals(dynamicType);
        if (dynamicType.isNotNullType()) {
          feedback.markFieldHasDynamicType(field, dynamicType);
        } else {
          DynamicTypeWithUpperBound staticType = field.getType().toDynamicType(appView);
          if (dynamicType.asDynamicTypeWithUpperBound().strictlyLessThan(staticType, appView)) {
            feedback.markFieldHasDynamicType(field, dynamicType);
          }
        }
      }
    }

    if (!field.getAccessFlags().isStatic()) {
      recordAllInstanceFieldPutsProcessed(field, feedback);
    }
  }

  private void recordAllInstanceFieldPutsProcessed(
      ProgramField field, OptimizationFeedbackDelayed feedback) {
    if (!appView.appInfo().isInstanceFieldWrittenOnlyInInstanceInitializers(field)) {
      return;
    }
    DexProgramClass clazz = field.getHolder();
    if (classesWithPrunedInstanceInitializers.contains(clazz)) {
      // The current method is analyzing the instance-puts to the field in the instance initializers
      // of the holder class. Due to single caller inlining of instance initializers some of the
      // methods needed for the analysis may have been pruned from the app, in which case the
      // analysis result will not be conservative.
      return;
    }
    AbstractValue abstractValue = BottomValue.getInstance();
    for (DexEncodedMethod method : clazz.directMethods(DexEncodedMethod::isInstanceInitializer)) {
      InstanceFieldInitializationInfo fieldInitializationInfo =
          method
              .getOptimizationInfo()
              .getContextInsensitiveInstanceInitializerInfo()
              .fieldInitializationInfos()
              .get(field);
      if (fieldInitializationInfo.isSingleValue()) {
        abstractValue =
            appView
                .getAbstractValueFieldJoiner()
                .join(abstractValue, fieldInitializationInfo.asSingleValue(), field);
        if (abstractValue.isUnknown()) {
          break;
        }
      } else if (fieldInitializationInfo.isTypeInitializationInfo()) {
        // TODO(b/149732532): Not handled, for now.
        abstractValue = UnknownValue.getInstance();
        break;
      } else {
        assert fieldInitializationInfo.isArgumentInitializationInfo()
            || fieldInitializationInfo.isUnknown();
        abstractValue = UnknownValue.getInstance();
        break;
      }
    }

    assert !abstractValue.isBottom();

    if (!abstractValue.isUnknown()) {
      feedback.recordFieldHasAbstractValue(field, appView, abstractValue);
    }
  }

  private void recordAllAllocationsSitesProcessed(
      DexProgramClass clazz, OptimizationFeedbackDelayed feedback) {
    ProgramFieldMap<AbstractValue> abstractInstanceFieldValuesForClass =
        abstractFinalInstanceFieldValues.get(clazz);
    if (abstractInstanceFieldValuesForClass == null) {
      return;
    }

    clazz.traverseProgramInstanceFields(
        field -> {
          AbstractValue abstractValue =
              abstractInstanceFieldValuesForClass.getOrDefault(field, UnknownValue.getInstance());
          if (abstractValue.isBottom()) {
            feedback.modifyAppInfoWithLiveness(modifier -> modifier.removeInstantiatedType(clazz));
            return TraversalContinuation.doBreak();
          }
          if (abstractValue.isNonTrivial()) {
            feedback.recordFieldHasAbstractValue(field, appView, abstractValue);
          }
          return TraversalContinuation.doContinue();
        });
  }

  public void onMethodPruned(ProgramMethod method) {
    onMethodCodePruned(method);
  }

  public void onMethodCodePruned(ProgramMethod method) {
    if (method.getDefinition().isInstanceInitializer()) {
      classesWithPrunedInstanceInitializers.add(method.getHolder());
    }
  }

  public void waveDone(ProgramMethodSet wave, OptimizationFeedbackDelayed feedback) {
    // This relies on the instance initializer info in the method optimization feedback. It is
    // therefore important that the optimization info has been flushed in advance.
    assert feedback.noUpdatesLeft();
    for (ProgramMethod method : wave) {
      fieldAccessGraph.markProcessed(method, field -> recordAllFieldPutsProcessed(field, feedback));
      objectAllocationGraph.markProcessed(
          method, clazz -> recordAllAllocationsSitesProcessed(clazz, feedback));
    }
    feedback.refineAppInfoWithLiveness(appView.appInfo().withLiveness());
    feedback.updateVisibleOptimizationInfo();
  }

  static class FieldAccessGraph {

    // The fields written by each method.
    private final Map<DexEncodedMethod, List<ProgramField>> fieldWrites = new ConcurrentHashMap<>();

    // The number of writes that have not yet been processed per field.
    private final Reference2IntMap<DexEncodedField> pendingFieldWrites =
        new Reference2IntOpenHashMap<>();

    FieldAccessGraph() {}

    public void initialize(AppView<AppInfoWithLiveness> appView, ExecutorService executorService)
        throws ExecutionException {
      FieldAccessInfoCollection<?> fieldAccessInfoCollection =
          appView.appInfo().getFieldAccessInfoCollection();
      fieldAccessInfoCollection.forEach(
          info -> {
            ProgramField field =
                appView.appInfo().resolveField(info.getField()).getSingleProgramField();
            if (field != null && !info.isReadIndirectly() && !info.isWrittenIndirectly()) {
              pendingFieldWrites.put(field.getDefinition(), 0);
            }
          });

      // Find all write contexts for the fields of interest. First collect the fields of interest
      // for thread safe querying.
      Set<DexEncodedField> fieldsOfInterest =
          SetUtils.newIdentityHashSet(pendingFieldWrites.size());
      fieldsOfInterest.addAll(pendingFieldWrites.keySet());

      // Iterate all code objects.
      ThreadUtils.processItems(
          appView.appInfo().classes(),
          clazz -> {
            Reference2IntMap<DexEncodedField> threadLocalPendingFieldWrites =
                new Reference2IntOpenHashMap<>();
            threadLocalPendingFieldWrites.defaultReturnValue(0);
            clazz.forEachProgramMethodMatching(
                m -> m.hasCode() && m.getCode().isLirCode(),
                method -> {
                  LirCode<?> code = method.getDefinition().getCode().asLirCode();
                  Set<DexEncodedField> methodFieldWrites = Sets.newIdentityHashSet();
                  for (LirInstructionView instruction : code) {
                    if (instruction.getOpcode() == LirOpcodes.PUTFIELD
                        || instruction.getOpcode() == LirOpcodes.PUTSTATIC) {
                      DexField field =
                          (DexField) code.getConstantItem(instruction.getNextConstantOperand());
                      ProgramField resolvedField =
                          appView.appInfo().resolveField(field).getSingleProgramField();
                      if (resolvedField == null) {
                        continue;
                      }
                      DexEncodedField resolvedFieldDefinition = resolvedField.getDefinition();
                      if (fieldsOfInterest.contains(resolvedFieldDefinition)
                          && methodFieldWrites.add(resolvedFieldDefinition)) {
                        fieldWrites
                            .computeIfAbsent(method.getDefinition(), ignoreKey(ArrayList::new))
                            .add(resolvedField);
                        Reference2IntMapUtils.increment(
                            threadLocalPendingFieldWrites, resolvedFieldDefinition);
                      }
                    }
                  }
                });
            // Commit the thread local data to the shared data.
            synchronized (pendingFieldWrites) {
              for (Entry<DexEncodedField> entry :
                  threadLocalPendingFieldWrites.reference2IntEntrySet()) {
                Reference2IntMapUtils.increment(
                    pendingFieldWrites, entry.getKey(), entry.getIntValue());
              }
            }
          },
          appView.options().getThreadingModule(),
          executorService);
    }

    void markProcessed(ProgramMethod method, Consumer<ProgramField> allWritesSeenConsumer) {
      List<ProgramField> fieldWritesInMethod = fieldWrites.remove(method.getDefinition());
      if (fieldWritesInMethod != null) {
        for (ProgramField field : fieldWritesInMethod) {
          int numberOfPendingFieldWrites = pendingFieldWrites.removeInt(field.getDefinition()) - 1;
          if (numberOfPendingFieldWrites > 0) {
            pendingFieldWrites.put(field.getDefinition(), numberOfPendingFieldWrites);
          } else {
            allWritesSeenConsumer.accept(field);
          }
        }
      }
    }
  }

  static class ObjectAllocationGraph {

    // The classes instantiated by each method.
    private final Map<DexEncodedMethod, List<DexProgramClass>> objectAllocations =
        new IdentityHashMap<>();

    // The number of allocation sites that have not yet been processed per class.
    private final Reference2IntMap<DexProgramClass> pendingObjectAllocations =
        new Reference2IntOpenHashMap<>();

    ObjectAllocationGraph() {}

    public void initialize(AppView<AppInfoWithLiveness> appView) {
      ObjectAllocationInfoCollection objectAllocationInfos =
          appView.appInfo().getObjectAllocationInfoCollection();
      objectAllocationInfos.forEachClassWithKnownAllocationSites(
          (clazz, contexts) -> {
            for (DexEncodedMethod context : contexts) {
              objectAllocations.computeIfAbsent(context, ignore -> new ArrayList<>()).add(clazz);
            }
            pendingObjectAllocations.put(clazz, contexts.size());
          });
    }

    void markProcessed(
        ProgramMethod method, Consumer<DexProgramClass> allAllocationsSitesSeenConsumer) {
      List<DexProgramClass> allocationSitesInMethod = objectAllocations.get(method.getDefinition());
      if (allocationSitesInMethod != null) {
        for (DexProgramClass type : allocationSitesInMethod) {
          int numberOfPendingAllocationSites = pendingObjectAllocations.removeInt(type) - 1;
          if (numberOfPendingAllocationSites > 0) {
            pendingObjectAllocations.put(type, numberOfPendingAllocationSites);
          } else {
            allAllocationsSitesSeenConsumer.accept(type);
          }
        }
      }
    }
  }
}
