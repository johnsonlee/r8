// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static com.android.tools.r8.profile.art.ArtProfileCompletenessChecker.CompletenessExceptions.ALLOW_MISSING_ENUM_UNBOXING_UTILITY_METHODS;
import static com.android.tools.r8.utils.AssertionUtils.forTesting;
import static com.android.tools.r8.utils.ExceptionUtils.unwrapExecutionException;

import com.android.build.shrinker.r8integration.LegacyResourceShrinker;
import com.android.build.shrinker.r8integration.LegacyResourceShrinker.ShrinkerResult;
import com.android.tools.r8.DexIndexedConsumer.ForwardingConsumer;
import com.android.tools.r8.androidapi.ApiReferenceStubber;
import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.dex.ApplicationWriter;
import com.android.tools.r8.dex.Marker;
import com.android.tools.r8.diagnostic.R8VersionDiagnostic;
import com.android.tools.r8.errors.CheckDiscardDiagnostic;
import com.android.tools.r8.experimental.graphinfo.GraphConsumer;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppServices;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexApplicationReadFlags;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DirectMappedDexApplication;
import com.android.tools.r8.graph.GenericSignatureContextBuilder;
import com.android.tools.r8.graph.GenericSignatureCorrectnessHelper;
import com.android.tools.r8.graph.ImmediateAppSubtypingInfo;
import com.android.tools.r8.graph.LazyLoadedDexApplication;
import com.android.tools.r8.graph.ProgramDefinition;
import com.android.tools.r8.graph.PrunedItems;
import com.android.tools.r8.horizontalclassmerging.HorizontalClassMerger;
import com.android.tools.r8.inspector.internal.InspectorImpl;
import com.android.tools.r8.ir.conversion.IRConverter;
import com.android.tools.r8.ir.conversion.LirConverter;
import com.android.tools.r8.ir.conversion.MethodConversionOptions;
import com.android.tools.r8.ir.conversion.MethodConversionOptions.MutableMethodConversionOptions;
import com.android.tools.r8.ir.conversion.PrimaryR8IRConverter;
import com.android.tools.r8.ir.desugar.BackportedMethodRewriter;
import com.android.tools.r8.ir.desugar.CfClassSynthesizerDesugaringCollection;
import com.android.tools.r8.ir.desugar.CfClassSynthesizerDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.desugaredlibrary.DesugaredLibraryAmender;
import com.android.tools.r8.ir.desugar.desugaredlibrary.PrefixRewritingNamingLens;
import com.android.tools.r8.ir.desugar.desugaredlibrary.R8LibraryDesugaring;
import com.android.tools.r8.ir.desugar.itf.InterfaceMethodRewriter;
import com.android.tools.r8.ir.desugar.records.RecordFieldValuesRewriter;
import com.android.tools.r8.ir.desugar.records.RecordInstructionDesugaring;
import com.android.tools.r8.ir.desugar.varhandle.VarHandleDesugaring;
import com.android.tools.r8.ir.optimize.Inliner;
import com.android.tools.r8.ir.optimize.ListIterationRewriter;
import com.android.tools.r8.ir.optimize.NestReducer;
import com.android.tools.r8.ir.optimize.SwitchMapCollector;
import com.android.tools.r8.ir.optimize.enums.EnumUnboxingCfMethods;
import com.android.tools.r8.ir.optimize.info.OptimizationInfoRemover;
import com.android.tools.r8.ir.optimize.templates.CfUtilityMethodsForCodeOptimizations;
import com.android.tools.r8.jar.CfApplicationWriter;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.keepanno.ast.KeepDeclaration;
import com.android.tools.r8.kotlin.KotlinMetadataRewriter;
import com.android.tools.r8.kotlin.KotlinMetadataUtils;
import com.android.tools.r8.naming.IdentifierMinifier;
import com.android.tools.r8.naming.Minifier;
import com.android.tools.r8.naming.ProguardMapMinifier;
import com.android.tools.r8.naming.RecordRewritingNamingLens;
import com.android.tools.r8.naming.signature.GenericSignatureRewriter;
import com.android.tools.r8.optimize.BridgeHoistingToSharedSyntheticSuperClass;
import com.android.tools.r8.optimize.MemberRebindingAnalysis;
import com.android.tools.r8.optimize.MemberRebindingIdentityLens;
import com.android.tools.r8.optimize.MemberRebindingIdentityLensFactory;
import com.android.tools.r8.optimize.accessmodification.AccessModifier;
import com.android.tools.r8.optimize.bridgehoisting.BridgeHoisting;
import com.android.tools.r8.optimize.fields.FieldFinalizer;
import com.android.tools.r8.optimize.interfaces.analysis.CfOpenClosedInterfacesAnalysis;
import com.android.tools.r8.optimize.proto.ProtoNormalizer;
import com.android.tools.r8.optimize.redundantbridgeremoval.RedundantBridgeRemover;
import com.android.tools.r8.optimize.singlecaller.SingleCallerInliner;
import com.android.tools.r8.origin.CommandLineOrigin;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.profile.art.ArtProfileCompletenessChecker;
import com.android.tools.r8.profile.rewriting.ProfileCollectionAdditions;
import com.android.tools.r8.repackaging.Repackaging;
import com.android.tools.r8.shaking.AbstractMethodRemover;
import com.android.tools.r8.shaking.AnnotationRemover;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.AssumeInfoCollection;
import com.android.tools.r8.shaking.ClassInitFieldSynthesizer;
import com.android.tools.r8.shaking.DefaultTreePrunerConfiguration;
import com.android.tools.r8.shaking.DiscardedChecker;
import com.android.tools.r8.shaking.Enqueuer;
import com.android.tools.r8.shaking.Enqueuer.Mode;
import com.android.tools.r8.shaking.EnqueuerFactory;
import com.android.tools.r8.shaking.EnqueuerResult;
import com.android.tools.r8.shaking.KeepSpecificationSource;
import com.android.tools.r8.shaking.MainDexInfo;
import com.android.tools.r8.shaking.MainDexListBuilder;
import com.android.tools.r8.shaking.ProguardConfigurationRule;
import com.android.tools.r8.shaking.ProguardConfigurationUtils;
import com.android.tools.r8.shaking.RootSetUtils.MainDexRootSet;
import com.android.tools.r8.shaking.RootSetUtils.RootSet;
import com.android.tools.r8.shaking.RootSetUtils.RootSetBuilder;
import com.android.tools.r8.shaking.RuntimeTypeCheckInfo;
import com.android.tools.r8.shaking.TreePruner;
import com.android.tools.r8.shaking.TreePrunerConfiguration;
import com.android.tools.r8.shaking.WhyAreYouKeepingConsumer;
import com.android.tools.r8.startup.NonStartupInStartupOutliner;
import com.android.tools.r8.synthesis.SyntheticFinalization;
import com.android.tools.r8.synthesis.SyntheticItems;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.ExceptionDiagnostic;
import com.android.tools.r8.utils.ExceptionUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.ResourceShrinkerUtils;
import com.android.tools.r8.utils.SelfRetraceTest;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.timing.Timing;
import com.android.tools.r8.verticalclassmerging.VerticalClassMerger;
import com.google.common.collect.Iterables;
import com.google.common.io.ByteStreams;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;
import javax.xml.parsers.ParserConfigurationException;
import org.xml.sax.SAXException;

/**
 * The R8 compiler.
 *
 * <p>R8 performs whole-program optimizing compilation of Java bytecode. It supports compilation of
 * Java bytecode to Java bytecode or DEX bytecode. R8 supports tree-shaking the program to remove
 * unneeded code and it supports minification of the program names to reduce the size of the
 * resulting program.
 *
 * <p>The R8 API is intentionally limited and should "do the right thing" given a command. If this
 * API does not suffice please contact the D8/R8 team.
 *
 * <p>R8 supports some configuration using configuration files mostly compatible with the format of
 * the <a href="https://www.guardsquare.com/en/proguard">ProGuard</a> optimizer.
 *
 * <p>The compiler is invoked by calling {@link #run(R8Command) R8.run} with an appropriate {link
 * R8Command}. For example:
 *
 * <pre>
 *   R8.run(R8Command.builder()
 *       .addProgramFiles(inputPathA, inputPathB)
 *       .addLibraryFiles(androidJar)
 *       .setOutput(outputPath, OutputMode.DexIndexed)
 *       .build());
 * </pre>
 *
 * The above reads the input files denoted by {@code inputPathA} and {@code inputPathB}, compiles
 * them to DEX bytecode, using {@code androidJar} as the reference of the system runtime library,
 * and then writes the result to the directory or zip archive specified by {@code outputPath}.
 */
@KeepForApi
public class R8 {

  private final Timing timing;
  private final InternalOptions options;

  R8(InternalOptions options) {
    this.options = options;
    if (options.printMemory) {
      System.gc();
    }
    timing = Timing.createRoot("R8 " + Version.LABEL, options);
  }

  /**
   * Main API entry for the R8 compiler.
   *
   * <p>The R8 API is intentionally limited and should "do the right thing" given a command. If this
   * API does not suffice please contact the R8 team.
   *
   * @param command R8 command.
   */
  public static void run(R8Command command) throws CompilationFailedException {
    AndroidApp app = command.getInputApp();
    InternalOptions options = command.getInternalOptions();
    runForTesting(app, options);
  }

  /**
   * Main API entry for the R8 compiler.
   *
   * <p>The R8 API is intentionally limited and should "do the right thing" given a command. If this
   * API does not suffice please contact the R8 team.
   *
   * @param command R8 command.
   * @param executor executor service from which to get threads for multi-threaded processing.
   */
  public static void run(R8Command command, ExecutorService executor)
      throws CompilationFailedException {
    AndroidApp app = command.getInputApp();
    InternalOptions options = command.getInternalOptions();
    ExceptionUtils.withR8CompilationHandler(
        command.getReporter(),
        () -> {
          runInternal(app, options, executor);
        });
  }

  static void writeApplication(
      AppView<?> appView, AndroidApp inputApp, ExecutorService executorService)
      throws ExecutionException {
    InternalOptions options = appView.options();
    InspectorImpl.runInspections(options.outputInspections, appView.appInfo().classes());
    try {
      Marker marker = options.getMarker();
      assert marker != null;
      if (options.isGeneratingClassFiles()) {
        new CfApplicationWriter(appView, marker)
            .write(options.getClassFileConsumer(), executorService, inputApp);
      } else {
        ApplicationWriter.create(appView, marker).write(executorService, inputApp);
      }
    } catch (IOException e) {
      throw new RuntimeException("Cannot write application", e);
    }
  }

  static void runForTesting(AndroidApp app, InternalOptions options)
      throws CompilationFailedException {
    ExecutorService executor = ThreadUtils.getExecutorService(options);
    ExceptionUtils.withR8CompilationHandler(
        options.reporter,
        () -> {
          try {
            runInternal(app, options, executor);
          } finally {
            executor.shutdown();
          }
        });
  }

  static void runInternal(AndroidApp app, InternalOptions options, ExecutorService executor)
      throws IOException {
    if (options.partialCompilationConfiguration.isEnabled()
        && options.partialSubCompilationConfiguration == null) {
      try {
        new R8Partial(options).runInternal(app, executor);
      } catch (ResourceException e) {
        throw new RuntimeException(e);
      }
    } else {
      new R8(options).runInternal(app, executor);
    }
  }

  private static DirectMappedDexApplication getDirectApp(AppView<?> appView) {
    return appView.appInfo().app().asDirect();
  }

  @SuppressWarnings("DefaultCharset")
  void runInternal(AndroidApp inputApp, ExecutorService executorService) throws IOException {
    timing.begin("Run prelude");
    assert options.programConsumer != null;
    if (getClass().desiredAssertionStatus()) {
      options.reporter.info(new R8VersionDiagnostic());
    }
    // Synthetic assertion to check that testing assertions works and can be enabled.
    assert forTesting(options, () -> !options.testing.testEnableTestAssertions);
    if (options.printMemory) {
      // Run GC twice to remove objects with finalizers.
      System.gc();
      System.gc();
      Runtime runtime = Runtime.getRuntime();
      System.out.println("R8 is running with total memory:" + runtime.totalMemory());
      System.out.println("R8 is running with free memory:" + runtime.freeMemory());
      System.out.println("R8 is running with max memory:" + runtime.maxMemory());
    }
    options.prepareForReportingLibraryAndProgramDuplicates();
    timing.end();
    try {
      AppView<AppInfoWithClassHierarchy> appView;
      List<KeepDeclaration> keepDeclarations;
      {
        timing.begin("Read app");
        ApplicationReader applicationReader = new ApplicationReader(inputApp, options, timing);
        LazyLoadedDexApplication lazyLoaded = applicationReader.read(executorService);
        keepDeclarations =
            options.partialSubCompilationConfiguration != null
                ? options.partialSubCompilationConfiguration.asR8().getAndClearKeepDeclarations()
                : lazyLoaded.getKeepDeclarations();
        timing.begin("To direct app");
        DirectMappedDexApplication application = lazyLoaded.toDirect();
        timing.end();
        timing.end();
        options
            .getLibraryDesugaringOptions()
            .loadMachineDesugaredLibrarySpecification(timing, application);
        timing.begin("Read main dex classes");
        MainDexInfo mainDexInfo = applicationReader.readMainDexClassesForR8(application);
        timing.end();
        // Now that the dex-application is fully loaded, close any internal archive providers.
        timing.time("Close providers", () -> inputApp.closeInternalArchiveProviders());
        timing.begin("Create AppView");
        appView = AppView.createForR8(application, mainDexInfo);
        timing.end();
        timing.time(
            "Set app services", () -> appView.setAppServices(AppServices.builder(appView).build()));
        timing.time(
            "Collect synthetic inputs", () -> SyntheticItems.collectSyntheticInputs(appView));
      }
      timing.begin("Register references and more setup");
      assert ArtProfileCompletenessChecker.verify(appView);

      readKeepSpecifications(appView, keepDeclarations);

      // Check for potentially having pass-through of Cf-code for kotlin libraries.
      options.enableCfByteCodePassThrough =
          options.isGeneratingClassFiles() && KotlinMetadataUtils.mayProcessKotlinMetadata(appView);

      // Up-front check for valid library setup.
      if (!options.mainDexKeepRules.isEmpty()) {
        MainDexListBuilder.checkForAssumedLibraryTypes(appView.appInfo());
      }
      DesugaredLibraryAmender.run(appView);
      InterfaceMethodRewriter.checkForAssumedLibraryTypes(appView.appInfo(), options);
      BackportedMethodRewriter.registerAssumedLibraryTypes(options);
      if (options.enableEnumUnboxing) {
        EnumUnboxingCfMethods.registerSynthesizedCodeReferences(appView.dexItemFactory());
      }
      if (options.desugarRecordState().isNotOff()) {
        RecordInstructionDesugaring.registerSynthesizedCodeReferences(appView.dexItemFactory());
      }
      if (options.shouldDesugarVarHandle()) {
        VarHandleDesugaring.registerSynthesizedCodeReferences(appView.dexItemFactory());
      }
      CfUtilityMethodsForCodeOptimizations.registerSynthesizedCodeReferences(
          appView.dexItemFactory());

      // Upfront desugaring generation: Generates new program classes to be added in the app.
      CfClassSynthesizerDesugaringEventConsumer classSynthesizerEventConsumer =
          CfClassSynthesizerDesugaringEventConsumer.createForR8(appView);
      CfClassSynthesizerDesugaringCollection.create(appView)
          .synthesizeClasses(executorService, classSynthesizerEventConsumer, timing);
      classSynthesizerEventConsumer.finished(appView);
      if (appView.getSyntheticItems().hasPendingSyntheticClasses()) {
        appView.rebuildAppInfo();
      }
      timing.end();
      timing.begin("Strip unused code");
      timing.begin("Before enqueuer");
      List<ProguardConfigurationRule> synthesizedProguardRules;
      boolean enableListIterationRewriter;
      try {
        synthesizedProguardRules = ProguardConfigurationUtils.synthesizeRules(appView);
        ProfileCollectionAdditions profileCollectionAdditions =
            ProfileCollectionAdditions.create(appView);
        AssumeInfoCollection.Builder assumeInfoCollectionBuilder = AssumeInfoCollection.builder();
        ImmediateAppSubtypingInfo subtypingInfo = ImmediateAppSubtypingInfo.create(appView);
        appView.setRootSet(
            RootSet.builder(
                    appView,
                    profileCollectionAdditions,
                    subtypingInfo,
                    Iterables.concat(
                        options.getProguardConfiguration().getRules(), synthesizedProguardRules))
                .setAssumeInfoCollectionBuilder(assumeInfoCollectionBuilder)
                .evaluateRules(executorService)
                .tracePartialCompilationDexingOutputClasses(executorService)
                .build());
        appView.setAssumeInfoCollection(assumeInfoCollectionBuilder.build());

        // Compute the main dex rootset that will be the base of first and final main dex tracing
        // before building a new appview with only live classes (and invalidating subtypingInfo).
        if (!options.mainDexKeepRules.isEmpty()) {
          assert appView.graphLens().isIdentityLens();
          // Find classes which may have code executed before secondary dex files installation.
          MainDexRootSet mainDexRootSet =
              MainDexRootSet.builder(
                      appView, profileCollectionAdditions, subtypingInfo, options.mainDexKeepRules)
                  .evaluateRulesAndBuild(executorService);
          appView.setMainDexRootSet(mainDexRootSet);
          appView.appInfo().unsetObsolete();
        }

        AnnotationRemover.Builder annotationRemoverBuilder =
            options.isShrinking() ? AnnotationRemover.builder(Mode.INITIAL_TREE_SHAKING) : null;
        timing.end();
        timing.begin("Enqueuer");
        AppView<AppInfoWithLiveness> appViewWithLiveness =
            runEnqueuer(
                annotationRemoverBuilder,
                executorService,
                appView,
                profileCollectionAdditions,
                subtypingInfo,
                keepDeclarations);
        timing.end();
        timing.begin("After enqueuer");
        assert appView.rootSet().verifyKeptFieldsAreAccessedAndLive(appViewWithLiveness);
        assert appView.rootSet().verifyKeptMethodsAreTargetedAndLive(appViewWithLiveness);
        assert appView.rootSet().verifyKeptTypesAreLive(appViewWithLiveness);
        assert appView.rootSet().verifyKeptItemsAreKept(appView);
        assert ArtProfileCompletenessChecker.verify(appView);
        appView.rootSet().checkAllRulesAreUsed(options);

        if (options.apiModelingOptions().isReportUnknownApiReferencesEnabled()) {
          appView.apiLevelCompute().reportUnknownApiReferences();
        }

        if (options.proguardSeedsConsumer != null) {
          ByteArrayOutputStream bytes = new ByteArrayOutputStream();
          PrintStream out = new PrintStream(bytes);
          RootSetBuilder.writeSeeds(appView.appInfo().withLiveness(), out, type -> true);
          out.flush();
          ExceptionUtils.withConsumeResourceHandler(
              options.reporter, options.proguardSeedsConsumer, bytes.toString());
          ExceptionUtils.withFinishedResourceHandler(
              options.reporter, options.proguardSeedsConsumer);
        }

        if (options.isShrinking()) {
          // Mark dead proto extensions fields as neither being read nor written. This step must
          // run prior to the tree pruner.
          appView.withGeneratedExtensionRegistryShrinker(
              shrinker -> shrinker.run(Mode.INITIAL_TREE_SHAKING));

          // Build enclosing information and type-parameter information before pruning.
          // TODO(b/187922482): Only consider referenced classes.
          GenericSignatureContextBuilder genericContextBuilder =
              GenericSignatureContextBuilder.create(appView);

          // Compute if all signatures are valid before modifying them.
          GenericSignatureCorrectnessHelper.createForInitialCheck(appView, genericContextBuilder)
              .run(appView.appInfo().classes());

          // TODO(b/226539525): Implement enum lite proto shrinking as deferred tracing.
          PrunedItems.Builder prunedItemsBuilder = PrunedItems.builder();
          if (appView.options().protoShrinking().isEnumLiteProtoShrinkingEnabled()) {
            appView.protoShrinker().enumLiteProtoShrinker.clearDeadEnumLiteMaps(prunedItemsBuilder);
          }

          TreePruner pruner = new TreePruner(appViewWithLiveness);
          PrunedItems prunedItems = pruner.run(executorService, timing, prunedItemsBuilder);
          appViewWithLiveness
              .appInfo()
              .notifyTreePrunerFinished(Enqueuer.Mode.INITIAL_TREE_SHAKING);

          // Recompute the subtyping information.
          new AbstractMethodRemover(appViewWithLiveness).run();

          AnnotationRemover annotationRemover =
              annotationRemoverBuilder.build(appViewWithLiveness, prunedItems.getRemovedClasses());
          annotationRemover.ensureValid().run(executorService);
          new GenericSignatureRewriter(appView, genericContextBuilder)
              .run(appView.appInfo().classes(), executorService);

          assert appView.checkForTesting(() -> allReferencesAssignedApiLevel(appViewWithLiveness));
        }

        // Compute after initial round of tree shaking to not trigger on pruned classes.
        enableListIterationRewriter = ListIterationRewriter.shouldEnable(appView, subtypingInfo);

        timing.end();
      } finally {
        timing.end();
      }
      timing.begin("Run center tasks");
      assert appView.appInfo().hasLiveness();
      AppView<AppInfoWithLiveness> appViewWithLiveness = appView.withLiveness();

      options.reportLibraryAndProgramDuplicates(appViewWithLiveness);

      new CfOpenClosedInterfacesAnalysis(appViewWithLiveness).run(executorService);

      // TODO(b/225838009): Move higher up.
      LirConverter.enterLirSupportedPhase(appViewWithLiveness, executorService);

      assert verifyNoJarApplicationReaders(appView.appInfo().classes());
      assert appView.checkForTesting(() -> allReferencesAssignedApiLevel(appViewWithLiveness));
      // Build conservative main dex content after first round of tree shaking. This is used
      // by certain optimizations to avoid introducing additional class references into main dex
      // classes, as that can cause the final number of main dex methods to grow.
      // TODO(b/190941270): See if we can move this up before treepruning.
      performInitialMainDexTracing(appView, executorService);

      // The class type lattice elements include information about the interfaces that a class
      // implements. This information can change as a result of vertical class merging, so we need
      // to clear the cache, so that we will recompute the type lattice elements.
      appView.dexItemFactory().clearTypeElementsCache();

      // This pass attempts to reduce the number of nests and nest size to allow further passes, and
      // should therefore be run after the publicizer.
      new NestReducer(appViewWithLiveness).run(executorService, timing);

      appView.setGraphLens(
          MemberRebindingIdentityLensFactory.create(appViewWithLiveness, executorService));

      if (options.getTestingOptions().enableMemberRebindingAnalysis) {
        new MemberRebindingAnalysis(appViewWithLiveness).run(executorService);
      } else {
        appViewWithLiveness.appInfo().getFieldAccessInfoCollection().flattenAccessContexts();
      }
      appViewWithLiveness.appInfo().notifyMemberRebindingFinished(appViewWithLiveness);

      assert ArtProfileCompletenessChecker.verify(appView);

      AccessModifier.run(appViewWithLiveness, executorService, timing);

      new RedundantBridgeRemover(appViewWithLiveness)
          .setMustRetargetInvokesToTargetMethod()
          .run(executorService, timing);

      BridgeHoistingToSharedSyntheticSuperClass.run(appViewWithLiveness, executorService, timing);

      assert ArtProfileCompletenessChecker.verify(appView);

      LirConverter.rewriteLirWithLens(appView, timing, executorService);

      VerticalClassMerger.createForInitialClassMerging(appViewWithLiveness, timing)
          .runIfNecessary(executorService, timing);

      // Clear traced methods roots to not hold on to the main dex live method set.
      appView.appInfo().getMainDexInfo().clearTracedMethodRoots();

      // None of the optimizations above should lead to the creation of type lattice elements.
      assert appView.dexItemFactory().verifyNoCachedTypeElements();
      assert appView.checkForTesting(() -> allReferencesAssignedApiLevel(appViewWithLiveness));

      // Collect switch maps and ordinals maps.
      if (options.enableEnumSwitchMapRemoval) {
        appViewWithLiveness.setAppInfo(new SwitchMapCollector(appViewWithLiveness).run());
      }

      // Collect the already pruned types before creating a new app info without liveness.
      // TODO: we should avoid removing liveness.
      Set<DexType> prunedTypes = appView.withLiveness().appInfo().getPrunedTypes();
      Set<DexType> prunedClasspathTypes =
          appView.withLiveness().appInfo().getClasspathTypesIncludingPruned();

      assert ArtProfileCompletenessChecker.verify(appView);

      new PrimaryR8IRConverter(appViewWithLiveness, timing, enableListIterationRewriter)
          .optimize(appViewWithLiveness, executorService);
      assert LirConverter.verifyLirOnly(appView);

      assert ArtProfileCompletenessChecker.verify(
          appView, ALLOW_MISSING_ENUM_UNBOXING_UTILITY_METHODS);

      // Clear the reference type lattice element cache to reduce memory pressure.
      appView.dexItemFactory().clearTypeElementsCache();

      // At this point all code has been mapped according to the graph lens. We cannot remove the
      // graph lens entirely, though, since it is needed for mapping all field and method signatures
      // back to the original program.
      timing.time("AppliedGraphLens construction", appView::flattenGraphLenses);
      timing.end();

      RuntimeTypeCheckInfo.Builder finalRuntimeTypeCheckInfoBuilder = null;
      if (options.shouldRerunEnqueuer()) {
        timing.begin("Post optimization code stripping");
        try {
          GraphConsumer keptGraphConsumer = null;
          WhyAreYouKeepingConsumer whyAreYouKeepingConsumer = null;
          if (options.isShrinking()) {
            keptGraphConsumer = options.keptGraphConsumer;
            if (!appView.rootSet().reasonAsked.isEmpty()) {
              whyAreYouKeepingConsumer = new WhyAreYouKeepingConsumer(keptGraphConsumer);
              keptGraphConsumer = whyAreYouKeepingConsumer;
            }
          }

          if (options.isClassMergingExtensionRequired()) {
            finalRuntimeTypeCheckInfoBuilder = new RuntimeTypeCheckInfo.Builder(appView);
          }
          Enqueuer enqueuer =
              EnqueuerFactory.createForFinalTreeShaking(
                  appView,
                  executorService,
                  ImmediateAppSubtypingInfo.create(appView),
                  keptGraphConsumer,
                  prunedTypes,
                  prunedClasspathTypes,
                  finalRuntimeTypeCheckInfoBuilder);
          EnqueuerResult enqueuerResult =
              enqueuer.traceApplication(appView.rootSet(), executorService, timing);
          appView.setAppInfo(enqueuerResult.getAppInfo());
          appView.dissallowFurtherInitClassUses();

          // Rerunning the enqueuer should not give rise to any method rewritings.
          MutableMethodConversionOptions conversionOptions =
              MethodConversionOptions.forLirPhase(appView);
          appView.withGeneratedMessageLiteBuilderShrinker(
              shrinker ->
                  shrinker.rewriteDeadBuilderReferencesFromDynamicMethods(
                      conversionOptions, appViewWithLiveness, executorService, timing));

          if (options.isShrinking()) {
            // Mark dead proto extensions fields as neither being read nor written. This step must
            // run prior to the tree pruner.
            TreePrunerConfiguration treePrunerConfiguration =
                appView.withGeneratedExtensionRegistryShrinker(
                    shrinker -> shrinker.run(enqueuer.getMode()),
                    DefaultTreePrunerConfiguration.getInstance());

            GenericSignatureContextBuilder genericContextBuilder =
                GenericSignatureContextBuilder.create(appView);

            TreePruner pruner = new TreePruner(appViewWithLiveness, treePrunerConfiguration);
            PrunedItems prunedItems =
                pruner.run(
                    executorService, timing, PrunedItems.builder().addRemovedClasses(prunedTypes));
            appViewWithLiveness
                .appInfo()
                .notifyTreePrunerFinished(Enqueuer.Mode.FINAL_TREE_SHAKING);

            if (options.usageInformationConsumer != null) {
              ExceptionUtils.withFinishedResourceHandler(
                  options.reporter, options.usageInformationConsumer);
            }

            assert Inliner.verifyAllSingleCallerMethodsHaveBeenPruned(appViewWithLiveness);
            assert Inliner.verifyAllMultiCallerInlinedMethodsHaveBeenPruned(appView);

            assert appView.allMergedClasses().verifyAllSourcesPruned(appViewWithLiveness);
            assert appView.validateUnboxedEnumsHaveBeenPruned();
            assert appView.withLiveness().appInfo().verifyNoIteratingOverPrunedClasses();

            processWhyAreYouKeepingAndCheckDiscarded(
                appView.rootSet(),
                () -> appView.appInfo().app().classesWithDeterministicOrder(),
                whyAreYouKeepingConsumer,
                appView,
                enqueuer,
                false,
                options,
                timing,
                executorService);
            appView.setRootSet(null);

            new BridgeHoisting(appViewWithLiveness).run(executorService, timing);

            // Remove annotations that refer to types that no longer exist.
            AnnotationRemover.builder(Mode.FINAL_TREE_SHAKING)
                .build(appView.withLiveness(), prunedItems.getRemovedClasses())
                .run(executorService)
                .runForExcludedClassesInR8Partial(executorService);
            new GenericSignatureRewriter(appView, genericContextBuilder)
                .run(appView.appInfo().classes(), executorService);
            assert appView.checkForTesting(
                    () ->
                        GenericSignatureCorrectnessHelper.createForVerification(
                                appView, GenericSignatureContextBuilder.create(appView))
                            .run(appView.appInfo().classes())
                            .isValid())
                : "Could not validate generic signatures";

            // Synthesize fields for triggering class initializers.
            new ClassInitFieldSynthesizer(appViewWithLiveness).run(executorService);

            // Finalize fields.
            FieldFinalizer.run(appViewWithLiveness, executorService, timing);
          }
        } finally {
          timing.end();
        }

        if (appView.options().protoShrinking().isProtoShrinkingEnabled()) {
          if (appView.options().protoShrinking().isEnumLiteProtoShrinkingEnabled()) {
            appView.protoShrinker().enumLiteProtoShrinker.verifyDeadEnumLiteMapsAreDead();
          }

          IRConverter converter = new IRConverter(appView);

          // If proto shrinking is enabled, we need to reprocess every dynamicMethod(). This ensures
          // that proto fields that have been removed by the second round of tree shaking are also
          // removed from the proto schemas in the bytecode.
          appView.withGeneratedMessageLiteShrinker(
              shrinker -> shrinker.postOptimizeDynamicMethods(converter, executorService, timing));

          // If proto shrinking is enabled, we need to post-process every
          // findLiteExtensionByNumber() method. This ensures that there are no references to dead
          // extensions that have been removed by the second round of tree shaking.
          appView.withGeneratedExtensionRegistryShrinker(
              shrinker ->
                  shrinker.postOptimizeGeneratedExtensionRegistry(
                      converter, executorService, timing));
        }
      }
      timing.begin("Run postlude");
      performFinalMainDexTracing(appView, executorService);

      // If there was a reference to java.lang.Record in the input, we rewrite the record values.
      DexApplicationReadFlags flags = appView.appInfo().app().getFlags();
      if (flags.hasReadRecordReferenceFromProgramClass()) {
        RecordFieldValuesRewriter recordFieldArrayRemover =
            RecordFieldValuesRewriter.create(appView);
        if (recordFieldArrayRemover != null) {
          recordFieldArrayRemover.rewriteRecordFieldValues();
        }
      }

      // Insert a member rebinding oracle in the graph to ensure that all subsequent rewritings of
      // the application has an applied oracle for looking up non-rebound references.
      MemberRebindingIdentityLens memberRebindingIdentityLens =
          MemberRebindingIdentityLensFactory.create(appView, executorService);
      appView.setGraphLens(memberRebindingIdentityLens);

      // Remove redundant bridges that have been inserted for member rebinding.
      // This can only be done if we have AppInfoWithLiveness.
      if (appView.appInfo().hasLiveness()) {
        timing.begin("Bridge remover");
        new RedundantBridgeRemover(appView.withLiveness())
            .run(executorService, timing, memberRebindingIdentityLens);
        timing.end();
      } else {
        // If we don't have AppInfoWithLiveness here, it must be because we are not shrinking. When
        // we are not shrinking, we can't move visibility bridges. In principle, though, it would be
        // possible to remove visibility bridges that have been synthesized by R8, but we currently
        // do not have this information.
        assert !options.isShrinking();
      }

      appView.setArtProfileCollection(
          appView.getArtProfileCollection().withoutMissingItems(appView));
      appView.setStartupProfile(appView.getStartupProfile().withoutMissingItems(appView));

      new NonStartupInStartupOutliner(appView).runIfNecessary(executorService, timing);

      R8LibraryDesugaring.runIfNecessary(appView, executorService, timing);

      if (appView.appInfo().hasLiveness()) {
        SyntheticFinalization.finalizeWithLiveness(appView.withLiveness(), executorService, timing);
      } else {
        SyntheticFinalization.finalizeWithClassHierarchy(appView, executorService, timing);
      }

      // Read any -applymapping input to allow for repackaging to not relocate the classes.
      timing.begin("read -applymapping file");
      appView.loadApplyMappingSeedMapper();
      timing.end();

      // Remove optimization info before remaining optimizations, since these optimization currently
      // do not rewrite the optimization info, which is OK since the optimization info should
      // already have been leveraged.
      OptimizationInfoRemover.run(appView, executorService);

      // Perform repackaging.
      if (appView.hasLiveness()) {
        if (options.isRepackagingEnabled()) {
          new Repackaging(appView.withLiveness()).run(executorService, timing);
        }
        assert Repackaging.verifyIdentityRepackaging(appView.withLiveness(), executorService);
      }

      GenericSignatureContextBuilder genericContextBuilderBeforeFinalMerging = null;

      // Include the D8 part of R8 partial for lens code rewriting
      // (synthetic finalization and repackaging).
      if (appView.options().partialSubCompilationConfiguration != null) {
        appView
            .options()
            .partialSubCompilationConfiguration
            .asR8()
            .commitDexingOutputClasses(appView);
      }

      // Rewrite LIR with lens to allow building IR from LIR in class mergers.
      LirConverter.rewriteLirWithLens(appView, timing, executorService);

      // Exclude the D8 part of R8 partial from R8 optimizations.
      if (appView.options().partialSubCompilationConfiguration != null) {
        appView
            .options()
            .partialSubCompilationConfiguration
            .asR8()
            .uncommitDexingOutputClasses(appView);
      }

      if (!appView.hasCfByteCodePassThroughMethods()) {
        assert appView.dexItemFactory().verifyNoCachedTypeElements();

        if (appView.hasLiveness()) {
          VerticalClassMerger.createForIntermediateClassMerging(appView.withLiveness(), timing)
              .runIfNecessary(executorService, timing);
          assert appView.dexItemFactory().verifyNoCachedTypeElements();
        }

        genericContextBuilderBeforeFinalMerging = GenericSignatureContextBuilder.create(appView);

        // Run horizontal class merging. This runs even if shrinking is disabled to ensure
        // synthetics are always merged.
        HorizontalClassMerger.createForFinalClassMerging(appView)
            .runIfNecessary(
                executorService,
                timing,
                finalRuntimeTypeCheckInfoBuilder != null
                    ? finalRuntimeTypeCheckInfoBuilder.build(appView.graphLens())
                    : null);
        assert appView.dexItemFactory().verifyNoCachedTypeElements();

        if (appView.hasLiveness()) {
          VerticalClassMerger.createForFinalClassMerging(appView.withLiveness(), timing)
              .runIfNecessary(executorService, timing);
          assert appView.dexItemFactory().verifyNoCachedTypeElements();

          new SingleCallerInliner(appViewWithLiveness).runIfNecessary(executorService, timing);
          new ProtoNormalizer(appViewWithLiveness).run(executorService, timing);
        }
      }

      // Perform minification.
      if (options.getProguardConfiguration().hasApplyMappingFile()) {
        timing.begin("apply-mapping");
        appView.setNamingLens(
            new ProguardMapMinifier(appView.withLiveness()).run(executorService, timing));
        timing.end();
        // Clear the applymapping data
        appView.clearApplyMappingSeedMapper();
      } else if (options.isMinifying()) {
        timing.begin("Minification");
        new Minifier(appView.withLiveness()).run(executorService, timing);
        timing.end();
      }
      appView.appInfo().notifyMinifierFinished();

      timing.begin("MinifyIdentifiers");
      new IdentifierMinifier(appView).run(executorService);
      timing.end();

      // If a method filter is present don't produce output since the application is likely partial.
      if (options.hasMethodsFilter()) {
        System.out.println("Finished compilation with method filter: ");
        options.methodsFilter.forEach(m -> System.out.println("  - " + m));
        return;
      }

      // Validity checks.
      assert getDirectApp(appView).verifyCodeObjectsOwners();
      assert appView.appInfo().classes().stream().allMatch(clazz -> clazz.isValid(options));
      assert verifyMappingToOriginalProgram(appView, inputApp, executorService);

      // Report synthetic rules (only for testing).
      // TODO(b/120959039): Move this to being reported through the graph consumer.
      if (options.syntheticProguardRulesConsumer != null) {
        options.syntheticProguardRulesConsumer.accept(synthesizedProguardRules);
      }

      PrefixRewritingNamingLens.commitPrefixRewritingNamingLens(appView);
      RecordRewritingNamingLens.commitRecordRewritingNamingLens(appView);

      new ApiReferenceStubber(appView).run(executorService);

      timing.begin("MinifyKotlinMetadata");
      new KotlinMetadataRewriter(appView).runForR8(executorService);
      timing.end();

      if (genericContextBuilderBeforeFinalMerging != null) {
        new GenericSignatureRewriter(appView, genericContextBuilderBeforeFinalMerging)
            .run(appView.appInfo().classes(), executorService);
      }

      assert appView.checkForTesting(
              () ->
                  !options.isShrinking()
                      || GenericSignatureCorrectnessHelper.createForVerification(
                              appView, GenericSignatureContextBuilder.create(appView))
                          .run(appView.appInfo().classes())
                          .isValid())
          : "Could not validate generic signatures";

      Map<String, byte[]> dexFileContent = new ConcurrentHashMap<>();
      if (options.androidResourceProvider != null
          && options.androidResourceConsumer != null
          // We trace the dex directly in the enqueuer.
          && !options.isOptimizedResourceShrinking()) {
        options.programConsumer =
            wrapConsumerStoreBytesInList(
                dexFileContent, (DexIndexedConsumer) options.programConsumer, "base");
        if (options.hasFeatureSplitConfiguration()) {
          int featureIndex = 0;
          for (FeatureSplit featureSplit :
              options.getFeatureSplitConfiguration().getFeatureSplits()) {
            featureSplit.internalSetProgramConsumer(
                wrapConsumerStoreBytesInList(
                    dexFileContent,
                    (DexIndexedConsumer) featureSplit.getProgramConsumer(),
                    "feature" + featureIndex++));
          }
        }
      }

      assert appView.verifyMovedMethodsHaveOriginalMethodPosition();
      timing.end();

      LirConverter.finalizeLirToOutputFormat(appView, timing, executorService);
      LirConverter.finalizeClasspathLirToOutputFormat(appView, timing, executorService);
      assert appView.dexItemFactory().verifyNoCachedTypeElements();

      // Generate the resulting application resources.
      writeKeepDeclarationsToConfigurationConsumer(keepDeclarations);
      writeApplication(appView, inputApp, executorService);

      if (options.androidResourceProvider != null && options.androidResourceConsumer != null) {
        shrinkResources(dexFileContent, appView);
      }
      assert appView.getDontWarnConfiguration().validate(options);

      options.printWarnings();

      if (options.isPrintTimesReportingEnabled()) {
        timing.report();
      }
    } catch (ExecutionException e) {
      throw unwrapExecutionException(e);
    } finally {
      inputApp.signalFinishedToProviders(options.reporter);
      options.signalFinishedToConsumers();
    }
  }

  private void readKeepSpecifications(
      AppView<AppInfoWithClassHierarchy> appView, List<KeepDeclaration> keepDeclarations) {
    timing.begin("Read keep specifications");
    try {
      for (KeepSpecificationSource source : appView.options().getKeepSpecifications()) {
        source.parse(keepDeclarations::add);
      }
    } catch (ResourceException e) {
      options.reporter.error(new ExceptionDiagnostic(e, e.getOrigin()));
    }
    timing.end();
  }

  private boolean verifyMappingToOriginalProgram(
      AppView<AppInfoWithClassHierarchy> appView,
      AndroidApp inputAndroidApp,
      ExecutorService executorService)
      throws IOException {
    if (options.testing.disableMappingToOriginalProgramVerification) {
      return true;
    }
    if (options.partialSubCompilationConfiguration != null) {
      // TODO(b/390587690): Find a way to re-enable this assert in R8 partial.
      return true;
    }
    DexApplication inputApp =
        new ApplicationReader(inputAndroidApp.withoutMainDexList(), options, timing)
            .readWithoutDumping(executorService);
    assert appView.graphLens().verifyMappingToOriginalProgram(appView, inputApp);
    return true;
  }

  private void writeKeepDeclarationsToConfigurationConsumer(
      List<KeepDeclaration> keepDeclarations) {
    if (options.configurationConsumer == null) {
      return;
    }
    if (keepDeclarations.isEmpty()) {
      return;
    }
    for (KeepDeclaration declaration : keepDeclarations) {
      List<String> lines = StringUtils.splitLines(declaration.toString());
      StringBuilder builder = new StringBuilder();
      builder.append("# Start of content from keep annotations\n");
      for (String line : lines) {
        builder.append("# ").append(line).append("\n");
      }
      builder.append("# End of content from keep annotations\n");
      ExceptionUtils.withConsumeResourceHandler(
          options.reporter, options.configurationConsumer, builder.toString());
    }
  }

  private static ForwardingConsumer wrapConsumerStoreBytesInList(
      Map<String, byte[]> dexFileContent,
      DexIndexedConsumer programConsumer,
      String classesPrefix) {

    return new ForwardingConsumer(programConsumer) {
      @Override
      public void accept(
          int fileIndex, ByteDataView data, Set<String> descriptors, DiagnosticsHandler handler) {
        dexFileContent.put(classesPrefix + "_classes" + fileIndex + ".dex", data.copyByteData());
        super.accept(fileIndex, data, descriptors, handler);
      }
    };
  }

  private void shrinkResources(
      Map<String, byte[]> dexFileContent, AppView<AppInfoWithClassHierarchy> appView) {
    Reporter reporter = options.reporter;
    try {
      ShrinkerResult shrinkerResult;

      if (appView.options().isOptimizedResourceShrinking()) {
        shrinkerResult = appView.getResourceShrinkerState().shrinkModel();
      } else {
        LegacyResourceShrinker.Builder resourceShrinkerBuilder = LegacyResourceShrinker.builder();
        dexFileContent.forEach(resourceShrinkerBuilder::addDexInput);
        addResourcesToBuilder(
            resourceShrinkerBuilder, reporter, options.androidResourceProvider, FeatureSplit.BASE);
        if (options.hasFeatureSplitConfiguration()) {
          for (FeatureSplit featureSplit :
              options.getFeatureSplitConfiguration().getFeatureSplits()) {
            if (featureSplit.getAndroidResourceProvider() != null) {
              addResourcesToBuilder(
                  resourceShrinkerBuilder,
                  reporter,
                  featureSplit.getAndroidResourceProvider(),
                  featureSplit);
            }
          }
        }
        if (options.androidResourceProguardMapStrings != null) {
          resourceShrinkerBuilder.setProguardMapStrings(options.androidResourceProguardMapStrings);
        }
        resourceShrinkerBuilder.setShrinkerDebugReporter(
            ResourceShrinkerUtils.shrinkerDebugReporterFromStringConsumer(
                options.resourceShrinkerConfiguration.getDebugConsumer(), reporter));
        LegacyResourceShrinker shrinker = resourceShrinkerBuilder.build();
        shrinkerResult = shrinker.run();
      }
      writeResourcesToConsumer(
          reporter,
          shrinkerResult,
          options.androidResourceProvider,
          options.androidResourceConsumer,
          FeatureSplit.BASE);
      if (options.hasFeatureSplitConfiguration()) {
        for (FeatureSplit featureSplit :
            options.getFeatureSplitConfiguration().getFeatureSplits()) {
          if (featureSplit.getAndroidResourceProvider() != null) {
            writeResourcesToConsumer(
                reporter,
                shrinkerResult,
                featureSplit.getAndroidResourceProvider(),
                featureSplit.getAndroidResourceConsumer(),
                featureSplit);
          }
        }
      }
    } catch (ParserConfigurationException | SAXException | ResourceException | IOException e) {
      reporter.error(new ExceptionDiagnostic(e));
    }
  }

  private static void writeResourcesToConsumer(
      Reporter reporter,
      ShrinkerResult shrinkerResult,
      AndroidResourceProvider androidResourceProvider,
      AndroidResourceConsumer androidResourceConsumer,
      FeatureSplit featureSplit)
      throws ResourceException {
    for (AndroidResourceInput androidResource : androidResourceProvider.getAndroidResources()) {
      switch (androidResource.getKind()) {
        case MANIFEST:
        case UNKNOWN:
          androidResourceConsumer.accept(
              new R8PassThroughAndroidResource(androidResource, reporter), reporter);
          break;
        case RESOURCE_TABLE:
          androidResourceConsumer.accept(
              new R8AndroidResourceWithData(
                  androidResource,
                  reporter,
                  shrinkerResult.getResourceTableInProtoFormat(featureSplit)),
              reporter);
          break;
        case KEEP_RULE_FILE:
          // Intentionally not written
          break;
        case RES_FOLDER_FILE:
        case XML_FILE:
          if (shrinkerResult
              .getResFolderEntriesToKeep()
              .contains(androidResource.getPath().location())) {
            androidResourceConsumer.accept(
                new R8PassThroughAndroidResource(androidResource, reporter), reporter);
          }
          break;
      }
    }
    androidResourceConsumer.finished(reporter);
  }

  private static void addResourcesToBuilder(
      LegacyResourceShrinker.Builder resourceShrinkerBuilder,
      Reporter reporter,
      AndroidResourceProvider androidResourceProvider,
      FeatureSplit featureSplit)
      throws ResourceException {
    for (AndroidResourceInput androidResource : androidResourceProvider.getAndroidResources()) {
      try {
        byte[] bytes = androidResource.getByteStream().readAllBytes();
        String path = androidResource.getPath().location();
        switch (androidResource.getKind()) {
          case MANIFEST:
            resourceShrinkerBuilder.addManifest(path, bytes);
            break;
          case RES_FOLDER_FILE:
            resourceShrinkerBuilder.addResFolderInput(path, bytes);
            break;
          case RESOURCE_TABLE:
            resourceShrinkerBuilder.addResourceTable(path, bytes, featureSplit);
            break;
          case XML_FILE:
            resourceShrinkerBuilder.addXmlInput(path, bytes);
            break;
          case KEEP_RULE_FILE:
            resourceShrinkerBuilder.addKeepRuleInput(bytes);
            break;
          case UNKNOWN:
            break;
        }
      } catch (IOException e) {
        reporter.error(new ExceptionDiagnostic(e, androidResource.getOrigin()));
      }
    }
  }

  private static boolean allReferencesAssignedApiLevel(
      AppView<? extends AppInfoWithClassHierarchy> appView) {
    if (!appView.options().apiModelingOptions().isCheckAllApiReferencesAreSet()) {
      return true;
    }
    // This will assert false if we find anything in the library which is not modeled.
    for (DexProgramClass clazz : appView.appInfo().classesWithDeterministicOrder()) {
      clazz.forEachProgramMember(
          member -> {
            assert !member.getDefinition().getApiLevel().isNotSetApiLevel()
                : "Every member should have been analyzed";
          });
    }
    return true;
  }

  private void performInitialMainDexTracing(
      AppView<AppInfoWithClassHierarchy> appView, ExecutorService executorService)
      throws ExecutionException {
    if (options.mainDexKeepRules.isEmpty()) {
      return;
    }
    assert appView.graphLens().isIdentityLens();

    // Find classes which may have code executed before secondary dex files installation by
    // computing from the initially computed main dex root set.
    MainDexInfo mainDexInfo =
        EnqueuerFactory.createForInitialMainDexTracing(
                appView, executorService, ImmediateAppSubtypingInfo.create(appView))
            .traceMainDex(executorService, timing);
    appView.setAppInfo(appView.appInfo().rebuildWithMainDexInfo(mainDexInfo));
  }

  private void performFinalMainDexTracing(
      AppView<AppInfoWithClassHierarchy> appView, ExecutorService executorService)
      throws ExecutionException {
    if (options.mainDexKeepRules.isEmpty()) {
      return;
    }
    // No need to build a new main dex root set
    assert appView.getMainDexRootSet() != null;
    GraphConsumer mainDexKeptGraphConsumer = options.mainDexKeptGraphConsumer;
    WhyAreYouKeepingConsumer whyAreYouKeepingConsumer = null;
    if (!appView.getMainDexRootSet().reasonAsked.isEmpty()) {
      whyAreYouKeepingConsumer = new WhyAreYouKeepingConsumer(mainDexKeptGraphConsumer);
      mainDexKeptGraphConsumer = whyAreYouKeepingConsumer;
    }

    Enqueuer enqueuer =
        EnqueuerFactory.createForFinalMainDexTracing(
            appView,
            executorService,
            ImmediateAppSubtypingInfo.create(appView),
            mainDexKeptGraphConsumer);
    // Find classes which may have code executed before secondary dex files installation.
    MainDexInfo mainDexInfo = enqueuer.traceMainDex(executorService, timing);
    appView.setAppInfo(appView.appInfo().rebuildWithMainDexInfo(mainDexInfo));

    processWhyAreYouKeepingAndCheckDiscarded(
        appView.getMainDexRootSet(),
        () -> {
          ArrayList<DexProgramClass> classes = new ArrayList<>();
          // TODO(b/131668850): This is not a deterministic order!
          mainDexInfo.forEach(
              type -> {
                DexClass clazz = appView.definitionFor(type);
                assert clazz.isProgramClass();
                classes.add(clazz.asProgramClass());
              });
          return classes;
        },
        whyAreYouKeepingConsumer,
        appView,
        enqueuer,
        true,
        options,
        timing,
        executorService);
  }

  private AppView<AppInfoWithLiveness> runEnqueuer(
      AnnotationRemover.Builder annotationRemoverBuilder,
      ExecutorService executorService,
      AppView<AppInfoWithClassHierarchy> appView,
      ProfileCollectionAdditions profileCollectionAdditions,
      ImmediateAppSubtypingInfo subtypingInfo,
      List<KeepDeclaration> keepDeclarations)
      throws ExecutionException {
    timing.begin("Update subtyping info");
    subtypingInfo.update(appView);
    assert subtypingInfo.verifyUpToDate(appView);
    timing.end();
    timing.begin("Set up enqueuer");
    Enqueuer enqueuer =
        EnqueuerFactory.createForInitialTreeShaking(
            appView, profileCollectionAdditions, executorService, subtypingInfo);
    enqueuer.setKeepDeclarations(keepDeclarations);
    enqueuer.setAnnotationRemoverBuilder(annotationRemoverBuilder);
    timing.end();
    timing.begin("Trace application");
    EnqueuerResult enqueuerResult =
        enqueuer.traceApplication(appView.rootSet(), executorService, timing);
    assert profileCollectionAdditions.verifyIsCommitted();
    timing.end();
    timing.begin("Finalize enqueuer result");
    AppView<AppInfoWithLiveness> appViewWithLiveness =
        appView.setAppInfo(enqueuerResult.getAppInfo());
    if (InternalOptions.assertionsEnabled()) {
      // Register the dead proto types. These are needed to verify that no new missing types are
      // reported and that no dead proto types are referenced in the generated application.
      appViewWithLiveness.withProtoShrinker(
          shrinker ->
              shrinker.setDeadProtoTypes(appViewWithLiveness.appInfo().getDeadProtoTypes()));
    }
    MutableMethodConversionOptions conversionOptions =
        MethodConversionOptions.forPreLirPhase(appView);
    appView.withGeneratedMessageLiteBuilderShrinker(
        shrinker ->
            shrinker.rewriteDeadBuilderReferencesFromDynamicMethods(
                conversionOptions, appViewWithLiveness, executorService, timing));
    timing.end();
    return appViewWithLiveness;
  }

  static void processWhyAreYouKeepingAndCheckDiscarded(
      RootSet rootSet,
      Supplier<Collection<DexProgramClass>> classes,
      WhyAreYouKeepingConsumer whyAreYouKeepingConsumer,
      AppView<? extends AppInfoWithClassHierarchy> appView,
      Enqueuer enqueuer,
      boolean forMainDex,
      InternalOptions options,
      Timing timing,
      ExecutorService executorService)
      throws ExecutionException {
    if (whyAreYouKeepingConsumer != null) {
      for (DexReference reference : rootSet.reasonAsked) {
        whyAreYouKeepingConsumer.printWhyAreYouKeeping(
            enqueuer.getGraphReporter().getGraphNode(reference), System.out);
      }
    }
    DiscardedChecker discardedChecker =
        forMainDex ? DiscardedChecker.createForMainDex(appView) : DiscardedChecker.create(appView);
    List<ProgramDefinition> failed = discardedChecker.run(classes.get(), executorService);
    if (appView.options().testing.dontReportFailingCheckDiscarded) {
      assert !failed.isEmpty();
      return;
    }
    if (failed.isEmpty()) {
      return;
    }
    // If there is no kept-graph info, re-run the enqueueing to compute it.
    if (whyAreYouKeepingConsumer == null) {
      whyAreYouKeepingConsumer = new WhyAreYouKeepingConsumer(null);
      ImmediateAppSubtypingInfo subtypingInfo = ImmediateAppSubtypingInfo.create(appView);
      if (forMainDex) {
        enqueuer =
            EnqueuerFactory.createForFinalMainDexTracing(
                appView, executorService, subtypingInfo, whyAreYouKeepingConsumer);
        enqueuer.traceMainDex(executorService, timing);
      } else {
        enqueuer =
            EnqueuerFactory.createForWhyAreYouKeeping(
                appView, executorService, subtypingInfo, whyAreYouKeepingConsumer);
        enqueuer.traceApplication(
            rootSet,
            executorService,
            timing);
      }
    }
    options.reporter.error(
        new CheckDiscardDiagnostic.Builder()
            .addFailedItems(failed, enqueuer.getGraphReporter(), whyAreYouKeepingConsumer)
            .build());
    options.reporter.failIfPendingErrors();
  }

  private static boolean verifyNoJarApplicationReaders(Collection<DexProgramClass> classes) {
    for (DexProgramClass clazz : classes) {
      for (DexEncodedMethod method : clazz.methods()) {
        if (method.getCode() != null) {
          assert method.getCode().verifyNoInputReaders();
        }
      }
    }
    return true;
  }

  private static void run(String[] args) throws CompilationFailedException {
    R8Command command = R8Command.parse(args, CommandLineOrigin.INSTANCE).build();
    if (command.isPrintHelp()) {
      SelfRetraceTest.test();
      System.out.println(R8Command.getUsageMessage());
      return;
    }
    if (command.isPrintVersion()) {
      System.out.println("R8 " + Version.getVersionString());
      return;
    }
    InternalOptions options = command.getInternalOptions();
    ExecutorService executorService = ThreadUtils.getExecutorService(options);
    try {
      ExceptionUtils.withR8CompilationHandler(
          options.reporter, () -> runInternal(command.getInputApp(), options, executorService));
    } finally {
      executorService.shutdown();
    }
  }

  /**
   * Command-line entry to R8.
   *
   * <p>See {@link R8Command#getUsageMessage()} or run {@code r8 --help} for usage information.
   */
  public static void main(String[] args) {
    if (args.length == 0) {
      throw new RuntimeException(
          StringUtils.joinLines("Invalid invocation.", R8Command.getUsageMessage()));
    }
    ExceptionUtils.withMainProgramHandler(() -> run(args));
  }

  private abstract static class R8AndroidResourceBase implements AndroidResourceOutput {

    protected final AndroidResourceInput androidResource;
    protected final Reporter reporter;

    public R8AndroidResourceBase(AndroidResourceInput androidResource, Reporter reporter) {
      this.androidResource = androidResource;
      this.reporter = reporter;
    }

    @Override
    public ResourcePath getPath() {
      return androidResource.getPath();
    }

    @Override
    public Origin getOrigin() {
      return androidResource.getOrigin();
    }
  }

  private static class R8PassThroughAndroidResource extends R8AndroidResourceBase {

    public R8PassThroughAndroidResource(AndroidResourceInput androidResource, Reporter reporter) {
      super(androidResource, reporter);
    }

    @Override
    public ByteDataView getByteDataView() {
      try {
        return ByteDataView.of(ByteStreams.toByteArray(androidResource.getByteStream()));
      } catch (IOException | ResourceException e) {
        reporter.error(new ExceptionDiagnostic(e, androidResource.getOrigin()));
      }
      return null;
    }
  }

  private static class R8AndroidResourceWithData extends R8AndroidResourceBase {

    private final byte[] data;

    public R8AndroidResourceWithData(
        AndroidResourceInput androidResource, Reporter reporter, byte[] data) {
      super(androidResource, reporter);
      this.data = data;
    }

    @Override
    public ByteDataView getByteDataView() {
      return ByteDataView.of(data);
    }
  }
}
