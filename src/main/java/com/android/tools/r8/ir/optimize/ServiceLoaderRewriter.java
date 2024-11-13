// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize;

import com.android.tools.r8.FeatureSplit;
import com.android.tools.r8.androidapi.AndroidApiLevelCompute;
import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.features.ClassToFeatureSplitMap;
import com.android.tools.r8.graph.AppServices;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory.IteratorMethods;
import com.android.tools.r8.graph.DexItemFactory.ServiceLoaderMethods;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.ConstClass;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.IRCodeInstructionListIterator;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InvokeDirect;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.code.InvokeVirtual;
import com.android.tools.r8.ir.code.NewInstance;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.Throw;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.ir.conversion.passes.CodeRewriterPass;
import com.android.tools.r8.ir.conversion.passes.result.CodeRewriterResult;
import com.android.tools.r8.ir.desugar.ServiceLoaderSourceCode;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.ConsumerUtils;
import com.android.tools.r8.utils.DominatorChecker;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.WorkList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * ServiceLoaderRewriter will attempt to rewrite calls on the form of: ServiceLoader.load(X.class,
 * X.class.getClassLoader()).iterator() ... to Arrays.asList(new X[] { new Y(), ..., new Z()
 * }).iterator() for classes Y..Z specified in the META-INF/services/X.
 *
 * <p>The reason for this optimization is to not have the ServiceLoader.load on the distributed R8
 * in AGP, since this can potentially conflict with debug versions added to a build.gradle file as:
 * classpath 'com.android.tools:r8:a.b.c' Additionally, it might also result in improved performance
 * because ServiceLoader.load is really slow on Android because it has to do a reflective lookup.
 *
 * <p>A call to ServiceLoader.load(X.class) is implicitly the same as ServiceLoader.load(X.class,
 * Thread.getContextClassLoader()) which can have different behavior in Android if a process host
 * multiple applications:
 *
 * <pre>
 * See <a href="https://stackoverflow.com/questions/13407006/android-class-loader-may-fail-for-
 * processes-that-host-multiple-applications">https://stackoverflow.com/questions/13407006/
 * android-class-loader-may-fail-for-processes-that-host-multiple-applications</a>
 * </pre>
 *
 * We therefore only conservatively rewrite if the invoke is on is on the form
 * ServiceLoader.load(X.class, X.class.getClassLoader()) or ServiceLoader.load(X.class, null).
 *
 * <p>Android Nougat do not use ClassLoader.getSystemClassLoader() when passing null and will almost
 * certainly fail when trying to find the service. It seems unlikely that programs rely on this
 * behaviour.
 */
public class ServiceLoaderRewriter extends CodeRewriterPass<AppInfoWithLiveness> {

  private final AndroidApiLevelCompute apiLevelCompute;
  private final ServiceLoaderMethods serviceLoaderMethods;
  private final IteratorMethods iteratorMethods;
  private final boolean hasAssumeNoSideEffects;

  public ServiceLoaderRewriter(AppView<?> appView) {
    super(appView);
    this.apiLevelCompute = appView.apiLevelCompute();
    this.serviceLoaderMethods = appView.dexItemFactory().serviceLoaderMethods;
    this.iteratorMethods = appView.dexItemFactory().iteratorMethods;
    hasAssumeNoSideEffects =
        appView.getAssumeInfoCollection().isSideEffectFree(serviceLoaderMethods.load);
  }

  @Override
  protected String getRewriterId() {
    return "ServiceLoaderRewriter";
  }

  @Override
  protected boolean shouldRewriteCode(IRCode code, MethodProcessor methodProcessor) {
    return appView.hasLiveness()
        && methodProcessor.isPrimaryMethodProcessor()
        && options.enableServiceLoaderRewriting
        && code.metadata().mayHaveConstClass()
        && code.metadata().mayHaveInvokeStatic()
        && code.metadata().mayHaveInvokeVirtual();
  }

  private boolean shouldReportWhyAreYouNotInliningServiceLoaderLoad() {
    AppInfoWithLiveness appInfo = appView().appInfo();
    return appInfo.isWhyAreYouNotInliningMethod(serviceLoaderMethods.load)
        || appInfo.isWhyAreYouNotInliningMethod(serviceLoaderMethods.loadWithClassLoader);
  }

  @Override
  protected CodeRewriterResult rewriteCode(
      IRCode code,
      MethodProcessor methodProcessor,
      MethodProcessingContext methodProcessingContext) {
    // Create a map from service type to loader methods local to this context since two
    // service loader calls to the same type in different methods and in the same wave can race.
    Map<DexType, DexEncodedMethod> synthesizedServiceLoaders = new IdentityHashMap<>();
    IRCodeInstructionListIterator iterator = code.instructionListIterator();
    Map<Instruction, Instruction> replacements = new HashMap<>();
    Map<Instruction, List<Instruction>> replacementExtras = new HashMap<>();

    while (iterator.hasNext()) {
      Instruction instruction = iterator.next();

      // Find ServiceLoader.load() calls.
      InvokeStatic invokeInstr = instruction.asInvokeStatic();
      if (invokeInstr == null
          || !serviceLoaderMethods.isLoadMethod(invokeInstr.getInvokedMethod())) {
        continue;
      }

      // See if it can be optimized.
      ServiceLoaderLoadResult loadResult = analyzeServiceLoaderLoad(code, invokeInstr);
      if (loadResult == null) {
        continue;
      }
      // See if there is a subsequent iterator() call that can be optimized.
      DirectRewriteResult directRewriteResult = analyzeForDirectRewrite(loadResult);

      // Remove ServiceLoader.load()
      replacements.put(loadResult.loadInvoke, code.createConstNull());
      // Remove getClassLoader() if we are about to remove all users.
      if (loadResult.classLoaderInvoke != null
          && loadResult.classLoaderInvoke.outValue().aliasedUsers().stream()
              .allMatch(ins -> ins.isAssume() || replacements.containsKey(ins))) {
        replacements.put(loadResult.classLoaderInvoke, code.createConstNull());
      }
      if (directRewriteResult != null) {
        populateDirectRewriteChanges(
            code, replacements, replacementExtras, loadResult, directRewriteResult);
      } else {
        populateSyntheticChanges(
            code,
            methodProcessor,
            methodProcessingContext,
            synthesizedServiceLoaders,
            replacements,
            loadResult);
      }
    }

    if (replacements.isEmpty()) {
      return CodeRewriterResult.NO_CHANGE;
    }

    AffectedValues affectedValues = new AffectedValues();
    iterator = code.instructionListIterator();
    while (iterator.hasNext()) {
      Instruction instruction = iterator.next();
      Instruction replacement = replacements.get(instruction);
      if (replacement == null) {
        continue;
      }
      iterator.replaceCurrentInstruction(replacement, affectedValues);
      List<Instruction> extras = replacementExtras.get(instruction);
      if (extras != null) {
        iterator.addPossiblyThrowingInstructionsToPossiblyThrowingBlock(extras, options);
        if (ListUtils.last(extras).isThrow()) {
          iterator.removeRemainingInBlockIgnoreOutValue();
        }
      }
    }

    code.removeUnreachableBlocks(affectedValues, ConsumerUtils.emptyConsumer());
    code.removeRedundantBlocks();
    affectedValues.narrowingWithAssumeRemoval(appView, code);
    assert code.isConsistentSSA(appView);

    return CodeRewriterResult.HAS_CHANGED;
  }

  private void populateSyntheticChanges(
      IRCode code,
      MethodProcessor methodProcessor,
      MethodProcessingContext methodProcessingContext,
      Map<DexType, DexEncodedMethod> synthesizedServiceLoaders,
      Map<Instruction, Instruction> replacements,
      ServiceLoaderLoadResult loadResult) {
    DexEncodedMethod synthesizedMethod =
        synthesizedServiceLoaders.computeIfAbsent(
            loadResult.serviceType,
            service -> {
              DexEncodedMethod addedMethod =
                  createSynthesizedMethod(
                      service, loadResult.implClasses, methodProcessor, methodProcessingContext);
              if (appView.options().isGeneratingClassFiles()) {
                addedMethod.upgradeClassFileVersion(
                    code.context().getDefinition().getClassFileVersion());
              }
              return addedMethod;
            });
    InvokeStatic synthesizedInvoke =
        new InvokeStatic(
            synthesizedMethod.getReference(),
            loadResult.iteratorInvoke.outValue(),
            Collections.emptyList());
    replacements.put(loadResult.iteratorInvoke, synthesizedInvoke);
  }

  private void populateDirectRewriteChanges(
      IRCode code,
      Map<Instruction, Instruction> replacements,
      Map<Instruction, List<Instruction>> replacementExtras,
      ServiceLoaderLoadResult loadResult,
      DirectRewriteResult directRewriteResult) {
    // Remove ServiceLoader.iterator()
    replacements.put(loadResult.iteratorInvoke, code.createConstNull());

    // Iterator.hasNext() --> true / false
    if (directRewriteResult.priorHasNextInstr != null) {
      replacements.put(
          directRewriteResult.priorHasNextInstr,
          code.createBooleanConstant(!loadResult.implClasses.isEmpty()));
    }
    if (directRewriteResult.nextInstr != null) {
      Position position = directRewriteResult.nextInstr.getPosition();
      if (loadResult.implClasses.isEmpty()) {
        // Iterator.next() -> null
        replacements.put(directRewriteResult.nextInstr, code.createConstNull());

        // throw new NoSuchElementException()
        NewInstance newInstanceInstr =
            new NewInstance(
                dexItemFactory.noSuchElementExceptionType,
                code.createValue(
                    dexItemFactory.noSuchElementExceptionType.toNonNullTypeElement(appView)));
        InvokeDirect initInstr =
            new InvokeDirect(
                dexItemFactory.noSuchElementExceptionInit,
                null,
                List.of(newInstanceInstr.outValue()));
        Throw throwInstr = new Throw(newInstanceInstr.outValue());

        newInstanceInstr.setPosition(position);
        initInstr.setPosition(position);
        throwInstr.setPosition(position);
        replacementExtras.put(
            directRewriteResult.nextInstr, List.of(newInstanceInstr, initInstr, throwInstr));
      } else {
        // Iterator.next() -> new ServiceImpl()
        DexType clazz = loadResult.implClasses.get(0).getType();
        NewInstance newInstance =
            new NewInstance(
                clazz,
                code.createValue(
                    clazz.toNonNullTypeElement(appView),
                    directRewriteResult.nextInstr.getLocalInfo()));
        replacements.put(directRewriteResult.nextInstr, newInstance);
        InvokeDirect initInstr =
            new InvokeDirect(
                dexItemFactory.createInstanceInitializer(clazz),
                null,
                List.of(newInstance.outValue()));
        initInstr.setPosition(position);
        replacementExtras.put(directRewriteResult.nextInstr, List.of(initInstr));
      }
    }
    if (directRewriteResult.subsequentHasNextInstr != null) {
      replacements.put(
          directRewriteResult.subsequentHasNextInstr,
          code.createBooleanConstant(loadResult.implClasses.size() > 1));
    }
  }

  private ServiceLoaderLoadResult analyzeServiceLoaderLoad(IRCode code, InvokeStatic invokeInstr) {
    // Check that the first argument is a const class.
    Value argument = invokeInstr.getFirstArgument().getAliasedValue();
    if (!argument.isDefinedByInstructionSatisfying(Instruction::isConstClass)) {
      report(code.context(), null, "The service loader type could not be determined");
      return null;
    }

    ConstClass constClass = argument.getDefinition().asConstClass();
    DexType serviceType = constClass.getType();
    if (invokeInstr.getInvokedMethod().isNotIdenticalTo(serviceLoaderMethods.loadWithClassLoader)) {
      report(
          code.context(),
          serviceType,
          "Inlining is only supported for `java.util.ServiceLoader.load(java.lang.Class,"
              + " java.lang.ClassLoader)`");
      return null;
    }

    String invalidUserMessage =
        "The returned ServiceLoader instance must only be used in a call to `java.util.Iterator"
            + " java.lang.ServiceLoader.iterator()`";
    Value serviceLoaderLoadOut = invokeInstr.outValue();
    if (!serviceLoaderLoadOut.hasSingleUniqueUser() || serviceLoaderLoadOut.hasPhiUsers()) {
      report(code.context(), serviceType, invalidUserMessage);
      return null;
    }

    // Check that the only user is a call to iterator().
    InvokeVirtual iteratorInvoke = serviceLoaderLoadOut.singleUniqueUser().asInvokeVirtual();
    if (iteratorInvoke == null
        || iteratorInvoke.getInvokedMethod().isNotIdenticalTo(serviceLoaderMethods.iterator)) {
      report(code.context(), serviceType, invalidUserMessage + ", but found other usages");
      return null;
    }

    // Check that the service is not kept.
    if (!options.allowServiceLoaderRewritingPinnedTypes
        && appView().appInfo().isPinnedWithDefinitionLookup(serviceType)) {
      report(code.context(), serviceType, "The service loader type is kept");
      return null;
    }

    // Package-private service types are allowed only when the load() call is made from the same
    // package. Not worth modelling.
    DexClass serviceTypeResolved = appView.definitionFor(serviceType);
    if (serviceTypeResolved == null) {
      report(code.context(), serviceType, "Service type could not be resolved");
      return null;
    }
    if (!serviceTypeResolved.isPublic()) {
      report(code.context(), serviceType, "Service type must be public");
      return null;
    }

    // Check that ClassLoader used is the ClassLoader defined for the service configuration
    // that we are instantiating or NULL.
    Value classLoaderValue = invokeInstr.getLastArgument().getAliasedValue();
    if (classLoaderValue.isPhi()) {
      report(
          code.context(),
          serviceType,
          "The java.lang.ClassLoader argument must be defined locally as null or "
              + serviceType
              + ".class.getClassLoader()");
      return null;
    }
    boolean isNullClassLoader = classLoaderValue.getType().isNullType();
    InvokeVirtual classLoaderInvoke = classLoaderValue.getDefinition().asInvokeVirtual();
    boolean isGetClassLoaderOnConstClass =
        classLoaderInvoke != null
            && classLoaderInvoke.arguments().size() == 1
            && classLoaderInvoke.getReceiver().getAliasedValue().isConstClass()
            && classLoaderInvoke
                .getReceiver()
                .getAliasedValue()
                .getDefinition()
                .asConstClass()
                .getType()
                .isIdenticalTo(serviceType);
    if (!isNullClassLoader && !isGetClassLoaderOnConstClass) {
      report(
          code.context(),
          serviceType,
          "The java.lang.ClassLoader argument must be defined locally as null or "
              + serviceType
              + ".class.getClassLoader()");
      return null;
    }

    // Check that we are not service loading anything from a feature into base.
    AppServices appServices = appView.appServices();
    // Check that we are not service loading anything from a feature into base.
    if (hasServiceImplementationInDifferentFeature(code, serviceType, isNullClassLoader)) {
      return null;
    }

    List<DexType> dexTypes = appServices.serviceImplementationsFor(serviceType);
    List<DexClass> implClasses = new ArrayList<>(dexTypes.size());
    for (DexType serviceImpl : dexTypes) {
      DexClass serviceImplementation = appView.definitionFor(serviceImpl);
      if (serviceImplementation == null) {
        report(
            code.context(),
            serviceType,
            "Unable to find definition for service implementation " + serviceImpl.getTypeName());
        return null;
      }
      if (appView.isSubtype(serviceImpl, serviceType).isFalse()) {
        report(
            code.context(),
            serviceType,
            "Implementation is not a subtype of the service: " + serviceImpl.getTypeName());
        return null;
      }
      DexEncodedMethod method = serviceImplementation.getDefaultInitializer();
      if (method == null) {
        report(
            code.context(),
            serviceType,
            "Implementation has no default constructor: " + serviceImpl.getTypeName());
        return null;
      }
      if (!method.getAccessFlags().wasPublic()) {
        // A non-public constructor causes a ServiceConfigurationError on APIs 24 & 25 (Nougat).
        report(
            code.context(),
            serviceType,
            "Implementation's default constructor is not public: " + serviceImpl.getTypeName());
        return null;
      }
      implClasses.add(serviceImplementation);
    }

    return new ServiceLoaderLoadResult(
        invokeInstr, classLoaderInvoke, serviceType, implClasses, iteratorInvoke);
  }

  /**
   * Checks that:
   *
   * <pre>
   *   * -assumenosideeffects for ServiceLoader.load() is set.
   *   * ServiceLoader.iterator() is used only by a single .hasNext() and/or .next()
   *   * .iterator(), .hasNext(), and .next() are all in the same try/catch.
   *   * .hasNext() never comes after a call to .next()
   *   * .hasNext() and .next() are not in a loop.
   * </pre>
   */
  private DirectRewriteResult analyzeForDirectRewrite(ServiceLoaderLoadResult loadResult) {
    // Require -assumenosideeffects class java.util.ServiceLoader { java.lang.Object load(...); }
    // because this direct rewriting does not wrap exceptions in ServiceConfigurationError.
    if (!hasAssumeNoSideEffects) {
      return null;
    }
    InvokeVirtual iteratorInvoke = loadResult.iteratorInvoke;

    if (iteratorInvoke.outValue().hasPhiUsers()) {
      return null;
    }
    InvokeMethod priorHasNextInstr = null;
    InvokeMethod nextInstr = null;
    InvokeMethod subsequentHasNextInstr = null;
    // We only bother to support a single call to hasNext() and next(), and they must appear within
    // the same try/catch.
    for (Instruction user : iteratorInvoke.outValue().aliasedUsers()) {
      if (user.isAssume()) {
        if (user.outValue().hasPhiUsers()) {
          return null;
        }
        continue;
      }
      if (!user.getBlock().hasEquivalentCatchHandlers(iteratorInvoke.getBlock())) {
        return null;
      }
      InvokeMethod curCall = user.asInvokeMethod();
      if (curCall == null) {
        return null;
      }
      if (curCall.getInvokedMethod().isIdenticalTo(iteratorMethods.hasNext)) {
        if (subsequentHasNextInstr != null) {
          return null;
        }
        if (priorHasNextInstr != null) {
          subsequentHasNextInstr = curCall;
        } else {
          priorHasNextInstr = curCall;
        }
      } else if (curCall.getInvokedMethod().isIdenticalTo(iteratorMethods.next)) {
        if (nextInstr != null) {
          return null;
        }
        nextInstr = curCall;
      } else {
        return null;
      }
    }

    // Figure out which hasNext is the first one.
    BasicBlock iteratorBlock = iteratorInvoke.getBlock();
    BasicBlock priorHasNextBlock = priorHasNextInstr != null ? priorHasNextInstr.getBlock() : null;
    BasicBlock subsequentHasNextBlock =
        subsequentHasNextInstr != null ? subsequentHasNextInstr.getBlock() : null;
    BasicBlock nextBlock = nextInstr != null ? nextInstr.getBlock() : null;
    if (priorHasNextBlock != null && nextBlock != null) {
      if (subsequentHasNextBlock != null) {
        if (priorHasNextBlock == subsequentHasNextBlock) {
          // This would be odd, since hasNext() is usually used in conditionals.
          return null;
        }
        // Make sure subsequentHasNextBlock and priorHasNextBlock are not reversed.
        if (hasPredecessorPathTo(iteratorBlock, priorHasNextBlock, subsequentHasNextBlock)) {
          InvokeMethod tmp = priorHasNextInstr;
          priorHasNextInstr = subsequentHasNextInstr;
          subsequentHasNextInstr = tmp;
          priorHasNextBlock = subsequentHasNextBlock;
          subsequentHasNextBlock = subsequentHasNextInstr.getBlock();
        }
        // Ensure the second hasNext() comes after the call to next().
        if (nextBlock == subsequentHasNextBlock) {
          if (nextBlock.iterator(nextInstr).nextUntil(subsequentHasNextInstr) == null) {
            return null;
          }
        } else if (!DominatorChecker.check(iteratorBlock, subsequentHasNextBlock, nextBlock)) {
          return null;
        }
      }

      // Ensure the first hasNext() is not after next().
      if (priorHasNextBlock == nextBlock) {
        if (nextBlock.iterator(nextInstr).nextUntil(priorHasNextInstr) != null) {
          return null;
        }
      } else if (hasPredecessorPathTo(iteratorBlock, priorHasNextBlock, nextBlock)) {
        return null;
      }
    }

    // Make sure each instruction can be run at most once (no loops).
    if (priorHasNextBlock != null && loopExists(iteratorBlock, priorHasNextBlock)) {
      return null;
    }
    if (nextBlock != null && loopExists(iteratorBlock, nextBlock)) {
      return null;
    }
    if (subsequentHasNextBlock != null && loopExists(iteratorBlock, subsequentHasNextBlock)) {
      return null;
    }

    return new DirectRewriteResult(priorHasNextInstr, nextInstr, subsequentHasNextInstr);
  }

  private static boolean loopExists(BasicBlock subgraphEntryBlock, BasicBlock targetBlock) {
    return hasPredecessorPathTo(subgraphEntryBlock, targetBlock, targetBlock);
  }

  private static boolean hasPredecessorPathTo(
      BasicBlock subgraphEntryBlock, BasicBlock subgraphExitBlock, BasicBlock targetBlock) {
    if (subgraphEntryBlock == subgraphExitBlock) {
      return false;
    }
    if (subgraphEntryBlock == targetBlock) {
      return true;
    }
    WorkList<BasicBlock> workList = WorkList.newIdentityWorkList();
    workList.markAsSeen(subgraphEntryBlock);
    workList.addIfNotSeen(subgraphExitBlock.getPredecessors());
    while (workList.hasNext()) {
      BasicBlock curBlock = workList.next();
      if (curBlock == targetBlock) {
        return true;
      }
      workList.addIfNotSeen(curBlock.getPredecessors());
    }
    return false;
  }

  private void report(ProgramMethod method, DexType serviceLoaderType, String message) {
    if (shouldReportWhyAreYouNotInliningServiceLoaderLoad()) {
      appView
          .reporter()
          .info(
              new ServiceLoaderRewriterDiagnostic(
                  method.getOrigin(),
                  "Could not inline ServiceLoader.load"
                      + (serviceLoaderType == null
                          ? ""
                          : (" of type " + serviceLoaderType.getTypeName()))
                      + ": "
                      + message));
    }
  }

  private boolean hasServiceImplementationInDifferentFeature(
      IRCode code, DexType serviceType, boolean baseFeatureOnly) {
    AppView<AppInfoWithLiveness> appViewWithClasses = appView();
    ClassToFeatureSplitMap classToFeatureSplitMap =
        appViewWithClasses.appInfo().getClassToFeatureSplitMap();
    if (classToFeatureSplitMap.isEmpty()) {
      return false;
    }
    Map<FeatureSplit, List<DexType>> featureImplementations =
        appView.appServices().serviceImplementationsByFeatureFor(serviceType);
    if (featureImplementations == null || featureImplementations.isEmpty()) {
      return false;
    }
    DexProgramClass serviceClass = appView.definitionForProgramType(serviceType);
    if (serviceClass == null) {
      return false;
    }
    FeatureSplit serviceFeature =
        classToFeatureSplitMap.getFeatureSplit(serviceClass, appViewWithClasses);
    if (baseFeatureOnly && !serviceFeature.isBase()) {
      report(
          code.context(),
          serviceType,
          "ClassLoader arg was null and service interface is in non-base feature");
      return true;
    }
    for (var entry : featureImplementations.entrySet()) {
      FeatureSplit metaInfFeature = entry.getKey();
      if (!metaInfFeature.isBase()) {
        if (baseFeatureOnly) {
          report(
              code.context(),
              serviceType,
              "ClassLoader arg was null and META-INF/ service entry found in non-base feature");
          return true;
        }
        if (metaInfFeature != serviceFeature) {
          report(
              code.context(),
              serviceType,
              "META-INF/ service found in different feature from service interface");
          return true;
        }
      }
      for (DexType impl : entry.getValue()) {
        FeatureSplit implFeature = classToFeatureSplitMap.getFeatureSplit(impl, appViewWithClasses);
        if (implFeature != serviceFeature) {
          report(
              code.context(),
              serviceType,
              "Implementation found in different feature from service interface: " + impl);
          return true;
        }
      }
    }
    return false;
  }

  private DexEncodedMethod createSynthesizedMethod(
      DexType serviceType,
      List<DexClass> classes,
      MethodProcessor methodProcessor,
      MethodProcessingContext methodProcessingContext) {
    DexProto proto =
        appView.dexItemFactory().createProto(appView.dexItemFactory().javaUtilIteratorType);
    ProgramMethod method =
        appView
            .getSyntheticItems()
            .createMethod(
                kinds -> kinds.SERVICE_LOADER,
                methodProcessingContext.createUniqueContext(),
                appView,
                builder ->
                    builder
                        .setAccessFlags(MethodAccessFlags.createPublicStaticSynthetic())
                        .setProto(proto)
                        .setApiLevelForDefinition(appView.computedMinApiLevel())
                        .setApiLevelForCode(
                            apiLevelCompute.computeApiLevelForDefinition(
                                ListUtils.map(classes, clazz -> clazz.type)))
                        .setCode(
                            m ->
                                ServiceLoaderSourceCode.generate(
                                    serviceType, classes, appView.dexItemFactory())));
    methodProcessor.scheduleDesugaredMethodForProcessing(method);
    methodProcessor
        .getEventConsumer()
        .acceptServiceLoaderLoadUtilityMethod(method, methodProcessingContext.getMethodContext());
    return method.getDefinition();
  }

  private static class ServiceLoaderLoadResult {
    public final InvokeStatic loadInvoke;
    public final InvokeVirtual classLoaderInvoke;
    public final DexType serviceType;
    public final List<DexClass> implClasses;
    public final InvokeVirtual iteratorInvoke;

    public ServiceLoaderLoadResult(
        InvokeStatic loadInvoke,
        InvokeVirtual classLoaderInvoke,
        DexType serviceType,
        List<DexClass> implClasses,
        InvokeVirtual iteratorInvoke) {
      this.loadInvoke = loadInvoke;
      this.classLoaderInvoke = classLoaderInvoke;
      this.serviceType = serviceType;
      this.implClasses = implClasses;
      this.iteratorInvoke = iteratorInvoke;
      }
  }

  private static class DirectRewriteResult {
    public final InvokeMethod priorHasNextInstr;
    public final InvokeMethod nextInstr;
    public final InvokeMethod subsequentHasNextInstr;

    public DirectRewriteResult(
        InvokeMethod priorHasNextInstr,
        InvokeMethod nextInstr,
        InvokeMethod subsequentHasNextInstr) {
      this.priorHasNextInstr = priorHasNextInstr;
      this.nextInstr = nextInstr;
      this.subsequentHasNextInstr = subsequentHasNextInstr;
    }
  }
}
