// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.ir.code.Invoke.Type;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;
import com.android.tools.r8.ir.desugar.itf.InterfaceProcessor.InterfaceProcessorNestedGraphLens;
import com.android.tools.r8.shaking.KeepInfoCollection;
import com.android.tools.r8.utils.Action;
import com.android.tools.r8.utils.IterableUtils;
import com.android.tools.r8.utils.SetUtils;
import com.android.tools.r8.utils.collections.BidirectionalManyToOneRepresentativeHashMap;
import com.android.tools.r8.utils.collections.BidirectionalManyToOneRepresentativeMap;
import com.android.tools.r8.utils.collections.MutableBidirectionalManyToOneRepresentativeMap;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.objects.Object2BooleanArrayMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A GraphLens implements a virtual view on top of the graph, used to delay global rewrites until
 * later IR processing stages.
 *
 * <p>Valid remappings are limited to the following operations:
 *
 * <ul>
 *   <li>Mapping a classes type to one of the super/subtypes.
 *   <li>Renaming private methods/fields.
 *   <li>Moving methods/fields to a super/subclass.
 *   <li>Replacing method/field references by the same method/field on a super/subtype
 *   <li>Moved methods might require changed invocation type at the call site
 * </ul>
 *
 * Note that the latter two have to take visibility into account.
 */
public abstract class GraphLens {

  abstract static class MemberLookupResult<R extends DexMember<?, R>> {

    private final R reference;
    private final R reboundReference;

    private MemberLookupResult(R reference, R reboundReference) {
      this.reference = reference;
      this.reboundReference = reboundReference;
    }

    public R getReference() {
      return reference;
    }

    public R getRewrittenReference(BidirectionalManyToOneRepresentativeMap<R, R> rewritings) {
      return rewritings.getOrDefault(reference, reference);
    }

    public R getRewrittenReference(Map<R, R> rewritings) {
      return rewritings.getOrDefault(reference, reference);
    }

    public boolean hasReboundReference() {
      return reboundReference != null;
    }

    public R getReboundReference() {
      return reboundReference;
    }

    public R getRewrittenReboundReference(
        BidirectionalManyToOneRepresentativeMap<R, R> rewritings) {
      return rewritings.getOrDefault(reboundReference, reboundReference);
    }

    public R getRewrittenReboundReference(Map<R, R> rewritings) {
      return rewritings.getOrDefault(reboundReference, reboundReference);
    }

    abstract static class Builder<R extends DexMember<?, R>, Self extends Builder<R, Self>> {

      R reference;
      R reboundReference;

      public Self setReference(R reference) {
        this.reference = reference;
        return self();
      }

      public Self setReboundReference(R reboundReference) {
        this.reboundReference = reboundReference;
        return self();
      }

      public abstract Self self();
    }
  }

  /**
   * Intermediate result of a field lookup that stores the actual non-rebound reference and the
   * rebound reference that points to the definition of the field.
   */
  public static class FieldLookupResult extends MemberLookupResult<DexField> {

    private final DexType castType;

    private FieldLookupResult(DexField reference, DexField reboundReference, DexType castType) {
      super(reference, reboundReference);
      this.castType = castType;
    }

    public static Builder builder(GraphLens lens) {
      return new Builder(lens);
    }

    public boolean hasCastType() {
      return castType != null;
    }

    public DexType getCastType() {
      return castType;
    }

    public DexType getRewrittenCastType(Function<DexType, DexType> fn) {
      return hasCastType() ? fn.apply(castType) : null;
    }

    public static class Builder extends MemberLookupResult.Builder<DexField, Builder> {

      private DexType castType;
      private GraphLens lens;

      private Builder(GraphLens lens) {
        this.lens = lens;
      }

      public Builder setCastType(DexType castType) {
        this.castType = castType;
        return this;
      }

      @Override
      public Builder self() {
        return this;
      }

      public FieldLookupResult build() {
        // TODO(b/168282032): All non-identity graph lenses should set the rebound reference.
        return new FieldLookupResult(reference, reboundReference, castType);
      }
    }
  }

  /**
   * Result of a method lookup in a GraphLens.
   *
   * <p>This provides the new target and invoke type to use, along with a description of the
   * prototype changes that have been made to the target method and the corresponding required
   * changes to the invoke arguments.
   */
  public static class MethodLookupResult extends MemberLookupResult<DexMethod> {

    private final Type type;
    private final RewrittenPrototypeDescription prototypeChanges;

    public MethodLookupResult(
        DexMethod reference,
        DexMethod reboundReference,
        Type type,
        RewrittenPrototypeDescription prototypeChanges) {
      super(reference, reboundReference);
      this.type = type;
      this.prototypeChanges = prototypeChanges;
    }

    public static Builder builder(GraphLens lens) {
      return new Builder(lens);
    }

    public Type getType() {
      return type;
    }

    public RewrittenPrototypeDescription getPrototypeChanges() {
      return prototypeChanges;
    }

    public static class Builder extends MemberLookupResult.Builder<DexMethod, Builder> {

      private final GraphLens lens;
      private RewrittenPrototypeDescription prototypeChanges = RewrittenPrototypeDescription.none();
      private Type type;

      private Builder(GraphLens lens) {
        this.lens = lens;
      }

      public Builder setPrototypeChanges(RewrittenPrototypeDescription prototypeChanges) {
        this.prototypeChanges = prototypeChanges;
        return this;
      }

      public Builder setType(Type type) {
        this.type = type;
        return this;
      }

      public MethodLookupResult build() {
        assert reference != null;
        // TODO(b/168282032): All non-identity graph lenses should set the rebound reference.
        return new MethodLookupResult(reference, reboundReference, type, prototypeChanges);
      }

      @Override
      public Builder self() {
        return this;
      }
    }
  }

  public abstract static class Builder {

    protected final MutableBidirectionalManyToOneRepresentativeMap<DexField, DexField> fieldMap =
        BidirectionalManyToOneRepresentativeHashMap.newIdentityHashMap();
    protected final MutableBidirectionalManyToOneRepresentativeMap<DexMethod, DexMethod> methodMap =
        BidirectionalManyToOneRepresentativeHashMap.newIdentityHashMap();
    protected final Map<DexType, DexType> typeMap = new IdentityHashMap<>();

    protected Builder() {}

    public void map(DexType from, DexType to) {
      if (from == to) {
        return;
      }
      typeMap.put(from, to);
    }

    public void move(DexMethod from, DexMethod to) {
      if (from == to) {
        return;
      }
      methodMap.put(from, to);
    }

    public void move(DexField from, DexField to) {
      if (from == to) {
        return;
      }
      fieldMap.put(from, to);
    }

    public abstract GraphLens build(AppView<?> appView);
  }

  /**
   * Intentionally private. All graph lenses except for {@link IdentityGraphLens} should inherit
   * from {@link NonIdentityGraphLens}.
   */
  private GraphLens() {}

  public boolean isSyntheticFinalizationGraphLens() {
    return false;
  }

  public abstract DexType getOriginalType(DexType type);

  public abstract Iterable<DexType> getOriginalTypes(DexType type);

  public abstract DexField getOriginalFieldSignature(DexField field);

  public abstract DexMethod getOriginalMethodSignature(DexMethod method);

  public abstract DexField getRenamedFieldSignature(DexField originalField);

  public final DexMember<?, ?> getRenamedMemberSignature(DexMember<?, ?> originalMember) {
    return originalMember.isDexField()
        ? getRenamedFieldSignature(originalMember.asDexField())
        : getRenamedMethodSignature(originalMember.asDexMethod());
  }

  public final DexMethod getRenamedMethodSignature(DexMethod originalMethod) {
    return getRenamedMethodSignature(originalMethod, null);
  }

  public abstract DexMethod getRenamedMethodSignature(DexMethod originalMethod, GraphLens applied);

  public DexEncodedMethod mapDexEncodedMethod(
      DexEncodedMethod originalEncodedMethod, DexDefinitionSupplier definitions) {
    return mapDexEncodedMethod(originalEncodedMethod, definitions, null);
  }

  public DexEncodedMethod mapDexEncodedMethod(
      DexEncodedMethod originalEncodedMethod,
      DexDefinitionSupplier definitions,
      GraphLens applied) {
    assert originalEncodedMethod != DexEncodedMethod.SENTINEL;
    DexMethod newMethod = getRenamedMethodSignature(originalEncodedMethod.getReference(), applied);
    // Note that:
    // * Even if `newMethod` is the same as `originalEncodedMethod.method`, we still need to look it
    //   up, since `originalEncodedMethod` may be obsolete.
    // * We can't directly use AppInfo#definitionFor(DexMethod) since definitions may not be
    //   updated either yet.
    DexClass newHolder = definitions.definitionFor(newMethod.holder);
    assert newHolder != null;
    DexEncodedMethod newEncodedMethod = newHolder.lookupMethod(newMethod);
    assert newEncodedMethod != null;
    return newEncodedMethod;
  }

  public ProgramMethod mapProgramMethod(
      ProgramMethod oldMethod, DexDefinitionSupplier definitions) {
    DexMethod newMethod = getRenamedMethodSignature(oldMethod.getReference());
    DexProgramClass holder = asProgramClassOrNull(definitions.definitionForHolder(newMethod));
    return newMethod.lookupOnProgramClass(holder);
  }

  // Predicate indicating if a rewritten reference is a simple renaming, meaning the move from one
  // reference to another is simply either just a renaming or/also renaming of the references. In
  // other words, the content of the definition, including the definition of all of its members is
  // the same modulo the renaming.
  public <T extends DexReference> boolean isSimpleRenaming(T from, T to) {
    assert from != to;
    return false;
  }

  public abstract String lookupPackageName(String pkg);

  public abstract DexType lookupClassType(DexType type);

  public abstract DexType lookupType(DexType type);

  // This overload can be used when the graph lens is known to be context insensitive.
  public final DexMethod lookupMethod(DexMethod method) {
    assert verifyIsContextFreeForMethod(method);
    return lookupMethod(method, null, null).getReference();
  }

  public final MethodLookupResult lookupInvokeDirect(DexMethod method, ProgramMethod context) {
    return lookupMethod(method, context.getReference(), Type.DIRECT);
  }

  public final MethodLookupResult lookupInvokeInterface(DexMethod method, ProgramMethod context) {
    return lookupMethod(method, context.getReference(), Type.INTERFACE);
  }

  public final MethodLookupResult lookupInvokeStatic(DexMethod method, ProgramMethod context) {
    return lookupMethod(method, context.getReference(), Type.STATIC);
  }

  public final MethodLookupResult lookupInvokeSuper(DexMethod method, ProgramMethod context) {
    return lookupMethod(method, context.getReference(), Type.SUPER);
  }

  public final MethodLookupResult lookupInvokeVirtual(DexMethod method, ProgramMethod context) {
    return lookupMethod(method, context.getReference(), Type.VIRTUAL);
  }

  /** Lookup a rebound or non-rebound method reference using the current graph lens. */
  public abstract MethodLookupResult lookupMethod(DexMethod method, DexMethod context, Type type);

  protected abstract MethodLookupResult internalLookupMethod(
      DexMethod reference, DexMethod context, Type type, LookupMethodContinuation continuation);

  interface LookupMethodContinuation {

    MethodLookupResult lookupMethod(MethodLookupResult previous);
  }

  public abstract RewrittenPrototypeDescription lookupPrototypeChangesForMethodDefinition(
      DexMethod method);

  /** Lookup a rebound or non-rebound field reference using the current graph lens. */
  public DexField lookupField(DexField field) {
    // Lookup the field using the graph lens and return the (non-rebound) reference from the lookup
    // result.
    return lookupFieldResult(field).getReference();
  }

  /** Lookup a rebound or non-rebound field reference using the current graph lens. */
  public FieldLookupResult lookupFieldResult(DexField field) {
    // Lookup the field using the graph lens and return the lookup result.
    return internalLookupField(field, x -> x);
  }

  protected abstract FieldLookupResult internalLookupField(
      DexField reference, LookupFieldContinuation continuation);

  interface LookupFieldContinuation {

    FieldLookupResult lookupField(FieldLookupResult previous);
  }

  public DexMethod lookupGetFieldForMethod(DexField field, DexMethod context) {
    return null;
  }

  public DexMethod lookupPutFieldForMethod(DexField field, DexMethod context) {
    return null;
  }

  public DexReference lookupReference(DexReference reference) {
    return reference.apply(this::lookupType, this::lookupField, this::lookupMethod);
  }

  // The method lookupMethod() maps a pair INVOKE=(method signature, invoke type) to a new pair
  // INVOKE'=(method signature', invoke type'). This mapping can be context sensitive, meaning that
  // the result INVOKE' depends on where the invocation INVOKE is in the program. This is, for
  // example, used by the vertical class merger to translate invoke-super instructions that hit
  // a method in the direct super class to invoke-direct instructions after class merging.
  //
  // This method can be used to determine if a graph lens is context sensitive. If a graph lens
  // is context insensitive, it is safe to invoke lookupMethod() without a context (or to pass null
  // as context). Trying to invoke a context sensitive graph lens without a context will lead to
  // an assertion error.
  public abstract boolean isContextFreeForMethods();

  public boolean verifyIsContextFreeForMethod(DexMethod method) {
    return isContextFreeForMethods();
  }

  public static GraphLens getIdentityLens() {
    return IdentityGraphLens.getInstance();
  }

  public boolean hasCodeRewritings() {
    return true;
  }

  public boolean isAppliedLens() {
    return false;
  }

  public abstract boolean isIdentityLens();

  public boolean isMemberRebindingLens() {
    return false;
  }

  public abstract boolean isNonIdentityLens();

  public NonIdentityGraphLens asNonIdentityLens() {
    return null;
  }

  public boolean isInterfaceProcessorLens() {
    return false;
  }

  public InterfaceProcessorNestedGraphLens asInterfaceProcessorLens() {
    return null;
  }

  public GraphLens withCodeRewritingsApplied(DexItemFactory dexItemFactory) {
    if (hasCodeRewritings()) {
      return new ClearCodeRewritingGraphLens(dexItemFactory, this);
    }
    return this;
  }

  public <T extends DexDefinition> boolean assertDefinitionsNotModified(Iterable<T> definitions) {
    for (DexDefinition definition : definitions) {
      DexReference reference = definition.getReference();
      // We allow changes to bridge methods as these get retargeted even if they are kept.
      boolean isBridge =
          definition.isDexEncodedMethod() && definition.asDexEncodedMethod().accessFlags.isBridge();
      assert isBridge || lookupReference(reference) == reference;
    }
    return true;
  }

  public <T extends DexReference> boolean assertPinnedNotModified(KeepInfoCollection keepInfo) {
    List<DexReference> pinnedItems = new ArrayList<>();
    keepInfo.forEachPinnedType(pinnedItems::add);
    keepInfo.forEachPinnedMethod(pinnedItems::add);
    keepInfo.forEachPinnedField(pinnedItems::add);
    return assertReferencesNotModified(pinnedItems);
  }

  public <T extends DexReference> boolean assertReferencesNotModified(Iterable<T> references) {
    for (DexReference reference : references) {
      if (reference.isDexField()) {
        DexField field = reference.asDexField();
        assert getRenamedFieldSignature(field) == field;
      } else if (reference.isDexMethod()) {
        DexMethod method = reference.asDexMethod();
        assert getRenamedMethodSignature(method) == method;
      } else {
        assert reference.isDexType();
        DexType type = reference.asDexType();
        assert lookupType(type) == type;
      }
    }
    return true;
  }

  public Map<DexCallSite, ProgramMethodSet> rewriteCallSites(
      Map<DexCallSite, ProgramMethodSet> callSites, DexDefinitionSupplier definitions) {
    Map<DexCallSite, ProgramMethodSet> result = new IdentityHashMap<>();
    LensCodeRewriterUtils rewriter = new LensCodeRewriterUtils(definitions, this);
    callSites.forEach(
        (callSite, contexts) -> {
          for (ProgramMethod context : contexts.rewrittenWithLens(definitions, this)) {
            DexCallSite rewrittenCallSite = rewriter.rewriteCallSite(callSite, context);
            result
                .computeIfAbsent(rewrittenCallSite, ignore -> ProgramMethodSet.create())
                .add(context);
          }
        });
    return result;
  }

  @SuppressWarnings("unchecked")
  public <T extends DexReference> T rewriteReference(T reference) {
    return (T)
        reference.apply(
            this::lookupType, this::getRenamedFieldSignature, this::getRenamedMethodSignature);
  }

  public Set<DexReference> rewriteReferences(Set<DexReference> references) {
    Set<DexReference> result = SetUtils.newIdentityHashSet(references.size());
    for (DexReference reference : references) {
      result.add(rewriteReference(reference));
    }
    return result;
  }

  public <R extends DexReference, T> ImmutableMap<R, T> rewriteReferenceKeys(Map<R, T> map) {
    ImmutableMap.Builder<R, T> builder = ImmutableMap.builder();
    map.forEach((reference, value) -> builder.put(rewriteReference(reference), value));
    return builder.build();
  }

  public Object2BooleanMap<DexReference> rewriteReferenceKeys(Object2BooleanMap<DexReference> map) {
    Object2BooleanMap<DexReference> result = new Object2BooleanArrayMap<>();
    for (Object2BooleanMap.Entry<DexReference> entry : map.object2BooleanEntrySet()) {
      result.put(rewriteReference(entry.getKey()), entry.getBooleanValue());
    }
    return result;
  }

  public ImmutableSet<DexMethod> rewriteMethods(Set<DexMethod> methods) {
    ImmutableSet.Builder<DexMethod> builder = ImmutableSet.builder();
    for (DexMethod method : methods) {
      builder.add(getRenamedMethodSignature(method));
    }
    return builder.build();
  }

  public ImmutableSet<DexField> rewriteFields(Set<DexField> fields) {
    ImmutableSet.Builder<DexField> builder = ImmutableSet.builder();
    for (DexField field : fields) {
      builder.add(getRenamedFieldSignature(field));
    }
    return builder.build();
  }

  public <T> ImmutableMap<DexField, T> rewriteFieldKeys(Map<DexField, T> map) {
    ImmutableMap.Builder<DexField, T> builder = ImmutableMap.builder();
    map.forEach((field, value) -> builder.put(getRenamedFieldSignature(field), value));
    return builder.build();
  }

  public ImmutableSet<DexType> rewriteTypes(Set<DexType> types) {
    ImmutableSet.Builder<DexType> builder = new ImmutableSet.Builder<>();
    for (DexType type : types) {
      builder.add(lookupType(type));
    }
    return builder.build();
  }

  public <T> ImmutableMap<DexType, T> rewriteTypeKeys(Map<DexType, T> map) {
    ImmutableMap.Builder<DexType, T> builder = ImmutableMap.builder();
    map.forEach((type, value) -> builder.put(lookupType(type), value));
    return builder.build();
  }

  public boolean verifyMappingToOriginalProgram(
      AppView<?> appView, DexApplication originalApplication) {
    Iterable<DexProgramClass> classes = appView.appInfo().classesWithDeterministicOrder();
    // Collect all original fields and methods for efficient querying.
    Set<DexField> originalFields = Sets.newIdentityHashSet();
    Set<DexMethod> originalMethods = Sets.newIdentityHashSet();
    for (DexProgramClass clazz : originalApplication.classes()) {
      for (DexEncodedField field : clazz.fields()) {
        originalFields.add(field.getReference());
      }
      for (DexEncodedMethod method : clazz.methods()) {
        originalMethods.add(method.getReference());
      }
    }

    // Check that all fields and methods in the generated program can be mapped back to one of the
    // original fields or methods.
    for (DexProgramClass clazz : classes) {
      if (appView.appInfo().getSyntheticItems().isSyntheticClass(clazz)) {
        continue;
      }
      for (DexEncodedField field : clazz.fields()) {
        if (field.isD8R8Synthesized()) {
          // Fields synthesized by D8/R8 may not be mapped.
          continue;
        }
        DexField originalField = getOriginalFieldSignature(field.getReference());
        assert originalFields.contains(originalField)
            : "Unable to map field `"
                + field.getReference().toSourceString()
                + "` back to original program";
      }
      for (DexEncodedMethod method : clazz.methods()) {
        if (method.isD8R8Synthesized()) {
          // Methods synthesized by D8/R8 may not be mapped.
          continue;
        }
        DexMethod originalMethod = getOriginalMethodSignature(method.getReference());
        assert originalMethods.contains(originalMethod)
            : "Method could not be mapped back: "
                + method.toSourceString()
                + ", originalMethod: "
                + originalMethod.toSourceString();
      }
    }

    return true;
  }

  public abstract static class NonIdentityGraphLens extends GraphLens {

    private final DexItemFactory dexItemFactory;
    private GraphLens previousLens;

    private final Map<DexType, DexType> arrayTypeCache = new ConcurrentHashMap<>();

    public NonIdentityGraphLens(AppView<?> appView) {
      this(appView.dexItemFactory(), appView.graphLens());
    }

    public NonIdentityGraphLens(DexItemFactory dexItemFactory, GraphLens previousLens) {
      this.dexItemFactory = dexItemFactory;
      this.previousLens = previousLens;
    }

    public final DexItemFactory dexItemFactory() {
      return dexItemFactory;
    }

    public final GraphLens getPrevious() {
      return previousLens;
    }

    @SuppressWarnings("unchecked")
    public final <T extends GraphLens> T findPrevious(Predicate<NonIdentityGraphLens> predicate) {
      GraphLens current = getPrevious();
      while (current.isNonIdentityLens()) {
        NonIdentityGraphLens nonIdentityGraphLens = current.asNonIdentityLens();
        if (predicate.test(nonIdentityGraphLens)) {
          return (T) nonIdentityGraphLens;
        }
        current = nonIdentityGraphLens.getPrevious();
      }
      return null;
    }

    public final void withAlternativeParentLens(GraphLens lens, Action action) {
      GraphLens oldParent = getPrevious();
      previousLens = lens;
      action.execute();
      previousLens = oldParent;
    }

    @Override
    public MethodLookupResult lookupMethod(DexMethod method, DexMethod context, Type type) {
      if (method.getHolderType().isArrayType()) {
        assert lookupType(method.getReturnType()) == method.getReturnType();
        assert method.getParameters().stream()
            .allMatch(parameterType -> lookupType(parameterType) == parameterType);
        return MethodLookupResult.builder(this)
            .setReference(method.withHolder(lookupType(method.getHolderType()), dexItemFactory))
            .setType(type)
            .build();
      }
      assert method.getHolderType().isClassType();
      return internalLookupMethod(method, context, type, result -> result);
    }

    @Override
    public String lookupPackageName(String pkg) {
      return pkg;
    }

    @Override
    public final DexType lookupType(DexType type) {
      if (type.isPrimitiveType() || type.isVoidType() || type.isNullValueType()) {
        return type;
      }
      if (type.isArrayType()) {
        DexType result = arrayTypeCache.get(type);
        if (result == null) {
          DexType baseType = type.toBaseType(dexItemFactory);
          DexType newType = lookupType(baseType);
          result = baseType == newType ? type : type.replaceBaseType(newType, dexItemFactory);
          arrayTypeCache.put(type, result);
        }
        return result;
      }
      return lookupClassType(type);
    }

    @Override
    public final DexType lookupClassType(DexType type) {
      assert type.isClassType() : "Expected class type, but was `" + type.toSourceString() + "`";
      return internalDescribeLookupClassType(getPrevious().lookupClassType(type));
    }

    @Override
    protected FieldLookupResult internalLookupField(
        DexField reference, LookupFieldContinuation continuation) {
      return previousLens.internalLookupField(
          reference, previous -> continuation.lookupField(internalDescribeLookupField(previous)));
    }

    @Override
    protected MethodLookupResult internalLookupMethod(
        DexMethod reference, DexMethod context, Type type, LookupMethodContinuation continuation) {
      return previousLens.internalLookupMethod(
          reference,
          internalGetPreviousMethodSignature(context),
          type,
          previous -> continuation.lookupMethod(internalDescribeLookupMethod(previous, context)));
    }

    protected abstract FieldLookupResult internalDescribeLookupField(FieldLookupResult previous);

    protected abstract MethodLookupResult internalDescribeLookupMethod(
        MethodLookupResult previous, DexMethod context);

    protected abstract DexType internalDescribeLookupClassType(DexType previous);

    protected abstract DexMethod internalGetPreviousMethodSignature(DexMethod method);

    @Override
    public final boolean isIdentityLens() {
      return false;
    }

    @Override
    public final boolean isNonIdentityLens() {
      return true;
    }

    @Override
    public final NonIdentityGraphLens asNonIdentityLens() {
      return this;
    }
  }

  private static final class IdentityGraphLens extends GraphLens {

    private static IdentityGraphLens INSTANCE = new IdentityGraphLens();

    private IdentityGraphLens() {}

    private static IdentityGraphLens getInstance() {
      return INSTANCE;
    }

    @Override
    public boolean isIdentityLens() {
      return true;
    }

    @Override
    public boolean isNonIdentityLens() {
      return false;
    }

    @Override
    public DexType getOriginalType(DexType type) {
      return type;
    }

    @Override
    public Iterable<DexType> getOriginalTypes(DexType type) {
      return IterableUtils.singleton(type);
    }

    @Override
    public DexField getOriginalFieldSignature(DexField field) {
      return field;
    }

    @Override
    public DexMethod getOriginalMethodSignature(DexMethod method) {
      return method;
    }

    @Override
    public DexField getRenamedFieldSignature(DexField originalField) {
      return originalField;
    }

    @Override
    public DexMethod getRenamedMethodSignature(DexMethod originalMethod, GraphLens applied) {
      return originalMethod;
    }

    @Override
    public String lookupPackageName(String pkg) {
      return pkg;
    }

    @Override
    public DexType lookupType(DexType type) {
      return type;
    }

    @Override
    public DexType lookupClassType(DexType type) {
      assert type.isClassType();
      return type;
    }

    @Override
    public MethodLookupResult lookupMethod(DexMethod method, DexMethod context, Type type) {
      return MethodLookupResult.builder(this).setReference(method).setType(type).build();
    }

    @Override
    public RewrittenPrototypeDescription lookupPrototypeChangesForMethodDefinition(
        DexMethod method) {
      return RewrittenPrototypeDescription.none();
    }

    @Override
    protected FieldLookupResult internalLookupField(
        DexField reference, LookupFieldContinuation continuation) {
      // Passes the field reference back to the next graph lens. The identity lens intentionally
      // does not set the rebound field reference, since it does not know what that is.
      return continuation.lookupField(
          FieldLookupResult.builder(this).setReference(reference).build());
    }

    @Override
    protected MethodLookupResult internalLookupMethod(
        DexMethod reference, DexMethod context, Type type, LookupMethodContinuation continuation) {
      // Passes the method reference back to the next graph lens. The identity lens intentionally
      // does not set the rebound method reference, since it does not know what that is.
      return continuation.lookupMethod(
          MethodLookupResult.builder(this).setReference(reference).setType(type).build());
    }

    @Override
    public boolean isContextFreeForMethods() {
      return true;
    }

    @Override
    public boolean hasCodeRewritings() {
      return false;
    }
  }

  // This lens clears all code rewriting (lookup methods mimics identity lens behavior) but still
  // relies on the previous lens for names (getRenamed/Original methods).
  public static class ClearCodeRewritingGraphLens extends NonIdentityGraphLens {

    public ClearCodeRewritingGraphLens(DexItemFactory dexItemFactory, GraphLens previousLens) {
      super(dexItemFactory, previousLens);
    }

    @Override
    public DexType getOriginalType(DexType type) {
      return getPrevious().getOriginalType(type);
    }

    @Override
    public Iterable<DexType> getOriginalTypes(DexType type) {
      return getPrevious().getOriginalTypes(type);
    }

    @Override
    public DexField getOriginalFieldSignature(DexField field) {
      return getPrevious().getOriginalFieldSignature(field);
    }

    @Override
    public DexMethod getOriginalMethodSignature(DexMethod method) {
      return getPrevious().getOriginalMethodSignature(method);
    }

    @Override
    public DexField getRenamedFieldSignature(DexField originalField) {
      return getPrevious().getRenamedFieldSignature(originalField);
    }

    @Override
    public DexMethod getRenamedMethodSignature(DexMethod originalMethod, GraphLens applied) {
      return this != applied
          ? getPrevious().getRenamedMethodSignature(originalMethod, applied)
          : originalMethod;
    }

    @Override
    public RewrittenPrototypeDescription lookupPrototypeChangesForMethodDefinition(
        DexMethod method) {
      return getIdentityLens().lookupPrototypeChangesForMethodDefinition(method);
    }

    @Override
    public final DexType internalDescribeLookupClassType(DexType previous) {
      return previous;
    }

    @Override
    protected FieldLookupResult internalLookupField(
        DexField reference, LookupFieldContinuation continuation) {
      return getIdentityLens().internalLookupField(reference, continuation);
    }

    @Override
    protected FieldLookupResult internalDescribeLookupField(FieldLookupResult previous) {
      throw new Unreachable();
    }

    @Override
    protected MethodLookupResult internalLookupMethod(
        DexMethod reference, DexMethod context, Type type, LookupMethodContinuation continuation) {
      return getIdentityLens().internalLookupMethod(reference, context, type, continuation);
    }

    @Override
    public MethodLookupResult internalDescribeLookupMethod(
        MethodLookupResult previous, DexMethod context) {
      throw new Unreachable();
    }

    @Override
    protected DexMethod internalGetPreviousMethodSignature(DexMethod method) {
      return method;
    }

    @Override
    public boolean isContextFreeForMethods() {
      return getIdentityLens().isContextFreeForMethods();
    }
  }
}
