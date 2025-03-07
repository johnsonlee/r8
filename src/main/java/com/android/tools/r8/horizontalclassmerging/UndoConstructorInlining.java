// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.horizontalclassmerging;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;
import static com.android.tools.r8.ir.analysis.type.Nullability.definitelyNotNull;
import static com.android.tools.r8.utils.MapUtils.ignoreKey;

import com.android.tools.r8.cf.CfVersion;
import com.android.tools.r8.classmerging.ClassMergerSharedData;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndMember;
import com.android.tools.r8.graph.DexEncodedMember;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ImmediateProgramSubtypingInfo;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.bytecodemetadata.BytecodeMetadataProvider;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeDirect;
import com.android.tools.r8.ir.code.NewInstance;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.IRToLirFinalizer;
import com.android.tools.r8.lightir.ByteArrayWriter;
import com.android.tools.r8.lightir.ByteUtils;
import com.android.tools.r8.lightir.LirBuilder;
import com.android.tools.r8.lightir.LirCode;
import com.android.tools.r8.lightir.LirConstant;
import com.android.tools.r8.lightir.LirEncodingStrategy;
import com.android.tools.r8.lightir.LirInstructionView;
import com.android.tools.r8.lightir.LirOpcodes;
import com.android.tools.r8.lightir.LirStrategy;
import com.android.tools.r8.lightir.LirWriter;
import com.android.tools.r8.optimize.argumentpropagation.utils.ProgramClassesBidirectedGraph;
import com.android.tools.r8.profile.rewriting.ProfileCollectionAdditions;
import com.android.tools.r8.utils.ArrayUtils;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.ObjectUtils;
import com.android.tools.r8.utils.SetUtils;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.WorkList;
import com.android.tools.r8.utils.timing.Timing;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class UndoConstructorInlining {

  private final AppView<? extends AppInfoWithClassHierarchy> appView;
  private final ClassMergerSharedData classMergerSharedData;
  private final ImmediateProgramSubtypingInfo immediateSubtypingInfo;

  public UndoConstructorInlining(
      AppView<?> appView,
      ClassMergerSharedData classMergerSharedData,
      ImmediateProgramSubtypingInfo immediateSubtypingInfo) {
    this.appView = appView.enableWholeProgramOptimizations() ? appView.withClassHierarchy() : null;
    this.classMergerSharedData = classMergerSharedData;
    this.immediateSubtypingInfo = immediateSubtypingInfo;
  }

  public void runIfNecessary(
      Collection<HorizontalMergeGroup> groups,
      ExecutorService executorService,
      Timing timing)
      throws ExecutionException {
    if (shouldRun()) {
      timing.begin("Undo constructor inlining");
      run(groups, executorService);
      timing.end();
    }
  }

  private boolean shouldRun() {
    // Only run when constructor inlining is enabled.
    return appView != null && appView.options().canInitNewInstanceUsingSuperclassConstructor();
  }

  private void run(Collection<HorizontalMergeGroup> groups, ExecutorService executorService)
      throws ExecutionException {
    // Find all classes in horizontal non-interface merge groups that have a class id. All
    // instantiations of these classes *must* use a constructor on the class itself, since the
    // constructor will be responsible for assigning the class id field. This property may not hold
    // as a result of constructor inlining, so we need to restore it.
    Map<DexType, DexProgramClass> ensureConstructorsOnClasses =
        groups.stream()
            .filter(group -> group.isClassGroup() && group.hasClassIdField())
            .flatMap(HorizontalMergeGroup::stream)
            .collect(Collectors.toMap(DexClass::getType, Function.identity()));
    if (ensureConstructorsOnClasses.isEmpty()) {
      return;
    }

    // Extend the mapping to include subclasses.
    ensureConstructorsOnSubclasses(ensureConstructorsOnClasses);

    // Create a mapping from program classes to their strongly connected program component. When we
    // need to synthesize a constructor on a class C we lock on the strongly connected component of
    // C to ensure thread safety.
    Map<DexProgramClass, StronglyConnectedComponent> stronglyConnectedComponents =
        computeStronglyConnectedComponents();
    new LirRewriter(appView, ensureConstructorsOnClasses, stronglyConnectedComponents)
        .run(executorService);
    appView.dexItemFactory().clearTypeElementsCache();
  }

  private void ensureConstructorsOnSubclasses(
      Map<DexType, DexProgramClass> ensureConstructorsOnClasses) {
    // Perform a top-down traversal from each merge class and record that instantiations of
    // subclasses of the merge class must not skip any constructors on the merge class.
    Map<DexType, DexProgramClass> ensureConstructorsOnSubclasses = new IdentityHashMap<>();
    for (DexProgramClass mergeClass : ensureConstructorsOnClasses.values()) {
      WorkList.newIdentityWorkList(immediateSubtypingInfo.getSubclasses(mergeClass))
          .process(
              (current, worklist) -> {
                if (ensureConstructorsOnClasses.containsKey(current.getType())) {
                  return;
                }
                ensureConstructorsOnSubclasses.put(current.getType(), mergeClass);
                worklist.addIfNotSeen(immediateSubtypingInfo.getSubclasses(current));
              });
    }
    ensureConstructorsOnClasses.putAll(ensureConstructorsOnSubclasses);
  }

  private Map<DexProgramClass, StronglyConnectedComponent> computeStronglyConnectedComponents() {
    List<Set<DexProgramClass>> stronglyConnectedComponents =
        new ProgramClassesBidirectedGraph(appView, immediateSubtypingInfo)
            .computeStronglyConnectedComponents();
    Map<DexProgramClass, StronglyConnectedComponent> stronglyConnectedComponentMap =
        new IdentityHashMap<>();
    for (Set<DexProgramClass> classes : stronglyConnectedComponents) {
      StronglyConnectedComponent stronglyConnectedComponent = new StronglyConnectedComponent();
      for (DexProgramClass clazz : classes) {
        stronglyConnectedComponentMap.put(clazz, stronglyConnectedComponent);
      }
    }
    return stronglyConnectedComponentMap;
  }

  private static class LirRewriter {

    private final AppView<? extends AppInfoWithClassHierarchy> appView;
    private final Map<DexType, DexProgramClass> ensureConstructorsOnClasses;
    private final ProfileCollectionAdditions profileCollectionAdditions;
    private final Map<DexProgramClass, StronglyConnectedComponent> stronglyConnectedComponents;

    LirRewriter(
        AppView<? extends AppInfoWithClassHierarchy> appView,
        Map<DexType, DexProgramClass> ensureConstructorsOnClasses,
        Map<DexProgramClass, StronglyConnectedComponent> stronglyConnectedComponents) {
      this.appView = appView;
      this.ensureConstructorsOnClasses = ensureConstructorsOnClasses;
      this.profileCollectionAdditions = ProfileCollectionAdditions.create(appView);
      this.stronglyConnectedComponents = stronglyConnectedComponents;
    }

    public void run(ExecutorService executorService) throws ExecutionException {
      ThreadUtils.processItems(
          appView.appInfo().classes(),
          this::processClass,
          appView.options().getThreadingModule(),
          executorService);
      commitPendingConstructors();
      profileCollectionAdditions.commit(appView);
    }

    private void processClass(DexProgramClass clazz) {
      clazz.forEachProgramMethodMatching(
          method -> filterMethod(clazz, method), this::processMethod);
    }

    private boolean filterMethod(DexProgramClass clazz, DexEncodedMethod method) {
      return method.hasCode()
          && method.getCode().isLirCode()
          && mayInstantiateClassOfInterest(
              clazz, method, method.getCode().asLirCode(), ensureConstructorsOnClasses);
    }

    private void processMethod(ProgramMethod method) {
      LirCode<Integer> code = method.getDefinition().getCode().asLirCode();
      LirCode<Integer> rewritten = rewriteLir(method, code);
      if (ObjectUtils.notIdentical(code, rewritten)) {
        method.setCode(rewritten, appView);
      }
    }

    private boolean mayInstantiateClassOfInterest(
        DexProgramClass clazz,
        DexEncodedMethod method,
        LirCode<Integer> code,
        Map<DexType, DexProgramClass> ensureConstructorsOnClasses) {
      // Treat constructors as allocations of the super class.
      if (method.isInstanceInitializer()
          && ensureConstructorsOnClasses.containsKey(clazz.getSuperType())) {
        return true;
      }
      return ArrayUtils.any(
          code.getConstantPool(),
          constant ->
              constant instanceof DexType && ensureConstructorsOnClasses.containsKey(constant));
    }

    private LirCode<Integer> rewriteLir(ProgramMethod method, LirCode<Integer> code) {
      // Create a mapping from new-instance value index -> new-instance type (limited to the types
      // of interest).
      Int2ReferenceMap<DexType> allocationsOfInterest = getAllocationsOfInterest(method, code);
      if (allocationsOfInterest.isEmpty()) {
        return code;
      }
      ByteArrayWriter byteWriter = new ByteArrayWriter();
      LirWriter lirWriter = new LirWriter(byteWriter);
      List<LirConstant> methodsToAppend = new ArrayList<>();
      Reference2IntMap<DexMethod> methodIndices = new Reference2IntOpenHashMap<>();
      for (LirInstructionView view : code) {
        InvokeDirectInfo info =
            getAllocationOfInterest(method, code, lirWriter, view, allocationsOfInterest);
        if (info == null) {
          continue;
        }
        ProgramMethod newInvokedMethod =
            getStronglyConnectedComponent(info.getProgramClass())
                .getOrCreateConstructor(
                    info.getProgramClass(),
                    info.getInvokedMethod(),
                    ensureConstructorsOnClasses,
                    newConstructor ->
                        profileCollectionAdditions.addMethodIfContextIsInProfile(
                            newConstructor, method));
        if (newInvokedMethod.getArity() != info.getInvokedMethod().getArity()) {
          assert newInvokedMethod.getArity() > info.getInvokedMethod().getArity();
          return rewriteIR(method, code);
        }
        int constantIndex =
            methodIndices.computeIfAbsent(
                newInvokedMethod.getReference(),
                ref -> {
                  methodsToAppend.add(ref);
                  return code.getConstantPool().length + methodsToAppend.size() - 1;
                });
        int constantIndexSize = ByteUtils.intEncodingSize(constantIndex);
        int firstValueSize = ByteUtils.intEncodingSize(info.getFirstValue());
        int remainingSize = view.getRemainingOperandSizeInBytes();
        lirWriter.writeInstruction(
            LirOpcodes.INVOKEDIRECT, constantIndexSize + firstValueSize + remainingSize);
        ByteUtils.writeEncodedInt(constantIndex, lirWriter::writeOperand);
        ByteUtils.writeEncodedInt(info.getFirstValue(), lirWriter::writeOperand);
        while (remainingSize-- > 0) {
          lirWriter.writeOperand(view.getNextU1());
        }
      }
      return methodsToAppend.isEmpty()
          ? code
          : code.copyWithNewConstantsAndInstructions(
              ArrayUtils.appendElements(code.getConstantPool(), methodsToAppend),
              byteWriter.toByteArray());
    }

    private LirCode<Integer> rewriteIR(ProgramMethod method, LirCode<Integer> code) {
      IRCode irCode = code.buildIR(method, appView);
      InstructionListIterator instructionIterator = irCode.instructionListIterator();
      InvokeDirect invoke;
      while ((invoke = instructionIterator.nextUntil(Instruction::isInvokeDirect)) != null) {
        DexType newType;
        if (invoke
            .getReceiver()
            .getAliasedValue()
            .isDefinedByInstructionSatisfying(Instruction::isNewInstance)) {
          NewInstance newInstance =
              invoke.getReceiver().getAliasedValue().getDefinition().asNewInstance();
          newType = newInstance.getType();
        } else if (invoke.getReceiver().isThis()
            && method.getDefinition().isInstanceInitializer()) {
          newType = method.getHolder().getSuperType();
        } else {
          continue;
        }
        DexMethod invokedMethod = invoke.getInvokedMethod();
        if (ensureConstructorsOnClasses.containsKey(newType)
            && isConstructorInlined(newType, invokedMethod)
            && !isForwardingConstructorCall(method, invokedMethod, invoke.getReceiver().isThis())
            && isSkippingRequiredConstructor(newType, invokedMethod)) {
          DexProgramClass noSkipClass = ensureConstructorsOnClasses.get(newType);
          ProgramMethod newInvokedMethod =
              getStronglyConnectedComponent(noSkipClass)
                  .getOrCreateConstructor(
                      noSkipClass,
                      invokedMethod,
                      ensureConstructorsOnClasses,
                      newConstructor ->
                          profileCollectionAdditions.addMethodIfContextIsInProfile(
                              newConstructor, method));
          InvokeDirect.Builder invokeDirectBuilder =
              InvokeDirect.builder()
                  .setArguments(invoke.arguments())
                  .setMethod(newInvokedMethod.getReference());
          if (newInvokedMethod.getArity() > invokedMethod.getArity()) {
            instructionIterator.previous();
            Value zeroValue =
                instructionIterator.insertConstIntInstruction(irCode, appView.options(), 0);
            List<Value> newArguments =
                new ArrayList<>(newInvokedMethod.getDefinition().getNumberOfArguments());
            newArguments.addAll(invoke.arguments());
            while (newArguments.size() < newInvokedMethod.getDefinition().getNumberOfArguments()) {
              newArguments.add(zeroValue);
            }
            Instruction next = instructionIterator.next();
            assert next == invoke;
            invokeDirectBuilder.setArguments(newArguments);
          }
          instructionIterator.replaceCurrentInstruction(invokeDirectBuilder.build());
        }
      }
      return new IRToLirFinalizer(appView)
          .finalizeCode(irCode, BytecodeMetadataProvider.empty(), Timing.empty());
    }

    private Int2ReferenceMap<DexType> getAllocationsOfInterest(
        ProgramMethod method, LirCode<Integer> code) {
      Int2ReferenceMap<DexType> allocationsOfInterest = new Int2ReferenceOpenHashMap<>();
      if (method.getDefinition().isInstanceInitializer()) {
        if (ensureConstructorsOnClasses.containsKey(method.getHolder().getSuperType())) {
          allocationsOfInterest.put(0, method.getHolder().getSuperType());
        }
      }
      for (LirInstructionView view : code) {
        if (view.getOpcode() == LirOpcodes.NEW) {
          DexType type = (DexType) code.getConstantItem(view.getNextConstantOperand());
          if (ensureConstructorsOnClasses.containsKey(type)) {
            allocationsOfInterest.put(view.getValueIndex(code), type);
          }
        }
      }
      return allocationsOfInterest;
    }

    /**
     * Returns non-null for constructor calls that need to be rewritten to a constructor call on the
     * new-instance type. When this returns null, the current instruction is written to the {@param
     * lirWriter}.
     */
    // TODO(b/225838009): Look into making it easier to "peek" data in the LirInstructionView to
    //  avoid needing to keep track of how many operands have been consumed.
    private InvokeDirectInfo getAllocationOfInterest(
        ProgramMethod method,
        LirCode<Integer> code,
        LirWriter lirWriter,
        LirInstructionView view,
        Int2ReferenceMap<DexType> allocationsOfInterest) {
      int opcode = view.getOpcode();
      if (LirOpcodes.isOneByteInstruction(opcode)) {
        lirWriter.writeOneByteInstruction(opcode);
        return null;
      }
      int operandSizeInBytes = view.getRemainingOperandSizeInBytes();
      int numReadOperands = 0;
      int constantIndex = -1;
      int firstValue = -1;
      if (opcode == LirOpcodes.INVOKEDIRECT) {
        constantIndex = view.getNextConstantOperand();
        numReadOperands++;
        DexMethod invokedMethod = (DexMethod) code.getConstantItem(constantIndex);
        if (invokedMethod.isInstanceInitializer(appView.dexItemFactory())) {
          firstValue = view.getNextValueOperand();
          numReadOperands++;
          int receiver = code.decodeValueIndex(firstValue, view.getValueIndex(code));
          DexType newType = allocationsOfInterest.get(receiver);
          if (newType != null
              && isConstructorInlined(newType, invokedMethod)
              && !isForwardingConstructorCall(method, invokedMethod, receiver == 0)
              && isSkippingRequiredConstructor(newType, invokedMethod)) {
            return new InvokeDirectInfo(
                invokedMethod, firstValue, ensureConstructorsOnClasses.get(newType));
          }
        }
      }
      lirWriter.writeInstruction(opcode, operandSizeInBytes);
      assert numReadOperands <= 2;
      if (numReadOperands > 0) {
        ByteUtils.writeEncodedInt(constantIndex, lirWriter::writeOperand);
        if (numReadOperands == 2) {
          ByteUtils.writeEncodedInt(firstValue, lirWriter::writeOperand);
        }
      }
      int size = view.getRemainingOperandSizeInBytes();
      while (size-- > 0) {
        lirWriter.writeOperand(view.getNextU1());
      }
      return null;
    }

    private boolean isConstructorInlined(DexType newType, DexMethod invokedMethod) {
      return newType.isNotIdenticalTo(invokedMethod.getHolderType());
    }

    private boolean isForwardingConstructorCall(
        ProgramMethod method, DexMethod invokedMethod, boolean isThisReceiver) {
      assert invokedMethod.isInstanceInitializer(appView.dexItemFactory());
      return method.getDefinition().isInstanceInitializer()
          && invokedMethod.getHolderType().isIdenticalTo(method.getHolderType())
          && isThisReceiver;
    }

    private boolean isSkippingRequiredConstructor(DexType newType, DexMethod invokedMethod) {
      DexProgramClass requiredConstructorClass = ensureConstructorsOnClasses.get(newType);
      assert requiredConstructorClass != null;
      return !appView
          .appInfo()
          .isSubtype(invokedMethod.getHolderType(), requiredConstructorClass.getType());
    }

    private StronglyConnectedComponent getStronglyConnectedComponent(DexProgramClass clazz) {
      return stronglyConnectedComponents.get(clazz);
    }

    private void commitPendingConstructors() {
      Set<StronglyConnectedComponent> uniqueStronglyConnectedComponents =
          SetUtils.newIdentityHashSet(stronglyConnectedComponents.values());
      uniqueStronglyConnectedComponents.forEach(
          StronglyConnectedComponent::commitPendingConstructors);
    }
  }

  private static class InvokeDirectInfo {

    private final DexMethod invokedMethod;
    private final int firstValue;
    private final DexProgramClass programClass;

    InvokeDirectInfo(DexMethod invokedMethod, int firstValue, DexProgramClass newType) {
      this.invokedMethod = invokedMethod;
      this.firstValue = firstValue;
      this.programClass = newType;
    }

    DexMethod getInvokedMethod() {
      return invokedMethod;
    }

    public int getFirstValue() {
      return firstValue;
    }

    public DexProgramClass getProgramClass() {
      return programClass;
    }
  }

  private class StronglyConnectedComponent {

    private final Map<DexProgramClass, Map<DexMethod, ProgramMethod>> constructorCache =
        new IdentityHashMap<>();

    // Get or create a constructor on the given class that calls target, which is a constructor on
    // a parent class. Note that the returned constructor may not actually call the given target
    // constructor directly, as constructors may also need to be synthesized between the current
    // class and the target holder.
    //
    // Synchronized to ensure thread safety.
    public synchronized ProgramMethod getOrCreateConstructor(
        DexProgramClass clazz,
        DexMethod target,
        Map<DexType, DexProgramClass> ensureConstructorsOnClasses,
        Consumer<ProgramMethod> creationConsumer) {
      return constructorCache
          .computeIfAbsent(clazz, ignoreKey(IdentityHashMap::new))
          .computeIfAbsent(
              target,
              k -> createConstructor(clazz, target, ensureConstructorsOnClasses, creationConsumer));
    }

    private ProgramMethod createConstructor(
        DexProgramClass clazz,
        DexMethod target,
        Map<DexType, DexProgramClass> ensureConstructorsOnClasses,
        Consumer<ProgramMethod> creationConsumer) {
      // Create a fresh constructor on the given class that calls target. If there is a class in the
      // hierarchy inbetween `clazz` and `target.holder`, which is also subject to class merging,
      // then we must create a constructor that calls a constructor on that intermediate class,
      // which then calls target.
      DexType currentType = clazz.getSuperType();
      while (currentType.isNotIdenticalTo(target.getHolderType())) {
        DexProgramClass currentClass = asProgramClassOrNull(appView.definitionFor(currentType));
        if (currentClass == null) {
          break;
        }
        if (ensureConstructorsOnClasses.containsKey(currentType)) {
          target =
              getOrCreateConstructor(
                      currentClass, target, ensureConstructorsOnClasses, creationConsumer)
                  .getReference();
          break;
        }
        currentType = currentClass.getSuperType();
      }

      // Create a constructor that calls target.
      DexItemFactory dexItemFactory = appView.dexItemFactory();
      DexMethod candidateMethodReference = target.withHolder(clazz, dexItemFactory);
      DexMethod methodReference =
          dexItemFactory.createInstanceInitializerWithFreshProto(
              candidateMethodReference,
              classMergerSharedData.getExtraUnusedArgumentTypes(),
              test -> clazz.lookupDirectMethod(test) == null);
      DexEncodedMethod method =
          DexEncodedMethod.syntheticBuilder()
              .setMethod(methodReference)
              .setAccessFlags(
                  MethodAccessFlags.builder().setConstructor().setPublic().setSynthetic().build())
              .setCode(createConstructorCode(methodReference, target))
              .setClassFileVersion(CfVersion.V1_6)
              // TODO(b/325199754): Compute api level here?
              .setApiLevelForCode(
                  appView.apiLevelCompute().computeInitialMinApiLevel(appView.options()))
              .setApiLevelForDefinition(
                  appView.apiLevelCompute().computeInitialMinApiLevel(appView.options()))
              .build();
      ProgramMethod programMethod = method.asProgramMethod(clazz);
      creationConsumer.accept(programMethod);
      return programMethod;
    }

    private LirCode<Integer> createConstructorCode(DexMethod methodReference, DexMethod target) {
      LirEncodingStrategy<Value, Integer> strategy =
          LirStrategy.getDefaultStrategy().getEncodingStrategy();
      LirBuilder<Value, Integer> lirBuilder =
          LirCode.builder(methodReference, true, strategy, appView.options());

      int instructionIndex = 0;
      List<Value> argumentValues = new ArrayList<>();

      // Add receiver argument.
      DexType receiverType = methodReference.getHolderType();
      TypeElement receiverTypeElement = receiverType.toTypeElement(appView, definitelyNotNull());
      Value receiverValue = Value.createNoDebugLocal(instructionIndex, receiverTypeElement);
      argumentValues.add(receiverValue);
      strategy.defineValue(receiverValue, receiverValue.getNumber());
      lirBuilder.addArgument(receiverValue.getNumber(), false);
      instructionIndex++;

      // Add non-receiver arguments.
      for (;
          instructionIndex < target.getNumberOfArgumentsForNonStaticMethod();
          instructionIndex++) {
        DexType argumentType = target.getArgumentTypeForNonStaticMethod(instructionIndex);
        TypeElement argumentTypeElement = argumentType.toTypeElement(appView);
        Value argumentValue = Value.createNoDebugLocal(instructionIndex, argumentTypeElement);
        argumentValues.add(argumentValue);
        strategy.defineValue(argumentValue, argumentValue.getNumber());
        lirBuilder.addArgument(argumentValue.getNumber(), argumentType.isBooleanType());
      }

      // Add remaining unused non-receiver arguments.
      for (;
          instructionIndex < methodReference.getNumberOfArgumentsForNonStaticMethod();
          instructionIndex++) {
        DexType argumentType = methodReference.getArgumentTypeForNonStaticMethod(instructionIndex);
        lirBuilder.addArgument(instructionIndex, argumentType.isBooleanType());
      }

      // Invoke parent constructor.
      lirBuilder.addInvokeDirect(target, argumentValues, false);
      instructionIndex++;

      // Return.
      lirBuilder.addReturnVoid();
      instructionIndex++;

      return lirBuilder.build();
    }

    public void commitPendingConstructors() {
      constructorCache.forEach(
          (clazz, constructors) -> {
            List<DexEncodedMethod> methods =
                ListUtils.sort(
                    ListUtils.map(constructors.values(), DexClassAndMember::getDefinition),
                    Comparator.comparing(DexEncodedMember::getReference));
            clazz.addDirectMethods(methods);
          });
    }
  }
}
