// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.signature;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexAnnotation;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GenericSignature;
import com.android.tools.r8.graph.GenericSignature.ClassSignature;
import com.android.tools.r8.graph.GenericSignature.FieldTypeSignature;
import com.android.tools.r8.graph.GenericSignature.MethodTypeSignature;
import com.android.tools.r8.graph.GenericSignatureTypeRewriter;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.PredicateUtils;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.ThreadUtils;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.function.Predicate;

// TODO(b/129925954): Reimplement this by using the internal encoding and transformation logic.
public class GenericSignatureRewriter {

  private final AppView<?> appView;
  private final NamingLens namingLens;
  private final InternalOptions options;
  private final Reporter reporter;
  private final Predicate<DexType> isTypeMissing;

  public GenericSignatureRewriter(AppView<?> appView, NamingLens namingLens) {
    this.appView = appView;
    this.namingLens = namingLens;
    this.options = appView.options();
    this.reporter = options.reporter;
    isTypeMissing = PredicateUtils.isNull(appView.appInfo()::definitionForWithoutExistenceAssert);
  }

  public void run(Iterable<? extends DexProgramClass> classes, ExecutorService executorService)
      throws ExecutionException {
    // Rewrite signature annotations for applications that are minified or if we have liveness
    // information, since we could have pruned types.
    if (namingLens.isIdentityLens() && !appView.appInfo().hasLiveness()) {
      return;
    }
    // Classes may not be the same as appInfo().classes() if applymapping is used on classpath
    // arguments. If that is the case, the ProguardMapMinifier will pass in all classes that is
    // either ProgramClass or has a mapping. This is then transitively called inside the
    // ClassNameMinifier.
    ThreadUtils.processItems(
        classes,
        clazz -> {
          GenericSignatureTypeRewriter typeRewriter =
              new GenericSignatureTypeRewriter(appView, clazz);
          clazz.setAnnotations(
              rewriteGenericSignatures(
                  clazz.annotations(),
                  signature -> {
                    ClassSignature classSignature =
                        GenericSignature.parseClassSignature(
                            clazz.toSourceString(),
                            signature,
                            clazz.origin,
                            appView.dexItemFactory(),
                            options.reporter);
                    if (classSignature.hasNoSignature()) {
                      return null;
                    }
                    return typeRewriter
                        .rewrite(classSignature)
                        .toRenamedString(namingLens, isTypeMissing);
                  }));
          clazz.forEachField(
              field ->
                  field.setAnnotations(
                      rewriteGenericSignatures(
                          field.annotations(),
                          signature -> {
                            FieldTypeSignature fieldSignature =
                                GenericSignature.parseFieldTypeSignature(
                                    field.toSourceString(),
                                    signature,
                                    clazz.origin,
                                    appView.dexItemFactory(),
                                    options.reporter);
                            if (fieldSignature.hasNoSignature()) {
                              return null;
                            }
                            return typeRewriter
                                .rewrite(fieldSignature)
                                .toRenamedString(namingLens, isTypeMissing);
                          })));
          clazz.forEachMethod(
              method ->
                  method.setAnnotations(
                      rewriteGenericSignatures(
                          method.annotations(),
                          signature -> {
                            MethodTypeSignature methodSignature =
                                GenericSignature.parseMethodSignature(
                                    method.toSourceString(),
                                    signature,
                                    clazz.origin,
                                    appView.dexItemFactory(),
                                    options.reporter);
                            if (methodSignature.hasNoSignature()) {
                              return null;
                            }
                            return typeRewriter
                                .rewrite(methodSignature)
                                .toRenamedString(namingLens, isTypeMissing);
                          })));
        },
        executorService);
  }

  private DexAnnotationSet rewriteGenericSignatures(
      DexAnnotationSet annotations, Function<String, String> rewrite) {
    // There can be no more than one signature annotation in an annotation set.
    final int VALID = -1;
    int invalidOrPrunedIndex = VALID;
    DexAnnotation[] rewrittenAnnotations = null;
    for (int i = 0; i < annotations.annotations.length && invalidOrPrunedIndex == VALID; i++) {
      DexAnnotation annotation = annotations.annotations[i];
      if (DexAnnotation.isSignatureAnnotation(annotation, appView.dexItemFactory())) {
        if (rewrittenAnnotations == null) {
          rewrittenAnnotations = new DexAnnotation[annotations.annotations.length];
          System.arraycopy(annotations.annotations, 0, rewrittenAnnotations, 0, i);
        }
        String signature = DexAnnotation.getSignature(annotation);
        String rewrittenSignature = rewrite.apply(signature);
        if (rewrittenSignature != null) {
          rewrittenAnnotations[i] =
              DexAnnotation.createSignatureAnnotation(rewrittenSignature, appView.dexItemFactory());
        } else {
          invalidOrPrunedIndex = i;
        }
      } else if (rewrittenAnnotations != null) {
        rewrittenAnnotations[i] = annotation;
      }
    }

    // Return the rewritten signatures if it was valid and could be rewritten.
    if (invalidOrPrunedIndex == VALID) {
      return rewrittenAnnotations != null
          ? new DexAnnotationSet(rewrittenAnnotations)
          : annotations;
    }
    // Remove invalid signature if found.
    DexAnnotation[] prunedAnnotations =
        new DexAnnotation[annotations.annotations.length - 1];
    int dest = 0;
    for (int i = 0; i < annotations.annotations.length; i++) {
      if (i != invalidOrPrunedIndex) {
        prunedAnnotations[dest++] = annotations.annotations[i];
      }
    }
    assert dest == prunedAnnotations.length;
    return new DexAnnotationSet(prunedAnnotations);
  }
}
