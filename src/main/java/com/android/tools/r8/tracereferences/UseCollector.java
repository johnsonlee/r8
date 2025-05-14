// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.tracereferences;

import static com.android.tools.r8.utils.ConsumerUtils.emptyConsumer;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.diagnostic.DefinitionContext;
import com.android.tools.r8.diagnostic.internal.DefinitionContextUtils;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ClassResolutionResult;
import com.android.tools.r8.graph.Definition;
import com.android.tools.r8.graph.DexAnnotation;
import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndField;
import com.android.tools.r8.graph.DexClassAndMember;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexEncodedAnnotation;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMember;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexTypeList;
import com.android.tools.r8.graph.DexValue;
import com.android.tools.r8.graph.FieldResolutionResult;
import com.android.tools.r8.graph.InnerClassAttribute;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.graph.MethodResolutionResult.SingleResolutionResult;
import com.android.tools.r8.graph.PermittedSubclassAttribute;
import com.android.tools.r8.graph.ProgramDefinition;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.ir.code.InvokeType;
import com.android.tools.r8.ir.desugar.LambdaDescriptor;
import com.android.tools.r8.kotlin.KotlinClassLevelInfo;
import com.android.tools.r8.kotlin.KotlinClassMetadataReader;
import com.android.tools.r8.kotlin.KotlinMetadataUseRegistry;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.FieldReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.tracereferences.TraceReferencesConsumer.TracedReference;
import com.android.tools.r8.tracereferences.internal.TracedClassImpl;
import com.android.tools.r8.tracereferences.internal.TracedFieldImpl;
import com.android.tools.r8.tracereferences.internal.TracedMethodImpl;
import com.android.tools.r8.utils.BooleanBox;
import com.android.tools.r8.utils.ThreadUtils;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

// The graph lens is intentionally only made accessible to the MethodUseCollector, since the
// graph lens should only be applied to the code.
public class UseCollector implements UseCollectorEventConsumer {

  protected final AppView<? extends AppInfoWithClassHierarchy> appView;
  private final DexItemFactory factory;
  private final TraceReferencesConsumer consumer;
  private final DiagnosticsHandler diagnostics;
  private final UseCollectorEventConsumer kotlinMetadataEventConsumer;
  private final Predicate<DexType> targetPredicate;
  private final TraceReferencesOptions traceReferencesOptions;

  private final Set<ClassReference> missingClasses = ConcurrentHashMap.newKeySet();
  private final Set<FieldReference> missingFields = ConcurrentHashMap.newKeySet();
  private final Set<MethodReference> missingMethods = ConcurrentHashMap.newKeySet();

  public final DexString dalvikAnnotationCodegenPrefix;

  public UseCollector(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      TraceReferencesConsumer consumer,
      DiagnosticsHandler diagnostics,
      Predicate<DexType> targetPredicate) {
    this.appView = appView;
    this.factory = appView.dexItemFactory();
    this.consumer = consumer;
    this.diagnostics = diagnostics;
    this.kotlinMetadataEventConsumer = new KotlinMetadataUseCollectorEventConsumer(this);
    this.targetPredicate = targetPredicate;
    this.traceReferencesOptions = appView.options().getTraceReferencesOptions();
    this.dalvikAnnotationCodegenPrefix = factory.createString("Ldalvik/annotation/codegen/");
  }

  private UseCollectorEventConsumer getDefaultEventConsumer() {
    return this;
  }

  protected UseCollectorEventConsumer getEventConsumerForNativeMethod() {
    return getDefaultEventConsumer();
  }

  protected void notifyReflectiveIdentification(DexMethod invokedMethod, ProgramMethod method) {
    // Intentionally empty. Overridden in R8PartialUseCollector.
  }

  public void traceClasses(Collection<DexProgramClass> classes) {
    for (DexProgramClass clazz : classes) {
      traceClass(clazz);
    }
  }

  public void traceClasses(Collection<DexProgramClass> classes, ExecutorService executorService)
      throws ExecutionException {
    ThreadUtils.processItems(
        classes, this::traceClass, appView.options().getThreadingModule(), executorService);
  }

  public void traceClass(DexProgramClass clazz) {
    DefinitionContext classContext = DefinitionContextUtils.create(clazz);
    clazz.forEachImmediateSupertype(
        supertype -> registerSuperType(clazz, supertype, classContext, getDefaultEventConsumer()));
    clazz.forEachProgramField(field -> registerField(field, getDefaultEventConsumer()));
    clazz.forEachProgramMethod(method -> registerMethod(method, getDefaultEventConsumer()));
    for (DexAnnotation annotation : clazz.annotations().getAnnotations()) {
      registerAnnotation(annotation, clazz, classContext, getDefaultEventConsumer());
    }
    traceEnclosingMethod(clazz, classContext, getDefaultEventConsumer());
    traceInnerClasses(clazz, classContext, getDefaultEventConsumer());
    traceKotlinMetadata(clazz, classContext, kotlinMetadataEventConsumer);
    traceNest(clazz, classContext, getDefaultEventConsumer());
    tracePermittedSubclasses(clazz, classContext, getDefaultEventConsumer());
    traceSignature(clazz, classContext, getDefaultEventConsumer());
  }

  private void traceEnclosingMethod(
      DexProgramClass clazz,
      DefinitionContext classContext,
      UseCollectorEventConsumer eventConsumer) {
    if (clazz.hasEnclosingMethodAttribute()) {
      if (clazz.getEnclosingMethodAttribute().hasEnclosingMethod()) {
        DexMethod enclosingMethod = clazz.getEnclosingMethodAttribute().getEnclosingMethod();
        handleMethodResolution(
            enclosingMethod,
            appInfo().unsafeResolveMethodDueToDexFormat(enclosingMethod),
            SingleResolutionResult::getResolutionPair,
            clazz,
            classContext,
            eventConsumer);
      } else {
        addType(
            clazz.getEnclosingMethodAttribute().getEnclosingClass(), classContext, eventConsumer);
      }
    }
  }

  private void traceInnerClasses(
      DexProgramClass clazz,
      DefinitionContext classContext,
      UseCollectorEventConsumer eventConsumer) {
    if (traceReferencesOptions.skipInnerClassesForTesting) {
      return;
    }
    for (InnerClassAttribute innerClassAttribute : clazz.getInnerClasses()) {
      if (innerClassAttribute.getInner() != null) {
        addType(innerClassAttribute.getInner(), classContext, eventConsumer);
      }
      if (innerClassAttribute.getOuter() != null) {
        addType(innerClassAttribute.getOuter(), classContext, eventConsumer);
      }
    }
  }

  private void traceKotlinMetadata(
      DexProgramClass clazz,
      DefinitionContext classContext,
      UseCollectorEventConsumer eventConsumer) {
    if (parseKotlinMetadata(clazz)) {
      KotlinMetadataUseRegistry registry = type -> addType(type, classContext, eventConsumer);
      clazz.getKotlinInfo().trace(registry);
      clazz.forEachProgramMember(member -> member.getDefinition().getKotlinInfo().trace(registry));
    }
  }

  private boolean parseKotlinMetadata(DexProgramClass clazz) {
    DexAnnotation metadata = clazz.annotations().getFirstMatching(factory.kotlinMetadataType);
    if (metadata == null) {
      return false;
    }
    BooleanSupplier reportUnknownMetadata = () -> false;
    KotlinClassLevelInfo kotlinInfo =
        KotlinClassMetadataReader.getKotlinInfoFromAnnotation(
            appView, clazz, metadata, emptyConsumer(), reportUnknownMetadata);
    clazz.setKotlinInfo(kotlinInfo);
    return true;
  }

  private void traceNest(
      DexProgramClass clazz,
      DefinitionContext classContext,
      UseCollectorEventConsumer eventConsumer) {
    if (clazz.isNestMember()) {
      addType(clazz.getNestHost(), classContext, eventConsumer);
    }
    clazz.forEachNestMemberOnHost(
        appView, memberType -> addType(memberType, classContext, eventConsumer));
  }

  private void tracePermittedSubclasses(
      DexProgramClass clazz,
      DefinitionContext classContext,
      UseCollectorEventConsumer eventConsumer) {
    for (PermittedSubclassAttribute attribute : clazz.getPermittedSubclassAttributes()) {
      addType(attribute.getPermittedSubclass(), classContext, eventConsumer);
    }
  }

  private void traceSignature(
      DexProgramClass clazz,
      DefinitionContext classContext,
      UseCollectorEventConsumer eventConsumer) {
    clazz.getClassSignature().registerUses(type -> addType(type, classContext, eventConsumer));
  }

  private void traceSignature(
      ProgramField field, DefinitionContext fieldContext, UseCollectorEventConsumer eventConsumer) {
    field
        .getDefinition()
        .getGenericSignature()
        .registerUses(type -> addType(type, fieldContext, eventConsumer));
  }

  private void traceSignature(
      ProgramMethod method,
      DefinitionContext methodContext,
      UseCollectorEventConsumer eventConsumer) {
    method
        .getDefinition()
        .getGenericSignature()
        .registerUses(type -> addType(type, methodContext, eventConsumer));
  }

  @Override
  public void notifyPresentClass(DexClass clazz, DefinitionContext referencedFrom) {
    TracedClassImpl tracedClass = new TracedClassImpl(clazz, referencedFrom);
    consumer.acceptType(tracedClass, diagnostics);
  }

  @Override
  public void notifyMissingClass(DexType type, DefinitionContext referencedFrom) {
    TracedClassImpl missingClass = new TracedClassImpl(type, referencedFrom);
    collectMissingClass(missingClass);
    consumer.acceptType(missingClass, diagnostics);
  }

  @Override
  public void notifyPresentField(DexClassAndField field, DefinitionContext referencedFrom) {
    TracedFieldImpl tracedField = new TracedFieldImpl(field, referencedFrom);
    consumer.acceptField(tracedField, diagnostics);
  }

  @Override
  public void notifyMissingField(DexField field, DefinitionContext referencedFrom) {
    TracedFieldImpl missingField = new TracedFieldImpl(field, referencedFrom);
    collectMissingField(missingField);
    consumer.acceptField(missingField, diagnostics);
  }

  @Override
  public void notifyPresentMethod(DexClassAndMethod method, DefinitionContext referencedFrom) {
    TracedMethodImpl tracedMethod = new TracedMethodImpl(method, referencedFrom);
    consumer.acceptMethod(tracedMethod, diagnostics);
  }

  @Override
  public void notifyPresentMethod(
      DexClassAndMethod method, DefinitionContext referencedFrom, DexMethod reference) {
    TracedMethodImpl tracedMethod = new TracedMethodImpl(method, referencedFrom, reference);
    consumer.acceptMethod(tracedMethod, diagnostics);
  }

  @Override
  public void notifyPresentMethodOverride(
      DexClassAndMethod method, ProgramMethod override, DefinitionContext referencedFrom) {
    // Intentionally empty.
  }

  @Override
  public void notifyMissingMethod(DexMethod method, DefinitionContext referencedFrom) {
    TracedMethodImpl missingMethod = new TracedMethodImpl(method, referencedFrom);
    collectMissingMethod(missingMethod);
    consumer.acceptMethod(missingMethod, diagnostics);
  }

  @Override
  public void notifyPackageOf(Definition definition) {
    consumer.acceptPackage(
        Reference.packageFromString(definition.getContextType().getPackageName()), diagnostics);
  }

  AppView<? extends AppInfoWithClassHierarchy> appView() {
    return appView;
  }

  AppInfoWithClassHierarchy appInfo() {
    return appView.appInfo();
  }

  protected final boolean isTargetType(DexType type) {
    return targetPredicate.test(type);
  }

  private void addType(
      DexType type, DefinitionContext referencedFrom, UseCollectorEventConsumer eventConsumer) {
    if (type.isArrayType()) {
      addType(type.toBaseType(factory), referencedFrom, eventConsumer);
      return;
    }
    if (type.isPrimitiveType() || type.isVoidType()) {
      return;
    }
    assert type.isClassType();
    addClassType(type, referencedFrom, eventConsumer);
  }

  private void addTypes(
      DexTypeList types,
      DefinitionContext referencedFrom,
      UseCollectorEventConsumer eventConsumer) {
    for (DexType type : types) {
      addType(type, referencedFrom, eventConsumer);
    }
  }

  private void addClassType(
      DexType type,
      DefinitionContext referencedFrom,
      UseCollectorEventConsumer eventConsumer,
      Consumer<DexClass> resolvedClassesConsumer) {
    assert type.isClassType();
    ClassResolutionResult result =
        appView.contextIndependentDefinitionForWithResolutionResult(type);
    if (result.hasClassResolutionResult()) {
      result.forEachClassResolutionResult(resolvedClassesConsumer);
    } else {
      eventConsumer.notifyMissingClass(type, referencedFrom);
    }
  }

  private void addClassType(
      DexType type, DefinitionContext referencedFrom, UseCollectorEventConsumer eventConsumer) {
    addClassType(
        type,
        referencedFrom,
        eventConsumer,
        clazz -> addClass(clazz, referencedFrom, eventConsumer));
  }

  private void addClass(
      DexClass clazz, DefinitionContext referencedFrom, UseCollectorEventConsumer eventConsumer) {
    if (isTargetType(clazz.getType())) {
      eventConsumer.notifyPresentClass(clazz, referencedFrom);
      if (clazz.getAccessFlags().isPackagePrivateOrProtected()) {
        eventConsumer.notifyPackageOf(clazz);
      }
    }
  }

  private void handleMemberResolution(
      DexMember<?, ?> reference,
      DexClassAndMember<?, ?> member,
      DexProgramClass context,
      DefinitionContext referencedFrom,
      UseCollectorEventConsumer eventConsumer) {
    DexClass holder = member.getHolder();
    assert isTargetType(holder.getType());
    if (member.getHolderType().isNotIdenticalTo(reference.getHolderType())) {
      eventConsumer.notifyPresentClass(holder, referencedFrom);
    }
    ensurePackageAccessToMember(member, context, eventConsumer);
  }

  private void ensurePackageAccessToMember(
      DexClassAndMember<?, ?> member,
      DexProgramClass context,
      UseCollectorEventConsumer eventConsumer) {
    if (member.getAccessFlags().isPackagePrivateOrProtected()) {
      if (member.getAccessFlags().isPackagePrivate()
          || !appInfo().isSubtype(context, member.getHolder())) {
        eventConsumer.notifyPackageOf(member);
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

  private void registerField(ProgramField field, UseCollectorEventConsumer eventConsumer) {
    DefinitionContext referencedFrom = DefinitionContextUtils.create(field);
    addType(field.getType(), referencedFrom, eventConsumer);
    field
        .getAnnotations()
        .forEach(
            dexAnnotation ->
                registerAnnotation(
                    dexAnnotation, field.getHolder(), referencedFrom, eventConsumer));
    traceFieldValue(field);
    traceSignature(field, referencedFrom, eventConsumer);
  }

  protected void traceFieldValue(ProgramField field) {
    // Intentionally empty. Overridden in R8PartialUseCollector.
  }

  private void registerMethod(ProgramMethod method, UseCollectorEventConsumer eventConsumer) {
    DefinitionContext referencedFrom = DefinitionContextUtils.create(method);
    UseCollectorEventConsumer signatureEventConsumer =
        method.getAccessFlags().isNative() ? getEventConsumerForNativeMethod() : eventConsumer;
    addTypes(method.getParameters(), referencedFrom, signatureEventConsumer);
    addType(method.getReturnType(), referencedFrom, signatureEventConsumer);
    method
        .getAnnotations()
        .forEach(
            dexAnnotation ->
                registerAnnotation(
                    dexAnnotation, method.getHolder(), referencedFrom, eventConsumer));
    method
        .getParameterAnnotations()
        .forEachAnnotation(
            dexAnnotation ->
                registerAnnotation(
                    dexAnnotation, method.getHolder(), referencedFrom, eventConsumer));
    traceCode(method, referencedFrom, eventConsumer);
    traceSignature(method, referencedFrom, eventConsumer);
  }

  private void traceCode(
      ProgramMethod method,
      DefinitionContext referencedFrom,
      UseCollectorEventConsumer eventConsumer) {
    method.registerCodeReferences(new MethodUseCollector(method, referencedFrom, eventConsumer));
  }

  private void registerSuperType(
      DexProgramClass clazz,
      DexType superType,
      DefinitionContext referencedFrom,
      UseCollectorEventConsumer eventConsumer) {
    addType(superType, referencedFrom, eventConsumer);
    // If clazz overrides any methods in superType, we should keep those as well.
    clazz.forEachProgramVirtualMethod(
        method -> {
          DexClassAndMethod resolvedMethod =
              appInfo()
                  .resolveMethodOn(
                      superType,
                      method.getReference(),
                      superType.isNotIdenticalTo(clazz.getSuperType()))
                  .getResolutionPair();
          if (resolvedMethod != null && isTargetType(resolvedMethod.getHolderType())) {
            // There should be no need to register the types referenced from the method signature:
            // - The return type and the parameter types are registered when visiting the source
            //   method that overrides this target method,
            // - The holder type is registered from visiting the extends/implements clause of the
            //   sub class.
            eventConsumer.notifyPresentMethodOverride(resolvedMethod, method, referencedFrom);
            if (resolvedMethod.getHolderType().isIdenticalTo(superType)) {
              eventConsumer.notifyPresentMethod(resolvedMethod, referencedFrom);
            } else if (isTargetType(superType)) {
              eventConsumer.notifyPresentMethod(
                  resolvedMethod,
                  referencedFrom,
                  resolvedMethod.getReference().withHolder(superType, factory));
            } else {
              eventConsumer.notifyPresentMethod(resolvedMethod, referencedFrom);
              addClass(resolvedMethod.getHolder(), referencedFrom, eventConsumer);
            }
            ensurePackageAccessToMember(resolvedMethod, method.getHolder(), eventConsumer);
          }
        });
  }

  private void registerAnnotation(
      DexAnnotation annotation,
      DexProgramClass context,
      DefinitionContext referencedFrom,
      UseCollectorEventConsumer eventConsumer) {
    DexType type = annotation.getAnnotationType();
    assert type.isClassType();
    if (type.isIdenticalTo(factory.annotationMethodParameters)
        || type.isIdenticalTo(factory.annotationReachabilitySensitive)
        || type.getDescriptor().startsWith(factory.dalvikAnnotationOptimizationPrefix)
        || type.getDescriptor().startsWith(dalvikAnnotationCodegenPrefix)) {
      // The remaining system annotations
      //   dalvik.annotation.EnclosingClass
      //   dalvik.annotation.EnclosingMethod
      //   dalvik.annotation.InnerClass
      //   dalvik.annotation.MemberClasses
      //   dalvik.annotation.Signature
      //   dalvik.annotation.NestHost (*)
      //   dalvik.annotation.NestMembers (*)
      //   dalvik.annotation.Record (*)
      //   dalvik.annotation.PermittedSubclasses (*)
      // are not added as annotations in the DexParser.
      //
      // (*) Not officially supported and documented.
      return;
    }
    if (type.isIdenticalTo(factory.annotationDefault)) {
      assert referencedFrom.isClassContext();
      annotation
          .getAnnotation()
          .forEachElement(
              element -> {
                assert element.getValue().isDexValueAnnotation();
                registerEncodedAnnotation(
                    element.getValue().asDexValueAnnotation().getValue(),
                    context,
                    referencedFrom,
                    eventConsumer);
              });
      return;
    }
    if (type.isIdenticalTo(factory.annotationSourceDebugExtension)) {
      assert annotation.getAnnotation().getNumberOfElements() == 1;
      assert annotation.getAnnotation().getElement(0).getValue().isDexValueString();
      return;
    }
    if (type.isIdenticalTo(factory.annotationThrows)) {
      assert referencedFrom.isMethodContext();
      registerDexValue(
          annotation.annotation.elements[0].value.asDexValueArray(),
          context,
          referencedFrom,
          eventConsumer);
      return;
    }
    assert !type.getDescriptor().startsWith(factory.dalvikAnnotationPrefix)
        : "Unexpected annotation with prefix "
            + factory.dalvikAnnotationPrefix
            + ": "
            + type.getDescriptor();
    registerEncodedAnnotation(annotation.getAnnotation(), context, referencedFrom, eventConsumer);
  }

  void registerEncodedAnnotation(
      DexEncodedAnnotation annotation,
      DexProgramClass context,
      DefinitionContext referencedFrom,
      UseCollectorEventConsumer eventConsumer) {
    addClassType(
        annotation.getType(),
        referencedFrom,
        eventConsumer,
        resolvedClass -> {
          addClass(resolvedClass, referencedFrom, eventConsumer);
          // For annotations in target handle annotation "methods" used to set values.
          annotation.forEachElement(
              element -> {
                if (isTargetType(resolvedClass.getType())) {
                  resolvedClass.forEachClassMethodMatching(
                      method -> method.getName().isIdenticalTo(element.name),
                      method -> eventConsumer.notifyPresentMethod(method, referencedFrom));
                }
                // Handle the argument values passed to the annotation "method".
                registerDexValue(element.getValue(), context, referencedFrom, eventConsumer);
              });
        });
  }

  private void registerDexValue(
      DexValue value,
      DexProgramClass context,
      DefinitionContext referencedFrom,
      UseCollectorEventConsumer eventConsumer) {
    if (value.isDexValueType()) {
      addType(value.asDexValueType().getValue(), referencedFrom, eventConsumer);
    } else if (value.isDexValueEnum()) {
      DexField field = value.asDexValueEnum().value;
      handleRewrittenFieldReference(field, context, referencedFrom, eventConsumer);
    } else if (value.isDexValueArray()) {
      for (DexValue elementValue : value.asDexValueArray().getValues()) {
        registerDexValue(elementValue, context, referencedFrom, eventConsumer);
      }
    }
  }

  private void handleRewrittenFieldReference(
      DexField field,
      DexProgramClass context,
      DefinitionContext referencedFrom,
      UseCollectorEventConsumer eventConsumer) {
    addType(field.getHolderType(), referencedFrom, eventConsumer);
    addType(field.getType(), referencedFrom, eventConsumer);
    FieldResolutionResult resolutionResult = appInfo().resolveField(field);
    if (resolutionResult.hasSuccessfulResolutionResult()) {
      resolutionResult.forEachSuccessfulFieldResolutionResult(
          singleResolutionResult -> {
            DexClassAndField resolvedField = singleResolutionResult.getResolutionPair();
            if (isTargetType(resolvedField.getHolderType())) {
              handleMemberResolution(field, resolvedField, context, referencedFrom, eventConsumer);
              eventConsumer.notifyPresentField(resolvedField, referencedFrom);
            }
          });
    } else {
      eventConsumer.notifyMissingField(field, referencedFrom);
    }
  }

  private void handleInvoke(
      DexMethod method,
      MethodResolutionResult resolutionResult,
      Function<SingleResolutionResult<?>, DexClassAndMethod> getResult,
      ProgramMethod context,
      DefinitionContext referencedFrom,
      UseCollectorEventConsumer eventConsumer) {
    handleMethodResolution(
        method, resolutionResult, getResult, context, referencedFrom, eventConsumer);
    notifyReflectiveIdentification(method, context);
  }

  private void handleMethodResolution(
      DexMethod method,
      MethodResolutionResult resolutionResult,
      Function<SingleResolutionResult<?>, DexClassAndMethod> getResult,
      ProgramDefinition context,
      DefinitionContext referencedFrom,
      UseCollectorEventConsumer eventConsumer) {
    BooleanBox seenSingleResult = new BooleanBox();
    resolutionResult.forEachMethodResolutionResult(
        result -> {
          if (result.isFailedResolution()) {
            result
                .asFailedResolution()
                .forEachFailureDependency(
                    type -> addType(type, referencedFrom, eventConsumer),
                    methodCausingFailure ->
                        handleMethodReference(
                            method,
                            methodCausingFailure.asDexClassAndMethod(appView),
                            context,
                            referencedFrom,
                            eventConsumer));
            return;
          }
          seenSingleResult.set();
          handleMethodReference(
              method,
              getResult.apply(result.asSingleResolution()),
              context,
              referencedFrom,
              eventConsumer);
        });
    if (seenSingleResult.isFalse()) {
      resolutionResult.forEachMethodResolutionResult(
          failingResult -> {
            assert failingResult.isFailedResolution();
            if (!failingResult.asFailedResolution().hasMethodsCausingError()) {
              handleMethodReference(method, null, context, referencedFrom, eventConsumer);
            }
          });
    }
  }

  private void handleMethodReference(
      DexMethod method,
      DexClassAndMethod resolvedMethod,
      ProgramDefinition context,
      DefinitionContext referencedFrom,
      UseCollectorEventConsumer eventConsumer) {
    addType(method.getHolderType(), referencedFrom, eventConsumer);
    addTypes(method.getParameters(), referencedFrom, eventConsumer);
    addType(method.getReturnType(), referencedFrom, eventConsumer);
    if (resolvedMethod != null) {
      DexEncodedMethod definition = resolvedMethod.getDefinition();
      assert resolvedMethod.getReference().match(method)
          || resolvedMethod.getHolder().isSignaturePolymorphicMethod(definition, factory);
      if (isTargetType(resolvedMethod.getHolderType())) {
        handleMemberResolution(
            method, resolvedMethod, context.getContextClass(), referencedFrom, eventConsumer);
        eventConsumer.notifyPresentMethod(resolvedMethod, referencedFrom);
      }
    } else {
      eventConsumer.notifyMissingMethod(method, referencedFrom);
    }
  }

  class MethodUseCollector extends UseRegistry<ProgramMethod> {

    private final DefinitionContext referencedFrom;
    private final UseCollectorEventConsumer eventConsumer;

    public MethodUseCollector(
        ProgramMethod context,
        DefinitionContext referencedFrom,
        UseCollectorEventConsumer eventConsumer) {
      super(appView(), context);
      this.referencedFrom = referencedFrom;
      this.eventConsumer = eventConsumer;
    }

    // Method references.

    @Override
    public void registerInvokeDirect(DexMethod method) {
      if (getContext().getHolder().originatesFromDexResource()) {
        handleInvoke(
            method,
            appInfo().unsafeResolveMethodDueToDexFormat(method),
            SingleResolutionResult::getResolutionPair,
            getContext(),
            referencedFrom,
            eventConsumer);
      } else {
        BooleanBox seenMethod = new BooleanBox();
        appView
            .contextIndependentDefinitionForWithResolutionResult(method.getHolderType())
            .forEachClassResolutionResult(
                holder -> {
                  DexClassAndMethod target = method.lookupMemberOnClass(holder);
                  if (target != null) {
                    handleMethodReference(
                        method, target, getContext(), referencedFrom, eventConsumer);
                    seenMethod.set();
                  }
                });
        if (seenMethod.isFalse()) {
          handleMethodReference(method, null, getContext(), referencedFrom, eventConsumer);
        }
      }
    }

    @Override
    public void registerInvokeInterface(DexMethod method) {
      handleInvokeWithDynamicDispatch(method, InvokeType.INTERFACE);
    }

    @Override
    public void registerInvokeStatic(DexMethod method) {
      handleInvoke(
          method,
          appInfo().unsafeResolveMethodDueToDexFormat(method),
          SingleResolutionResult::getResolutionPair,
          getContext(),
          referencedFrom,
          eventConsumer);
    }

    @Override
    public void registerInvokeSuper(DexMethod method) {
      handleInvoke(
          method,
          appInfo().unsafeResolveMethodDueToDexFormat(method),
          result -> result.lookupInvokeSuperTarget(getContext().getHolder(), appView, appInfo()),
          getContext(),
          referencedFrom,
          eventConsumer);
    }

    @Override
    public void registerInvokeVirtual(DexMethod method) {
      handleInvokeWithDynamicDispatch(method, InvokeType.VIRTUAL);
    }

    private void handleInvokeWithDynamicDispatch(DexMethod method, InvokeType invokeType) {
      if (method.getHolderType().isArrayType()) {
        assert invokeType.isVirtual();
        addType(method.getHolderType(), referencedFrom, eventConsumer);
        return;
      }
      assert invokeType.isInterface() || invokeType.isVirtual();
      handleInvoke(
          method,
          invokeType.isInterface()
              ? appInfo().resolveMethodOnInterfaceHolder(method)
              : appInfo().resolveMethodOnClassHolder(method),
          SingleResolutionResult::getResolutionPair,
          getContext(),
          referencedFrom,
          eventConsumer);
    }

    // Field references.

    @Override
    public void registerInitClass(DexType clazz) {
      DexField clinitField = appView.initClassLens().getInitClassField(clazz);
      handleRewrittenFieldReference(
          clinitField, getContext().getHolder(), referencedFrom, eventConsumer);
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
      handleRewrittenFieldReference(field, getContext().getHolder(), referencedFrom, eventConsumer);
    }

    // Type references.

    @Override
    public void registerTypeReference(DexType type) {
      addType(type, referencedFrom, eventConsumer);
    }

    // Call sites.

    @Override
    public void registerCallSite(DexCallSite callSite) {
      super.registerCallSite(callSite);

      // For lambdas that implement an interface, also keep the interface method by simulating an
      // invoke to it from the current context.
      LambdaDescriptor descriptor =
          LambdaDescriptor.tryInfer(callSite, appView(), appInfo(), getContext());
      if (descriptor != null) {
        for (DexType interfaceType : descriptor.interfaces) {
          ClassResolutionResult classResolutionResult =
              appView.contextIndependentDefinitionForWithResolutionResult(interfaceType);
          if (classResolutionResult.hasClassResolutionResult()) {
            classResolutionResult.forEachClassResolutionResult(
                interfaceDefinition -> {
                  DexEncodedMethod mainMethod =
                      interfaceDefinition.lookupMethod(descriptor.getMainMethod());
                  if (mainMethod != null) {
                    registerInvokeInterface(mainMethod.getReference());
                  }
                  for (DexProto bridgeProto : descriptor.bridges) {
                    DexEncodedMethod bridgeMethod =
                        interfaceDefinition.lookupMethod(bridgeProto, descriptor.getName());
                    if (bridgeMethod != null) {
                      registerInvokeInterface(bridgeMethod.getReference());
                    }
                  }
                });
          } else {
            eventConsumer.notifyMissingClass(interfaceType, referencedFrom);
          }
        }
      }
    }
  }

  private static class KotlinMetadataUseCollectorEventConsumer
      implements UseCollectorEventConsumer {

    private final UseCollectorEventConsumer parent;

    private KotlinMetadataUseCollectorEventConsumer(UseCollectorEventConsumer parent) {
      this.parent = parent;
    }

    @Override
    public void notifyPresentClass(DexClass clazz, DefinitionContext referencedFrom) {
      parent.notifyPresentClass(clazz, referencedFrom);
    }

    @Override
    public void notifyMissingClass(DexType type, DefinitionContext referencedFrom) {
      // Intentionally empty.
    }

    @Override
    public void notifyPresentField(DexClassAndField field, DefinitionContext referencedFrom) {
      parent.notifyPresentField(field, referencedFrom);
    }

    @Override
    public void notifyMissingField(DexField field, DefinitionContext referencedFrom) {
      // Intentionally empty.
    }

    @Override
    public void notifyPresentMethod(DexClassAndMethod method, DefinitionContext referencedFrom) {
      parent.notifyPresentMethod(method, referencedFrom);
    }

    @Override
    public void notifyPresentMethod(
        DexClassAndMethod method, DefinitionContext referencedFrom, DexMethod reference) {
      parent.notifyPresentMethod(method, referencedFrom, reference);
    }

    @Override
    public void notifyPresentMethodOverride(
        DexClassAndMethod method, ProgramMethod override, DefinitionContext referencedFrom) {
      parent.notifyPresentMethodOverride(method, override, referencedFrom);
    }

    @Override
    public void notifyMissingMethod(DexMethod method, DefinitionContext referencedFrom) {
      // Intentionally empty.
    }

    @Override
    public void notifyPackageOf(Definition definition) {
      parent.notifyPackageOf(definition);
    }
  }
}
