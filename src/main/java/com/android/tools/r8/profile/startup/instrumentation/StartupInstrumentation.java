// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.startup.instrumentation;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;
import static com.android.tools.r8.ir.analysis.type.Nullability.definitelyNotNull;
import static com.android.tools.r8.utils.PredicateUtils.not;

import com.android.tools.r8.androidapi.ComputedApiLevel;
import com.android.tools.r8.cf.CfVersion;
import com.android.tools.r8.dex.code.DexInstruction;
import com.android.tools.r8.dex.code.DexReturnVoid;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexValue.DexValueBoolean;
import com.android.tools.r8.graph.DexValue.DexValueString;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.bytecodemetadata.BytecodeMetadataProvider;
import com.android.tools.r8.ir.code.BasicBlockIterator;
import com.android.tools.r8.ir.code.ConstString;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.IRConverter;
import com.android.tools.r8.ir.conversion.IRToDexFinalizer;
import com.android.tools.r8.ir.conversion.MethodConversionOptions;
import com.android.tools.r8.ir.conversion.MethodConversionOptions.MutableMethodConversionOptions;
import com.android.tools.r8.ir.conversion.MethodProcessorEventConsumer;
import com.android.tools.r8.startup.generated.InstrumentationServerFactory;
import com.android.tools.r8.startup.generated.InstrumentationServerImplFactory;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class StartupInstrumentation {

  private final AppView<AppInfo> appView;
  private final IRConverter converter;
  private final DexItemFactory dexItemFactory;
  private final InternalOptions options;
  private final StartupInstrumentationReferences references;
  private final InstrumentationOptions instrumentationOptions;

  private StartupInstrumentation(AppView<AppInfo> appView) {
    this.appView = appView;
    this.converter = new IRConverter(appView);
    this.dexItemFactory = appView.dexItemFactory();
    this.options = appView.options();
    this.references = new StartupInstrumentationReferences(dexItemFactory);
    this.instrumentationOptions = options.getInstrumentationOptions();
  }

  public static void run(AppView<AppInfo> appView, ExecutorService executorService)
      throws ExecutionException {
    if (appView.options().getInstrumentationOptions().isInstrumentationEnabled()
        && appView.options().isGeneratingDex()) {
      StartupInstrumentation startupInstrumentation = new StartupInstrumentation(appView);
      startupInstrumentation.instrumentAllClasses(executorService);
      startupInstrumentation.injectStartupRuntimeLibrary(executorService);
    }
  }

  private void instrumentAllClasses(ExecutorService executorService) throws ExecutionException {
    ThreadUtils.processItems(
        appView.appInfo().classes(),
        this::instrumentClass,
        appView.options().getThreadingModule(),
        executorService);
  }

  private void injectStartupRuntimeLibrary(ExecutorService executorService)
      throws ExecutionException {
    // Only inject the startup instrumentation server if it is not already in the app.
    if (appView.definitionFor(references.instrumentationServerImplType) != null) {
      return;
    }

    // If the startup options has a synthetic context for the startup instrumentation server, then
    // only inject the runtime library if the synthetic context exists in program to avoid injecting
    // the runtime library multiple times when there is separate compilation.
    if (instrumentationOptions.hasSyntheticServerContext()) {
      DexType syntheticContext =
          dexItemFactory.createType(
              DescriptorUtils.javaTypeToDescriptor(
                  instrumentationOptions.getSyntheticServerContext()));
      if (asProgramClassOrNull(appView.definitionFor(syntheticContext)) == null) {
        return;
      }
    }

    List<DexProgramClass> extraProgramClasses = createStartupRuntimeLibraryClasses();
    MethodProcessorEventConsumer eventConsumer = MethodProcessorEventConsumer.empty();
    converter.processClassesConcurrently(
        extraProgramClasses,
        eventConsumer,
        MethodConversionOptions.forD8(appView),
        executorService);

    DexApplication newApplication =
        appView.app().builder().addProgramClasses(extraProgramClasses).build();
    appView.setAppInfo(
        new AppInfo(
            appView.appInfo().getSyntheticItems().commit(newApplication),
            appView.appInfo().getMainDexInfo()));
  }

  private List<DexProgramClass> createStartupRuntimeLibraryClasses() {
    DexProgramClass instrumentationServerImplClass =
        InstrumentationServerImplFactory.createClass(dexItemFactory);
    if (instrumentationOptions.hasTag()) {
      instrumentationServerImplClass
          .lookupUniqueStaticFieldWithName(dexItemFactory.createString("writeToLogcat"))
          .setStaticValue(DexValueBoolean.create(true));
      instrumentationServerImplClass
          .lookupUniqueStaticFieldWithName(
              dexItemFactory.createString("writeToLogcatIncludeDuplicates"))
          .setStaticValue(DexValueBoolean.create(true));
      instrumentationServerImplClass
          .lookupUniqueStaticFieldWithName(dexItemFactory.createString("logcatTag"))
          .setStaticValue(
              new DexValueString(dexItemFactory.createString(instrumentationOptions.getTag())));
    }

    return ImmutableList.of(
        InstrumentationServerFactory.createClass(dexItemFactory), instrumentationServerImplClass);
  }

  private void instrumentClass(DexProgramClass clazz) {
    // Do not instrument the instrumentation server if it is already in the app.
    if (clazz.getType().isIdenticalTo(references.instrumentationServerType)
        || clazz.getType().isIdenticalTo(references.instrumentationServerImplType)) {
      return;
    }

    boolean addedClassInitializer = ensureClassInitializer(clazz);
    clazz.forEachProgramMethodMatching(
        DexEncodedMethod::hasCode,
        method ->
            instrumentMethod(
                method, method.getDefinition().isClassInitializer() && addedClassInitializer));
  }

  private boolean ensureClassInitializer(DexProgramClass clazz) {
    if (clazz.hasClassInitializer()
        || !instrumentationOptions.isExecutedClassesAndMethodsInstrumentationEnabled()) {
      return false;
    }
    ComputedApiLevel computedApiLevel =
        appView.apiLevelCompute().computeInitialMinApiLevel(options);
    DexReturnVoid returnInstruction = new DexReturnVoid();
    returnInstruction.setOffset(0);
    clazz.addDirectMethod(
        DexEncodedMethod.syntheticBuilder()
            .setAccessFlags(MethodAccessFlags.createForClassInitializer())
            .setApiLevelForCode(computedApiLevel)
            .setApiLevelForDefinition(computedApiLevel)
            .setClassFileVersion(CfVersion.V1_6)
            .setCode(new DexCode(0, 0, 0, new DexInstruction[] {returnInstruction}))
            .setMethod(dexItemFactory.createClassInitializer(clazz.getType()))
            .build());
    return true;
  }

  private void instrumentMethod(ProgramMethod method, boolean skipMethodLogging) {
    // Disable StringSwitch conversion to avoid having to run the StringSwitchRemover before
    // finalizing the code.
    MutableMethodConversionOptions conversionOptions = MethodConversionOptions.forD8(appView);
    IRCode code = method.buildIR(appView, conversionOptions);
    BasicBlockIterator blocks = code.listIterator();
    InstructionListIterator instructionIterator = blocks.next().listIterator();
    instructionIterator.positionBeforeNextInstructionThatMatches(not(Instruction::isArgument));

    // Insert invoke to record that the enclosing class is a startup class.
    if (instrumentationOptions.isExecutedClassesAndMethodsInstrumentationEnabled()
        && method.getDefinition().isClassInitializer()) {
      DexType classToPrint = method.getHolderType();
      Value descriptorValue =
          instructionIterator.insertConstStringInstruction(
              appView, code, dexItemFactory.createString(classToPrint.toSmaliString()));
      instructionIterator.add(
          InvokeStatic.builder()
              .setMethod(references.addMethod)
              .setSingleArgument(descriptorValue)
              .setPosition(Position.syntheticNone())
              .build());
    }

    // Insert invoke to record the execution of the current method.
    if (!skipMethodLogging) {
      Value descriptorValue =
          instructionIterator.insertConstStringInstruction(
              appView, code, dexItemFactory.createString(method.getReference().toSmaliString()));
      if (instrumentationOptions.isExecutedClassesAndMethodsInstrumentationEnabled()) {
        instructionIterator.add(
            InvokeStatic.builder()
                .setMethod(references.addMethod)
                .setSingleArgument(descriptorValue)
                .setPosition(Position.syntheticNone())
                .build());
      }

      Set<DexMethod> callSitesToInstrument = instrumentationOptions.getCallSitesToInstrument();
      if (!callSitesToInstrument.isEmpty()) {
        do {
          while (instructionIterator.hasNext()) {
            Instruction instruction = instructionIterator.next();
            if (instruction.isInvokeMethod()) {
              DexMethod invokedMethod = instruction.asInvokeMethod().getInvokedMethod();
              if (callSitesToInstrument.contains(invokedMethod)) {
                instructionIterator.previous();
                ConstString calleeString =
                    ConstString.builder()
                        .setFreshOutValue(
                            code,
                            dexItemFactory.stringType.toTypeElement(appView, definitelyNotNull()))
                        .setPosition(instruction.getPosition())
                        .setValue(dexItemFactory.createString(invokedMethod.toSmaliString()))
                        .build();
                instructionIterator =
                    instructionIterator.addPossiblyThrowingInstructionsToPossiblyThrowingBlock(
                        code,
                        blocks,
                        ImmutableList.of(
                            calleeString,
                            InvokeStatic.builder()
                                .setMethod(references.addCall)
                                .setArguments(descriptorValue, calleeString.outValue())
                                .setPosition(instruction.getPosition())
                                .build()),
                        options);
                Instruction next = instructionIterator.next();
                assert next == instruction;
              }
            }
          }
          if (blocks.hasNext()) {
            instructionIterator = blocks.next().listIterator();
          }
        } while (instructionIterator.hasNext());
      }
    }

    code.removeRedundantBlocks();
    converter.deadCodeRemover.run(code, Timing.empty());

    DexCode instrumentedCode =
        new IRToDexFinalizer(appView, converter.deadCodeRemover)
            .finalizeCode(code, BytecodeMetadataProvider.empty(), Timing.empty());
    method.setCode(instrumentedCode, appView);
  }
}
