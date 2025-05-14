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
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMember;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexValue;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.naming.IdentifierNameStringUtils;
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
import com.android.tools.r8.tracereferences.UseCollectorEventConsumer;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.NopDiagnosticsHandler;
import com.android.tools.r8.utils.timing.Timing;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Predicate;

public abstract class R8PartialUseCollector extends UseCollector {

  private final Set<DexMember<?, ?>> identifierNameStrings;
  private final ReflectiveIdentification reflectiveIdentification;

  private final Set<DexReference> seenAllowObfuscation = ConcurrentHashMap.newKeySet();
  private final Set<DexReference> seenDisallowObfuscation = ConcurrentHashMap.newKeySet();
  private final Set<String> packagesToKeep = ConcurrentHashMap.newKeySet();

  public R8PartialUseCollector(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      Set<DexMember<?, ?>> identifierNameStrings) {
    super(
        appView,
        new MissingReferencesConsumer(),
        new NopDiagnosticsHandler(),
        getTargetPredicate(appView));
    this.identifierNameStrings = identifierNameStrings;
    this.reflectiveIdentification =
        new ReflectiveIdentification(
            appView, new KeepAllReflectiveIdentificationEventConsumer(this), identifierNameStrings);
  }

  private static Predicate<DexType> getTargetPredicate(
      AppView<? extends AppInfoWithClassHierarchy> appView) {
    return type -> appView.definitionFor(type) != null;
  }

  @Override
  protected UseCollectorEventConsumer getEventConsumerForNativeMethod() {
    return new KeepNativeMethodSignatureEventConsumer();
  }

  @Override
  protected void traceFieldValue(ProgramField field) {
    if (field.getAccessFlags().isStatic()
        && field.getDefinition().hasExplicitStaticValue()
        && identifierNameStrings.contains(field.getReference())) {
      DexValue fieldValue = field.getDefinition().getStaticValue();
      if (fieldValue.isDexValueString()) {
        Definition definition =
            IdentifierNameStringUtils.inferMemberOrTypeFromNameString(
                appView, fieldValue.asDexValueString().getValue());
        if (definition != null && isTargetType(definition.getContextType())) {
          reflectiveIdentification.enqueue(field, definition);
        }
      }
    }
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
    keepAllowObfuscation(clazz, referencedFrom);
  }

  @Override
  public void notifyPresentField(DexClassAndField field, DefinitionContext referencedFrom) {
    keepAllowObfuscation(field, referencedFrom);
  }

  @Override
  public void notifyPresentMethod(DexClassAndMethod method, DefinitionContext referencedFrom) {
    keepAllowObfuscation(method, referencedFrom);
  }

  @Override
  public void notifyPresentMethod(
      DexClassAndMethod method, DefinitionContext referencedFrom, DexMethod reference) {
    keepAllowObfuscation(method, referencedFrom);
  }

  @Override
  public void notifyPresentMethodOverride(
      DexClassAndMethod method, ProgramMethod override, DefinitionContext referencedFrom) {
    keepDisallowObfuscation(method, referencedFrom);
  }

  private void keepAllowObfuscation(Definition definition, DefinitionContext referencedFrom) {
    if (seenAllowObfuscation.add(definition.getReference())) {
      keep(definition, referencedFrom, true);
    }
  }

  private void keepDisallowObfuscation(Definition definition, DefinitionContext referencedFrom) {
    if (seenDisallowObfuscation.add(definition.getReference())) {
      keep(definition, referencedFrom, false);
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

  private class KeepNativeMethodSignatureEventConsumer implements UseCollectorEventConsumer {

    @Override
    public void notifyPresentClass(DexClass clazz, DefinitionContext referencedFrom) {
      keepDisallowObfuscation(clazz, referencedFrom);
    }

    @Override
    public void notifyMissingClass(DexType type, DefinitionContext referencedFrom) {
      R8PartialUseCollector.this.notifyMissingClass(type, referencedFrom);
    }

    @Override
    public void notifyPackageOf(Definition definition) {
      R8PartialUseCollector.this.notifyPackageOf(definition);
    }

    @Override
    public void notifyPresentField(DexClassAndField field, DefinitionContext referencedFrom) {
      assert false;
    }

    @Override
    public void notifyMissingField(DexField field, DefinitionContext referencedFrom) {
      assert false;
    }

    @Override
    public void notifyPresentMethod(DexClassAndMethod method, DefinitionContext referencedFrom) {
      assert false;
    }

    @Override
    public void notifyPresentMethod(
        DexClassAndMethod method, DefinitionContext referencedFrom, DexMethod reference) {
      assert false;
    }

    @Override
    public void notifyPresentMethodOverride(
        DexClassAndMethod method, ProgramMethod override, DefinitionContext referencedFrom) {
      assert false;
    }

    @Override
    public void notifyMissingMethod(DexMethod method, DefinitionContext referencedFrom) {
      assert false;
    }
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
