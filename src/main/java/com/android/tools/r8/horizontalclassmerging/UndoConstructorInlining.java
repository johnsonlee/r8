// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.horizontalclassmerging;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;
import static com.android.tools.r8.ir.analysis.type.Nullability.definitelyNotNull;
import static com.android.tools.r8.utils.MapUtils.ignoreKey;

import com.android.tools.r8.cf.CfVersion;
import com.android.tools.r8.classmerging.ClassMergerMode;
import com.android.tools.r8.classmerging.ClassMergerSharedData;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ImmediateProgramSubtypingInfo;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.IRMetadata;
import com.android.tools.r8.ir.code.Value;
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
import com.android.tools.r8.utils.ObjectUtils;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Collection;
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
      ClassMergerMode mode,
      ExecutorService executorService,
      Timing timing)
      throws ExecutionException {
    if (shouldRun(mode)) {
      timing.begin("Undo constructor inlining");
      run(groups, executorService);
      timing.end();
    }
  }

  private boolean shouldRun(ClassMergerMode mode) {
    // Only run when constructor inlining is enabled.
    return appView != null
        && appView.options().canInitNewInstanceUsingSuperclassConstructor()
        && mode.isFinal();
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

    // Create a mapping from program classes to their strongly connected program component. When we
    // need to synthesize a constructor on a class C we lock on the strongly connected component of
    // C to ensure thread safety.
    Map<DexProgramClass, StronglyConnectedComponent> stronglyConnectedComponents =
        computeStronglyConnectedComponents();
    new LirRewriter(appView, ensureConstructorsOnClasses, stronglyConnectedComponents)
        .run(executorService);
    appView.dexItemFactory().clearTypeElementsCache();
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
      profileCollectionAdditions.commit(appView);
    }

    private void processClass(DexProgramClass clazz) {
      clazz.forEachProgramMethodMatching(this::filterMethod, this::processMethod);
    }

    private boolean filterMethod(DexEncodedMethod method) {
      return method.hasCode()
          && method.getCode().isLirCode()
          && mayInstantiateClassOfInterest(
              method.getCode().asLirCode(), ensureConstructorsOnClasses);
    }

    private void processMethod(ProgramMethod method) {
      LirCode<Integer> code = method.getDefinition().getCode().asLirCode();
      LirCode<Integer> rewritten = rewriteLir(method, code);
      if (ObjectUtils.notIdentical(code, rewritten)) {
        method.setCode(rewritten, appView);
      }
    }

    private boolean mayInstantiateClassOfInterest(
        LirCode<Integer> code, Map<DexType, DexProgramClass> ensureConstructorsOnClasses) {
      return ArrayUtils.any(
          code.getConstantPool(),
          constant ->
              constant instanceof DexType && ensureConstructorsOnClasses.containsKey(constant));
    }

    private LirCode<Integer> rewriteLir(ProgramMethod method, LirCode<Integer> code) {
      // Create a mapping from new-instance value index -> new-instance type (limited to the types
      // of interest).
      Int2ReferenceMap<DexProgramClass> allocationsOfInterest =
          getAllocationsOfInterest(method, code);
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
              code.getMetadataForIR(),
              ArrayUtils.appendElements(code.getConstantPool(), methodsToAppend),
              byteWriter.toByteArray());
    }

    private Int2ReferenceMap<DexProgramClass> getAllocationsOfInterest(
        ProgramMethod method, LirCode<Integer> code) {
      Int2ReferenceMap<DexProgramClass> allocationsOfInterest = new Int2ReferenceOpenHashMap<>();
      if (method.getDefinition().isInstanceInitializer()) {
        DexProgramClass classOfInterest =
            ensureConstructorsOnClasses.get(method.getHolder().getSuperType());
        if (classOfInterest != null) {
          allocationsOfInterest.put(0, classOfInterest);
        }
      }
      for (LirInstructionView view : code) {
        if (view.getOpcode() == LirOpcodes.NEW) {
          DexType type = (DexType) code.getConstantItem(view.getNextConstantOperand());
          DexProgramClass classOfInterest = ensureConstructorsOnClasses.get(type);
          if (classOfInterest != null) {
            allocationsOfInterest.put(view.getValueIndex(code), classOfInterest);
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
        Int2ReferenceMap<DexProgramClass> allocationsOfInterest) {
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
          DexProgramClass classOfInterest = allocationsOfInterest.get(receiver);
          if (classOfInterest != null
              && classOfInterest.getType().isNotIdenticalTo(invokedMethod.getHolderType())
              && !isForwardingConstructorCall(method, invokedMethod, receiver)) {
            return new InvokeDirectInfo(invokedMethod, firstValue, classOfInterest);
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

    private boolean isForwardingConstructorCall(
        ProgramMethod method, DexMethod invokedMethod, int receiver) {
      assert invokedMethod.isInstanceInitializer(appView.dexItemFactory());
      return method.getDefinition().isInstanceInitializer()
          && invokedMethod.getHolderType().isIdenticalTo(method.getHolderType())
          && receiver == 0;
    }

    private StronglyConnectedComponent getStronglyConnectedComponent(DexProgramClass clazz) {
      return stronglyConnectedComponents.get(clazz);
    }
  }

  private static class InvokeDirectInfo {

    private final DexMethod invokedMethod;
    private final int firstValue;
    private final DexProgramClass programClass;

    InvokeDirectInfo(DexMethod invokedMethod, int firstValue, DexProgramClass programClass) {
      this.invokedMethod = invokedMethod;
      this.firstValue = firstValue;
      this.programClass = programClass;
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
              .build();
      clazz.addDirectMethod(method);
      ProgramMethod programMethod = method.asProgramMethod(clazz);
      creationConsumer.accept(programMethod);
      return programMethod;
    }

    private LirCode<Integer> createConstructorCode(DexMethod methodReference, DexMethod target) {
      LirEncodingStrategy<Value, Integer> strategy =
          LirStrategy.getDefaultStrategy().getEncodingStrategy();
      LirBuilder<Value, Integer> lirBuilder =
          LirCode.builder(methodReference, true, strategy, appView.options())
              .setMetadata(IRMetadata.unknown());

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
  }
}
