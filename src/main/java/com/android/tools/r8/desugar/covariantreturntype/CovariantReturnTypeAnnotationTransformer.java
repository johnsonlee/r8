// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.covariantreturntype;

import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexAnnotation;
import com.android.tools.r8.graph.DexAnnotationElement;
import com.android.tools.r8.graph.DexEncodedAnnotation;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexValue;
import com.android.tools.r8.graph.DexValue.DexValueAnnotation;
import com.android.tools.r8.graph.DexValue.DexValueArray;
import com.android.tools.r8.graph.DexValue.DexValueType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.conversion.IRConverter;
import com.android.tools.r8.ir.conversion.MethodConversionOptions;
import com.android.tools.r8.ir.conversion.MethodProcessorEventConsumer;
import com.android.tools.r8.ir.synthetic.ForwardMethodBuilder;
import com.android.tools.r8.utils.ForEachable;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.ThreadUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

// Responsible for processing the annotations dalvik.annotation.codegen.CovariantReturnType and
// dalvik.annotation.codegen.CovariantReturnType$CovariantReturnTypes.
//
// Consider the following class:
//   public class B extends A {
//     @CovariantReturnType(returnType = B.class, presentAfter = 25)
//     @Override
//     public A m(...) { ... return new B(); }
//   }
//
// The annotation is used to indicate that the compiler should insert a synthetic method that is
// equivalent to method m, but has return type B instead of A. Thus, for this example, this
// component is responsible for inserting the following method in class B (in addition to the
// existing method m):
//   public B m(...) { A result = "invoke B.m(...)A;"; return (B) result; }
//
// Note that a method may be annotated with more than one CovariantReturnType annotation. In this
// case there will be a CovariantReturnType$CovariantReturnTypes annotation on the method that wraps
// several CovariantReturnType annotations. In this case, a new method is synthesized for each of
// the contained CovariantReturnType annotations.
public final class CovariantReturnTypeAnnotationTransformer {

  private final AppView<?> appView;
  private final IRConverter converter;
  private final DexItemFactory factory;
  private final CovariantReturnTypeReferences references;

  public CovariantReturnTypeAnnotationTransformer(
      AppView<? extends AppInfoWithClassHierarchy> appView) {
    this(appView, null);
  }

  private CovariantReturnTypeAnnotationTransformer(AppView<?> appView, IRConverter converter) {
    this.appView = appView;
    this.converter = converter;
    this.factory = appView.dexItemFactory();
    this.references = new CovariantReturnTypeReferences(factory);
  }

  public CovariantReturnTypeReferences getReferences() {
    return references;
  }

  public static void runIfNecessary(
      AppView<?> appView,
      IRConverter converter,
      CovariantReturnTypeAnnotationTransformerEventConsumer eventConsumer,
      ExecutorService executorService)
      throws ExecutionException {
    if (shouldRun(appView)) {
      new CovariantReturnTypeAnnotationTransformer(appView, converter)
          .run(eventConsumer, executorService);
    }
  }

  public static boolean shouldRun(AppView<?> appView) {
    if (!appView.options().processCovariantReturnTypeAnnotations
        || appView.options().isDesugaredLibraryCompilation()) {
      return false;
    }
    DexItemFactory factory = appView.dexItemFactory();
    DexString covariantReturnTypeDescriptor =
        factory.createString(CovariantReturnTypeReferences.COVARIANT_RETURN_TYPE_DESCRIPTOR);
    return factory.lookupType(covariantReturnTypeDescriptor) != null;
  }

  private void run(
      CovariantReturnTypeAnnotationTransformerEventConsumer eventConsumer,
      ExecutorService executorService)
      throws ExecutionException {
    List<ProgramMethod> covariantReturnTypeMethods = new ArrayList<>();
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      List<ProgramMethod> newCovariantReturnTypeMethods =
          processClass(clazz, clazz::forEachProgramVirtualMethod, eventConsumer);
      covariantReturnTypeMethods.addAll(newCovariantReturnTypeMethods);
    }
    // Convert methods to DEX.
    converter.optimizeSynthesizedMethods(
        covariantReturnTypeMethods,
        MethodProcessorEventConsumer.empty(),
        MethodConversionOptions.forD8(appView),
        executorService);
  }

  public void processMethods(
      Map<DexProgramClass, List<ProgramMethod>> methodsToProcess,
      CovariantReturnTypeAnnotationTransformerEventConsumer eventConsumer,
      ExecutorService executorService)
      throws ExecutionException {
    ThreadUtils.processMap(
        methodsToProcess,
        (clazz, methods) -> {
          List<ProgramMethod> sortedMethods =
              ListUtils.destructiveSort(methods, Comparator.comparing(ProgramMethod::getReference));
          processClass(clazz, sortedMethods::forEach, eventConsumer);
        },
        appView.options().getThreadingModule(),
        executorService);
  }

  // Processes all the dalvik.annotation.codegen.CovariantReturnType and dalvik.annotation.codegen.
  // CovariantReturnTypes annotations in the given DexClass. Adds the newly constructed, synthetic
  // methods to the list covariantReturnTypeMethods.
  private List<ProgramMethod> processClass(
      DexProgramClass clazz,
      ForEachable<ProgramMethod> methodsToProcess,
      CovariantReturnTypeAnnotationTransformerEventConsumer eventConsumer) {
    List<ProgramMethod> covariantReturnTypeMethods = new ArrayList<>();
    methodsToProcess.forEach(
        method ->
            processMethod(
                method,
                covariantReturnTypeMethod -> {
                  covariantReturnTypeMethods.add(covariantReturnTypeMethod);
                  eventConsumer.acceptCovariantReturnTypeBridgeMethod(
                      covariantReturnTypeMethod, method);
                }));
    clazz.getMethodCollection().addVirtualClassMethods(covariantReturnTypeMethods);
    return covariantReturnTypeMethods;
  }

  // Processes all the dalvik.annotation.codegen.CovariantReturnType and dalvik.annotation.Co-
  // variantReturnTypes annotations on the given method. Adds the newly constructed, synthetic
  // methods to the list covariantReturnTypeMethods.
  private void processMethod(ProgramMethod method, Consumer<ProgramMethod> consumer) {
    for (DexType covariantReturnType : clearCovariantReturnTypeAnnotations(method)) {
      consumer.accept(buildCovariantReturnTypeMethod(method, covariantReturnType));
    }
  }

  // Builds a synthetic method that invokes the given method, casts the result to
  // covariantReturnType, and then returns the result. The newly created method will have return
  // type covariantReturnType.
  //
  // Note: any "synchronized" or "strictfp" modifier could be dropped safely.
  private ProgramMethod buildCovariantReturnTypeMethod(
      ProgramMethod method, DexType covariantReturnType) {
    DexMethod covariantReturnTypeMethodReference =
        method.getReference().withReturnType(covariantReturnType, factory);
    failIfPresent(method.getHolder(), covariantReturnTypeMethodReference);
    DexEncodedMethod definition =
        DexEncodedMethod.syntheticBuilder()
            .setMethod(covariantReturnTypeMethodReference)
            .setAccessFlags(
                method.getAccessFlags().copy().setBridge().setSynthetic().unsetAbstract())
            .setGenericSignature(method.getDefinition().getGenericSignature())
            .setAnnotations(method.getAnnotations())
            .setParameterAnnotations(method.getParameterAnnotations())
            .setCode(
                ForwardMethodBuilder.builder(factory)
                    .setNonStaticSource(covariantReturnTypeMethodReference)
                    .setVirtualTarget(method.getReference(), method.getHolder().isInterface())
                    .setCastResult()
                    .buildCf())
            .setApiLevelForDefinition(method.getDefinition().getApiLevelForDefinition())
            .setApiLevelForCode(method.getDefinition().getApiLevelForCode())
            .build();
    return new ProgramMethod(method.getHolder(), definition);
  }

  private void failIfPresent(DexProgramClass clazz, DexMethod covariantReturnTypeMethodReference) {
    // It is a compilation error if the class already has a method with a signature similar to one
    // of the methods in covariantReturnTypeMethods.
    if (clazz.lookupMethod(covariantReturnTypeMethodReference) != null) {
      throw appView
          .reporter()
          .fatalError(
              String.format(
                  "Cannot process CovariantReturnType annotation: Class %s already "
                      + "has a method \"%s\"",
                  clazz.getTypeName(), covariantReturnTypeMethodReference.toSourceString()));
    }
  }

  // Returns the set of covariant return types for method.
  //
  // If the method is:
  //   @dalvik.annotation.codegen.CovariantReturnType(returnType=SubOfFoo, presentAfter=25)
  //   @dalvik.annotation.codegen.CovariantReturnType(returnType=SubOfSubOfFoo, presentAfter=28)
  //   @Override
  //   public Foo foo() { ... return new SubOfSubOfFoo(); }
  // then this method returns the set { SubOfFoo, SubOfSubOfFoo }.
  private Set<DexType> clearCovariantReturnTypeAnnotations(ProgramMethod method) {
    Set<DexType> covariantReturnTypes = new LinkedHashSet<>();
    for (DexAnnotation annotation : method.getAnnotations().getAnnotations()) {
      if (references.isOneOfCovariantReturnTypeAnnotations(annotation.getAnnotationType())) {
        getCovariantReturnTypesFromAnnotation(
            method, annotation.getAnnotation(), covariantReturnTypes);
      }
    }
    if (!covariantReturnTypes.isEmpty()) {
      method
          .getDefinition()
          .setAnnotations(
              method
                  .getAnnotations()
                  .removeIf(
                      annotation ->
                          references.isOneOfCovariantReturnTypeAnnotations(
                              annotation.getAnnotationType())));
    }
    return covariantReturnTypes;
  }

  private void getCovariantReturnTypesFromAnnotation(
      ProgramMethod method, DexEncodedAnnotation annotation, Set<DexType> covariantReturnTypes) {
    boolean hasPresentAfterElement = false;
    for (DexAnnotationElement element : annotation.elements) {
      DexString name = element.getName();
      if (references.isCovariantReturnTypeAnnotation(annotation.getType())) {
        if (name.isIdenticalTo(references.returnTypeName)) {
          DexValueType dexValueType = element.getValue().asDexValueType();
          if (dexValueType == null) {
            throw new CompilationError(
                String.format(
                    "Expected element \"returnType\" of CovariantReturnType annotation to "
                        + "reference a type (method: \"%s\", was: %s)",
                    method.toSourceString(), element.value.getClass().getCanonicalName()));
          }
          covariantReturnTypes.add(dexValueType.getValue());
        } else if (name.isIdenticalTo(references.presentAfterName)) {
          hasPresentAfterElement = true;
        }
      } else {
        assert references.isCovariantReturnTypesAnnotation(annotation.getType());
        if (name.isIdenticalTo(references.valueName)) {
          DexValueArray array = element.getValue().asDexValueArray();
          if (array == null) {
            throw new CompilationError(
                String.format(
                    "Expected element \"value\" of CovariantReturnTypes annotation to "
                        + "be an array (method: \"%s\", was: %s)",
                    method.toSourceString(), element.getValue().getClass().getCanonicalName()));
          }

          // Handle the inner dalvik.annotation.codegen.CovariantReturnType annotations recursively.
          for (DexValue value : array.getValues()) {
            assert value.isDexValueAnnotation();
            DexValueAnnotation innerAnnotation = value.asDexValueAnnotation();
            getCovariantReturnTypesFromAnnotation(
                method, innerAnnotation.getValue(), covariantReturnTypes);
          }
        }
      }
    }

    if (references.isCovariantReturnTypeAnnotation(annotation.getType())
        && !hasPresentAfterElement) {
      throw new CompilationError(
          String.format(
              "CovariantReturnType annotation for method \"%s\" is missing mandatory element "
                  + "\"presentAfter\" (class %s)",
              method.toSourceString(), method.getHolder().getType()));
    }
  }
}
