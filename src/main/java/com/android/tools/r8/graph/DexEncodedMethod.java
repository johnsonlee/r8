// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import static com.android.tools.r8.graph.DexEncodedMethod.CompilationState.PROCESSED_INLINING_CANDIDATE_ANY;
import static com.android.tools.r8.graph.DexEncodedMethod.CompilationState.PROCESSED_INLINING_CANDIDATE_SAME_CLASS;
import static com.android.tools.r8.graph.DexEncodedMethod.CompilationState.PROCESSED_INLINING_CANDIDATE_SAME_NEST;
import static com.android.tools.r8.graph.DexEncodedMethod.CompilationState.PROCESSED_INLINING_CANDIDATE_SAME_PACKAGE;
import static com.android.tools.r8.graph.DexEncodedMethod.CompilationState.PROCESSED_INLINING_CANDIDATE_SUBCLASS;
import static com.android.tools.r8.graph.DexEncodedMethod.CompilationState.PROCESSED_NOT_INLINING_CANDIDATE;
import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;
import static com.android.tools.r8.ir.analysis.type.Nullability.definitelyNotNull;
import static com.android.tools.r8.kotlin.KotlinMetadataUtils.getNoKotlinInfo;
import static com.android.tools.r8.utils.ConsumerUtils.emptyConsumer;
import static java.util.Objects.requireNonNull;

import com.android.tools.r8.androidapi.ComputedApiLevel;
import com.android.tools.r8.cf.CfVersion;
import com.android.tools.r8.cf.code.CfConstNumber;
import com.android.tools.r8.cf.code.CfConstString;
import com.android.tools.r8.cf.code.CfInstanceOf;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfLoad;
import com.android.tools.r8.cf.code.CfLogicalBinop;
import com.android.tools.r8.cf.code.CfNew;
import com.android.tools.r8.cf.code.CfReturn;
import com.android.tools.r8.cf.code.CfStackInstruction;
import com.android.tools.r8.cf.code.CfStackInstruction.Opcode;
import com.android.tools.r8.cf.code.CfStore;
import com.android.tools.r8.cf.code.CfThrow;
import com.android.tools.r8.dex.MixedSectionCollection;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexAnnotation.AnnotatedKind;
import com.android.tools.r8.graph.GenericSignature.MethodTypeSignature;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.graph.proto.RewrittenPrototypeDescription;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.NumericType;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.NestUtils;
import com.android.tools.r8.ir.optimize.info.DefaultMethodOptimizationInfo;
import com.android.tools.r8.ir.optimize.info.MethodOptimizationInfo;
import com.android.tools.r8.ir.optimize.info.MethodOptimizationInfoFixer;
import com.android.tools.r8.ir.optimize.info.MutableMethodOptimizationInfo;
import com.android.tools.r8.ir.optimize.inliner.WhyAreYouNotInliningReporter;
import com.android.tools.r8.ir.synthetic.ForwardMethodBuilder;
import com.android.tools.r8.kotlin.KotlinMethodLevelInfo;
import com.android.tools.r8.lightir.LirBuilder;
import com.android.tools.r8.lightir.LirCode;
import com.android.tools.r8.lightir.LirEncodingStrategy;
import com.android.tools.r8.lightir.LirStrategy;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.naming.MemberNaming.Signature;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.BooleanBox;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.ConsumerUtils;
import com.android.tools.r8.utils.ObjectUtils;
import com.android.tools.r8.utils.OptionalBool;
import com.android.tools.r8.utils.RetracerForCodePrinting;
import com.android.tools.r8.utils.structural.CompareToVisitor;
import com.android.tools.r8.utils.structural.HashingVisitor;
import com.android.tools.r8.utils.structural.Ordered;
import com.android.tools.r8.utils.structural.StructuralItem;
import com.android.tools.r8.utils.structural.StructuralMapping;
import com.android.tools.r8.utils.structural.StructuralSpecification;
import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.ints.Int2ReferenceArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import org.objectweb.asm.Opcodes;

public class DexEncodedMethod extends DexEncodedMember<DexEncodedMethod, DexMethod>
    implements StructuralItem<DexEncodedMethod> {

  public static final String CONFIGURATION_DEBUGGING_PREFIX = "Shaking error: Missing method in ";

  /**
   * Encodes the processing state of a method.
   *
   * <p>We also use this enum to encode under what constraints a method may be inlined.
   */
  // TODO(b/128967328): Need to extend this to a state with the context.
  public enum CompilationState {
    /**
     * Has not been processed, yet.
     */
    NOT_PROCESSED,
    /**
     * Has been processed but cannot be inlined due to instructions that are not supported.
     */
    PROCESSED_NOT_INLINING_CANDIDATE,
    /**
     * Code only contains instructions that access public entities and can this be inlined into any
     * context.
     */
    PROCESSED_INLINING_CANDIDATE_ANY,
    /**
     * Code also contains instructions that access protected entities that reside in a different
     * package and hence require subclass relationship to be visible.
     */
    PROCESSED_INLINING_CANDIDATE_SUBCLASS,
    /**
     * Code contains instructions that reference package private entities or protected entities from
     * the same package.
     */
    PROCESSED_INLINING_CANDIDATE_SAME_PACKAGE,
    /** Code contains instructions that reference private entities. */
    PROCESSED_INLINING_CANDIDATE_SAME_NEST,
    /** Code contains invoke super */
    PROCESSED_INLINING_CANDIDATE_SAME_CLASS
  }

  public static final DexEncodedMethod[] EMPTY_ARRAY = {};
  public static final DexEncodedMethod SENTINEL =
      new DexEncodedMethod(
          null,
          MethodAccessFlags.fromDexAccessFlags(0),
          MethodTypeSignature.noSignature(),
          DexAnnotationSet.empty(),
          ParameterAnnotationsList.empty(),
          null,
          null,
          false,
          ComputedApiLevel.notSet(),
          ComputedApiLevel.notSet(),
          null,
          DefaultMethodOptimizationInfo.getInstance(),
          false);
  public static final Int2ReferenceMap<DebugLocalInfo> NO_PARAMETER_INFO =
      new Int2ReferenceArrayMap<>(0);

  public final MethodAccessFlags accessFlags;
  public final boolean deprecated;
  public ParameterAnnotationsList parameterAnnotationsList;
  private Code code;
  private DexMethod pendingInlineFrame;
  // TODO(b/128967328): towards finer-grained inlining constraints,
  //   we need to maintain a set of states with (potentially different) contexts.
  private CompilationState compilationState = CompilationState.NOT_PROCESSED;
  private MethodOptimizationInfo optimizationInfo;
  private CfVersion classFileVersion;
  /** The apiLevelForCode describes the api level needed for knowing all references in the code */
  private ComputedApiLevel apiLevelForCode;

  private KotlinMethodLevelInfo kotlinMemberInfo = getNoKotlinInfo();
  /** Generic signature information if the attribute is present in the input */
  private MethodTypeSignature genericSignature;

  private OptionalBool isLibraryMethodOverride = OptionalBool.unknown();

  private Int2ReferenceMap<DebugLocalInfo> parameterInfo = NO_PARAMETER_INFO;

  // This flag indicates the current instance is no longer up-to-date as another instance was
  // created based on this. Any further (public) operations on this instance will raise an error
  // to catch potential bugs due to the inconsistency (e.g., http://b/111893131)
  // Any newly added `public` method should check if `this` instance is obsolete.
  private boolean obsolete = false;

  private void checkIfObsolete() {
    assert !obsolete;
  }

  public boolean isObsolete() {
    // Do not be cheating. This util can be used only if you're going to do appropriate action,
    // e.g., using GraphLens#mapDexEncodedMethod to look up the correct, up-to-date instance.
    return obsolete;
  }

  public void setObsolete() {
    // By assigning an Exception, you can see when/how this instance becomes obsolete v.s.
    // when/how such obsolete instance is used.
    obsolete = true;
  }

  @Override
  public MethodAccessFlags getAccessFlags() {
    return accessFlags;
  }

  public int getArgumentIndexFromParameterIndex(int parameterIndex) {
    return parameterIndex + getFirstNonReceiverArgumentIndex();
  }

  public DexType getArgumentType(int argumentIndex) {
    return getReference().getArgumentType(argumentIndex, isStatic());
  }

  public int getFirstNonReceiverArgumentIndex() {
    return isStatic() ? 0 : 1;
  }

  public int getNumberOfArguments() {
    return getReference().getNumberOfArguments(isStatic());
  }

  public CompilationState getCompilationState() {
    return compilationState;
  }

  private DexEncodedMethod(
      DexMethod method,
      MethodAccessFlags accessFlags,
      MethodTypeSignature genericSignature,
      DexAnnotationSet annotations,
      ParameterAnnotationsList parameterAnnotationsList,
      Code code,
      DexMethod pendingInlineFrame,
      boolean d8R8Synthesized,
      ComputedApiLevel apiLevelForDefinition,
      ComputedApiLevel apiLevelForCode,
      CfVersion classFileVersion,
      MethodOptimizationInfo optimizationInfo,
      boolean deprecated) {
    super(method, annotations, d8R8Synthesized, apiLevelForDefinition);
    this.accessFlags = accessFlags;
    this.deprecated = deprecated;
    this.genericSignature = genericSignature;
    this.parameterAnnotationsList = parameterAnnotationsList;
    this.code = code;
    this.pendingInlineFrame = pendingInlineFrame;
    this.classFileVersion = classFileVersion;
    this.apiLevelForCode = apiLevelForCode;
    this.optimizationInfo = requireNonNull(optimizationInfo);
    assert accessFlags != null;
    assert code == null || !shouldNotHaveCode();
    assert supportsPendingInlineFrame() || !hasPendingInlineFrame();
    assert parameterAnnotationsList != null;
    assert apiLevelForDefinition != null;
    assert apiLevelForCode != null;
  }

  public static DexEncodedMethod toMethodDefinitionOrNull(DexClassAndMethod method) {
    return method != null ? method.getDefinition() : null;
  }

  public boolean isDeprecated() {
    return deprecated;
  }

  @Override
  public DexEncodedMethod self() {
    return this;
  }

  @Override
  public StructuralMapping<DexEncodedMethod> getStructuralMapping() {
    return DexEncodedMethod::syntheticSpecify;
  }

  // Visitor specifying the structure of the method with respect to its "synthetic" content.
  // TODO(b/171867022): Generalize this so that it determines any method in full.
  private static void syntheticSpecify(StructuralSpecification<DexEncodedMethod, ?> spec) {
    spec.withItem(DexEncodedMethod::getReference)
        .withItem(DexEncodedMethod::getAccessFlags)
        .withItem(DexDefinition::annotations)
        .withItem(m -> m.parameterAnnotationsList)
        .withNullableItem(m -> m.classFileVersion)
        .withBool(DexEncodedMember::isD8R8Synthesized)
        .withNullableItem(m -> m.pendingInlineFrame)
        // TODO(b/171867022): Make signatures structural and include it in the definition.
        .withAssert(m -> m.genericSignature.hasNoSignature())
        .withCustomItem(
            DexEncodedMethod::getCode,
            DexEncodedMethod::compareCodeObject,
            DexEncodedMethod::hashCodeObject);
  }

  @SuppressWarnings("ReferenceEquality")
  private static int compareCodeObject(Code code1, Code code2, CompareToVisitor visitor) {
    if (code1 == code2) {
      return 0;
    }
    if (code1 == null || code2 == null) {
      // This call is to remain order consistent with the 'withNullableItem' code.
      return visitor.visitBool(code1 != null, code2 != null);
    }
    if (code1.isLirCode() && code2.isLirCode()) {
      return code1.asLirCode().acceptCompareTo(code2.asLirCode(), visitor);
    }
    if (code1.isCfWritableCode() && code2.isCfWritableCode()) {
      return code1.asCfWritableCode().acceptCompareTo(code2.asCfWritableCode(), visitor);
    }
    if (code1.isDexWritableCode() && code2.isDexWritableCode()) {
      return code1.asDexWritableCode().acceptCompareTo(code2.asDexWritableCode(), visitor);
    }
    throw new Unreachable(
        "Unexpected attempt to compare incompatible synthetic objects: " + code1 + " and " + code2);
  }

  private static void hashCodeObject(Code code, HashingVisitor visitor) {
    if (code == null) {
      // The null code does not contribute to the hash. This should be distinct from non-null as
      // code otherwise has a non-empty instruction payload.
    } else if (code.isLirCode()) {
      code.asLirCode().acceptHashing(visitor);
    } else if (code.isCfWritableCode()) {
      code.asCfWritableCode().acceptHashing(visitor);
    } else {
      assert code.isDexWritableCode();
      code.asDexWritableCode().acceptHashing(visitor);
    }
  }

  public DexProto getProto() {
    return getReference().getProto();
  }

  public DexType getParameter(int index) {
    return getReference().getParameter(index);
  }

  public DexTypeList getParameters() {
    return getReference().getParameters();
  }

  public int getParameterIndexFromArgumentIndex(int argumentIndex) {
    assert argumentIndex >= getFirstNonReceiverArgumentIndex();
    return argumentIndex - getFirstNonReceiverArgumentIndex();
  }

  public DexType getReturnType() {
    return getReference().getReturnType();
  }

  public DexMethodSignature getSignature() {
    return DexMethodSignature.create(getReference());
  }

  public DexType returnType() {
    return getReference().proto.returnType;
  }

  public OptionalBool isLibraryMethodOverride() {
    return isNonPrivateVirtualMethod() ? isLibraryMethodOverride : OptionalBool.FALSE;
  }

  public void setLibraryMethodOverride(OptionalBool isLibraryMethodOverride) {
    assert isNonPrivateVirtualMethod();
    assert !isLibraryMethodOverride.isUnknown();
    assert isLibraryMethodOverride.isPossiblyFalse()
            || this.isLibraryMethodOverride.isPossiblyTrue()
        : "Method `"
            + getReference().toSourceString()
            + "` went from not overriding a library method to overriding a library method";
    assert isLibraryMethodOverride.isPossiblyTrue()
            || this.isLibraryMethodOverride.isPossiblyFalse()
        : "Method `"
            + getReference().toSourceString()
            + "` went from overriding a library method to not overriding a library method";
    this.isLibraryMethodOverride = isLibraryMethodOverride;
  }

  public boolean isProgramMethod(DexDefinitionSupplier definitions) {
    if (getReference().holder.isClassType()) {
      DexClass clazz = definitions.definitionFor(getReference().holder);
      return clazz != null && clazz.isProgramClass();
    }
    return false;
  }

  @Override
  public ProgramMethod asProgramMember(DexDefinitionSupplier definitions) {
    return asProgramMethod(definitions);
  }

  @Override
  public <T> T apply(
      Function<DexEncodedField, T> fieldConsumer, Function<DexEncodedMethod, T> methodConsumer) {
    return methodConsumer.apply(this);
  }

  public DexClassAndMethod asDexClassAndMethod(DexDefinitionSupplier definitions) {
    assert getReference().holder.isClassType();
    DexClass clazz = definitions.definitionForHolder(getReference());
    if (clazz != null) {
      return DexClassAndMethod.create(clazz, this);
    }
    return null;
  }

  @SuppressWarnings("ReferenceEquality")
  public ProgramMethod asProgramMethod(DexProgramClass holder) {
    assert getHolderType() == holder.getType();
    return new ProgramMethod(holder, this);
  }

  public ProgramMethod asProgramMethod(DexDefinitionSupplier definitions) {
    assert getReference().holder.isClassType();
    DexProgramClass clazz = asProgramClassOrNull(definitions.definitionForHolder(getReference()));
    if (clazz != null) {
      return new ProgramMethod(clazz, this);
    }
    return null;
  }

  public static DexClassAndMethod asDexClassAndMethodOrNull(
      DexEncodedMethod method, DexDefinitionSupplier definitions) {
    return method != null ? method.asDexClassAndMethod(definitions) : null;
  }

  public static ProgramMethod asProgramMethodOrNull(
      DexEncodedMethod method, DexProgramClass holder) {
    return method != null ? method.asProgramMethod(holder) : null;
  }

  public static ProgramMethod asProgramMethodOrNull(
      DexEncodedMethod method, DexDefinitionSupplier definitions) {
    return method != null ? method.asProgramMethod(definitions) : null;
  }

  public boolean isProcessed() {
    checkIfObsolete();
    return compilationState != CompilationState.NOT_PROCESSED;
  }

  public boolean isAbstract() {
    return accessFlags.isAbstract();
  }

  public boolean isBridge() {
    return accessFlags.isBridge();
  }

  public boolean isFinal() {
    return accessFlags.isFinal();
  }

  public boolean isNative() {
    return accessFlags.isNative();
  }

  public boolean isSynchronized() {
    return accessFlags.isSynchronized();
  }

  public boolean isVarargs() {
    return accessFlags.isVarargs();
  }

  public boolean isInitializer() {
    checkIfObsolete();
    return isInstanceInitializer() || isClassInitializer();
  }

  public boolean isInstanceInitializer() {
    checkIfObsolete();
    return accessFlags.isConstructor() && !accessFlags.isStatic();
  }

  public boolean isDefaultInstanceInitializer() {
    checkIfObsolete();
    return isInstanceInitializer() && getParameters().isEmpty();
  }

  public boolean isClassInitializer() {
    checkIfObsolete();
    return accessFlags.isConstructor() && accessFlags.isStatic();
  }

  public boolean isDefaultMethod() {
    // Assumes holder is an interface
    return !isStatic() && !isAbstract() && !isPrivateMethod() && !isInstanceInitializer();
  }

  /**
   * Returns true if this method can be invoked via invoke-virtual/interface.
   *
   * <p>Note that also private methods can be the target of a virtual invoke. In such cases, the
   * validity of the invoke depends on the access granted to the call site.
   */
  public boolean isVirtualMethod() {
    checkIfObsolete();
    return !accessFlags.isStatic() && !accessFlags.isConstructor();
  }

  public boolean isNonPrivateVirtualMethod() {
    checkIfObsolete();
    return !isPrivateMethod() && isVirtualMethod();
  }

  public boolean isNonStaticPrivateMethod() {
    checkIfObsolete();
    return isInstance() && isPrivate();
  }

  /**
   * Returns true if this method can be invoked via invoke-virtual, invoke-super or invoke-interface
   * and is non-abstract.
   */
  public boolean isNonAbstractVirtualMethod() {
    checkIfObsolete();
    return isVirtualMethod() && !accessFlags.isAbstract();
  }

  public boolean isNonAbstractNonNativeMethod() {
    checkIfObsolete();
    return !accessFlags.isAbstract() && !accessFlags.isNative();
  }

  public boolean isPublicized() {
    checkIfObsolete();
    return accessFlags.isPromotedToPublic();
  }

  public boolean isPublicMethod() {
    checkIfObsolete();
    return accessFlags.isPublic();
  }

  public boolean isProtectedMethod() {
    checkIfObsolete();
    return accessFlags.isProtected();
  }

  public boolean isPrivateMethod() {
    checkIfObsolete();
    return accessFlags.isPrivate();
  }

  /**
   * Returns true if this method can be invoked via invoke-direct.
   */
  public boolean isDirectMethod() {
    checkIfObsolete();
    return (accessFlags.isPrivate() || accessFlags.isConstructor()) && !accessFlags.isStatic();
  }

  public boolean isInstance() {
    return !isStatic();
  }

  @Override
  public boolean isStatic() {
    checkIfObsolete();
    return accessFlags.isStatic();
  }

  @Override
  @SuppressWarnings("ReferenceEquality")
  public boolean isStaticMember() {
    checkIfObsolete();
    return isStatic();
  }

  @SuppressWarnings("ReferenceEquality")
  public boolean isAtLeastAsVisibleAsOtherInSameHierarchy(
      DexEncodedMethod other, AppView<? extends AppInfoWithClassHierarchy> appView) {
    assert getReference().getProto() == other.getReference().getProto();
    assert appView.isSubtype(getHolderType(), other.getHolderType()).isTrue()
        || appView.isSubtype(other.getHolderType(), getHolderType()).isTrue();
    AccessFlags<MethodAccessFlags> accessFlags = getAccessFlags();
    AccessFlags<?> otherAccessFlags = other.getAccessFlags();
    if (accessFlags.getVisibilityOrdinal() < otherAccessFlags.getVisibilityOrdinal()) {
      return false;
    } else if (accessFlags.isPrivate()) {
      return getHolderType() == other.getHolderType();
    } else if (accessFlags.isPublic()) {
      return true;
    } else {
      assert accessFlags.isPackagePrivate() || accessFlags.isProtected();
      return getHolderType().getPackageName().equals(other.getHolderType().getPackageName());
    }
  }

  @SuppressWarnings("ReferenceEquality")
  public boolean isSameVisibility(DexEncodedMethod other) {
    AccessFlags<MethodAccessFlags> accessFlags = getAccessFlags();
    if (accessFlags.getVisibilityOrdinal() != other.getAccessFlags().getVisibilityOrdinal()) {
      return false;
    }
    if (accessFlags.isPublic()) {
      return true;
    }
    if (accessFlags.isPrivate()) {
      return getHolderType() == other.getHolderType();
    }
    assert accessFlags.isPackagePrivateOrProtected();
    return getHolderType().getPackageName().equals(other.getHolderType().getPackageName());
  }

  /**
   * Returns true if this method is synthetic.
   */
  public boolean isSyntheticMethod() {
    checkIfObsolete();
    return accessFlags.isSynthetic();
  }

  /** Returns true if this method is synthetic and a bridge method. */
  public boolean isSyntheticBridgeMethod() {
    checkIfObsolete();
    return accessFlags.isSynthetic() && accessFlags.isBridge();
  }

  public boolean belongsToDirectPool() {
    return accessFlags.belongsToDirectPool();
  }

  public boolean belongsToVirtualPool() {
    return accessFlags.belongsToVirtualPool();
  }

  @Override
  public KotlinMethodLevelInfo getKotlinInfo() {
    return kotlinMemberInfo;
  }

  @Override
  public void clearKotlinInfo() {
    kotlinMemberInfo = getNoKotlinInfo();
  }

  public void setKotlinMemberInfo(KotlinMethodLevelInfo kotlinMemberInfo) {
    // Structure-changing optimizations, such as (vertical|horizontal) merger or inliner, that
    // may need to redefine what this method is. Simply, the method merged/inlined by optimization
    // is no longer what it used to be; it's safe to ignore metadata of that method, since it is
    // not asked to be kept. But, the nature of the current one is not changed, hence keeping the
    // original one as-is.
    // E.g., originally the current method is extension function, and new information, say, from
    // an inlinee, is extension property. Being merged here means:
    //   * That inlinee is not an extension property anymore. We can ignore metadata from it.
    //   * This method is still an extension function, just with a bigger body.
    assert this.kotlinMemberInfo == getNoKotlinInfo();
    this.kotlinMemberInfo = kotlinMemberInfo;
  }

  public boolean isOnlyInlinedIntoNestMembers() {
    return compilationState == PROCESSED_INLINING_CANDIDATE_SAME_NEST;
  }

  public boolean isInliningCandidate(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      ProgramMethod context,
      WhyAreYouNotInliningReporter whyAreYouNotInliningReporter) {
    checkIfObsolete();
    AppInfoWithClassHierarchy appInfo = appView.appInfo();
    switch (compilationState) {
      case PROCESSED_INLINING_CANDIDATE_ANY:
        return true;

      case PROCESSED_INLINING_CANDIDATE_SUBCLASS:
        if (appInfo.isSubtype(context.getHolderType(), getHolderType())) {
          return true;
        }
        whyAreYouNotInliningReporter.reportCallerNotSubtype();
        return false;

      case PROCESSED_INLINING_CANDIDATE_SAME_PACKAGE:
        if (context.isSamePackage(getHolderType())) {
          return true;
        }
        whyAreYouNotInliningReporter.reportCallerNotSamePackage();
        return false;

      case PROCESSED_INLINING_CANDIDATE_SAME_NEST:
        if (NestUtils.sameNest(context.getHolderType(), getHolderType(), appInfo)) {
          return true;
        }
        whyAreYouNotInliningReporter.reportCallerNotSameNest();
        return false;

      case PROCESSED_INLINING_CANDIDATE_SAME_CLASS:
        if (context.getHolderType().isIdenticalTo(getHolderType())) {
          return true;
        }
        whyAreYouNotInliningReporter.reportCallerNotSameClass();
        return false;

      case PROCESSED_NOT_INLINING_CANDIDATE:
        whyAreYouNotInliningReporter.reportInlineeNotInliningCandidate();
        return false;

      case NOT_PROCESSED:
        whyAreYouNotInliningReporter.reportInlineeNotProcessed();
        return false;

      default:
        throw new Unreachable("Unexpected compilation state: " + compilationState);
    }
  }

  public boolean markProcessed(ConstraintWithTarget state) {
    checkIfObsolete();
    CompilationState prevCompilationState = compilationState;
    switch (state.constraint) {
      case ALWAYS:
        compilationState = PROCESSED_INLINING_CANDIDATE_ANY;
        break;
      case SUBCLASS:
        compilationState = PROCESSED_INLINING_CANDIDATE_SUBCLASS;
        break;
      case PACKAGE:
        compilationState = PROCESSED_INLINING_CANDIDATE_SAME_PACKAGE;
        break;
      case SAMENEST:
        compilationState = PROCESSED_INLINING_CANDIDATE_SAME_NEST;
        break;
      case SAMECLASS:
        compilationState = PROCESSED_INLINING_CANDIDATE_SAME_CLASS;
        break;
      case NEVER:
        compilationState = PROCESSED_NOT_INLINING_CANDIDATE;
        break;
    }
    return prevCompilationState != compilationState;
  }

  public void markNotProcessed() {
    checkIfObsolete();
    compilationState = CompilationState.NOT_PROCESSED;
  }

  public void setCode(Code code, Int2ReferenceMap<DebugLocalInfo> parameterInfo) {
    checkIfObsolete();
    if (code != null && !code.supportsPendingInlineFrame() && hasPendingInlineFrame()) {
      // If the method is set abstract, or the code supports a pending inline frame, then it simply
      // remains in place. Note that if we start supporting pending frames on instruction-code
      // objects this implicit keeping of the pending frame would not be correct.
      assert verifyPendingInlinePositionFoundInCode(code);
      pendingInlineFrame = null;
    }
    this.code = code;
    this.parameterInfo = parameterInfo;
  }

  private boolean verifyPendingInlinePositionFoundInCode(Code code) {
    BooleanBox foundInlineFrame = new BooleanBox(false);
    code.forEachPosition(
        getReference(),
        isDexEncodedMethod(),
        position -> {
          if (foundInlineFrame.isTrue()) {
            return;
          }
          do {
            if (!position.isD8R8Synthesized() && position.getMethod().equals(pendingInlineFrame)) {
              foundInlineFrame.set(true);
              break;
            }
            position = position.getCallerPosition();
          } while (position != null);
        });
    assert foundInlineFrame.isTrue();
    return true;
  }

  public void unsetCode() {
    checkIfObsolete();
    code = null;
  }

  public boolean hasParameterInfo() {
    return parameterInfo != NO_PARAMETER_INFO;
  }

  public Int2ReferenceMap<DebugLocalInfo> getParameterInfo() {
    return parameterInfo;
  }

  @Override
  public String toString() {
    return toSourceString();
  }

  @Override
  protected void collectMixedSectionItems(MixedSectionCollection mixedItems) {
    mixedItems.visit(this);
  }

  public void collectMixedSectionItemsWithCodeMapping(MixedSectionCollection mixedItems) {
    DexWritableCode code = getDexWritableCodeOrNull();
    if (code != null && mixedItems.add(this, code)) {
      code.collectMixedSectionItems(mixedItems);
    }
    annotations().collectMixedSectionItems(mixedItems);
    parameterAnnotationsList.collectMixedSectionItems(mixedItems);
  }

  public boolean shouldNotHaveCode() {
    return accessFlags.isAbstract() || accessFlags.isNative();
  }

  public boolean supportsPendingInlineFrame() {
    return code == null || code.supportsPendingInlineFrame();
  }

  public boolean hasPendingInlineFrame() {
    return pendingInlineFrame != null;
  }

  public DexMethod getPendingInlineFrame() {
    return pendingInlineFrame;
  }

  public Position getAndClearPendingInlineFrameAsPosition() {
    Position position = getPendingInlineFrameAsPosition();
    pendingInlineFrame = null;
    return position;
  }

  public Position getPendingInlineFrameAsPosition() {
    if (!hasPendingInlineFrame()) {
      return null;
    }
    // This method is the caller method and must be synthetic.
    assert isD8R8Synthesized();
    return getPendingInlineFrameAsPositionWithCallerPosition(
        Position.SyntheticPosition.builder()
            .setMethod(getReference())
            .setIsD8R8Synthesized(isD8R8Synthesized())
            .setLine(0)
            .build());
  }

  public Position getPendingInlineFrameAsPositionWithCallerPosition(Position callerPosition) {
    assert callerPosition != null;
    if (!hasPendingInlineFrame()) {
      return callerPosition;
    }
    return Position.SyntheticPosition.builder()
        .setLine(0)
        .setMethod(getPendingInlineFrame())
        .setCallerPosition(callerPosition)
        .build();
  }

  public boolean hasCode() {
    return code != null;
  }

  public Code getCode() {
    checkIfObsolete();
    return code;
  }

  public CfVersion getClassFileVersion() {
    checkIfObsolete();
    assert classFileVersion != null;
    return classFileVersion;
  }

  public CfVersion getClassFileVersionOrElse(CfVersion defaultValue) {
    return hasClassFileVersion() ? getClassFileVersion() : defaultValue;
  }

  public boolean hasClassFileVersion() {
    checkIfObsolete();
    return classFileVersion != null;
  }

  public void upgradeClassFileVersion(CfVersion version) {
    checkIfObsolete();
    assert version != null;
    classFileVersion = Ordered.maxIgnoreNull(classFileVersion, version);
  }

  public void downgradeClassFileVersion(CfVersion version) {
    checkIfObsolete();
    assert version != null;
    classFileVersion = Ordered.minIgnoreNull(classFileVersion, version);
  }

  public String qualifiedName() {
    checkIfObsolete();
    return getReference().qualifiedName();
  }

  public String descriptor() {
    checkIfObsolete();
    return descriptor(NamingLens.getIdentityLens());
  }

  public String descriptor(NamingLens namingLens) {
    checkIfObsolete();
    StringBuilder builder = new StringBuilder();
    builder.append("(");
    for (DexType type : getReference().proto.parameters.values) {
      builder.append(namingLens.lookupDescriptor(type).toString());
    }
    builder.append(")");
    builder.append(namingLens.lookupDescriptor(getReference().proto.returnType).toString());
    return builder.toString();
  }

  @Override
  public boolean hasAnyAnnotations() {
    return hasAnnotations() || hasParameterAnnotations();
  }

  @Override
  public void clearAllAnnotations() {
    clearAnnotations();
    clearParameterAnnotations();
  }

  @Override
  public void rewriteAllAnnotations(
      BiFunction<DexAnnotation, AnnotatedKind, DexAnnotation> rewriter) {
    setAnnotations(
        annotations().rewrite(annotation -> rewriter.apply(annotation, AnnotatedKind.METHOD)));
    setParameterAnnotations(
        getParameterAnnotations()
            .rewrite(annotation -> rewriter.apply(annotation, AnnotatedKind.PARAMETER)));
  }

  public void clearParameterAnnotations() {
    parameterAnnotationsList = ParameterAnnotationsList.empty();
  }

  public DexAnnotationSet getParameterAnnotation(int index) {
    return getParameterAnnotations().get(index);
  }

  public ParameterAnnotationsList getParameterAnnotations() {
    return parameterAnnotationsList;
  }

  public boolean hasParameterAnnotations() {
    return !getParameterAnnotations().isEmpty();
  }

  public void setParameterAnnotations(ParameterAnnotationsList parameterAnnotations) {
    this.parameterAnnotationsList = parameterAnnotations;
  }

  public String toSmaliString(RetracerForCodePrinting naming) {
    checkIfObsolete();
    StringBuilder builder = new StringBuilder();
    builder.append(".method ");
    builder.append(accessFlags.toSmaliString());
    builder.append(" ");
    builder.append(getReference().name.toSmaliString());
    builder.append(getReference().proto.toSmaliString());
    builder.append("\n");
    if (code != null) {
      DexCode dexCode = code.asDexCode();
      builder.append("    .registers ");
      builder.append(dexCode.registerSize);
      builder.append("\n\n");
      builder.append(dexCode.toSmaliString(naming));
    }
    builder.append(".end method\n");
    return builder.toString();
  }

  @Override
  public String toSourceString() {
    checkIfObsolete();
    return getReference().toSourceString();
  }

  public CfCode buildInstanceOfCfCode(DexType type, boolean negate) {
    CfInstruction[] instructions = new CfInstruction[3 + BooleanUtils.intValue(negate) * 2];
    int i = 0;
    instructions[i++] = new CfLoad(ValueType.OBJECT, 0);
    instructions[i++] = new CfInstanceOf(type);
    if (negate) {
      instructions[i++] = new CfConstNumber(1, ValueType.INT);
      instructions[i++] = new CfLogicalBinop(CfLogicalBinop.Opcode.Xor, NumericType.INT);
    }
    instructions[i] = new CfReturn(ValueType.INT);
    return new CfCode(
        getReference().holder,
        1 + BooleanUtils.intValue(negate),
        getReference().getArity() + 1,
        Arrays.asList(instructions));
  }

  public DexEncodedMethod toMethodThatLogsError(
      AppView<? extends AppInfoWithClassHierarchy> appView) {
    assert appView.testing().isPreLirPhase() || appView.testing().isSupportedLirPhase();
    Builder builder =
        builder(this)
            .setCode(
                appView.testing().isPreLirPhase()
                    ? toCfCodeThatLogsError(appView.dexItemFactory())
                    : toLirCodeThatLogsError(appView))
            .setIsLibraryMethodOverrideIf(
                belongsToVirtualPool() && !isLibraryMethodOverride().isUnknown(),
                isLibraryMethodOverride())
            .setApiLevelForCode(appView.computedMinApiLevel())
            .setApiLevelForDefinition(ComputedApiLevel.unknown());
    setObsolete();
    return builder.build();
  }

  public static void setDebugInfoWithFakeThisParameter(Code code, int arity, AppView<?> appView) {
    if (code.isDexCode()) {
      DexCode dexCode = code.asDexCode();
      DexDebugInfo newDebugInfo = dexCode.debugInfoWithFakeThisParameter(appView.dexItemFactory());
      assert (newDebugInfo == null) || (arity == newDebugInfo.getParameterCount());
      dexCode.setDebugInfo(newDebugInfo);
    } else if (code.isCfCode()) {
      CfCode cfCode = code.asCfCode();
      cfCode.addFakeThisParameter(appView.dexItemFactory());
    } else if (code.isLirCode()) {
      assert appView.options().isRelease();
      assert code.asLirCode().getDebugLocalInfoTable() == null;
    }
  }

  private LirCode<?> toLirCodeThatLogsError(AppView<? extends AppInfoWithClassHierarchy> appView) {
    checkIfObsolete();
    DexItemFactory factory = appView.dexItemFactory();
    Signature signature = MethodSignature.fromDexMethod(getReference());
    DexString message =
        factory.createString(
            CONFIGURATION_DEBUGGING_PREFIX
                + getReference().holder.toSourceString()
                + ": "
                + signature);
    DexString tag = factory.createString("[R8]");
    DexType logger = factory.javaUtilLoggingLoggerType;
    DexMethod getLogger =
        factory.createMethod(
            logger,
            factory.createProto(logger, factory.stringType),
            factory.createString("getLogger"));
    DexMethod severe =
        factory.createMethod(
            logger,
            factory.createProto(factory.voidType, factory.stringType),
            factory.createString("severe"));
    DexType exceptionType = factory.runtimeExceptionType;
    DexMethod exceptionInitMethod =
        factory.createMethod(
            exceptionType,
            factory.createProto(factory.voidType, factory.stringType),
            factory.constructorMethodName);

    TypeElement stringValueType = TypeElement.stringClassType(appView, definitelyNotNull());
    TypeElement loggerValueType = logger.toTypeElement(appView);
    TypeElement exceptionValueType = exceptionType.toTypeElement(appView, definitelyNotNull());

    LirEncodingStrategy<Value, Integer> strategy =
        LirStrategy.getDefaultStrategy().getEncodingStrategy();
    LirBuilder<Value, Integer> lirBuilder =
        LirCode.builder(getReference(), isD8R8Synthesized(), strategy, appView.options());
    int instructionIndex = 0;
    for (; instructionIndex < getNumberOfArguments(); instructionIndex++) {
      DexType argumentType = getArgumentType(instructionIndex);
      lirBuilder.addArgument(instructionIndex, argumentType.isBooleanType());
    }

    // Load tag.
    Value tagValue = new Value(instructionIndex, stringValueType, null);
    strategy.defineValue(tagValue, tagValue.getNumber());
    lirBuilder.addConstString(tag);
    instructionIndex++;

    // Get logger.
    Value loggerValue = new Value(instructionIndex, loggerValueType, null);
    strategy.defineValue(loggerValue, loggerValue.getNumber());
    lirBuilder.addInvokeStatic(getLogger, ImmutableList.of(tagValue), false);
    instructionIndex++;

    // Load message.
    Value messageValue = new Value(instructionIndex, stringValueType, null);
    strategy.defineValue(messageValue, messageValue.getNumber());
    lirBuilder.addConstString(message);
    instructionIndex++;

    // Call logger.
    lirBuilder.addInvokeVirtual(severe, ImmutableList.of(loggerValue, messageValue));
    instructionIndex++;

    // Instantiate exception.
    Value exceptionValue = new Value(instructionIndex, exceptionValueType, null);
    strategy.defineValue(exceptionValue, exceptionValue.getNumber());
    lirBuilder
        .addNewInstance(exceptionType)
        .addInvokeDirect(
            exceptionInitMethod, ImmutableList.of(exceptionValue, messageValue), false);
    instructionIndex += 2;

    // Throw exception.
    lirBuilder.addThrow(exceptionValue);
    instructionIndex++;

    return lirBuilder.build();
  }

  private CfCode toCfCodeThatLogsError(DexItemFactory itemFactory) {
    checkIfObsolete();
    Signature signature = MethodSignature.fromDexMethod(getReference());
    DexString message =
        itemFactory.createString(
            CONFIGURATION_DEBUGGING_PREFIX
                + getReference().holder.toSourceString()
                + ": "
                + signature);
    DexString tag = itemFactory.createString("[R8]");
    DexType logger = itemFactory.javaUtilLoggingLoggerType;
    DexMethod getLogger =
        itemFactory.createMethod(
            logger,
            itemFactory.createProto(logger, itemFactory.stringType),
            itemFactory.createString("getLogger"));
    DexMethod severe =
        itemFactory.createMethod(
            logger,
            itemFactory.createProto(itemFactory.voidType, itemFactory.stringType),
            itemFactory.createString("severe"));
    DexType exceptionType = itemFactory.runtimeExceptionType;
    DexMethod exceptionInitMethod =
        itemFactory.createMethod(
            exceptionType,
            itemFactory.createProto(itemFactory.voidType, itemFactory.stringType),
            itemFactory.constructorMethodName);
    int locals = getReference().proto.parameters.size() + 1;
    if (!isStaticMember()) {
      // Consider `this` pointer
      locals++;
    }
    ImmutableList.Builder<CfInstruction> instructionBuilder = ImmutableList.builder();
    instructionBuilder
        .add(new CfConstString(tag))
        .add(new CfInvoke(Opcodes.INVOKESTATIC, getLogger, false))
        .add(new CfStore(ValueType.OBJECT, locals - 1))
        .add(new CfLoad(ValueType.OBJECT, locals - 1))
        .add(new CfConstString(message))
        .add(new CfInvoke(Opcodes.INVOKEVIRTUAL, severe, false))
        .add(new CfNew(exceptionType))
        .add(new CfStackInstruction(Opcode.Dup))
        .add(new CfConstString(message))
        .add(new CfInvoke(Opcodes.INVOKESPECIAL, exceptionInitMethod, false))
        .add(new CfThrow());
    return new CfCode(getReference().holder, 3, locals, instructionBuilder.build());
  }

  public DexEncodedMethod toTypeSubstitutedMethodAsInlining(
      DexMethod method, DexItemFactory factory) {
    checkIfObsolete();
    return toTypeSubstitutedMethodAsInlining(method, factory, null);
  }

  public DexEncodedMethod toTypeSubstitutedMethodAsInlining(
      DexMethod method, DexItemFactory factory, Consumer<Builder> consumer) {
    boolean isCallerD8R8Synthesized = true;
    return toTypeSubstitutedMethodHelper(
        method,
        isCallerD8R8Synthesized,
        builder -> {
          if (code == null || code.supportsPendingInlineFrame()) {
            builder.setInlineFrame(getReference(), isD8R8Synthesized());
          } else {
            // TODO(b/302509457): Use prepend inline frame here too and delay code rewriting.
            builder.setCode(
                getCode()
                    .getCodeAsInlining(
                        method,
                        isCallerD8R8Synthesized,
                        getReference(),
                        isD8R8Synthesized(),
                        factory));
          }
          if (consumer != null) {
            Code oldCode = builder.code;
            consumer.accept(builder);
            assert ObjectUtils.identical(oldCode, builder.code);
          }
        });
  }

  @SuppressWarnings("ReferenceEquality")
  private DexEncodedMethod toTypeSubstitutedMethodHelper(
      DexMethod method, boolean isD8R8Synthesized, Consumer<Builder> consumer) {
    checkIfObsolete();
    Builder builder = isD8R8Synthesized ? syntheticBuilder(this) : builder(this);
    if (isNonPrivateVirtualMethod() && isLibraryMethodOverride() != OptionalBool.unknown()) {
      builder.setIsLibraryMethodOverride(isLibraryMethodOverride());
    }
    builder.setMethod(method);
    // TODO(b/112847660): Fix type fixers that use this method: Class staticizer
    // TODO(b/112847660): Fix type fixers that use this method: Uninstantiated type optimization
    // TODO(b/112847660): Fix type fixers that use this method: Unused argument removal
    // TODO(b/112847660): Fix type fixers that use this method: Vertical class merger
    // setObsolete();
    if (consumer != null) {
      consumer.accept(builder);
    }
    return builder.build();
  }

  public DexEncodedMethod toRenamedHolderMethod(DexType newHolderType, DexItemFactory factory) {
    DexEncodedMethod.Builder builder = DexEncodedMethod.builder(this);
    builder.setMethod(getReference().withHolder(newHolderType, factory));
    return builder.build();
  }

  public ProgramMethod toPrivateSyntheticMethod(
      DexProgramClass holder, DexMethod method, DexItemFactory factory) {
    assert !isStatic();
    assert !isPrivate();
    assert getHolderType().isIdenticalTo(method.getHolderType());
    checkIfObsolete();
    DexEncodedMethod newMethod =
        toTypeSubstitutedMethodAsInlining(
            method,
            factory,
            builder -> {
              builder.modifyAccessFlags(
                  accessFlags -> {
                    accessFlags.setSynthetic();
                    accessFlags.unsetProtected();
                    accessFlags.unsetPublic();
                    accessFlags.setPrivate();
                  });
            });
    return new ProgramMethod(holder, newMethod);
  }

  public DexEncodedMethod toForwardingMethod(DexClass newHolder, AppView<?> appView) {
    return toForwardingMethod(newHolder, appView, ConsumerUtils.emptyConsumer());
  }

  public DexEncodedMethod toForwardingMethod(
      DexClass newHolder, AppView<?> appView, Consumer<DexEncodedMethod.Builder> builderConsumer) {
    DexMethod newMethod = getReference().withHolder(newHolder, appView.dexItemFactory());
    checkIfObsolete();

    // Clear the final flag, as this method is now overwritten. Do this before creating the builder
    // for the forwarding method, as the forwarding method will copy the access flags from this,
    // and if different forwarding methods are created in different subclasses the first could be
    // final.
    accessFlags.demoteFromFinal();

    return syntheticBuilder(this)
        .setMethod(newMethod)
        .modifyAccessFlags(MethodAccessFlags::setSynthetic)
        .setGenericSignature(MethodTypeSignature.noSignature())
        // If the forwarding target is abstract, we can just create an abstract method. While it
        // will not actually forward, it will create the same exception when hit at runtime.
        // Otherwise, we need to create code that forwards the call to the target.
        .applyIf(
            !isAbstract(),
            builder ->
                builder
                    .setCode(
                        ForwardMethodBuilder.builder(appView.dexItemFactory())
                            .applyIf(
                                isStatic(),
                                codeBuilder ->
                                    codeBuilder
                                        .setStaticSource(newMethod)
                                        .setStaticTarget(
                                            getReference(),
                                            getReference().getHolderType().isInterface(appView)),
                                codeBuilder ->
                                    codeBuilder
                                        .setNonStaticSource(newMethod)
                                        .setSuperTarget(
                                            getReference(),
                                            getReference().getHolderType().isInterface(appView)))
                            .build(appView))
                    .modifyAccessFlags(MethodAccessFlags::setBridge))
        .setIsLibraryMethodOverrideIf(
            !isStatic() && !isLibraryMethodOverride().isUnknown(), isLibraryMethodOverride())
        .apply(builderConsumer)
        .build();
  }

  public static DexEncodedMethod createDesugaringForwardingMethod(
      DexClassAndMethod target,
      DexClass clazz,
      DexMethod forwardMethod,
      DexItemFactory factory,
      boolean targetIsStatic) {
    assert forwardMethod != null;
    // New method will have the same name, proto, and also all the flags of the
    // default method, including bridge flag.
    DexMethod newMethod = target.getReference().withHolder(clazz, factory);
    MethodAccessFlags newFlags = target.getAccessFlags().copy();
    // Some debuggers (like IntelliJ) automatically skip synthetic methods on single step.
    newFlags.setSynthetic();
    newFlags.unsetAbstract();
    // Holder is companion class, or retarget method, not an interface.
    boolean isInterfaceMethodReference = false;
    ForwardMethodBuilder builder =
        ForwardMethodBuilder.builder(factory).setNonStaticSource(newMethod);
    if (targetIsStatic) {
      builder.setStaticTarget(forwardMethod, isInterfaceMethodReference);
    } else {
      builder.setVirtualTarget(forwardMethod, isInterfaceMethodReference);
    }
    if (forwardMethod.getReturnType().isNotIdenticalTo(target.getReturnType())) {
      assert target.getReturnType().isVoidType();
      builder.setIgnoreTargetResult();
    }
    return syntheticBuilder()
        .setMethod(newMethod)
        .setAccessFlags(newFlags)
        .setGenericSignature(MethodTypeSignature.noSignature())
        .setAnnotations(DexAnnotationSet.empty())
        .setCode(builder.buildCf())
        .setApiLevelForDefinition(target.getDefinition().getApiLevelForDefinition())
        .setApiLevelForCode(target.getDefinition().getApiLevelForCode())
        .build();
  }

  public String codeToString() {
    checkIfObsolete();
    return code == null ? "<no code>" : code.toString(this, RetracerForCodePrinting.empty());
  }

  @Override
  public boolean isDexEncodedMethod() {
    checkIfObsolete();
    return true;
  }

  @Override
  public DexEncodedMethod asDexEncodedMethod() {
    checkIfObsolete();
    return this;
  }

  public static int slowCompare(DexEncodedMethod m1, DexEncodedMethod m2) {
    return m1.getReference().compareTo(m2.getReference());
  }

  @Override
  public MethodOptimizationInfo getOptimizationInfo() {
    checkIfObsolete();
    return optimizationInfo;
  }

  public ComputedApiLevel getApiLevelForCode() {
    return apiLevelForCode;
  }

  public void clearApiLevelForCode() {
    this.apiLevelForCode = ComputedApiLevel.notSet();
  }

  public void setApiLevelForCode(ComputedApiLevel apiLevel) {
    assert apiLevel != null;
    this.apiLevelForCode = apiLevel;
  }

  @Override
  public ComputedApiLevel getApiLevel() {
    ComputedApiLevel apiLevelForDefinition = getApiLevelForDefinition();
    return shouldNotHaveCode() ? apiLevelForDefinition : apiLevelForDefinition.max(apiLevelForCode);
  }

  public synchronized MutableMethodOptimizationInfo getMutableOptimizationInfo() {
    checkIfObsolete();
    MutableMethodOptimizationInfo mutableInfo = optimizationInfo.toMutableOptimizationInfo();
    optimizationInfo = mutableInfo;
    return mutableInfo;
  }

  public void setOptimizationInfo(MutableMethodOptimizationInfo info) {
    checkIfObsolete();
    optimizationInfo = info;
  }

  public void unsetOptimizationInfo() {
    checkIfObsolete();
    optimizationInfo = DefaultMethodOptimizationInfo.getInstance();
  }

  public void copyMetadata(AppView<?> appView, DexEncodedMethod from) {
    checkIfObsolete();
    if (from.hasClassFileVersion()) {
      upgradeClassFileVersion(from.getClassFileVersion());
    }
    if (appView.options().apiModelingOptions().isApiCallerIdentificationEnabled()
        && appView.enableWholeProgramOptimizations()) {
      apiLevelForCode = getApiLevelForCode().max(from.getApiLevelForCode());
    }
  }

  @Override
  public MethodTypeSignature getGenericSignature() {
    return genericSignature;
  }

  public void setGenericSignature(MethodTypeSignature genericSignature) {
    assert genericSignature != null;
    this.genericSignature = genericSignature;
  }

  @Override
  public void clearGenericSignature() {
    this.genericSignature = MethodTypeSignature.noSignature();
  }

  public DexWritableCode getDexWritableCodeOrNull() {
    Code code = getCode();
    assert code == null || code.isDexWritableCode();
    return code == null ? null : code.asDexWritableCode();
  }

  @SuppressWarnings("ReferenceEquality")
  public DexEncodedMethod rewrittenWithLens(
      GraphLens lens, GraphLens appliedLens, DexDefinitionSupplier definitions) {
    assert this != SENTINEL;
    DexMethod newMethodReference = lens.getRenamedMethodSignature(getReference(), appliedLens);
    DexClass newHolder = definitions.definitionFor(newMethodReference.getHolderType());
    assert newHolder != null;
    DexEncodedMethod newMethod = newHolder.lookupMethod(newMethodReference);
    assert newMethod != null : newMethodReference.toSourceString();
    return newMethod;
  }

  public static Builder syntheticBuilder() {
    return new Builder(true);
  }

  public static Builder syntheticBuilder(DexEncodedMethod from) {
    return new Builder(true, from);
  }

  public static Builder builder() {
    return new Builder(false);
  }

  private static Builder builder(DexEncodedMethod from) {
    return new Builder(from.isD8R8Synthesized(), from);
  }

  public static class Builder {

    private MethodAccessFlags accessFlags;
    private Code code;
    private DexMethod method;
    private MethodTypeSignature genericSignature = MethodTypeSignature.noSignature();
    private DexAnnotationSet annotations = DexAnnotationSet.empty();
    private OptionalBool isLibraryMethodOverride = OptionalBool.UNKNOWN;
    private ParameterAnnotationsList parameterAnnotations = ParameterAnnotationsList.empty();
    private CompilationState compilationState = CompilationState.NOT_PROCESSED;
    private MethodOptimizationInfo optimizationInfo = DefaultMethodOptimizationInfo.getInstance();
    private KotlinMethodLevelInfo kotlinInfo = getNoKotlinInfo();
    private CfVersion classFileVersion = null;
    private ComputedApiLevel apiLevelForDefinition = ComputedApiLevel.notSet();
    private ComputedApiLevel apiLevelForCode = ComputedApiLevel.notSet();
    private final boolean d8R8Synthesized;
    private boolean deprecated = false;
    private DexMethod pendingInlineFrame = null;

    // Checks to impose on the built method. They should always be active to start with and be
    // lowered on the use site.
    private boolean checkMethodNotNull = true;
    private boolean checkParameterAnnotationList = true;
    private boolean checkAndroidApiLevels = true;

    private Consumer<DexEncodedMethod> buildConsumer = ConsumerUtils.emptyConsumer();

    private Builder(boolean d8R8Synthesized) {
      this.d8R8Synthesized = d8R8Synthesized;
    }

    private Builder(boolean d8R8Synthesized, DexEncodedMethod from) {
      // Copy all the mutable state of a DexEncodedMethod here.
      method = from.getReference();
      accessFlags = from.getAccessFlags().copy();
      genericSignature = from.getGenericSignature();
      annotations = from.annotations();
      code = from.getCode();
      pendingInlineFrame = from.getPendingInlineFrame();
      apiLevelForDefinition = from.getApiLevelForDefinition();
      apiLevelForCode = from.getApiLevelForCode();
      optimizationInfo =
          from.getOptimizationInfo().isMutableOptimizationInfo()
              ? from.getOptimizationInfo().asMutableMethodOptimizationInfo().mutableCopy()
              : from.getOptimizationInfo();
      kotlinInfo = from.getKotlinInfo();
      classFileVersion = from.classFileVersion;
      this.d8R8Synthesized = d8R8Synthesized;
      deprecated = from.isDeprecated();

      if (from.getParameterAnnotations().isEmpty()
          || from.getParameterAnnotations().size() == from.getParameters().size()) {
        parameterAnnotations = from.getParameterAnnotations();
      } else {
        // If the there are missing parameter annotations populate these when creating the builder.
        parameterAnnotations =
            from.getParameterAnnotations().withParameterCount(from.getParameters().size());
      }
    }

    public Builder apply(Consumer<Builder> consumer) {
      consumer.accept(this);
      return this;
    }

    public Builder applyIf(boolean condition, Consumer<Builder> thenConsumer) {
      return applyIf(condition, thenConsumer, emptyConsumer());
    }

    public Builder applyIf(
        boolean condition, Consumer<Builder> thenConsumer, Consumer<Builder> elseConsumer) {
      if (condition) {
        thenConsumer.accept(this);
      } else {
        elseConsumer.accept(this);
      }
      return this;
    }

    public Builder fixupOptimizationInfo(
        AppView<AppInfoWithLiveness> appView, MethodOptimizationInfoFixer fixer) {
      return modifyOptimizationInfo(
          (newMethod, optimizationInfo) -> optimizationInfo.fixup(appView, newMethod, fixer));
    }

    public Builder addBuildConsumer(Consumer<DexEncodedMethod> consumer) {
      this.buildConsumer = this.buildConsumer.andThen(consumer);
      return this;
    }

    public Builder modifyAccessFlags(Consumer<MethodAccessFlags> consumer) {
      consumer.accept(accessFlags);
      return this;
    }

    public Builder setAccessFlags(MethodAccessFlags accessFlags) {
      this.accessFlags = accessFlags;
      return this;
    }

    public Builder setMethod(DexMethod method) {
      this.method = method;
      return this;
    }

    public Builder setCompilationState(CompilationState compilationState) {
      assert this.compilationState == CompilationState.NOT_PROCESSED;
      this.compilationState = compilationState;
      return this;
    }

    public Builder setIsLibraryMethodOverride(OptionalBool isLibraryMethodOverride) {
      assert !isLibraryMethodOverride.isUnknown();
      this.isLibraryMethodOverride = isLibraryMethodOverride;
      return this;
    }

    public Builder setIsLibraryMethodOverrideIf(
        boolean condition, OptionalBool isLibraryMethodOverride) {
      if (condition) {
        return setIsLibraryMethodOverride(isLibraryMethodOverride);
      }
      return this;
    }

    public Builder setIsLibraryMethodOverrideIfKnown(OptionalBool isLibraryMethodOverride) {
      return setIsLibraryMethodOverrideIf(
          !isLibraryMethodOverride.isUnknown(), isLibraryMethodOverride);
    }

    public Builder unsetIsLibraryMethodOverride() {
      this.isLibraryMethodOverride = OptionalBool.UNKNOWN;
      return this;
    }

    public Builder clearAnnotations() {
      return setAnnotations(DexAnnotationSet.empty());
    }

    public Builder clearParameterAnnotations() {
      return setParameterAnnotations(ParameterAnnotationsList.empty());
    }

    public Builder clearAllAnnotations() {
      return clearAnnotations().clearParameterAnnotations();
    }

    public Builder setAnnotations(DexAnnotationSet annotations) {
      this.annotations = annotations;
      return this;
    }

    public Builder setParameterAnnotations(ParameterAnnotationsList parameterAnnotations) {
      this.parameterAnnotations = parameterAnnotations;
      return this;
    }

    public Builder rewriteParameterAnnotations(
        DexEncodedMethod method, RewrittenPrototypeDescription rewrittenPrototypeDescription) {
      if (parameterAnnotations.isEmpty()) {
        // Nothing to do.
        return this;
      }

      var argumentInfoCollection = rewrittenPrototypeDescription.getArgumentInfoCollection();
      if (!argumentInfoCollection.hasArgumentPermutation()
          && !argumentInfoCollection.hasRemovedArguments()
          && !rewrittenPrototypeDescription.hasExtraParameters()) {
        // Nothing to do.
        return this;
      }

      List<DexAnnotationSet> newParameterAnnotations =
          new ArrayList<>(parameterAnnotations.countNonMissing());
      int newNumberOfMissingParameterAnnotations = 0;

      for (int parameterIndex = 0;
          parameterIndex < method.getParameters().size();
          parameterIndex++) {
        int argumentIndex = parameterIndex + method.getFirstNonReceiverArgumentIndex();
        if (!argumentInfoCollection.isArgumentRemoved(argumentIndex)) {
          if (parameterAnnotations.isMissing(parameterIndex)) {
            newNumberOfMissingParameterAnnotations++;
          } else {
            newParameterAnnotations.add(parameterAnnotations.get(parameterIndex));
          }
        }
      }

      if (newParameterAnnotations.isEmpty()) {
        return setParameterAnnotations(ParameterAnnotationsList.empty());
      }

      if (argumentInfoCollection.hasArgumentPermutation()) {
        // If we have missing parameter annotations we cannot reliably reorder without handling
        // missing annotations. We could introduce empty annotations to fill in empty spots but the
        // missing parameters are only bridged in the reflection api for enums or local/anonymous
        // classes and permuting such method arguments destroys the "invariant" that these are
        // shifted.
        // Having a keep on the members will automatically remove the permutation so the developer
        // can easily recover.
        if (newNumberOfMissingParameterAnnotations > 0) {
          return setParameterAnnotations(ParameterAnnotationsList.empty());
        }
        List<DexAnnotationSet> newPermutedParameterAnnotations =
            Arrays.asList(new DexAnnotationSet[method.getParameters().size()]);
        for (int parameterIndex = newNumberOfMissingParameterAnnotations;
            parameterIndex < method.getParameters().size();
            parameterIndex++) {
          int argumentIndex = parameterIndex + method.getFirstNonReceiverArgumentIndex();
          int newArgumentIndex = argumentInfoCollection.getNewArgumentIndex(argumentIndex, 0);
          int newParameterIndex = newArgumentIndex - method.getFirstNonReceiverArgumentIndex();
          newPermutedParameterAnnotations.set(
              newParameterIndex,
              newParameterAnnotations.get(parameterIndex - newNumberOfMissingParameterAnnotations));
        }
        newParameterAnnotations = newPermutedParameterAnnotations;
        newNumberOfMissingParameterAnnotations = 0;
      }

      if (rewrittenPrototypeDescription.hasExtraParameters()) {
        for (int extraParameter = 0;
            extraParameter < rewrittenPrototypeDescription.getExtraParameters().size();
            extraParameter++) {
          newParameterAnnotations.add(DexAnnotationSet.empty());
        }
      }

      return setParameterAnnotations(
          ParameterAnnotationsList.create(
              newParameterAnnotations.toArray(DexAnnotationSet.EMPTY_ARRAY),
              newNumberOfMissingParameterAnnotations));
    }

    public Builder setOptimizationInfo(MethodOptimizationInfo optimizationInfo) {
      this.optimizationInfo = optimizationInfo;
      return this;
    }

    public Builder modifyOptimizationInfo(
        BiConsumer<DexEncodedMethod, MutableMethodOptimizationInfo> consumer) {
      return addBuildConsumer(
          newMethod -> {
            if (optimizationInfo.isMutableOptimizationInfo()) {
              consumer.accept(newMethod, optimizationInfo.asMutableMethodOptimizationInfo());
            }
          });
    }

    public Builder setInlineFrame(DexMethod callee, boolean calleeIsD8R8Synthesized) {
      assert code == null || code.supportsPendingInlineFrame();
      if (calleeIsD8R8Synthesized) {
        // No need to record a compiler synthesized inline frame.
        return this;
      }
      // Two pending non-synthetic callee frames should never happen.
      assert pendingInlineFrame == null;
      pendingInlineFrame = callee;
      return this;
    }

    public Builder setCode(Code code) {
      this.code = code;
      return this;
    }

    public Builder setCode(Function<DexMethod, Code> fn) {
      this.code = fn.apply(method);
      return this;
    }

    public Builder unsetCode() {
      return setCode((Code) null);
    }

    public Builder setGenericSignature(MethodTypeSignature methodSignature) {
      this.genericSignature = methodSignature;
      return this;
    }

    public Builder setApiLevelForDefinition(ComputedApiLevel apiLevelForDefinition) {
      this.apiLevelForDefinition = apiLevelForDefinition;
      return this;
    }

    public Builder setApiLevelForCode(ComputedApiLevel apiLevelForCode) {
      this.apiLevelForCode = apiLevelForCode;
      return this;
    }

    public Builder setDeprecated(boolean deprecated) {
      this.deprecated = deprecated;
      return this;
    }

    public Builder setClassFileVersion(CfVersion version) {
      classFileVersion = version;
      return this;
    }

    public Builder disableMethodNotNullCheck() {
      checkMethodNotNull = false;
      return this;
    }

    public Builder disableParameterAnnotationListCheck() {
      checkParameterAnnotationList = false;
      return this;
    }

    public Builder disableAndroidApiLevelCheck() {
      checkAndroidApiLevels = false;
      return this;
    }

    public DexEncodedMethod build() {
      assert !checkMethodNotNull || method != null;
      assert accessFlags != null;
      assert annotations != null;
      assert parameterAnnotations != null;
      assert !checkParameterAnnotationList
          || parameterAnnotations.isEmpty()
          || parameterAnnotations.size() == method.proto.parameters.size();
      assert !checkAndroidApiLevels || apiLevelForDefinition != null;
      assert !checkAndroidApiLevels || apiLevelForCode != null;
      assert code == null || code.supportsPendingInlineFrame() || pendingInlineFrame == null;
      DexEncodedMethod result =
          new DexEncodedMethod(
              method,
              accessFlags,
              genericSignature,
              annotations,
              parameterAnnotations,
              code,
              pendingInlineFrame,
              d8R8Synthesized,
              apiLevelForDefinition,
              apiLevelForCode,
              classFileVersion,
              optimizationInfo,
              deprecated);
      result.setKotlinMemberInfo(kotlinInfo);
      result.compilationState = compilationState;
      if (!isLibraryMethodOverride.isUnknown()) {
        result.setLibraryMethodOverride(isLibraryMethodOverride);
      }
      buildConsumer.accept(result);
      return result;
    }
  }
}
