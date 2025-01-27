// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.partial;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.diagnostic.DefinitionContext;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.Definition;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndField;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.partial.R8PartialSubCompilationConfiguration.R8PartialR8SubCompilationConfiguration;
import com.android.tools.r8.references.PackageReference;
import com.android.tools.r8.shaking.ProguardClassFilter;
import com.android.tools.r8.shaking.ProguardClassNameList;
import com.android.tools.r8.shaking.ProguardConfiguration;
import com.android.tools.r8.shaking.ProguardConfigurationParser.IdentifierPatternWithWildcards;
import com.android.tools.r8.shaking.ProguardTypeMatcher;
import com.android.tools.r8.shaking.ProguardTypeMatcher.ClassOrType;
import com.android.tools.r8.tracereferences.TraceReferencesConsumer;
import com.android.tools.r8.tracereferences.UseCollector;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.NopDiagnosticsHandler;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Predicate;

public abstract class R8PartialUseCollector extends UseCollector {

  private final Set<DexReference> seen = ConcurrentHashMap.newKeySet();
  private final Set<String> packagesToKeep = ConcurrentHashMap.newKeySet();

  public R8PartialUseCollector(AppView<? extends AppInfoWithClassHierarchy> appView) {
    super(
        appView,
        new MissingReferencesConsumer(),
        new NopDiagnosticsHandler(),
        getTargetPredicate(appView));
  }

  public static Predicate<DexType> getTargetPredicate(
      AppView<? extends AppInfoWithClassHierarchy> appView) {
    return type -> asProgramClassOrNull(appView.definitionFor(type)) != null;
  }

  public void run(ExecutorService executorService) throws ExecutionException {
    R8PartialR8SubCompilationConfiguration r8SubCompilationConfiguration =
        appView.options().partialSubCompilationConfiguration.asR8();
    traceClasses(r8SubCompilationConfiguration.getDexingOutputClasses(), executorService);
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

  protected abstract void keep(Definition definition);

  @Override
  protected void notifyPresentClass(DexClass clazz, DefinitionContext referencedFrom) {
    notifyPresentItem(clazz);
  }

  @Override
  protected void notifyPresentField(DexClassAndField field, DefinitionContext referencedFrom) {
    notifyPresentItem(field);
  }

  @Override
  protected void notifyPresentMethod(DexClassAndMethod method, DefinitionContext referencedFrom) {
    notifyPresentItem(method);
  }

  private void notifyPresentItem(Definition definition) {
    if (seen.add(definition.getReference())) {
      keep(definition);
    }
  }

  @Override
  protected void notifyPackageOf(Definition definition) {
    packagesToKeep.add(definition.getContextType().getPackageName());
  }

  private static class MissingReferencesConsumer implements TraceReferencesConsumer {

    @Override
    public void acceptType(TracedClass tracedClass, DiagnosticsHandler handler) {
      assert tracedClass.isMissingDefinition();
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
