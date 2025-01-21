// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.tracereferences;

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
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.graph.MethodResolutionResult.SingleResolutionResult;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.graph.lens.FieldLookupResult;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.graph.lens.MethodLookupResult;
import com.android.tools.r8.ir.desugar.LambdaDescriptor;
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
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

// The graph lens is intentionally only made accessible to the MethodUseCollector, since the
// graph lens should only be applied to the code.
public class UseCollector {

  protected final AppView<? extends AppInfoWithClassHierarchy> appView;
  private final DexItemFactory factory;
  private final TraceReferencesConsumer consumer;
  private final DiagnosticsHandler diagnostics;
  private final Predicate<DexType> targetPredicate;

  private final Set<ClassReference> missingClasses = new HashSet<>();
  private final Set<FieldReference> missingFields = new HashSet<>();
  private final Set<MethodReference> missingMethods = new HashSet<>();

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
    this.targetPredicate = targetPredicate;
    this.dalvikAnnotationCodegenPrefix = factory.createString("Ldalvik/annotation/codegen/");
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
    clazz.forEachImmediateSupertype(supertype -> registerSuperType(clazz, supertype, classContext));
    clazz.forEachProgramField(this::registerField);
    clazz.forEachProgramMethod(this::registerMethod);
    for (DexAnnotation annotation : clazz.annotations().getAnnotations()) {
      registerAnnotation(annotation, clazz, classContext);
    }
  }

  protected void notifyPresentClass(DexClass clazz, DefinitionContext referencedFrom) {
    TracedClassImpl tracedClass = new TracedClassImpl(clazz, referencedFrom);
    consumer.acceptType(tracedClass, diagnostics);
  }

  protected void notifyPresentField(DexClassAndField field, DefinitionContext referencedFrom) {
    TracedFieldImpl tracedField = new TracedFieldImpl(field, referencedFrom);
    consumer.acceptField(tracedField, diagnostics);
  }

  protected void notifyPresentMethod(DexClassAndMethod method, DefinitionContext referencedFrom) {
    TracedMethodImpl tracedMethod = new TracedMethodImpl(method, referencedFrom);
    consumer.acceptMethod(tracedMethod, diagnostics);
  }

  protected void notifyPackageOf(Definition definition) {
    consumer.acceptPackage(
        Reference.packageFromString(definition.getContextType().getPackageName()), diagnostics);
  }

  AppView<? extends AppInfoWithClassHierarchy> appView() {
    return appView;
  }

  AppInfoWithClassHierarchy appInfo() {
    return appView.appInfo();
  }

  GraphLens graphLens() {
    return appView.graphLens();
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

  private void addClassType(
      DexType type, DefinitionContext referencedFrom, Consumer<DexClass> resolvedClassesConsumer) {
    assert type.isClassType();
    ClassResolutionResult result =
        appView.contextIndependentDefinitionForWithResolutionResult(type);
    if (result.hasClassResolutionResult()) {
      result.forEachClassResolutionResult(resolvedClassesConsumer);
    } else {
      TracedClassImpl missingClass = new TracedClassImpl(type, referencedFrom);
      collectMissingClass(missingClass);
      consumer.acceptType(missingClass, diagnostics);
    }
  }

  private void addClassType(DexType type, DefinitionContext referencedFrom) {
    addClassType(type, referencedFrom, clazz -> addClass(clazz, referencedFrom));
  }

  private void addClass(DexClass clazz, DefinitionContext referencedFrom) {
    if (isTargetType(clazz.getType())) {
      notifyPresentClass(clazz, referencedFrom);
      if (clazz.getAccessFlags().isPackagePrivateOrProtected()) {
        notifyPackageOf(clazz);
      }
    }
  }

  private void handleMemberResolution(
      DexMember<?, ?> reference,
      DexClassAndMember<?, ?> member,
      DexProgramClass context,
      DefinitionContext referencedFrom) {
    DexClass holder = member.getHolder();
    assert isTargetType(holder.getType());
    if (member.getHolderType().isNotIdenticalTo(reference.getHolderType())) {
      notifyPresentClass(holder, referencedFrom);
    }
    ensurePackageAccessToMember(member, context);
  }

  private void ensurePackageAccessToMember(
      DexClassAndMember<?, ?> member, DexProgramClass context) {
    if (member.getAccessFlags().isPackagePrivateOrProtected()) {
      if (member.getAccessFlags().isPackagePrivate()
          || !appInfo().isSubtype(context, member.getHolder())) {
        notifyPackageOf(member);
      }
    }
  }

  private void addSuperMethodFromTarget(
      DexClassAndMethod method, ProgramMethod context, DefinitionContext referencedFrom) {
    assert !method.isProgramMethod();
    assert isTargetType(method.getHolderType());
    // There should be no need to register the types referenced from the method signature:
    // - The return type and the parameter types are registered when visiting the source method
    //   that overrides this target method,
    // - The holder type is registered from visiting the extends/implements clause of the sub
    //   class.
    notifyPresentMethod(method, referencedFrom);
    ensurePackageAccessToMember(method, context.getHolder());
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
    field
        .getAnnotations()
        .forEach(
            dexAnnotation -> registerAnnotation(dexAnnotation, field.getHolder(), referencedFrom));
  }

  private void registerMethod(ProgramMethod method) {
    DefinitionContext referencedFrom = DefinitionContextUtils.create(method);
    addTypes(method.getParameters(), referencedFrom);
    addType(method.getReturnType(), referencedFrom);
    method
        .getAnnotations()
        .forEach(
            dexAnnotation -> registerAnnotation(dexAnnotation, method.getHolder(), referencedFrom));
    method
        .getParameterAnnotations()
        .forEachAnnotation(
            dexAnnotation -> registerAnnotation(dexAnnotation, method.getHolder(), referencedFrom));
    traceCode(method);
  }

  private void traceCode(ProgramMethod method) {
    method.registerCodeReferences(new MethodUseCollector(method));
  }

  private void registerSuperType(
      DexProgramClass clazz, DexType superType, DefinitionContext referencedFrom) {
    addType(superType, referencedFrom);
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
          if (resolvedMethod != null
              && !resolvedMethod.isProgramMethod()
              && isTargetType(resolvedMethod.getHolderType())) {
            addSuperMethodFromTarget(resolvedMethod, method, referencedFrom);
          }
        });
  }

  private void registerAnnotation(
      DexAnnotation annotation, DexProgramClass context, DefinitionContext referencedFrom) {
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
                    element.getValue().asDexValueAnnotation().getValue(), context, referencedFrom);
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
          annotation.annotation.elements[0].value.asDexValueArray(), context, referencedFrom);
      return;
    }
    assert !type.getDescriptor().startsWith(factory.dalvikAnnotationPrefix)
        : "Unexpected annotation with prefix "
            + factory.dalvikAnnotationPrefix
            + ": "
            + type.getDescriptor();
    registerEncodedAnnotation(annotation.getAnnotation(), context, referencedFrom);
  }

  void registerEncodedAnnotation(
      DexEncodedAnnotation annotation, DexProgramClass context, DefinitionContext referencedFrom) {
    addClassType(
        annotation.getType(),
        referencedFrom,
        resolvedClass -> {
          addClass(resolvedClass, referencedFrom);
          // For annotations in target handle annotation "methods" used to set values.
          annotation.forEachElement(
              element -> {
                if (isTargetType(resolvedClass.getType())) {
                  resolvedClass.forEachClassMethodMatching(
                      method -> method.getName().isIdenticalTo(element.name),
                      method -> notifyPresentMethod(method, referencedFrom));
                }
                // Handle the argument values passed to the annotation "method".
                registerDexValue(element.getValue(), context, referencedFrom);
              });
        });
  }

  private void registerDexValue(
      DexValue value, DexProgramClass context, DefinitionContext referencedFrom) {
    if (value.isDexValueType()) {
      addType(value.asDexValueType().getValue(), referencedFrom);
    } else if (value.isDexValueEnum()) {
      DexField field = value.asDexValueEnum().value;
      handleRewrittenFieldReference(field, context, referencedFrom);
    } else if (value.isDexValueArray()) {
      for (DexValue elementValue : value.asDexValueArray().getValues()) {
        registerDexValue(elementValue, context, referencedFrom);
      }
    }
  }

  private void handleRewrittenFieldReference(
      DexField field, DexProgramClass context, DefinitionContext referencedFrom) {
    addType(field.getHolderType(), referencedFrom);
    addType(field.getType(), referencedFrom);
    FieldResolutionResult resolutionResult = appInfo().resolveField(field);
    if (resolutionResult.hasSuccessfulResolutionResult()) {
      resolutionResult.forEachSuccessfulFieldResolutionResult(
          singleResolutionResult -> {
            DexClassAndField resolvedField = singleResolutionResult.getResolutionPair();
            if (isTargetType(resolvedField.getHolderType())) {
              handleMemberResolution(field, resolvedField, context, referencedFrom);
              notifyPresentField(resolvedField, referencedFrom);
            }
          });
    } else {
      TracedFieldImpl missingField = new TracedFieldImpl(field, referencedFrom);
      collectMissingField(missingField);
      consumer.acceptField(missingField, diagnostics);
    }
  }

  class MethodUseCollector extends UseRegistry<ProgramMethod> {

    private final DefinitionContext referencedFrom;

    public MethodUseCollector(ProgramMethod context) {
      super(appView(), context);
      this.referencedFrom = DefinitionContextUtils.create(context);
    }

    // Method references.

    @Override
    public void registerInvokeDirect(DexMethod method) {
      MethodLookupResult lookupResult = graphLens().lookupInvokeDirect(method, getContext());
      assert lookupResult.getType().isDirect();
      DexMethod rewrittenMethod = lookupResult.getReference();
      if (getContext().getHolder().originatesFromDexResource()) {
        handleRewrittenMethodResolution(
            rewrittenMethod,
            appInfo().unsafeResolveMethodDueToDexFormat(rewrittenMethod),
            SingleResolutionResult::getResolutionPair);
      } else {
        BooleanBox seenMethod = new BooleanBox();
        appView
            .contextIndependentDefinitionForWithResolutionResult(rewrittenMethod.getHolderType())
            .forEachClassResolutionResult(
                holder -> {
                  DexClassAndMethod target = rewrittenMethod.lookupMemberOnClass(holder);
                  if (target != null) {
                    handleRewrittenMethodReference(rewrittenMethod, target);
                    seenMethod.set();
                  }
                });
        if (seenMethod.isFalse()) {
          handleRewrittenMethodReference(rewrittenMethod, null);
        }
      }
    }

    @Override
    public void registerInvokeInterface(DexMethod method) {
      MethodLookupResult lookupResult = graphLens().lookupInvokeInterface(method, getContext());
      assert lookupResult.getType().isInterface();
      handleInvokeWithDynamicDispatch(lookupResult);
    }

    @Override
    public void registerInvokeStatic(DexMethod method) {
      MethodLookupResult lookupResult = graphLens().lookupInvokeStatic(method, getContext());
      assert lookupResult.getType().isStatic();
      DexMethod rewrittenMethod = lookupResult.getReference();
      handleRewrittenMethodResolution(
          rewrittenMethod,
          appInfo().unsafeResolveMethodDueToDexFormat(rewrittenMethod),
          SingleResolutionResult::getResolutionPair);
    }

    @Override
    public void registerInvokeSuper(DexMethod method) {
      MethodLookupResult lookupResult = graphLens().lookupInvokeSuper(method, getContext());
      assert lookupResult.getType().isSuper();
      DexMethod rewrittenMethod = lookupResult.getReference();
      handleRewrittenMethodResolution(
          method,
          appInfo().unsafeResolveMethodDueToDexFormat(rewrittenMethod),
          result -> result.lookupInvokeSuperTarget(getContext().getHolder(), appView, appInfo()));
    }

    @Override
    public void registerInvokeVirtual(DexMethod method) {
      MethodLookupResult lookupResult = graphLens().lookupInvokeVirtual(method, getContext());
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
      handleRewrittenMethodResolution(
          method,
          lookupResult.getType().isInterface()
              ? appInfo().resolveMethodOnInterfaceHolder(method)
              : appInfo().resolveMethodOnClassHolder(method),
          SingleResolutionResult::getResolutionPair);
    }

    private void handleRewrittenMethodResolution(
        DexMethod method,
        MethodResolutionResult resolutionResult,
        Function<SingleResolutionResult<?>, DexClassAndMethod> getResult) {
      BooleanBox seenSingleResult = new BooleanBox();
      resolutionResult.forEachMethodResolutionResult(
          result -> {
            if (result.isFailedResolution()) {
              result
                  .asFailedResolution()
                  .forEachFailureDependency(
                      type -> addType(type, referencedFrom),
                      methodCausingFailure ->
                          handleRewrittenMethodReference(
                              method, methodCausingFailure.asDexClassAndMethod(appView)));
              return;
            }
            seenSingleResult.set();
            handleRewrittenMethodReference(method, getResult.apply(result.asSingleResolution()));
          });
      if (seenSingleResult.isFalse()) {
        resolutionResult.forEachMethodResolutionResult(
            failingResult -> {
              assert failingResult.isFailedResolution();
              if (!failingResult.asFailedResolution().hasMethodsCausingError()) {
                handleRewrittenMethodReference(method, null);
              }
            });
      }
    }

    private void handleRewrittenMethodReference(
        DexMethod method, DexClassAndMethod resolvedMethod) {
      addType(method.getHolderType(), referencedFrom);
      addTypes(method.getParameters(), referencedFrom);
      addType(method.getReturnType(), referencedFrom);
      if (resolvedMethod != null) {
        DexEncodedMethod definition = resolvedMethod.getDefinition();
        assert resolvedMethod.getReference().match(method)
            || resolvedMethod.getHolder().isSignaturePolymorphicMethod(definition, factory);
        if (isTargetType(resolvedMethod.getHolderType())) {
          handleMemberResolution(method, resolvedMethod, getContext().getHolder(), referencedFrom);
          notifyPresentMethod(resolvedMethod, referencedFrom);
        }
      } else {
        TracedMethodImpl missingMethod = new TracedMethodImpl(method, referencedFrom);
        collectMissingMethod(missingMethod);
        consumer.acceptMethod(missingMethod, diagnostics);
      }
    }

    // Field references.

    @Override
    public void registerInitClass(DexType clazz) {
      DexType rewrittenClass = graphLens().lookupType(clazz);
      DexField clinitField = appView.initClassLens().getInitClassField(rewrittenClass);
      handleRewrittenFieldReference(clinitField, getContext().getHolder(), referencedFrom);
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
      FieldLookupResult lookupResult = graphLens().lookupFieldResult(field);
      handleRewrittenFieldReference(
          lookupResult.getReference(), getContext().getHolder(), referencedFrom);
    }

    // Type references.

    @Override
    public void registerTypeReference(DexType type) {
      addType(graphLens().lookupType(type), referencedFrom);
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
            TracedClassImpl missingClass = new TracedClassImpl(interfaceType, referencedFrom);
            collectMissingClass(missingClass);
            consumer.acceptType(missingClass, diagnostics);
          }
        }
      }
    }
  }
}
