// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.lightir;

import static com.android.tools.r8.graph.UseRegistry.MethodHandleUse.NOT_ARGUMENT_TO_LAMBDA_METAFACTORY;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexMethodHandle;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.bytecodemetadata.BytecodeMetadataProvider;
import com.android.tools.r8.graph.lens.FieldLookupResult;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.graph.lens.MethodLookupResult;
import com.android.tools.r8.graph.lens.NonIdentityGraphLens;
import com.android.tools.r8.graph.proto.RewrittenPrototypeDescription;
import com.android.tools.r8.horizontalclassmerging.HorizontalClassMergerGraphLens;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InvokeType;
import com.android.tools.r8.ir.conversion.IRToLirFinalizer;
import com.android.tools.r8.ir.conversion.LensCodeRewriter;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;
import com.android.tools.r8.ir.conversion.MethodConversionOptions;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.ir.optimize.AffectedValues;
import com.android.tools.r8.ir.optimize.DeadCodeRemover;
import com.android.tools.r8.lightir.LirBuilder.NameComputationPayload;
import com.android.tools.r8.lightir.LirCode.TryCatchTable;
import com.android.tools.r8.naming.dexitembasedstring.NameComputationInfo;
import com.android.tools.r8.utils.ArrayUtils;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.verticalclassmerging.VerticalClassMergerGraphLens;
import com.google.common.collect.ImmutableSet;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LirLensCodeRewriter<EV> extends LirParsedInstructionCallback<EV> {

  private static final Set<DexMethod> NO_INVOKES_TO_REWRITE = ImmutableSet.of();

  private final AppView<? extends AppInfoWithClassHierarchy> appView;
  private final ProgramMethod context;
  private final DexMethod contextReference;
  private final GraphLens graphLens;
  private final GraphLens codeLens;
  private final LensCodeRewriterUtils helper;

  private final boolean isNonStartupInStartupOutlinerLens;

  private int numberOfInvokeOpcodeChanges = 0;
  private Set<DexMethod> invokesToRewrite = NO_INVOKES_TO_REWRITE;
  private Map<LirConstant, LirConstant> constantPoolMapping = null;

  private boolean hasNonTrivialRewritings = false;

  public LirLensCodeRewriter(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      LirCode<EV> code,
      ProgramMethod context,
      LensCodeRewriterUtils helper) {
    super(code);
    this.appView = appView;
    this.context = context;
    this.contextReference = context.getReference();
    this.graphLens = appView.graphLens();
    this.codeLens = context.getDefinition().getCode().getCodeLens(appView);
    this.helper = helper;
    NonIdentityGraphLens nonStartupInStartupOutlinerLens =
        graphLens.isNonIdentityLens()
            ? graphLens
                .asNonIdentityLens()
                .find(l -> l.isNonStartupInStartupOutlinerLens() || l == codeLens)
            : null;
    this.isNonStartupInStartupOutlinerLens =
        nonStartupInStartupOutlinerLens != null
            && nonStartupInStartupOutlinerLens.isNonStartupInStartupOutlinerLens();
  }

  @Override
  public int getCurrentValueIndex() {
    // We do not need to interpret values.
    return -1;
  }

  public void onTypeReference(DexType type) {
    addRewrittenMapping(type, graphLens.lookupType(type, codeLens));
  }

  public void onFieldReference(DexField field) {
    FieldLookupResult result = graphLens.lookupFieldResult(field, codeLens);
    assert !result.hasReadCastType() || graphLens.isHorizontalClassMergerGraphLens();
    assert !result.hasWriteCastType() || graphLens.isHorizontalClassMergerGraphLens();
    addRewrittenMapping(field, result.getReference());
  }

  public void onCallSiteReference(DexCallSite callSite) {
    addRewrittenMapping(callSite, helper.rewriteCallSite(callSite, context));
  }

  public void onNameComputationPayload(NameComputationPayload nameComputationPayload) {
    addRewrittenMapping(
        nameComputationPayload, nameComputationPayload.rewrittenWithLens(graphLens, codeLens));
  }

  public void onMethodHandleReference(DexMethodHandle methodHandle) {
    addRewrittenMapping(
        methodHandle,
        helper.rewriteDexMethodHandle(methodHandle, NOT_ARGUMENT_TO_LAMBDA_METAFACTORY, context));
  }

  public void onProtoReference(DexProto proto) {
    addRewrittenMapping(proto, helper.rewriteProto(proto));
  }

  private void onInvoke(DexMethod method, InvokeType type, boolean isInterface) {
    MethodLookupResult result = graphLens.lookupMethod(method, contextReference, type, codeLens);
    if (hasPotentialNonTrivialInvokeRewriting(method, type, result)) {
      hasNonTrivialRewritings = true;
      return;
    }
    int opcode = type.getLirOpcode(isInterface);
    DexMethod newMethod = result.getReference();
    InvokeType newType = result.getType();
    boolean newIsInterface = lookupIsInterface(method, opcode, result);
    int newOpcode = newType.getLirOpcode(newIsInterface);
    assert newMethod.getArity() == method.getArity() || newType.isStatic();
    if (newOpcode != opcode) {
      assert type == newType
              || (type.isDirect()
                  && (newType.isInterface() || newType.isStatic() || newType.isVirtual()))
              || (type.isInterface() && (newType.isStatic() || newType.isVirtual()))
              || (type.isSuper() && (newType.isStatic() || newType.isVirtual()))
              || (type.isVirtual() && (newType.isInterface() || newType.isStatic()))
          : type + " -> " + newType;
      numberOfInvokeOpcodeChanges++;
    } else {
      // All non-type dependent mappings are just rewritten in the content pool.
      addRewrittenMethodMapping(method, newMethod);
    }
  }

  private boolean hasPotentialNonTrivialInvokeRewriting(
      DexMethod method, InvokeType type, MethodLookupResult result) {
    if (graphLens.isHorizontalClassMergerGraphLens()) {
      return !result.getPrototypeChanges().isEmpty();
    }
    if (graphLens.isProtoNormalizerLens()) {
      return result.getPrototypeChanges().getArgumentInfoCollection().hasArgumentPermutation();
    }
    VerticalClassMergerGraphLens verticalClassMergerLens = graphLens.asVerticalClassMergerLens();
    if (verticalClassMergerLens != null) {
      if (!result.getPrototypeChanges().isEmpty()) {
        return true;
      }
      for (int argumentIndex = 0;
          argumentIndex < method.getNumberOfArguments(type.isStatic());
          argumentIndex++) {
        DexType argumentType = method.getArgumentType(argumentIndex, type.isStatic());
        if (verticalClassMergerLens.hasInterfaceBeenMergedIntoClass(argumentType)) {
          return true;
        }
      }
    }
    assert result.getPrototypeChanges().isEmpty();
    return false;
  }

  private void addRewrittenMethodMapping(DexMethod method, DexMethod rewrittenMethod) {
    getOrCreateConstantPoolMapping()
        .compute(
            method,
            (unusedKey, otherRewrittenMethod) -> {
              if (otherRewrittenMethod == null || otherRewrittenMethod == rewrittenMethod) {
                return rewrittenMethod;
              } else {
                // Two invokes with the same symbolic method reference but different invoke types
                // are rewritten to two different symbolic method references. Record that the
                // invokes need to be processed.
                if (invokesToRewrite == NO_INVOKES_TO_REWRITE) {
                  invokesToRewrite = new HashSet<>();
                }
                invokesToRewrite.add(method);
                return method;
              }
            });
  }

  private void addRewrittenMapping(LirConstant item, LirConstant rewrittenItem) {
    if (item == rewrittenItem) {
      return;
    }
    LirConstant old = getOrCreateConstantPoolMapping().put(item, rewrittenItem);
    if (old != null && old != rewrittenItem) {
      throw new Unreachable(
          "Unexpected rewriting of item: "
              + item
              + " to two distinct items: "
              + rewrittenItem
              + " and "
              + old);
    }
  }

  private Map<LirConstant, LirConstant> getOrCreateConstantPoolMapping() {
    if (constantPoolMapping == null) {
      constantPoolMapping =
          new IdentityHashMap<>(
              // Avoid using initial capacity larger than the number of actual constants.
              Math.min(getCode().getConstantPool().length, 32));
    }
    return constantPoolMapping;
  }

  @Override
  public void onDexItemBasedConstString(
      DexReference item, NameComputationInfo<?> nameComputationInfo) {
    addRewrittenMapping(item, graphLens.getRenamedReference(item, codeLens));
  }

  @Override
  public void onInstanceGet(DexField field, EV object) {
    onFieldGet(field);
  }

  @Override
  public void onStaticGet(DexField field) {
    onFieldGet(field);
  }

  private void onFieldGet(DexField field) {
    if (hasPotentialNonTrivialFieldGetRewriting(field)) {
      hasNonTrivialRewritings = true;
    }
  }

  private boolean hasPotentialNonTrivialFieldGetRewriting(DexField field) {
    HorizontalClassMergerGraphLens horizontalClassMergerLens =
        graphLens.asHorizontalClassMergerGraphLens();
    if (horizontalClassMergerLens != null) {
      FieldLookupResult result = horizontalClassMergerLens.lookupFieldResult(field, codeLens);
      return result.hasReadCastType();
    }
    return false;
  }

  @Override
  public void onInstancePut(DexField field, EV object, EV value) {
    onFieldPut(field);
  }

  @Override
  public void onStaticPut(DexField field, EV value) {
    onFieldPut(field);
  }

  private void onFieldPut(DexField field) {
    if (hasPotentialNonTrivialFieldPutRewriting(field)) {
      hasNonTrivialRewritings = true;
    }
  }

  private boolean hasPotentialNonTrivialFieldPutRewriting(DexField field) {
    HorizontalClassMergerGraphLens horizontalClassMergerLens =
        graphLens.asHorizontalClassMergerGraphLens();
    if (horizontalClassMergerLens != null) {
      FieldLookupResult result = horizontalClassMergerLens.lookupFieldResult(field, codeLens);
      return result.hasWriteCastType();
    }
    VerticalClassMergerGraphLens verticalClassMergerLens = graphLens.asVerticalClassMergerLens();
    if (verticalClassMergerLens != null
        && verticalClassMergerLens.hasInterfaceBeenMergedIntoClass(field.getType())) {
      return true;
    }
    return false;
  }

  @Override
  public void onInvokeDirect(DexMethod method, List<EV> arguments, boolean isInterface) {
    onInvoke(method, InvokeType.DIRECT, isInterface);
  }

  @Override
  public void onInvokeSuper(DexMethod method, List<EV> arguments, boolean isInterface) {
    onInvoke(method, InvokeType.SUPER, isInterface);
  }

  @Override
  public void onInvokeVirtual(DexMethod method, List<EV> arguments) {
    onInvoke(method, InvokeType.VIRTUAL, false);
  }

  @Override
  public void onInvokeStatic(DexMethod method, List<EV> arguments, boolean isInterface) {
    onInvoke(method, InvokeType.STATIC, isInterface);
  }

  @Override
  public void onInvokeInterface(DexMethod method, List<EV> arguments) {
    onInvoke(method, InvokeType.INTERFACE, true);
  }

  private boolean isInvokeThatMaybeRequiresRewriting(int opcode) {
    assert LirOpcodeUtils.isInvokeMethod(opcode);
    if (!invokesToRewrite.isEmpty()) {
      return true;
    }
    if (codeLens.isIdentityLens() && LirOpcodeUtils.isInvokeMethod(opcode)) {
      return true;
    }
    if (opcode == LirOpcodes.INVOKEVIRTUAL) {
      return true;
    }
    if (opcode == LirOpcodes.INVOKEINTERFACE) {
      return true;
    }
    if (isNonStartupInStartupOutlinerLens) {
      if (LirOpcodeUtils.isInvokeDirect(opcode)) {
        return true;
      }
      if (LirOpcodeUtils.isInvokeInterface(opcode)) {
        return true;
      }
      if (LirOpcodeUtils.isInvokeSuper(opcode)) {
        return true;
      }
      if (LirOpcodeUtils.isInvokeVirtual(opcode)) {
        return true;
      }
    }
    if (graphLens.isVerticalClassMergerLens()) {
      if (opcode == LirOpcodes.INVOKESTATIC_ITF) {
        return true;
      }
      if (opcode == LirOpcodes.INVOKESUPER) {
        return true;
      }
    }
    return false;
  }

  public LirCode<EV> rewrite() {
    if (getCode().hasExplicitCodeLens()) {
      // Only happens when the code is already rewritten, so simply clear the code lens and return.
      assert getCode().getCodeLens(appView) == graphLens;
      LirCode<EV> rewritten = new LirCode<>(getCode());
      assert !rewritten.hasExplicitCodeLens();
      return rewritten;
    }
    if (hasNonTrivialMethodChanges()) {
      return rewriteWithLensCodeRewriter();
    }
    assert !hasNonTrivialRewritings;
    LirCode<EV> rewritten = rewriteConstantPoolAndScanForTypeChanges(getCode());
    if (hasNonTrivialRewritings) {
      return rewriteWithLensCodeRewriter();
    }
    rewritten = rewriteInstructionsWithInvokeTypeChanges(rewritten);
    rewritten = rewriteTryCatchTable(rewritten);
    // In the unusual case where a catch handler has been eliminated as a result of class merging
    // we remove the unreachable blocks.
    if (hasPrunedCatchHandlers(rewritten)) {
      rewritten = removeUnreachableBlocks(rewritten);
    }
    assert !rewritten.hasExplicitCodeLens();
    return rewritten;
  }

  private boolean hasNonTrivialMethodChanges() {
    if (graphLens.isClassMergerLens()) {
      RewrittenPrototypeDescription prototypeChanges =
          graphLens.lookupPrototypeChangesForMethodDefinition(context.getReference(), codeLens);
      if (prototypeChanges.hasExtraParameters()
          || prototypeChanges.getArgumentInfoCollection().isConvertedToStaticMethod()) {
        return true;
      }
      assert prototypeChanges.getArgumentInfoCollection().isEmpty();
      assert !prototypeChanges.hasRewrittenReturnInfo();
      VerticalClassMergerGraphLens verticalClassMergerLens = graphLens.asVerticalClassMergerLens();
      if (verticalClassMergerLens != null) {
        DexMethod previousReference =
            verticalClassMergerLens.getPreviousMethodSignature(contextReference);
        return verticalClassMergerLens.hasInterfaceBeenMergedIntoClass(
            previousReference.getReturnType());
      }
    }
    if (graphLens.isProtoNormalizerLens()) {
      RewrittenPrototypeDescription prototypeChanges =
          graphLens.lookupPrototypeChangesForMethodDefinition(context.getReference(), codeLens);
      assert !prototypeChanges.hasExtraParameters();
      assert !prototypeChanges.hasRewrittenReturnInfo();
      return prototypeChanges.getArgumentInfoCollection().hasArgumentPermutation();
    }
    return false;
  }

  private boolean hasPrunedCatchHandlers(LirCode<EV> rewritten) {
    if (!getCode().hasTryCatchTable()) {
      return false;
    }
    if (!appView.graphLens().isClassMergerLens()) {
      assert !internalHasPrunedCatchHandlers(rewritten);
      return false;
    }
    return internalHasPrunedCatchHandlers(rewritten);
  }

  private boolean internalHasPrunedCatchHandlers(LirCode<EV> rewritten) {
    TryCatchTable tryCatchTable = getCode().getTryCatchTable();
    TryCatchTable rewrittenTryCatchTable = rewritten.getTryCatchTable();
    return tryCatchTable.hasHandlerThatMatches(
        (blockIndex, handlers) ->
            handlers.size() > rewrittenTryCatchTable.getHandlersForBlock(blockIndex).size());
  }

  @SuppressWarnings("unchecked")
  private LirCode<EV> removeUnreachableBlocks(LirCode<EV> rewritten) {
    IRCode code = rewritten.buildIR(context, appView, MethodConversionOptions.forLirPhase(appView));
    AffectedValues affectedValues = code.removeUnreachableBlocks();
    affectedValues.narrowingWithAssumeRemoval(appView, code);
    new DeadCodeRemover(appView).run(code, Timing.empty());
    LirCode<Integer> result =
        new IRToLirFinalizer(appView)
            .finalizeCode(code, BytecodeMetadataProvider.empty(), Timing.empty());
    return (LirCode<EV>) result;
  }

  @SuppressWarnings("unchecked")
  private LirCode<EV> rewriteWithLensCodeRewriter() {
    IRCode code =
        context.buildIR(
            appView,
            MethodConversionOptions.forLirPhase(appView)
                .setFinalizeAfterLensCodeRewriter());
    // MethodProcessor argument is only used by unboxing lenses.
    MethodProcessor methodProcessor = null;
    new LensCodeRewriter(appView).rewrite(code, context, methodProcessor);
    IRToLirFinalizer finalizer = new IRToLirFinalizer(appView);
    LirCode<?> rewritten =
        finalizer.finalizeCode(code, BytecodeMetadataProvider.empty(), Timing.empty());
    return (LirCode<EV>) rewritten;
  }

  private LirCode<EV> rewriteConstantPoolAndScanForTypeChanges(LirCode<EV> code) {
    // The code may need to be rewritten by the lens.
    // First pass scans just the constant pool to see if any types change or if there are any
    // fields/methods that need to be examined.
    boolean hasDexItemBasedConstString = false;
    boolean hasFieldReference = false;
    boolean hasPotentialRewrittenMethod = false;
    for (LirConstant constant : code.getConstantPool()) {
      if (constant instanceof DexType) {
        onTypeReference((DexType) constant);
      } else if (constant instanceof DexField) {
        onFieldReference((DexField) constant);
        hasFieldReference = true;
      } else if (constant instanceof DexCallSite) {
        onCallSiteReference((DexCallSite) constant);
      } else if (constant instanceof NameComputationPayload) {
        onNameComputationPayload((NameComputationPayload) constant);
        hasDexItemBasedConstString = true;
      } else if (constant instanceof DexMethodHandle) {
        onMethodHandleReference((DexMethodHandle) constant);
      } else if (constant instanceof DexProto) {
        onProtoReference((DexProto) constant);
      } else if (!hasPotentialRewrittenMethod && constant instanceof DexMethod) {
        // We might be able to still fast-case this if we can guarantee the method is never
        // rewritten. Say it is an java.lang.Object reference or if the lens can fast-check it.
        hasPotentialRewrittenMethod = true;
      }
    }

    // If there are potential method rewritings then we need to iterate the instructions as the
    // rewriting is instruction-sensitive (i.e., may be dependent on the invoke type).
    boolean hasPotentialNonTrivialFieldAccessRewriting =
        hasFieldReference && graphLens.isClassMergerLens();
    if (hasDexItemBasedConstString
        || hasPotentialNonTrivialFieldAccessRewriting
        || hasPotentialRewrittenMethod) {
      for (LirInstructionView view : code) {
        view.accept(this);
        if (hasNonTrivialRewritings) {
          return null;
        }
      }
    }

    if (constantPoolMapping == null) {
      return code;
    }

    return code.newCodeWithRewrittenConstantPool(
        item -> constantPoolMapping.getOrDefault(item, item));
  }

  private LirCode<EV> rewriteInstructionsWithInvokeTypeChanges(LirCode<EV> code) {
    if (numberOfInvokeOpcodeChanges == 0 && invokesToRewrite.isEmpty()) {
      return code;
    }
    // Build a small map from method refs to index in case the type-dependent methods are already
    // in the constant pool.
    Reference2IntMap<DexMethod> methodIndices = new Reference2IntOpenHashMap<>();
    LirConstant[] rewrittenConstants = code.getConstantPool();
    for (int i = 0, length = rewrittenConstants.length; i < length; i++) {
      LirConstant constant = rewrittenConstants[i];
      if (constant instanceof DexMethod) {
        methodIndices.put((DexMethod) constant, i);
      }
    }

    ByteArrayWriter byteWriter = new ByteArrayWriter();
    LirWriter lirWriter = new LirWriter(byteWriter);
    List<LirConstant> methodsToAppend = new ArrayList<>(numberOfInvokeOpcodeChanges);
    for (LirInstructionView view : code) {
      int opcode = view.getOpcode();
      // Instructions that do not have an invoke-type change are just mapped via identity.
      if (LirOpcodes.isOneByteInstruction(opcode)) {
        lirWriter.writeOneByteInstruction(opcode);
        continue;
      }
      if (!LirOpcodeUtils.isInvokeMethod(opcode) || !isInvokeThatMaybeRequiresRewriting(opcode)) {
        int size = view.getRemainingOperandSizeInBytes();
        lirWriter.writeInstruction(opcode, size);
        while (size-- > 0) {
          lirWriter.writeOperand(view.getNextU1());
        }
        continue;
      }
      // If this is either (i) an invoke with a type change or (ii) an invoke to a method M where
      // there exists another invoke in the current method to M, and the two invokes are mapped to
      // two different methods (one-to-many constant pool mapping), then the method is mapped with
      // the instruction updated to the new type. The constant pool is amended with the mapped
      // method if needed.
      InvokeType type = LirOpcodeUtils.getInvokeType(opcode);
      int constantIndex = view.getNextConstantOperand();
      DexMethod method = (DexMethod) code.getConstantItem(constantIndex);
      MethodLookupResult result =
          graphLens.lookupMethod(method, context.getReference(), type, codeLens);
      boolean newIsInterface = lookupIsInterface(method, opcode, result);
      InvokeType newType = result.getType();
      int newOpcode = newType.getLirOpcode(newIsInterface);
      if (newOpcode != opcode || invokesToRewrite.contains(method)) {
        constantIndex =
            methodIndices.computeIfAbsent(
                result.getReference(),
                ref -> {
                  methodsToAppend.add(ref);
                  return rewrittenConstants.length + methodsToAppend.size() - 1;
                });
        numberOfInvokeOpcodeChanges -= BooleanUtils.intValue(newOpcode != opcode);
      }
      int constantIndexSize = ByteUtils.intEncodingSize(constantIndex);
      int remainingSize = view.getRemainingOperandSizeInBytes();
      lirWriter.writeInstruction(newOpcode, constantIndexSize + remainingSize);
      ByteUtils.writeEncodedInt(constantIndex, lirWriter::writeOperand);
      while (remainingSize-- > 0) {
        lirWriter.writeOperand(view.getNextU1());
      }
    }
    assert numberOfInvokeOpcodeChanges == 0;
    // Note that since we assume 'null' in the mapping is identity this may end up with a stale
    // reference to a no longer used method. That is not an issue as it will be pruned when
    // building IR again, it is just a small and size overhead.
    return code.copyWithNewConstantsAndInstructions(
        ArrayUtils.appendElements(code.getConstantPool(), methodsToAppend),
        byteWriter.toByteArray());
  }

  // TODO(b/157111832): This should be part of the graph lens lookup result.
  private boolean lookupIsInterface(DexMethod method, int opcode, MethodLookupResult result) {
    boolean wasInterface = LirOpcodeUtils.getInterfaceBitFromInvokeOpcode(opcode);
    // Update interface bit after member rebinding.
    if (codeLens.isIdentityLens() && method.isNotIdenticalTo(result.getReference())) {
      return result.getReference().getHolderType().isInterfaceOrDefault(appView, wasInterface);
    }
    // Update interface bit after vertical class merging.
    VerticalClassMergerGraphLens verticalClassMergerLens = graphLens.asVerticalClassMergerLens();
    if (verticalClassMergerLens != null
        && verticalClassMergerLens.hasInterfaceBeenMergedIntoClass(method.getHolderType())) {
      return result.getReference().getHolderType().isInterfaceOrDefault(appView, wasInterface);
    }
    return wasInterface;
  }

  private LirCode<EV> rewriteTryCatchTable(LirCode<EV> code) {
    TryCatchTable tryCatchTable = code.getTryCatchTable();
    if (tryCatchTable == null) {
      return code;
    }
    TryCatchTable newTryCatchTable = tryCatchTable.rewriteWithLens(graphLens, codeLens);
    return code.newCodeWithRewrittenTryCatchTable(newTryCatchTable);
  }
}
