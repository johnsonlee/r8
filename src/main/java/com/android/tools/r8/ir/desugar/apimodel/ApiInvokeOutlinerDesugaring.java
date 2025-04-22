// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.apimodel;

import static com.android.tools.r8.utils.AndroidApiLevelUtils.isApiLevelLessThanOrEqualToG;
import static com.android.tools.r8.utils.AndroidApiLevelUtils.isOutlinedAtSameOrLowerLevel;

import com.android.tools.r8.androidapi.AndroidApiLevelCompute;
import com.android.tools.r8.androidapi.ComputedApiLevel;
import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMember;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexLibraryClass;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexTypeList;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaringEventConsumer;
import com.android.tools.r8.ir.synthetic.CheckCastSourceCode;
import com.android.tools.r8.ir.synthetic.ConstClassSourceCode;
import com.android.tools.r8.ir.synthetic.FieldAccessorBuilder;
import com.android.tools.r8.ir.synthetic.ForwardMethodBuilder;
import com.android.tools.r8.ir.synthetic.InstanceOfSourceCode;
import com.android.tools.r8.synthesis.SyntheticMethodBuilder;
import com.android.tools.r8.utils.AndroidApiLevelUtils;
import com.android.tools.r8.utils.Pair;
import com.android.tools.r8.utils.TraversalContinuation;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * This desugaring will outline calls to library methods that are introduced after the min-api
 * level. For classes introduced after the min-api level see ApiReferenceStubber.
 */
public abstract class ApiInvokeOutlinerDesugaring {

  private final AppView<?> appView;
  private final AndroidApiLevelCompute apiLevelCompute;
  private final DexItemFactory factory;

  private final DexTypeList objectParams;

  ApiInvokeOutlinerDesugaring(AppView<?> appView, AndroidApiLevelCompute apiLevelCompute) {
    this.appView = appView;
    this.apiLevelCompute = apiLevelCompute;
    this.factory = appView.dexItemFactory();
    this.objectParams = DexTypeList.create(new DexType[] {factory.objectType});
  }

  public static CfToCfApiInvokeOutlinerDesugaring createCfToCf(
      AppView<?> appView, AndroidApiLevelCompute apiLevelCompute) {
    if (appView.options().apiModelingOptions().isCfToCfApiOutliningEnabled()) {
      return new CfToCfApiInvokeOutlinerDesugaring(appView, apiLevelCompute);
    }
    return null;
  }

  public static LirToLirApiInvokeOutlinerDesugaring createLirToLir(
      AppView<?> appView,
      AndroidApiLevelCompute apiLevelCompute,
      CfInstructionDesugaringEventConsumer eventConsumer) {
    if (appView.options().apiModelingOptions().isLirToLirApiOutliningEnabled()) {
      return new LirToLirApiInvokeOutlinerDesugaring(appView, apiLevelCompute, eventConsumer);
    }
    return null;
  }

  public RetargetMethodSupplier getRetargetMethodSupplier(
      InstructionKind instructionKind, DexReference reference, ProgramMethod context) {
    ComputedApiLevel computedApiLevel =
        getComputedApiLevelInstructionOnHolderWithMinApi(instructionKind, reference, context);
    if (appView.computedMinApiLevel().isGreaterThanOrEqualTo(computedApiLevel)) {
      return null;
    }
    return (eventConsumer, methodProcessingContext) -> {
      ProgramMethod outlinedMethod =
          ensureOutlineMethod(
              instructionKind, reference, computedApiLevel, context, methodProcessingContext);
      eventConsumer.acceptOutlinedMethod(outlinedMethod, context);
      return outlinedMethod.getReference();
    };
  }

  private ComputedApiLevel getComputedApiLevelInstructionOnHolderWithMinApi(
      InstructionKind instructionKind, DexReference reference, ProgramMethod context) {
    // Some backports will forward to the method/field they backport. For such synthetics run
    // outlining. Other synthetics should not need it. And explicitly not API outlines, as that
    // would cause infinite outlining.
    if (context.getDefinition().isD8R8Synthesized()
        && !appView
            .getSyntheticItems()
            .isSyntheticOfKind(context.getHolderType(), k -> k.BACKPORT_WITH_FORWARDING)) {
      return appView.computedMinApiLevel();
    }
    if (reference == null || !reference.getContextType().isClassType()) {
      return appView.computedMinApiLevel();
    }
    DexClass holder = appView.definitionFor(reference.getContextType());
    if (holder == null) {
      return appView.computedMinApiLevel();
    }
    Pair<DexClass, ComputedApiLevel> classAndApiLevel =
        reference.isDexType()
            ? Pair.create(
                holder,
                apiLevelCompute.computeApiLevelForLibraryReference(
                    reference, ComputedApiLevel.unknown()))
            : AndroidApiLevelUtils.findAndComputeApiLevelForLibraryDefinition(
                appView, appView.appInfoForDesugaring(), holder, reference.asDexMember());
    ComputedApiLevel referenceApiLevel = classAndApiLevel.getSecond();
    if (appView.computedMinApiLevel().isGreaterThanOrEqualTo(referenceApiLevel)
        || isApiLevelLessThanOrEqualToG(referenceApiLevel)
        || referenceApiLevel.isUnknownApiLevel()) {
      return appView.computedMinApiLevel();
    }
    assert referenceApiLevel.isKnownApiLevel();
    DexClass firstLibraryClass = classAndApiLevel.getFirst();
    if (firstLibraryClass == null || !firstLibraryClass.isLibraryClass()) {
      assert false : "When computed a known api level we should always have a library class";
      return appView.computedMinApiLevel();
    }
    // Check if this is already outlined.
    if (isOutlinedAtSameOrLowerLevel(context.getHolder(), referenceApiLevel)) {
      return appView.computedMinApiLevel();
    }
    // Check for protected or package private access flags before outlining.
    if (firstLibraryClass.isInterface() || instructionKind.isTypeInstruction()) {
      return referenceApiLevel;
    } else {
      DexEncodedMember<?, ?> definition =
          simpleLookupInClassHierarchy(
              firstLibraryClass.asLibraryClass(),
              reference.isDexMethod()
                  ? x -> x.lookupMethod(reference.asDexMethod())
                  : x -> x.lookupField(reference.asDexField()));
      return definition != null && definition.isPublic()
          ? referenceApiLevel
          : appView.computedMinApiLevel();
    }
  }

  private DexEncodedMember<?, ?> simpleLookupInClassHierarchy(
      DexLibraryClass holder, Function<DexClass, DexEncodedMember<?, ?>> lookup) {
    DexEncodedMember<?, ?> result = lookup.apply(holder);
    if (result != null) {
      return result;
    }
    TraversalContinuation<DexEncodedMember<?, ?>, ?> traversalResult =
        appView
            .appInfoForDesugaring()
            .traverseSuperClasses(
                holder,
                (ignored, superClass, ignored_) -> {
                  DexEncodedMember<?, ?> definition = lookup.apply(superClass);
                  if (definition != null) {
                    return TraversalContinuation.doBreak(definition);
                  }
                  return TraversalContinuation.doContinue();
                });
    return traversalResult.isBreak() ? traversalResult.asBreak().getValue() : null;
  }

  private ProgramMethod ensureOutlineMethod(
      InstructionKind instructionKind,
      DexReference reference,
      ComputedApiLevel apiLevel,
      ProgramMethod programContext,
      MethodProcessingContext methodProcessingContext) {
    assert reference != null;
    DexClass holder = appView.definitionFor(reference.getContextType());
    assert holder != null;
    return appView
        .getSyntheticItems()
        .createMethod(
            kinds ->
                // We've already checked that the definition the reference is targeting is public
                // when computing the api-level for desugaring. We still have to ensure that the
                // class cannot be merged globally if it is package private.
                holder.isPublic()
                    ? kinds.API_MODEL_OUTLINE
                    : kinds.API_MODEL_OUTLINE_WITHOUT_GLOBAL_MERGING,
            methodProcessingContext.createUniqueContext(),
            appView,
            syntheticMethodBuilder -> {
              syntheticMethodBuilder
                  .setAccessFlags(
                      MethodAccessFlags.builder()
                          .setPublic()
                          .setSynthetic()
                          .setStatic()
                          .setBridge()
                          .build())
                  .setApiLevelForDefinition(apiLevel)
                  .setApiLevelForCode(apiLevel);
              if (instructionKind.isInvoke()) {
                setCodeForInvoke(syntheticMethodBuilder, instructionKind, reference.asDexMethod());
              } else if (instructionKind == InstructionKind.CHECKCAST) {
                setCodeForCheckCast(syntheticMethodBuilder, reference.asDexType());
              } else if (instructionKind == InstructionKind.INSTANCEOF) {
                setCodeForInstanceOf(syntheticMethodBuilder, reference.asDexType());
              } else if (instructionKind == InstructionKind.CONSTCLASS) {
                setCodeForConstClass(syntheticMethodBuilder, reference.asDexType());
              } else {
                assert instructionKind.isFieldInstruction();
                setCodeForFieldInstruction(
                    syntheticMethodBuilder,
                    instructionKind,
                    reference.asDexField(),
                    programContext);
              }
            });
  }

  private void setCodeForInvoke(
      SyntheticMethodBuilder methodBuilder, InstructionKind instructionKind, DexMethod method) {
    DexClass libraryHolder = appView.definitionFor(method.getHolderType());
    assert libraryHolder != null;
    boolean isVirtualMethod =
        instructionKind == InstructionKind.INVOKEINTERFACE
            || instructionKind == InstructionKind.INVOKEVIRTUAL;
    assert verifyLibraryHolderAndInvoke(libraryHolder, method, isVirtualMethod);
    DexProto proto = factory.prependHolderToProtoIf(method, isVirtualMethod);
    methodBuilder
        .setProto(proto)
        .setCode(
            m -> {
              if (isVirtualMethod) {
                return ForwardMethodBuilder.builder(factory)
                    .setVirtualTarget(method, libraryHolder.isInterface())
                    .setNonStaticSource(method)
                    .buildCf();
              } else {
                return ForwardMethodBuilder.builder(factory)
                    .setStaticTarget(method, libraryHolder.isInterface())
                    .setStaticSource(method)
                    .buildCf();
              }
            });
  }

  private void setCodeForFieldInstruction(
      SyntheticMethodBuilder methodBuilder,
      InstructionKind instructionKind,
      DexField field,
      ProgramMethod programContext) {
    DexClass libraryHolder = appView.definitionFor(field.getHolderType());
    assert libraryHolder != null;
    boolean isInstance =
        instructionKind == InstructionKind.IGET || instructionKind == InstructionKind.IPUT;
    // Outlined field references will return a value if getter and only takes arguments if
    // instance or if put or two arguments if both.
    boolean isGet =
        instructionKind == InstructionKind.IGET || instructionKind == InstructionKind.SGET;
    DexType returnType = isGet ? field.getType() : factory.voidType;
    List<DexType> parameters = new ArrayList<>();
    if (isInstance) {
      parameters.add(libraryHolder.getType());
    }
    boolean isPut = !isGet;
    if (isPut) {
      parameters.add(field.getType());
    }
    methodBuilder
        .setProto(factory.createProto(returnType, parameters))
        .setCode(
            m ->
                FieldAccessorBuilder.builder()
                    .applyIf(
                        isInstance,
                        thenConsumer -> thenConsumer.setInstanceField(field),
                        elseConsumer -> elseConsumer.setStaticField(field))
                    .applyIf(
                        isGet, FieldAccessorBuilder::setGetter, FieldAccessorBuilder::setSetter)
                    .setSourceMethod(programContext.getReference())
                    .build());
  }

  private void setCodeForCheckCast(SyntheticMethodBuilder methodBuilder, DexType type) {
    methodBuilder
        .setProto(factory.createProto(type, objectParams))
        .setCode(
            m -> CheckCastSourceCode.create(appView, m.getHolderType(), type).generateCfCode());
  }

  private void setCodeForInstanceOf(SyntheticMethodBuilder methodBuilder, DexType type) {
    methodBuilder
        .setProto(factory.createProto(factory.booleanType, objectParams))
        .setCode(
            m -> InstanceOfSourceCode.create(appView, m.getHolderType(), type).generateCfCode());
  }

  private void setCodeForConstClass(SyntheticMethodBuilder methodBuilder, DexType type) {
    methodBuilder
        .setProto(factory.createProto(factory.classType))
        .setCode(
            m -> ConstClassSourceCode.create(appView, m.getHolderType(), type).generateCfCode());
  }

  private boolean verifyLibraryHolderAndInvoke(
      DexClass libraryHolder, DexMethod apiMethod, boolean isVirtualInvoke) {
    DexEncodedMethod libraryApiMethodDefinition = libraryHolder.lookupMethod(apiMethod);
    return libraryApiMethodDefinition == null
        || libraryApiMethodDefinition.isVirtualMethod() == isVirtualInvoke;
  }

  public enum InstructionKind {
    CHECKCAST,
    CONSTCLASS,
    IGET,
    IPUT,
    INSTANCEOF,
    INVOKEINTERFACE,
    INVOKESTATIC,
    INVOKEVIRTUAL,
    SGET,
    SPUT;

    public boolean isFieldInstruction() {
      return this == IGET || this == IPUT || this == SGET || this == SPUT;
    }

    public boolean isInvoke() {
      return this == INVOKEINTERFACE || this == INVOKESTATIC || this == INVOKEVIRTUAL;
    }

    boolean isTypeInstruction() {
      return this == InstructionKind.CHECKCAST
          || this == InstructionKind.CONSTCLASS
          || this == InstructionKind.INSTANCEOF;
    }
  }

  public interface RetargetMethodSupplier {

    DexMethod getRetargetMethod(
        CfInstructionDesugaringEventConsumer eventConsumer,
        MethodProcessingContext methodProcessingContext);
  }
}
