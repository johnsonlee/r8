// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.partial;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.diagnostic.DefinitionContext;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.Definition;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndField;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.partial.R8PartialSubCompilationConfiguration.R8PartialR8SubCompilationConfiguration;
import com.android.tools.r8.references.PackageReference;
import com.android.tools.r8.shaking.ProguardClassFilter;
import com.android.tools.r8.shaking.ProguardClassNameList;
import com.android.tools.r8.shaking.ProguardConfiguration;
import com.android.tools.r8.shaking.ProguardConfigurationParser.IdentifierPatternWithWildcards;
import com.android.tools.r8.shaking.ProguardTypeMatcher;
import com.android.tools.r8.shaking.ProguardTypeMatcher.ClassOrType;
import com.android.tools.r8.shaking.reflectiveidentification.KeepAllReflectiveIdentificationEventConsumer;
import com.android.tools.r8.shaking.reflectiveidentification.ReflectiveIdentification;
import com.android.tools.r8.tracereferences.TraceReferencesConsumer;
import com.android.tools.r8.tracereferences.UseCollector;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.NopDiagnosticsHandler;
import com.android.tools.r8.utils.timing.Timing;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Predicate;

public abstract class R8PartialUseCollector extends UseCollector {

  private final ReflectiveIdentification reflectiveIdentification;

  private final Set<DexReference> seenAllowObfuscation = ConcurrentHashMap.newKeySet();
  private final Set<DexReference> seenDisallowObfuscation = ConcurrentHashMap.newKeySet();
  private final Set<String> packagesToKeep = ConcurrentHashMap.newKeySet();

  public R8PartialUseCollector(AppView<? extends AppInfoWithClassHierarchy> appView) {
    super(
        appView,
        new MissingReferencesConsumer(),
        new NopDiagnosticsHandler(),
        getTargetPredicate(appView));
    this.reflectiveIdentification =
        new ReflectiveIdentification(
            appView, new KeepAllReflectiveIdentificationEventConsumer(this));
  }

  public static Predicate<DexType> getTargetPredicate(
      AppView<? extends AppInfoWithClassHierarchy> appView) {
    return type -> appView.definitionFor(type) != null;
  }

  public void run(ExecutorService executorService) throws ExecutionException {
    R8PartialR8SubCompilationConfiguration r8SubCompilationConfiguration =
        appView.options().partialSubCompilationConfiguration.asR8();
    traceClasses(r8SubCompilationConfiguration.getDexingOutputClasses(), executorService);
    reflectiveIdentification.processWorklist(Timing.empty());
    commitPackagesToKeep();
  }

  private void commitPackagesToKeep() {
    if (packagesToKeep.isEmpty()) {
      return;
    }
    ProguardClassNameList.Builder packageNameList = ProguardClassNameList.builder();
    for (String packageToKeep : ListUtils.sort(packagesToKeep, String::compareTo)) {
      ProguardTypeMatcher packageNameMatcher =
          ProguardTypeMatcher.create(
              IdentifierPatternWithWildcards.withoutWildcards(packageToKeep),
              ClassOrType.CLASS,
              appView.dexItemFactory());
      packageNameList.addClassName(false, packageNameMatcher);
    }
    ProguardConfiguration proguardConfiguration = appView.options().getProguardConfiguration();
    proguardConfiguration.setKeepPackageNamesPatterns(
        ProguardClassFilter.builder()
            .addPattern(packageNameList.build())
            .addPatterns(proguardConfiguration.getKeepPackageNamesPatterns().getPatterns())
            .build());
  }

  public abstract void keep(
      Definition definition, DefinitionContext referencedFrom, boolean allowObfuscation);

  @Override
  public void notifyPresentClass(DexClass clazz, DefinitionContext referencedFrom) {
    notifyPresentItem(clazz, referencedFrom);
  }

  @Override
  public void notifyPresentField(DexClassAndField field, DefinitionContext referencedFrom) {
    notifyPresentItem(field, referencedFrom);
  }

  @Override
  public void notifyPresentMethod(DexClassAndMethod method, DefinitionContext referencedFrom) {
    notifyPresentItem(method, referencedFrom);
  }

  @Override
  public void notifyPresentMethod(
      DexClassAndMethod method, DefinitionContext referencedFrom, DexMethod reference) {
    notifyPresentItem(method, referencedFrom);
  }

  @Override
  public void notifyPresentMethodOverride(
      DexClassAndMethod method, ProgramMethod override, DefinitionContext referencedFrom) {
    if (seenDisallowObfuscation.add(method.getReference())) {
      keep(method, referencedFrom, false);
    }
  }

  private void notifyPresentItem(Definition definition, DefinitionContext referencedFrom) {
    if (seenAllowObfuscation.add(definition.getReference())) {
      keep(definition, referencedFrom, true);
    }
  }

  @Override
  public void notifyPackageOf(Definition definition) {
    packagesToKeep.add(definition.getContextType().getPackageName());
  }

  @Override
  protected void notifyReflectiveIdentification(DexMethod invokedMethod, ProgramMethod method) {
    reflectiveIdentification.scanInvoke(invokedMethod, method);
  }

  private static class MissingReferencesConsumer implements TraceReferencesConsumer {

    @Override
    public void acceptType(TracedClass tracedClass, DiagnosticsHandler handler) {
      assert false;
    }

    @Override
    public void acceptField(TracedField tracedField, DiagnosticsHandler handler) {
      assert tracedField.isMissingDefinition();
    }

    @Override
    public void acceptMethod(TracedMethod tracedMethod, DiagnosticsHandler handler) {
      assert tracedMethod.isMissingDefinition();
    }

    @Override
    public void acceptPackage(PackageReference pkg, DiagnosticsHandler handler) {
      assert false;
    }
  }
}
