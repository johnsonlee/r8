// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming;

import static com.android.tools.r8.graph.DexApplication.classesWithDeterministicOrder;
import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;
import static com.android.tools.r8.ir.desugar.itf.InterfaceDesugaringSyntheticHelper.defaultAsMethodOfCompanionClass;
import static com.android.tools.r8.ir.desugar.itf.InterfaceDesugaringSyntheticHelper.getInterfaceClassType;
import static com.android.tools.r8.ir.desugar.itf.InterfaceDesugaringSyntheticHelper.isCompanionClassType;
import static com.android.tools.r8.ir.desugar.itf.InterfaceDesugaringSyntheticHelper.staticAsMethodOfCompanionClass;
import static com.android.tools.r8.naming.Minifier.renameNonReboundReferences;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndField;
import com.android.tools.r8.graph.DexClassAndMember;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ImmediateAppSubtypingInfo;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramOrClasspathClass;
import com.android.tools.r8.naming.ClassNameMinifier.ClassRenaming;
import com.android.tools.r8.naming.FieldNameMinifier.FieldRenaming;
import com.android.tools.r8.naming.MemberNaming.FieldSignature;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.naming.MemberNaming.Signature;
import com.android.tools.r8.naming.MethodNameMinifier.MethodRenaming;
import com.android.tools.r8.naming.Minifier.MinificationClassNamingStrategy;
import com.android.tools.r8.naming.Minifier.MinifierMemberNamingStrategy;
import com.android.tools.r8.position.Position;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.timing.Timing;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/**
 * The ProguardMapMinifier will assign names to classes and members following the initial naming
 * seed given by the mapping files.
 *
 * <p>First the object hierarchy is traversed maintaining a collection of all program classes and
 * classes that needs to be renamed in {@link #mappedClasses}. For each level we keep track of all
 * renamed members and propagate all non-private items to descendants. This is necessary to ensure
 * that virtual methods are renamed when there are "gaps" in the hierarchy. We keep track of all
 * namings such that future renaming of non-private members will not collide or fail with an error.
 *
 * <p>Second, we compute desugared default interface methods and companion classes to ensure these
 * can be referred to by clients.
 *
 * <p>Third, we traverse all reachable interfaces for class mappings and add them to our tracking
 * maps. Otherwise, the minification follows the ordinary minification.
 */
public class ProguardMapMinifier {

  private final AppView<AppInfoWithLiveness> appView;
  private final DexItemFactory factory;
  private final SeedMapper seedMapper;
  private final BiMap<DexType, DexString> mappedNames = HashBiMap.create();
  // To keep the order deterministic, we sort the classes by their type, which is a unique key.
  private final Set<ProgramOrClasspathClass> mappedClasses = Sets.newIdentityHashSet();
  private final Map<DexReference, MemberNaming> memberNames = Maps.newIdentityHashMap();
  private final Map<DexMethod, DexString> companionClassInterfaceMethodImplementationNames =
      Maps.newIdentityHashMap();
  private final Map<DexMethod, DexString> additionalMethodNamings = Maps.newIdentityHashMap();
  private final Map<DexField, DexString> additionalFieldNamings = Maps.newIdentityHashMap();

  public ProguardMapMinifier(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
    this.factory = appView.dexItemFactory();
    this.seedMapper = appView.getApplyMappingSeedMapper();
    assert seedMapper != null;
  }

  public NamingLens run(ExecutorService executorService, Timing timing) throws ExecutionException {
    ArrayDeque<Map<DexReference, MemberNaming>> nonPrivateMembers = new ArrayDeque<>();
    Set<DexReference> notMappedReferences = new HashSet<>();
    ImmediateAppSubtypingInfo subtypingInfo = ImmediateAppSubtypingInfo.create(appView);
    timing.begin("MappingInterfaces");
    List<DexClass> interfaces = subtypingInfo.computeReachableInterfacesWithDeterministicOrder();
    interfaces.forEach(
        iface -> computeMapping(iface, nonPrivateMembers, notMappedReferences, subtypingInfo));
    timing.end();
    timing.begin("MappingClasses");
    mappedClasses.addAll(appView.appInfo().classes());
    DexClass objectClass = appView.definitionFor(factory.objectType);
    for (DexClass subclass : subtypingInfo.getSubclasses(objectClass)) {
      if (!subclass.isInterface()) {
        computeMapping(subclass, nonPrivateMembers, notMappedReferences, subtypingInfo);
      }
    }
    assert nonPrivateMembers.isEmpty();
    timing.end();

    timing.begin("MappingDefaultInterfaceMethods");
    computeDefaultInterfaceMethodMethods();
    timing.end();

    appView.options().reporter.failIfPendingErrors();

    timing.begin("MinifyClasses");
    ClassNameMinifier classNameMinifier =
        new ClassNameMinifier(
            appView,
            new ApplyMappingClassNamingStrategy(
                appView, mappedNames, seedMapper.getMappedToDescriptorNames()),
            classesWithDeterministicOrder(mappedClasses));
    ClassRenaming classRenaming = classNameMinifier.computeRenaming(timing);
    timing.end();

    ApplyMappingMemberNamingStrategy nameStrategy =
        new ApplyMappingMemberNamingStrategy(appView, memberNames);
    timing.begin("MinifyMethods");
    MethodRenaming methodRenaming =
        new MethodNameMinifier(appView, nameStrategy)
            .computeRenaming(interfaces, subtypingInfo, timing);
    // Amend the method renamings with the default interface methods.
    methodRenaming.renaming.putAll(companionClassInterfaceMethodImplementationNames);
    methodRenaming.renaming.putAll(additionalMethodNamings);
    timing.end();

    timing.begin("MinifyFields");
    FieldRenaming fieldRenaming =
        new FieldNameMinifier(appView, subtypingInfo, nameStrategy)
            .computeRenaming(interfaces, timing);
    fieldRenaming.renaming.putAll(additionalFieldNamings);
    timing.end();

    // Rename the references that are not rebound to definitions.
    timing.begin("non-rebound-references");
    renameNonReboundReferences(appView, fieldRenaming, methodRenaming, executorService);
    timing.end();

    appView.options().reporter.failIfPendingErrors();

    NamingLens lens =
        new ProguardMapMinifiedRenaming(
            appView, classRenaming, methodRenaming, fieldRenaming, notMappedReferences);
    appView.notifyOptimizationFinishedForTesting();
    return lens;
  }

  private void computeMapping(
      DexClass clazz,
      Deque<Map<DexReference, MemberNaming>> buildUpNames,
      Set<DexReference> notMappedReferences,
      ImmediateAppSubtypingInfo subtypingInfo) {
    DexType type = clazz.getType();
    ClassNamingForMapApplier classNaming = seedMapper.getClassNaming(type);

    // Keep track of classpath classes that needs to get renamed.
    if (clazz.isClasspathClass() && classNaming != null) {
      mappedClasses.add(clazz.asClasspathClass());
    }

    Map<DexReference, MemberNaming> nonPrivateMembers = new IdentityHashMap<>();

    if (classNaming != null && !clazz.isLibraryClass()) {
      DexString mappedName = factory.createString(classNaming.renamedName);
      checkAndAddMappedNames(type, mappedName, classNaming.position);
      classNaming.forAllMemberNaming(
          memberNaming -> addMemberNamings(type, memberNaming, nonPrivateMembers, false));
    } else {
      // We have to ensure we do not rename to an existing member, that cannot be renamed.
      DexProgramClass programClass = asProgramClassOrNull(clazz);
      if (programClass == null
          || !appView.getKeepInfo(programClass).isMinificationAllowed(appView.options())) {
        notMappedReferences.add(type);
      }
    }

    for (Map<DexReference, MemberNaming> parentMembers : buildUpNames) {
      for (DexReference key : parentMembers.keySet()) {
        if (key.isDexMethod()) {
          DexMethod parentReference = key.asDexMethod();
          DexMethod parentReferenceOnCurrentType =
              factory.createMethod(type, parentReference.proto, parentReference.name);
          if (!memberNames.containsKey(parentReferenceOnCurrentType)) {
            addMemberNaming(
                parentReferenceOnCurrentType, parentMembers.get(key), additionalMethodNamings);
          } else {
            DexEncodedMethod method = clazz.lookupMethod(parentReferenceOnCurrentType);
            assert method == null
                || method.isStatic()
                || memberNames
                    .get(parentReferenceOnCurrentType)
                    .getRenamedName()
                    .equals(parentMembers.get(key).getRenamedName());
          }
        } else {
          DexField parentReference = key.asDexField();
          DexField parentReferenceOnCurrentType =
              factory.createField(type, parentReference.type, parentReference.name);
          if (!memberNames.containsKey(parentReferenceOnCurrentType)) {
            addMemberNaming(
                parentReferenceOnCurrentType, parentMembers.get(key), additionalFieldNamings);
          } else {
            // Fields are allowed to be named differently in the hierarchy.
          }
        }
      }
    }

    // If a class is marked as abstract it is allowed to not implement methods from interfaces
    // thus the map will not contain a mapping. Also, if an interface is defined in the library
    // and the class is in the program, we have to build up the correct names to reserve them.
    if (clazz.isProgramClass() || clazz.isAbstract()) {
      addNonPrivateInterfaceMappings(type, nonPrivateMembers, clazz.interfaces.values);
    }

    if (!nonPrivateMembers.isEmpty()) {
      buildUpNames.addLast(nonPrivateMembers);
      subtypingInfo.forEachImmediateSubClassMatching(
          clazz,
          subclass -> clazz.isInterface() == subclass.isInterface(),
          subclass -> computeMapping(subclass, buildUpNames, notMappedReferences, subtypingInfo));
      buildUpNames.removeLast();
    } else {
      subtypingInfo.forEachImmediateSubClassMatching(
          clazz,
          subclass -> clazz.isInterface() == subclass.isInterface(),
          subclass -> computeMapping(subclass, buildUpNames, notMappedReferences, subtypingInfo));
    }
  }

  private void addNonPrivateInterfaceMappings(
      DexType type, Map<DexReference, MemberNaming> nonPrivateMembers, DexType[] interfaces) {
    for (DexType iface : interfaces) {
      ClassNamingForMapApplier interfaceNaming = seedMapper.getClassNaming(iface);
      if (interfaceNaming != null) {
        interfaceNaming.forAllMemberNaming(
            memberNaming -> addMemberNamings(type, memberNaming, nonPrivateMembers, true));
      }
      DexClass ifaceClass = appView.definitionFor(iface);
      if (ifaceClass != null) {
        addNonPrivateInterfaceMappings(type, nonPrivateMembers, ifaceClass.interfaces.values);
      }
    }
  }

  private void addMemberNamings(
      DexType type,
      MemberNaming memberNaming,
      Map<DexReference, MemberNaming> nonPrivateMembers,
      boolean addToAdditionalMaps) {
    Signature signature = memberNaming.getOriginalSignature();
    assert !signature.isQualified();
    if (signature instanceof MethodSignature) {
      DexMethod originalMethod = ((MethodSignature) signature).toDexMethod(factory, type);
      addMemberNaming(
          originalMethod, memberNaming, addToAdditionalMaps ? additionalMethodNamings : null);
      DexClass holder = appView.definitionForHolder(originalMethod);
      DexEncodedMethod definition = originalMethod.lookupOnClass(holder);
      if (definition == null || !definition.accessFlags.isPrivate()) {
        nonPrivateMembers.put(originalMethod, memberNaming);
      }
    } else {
      DexField originalField = ((FieldSignature) signature).toDexField(factory, type);
      addMemberNaming(
          originalField, memberNaming, addToAdditionalMaps ? additionalFieldNamings : null);
      DexClass holder = appView.definitionForHolder(originalField);
      DexEncodedField field = originalField.lookupOnClass(holder);
      if (field == null || !field.isPrivate()) {
        nonPrivateMembers.put(originalField, memberNaming);
      }
    }
  }

  private <T extends DexReference> void addMemberNaming(
      T member, MemberNaming memberNaming, Map<T, DexString> additionalMemberNamings) {
    assert !memberNames.containsKey(member)
        || memberNames.get(member).getRenamedName().equals(memberNaming.getRenamedName());
    memberNames.put(member, memberNaming);
    if (additionalMemberNamings != null) {
      DexString renamedName = factory.createString(memberNaming.getRenamedName());
      additionalMemberNamings.put(member, renamedName);
    }
  }

  @SuppressWarnings("ReferenceEquality")
  private void checkAndAddMappedNames(DexType type, DexString mappedName, Position position) {
    if (mappedNames.inverse().containsKey(mappedName)
        && mappedNames.inverse().get(mappedName) != type) {
      appView
          .options()
          .reporter
          .error(
              ApplyMappingError.mapToExistingClass(
                  type.toString(), mappedName.toString(), position));
    } else {
      mappedNames.put(type, mappedName);
    }
  }

  private void computeDefaultInterfaceMethodMethods() {
    for (String key : seedMapper.getKeyset()) {
      ClassNamingForMapApplier classNaming = seedMapper.getMapping(key);
      DexType type = factory.lookupType(factory.createString(key));
      if (type == null) {
        // The map contains additional mapping of classes compared to what we have seen. This should
        // have no effect.
        continue;
      }
      // TODO(b/150736225): Is this sound? What if the type is a library type that has been pruned?
      DexClass dexClass = appView.appInfo().definitionForWithoutExistenceAssert(type);
      if (dexClass == null || dexClass.isClasspathClass()) {
        computeCompanionClassInterfaceMethodMappingsForType(
            type, classNaming, companionClassInterfaceMethodImplementationNames);
      }
    }
  }

  private void computeCompanionClassInterfaceMethodMappingsForType(
      DexType type,
      ClassNamingForMapApplier classNaming,
      Map<DexMethod, DexString> companionClassInterfaceMethodImplementationNames) {
    // If the class does not resolve, then check if it is a companion class for an interface on
    // the class path.
    if (!isCompanionClassType(type)) {
      return;
    }
    DexClass interfaceType = appView.definitionFor(getInterfaceClassType(type, factory));
    if (interfaceType == null || !interfaceType.isClasspathClass()) {
      return;
    }
    for (List<MemberNaming> namings : classNaming.getQualifiedMethodMembers().values()) {
      // If the qualified name has been mapped to multiple names we can't compute a mapping (and it
      // should not be possible that this is a default interface method in that case.)
      if (namings.size() != 1) {
        continue;
      }
      MemberNaming naming = namings.get(0);
      MethodSignature signature = (MethodSignature) naming.getOriginalSignature();
      if (signature.name.startsWith(interfaceType.type.toSourceString())) {
        DexMethod originalReference =
            signature.toUnqualified().toDexMethod(factory, interfaceType.type);
        DexEncodedMethod originalDefinition = interfaceType.lookupMethod(originalReference);
        if (originalDefinition == null) {
          assert false;
          continue;
        }
        DexMethod originalDesugaredMethod;
        if (originalDefinition.isStatic()) {
          originalDesugaredMethod = staticAsMethodOfCompanionClass(originalReference, factory);
        } else if (originalDefinition.isNonAbstractVirtualMethod()) {
          originalDesugaredMethod = defaultAsMethodOfCompanionClass(originalReference, factory);
        } else {
          // There is no implementation for private interface methods as those are not accessible
          // outside the interface itself and should never need to be represented for apply mapping.
          continue;
        }
        assert type.isIdenticalTo(originalDesugaredMethod.holder);
        DexString renamed = factory.createString(naming.getRenamedName());
        companionClassInterfaceMethodImplementationNames.put(originalDesugaredMethod, renamed);
      }
    }
  }

  static class ApplyMappingClassNamingStrategy extends MinificationClassNamingStrategy {

    private final Map<DexType, DexString> mappings;
    private final Set<String> mappedNames;

    ApplyMappingClassNamingStrategy(
        AppView<AppInfoWithLiveness> appView,
        Map<DexType, DexString> mappings,
        Set<String> mappedNames) {
      super(appView);
      this.mappings = mappings;
      this.mappedNames = mappedNames;
    }

    @Override
    public DexString next(
        DexType type, char[] packagePrefix, InternalNamingState state, Predicate<String> isUsed) {
      assert !mappings.containsKey(type);
      assert appView
          .getKeepInfo(appView.definitionFor(type).asProgramClass())
          .isMinificationAllowed(appView.options());
      return super.next(
          type,
          packagePrefix,
          state,
          candidate -> {
            if (mappedNames.contains(candidate)) {
              return true;
            }
            return isUsed.test(candidate);
          });
    }

    @Override
    public DexString reservedDescriptor(DexType type) {
      // TODO(b/136694827): Check for already used and report an error. It seems like this can be
      //  done already in the reservation step for classes since there is only one 'path', unlike
      //  members that can be reserved differently in the hierarchy.
      DexClass clazz = appView.appInfo().definitionForWithoutExistenceAssert(type);
      if (clazz == null) {
        return type.getDescriptor();
      }
      if (clazz.isProgramClass()) {
        DexProgramClass programClass = clazz.asProgramClass();
        if (appView.getKeepInfo(programClass).isMinificationAllowed(appView.options())) {
          return mappings.get(type);
        }
        // TODO(b/136694827): Report a warning here if in the mapping since the user may find this
        //  non intuitive.
      } else {
        if (mappings.containsKey(type)) {
          return mappings.get(type);
        }
      }
      return type.getDescriptor();
    }

    @Override
    public boolean isRenamedByApplyMapping(DexType type) {
      return mappings.containsKey(type);
    }
  }

  static class ApplyMappingMemberNamingStrategy extends MinifierMemberNamingStrategy {

    private final Map<DexReference, MemberNaming> mappedNames;
    private final DexItemFactory factory;

    public ApplyMappingMemberNamingStrategy(
        AppView<AppInfoWithLiveness> appView, Map<DexReference, MemberNaming> mappedNames) {
      super(appView);
      this.mappedNames = mappedNames;
      this.factory = appView.dexItemFactory();
    }

    @Override
    @SuppressWarnings("ReferenceEquality")
    public DexString next(
        DexClassAndMethod method,
        InternalNamingState internalState,
        BiPredicate<DexString, DexMethod> isAvailable) {
      DexMethod reference = method.getReference();
      DexString reservedName = getReservedName(method, reference.getName());
      DexString nextName;
      if (reservedName != null) {
        if (!isAvailable.test(reservedName, reference)) {
          reportReservationError(reference, reservedName);
        }
        nextName = reservedName;
      } else {
        assert !mappedNames.containsKey(reference);
        assert !method.isProgramMethod()
            || appView
                .getKeepInfo(method.asProgramMethod())
                .isMinificationAllowed(appView.options());
        nextName = super.next(method, internalState, isAvailable);
      }
      assert nextName.isIdenticalTo(reference.getName()) || !method.getDefinition().isInitializer();
      assert nextName.isIdenticalTo(reference.getName()) || !method.getHolder().isAnnotation();
      return nextName;
    }

    @Override
    public DexString next(
        ProgramField field,
        InternalNamingState internalState,
        BiPredicate<DexString, ProgramField> isAvailable) {
      DexField reference = field.getReference();
      DexString reservedName = getReservedName(field, reference.getName());
      if (reservedName != null) {
        if (!isAvailable.test(reservedName, field)) {
          reportReservationError(reference, reservedName);
        }
        return reservedName;
      }
      assert !mappedNames.containsKey(reference);
      assert appView.getKeepInfo(field).isMinificationAllowed(appView.options());
      return super.next(field, internalState, isAvailable);
    }

    @Override
    public DexString getReservedName(DexClassAndMethod method) {
      return getReservedName(method, method.getName());
    }

    @Override
    public DexString getReservedName(DexClassAndField field) {
      return getReservedName(field, field.getName());
    }

    private DexString getReservedName(DexClassAndMember<?, ?> definition, DexString name) {
      // Always consult the mapping for renamed members that are not on program path.
      DexReference reference = definition.getReference();
      if (definition.getHolder().isNotProgramClass()) {
        if (mappedNames.containsKey(reference)) {
          return factory.createString(mappedNames.get(reference).getRenamedName());
        }
        return name;
      }
      assert definition.isProgramMember();
      DexString reservedName =
          definition.isMethod()
              ? super.getReservedName(definition.asMethod())
              : super.getReservedName(definition.asField());
      if (reservedName != null) {
        return reservedName;
      }
      if (mappedNames.containsKey(reference)) {
        return factory.createString(mappedNames.get(reference).getRenamedName());
      }
      return null;
    }

    @Override
    public boolean allowMemberRenaming(DexClass clazz) {
      return true;
    }

    @SuppressWarnings("UnusedVariable")
    void reportReservationError(DexReference source, DexString name) {
      MemberNaming memberNaming = mappedNames.get(source);
      assert source.isDexMethod() || source.isDexField();
      ApplyMappingError applyMappingError =
          ApplyMappingError.mapToExistingMember(
              source.toSourceString(),
              name.toString(),
              memberNaming == null ? Position.UNKNOWN : memberNaming.getPosition());
      // TODO(b/136694827) Enable when we have proper support
      // reporter.error(applyMappingError);
    }
  }

  public static class ProguardMapMinifiedRenaming extends MinifiedRenaming {

    private final Set<DexReference> unmappedReferences;
    private final Map<DexString, DexType> classRenamingsMappingToDifferentName;

    ProguardMapMinifiedRenaming(
        AppView<? extends AppInfoWithClassHierarchy> appView,
        ClassRenaming classRenaming,
        MethodRenaming methodRenaming,
        FieldRenaming fieldRenaming,
        Set<DexReference> unmappedReferences) {
      super(appView, classRenaming, methodRenaming, fieldRenaming);
      this.unmappedReferences = unmappedReferences;
      classRenamingsMappingToDifferentName = new HashMap<>();
      classRenaming.classRenaming.forEach(
          (type, dexString) -> {
            if (type.getDescriptor().isNotIdenticalTo(dexString)) {
              classRenamingsMappingToDifferentName.put(dexString, type);
            }
          });
    }

    @Override
    protected DexString internalLookupClassDescriptor(DexType type) {
      checkForUseOfNotMappedReference(type);
      return super.internalLookupClassDescriptor(type);
    }

    private void checkForUseOfNotMappedReference(DexType type) {
      if (unmappedReferences.contains(type)
          && classRenamingsMappingToDifferentName.containsKey(type.descriptor)) {
        // Type is an unmapped reference and there is a mapping from some other type to this one.
        // We are emitting a warning here, since this will generally be undesired behavior.
        DexType mappedType = classRenamingsMappingToDifferentName.get(type.descriptor);
        appView
            .options()
            .reporter
            .error(
                ApplyMappingError.mapToExistingClass(
                    mappedType.toString(), type.toSourceString(), Position.UNKNOWN));
        // Remove the type to ensure us only reporting the error once.
        unmappedReferences.remove(type);
      }
    }
  }
}
