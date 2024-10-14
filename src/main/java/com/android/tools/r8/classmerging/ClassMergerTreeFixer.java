// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.classmerging;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DefaultInstanceInitializerCode;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexMethodSignature;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexTypeList;
import com.android.tools.r8.graph.EnclosingMethodAttribute;
import com.android.tools.r8.graph.ImmediateProgramSubtypingInfo;
import com.android.tools.r8.graph.classmerging.MergedClasses;
import com.android.tools.r8.graph.fixup.TreeFixerBase;
import com.android.tools.r8.horizontalclassmerging.IncompleteHorizontalClassMergerCode;
import com.android.tools.r8.shaking.AnnotationFixer;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.ArrayUtils;
import com.android.tools.r8.utils.OptionalBool;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.collections.BidirectionalOneToOneHashMap;
import com.android.tools.r8.utils.collections.DexMethodSignatureBiMap;
import com.android.tools.r8.utils.collections.DexMethodSignatureSet;
import com.android.tools.r8.utils.collections.MutableBidirectionalOneToOneMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public abstract class ClassMergerTreeFixer<
        LB extends ClassMergerGraphLens.BuilderBase<GL, MC>,
        GL extends ClassMergerGraphLens,
        MC extends MergedClasses>
    extends TreeFixerBase {

  private final ClassMergerSharedData classMergerSharedData;
  protected final ImmediateProgramSubtypingInfo immediateSubtypingInfo;
  protected final LB lensBuilder;
  protected final MC mergedClasses;

  private final Map<DexProgramClass, DexType> originalSuperTypes = new IdentityHashMap<>();

  protected final DexMethodSignatureSet keptSignatures = DexMethodSignatureSet.create();
  private final DexMethodSignatureBiMap<DexMethodSignature> reservedInterfaceSignatures =
      new DexMethodSignatureBiMap<>();

  public ClassMergerTreeFixer(
      AppView<?> appView,
      ClassMergerSharedData classMergerSharedData,
      ImmediateProgramSubtypingInfo immediateSubtypingInfo,
      LB lensBuilder,
      MC mergedClasses) {
    super(appView);
    this.classMergerSharedData = classMergerSharedData;
    this.immediateSubtypingInfo = immediateSubtypingInfo;
    this.lensBuilder = lensBuilder;
    this.mergedClasses = mergedClasses;
  }

  public GL run(ExecutorService executorService, Timing timing) throws ExecutionException {
    if (!appView.enableWholeProgramOptimizations()) {
      return timing.time("Fixup", () -> lensBuilder.build(appView, mergedClasses));
    }
    timing.begin("Fixup");
    AppView<AppInfoWithLiveness> appViewWithLiveness = appView.withLiveness();
    preprocess();
    Collection<DexProgramClass> classes = appView.appInfo().classesWithDeterministicOrder();
    Set<DexProgramClass> seen = Sets.newIdentityHashSet();
    Iterables.filter(classes, DexProgramClass::isInterface)
        .forEach(itf -> fixupInterfaceClass(itf, seen));
    classes.forEach(this::fixupAttributes);
    classes.forEach(this::fixupProgramClassSuperTypes);

    // TODO(b/170078037): parallelize this code segment.
    for (DexProgramClass root : getRoots()) {
      traverseProgramClassesDepthFirst(root, seen, new DexMethodSignatureBiMap<>());
    }
    assert seen.containsAll(appView.appInfo().classes());
    postprocess();
    GL lens = lensBuilder.build(appViewWithLiveness, mergedClasses);
    new AnnotationFixer(appView, lens).run(appView.appInfo().classes(), executorService);
    timing.end();
    return lens;
  }

  private List<DexProgramClass> getRoots() {
    List<DexProgramClass> roots = new ArrayList<>();
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      if (isRoot(clazz)) {
        roots.add(clazz);
      }
    }
    return roots;
  }

  protected boolean isRoot(DexProgramClass clazz) {
    if (clazz.isInterface()) {
      return false;
    }
    if (clazz.hasSuperType()) {
      DexProgramClass superClass =
          asProgramClassOrNull(appView.definitionFor(clazz.getSuperType(), clazz));
      return superClass == null;
    }
    return true;
  }

  protected abstract void traverseProgramClassesDepthFirst(
      DexProgramClass clazz,
      Set<DexProgramClass> seen,
      DexMethodSignatureBiMap<DexMethodSignature> state);

  public void preprocess() {
    // Intentionally empty.
  }

  public void postprocess() {
    // Intentionally empty.
  }

  public void fixupAttributes(DexProgramClass clazz) {
    if (clazz.hasEnclosingMethodAttribute()) {
      EnclosingMethodAttribute enclosingMethodAttribute = clazz.getEnclosingMethodAttribute();
      if (mergedClasses.isMergeSource(enclosingMethodAttribute.getEnclosingType())) {
        clazz.clearEnclosingMethodAttribute();
      } else {
        clazz.setEnclosingMethodAttribute(fixupEnclosingMethodAttribute(enclosingMethodAttribute));
      }
    }
    clazz.setInnerClasses(fixupInnerClassAttributes(clazz.getInnerClasses()));
    clazz.setNestHostAttribute(fixupNestHost(clazz.getNestHostClassAttribute()));
    clazz.setNestMemberAttributes(fixupNestMemberAttributes(clazz.getNestMembersClassAttributes()));
    clazz.setPermittedSubclassAttributes(
        fixupPermittedSubclassAttribute(clazz.getPermittedSubclassAttributes()));
  }

  private void fixupProgramClassSuperTypes(DexProgramClass clazz) {
    if (clazz.hasSuperType()) {
      DexType rewrittenSuperType = fixupType(clazz.getSuperType());
      if (rewrittenSuperType.isNotIdenticalTo(clazz.getSuperType())) {
        originalSuperTypes.put(clazz, clazz.getSuperType());
        clazz.setSuperType(rewrittenSuperType);
      }
    }
    clazz.setInterfaces(fixupInterfaces(clazz, clazz.getInterfaces()));
  }

  protected DexMethodSignatureBiMap<DexMethodSignature> fixupProgramClass(
      DexProgramClass clazz, DexMethodSignatureBiMap<DexMethodSignature> remappedVirtualMethods) {
    assert !clazz.isInterface();

    MutableBidirectionalOneToOneMap<DexEncodedMethod, DexMethodSignature> newMethodSignatures =
        createLocallyReservedMethodSignatures(clazz, remappedVirtualMethods);
    DexMethodSignatureBiMap<DexMethodSignature> remappedClassVirtualMethods =
        new DexMethodSignatureBiMap<>(remappedVirtualMethods);
    clazz
        .getMethodCollection()
        .replaceAllVirtualMethods(
            method ->
                fixupVirtualMethod(
                    clazz, method, remappedClassVirtualMethods, newMethodSignatures));
    clazz
        .getMethodCollection()
        .replaceAllDirectMethods(
            method ->
                fixupDirectMethod(clazz, method, remappedClassVirtualMethods, newMethodSignatures));

    Set<DexField> newFieldReferences = Sets.newIdentityHashSet();
    DexEncodedField[] instanceFields = clazz.clearInstanceFields();
    DexEncodedField[] staticFields = clazz.clearStaticFields();
    clazz.setInstanceFields(fixupFields(instanceFields, newFieldReferences));
    clazz.setStaticFields(fixupFields(staticFields, newFieldReferences));

    lensBuilder.commitPendingUpdates();

    return remappedClassVirtualMethods;
  }

  private DexEncodedMethod fixupVirtualInterfaceMethod(DexEncodedMethod method) {
    if (keptSignatures.contains(method)) {
      return method;
    }

    // Don't process this method if it does not refer to a merge class type.
    DexMethod originalMethodReference = method.getReference();
    boolean referencesMergeClass =
        Iterables.any(
            originalMethodReference.getReferencedBaseTypes(dexItemFactory),
            mergedClasses::isMergeSourceOrTarget);
    if (!referencesMergeClass) {
      return method;
    }

    DexMethodSignature originalMethodSignature = originalMethodReference.getSignature();
    DexMethodSignature newMethodSignature =
        reservedInterfaceSignatures.get(originalMethodSignature);

    if (newMethodSignature == null) {
      newMethodSignature = fixupMethodReference(originalMethodReference).getSignature();

      // If the signature is kept or already reserved by another interface, find a fresh one.
      if (keptSignatures.contains(newMethodSignature)
          || reservedInterfaceSignatures.containsValue(newMethodSignature)) {
        DexString name =
            dexItemFactory.createGloballyFreshMemberString(
                originalMethodReference.getName().toSourceString());
        newMethodSignature = newMethodSignature.withName(name);
      }

      assert !reservedInterfaceSignatures.containsValue(newMethodSignature);
      reservedInterfaceSignatures.put(originalMethodSignature, newMethodSignature);
    }

    DexMethod newMethodReference =
        newMethodSignature.withHolder(originalMethodReference, dexItemFactory);
    lensBuilder.fixupMethod(originalMethodReference, newMethodReference);
    return newMethodReference.isNotIdenticalTo(originalMethodReference)
        ? method.toTypeSubstitutedMethodAsInlining(newMethodReference, dexItemFactory)
        : method;
  }

  private void fixupInterfaceClass(DexProgramClass iface, Set<DexProgramClass> seen) {
    assert seen.add(iface);
    DexMethodSignatureBiMap<DexMethodSignature> remappedVirtualMethods =
        DexMethodSignatureBiMap.empty();
    MutableBidirectionalOneToOneMap<DexEncodedMethod, DexMethodSignature> newMethodSignatures =
        new BidirectionalOneToOneHashMap<>();
    iface
        .getMethodCollection()
        .replaceDirectMethods(
            method ->
                fixupDirectMethod(iface, method, remappedVirtualMethods, newMethodSignatures));
    iface.getMethodCollection().replaceVirtualMethods(this::fixupVirtualInterfaceMethod);

    assert !iface.hasInstanceFields();

    Set<DexField> newFieldReferences = Sets.newIdentityHashSet();
    DexEncodedField[] staticFields = iface.clearStaticFields();
    iface.setStaticFields(fixupFields(staticFields, newFieldReferences));

    lensBuilder.commitPendingUpdates();
  }

  private DexTypeList fixupInterfaces(DexProgramClass clazz, DexTypeList interfaceTypes) {
    Set<DexType> seen = Sets.newIdentityHashSet();
    return interfaceTypes.map(
        interfaceType -> {
          DexType rewrittenInterfaceType = mapClassType(interfaceType);
          assert rewrittenInterfaceType.isNotIdenticalTo(clazz.getType());
          return seen.add(rewrittenInterfaceType) ? rewrittenInterfaceType : null;
        });
  }

  private DexEncodedMethod fixupProgramMethod(
      DexProgramClass clazz, DexEncodedMethod method, DexMethod newMethodReference) {
    // Convert out of DefaultInstanceInitializerCode, since this piece of code will require lens
    // code rewriting.
    if (method.hasCode()
        && method.getCode().isDefaultInstanceInitializerCode()
        && mergedClasses.isMergeSourceOrTarget(clazz.getSuperType())) {
      DexType originalSuperType = originalSuperTypes.getOrDefault(clazz, clazz.getSuperType());
      DefaultInstanceInitializerCode.uncanonicalizeCode(
          appView, method.asProgramMethod(clazz), originalSuperType);
    }

    DexMethod originalMethodReference = method.getReference();
    if (newMethodReference.isIdenticalTo(originalMethodReference)) {
      return method;
    }

    lensBuilder.fixupMethod(originalMethodReference, newMethodReference);

    DexEncodedMethod newMethod =
        method.toTypeSubstitutedMethodAsInlining(newMethodReference, dexItemFactory);
    if (newMethod.isNonPrivateVirtualMethod()) {
      // Since we changed the return type or one of the parameters, this method cannot be a
      // classpath or library method override, since we only class merge program classes.
      assert !method.isLibraryMethodOverride().isTrue();
      newMethod.setLibraryMethodOverride(OptionalBool.FALSE);
    }

    return newMethod;
  }

  private DexEncodedMethod fixupDirectMethod(
      DexProgramClass clazz,
      DexEncodedMethod method,
      DexMethodSignatureBiMap<DexMethodSignature> remappedVirtualMethods,
      MutableBidirectionalOneToOneMap<DexEncodedMethod, DexMethodSignature> newMethodSignatures) {
    DexMethod originalMethodReference = method.getReference();

    // Fix all type references in the method prototype.
    DexMethod newMethodReference;
    if (keptSignatures.contains(method)) {
      newMethodReference = method.getReference();
    } else {
      DexMethodSignature reservedMethodSignature = newMethodSignatures.get(method);
      if (reservedMethodSignature != null) {
        newMethodReference = reservedMethodSignature.withHolder(clazz, dexItemFactory);
      } else {
        newMethodReference = fixupMethodReference(originalMethodReference);
        if (keptSignatures.contains(newMethodReference)
            || newMethodSignatures.containsValue(newMethodReference.getSignature())) {
          // If the method collides with a direct method on the same class then rename it to a
          // globally fresh name and record the signature.
          if (method.isInstanceInitializer()) {
            // If the method is an instance initializer, then add extra unused arguments.
            newMethodReference =
                dexItemFactory.createInstanceInitializerWithFreshProto(
                    newMethodReference,
                    classMergerSharedData.getExtraUnusedArgumentTypes(),
                    tryMethod -> !newMethodSignatures.containsValue(tryMethod.getSignature()));
            if (method.getCode() instanceof IncompleteHorizontalClassMergerCode) {
              IncompleteHorizontalClassMergerCode code =
                  (IncompleteHorizontalClassMergerCode) method.getCode();
              code.addExtraUnusedArguments(
                  newMethodReference.getArity() - originalMethodReference.getArity());
            }
          } else {
            newMethodReference =
                dexItemFactory.createFreshMethodNameWithoutHolder(
                    newMethodReference.getName().toSourceString(),
                    newMethodReference.getProto(),
                    newMethodReference.getHolderType(),
                    tryMethod ->
                        !keptSignatures.contains(tryMethod)
                            && !reservedInterfaceSignatures.containsValue(tryMethod.getSignature())
                            && !remappedVirtualMethods.containsValue(tryMethod.getSignature())
                            && !newMethodSignatures.containsValue(tryMethod.getSignature()));
          }
        }

        assert !newMethodSignatures.containsValue(newMethodReference.getSignature());
        newMethodSignatures.put(method, newMethodReference.getSignature());
      }
    }

    return fixupProgramMethod(clazz, method, newMethodReference);
  }

  private MutableBidirectionalOneToOneMap<DexEncodedMethod, DexMethodSignature>
      createLocallyReservedMethodSignatures(
          DexProgramClass clazz,
          DexMethodSignatureBiMap<DexMethodSignature> remappedVirtualMethods) {
    MutableBidirectionalOneToOneMap<DexEncodedMethod, DexMethodSignature> newMethodSignatures =
        new BidirectionalOneToOneHashMap<>();
    for (DexEncodedMethod method : clazz.methods()) {
      if (method.belongsToVirtualPool()) {
        DexMethodSignature reservedMethodSignature =
            lookupReservedVirtualName(method, remappedVirtualMethods);
        if (reservedMethodSignature != null) {
          newMethodSignatures.put(method, reservedMethodSignature);
          continue;
        }
      }
      // Reserve the method signature if it is unchanged and not globally reserved.
      DexMethodSignature newMethodSignature = fixupMethodSignature(method);
      if (newMethodSignature.equals(method.getName(), method.getProto())
          && !reservedInterfaceSignatures.containsValue(newMethodSignature)
          && !remappedVirtualMethods.containsValue(newMethodSignature)) {
        newMethodSignatures.put(method, newMethodSignature);
      }
    }
    return newMethodSignatures;
  }

  private DexMethodSignature lookupReservedVirtualName(
      DexEncodedMethod method,
      DexMethodSignatureBiMap<DexMethodSignature> renamedClassVirtualMethods) {
    DexMethodSignature originalSignature = method.getSignature();

    // Determine if the original method has been rewritten by a parent class
    DexMethodSignature renamedVirtualName = renamedClassVirtualMethods.get(originalSignature);
    if (renamedVirtualName == null) {
      // Determine if there is a signature mapping.
      DexMethodSignature mappedInterfaceSignature =
          reservedInterfaceSignatures.get(originalSignature);
      if (mappedInterfaceSignature != null) {
        renamedVirtualName = mappedInterfaceSignature;
      }
    } else {
      assert !reservedInterfaceSignatures.containsKey(originalSignature);
    }
    return renamedVirtualName;
  }

  private DexEncodedMethod fixupVirtualMethod(
      DexProgramClass clazz,
      DexEncodedMethod method,
      DexMethodSignatureBiMap<DexMethodSignature> renamedClassVirtualMethods,
      MutableBidirectionalOneToOneMap<DexEncodedMethod, DexMethodSignature> newMethodSignatures) {
    DexMethodSignature newSignature;
    if (keptSignatures.contains(method)) {
      newSignature = method.getSignature();
    } else {
      newSignature = newMethodSignatures.get(method);
      if (newSignature == null) {
        // Fix all type references in the method prototype.
        newSignature =
            dexItemFactory.createFreshMethodSignatureName(
                method.getName().toSourceString(),
                null,
                fixupProto(method.getProto()),
                trySignature ->
                    !keptSignatures.contains(trySignature)
                        && !reservedInterfaceSignatures.containsValue(trySignature)
                        && !newMethodSignatures.containsValue(trySignature)
                        && !renamedClassVirtualMethods.containsValue(trySignature));
        newMethodSignatures.put(method, newSignature);
      }
    }

    // If any of the parameter types have been merged, record the signature mapping so that
    // subclasses perform the identical rename.
    if (!keptSignatures.contains(method)
        && !reservedInterfaceSignatures.containsKey(method)
        && Iterables.any(
            newSignature.getProto().getBaseTypes(dexItemFactory), mergedClasses::isMergeTarget)) {
      renamedClassVirtualMethods.put(method.getSignature(), newSignature);
    }

    DexMethod newMethodReference = newSignature.withHolder(clazz, dexItemFactory);
    return fixupProgramMethod(clazz, method, newMethodReference);
  }

  @SuppressWarnings("ReferenceEquality")
  private DexEncodedField[] fixupFields(
      DexEncodedField[] fields, Set<DexField> newFieldReferences) {
    if (fields == null || ArrayUtils.isEmpty(fields)) {
      return DexEncodedField.EMPTY_ARRAY;
    }

    DexEncodedField[] newFields = new DexEncodedField[fields.length];
    for (int i = 0; i < fields.length; i++) {
      DexEncodedField oldField = fields[i];
      DexField oldFieldReference = oldField.getReference();
      DexField newFieldReference = fixupFieldReference(oldFieldReference);

      // Rename the field if it already exists.
      if (!newFieldReferences.add(newFieldReference)) {
        DexField template = newFieldReference;
        newFieldReference =
            dexItemFactory.createFreshMember(
                tryName ->
                    Optional.of(template.withName(tryName, dexItemFactory))
                        .filter(tryMethod -> !newFieldReferences.contains(tryMethod)),
                newFieldReference.name.toSourceString());
        boolean added = newFieldReferences.add(newFieldReference);
        assert added;
      }

      if (newFieldReference != oldFieldReference) {
        lensBuilder.fixupField(oldFieldReference, newFieldReference);
        newFields[i] = oldField.toTypeSubstitutedField(appView, newFieldReference);
      } else {
        newFields[i] = oldField;
      }
    }

    return newFields;
  }

  @Override
  public DexType mapClassType(DexType type) {
    return mergedClasses.getMergeTargetOrDefault(type, type);
  }

  @Override
  public void recordClassChange(DexType from, DexType to) {
    // Classes are not changed but in-place updated.
    throw new Unreachable();
  }

  @Override
  public void recordFieldChange(DexField from, DexField to) {
    // Fields are manually changed in 'fixupFields' above.
    throw new Unreachable();
  }

  @Override
  public void recordMethodChange(DexMethod from, DexMethod to) {
    // Methods are manually changed in 'fixupMethods' above.
    throw new Unreachable();
  }
}
