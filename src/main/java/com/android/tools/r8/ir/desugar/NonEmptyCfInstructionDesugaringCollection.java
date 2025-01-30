// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar;

import static com.android.tools.r8.androidapi.AndroidApiLevelCompute.noAndroidApiLevelCompute;

import com.android.tools.r8.androidapi.AndroidApiLevelCompute;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.CfCompareHelper;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.desugar.apimodel.ApiInvokeOutlinerDesugaring;
import com.android.tools.r8.ir.desugar.constantdynamic.ConstantDynamicInstructionDesugaring;
import com.android.tools.r8.ir.desugar.desugaredlibrary.apiconversion.DesugaredLibraryAPIConverter;
import com.android.tools.r8.ir.desugar.desugaredlibrary.disabledesugarer.DesugaredLibraryDisableDesugarer;
import com.android.tools.r8.ir.desugar.desugaredlibrary.retargeter.DesugaredLibraryLibRewriter;
import com.android.tools.r8.ir.desugar.desugaredlibrary.retargeter.DesugaredLibraryRetargeter;
import com.android.tools.r8.ir.desugar.icce.AlwaysThrowingInstructionDesugaring;
import com.android.tools.r8.ir.desugar.invokespecial.InvokeSpecialToSelfDesugaring;
import com.android.tools.r8.ir.desugar.itf.InterfaceMethodProcessorFacade;
import com.android.tools.r8.ir.desugar.itf.InterfaceMethodRewriter;
import com.android.tools.r8.ir.desugar.itf.InterfaceProcessor;
import com.android.tools.r8.ir.desugar.lambda.LambdaInstructionDesugaring;
import com.android.tools.r8.ir.desugar.nest.D8NestBasedAccessDesugaring;
import com.android.tools.r8.ir.desugar.nest.NestBasedAccessDesugaring;
import com.android.tools.r8.ir.desugar.records.RecordInstructionDesugaring;
import com.android.tools.r8.ir.desugar.stringconcat.StringConcatInstructionDesugaring;
import com.android.tools.r8.ir.desugar.twr.TwrInstructionDesugaring;
import com.android.tools.r8.ir.desugar.typeswitch.TypeSwitchDesugaring;
import com.android.tools.r8.ir.desugar.varhandle.VarHandleDesugaring;
import com.android.tools.r8.position.MethodPosition;
import com.android.tools.r8.utils.Box;
import com.android.tools.r8.utils.IntBox;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.SetUtils;
import com.android.tools.r8.utils.StringDiagnostic;
import com.android.tools.r8.utils.ThrowingConsumer;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap.Entry;
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public class NonEmptyCfInstructionDesugaringCollection extends CfInstructionDesugaringCollection {

  private final AppView<?> appView;
  private final List<CfInstructionDesugaring> desugarings = new ArrayList<>();
  // A special collection of desugarings that yield to all other desugarings.
  private final List<CfInstructionDesugaring> yieldingDesugarings = new ArrayList<>();

  private final NestBasedAccessDesugaring nestBasedAccessDesugaring;
  private final DesugaredLibraryRetargeter desugaredLibraryRetargeter;
  private final InterfaceMethodRewriter interfaceMethodRewriter;
  private final DesugaredLibraryAPIConverter desugaredLibraryAPIConverter;
  private final DesugaredLibraryDisableDesugarer disableDesugarer;

  private final CfInstructionDesugaring[][] asmOpcodeOrCompareToIdToDesugaringsMap;

  NonEmptyCfInstructionDesugaringCollection(AppView<?> appView) {
    this(appView, appView.apiLevelCompute());
  }

  NonEmptyCfInstructionDesugaringCollection(
      AppView<?> appView, AndroidApiLevelCompute apiLevelCompute) {
    this.appView = appView;
    AlwaysThrowingInstructionDesugaring alwaysThrowingInstructionDesugaring =
        appView.enableWholeProgramOptimizations()
            ? new AlwaysThrowingInstructionDesugaring(appView.withClassHierarchy())
            : null;
    if (alwaysThrowingInstructionDesugaring != null) {
      desugarings.add(alwaysThrowingInstructionDesugaring);
    }
    if (appView.options().apiModelingOptions().isOutliningOfMethodsEnabled()) {
      yieldingDesugarings.add(new ApiInvokeOutlinerDesugaring(appView, apiLevelCompute));
    }
    if (appView.options().desugarState.isOff()) {
      this.nestBasedAccessDesugaring = null;
      this.desugaredLibraryRetargeter = null;
      this.interfaceMethodRewriter = null;
      this.desugaredLibraryAPIConverter = null;
      this.disableDesugarer = null;
      desugarings.add(new InvokeSpecialToSelfDesugaring(appView));
      if (appView.options().isGeneratingDex()) {
        if (appView.options().canHaveArtArrayCloneFromInterfaceMethodBug()) {
          desugarings.add(new OutlineArrayCloneFromInterfaceMethodDesugaring(appView));
        }
        yieldingDesugarings.add(new UnrepresentableInDexInstructionRemover(appView));
      }
      this.asmOpcodeOrCompareToIdToDesugaringsMap = createAsmOpcodeOrCompareToIdToDesugaringsMap();
      return;
    }
    this.nestBasedAccessDesugaring = NestBasedAccessDesugaring.create(appView);
    BackportedMethodRewriter backportedMethodRewriter = new BackportedMethodRewriter(appView);
    DesugaredLibraryLibRewriter desugaredLibRewriter = DesugaredLibraryLibRewriter.create(appView);
    if (desugaredLibRewriter != null) {
      desugarings.add(desugaredLibRewriter);
    }
    desugaredLibraryRetargeter =
        appView
                .options()
                .getLibraryDesugaringOptions()
                .getMachineDesugaredLibrarySpecification()
                .hasRetargeting()
            ? new DesugaredLibraryRetargeter(appView)
            : null;
    if (desugaredLibraryRetargeter != null) {
      desugarings.add(desugaredLibraryRetargeter);
    }
    disableDesugarer = DesugaredLibraryDisableDesugarer.create(appView);
    if (disableDesugarer != null) {
      desugarings.add(disableDesugarer);
    }
    if (appView.options().enableTryWithResourcesDesugaring()) {
      desugarings.add(new TwrInstructionDesugaring(appView));
    }
    if (appView.options().enableTypeSwitchDesugaring) {
      desugarings.add(new TypeSwitchDesugaring(appView));
    }
    RecordInstructionDesugaring recordRewriter = RecordInstructionDesugaring.create(appView);
    if (recordRewriter != null) {
      desugarings.add(recordRewriter);
    }
    StringConcatInstructionDesugaring stringConcatDesugaring =
        new StringConcatInstructionDesugaring(appView);
    desugarings.add(stringConcatDesugaring);
    LambdaInstructionDesugaring lambdaDesugaring = new LambdaInstructionDesugaring(appView);
    desugarings.add(lambdaDesugaring);
    interfaceMethodRewriter =
        InterfaceMethodRewriter.create(
            appView,
            SetUtils.newImmutableSetExcludingNullItems(
                alwaysThrowingInstructionDesugaring,
                backportedMethodRewriter,
                desugaredLibraryRetargeter),
            SetUtils.newImmutableSetExcludingNullItems(
                lambdaDesugaring, stringConcatDesugaring, recordRewriter));
    if (interfaceMethodRewriter != null) {
      desugarings.add(interfaceMethodRewriter);
    } else if (appView.options().canHaveArtArrayCloneFromInterfaceMethodBug()) {
      desugarings.add(new OutlineArrayCloneFromInterfaceMethodDesugaring(appView));
    }
    desugaredLibraryAPIConverter =
        appView.desugaredLibraryTypeRewriter.isRewriting()
            ? new DesugaredLibraryAPIConverter(
                appView,
                SetUtils.newImmutableSetExcludingNullItems(
                    interfaceMethodRewriter, desugaredLibraryRetargeter, backportedMethodRewriter),
                interfaceMethodRewriter != null
                    ? interfaceMethodRewriter.getEmulatedMethods()
                    : ImmutableSet.of())
            : null;
    if (desugaredLibraryAPIConverter != null) {
      desugarings.add(desugaredLibraryAPIConverter);
    }
    desugarings.add(new ConstantDynamicInstructionDesugaring(appView));
    desugarings.add(new InvokeSpecialToSelfDesugaring(appView));
    if (appView.options().isGeneratingClassFiles()) {
      // Nest desugaring has to be enabled to avoid other invokevirtual to private methods.
      assert nestBasedAccessDesugaring != null;
      desugarings.add(new InvokeToPrivateRewriter());
    }
    if (appView.options().shouldDesugarBufferCovariantReturnType()) {
      desugarings.add(new BufferCovariantReturnTypeRewriter(appView));
    }
    if (backportedMethodRewriter.hasBackports()) {
      desugarings.add(backportedMethodRewriter);
    }
    if (nestBasedAccessDesugaring != null) {
      desugarings.add(nestBasedAccessDesugaring);
    }
    VarHandleDesugaring varHandleDesugaring = VarHandleDesugaring.create(appView);
    if (varHandleDesugaring != null) {
      desugarings.add(varHandleDesugaring);
    }
    yieldingDesugarings.add(new UnrepresentableInDexInstructionRemover(appView));
    asmOpcodeOrCompareToIdToDesugaringsMap = createAsmOpcodeOrCompareToIdToDesugaringsMap();
  }

  private CfInstructionDesugaring[][] createAsmOpcodeOrCompareToIdToDesugaringsMap() {
    Int2ReferenceMap<List<CfInstructionDesugaring>> map = new Int2ReferenceOpenHashMap<>();
    for (CfInstructionDesugaring desugaring : Iterables.concat(desugarings, yieldingDesugarings)) {
      desugaring.acceptRelevantAsmOpcodes(
          opcode -> registerRelevantAsmOpcodeOrCompareToId(map, desugaring, opcode));
      desugaring.acceptRelevantCompareToIds(
          id -> registerRelevantAsmOpcodeOrCompareToId(map, desugaring, id));
    }
    // Convert map to a dense array.
    int maxAsmOpcodeOrCompareToId = CfCompareHelper.MAX_COMPARE_ID;
    CfInstructionDesugaring[][] arrayMap =
        new CfInstructionDesugaring[maxAsmOpcodeOrCompareToId + 1][];
    for (Entry<List<CfInstructionDesugaring>> entry : map.int2ReferenceEntrySet()) {
      arrayMap[entry.getIntKey()] = entry.getValue().toArray(CfInstructionDesugaring.EMPTY_ARRAY);
    }
    for (int i = 0; i < arrayMap.length; i++) {
      if (arrayMap[i] == null) {
        arrayMap[i] = CfInstructionDesugaring.EMPTY_ARRAY;
      }
    }
    return arrayMap;
  }

  private void registerRelevantAsmOpcodeOrCompareToId(
      Int2ReferenceMap<List<CfInstructionDesugaring>> asmOpcodeOrCompareToIdToDesugaringsMap,
      CfInstructionDesugaring desugaring,
      int opcodeOrCompareToId) {
    if (asmOpcodeOrCompareToIdToDesugaringsMap.containsKey(opcodeOrCompareToId)) {
      asmOpcodeOrCompareToIdToDesugaringsMap.get(opcodeOrCompareToId).add(desugaring);
    } else {
      asmOpcodeOrCompareToIdToDesugaringsMap.put(
          opcodeOrCompareToId, ListUtils.newArrayList(desugaring));
    }
  }

  static NonEmptyCfInstructionDesugaringCollection createForCfToCfNonDesugar(AppView<?> appView) {
    assert appView.options().desugarState.isOff();
    assert appView.options().isGeneratingClassFiles();
    return new NonEmptyCfInstructionDesugaringCollection(appView, noAndroidApiLevelCompute());
  }

  static NonEmptyCfInstructionDesugaringCollection createForCfToDexNonDesugar(AppView<?> appView) {
    assert appView.options().desugarState.isOff();
    assert appView.options().isGeneratingDex();
    return new NonEmptyCfInstructionDesugaringCollection(appView);
  }

  private void ensureCfCode(ProgramMethod method) {
    if (!method.getDefinition().getCode().isCfCode()) {
      appView
          .options()
          .reporter
          .error(
              new StringDiagnostic(
                  "Unsupported attempt to desugar non-CF code",
                  method.getOrigin(),
                  MethodPosition.create(method)));
    }
  }

  @Override
  public void prepare(
      ProgramMethod method,
      CfInstructionDesugaringEventConsumer eventConsumer,
      ProgramAdditions programAdditions) {
    ensureCfCode(method);
    desugarings.forEach(d -> d.prepare(method, eventConsumer, programAdditions));
    yieldingDesugarings.forEach(d -> d.prepare(method, eventConsumer, programAdditions));
  }

  @Override
  public void scan(ProgramMethod method, CfInstructionDesugaringEventConsumer eventConsumer) {
    ensureCfCode(method);
    desugarings.forEach(d -> d.scan(method, eventConsumer));
    yieldingDesugarings.forEach(d -> d.scan(method, eventConsumer));
  }

  @Override
  public void desugar(
      ProgramMethod method,
      MethodProcessingContext methodProcessingContext,
      CfInstructionDesugaringEventConsumer eventConsumer) {
    ensureCfCode(method);
    CfCode cfCode = method.getDefinition().getCode().asCfCode();

    // Tracking of temporary locals used for instruction desugaring. The desugaring of each
    // instruction is assumed to use locals only for the duration of the instruction, such that any
    // temporarily used locals will be free again at the next instruction to be desugared.
    IntBox maxLocalsForCode = new IntBox(cfCode.getMaxLocals());
    IntBox maxLocalsForInstruction = new IntBox(cfCode.getMaxLocals());

    IntBox maxStackForCode = new IntBox(cfCode.getMaxStack());
    IntBox maxStackForInstruction = new IntBox(cfCode.getMaxStack());

    class CfDesugaringInfoImpl implements CfDesugaringInfo {
      int bytecodeSizeUpperBound;

      private CfDesugaringInfoImpl(int bytecodeSizeUpperBound) {
        this.bytecodeSizeUpperBound = bytecodeSizeUpperBound;
      }

      @Override
      public boolean canIncreaseBytecodeSize() {
        return bytecodeSizeUpperBound < 65000;
      }

      private void updateBytecodeSize(int delta) {
        bytecodeSizeUpperBound += delta;
      }
    }

    CfDesugaringInfoImpl desugaringInfo = new CfDesugaringInfoImpl(cfCode.bytecodeSizeUpperBound());

    Box<Position> currentPosition = new Box<>();
    boolean maintainPositionForInlineInfo = appView.options().hasMappingFileSupport();
    if (maintainPositionForInlineInfo) {
      currentPosition.set(cfCode.getPreamblePosition());
      if (!currentPosition.isSet()) {
        currentPosition.set(
            Position.SyntheticPosition.builder()
                .setLine(0)
                .setMethod(method.getReference())
                .setIsD8R8Synthesized(method.getDefinition().isD8R8Synthesized())
                .build());
      }
    }
    List<CfInstruction> desugaredInstructions =
        ListUtils.flatMapSameType(
            cfCode.getInstructions(),
            instruction -> {
              if (instruction.isPosition()) {
                if (maintainPositionForInlineInfo) {
                  currentPosition.set(instruction.asPosition().getPosition());
                }
                return null;
              }
              Collection<CfInstruction> replacement =
                  desugarInstruction(
                      instruction,
                      currentPosition.get(),
                      maxLocalsForInstruction::getAndIncrement,
                      maxStackForInstruction::getAndIncrement,
                      desugaringInfo,
                      eventConsumer,
                      method,
                      methodProcessingContext);
              if (replacement != null) {
                // Record if we increased the max number of locals and stack height for the method,
                // and reset the next temporary locals register.
                maxLocalsForCode.setMax(maxLocalsForInstruction.getAndSet(cfCode.getMaxLocals()));
                maxStackForCode.setMax(maxStackForInstruction.getAndSet(cfCode.getMaxStack()));
                // Record the change in bytecode size.
                desugaringInfo.updateBytecodeSize(-instruction.bytecodeSizeUpperBound());
                replacement.forEach(
                    i -> desugaringInfo.updateBytecodeSize(i.bytecodeSizeUpperBound()));
              } else {
                // The next temporary locals register should be unchanged.
                assert maxLocalsForInstruction.get() == cfCode.getMaxLocals();
                assert maxStackForInstruction.get() == cfCode.getMaxStack();
              }
              return replacement;
            },
            null);
    if (desugaredInstructions != null) {
      assert maxLocalsForCode.get() >= cfCode.getMaxLocals();
      assert maxStackForCode.get() >= cfCode.getMaxStack();
      cfCode.setInstructions(desugaredInstructions);
      cfCode.setMaxLocals(maxLocalsForCode.get());
      cfCode.setMaxStack(maxStackForCode.get());
    } else {
      assert noDesugaringBecauseOfImpreciseDesugaring(method);
    }
  }

  private boolean noDesugaringBecauseOfImpreciseDesugaring(ProgramMethod method) {
    assert desugarings.stream().anyMatch(desugaring -> !desugaring.hasPreciseNeedsDesugaring())
        : "Expected code to be desugared";
    assert needsDesugaring(method);
    boolean foundFalsePositive = false;
    for (CfInstruction instruction :
        method.getDefinition().getCode().asCfCode().getInstructions()) {
      for (CfInstructionDesugaring impreciseDesugaring :
          Iterables.filter(desugarings, desugaring -> !desugaring.hasPreciseNeedsDesugaring())) {
        if (impreciseDesugaring.compute(instruction, method).needsDesugaring()) {
          foundFalsePositive = true;
        }
      }
      for (CfInstructionDesugaring preciseDesugaring :
          Iterables.filter(desugarings, desugaring -> desugaring.hasPreciseNeedsDesugaring())) {
        assert !preciseDesugaring.compute(instruction, method).needsDesugaring();
      }
    }
    assert foundFalsePositive;
    return true;
  }

  @Override
  public Collection<CfInstruction> desugarInstruction(
      CfInstruction instruction,
      Position position,
      FreshLocalProvider freshLocalProvider,
      LocalStackAllocator localStackAllocator,
      CfDesugaringInfo desugaringInfo,
      CfInstructionDesugaringEventConsumer eventConsumer,
      ProgramMethod context,
      MethodProcessingContext methodProcessingContext) {
    // TODO(b/177810578): Migrate other cf-to-cf based desugaring here.
    Collection<CfInstruction> replacement =
        applyDesugaring(
            instruction,
            position,
            freshLocalProvider,
            localStackAllocator,
            desugaringInfo,
            eventConsumer,
            context,
            methodProcessingContext,
            desugarings.iterator());
    if (replacement != null) {
      return replacement;
    }
    // If we made it here there it is because a yielding desugaring reported that it needs
    // desugaring and no other desugaring happened.
    return applyDesugaring(
        instruction,
        position,
        freshLocalProvider,
        localStackAllocator,
        desugaringInfo,
        eventConsumer,
        context,
        methodProcessingContext,
        yieldingDesugarings.iterator());
  }

  private Collection<CfInstruction> applyDesugaring(
      CfInstruction instruction,
      Position position,
      FreshLocalProvider freshLocalProvider,
      LocalStackAllocator localStackAllocator,
      CfDesugaringInfo desugaringInfo,
      CfInstructionDesugaringEventConsumer eventConsumer,
      ProgramMethod context,
      MethodProcessingContext methodProcessingContext,
      Iterator<CfInstructionDesugaring> iterator) {
    while (iterator.hasNext()) {
      CfInstructionDesugaring desugaring = iterator.next();
      Collection<CfInstruction> replacement =
          desugaring
              .compute(instruction, context)
              .desugarInstruction(
                  position,
                  freshLocalProvider,
                  localStackAllocator,
                  desugaringInfo,
                  eventConsumer,
                  context,
                  methodProcessingContext,
                  this,
                  appView.dexItemFactory());
      if (replacement != null) {
        assert verifyNoOtherDesugaringNeeded(instruction, context, iterator, desugaring);
        return replacement;
      }
    }
    return null;
  }

  @Override
  public boolean needsDesugaring(ProgramMethod method) {
    if (!method.getDefinition().hasCode()) {
      return false;
    }

    Code code = method.getDefinition().getCode();
    if (code.isDexCode()) {
      return false;
    }

    if (!code.isCfCode()) {
      throw new Unreachable("Unexpected attempt to determine if non-CF code needs desugaring");
    }

    CfCode cfCode = code.asCfCode();
    for (CfInstruction instruction : cfCode.getInstructions()) {
      int asmOpcodeOrCompareToId =
          instruction.hasAsmOpcode() ? instruction.getAsmOpcode() : instruction.getCompareToId();
      CfInstructionDesugaring[] desugaringsForInstruction =
          asmOpcodeOrCompareToIdToDesugaringsMap[asmOpcodeOrCompareToId];
      for (CfInstructionDesugaring desugaring : desugaringsForInstruction) {
        if (desugaring.compute(instruction, method).needsDesugaring()) {
          return true;
        }
      }
      assert verifyNoDesugaringNeeded(instruction, method);
    }
    return false;
  }

  private boolean verifyNoDesugaringNeeded(CfInstruction instruction, ProgramMethod context) {
    for (CfInstructionDesugaring desugaring : Iterables.concat(desugarings, yieldingDesugarings)) {
      assert !desugaring.compute(instruction, context).needsDesugaring()
          : "Expected instruction to be desugared, but matched by: "
              + desugaring.getClass().getName();
    }
    return true;
  }

  private boolean verifyNoOtherDesugaringNeeded(
      CfInstruction instruction,
      ProgramMethod context,
      Iterator<CfInstructionDesugaring> iterator,
      CfInstructionDesugaring appliedDesugaring) {
    iterator.forEachRemaining(
        desugaring -> {
          boolean alsoApplicable = desugaring.compute(instruction, context).needsDesugaring();
          // TODO(b/187913003): As part of precise interface desugaring, make sure the
          //  identification is explicitly non-overlapping and remove the exceptions below.
          assert !alsoApplicable
                  || (appliedDesugaring instanceof InterfaceMethodRewriter
                      && (desugaring instanceof InvokeToPrivateRewriter
                          || desugaring instanceof NestBasedAccessDesugaring))
                  || (appliedDesugaring instanceof TwrInstructionDesugaring
                      && desugaring instanceof InterfaceMethodRewriter)
              : "Desugaring of "
                  + instruction
                  + " in method "
                  + context.toSourceString()
                  + " has multiple matches: "
                  + appliedDesugaring.getClass().getName()
                  + " and "
                  + desugaring.getClass().getName();
        });
    return true;
  }

  @Override
  public <T extends Throwable> void withD8NestBasedAccessDesugaring(
      ThrowingConsumer<D8NestBasedAccessDesugaring, T> consumer) throws T {
    if (nestBasedAccessDesugaring != null) {
      assert nestBasedAccessDesugaring instanceof D8NestBasedAccessDesugaring;
      consumer.accept((D8NestBasedAccessDesugaring) nestBasedAccessDesugaring);
    }
  }

  @Override
  public InterfaceMethodProcessorFacade getInterfaceMethodPostProcessingDesugaringR8(
      Predicate<ProgramMethod> isLiveMethod, InterfaceProcessor processor) {
    return withInterfaceMethodRewriter(
        interfaceMethodRewriter ->
            interfaceMethodRewriter.getPostProcessingDesugaringR8(isLiveMethod, processor));
  }

  @Override
  public void withDesugaredLibraryAPIConverter(Consumer<DesugaredLibraryAPIConverter> consumer) {
    if (desugaredLibraryAPIConverter != null) {
      consumer.accept(desugaredLibraryAPIConverter);
    }
  }

  @Override
  public <T> T withInterfaceMethodRewriter(Function<InterfaceMethodRewriter, T> fn) {
    if (interfaceMethodRewriter != null) {
      return fn.apply(interfaceMethodRewriter);
    }
    return null;
  }
}
