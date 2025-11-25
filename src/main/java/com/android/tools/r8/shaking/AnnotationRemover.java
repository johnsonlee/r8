// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import static com.android.tools.r8.graph.DexAnnotation.VISIBILITY_BUILD;
import static com.android.tools.r8.graph.DexAnnotation.alwaysRetainCompileTimeAnnotation;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexAnnotation;
import com.android.tools.r8.graph.DexAnnotation.AnnotatedKind;
import com.android.tools.r8.graph.DexAnnotationElement;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexDefinition;
import com.android.tools.r8.graph.DexEncodedAnnotation;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexValue;
import com.android.tools.r8.graph.DexValue.DexValueAnnotation;
import com.android.tools.r8.graph.EnclosingMethodAttribute;
import com.android.tools.r8.graph.InnerClassAttribute;
import com.android.tools.r8.graph.ProgramDefinition;
import com.android.tools.r8.graph.ProgramMember;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.kotlin.KotlinMemberLevelInfo;
import com.android.tools.r8.kotlin.KotlinPropertyInfo;
import com.android.tools.r8.shaking.Enqueuer.Mode;
import com.android.tools.r8.utils.ArrayUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ThreadUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class AnnotationRemover {

  private final AppView<AppInfoWithLiveness> appView;
  private final Mode mode;
  private final InternalOptions options;
  private final Set<DexAnnotation> annotationsToRetain;
  private final ProguardKeepAttributes keep;

  private AnnotationRemover(
      AppView<AppInfoWithLiveness> appView, Set<DexAnnotation> annotationsToRetain, Mode mode) {
    this.appView = appView;
    this.mode = mode;
    this.options = appView.options();
    this.annotationsToRetain = annotationsToRetain;
    this.keep = appView.options().getProguardConfiguration().getKeepAttributes();
  }

  public static Builder builder(Mode mode) {
    return new Builder(mode);
  }

  /** Used to filter annotations on classes, methods and fields. */
  private boolean filterAnnotations(
      ProgramDefinition holder,
      DexAnnotation annotation,
      AnnotatedKind kind,
      KeepInfo<?, ?> keepInfo) {
    return annotationsToRetain.contains(annotation)
        || shouldKeepAnnotation(
            appView, holder, annotation, isAnnotationTypeLive(annotation), kind, mode, keepInfo);
  }

  public static boolean shouldKeepAnnotation(
      AppView<?> appView,
      ProgramDefinition holder,
      DexAnnotation annotation,
      boolean isAnnotationTypeLive,
      AnnotatedKind kind,
      Mode mode,
      KeepInfo<?, ?> keepInfo) {
    // If we cannot run the AnnotationRemover we are keeping the annotation.
    InternalOptions options = appView.options();
    if (!options.isShrinking()) {
      return true;
    }

    boolean isAnnotationOnAnnotationClass =
        holder.isProgramClass() && holder.asProgramClass().isAnnotation();

    ProguardKeepAttributes config =
        options.getProguardConfiguration() != null
            ? options.getProguardConfiguration().getKeepAttributes()
            : ProguardKeepAttributes.fromPatterns(ImmutableList.of());

    DexItemFactory dexItemFactory = appView.dexItemFactory();
    switch (annotation.visibility) {
      case DexAnnotation.VISIBILITY_SYSTEM:
        if (kind.isParameter()) {
          return false;
        }
        // InnerClass and EnclosingMember are represented in class attributes, not annotations.
        assert !DexAnnotation.isInnerClassAnnotation(annotation, dexItemFactory);
        assert !DexAnnotation.isMemberClassesAnnotation(annotation, dexItemFactory);
        assert !DexAnnotation.isEnclosingMethodAnnotation(annotation, dexItemFactory);
        assert !DexAnnotation.isEnclosingClassAnnotation(annotation, dexItemFactory);
        assert options.passthroughDexCode
            || !DexAnnotation.isSignatureAnnotation(annotation, dexItemFactory);
        if (DexAnnotation.isThrowingAnnotation(annotation, dexItemFactory)) {
          KeepMethodInfo methodInfo = keepInfo.asMethodInfo();
          return methodInfo != null && !methodInfo.isThrowsRemovalAllowed(options);
        }
        if (DexAnnotation.isSourceDebugExtension(annotation, dexItemFactory)) {
          assert holder.isProgramClass();
          appView.setSourceDebugExtensionForType(
              holder.asProgramClass(), annotation.annotation.elements[0].value.asDexValueString());
          // TODO(b/343909250): Is this supposed to be kept on all live items?
          return config.sourceDebugExtension;
        }
        if (DexAnnotation.isParameterNameAnnotation(annotation, dexItemFactory)) {
          KeepMethodInfo methodInfo = keepInfo.asMethodInfo();
          return methodInfo != null && !methodInfo.isParameterNamesRemovalAllowed(options);
        }
        if (isAnnotationOnAnnotationClass
            && DexAnnotation.isAnnotationDefaultAnnotation(annotation, dexItemFactory)
            && shouldRetainAnnotationDefaultAnnotationOnAnnotationClass(annotation)) {
          // TODO(b/343909250): Is this supposed to be kept on all live items?
          return true;
        }
        return false;

      case DexAnnotation.VISIBILITY_RUNTIME:
        if (isAnnotationOnAnnotationClass
            && DexAnnotation.isJavaLangRetentionAnnotation(annotation, dexItemFactory)
            && shouldRetainRetentionAnnotationOnAnnotationClass(annotation, dexItemFactory)) {
          return true;
        }
        return shouldKeepNormalAnnotation(
            annotation, isAnnotationTypeLive, kind, keepInfo, options);

      case VISIBILITY_BUILD:
        if (annotation
            .getAnnotationType()
            .getDescriptor()
            .startsWith(options.itemFactory.dalvikAnnotationOptimizationPrefix)) {
          return true;
        }
        if (isComposableAnnotationToRetain(appView, annotation, kind, mode)) {
          return true;
        }
        return shouldKeepNormalAnnotation(
            annotation, isAnnotationTypeLive, kind, keepInfo, options);

      default:
        throw new Unreachable("Unexpected annotation visibility.");
    }
  }

  private static boolean shouldKeepNormalAnnotation(
      DexAnnotation annotation,
      boolean isAnnotationTypeLive,
      AnnotatedKind kind,
      KeepInfo<?, ?> keepInfo,
      InternalOptions options) {
    if (kind.isParameter()) {
      KeepMethodInfo methodInfo = keepInfo.asMethodInfo();
      return methodInfo != null
          && !methodInfo.isParameterAnnotationRemovalAllowed(
              options, annotation, isAnnotationTypeLive);
    }
    if (annotation.isTypeAnnotation()) {
      return !keepInfo.isTypeAnnotationRemovalAllowed(options, annotation, isAnnotationTypeLive);
    }
    return !keepInfo.isAnnotationRemovalAllowed(options, annotation, isAnnotationTypeLive);
  }

  private boolean isAnnotationTypeLive(DexAnnotation annotation) {
    return isAnnotationTypeLive(annotation, appView);
  }

  private static boolean isAnnotationTypeLive(
      DexAnnotation annotation, AppView<AppInfoWithLiveness> appView) {
    DexType annotationType = annotation.annotation.type.getBaseType();
    return appView.appInfo().isNonProgramTypeOrLiveProgramType(annotationType);
  }

  public AnnotationRemover ensureValid() {
    keep.ensureValid(appView.options().forceProguardCompatibility);
    return this;
  }

  public AnnotationRemover run(ExecutorService executorService) throws ExecutionException {
    ThreadUtils.processItems(
        appView.appInfo().classes(),
        this::run,
        appView.options().getThreadingModule(),
        executorService);
    assert verifyNoKeptKotlinMembersForClassesWithNoKotlinInfo();
    return this;
  }

  public void runForExcludedClassesInR8Partial(ExecutorService executorService)
      throws ExecutionException {
    if (options.partialSubCompilationConfiguration == null) {
      return;
    }
    ThreadUtils.processItems(
        options.partialSubCompilationConfiguration.asR8().getDexingOutputClasses(),
        clazz -> {
          clazz.removeAnnotations(
              annotation ->
                  annotation.isClassRetentionAnnotation()
                      && !alwaysRetainCompileTimeAnnotation(
                          annotation.getAnnotationType(), options));
          clazz.forEachProgramMember(
              member ->
                  member
                      .getDefinition()
                      .rewriteAllAnnotations(
                          (annotation, kind) ->
                              annotation.isClassRetentionAnnotation()
                                      && !alwaysRetainCompileTimeAnnotation(
                                          annotation.getAnnotationType(), options)
                                  ? null
                                  : annotation));
        },
        options.getThreadingModule(),
        executorService);
  }

  private void run(DexProgramClass clazz) {
    KeepClassInfo keepInfo = appView.getKeepInfo().getClassInfo(clazz);
    removeAnnotations(clazz, keepInfo);
    stripAttributes(clazz, keepInfo);
    // Kotlin metadata for classes are removed in the KotlinMetadataEnqueuerExtension. Kotlin
    // properties are split over fields and methods. Check if any is pinned before pruning the
    // information.
    Set<KotlinPropertyInfo> pinnedKotlinProperties = Sets.newIdentityHashSet();
    clazz.forEachProgramMember(member -> processMember(member, clazz, pinnedKotlinProperties));
    clazz.forEachProgramMember(
        member -> {
          KotlinMemberLevelInfo kotlinInfo = member.getKotlinInfo();
          if (kotlinInfo.isProperty()
              && !pinnedKotlinProperties.contains(kotlinInfo.asProperty().getReference())) {
            member.clearKotlinInfo();
          }
        });
  }

  private boolean verifyNoKeptKotlinMembersForClassesWithNoKotlinInfo() {
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      if (clazz.getKotlinInfo().isNoKotlinInformation()) {
        clazz.forEachProgramMember(
            member -> {
              assert member.getKotlinInfo().isNoKotlinInformation()
                  : "Should have pruned kotlin info";
            });
      }
    }
    return true;
  }

  private void processMember(
      ProgramMember<?, ?> member,
      DexProgramClass clazz,
      Set<KotlinPropertyInfo> pinnedKotlinProperties) {
    KeepMemberInfo<?, ?> memberInfo = appView.getKeepInfo().getMemberInfo(member);
    removeAnnotations(member, memberInfo);
    if (memberInfo.isSignatureRemovalAllowed(options)) {
      member.clearGenericSignature();
    }
    if (!member.getKotlinInfo().isProperty()
        && memberInfo.isKotlinMetadataRemovalAllowed(clazz, options)) {
      member.clearKotlinInfo();
    }
    // Postpone removal of kotlin property info until we have seen all fields, setters and getters.
    if (member.getKotlinInfo().isProperty()
        && !memberInfo.isKotlinMetadataRemovalAllowed(clazz, options)) {
      pinnedKotlinProperties.add(member.getKotlinInfo().asProperty().getReference());
    }
  }

  private DexAnnotation rewriteAnnotation(
      ProgramDefinition holder,
      DexAnnotation original,
      AnnotatedKind kind,
      KeepInfo<?, ?> keepInfo) {
    // Check if we should keep this annotation first.
    if (filterAnnotations(holder, original, kind, keepInfo)) {
      // Then, filter out values that refer to dead definitions.
      return original.rewrite(annotation -> rewriteEncodedAnnotation(annotation, holder));
    }
    return null;
  }

  private DexEncodedAnnotation rewriteEncodedAnnotation(
      DexEncodedAnnotation original, ProgramDefinition holder) {
    GraphLens graphLens = appView.graphLens();
    DexType annotationType = original.type.getBaseType();
    DexType rewrittenType = graphLens.lookupType(annotationType);
    DexEncodedAnnotation rewrite =
        original.rewrite(
            graphLens::lookupType,
            element -> rewriteAnnotationElement(rewrittenType, element, holder));
    assert rewrite != null;
    assert appView.appInfo().definitionFor(rewrittenType) == null
        || appView.appInfo().isNonProgramTypeOrLiveProgramType(rewrittenType);
    if (rewrite.getType().isIdenticalTo(appView.dexItemFactory().annotationDefault)
        && rewrite.getNumberOfElements() == 0) {
      return null;
    }
    return rewrite;
  }

  private DexAnnotationElement rewriteAnnotationElement(
      DexType annotationType, DexAnnotationElement original, ProgramDefinition holder) {
    // The dalvik.annotation.AnnotationDefault is typically not on bootclasspath. However, if it
    // is present, the definition does not define the 'value' getter but that is the spec:
    // https://source.android.com/devices/tech/dalvik/dex-format#dalvik-annotation-default
    // If the annotation matches the structural requirement keep it, but prune the default values
    // that no longer match a method on the annotation class. If no default values remain after
    // pruning, then remove the AnnotationDefault altogether.
    DexString name = original.getName();
    if (appView.dexItemFactory().annotationDefault.isIdenticalTo(annotationType)
        && appView.dexItemFactory().valueString.isIdenticalTo(name)) {
      DexEncodedAnnotation annotationValue = original.getValue().asDexValueAnnotation().getValue();
      DexAnnotationElement[] elements = annotationValue.elements;
      DexAnnotationElement[] rewrittenElements =
          ArrayUtils.map(
              elements,
              element -> {
                DexString innerName = element.getName();
                DexEncodedMethod method =
                    holder
                        .getContextClass()
                        .lookupMethod(m -> m.getName().isIdenticalTo(innerName));
                return method != null ? element : null;
              },
              DexAnnotationElement.EMPTY_ARRAY);
      if (rewrittenElements == elements) {
        return original;
      }
      return ArrayUtils.isEmpty(rewrittenElements)
          ? null
          : new DexAnnotationElement(
              name,
              new DexValueAnnotation(
                  new DexEncodedAnnotation(annotationValue.getType(), rewrittenElements)));
    }
    // We cannot strip annotations where we cannot look up the definition, because this will break
    // apps that rely on the annotation to exist. See b/134766810 for more information.
    DexClass definition = appView.definitionFor(annotationType);
    if (definition == null) {
      return original;
    }
    assert definition.isInterface();
    boolean liveGetter =
        definition
            .getMethodCollection()
            .hasVirtualMethods(method -> method.getName().isIdenticalTo(name));
    return liveGetter ? original : null;
  }

  private void removeAnnotations(ProgramDefinition definition, KeepInfo<?, ?> keepInfo) {
    assert mode.isInitialTreeShaking() || annotationsToRetain.isEmpty();
    definition.rewriteAllAnnotations(
        (annotation, kind) -> rewriteAnnotation(definition, annotation, kind, keepInfo));
  }

  private static boolean isComposableAnnotationToRetain(
      AppView<?> appView, DexAnnotation annotation, AnnotatedKind kind, Mode mode) {
    return mode.isInitialTreeShaking()
        && kind.isMethod()
        && annotation
            .getAnnotationType()
            .isIdenticalTo(appView.getComposeReferences().composableType);
  }

  private static boolean shouldRetainAnnotationDefaultAnnotationOnAnnotationClass(
      DexAnnotation unusedAnnotation) {
    // We currently always retain the @AnnotationDefault annotations for annotation classes. In full
    // mode we could consider only retaining @AnnotationDefault annotations for pinned annotations,
    // as this is consistent with removing all annotations for non-kept items.
    return true;
  }

  private static boolean shouldRetainRetentionAnnotationOnAnnotationClass(
      DexAnnotation annotation, DexItemFactory dexItemFactory) {
    // Retain @Retention annotations that are different from @Retention(RetentionPolicy.CLASS).
    if (annotation.annotation.getNumberOfElements() != 1) {
      return true;
    }
    DexAnnotationElement element = annotation.annotation.getElement(0);
    if (element.name.isNotIdenticalTo(dexItemFactory.valueString)) {
      return true;
    }
    DexValue value = element.getValue();
    if (!value.isDexValueEnum()
        || value
            .asDexValueEnum()
            .getValue()
            .isNotIdenticalTo(dexItemFactory.javaLangAnnotationRetentionPolicyMembers.CLASS)) {
      return true;
    }
    return false;
  }

  private void stripAttributes(DexProgramClass clazz, KeepClassInfo keepInfo) {
    // If [clazz] is mentioned by a keep rule, it could be used for reflection, and we therefore
    // need to keep the enclosing method and inner classes attributes, if requested. In Proguard
    // compatibility mode we keep these attributes independent of whether the given class is kept.
    // In full mode we remove the attribute if not both sides are kept.
    clazz.removeEnclosingMethodAttribute(
        enclosingMethodAttribute ->
            keepInfo.isEnclosingMethodAttributeRemovalAllowed(
                options, enclosingMethodAttribute, appView));
    // It is important that the call to getEnclosingMethodAttribute is done after we potentially
    // pruned it above.
    clazz.removeInnerClasses(
        attribute ->
            canRemoveInnerClassAttribute(clazz, attribute, clazz.getEnclosingMethodAttribute()));
    if (clazz.getClassSignature().isValid() && keepInfo.isSignatureRemovalAllowed(options)) {
      clazz.clearClassSignature();
    }
    if (keepInfo.isPermittedSubclassesRemovalAllowed(options)) {
      clazz.clearPermittedSubclasses();
    }
  }

  private boolean canRemoveInnerClassAttribute(
      DexProgramClass clazz,
      InnerClassAttribute innerClassAttribute,
      EnclosingMethodAttribute enclosingAttributeMethod) {
    if (innerClassAttribute.getOuter() == null
        && (clazz.isLocalClass() || clazz.isAnonymousClass())) {
      // This is a class that has an enclosing method attribute and the inner class attribute
      // is related to the enclosed method.
      assert innerClassAttribute.getInner() != null;
      return appView
          .getKeepInfo()
          .getClassInfo(innerClassAttribute.getInner(), appView)
          .isInnerClassesAttributeRemovalAllowed(options, enclosingAttributeMethod);
    } else if (innerClassAttribute.getOuter() == null) {
      assert !clazz.isLocalClass() && !clazz.isAnonymousClass();
      return appView
          .getKeepInfo()
          .getClassInfo(innerClassAttribute.getInner(), appView)
          .isInnerClassesAttributeRemovalAllowed(options);
    } else if (innerClassAttribute.getInner() == null) {
      assert innerClassAttribute.getOuter() != null;
      return appView
          .getKeepInfo()
          .getClassInfo(innerClassAttribute.getOuter(), appView)
          .isInnerClassesAttributeRemovalAllowed(options);
    } else {
      // If both inner and outer is specified, only keep if both are pinned.
      assert innerClassAttribute.getOuter() != null && innerClassAttribute.getInner() != null;
      return appView
              .getKeepInfo()
              .getClassInfo(innerClassAttribute.getInner(), appView)
              .isInnerClassesAttributeRemovalAllowed(options)
          || appView
              .getKeepInfo()
              .getClassInfo(innerClassAttribute.getOuter(), appView)
              .isInnerClassesAttributeRemovalAllowed(options);
    }
  }

  public static void clearAnnotations(AppView<?> appView) {
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      clazz.clearAnnotations();
      clazz.members().forEach(DexDefinition::clearAnnotations);
    }
  }

  public static class Builder {

    /**
     * The set of annotations that were matched by a conditional if rule. These are needed for the
     * interpretation of if rules in the second round of tree shaking.
     */
    private final Set<DexAnnotation> annotationsToRetain = Sets.newIdentityHashSet();

    private final Mode mode;

    Builder(Mode mode) {
      this.mode = mode;
    }

    public boolean isRetainedForFinalTreeShaking(DexAnnotation annotation) {
      return annotationsToRetain.contains(annotation);
    }

    public void retainAnnotation(DexAnnotation annotation) {
      annotationsToRetain.add(annotation);
    }

    public AnnotationRemover build(AppView<AppInfoWithLiveness> appView) {
      return new AnnotationRemover(appView, annotationsToRetain, mode);
    }
  }
}
