// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.tracereferences;


import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.diagnostic.DefinitionContext;
import com.android.tools.r8.diagnostic.internal.DefinitionContextUtils;
import com.android.tools.r8.features.ClassToFeatureSplitMap;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexAnnotation;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndField;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexTypeList;
import com.android.tools.r8.graph.DexValue;
import com.android.tools.r8.graph.DexValue.DexValueArray;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.GraphLens.FieldLookupResult;
import com.android.tools.r8.graph.GraphLens.MethodLookupResult;
import com.android.tools.r8.graph.InitClassLens;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.ResolutionResult;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.FieldReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.shaking.MainDexInfo;
import com.android.tools.r8.tracereferences.TraceReferencesConsumer.TracedReference;
import com.android.tools.r8.tracereferences.internal.TracedClassImpl;
import com.android.tools.r8.tracereferences.internal.TracedFieldImpl;
import com.android.tools.r8.tracereferences.internal.TracedMethodImpl;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Timing;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

public class Tracer {

  private final AppInfoWithClassHierarchy appInfo;
  private final DiagnosticsHandler diagnostics;
  private final GraphLens graphLens;
  private final InitClassLens initClassLens;
  private final Predicate<DexType> targetPredicate;

  Tracer(Set<String> targetDescriptors, AndroidApp inputApp, DiagnosticsHandler diagnostics)
      throws IOException {
    this(
        AppInfoWithClassHierarchy.createInitialAppInfoWithClassHierarchy(
            new ApplicationReader(inputApp, new InternalOptions(), Timing.empty())
                .read()
                .toDirect(),
            ClassToFeatureSplitMap.createEmptyClassToFeatureSplitMap(),
            MainDexInfo.none()),
        diagnostics,
        GraphLens.getIdentityLens(),
        InitClassLens.getThrowingInstance(),
        type -> targetDescriptors.contains(type.toDescriptorString()));
  }

  public Tracer(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      DiagnosticsHandler diagnostics,
      Predicate<DexType> targetPredicate) {
    this(
        appView.appInfo(),
        diagnostics,
        appView.graphLens(),
        appView.initClassLens(),
        targetPredicate);
  }

  private Tracer(
      AppInfoWithClassHierarchy appInfo,
      DiagnosticsHandler diagnostics,
      GraphLens graphLens,
      InitClassLens initClassLens,
      Predicate<DexType> targetPredicate) {
    this.appInfo = appInfo;
    this.diagnostics = diagnostics;
    this.graphLens = graphLens;
    this.initClassLens = initClassLens;
    this.targetPredicate = targetPredicate;
  }

  public void run(TraceReferencesConsumer consumer) {
    UseCollector useCollector = new UseCollector(appInfo, consumer, diagnostics, targetPredicate);
    for (DexProgramClass clazz : appInfo.classes()) {
      DefinitionContext classContext = DefinitionContextUtils.create(clazz);
      useCollector.registerSuperType(clazz, clazz.superType, classContext);
      for (DexType implementsType : clazz.getInterfaces()) {
        useCollector.registerSuperType(clazz, implementsType, classContext);
      }
      clazz.forEachProgramField(useCollector::registerField);
      clazz.forEachProgramMethod(
          method -> {
            useCollector.registerMethod(method);
            useCollector.traceCode(method, graphLens, initClassLens);
          });
    }
    consumer.finished(diagnostics);
  }

  // The graph lens is intentionally only made accessible to the MethodUseCollector, since the
  // graph lens should only be applied to the code.
  static class UseCollector {

    private final AppInfoWithClassHierarchy appInfo;
    private final DexItemFactory factory;
    private final TraceReferencesConsumer consumer;
    private final DiagnosticsHandler diagnostics;
    private final Predicate<DexType> targetPredicate;

    private final Set<ClassReference> missingClasses = new HashSet<>();
    private final Set<FieldReference> missingFields = new HashSet<>();
    private final Set<MethodReference> missingMethods = new HashSet<>();

    UseCollector(
        AppInfoWithClassHierarchy appInfo,
        TraceReferencesConsumer consumer,
        DiagnosticsHandler diagnostics,
        Predicate<DexType> targetPredicate) {
      this.appInfo = appInfo;
      this.factory = appInfo.dexItemFactory();
      this.consumer = consumer;
      this.diagnostics = diagnostics;
      this.targetPredicate = targetPredicate;
    }

    private boolean isTargetType(DexType type) {
      return targetPredicate.test(type);
    }

    private void addType(DexType type, DefinitionContext referencedFrom) {
      if (type.isArrayType()) {
        addType(type.toBaseType(factory), referencedFrom);
        return;
      }
      if (type.isPrimitiveType() || type.isVoidType()) {
        return;
      }
      assert type.isClassType();
      addClassType(type, referencedFrom);
    }

    private void addTypes(DexTypeList types, DefinitionContext referencedFrom) {
      for (DexType type : types) {
        addType(type, referencedFrom);
      }
    }

    private void addClassType(DexType type, DefinitionContext referencedFrom) {
      assert type.isClassType();
      DexClass clazz = appInfo.definitionFor(type);
      if (clazz != null) {
        addClass(clazz, referencedFrom);
      } else {
        TracedClassImpl tracedClass = new TracedClassImpl(type, referencedFrom);
        collectMissingClass(tracedClass);
        consumer.acceptType(tracedClass, diagnostics);
      }
    }

    private void addClass(DexClass clazz, DefinitionContext referencedFrom) {
      if (isTargetType(clazz.getType())) {
        TracedClassImpl tracedClass = new TracedClassImpl(clazz, referencedFrom);
        consumer.acceptType(tracedClass, diagnostics);
        if (clazz.getAccessFlags().isVisibilityDependingOnPackage()) {
          consumer.acceptPackage(
              Reference.packageFromString(clazz.getType().getPackageName()), diagnostics);
        }
      }
    }

    private void addSuperMethodFromTarget(
        DexClassAndMethod method, DefinitionContext referencedFrom) {
      assert !method.isProgramMethod();
      assert isTargetType(method.getHolderType());

      // There should be no need to register the types referenced from the method signature:
      // - The return type and the parameter types are registered when visiting the source method
      //   that overrides this target method,
      // - The holder type is registered from visiting the extends/implements clause of the sub
      //   class.

      TracedMethodImpl tracedMethod = new TracedMethodImpl(method, referencedFrom);
      if (isTargetType(method.getHolderType())) {
        consumer.acceptMethod(tracedMethod, diagnostics);
        if (method.getAccessFlags().isVisibilityDependingOnPackage()) {
          consumer.acceptPackage(
              Reference.packageFromString(method.getHolderType().getPackageName()), diagnostics);
        }
      }
    }

    private <R, T extends TracedReference<R, ?>> void collectMissing(
        T tracedReference, Set<R> missingCollection) {
      if (tracedReference.isMissingDefinition()) {
        missingCollection.add(tracedReference.getReference());
      }
    }

    private void collectMissingClass(TracedClassImpl tracedClass) {
      assert tracedClass.isMissingDefinition();
      collectMissing(tracedClass, missingClasses);
    }

    private void collectMissingField(TracedFieldImpl tracedField) {
      assert tracedField.isMissingDefinition();
      collectMissing(tracedField, missingFields);
    }

    private void collectMissingMethod(TracedMethodImpl tracedMethod) {
      assert tracedMethod.isMissingDefinition();
      collectMissing(tracedMethod, missingMethods);
    }

    private void registerField(ProgramField field) {
      DefinitionContext referencedFrom = DefinitionContextUtils.create(field);
      addType(field.getType(), referencedFrom);
    }

    private void registerMethod(ProgramMethod method) {
      DefinitionContext referencedFrom = DefinitionContextUtils.create(method);
      addTypes(method.getParameters(), referencedFrom);
      addType(method.getReturnType(), referencedFrom);
      for (DexAnnotation annotation : method.getDefinition().annotations().annotations) {
        if (annotation.getAnnotationType() == appInfo.dexItemFactory().annotationThrows) {
          DexValueArray dexValues = annotation.annotation.elements[0].value.asDexValueArray();
          for (DexValue dexValType : dexValues.getValues()) {
            addType(dexValType.asDexValueType().value, referencedFrom);
          }
        }
      }

      DexClassAndMethod superTarget =
          appInfo
              .resolveMethodOn(method.getHolder(), method.getReference())
              .lookupInvokeSpecialTarget(method.getHolder(), appInfo);
      if (superTarget != null
          && !superTarget.isProgramMethod()
          && isTargetType(superTarget.getHolderType())) {
        addSuperMethodFromTarget(superTarget, referencedFrom);
      }
    }

    private void traceCode(ProgramMethod method, GraphLens graphLens, InitClassLens initClassLens) {
      method.registerCodeReferences(new MethodUseCollector(method, graphLens, initClassLens));
    }

    private void registerSuperType(
        DexProgramClass clazz, DexType superType, DefinitionContext referencedFrom) {
      addType(superType, referencedFrom);
      // If clazz overrides any methods in superType, we should keep those as well.
      clazz.forEachMethod(
          method -> {
            DexClassAndMethod resolvedMethod =
                appInfo
                    .resolveMethodOn(superType, method.getReference(), superType != clazz.superType)
                    .getResolutionPair();
            if (resolvedMethod != null
                && !resolvedMethod.isProgramMethod()
                && isTargetType(resolvedMethod.getHolderType())) {
              addSuperMethodFromTarget(resolvedMethod, referencedFrom);
            }
          });
    }

    class MethodUseCollector extends UseRegistry {

      private final ProgramMethod context;
      private final GraphLens graphLens;
      private final InitClassLens initClassLens;
      private final DefinitionContext referencedFrom;

      public MethodUseCollector(
          ProgramMethod context, GraphLens graphLens, InitClassLens initClassLens) {
        super(appInfo.dexItemFactory());
        this.context = context;
        this.graphLens = graphLens;
        this.initClassLens = initClassLens;
        this.referencedFrom = DefinitionContextUtils.create(context);
      }

      // Method references.

      @Override
      public void registerInvokeDirect(DexMethod method) {
        MethodLookupResult lookupResult = graphLens.lookupInvokeDirect(method, context);
        assert lookupResult.getType().isDirect();
        DexMethod rewrittenMethod = lookupResult.getReference();
        DexClass holder = appInfo.definitionFor(rewrittenMethod.getHolderType());
        handleRewrittenMethodReference(
            rewrittenMethod, rewrittenMethod.lookupMemberOnClass(holder));
      }

      @Override
      public void registerInvokeInterface(DexMethod method) {
        MethodLookupResult lookupResult = graphLens.lookupInvokeInterface(method, context);
        assert lookupResult.getType().isInterface();
        handleInvokeWithDynamicDispatch(lookupResult);
      }

      @Override
      public void registerInvokeStatic(DexMethod method) {
        MethodLookupResult lookupResult = graphLens.lookupInvokeStatic(method, context);
        assert lookupResult.getType().isStatic();
        DexMethod rewrittenMethod = lookupResult.getReference();
        DexClassAndMethod resolvedMethod =
            appInfo.unsafeResolveMethodDueToDexFormat(rewrittenMethod).getResolutionPair();
        handleRewrittenMethodReference(rewrittenMethod, resolvedMethod);
      }

      @Override
      public void registerInvokeSuper(DexMethod method) {
        MethodLookupResult lookupResult = graphLens.lookupInvokeSuper(method, context);
        assert lookupResult.getType().isSuper();
        DexMethod rewrittenMethod = lookupResult.getReference();
        DexClassAndMethod superTarget = appInfo.lookupSuperTarget(rewrittenMethod, context);
        handleRewrittenMethodReference(rewrittenMethod, superTarget);
      }

      @Override
      public void registerInvokeVirtual(DexMethod method) {
        MethodLookupResult lookupResult = graphLens.lookupInvokeVirtual(method, context);
        assert lookupResult.getType().isVirtual();
        handleInvokeWithDynamicDispatch(lookupResult);
      }

      private void handleInvokeWithDynamicDispatch(MethodLookupResult lookupResult) {
        DexMethod method = lookupResult.getReference();
        if (method.getHolderType().isArrayType()) {
          assert lookupResult.getType().isVirtual();
          addType(method.getHolderType(), referencedFrom);
          return;
        }
        assert lookupResult.getType().isInterface() || lookupResult.getType().isVirtual();
        ResolutionResult resolutionResult =
            lookupResult.getType().isInterface()
                ? appInfo.resolveMethodOnInterface(method)
                : appInfo.resolveMethodOnClass(method);
        DexClassAndMethod resolvedMethod =
            resolutionResult.isVirtualTarget() ? resolutionResult.getResolutionPair() : null;
        handleRewrittenMethodReference(method, resolvedMethod);
      }

      private void handleRewrittenMethodReference(
          DexMethod method, DexClassAndMethod resolvedMethod) {
        assert resolvedMethod == null || resolvedMethod.getReference().match(method);
        addType(method.getHolderType(), referencedFrom);
        addTypes(method.getParameters(), referencedFrom);
        addType(method.getReturnType(), referencedFrom);
        if (resolvedMethod != null) {
          if (isTargetType(resolvedMethod.getHolderType())) {
            if (resolvedMethod.getHolderType() != method.getHolderType()) {
              addType(resolvedMethod.getHolderType(), referencedFrom);
            }
            TracedMethodImpl tracedMethod = new TracedMethodImpl(resolvedMethod, referencedFrom);
            consumer.acceptMethod(tracedMethod, diagnostics);
            if (resolvedMethod.getAccessFlags().isVisibilityDependingOnPackage()) {
              consumer.acceptPackage(
                  Reference.packageFromString(resolvedMethod.getHolderType().getPackageName()),
                  diagnostics);
            }
          }
        } else {
          TracedMethodImpl tracedMethod = new TracedMethodImpl(method, referencedFrom);
          collectMissingMethod(tracedMethod);
          consumer.acceptMethod(tracedMethod, diagnostics);
        }
      }

      // Field references.

      @Override
      public void registerInitClass(DexType clazz) {
        DexType rewrittenClass = graphLens.lookupType(clazz);
        DexField clinitField = initClassLens.getInitClassField(rewrittenClass);
        handleRewrittenFieldReference(clinitField);
      }

      @Override
      public void registerInstanceFieldRead(DexField field) {
        handleFieldAccess(field);
      }

      @Override
      public void registerInstanceFieldWrite(DexField field) {
        handleFieldAccess(field);
      }

      @Override
      public void registerStaticFieldRead(DexField field) {
        handleFieldAccess(field);
      }

      @Override
      public void registerStaticFieldWrite(DexField field) {
        handleFieldAccess(field);
      }

      private void handleFieldAccess(DexField field) {
        FieldLookupResult lookupResult = graphLens.lookupFieldResult(field);
        handleRewrittenFieldReference(lookupResult.getReference());
      }

      private void handleRewrittenFieldReference(DexField field) {
        addType(field.getHolderType(), referencedFrom);
        addType(field.getType(), referencedFrom);

        DexClassAndField resolvedField = appInfo.resolveField(field).getResolutionPair();
        if (resolvedField != null) {
          if (isTargetType(resolvedField.getHolderType())) {
            if (resolvedField.getHolderType() != field.getHolderType()) {
              addClass(resolvedField.getHolder(), referencedFrom);
            }
            TracedFieldImpl tracedField = new TracedFieldImpl(resolvedField, referencedFrom);
            consumer.acceptField(tracedField, diagnostics);
            if (resolvedField.getAccessFlags().isVisibilityDependingOnPackage()) {
              consumer.acceptPackage(
                  Reference.packageFromString(resolvedField.getHolderType().getPackageName()),
                  diagnostics);
            }
          }
        } else {
          TracedFieldImpl tracedField = new TracedFieldImpl(field, referencedFrom);
          collectMissingField(tracedField);
          consumer.acceptField(tracedField, diagnostics);
        }
      }

      // Type references.

      @Override
      public void registerTypeReference(DexType type) {
        addType(graphLens.lookupType(type), referencedFrom);
      }
    }
  }
}
