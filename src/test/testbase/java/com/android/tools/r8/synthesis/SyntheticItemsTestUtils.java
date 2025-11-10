// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.synthesis;

import static com.android.tools.r8.ir.desugar.itf.InterfaceDesugaringSyntheticHelper.PRIVATE_METHOD_PREFIX;
import static com.android.tools.r8.ir.desugar.nest.NestBasedAccessDesugaring.NEST_ACCESS_FIELD_GET_NAME_PREFIX;
import static com.android.tools.r8.ir.desugar.nest.NestBasedAccessDesugaring.NEST_ACCESS_FIELD_PUT_NAME_PREFIX;
import static com.android.tools.r8.ir.desugar.nest.NestBasedAccessDesugaring.NEST_ACCESS_METHOD_NAME_PREFIX;
import static com.android.tools.r8.ir.desugar.nest.NestBasedAccessDesugaring.NEST_ACCESS_STATIC_GET_FIELD_NAME_PREFIX;
import static com.android.tools.r8.ir.desugar.nest.NestBasedAccessDesugaring.NEST_ACCESS_STATIC_METHOD_NAME_PREFIX;
import static com.android.tools.r8.ir.desugar.nest.NestBasedAccessDesugaring.NEST_ACCESS_STATIC_PUT_FIELD_NAME_PREFIX;
import static com.android.tools.r8.utils.MapUtils.ignoreKey;
import static org.hamcrest.CoreMatchers.containsString;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.desugar.invokespecial.InvokeSpecialToSelfDesugaring;
import com.android.tools.r8.ir.desugar.itf.InterfaceDesugaringForTesting;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.FieldReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.references.TypeReference;
import com.android.tools.r8.synthesis.SyntheticNaming.Phase;
import com.android.tools.r8.synthesis.SyntheticNaming.SyntheticKind;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.hamcrest.Matcher;

// TODO(b/454846973): Instantiate this based on an output from D8/R8 so that this is completely
//  independent of the naming used.
public abstract class SyntheticItemsTestUtils {

  // Private copy of the synthetic namings. This is not the compiler instance, but checking on the
  // id/descriptor content is safe.
  private static final SyntheticNaming naming = new SyntheticNaming();

  public static SyntheticItemsTestUtils getDefaultSyntheticItemsTestUtils() {
    return new DefaultSyntheticItemsTestUtils();
  }

  public static String syntheticFileNameD8() {
    return "D8$$SyntheticClass";
  }

  public static String syntheticMethodName() {
    return SyntheticNaming.INTERNAL_SYNTHETIC_METHOD_NAME;
  }

  public static ClassReference syntheticCompanionClass(Class<?> clazz) {
    return syntheticCompanionClass(Reference.classFromClass(clazz));
  }

  public static ClassReference syntheticCompanionClass(ClassReference clazz) {
    return Reference.classFromDescriptor(
        InterfaceDesugaringForTesting.getCompanionClassDescriptor(clazz.getDescriptor()));
  }

  public static ClassReference syntheticClassWithMinimalName(ClassReference clazz, int id) {
    return SyntheticNaming.makeMinimalSyntheticReferenceForTest(clazz, Integer.toString(id));
  }

  private static ClassReference syntheticClass(ClassReference clazz, SyntheticKind kind, int id) {
    return SyntheticNaming.makeSyntheticReferenceForTest(clazz, kind, "" + id);
  }

  public final MethodReference syntheticBackportMethod(Class<?> clazz, int id, Method method) {
    return syntheticBackportMethod(Reference.classFromClass(clazz), id, method);
  }

  public final MethodReference syntheticBackportMethod(
      ClassReference classReference, int id, Method method) {
    MethodReference originalMethod = Reference.methodFromMethod(method);
    return syntheticBackportMethod(classReference, originalMethod, id);
  }

  public static MethodReference syntheticInvokeSpecialMethod(Method method) {
    MethodReference originalMethod = Reference.methodFromMethod(method);
    return Reference.method(
        originalMethod.getHolderClass(),
        InvokeSpecialToSelfDesugaring.INVOKE_SPECIAL_BRIDGE_PREFIX + method.getName(),
        originalMethod.getFormalTypes(),
        originalMethod.getReturnType());
  }

  public final MethodReference syntheticBackportWithForwardingMethod(
      ClassReference clazz, int id, MethodReference method) {
    // For backports with forwarding the backported method is not static, so the original method
    // signature has the receiver type pre-pended.
    ImmutableList.Builder<TypeReference> builder = ImmutableList.builder();
    builder.add(method.getHolderClass()).addAll(method.getFormalTypes());
    MethodReference methodWithReceiverForForwarding =
        Reference.method(
            method.getHolderClass(),
            method.getMethodName(),
            builder.build(),
            method.getReturnType());
    ClassReference syntheticBackportClass = syntheticBackportWithForwardingClass(clazz, id);
    if (syntheticBackportClass != null) {
      return Reference.methodFromDescriptor(
          syntheticBackportClass,
          syntheticMethodName(),
          methodWithReceiverForForwarding.getMethodDescriptor());
    }
    return null;
  }

  public final ClassReference syntheticBottomUpOutlineClass(Class<?> clazz, int id) {
    return syntheticBottomUpOutlineClass(Reference.classFromClass(clazz), id);
  }

  public abstract ClassReference syntheticBottomUpOutlineClass(ClassReference clazz, int id);

  public final ClassReference syntheticOutlineClass(Class<?> clazz, int id) {
    return syntheticOutlineClass(Reference.classFromClass(clazz), id);
  }

  public abstract ClassReference syntheticOutlineClass(ClassReference clazz, int id);

  public final ClassReference syntheticLambdaClass(Class<?> clazz, int id) {
    return syntheticLambdaClass(Reference.classFromClass(clazz), id);
  }

  public abstract ClassReference syntheticLambdaClass(ClassReference clazz, int id);

  public final ClassReference syntheticApiConversionClass(Class<?> clazz, int id) {
    return syntheticApiConversionClass(Reference.classFromClass(clazz), id);
  }

  public abstract ClassReference syntheticApiConversionClass(ClassReference classReference, int id);

  public final ClassReference syntheticApiOutlineClass(Class<?> clazz, int id) {
    return syntheticApiOutlineClass(Reference.classFromClass(clazz), id);
  }

  public abstract ClassReference syntheticApiOutlineClass(ClassReference classReference, int id);

  public final ClassReference syntheticBackportClass(Class<?> clazz, int id) {
    return syntheticBackportClass(Reference.classFromClass(clazz), id);
  }

  public abstract ClassReference syntheticBackportClass(ClassReference classReference, int id);

  public abstract MethodReference syntheticBackportMethod(
      ClassReference classReference, MethodReference originalMethod, int id);

  public final ClassReference syntheticBackportWithForwardingClass(Class<?> clazz, int id) {
    return syntheticBackportWithForwardingClass(Reference.classFromClass(clazz), id);
  }

  public abstract ClassReference syntheticBackportWithForwardingClass(
      ClassReference classReference, int id);

  public static ClassReference syntheticRecordTagClass() {
    return Reference.classFromDescriptor(DexItemFactory.recordTagDescriptorString);
  }

  public abstract ClassReference syntheticRecordHelperClass(ClassReference reference, int id);

  public abstract ClassReference syntheticTwrCloseResourceClass(ClassReference reference, int id);

  public abstract ClassReference syntheticAutoCloseableDispatcherClass(
      ClassReference classReference, int id);

  public abstract ClassReference syntheticAutoCloseableForwarderClass(
      ClassReference classReference, int id);

  public abstract ClassReference syntheticThrowIAEClass(ClassReference classReference, int id);

  public final MethodReference syntheticLambdaMethod(Class<?> clazz, int id, Method method) {
    ClassReference syntheticHolder = syntheticLambdaClass(clazz, id);
    MethodReference originalMethod = Reference.methodFromMethod(method);
    return Reference.methodFromDescriptor(
        syntheticHolder.getDescriptor(),
        originalMethod.getMethodName(),
        originalMethod.getMethodDescriptor());
  }

  public static ClassReference syntheticNestConstructorArgumentClass(
      ClassReference classReference) {
    return Reference.classFromDescriptor(
        SyntheticNaming.createDescriptor(
            "", naming.INIT_TYPE_ARGUMENT, classReference.getBinaryName(), ""));
  }

  public static MethodReference syntheticNestInstanceFieldGetter(Field field) {
    return syntheticNestInstanceFieldGetter(Reference.fieldFromField(field));
  }

  public static MethodReference syntheticNestInstanceFieldGetter(FieldReference fieldReference) {
    return Reference.method(
        fieldReference.getHolderClass(),
        NEST_ACCESS_FIELD_GET_NAME_PREFIX + fieldReference.getFieldName(),
        Collections.emptyList(),
        fieldReference.getFieldType());
  }

  public static MethodReference syntheticNestInstanceFieldSetter(Field field) {
    FieldReference fieldReference = Reference.fieldFromField(field);
    return Reference.method(
        fieldReference.getHolderClass(),
        NEST_ACCESS_FIELD_PUT_NAME_PREFIX + field.getName(),
        ImmutableList.of(fieldReference.getFieldType()),
        null);
  }

  public static MethodReference syntheticNestInstanceMethodAccessor(Method method) {
    MethodReference originalMethod = Reference.methodFromMethod(method);
    return Reference.methodFromDescriptor(
        originalMethod.getHolderClass(),
        NEST_ACCESS_METHOD_NAME_PREFIX + method.getName(),
        originalMethod.getMethodDescriptor());
  }

  public static MethodReference syntheticNestStaticFieldGetter(Field field) {
    FieldReference fieldReference = Reference.fieldFromField(field);
    return Reference.method(
        fieldReference.getHolderClass(),
        NEST_ACCESS_STATIC_GET_FIELD_NAME_PREFIX + field.getName(),
        Collections.emptyList(),
        fieldReference.getFieldType());
  }

  public static MethodReference syntheticNestStaticFieldSetter(Field field) {
    FieldReference fieldReference = Reference.fieldFromField(field);
    return Reference.method(
        fieldReference.getHolderClass(),
        NEST_ACCESS_STATIC_PUT_FIELD_NAME_PREFIX + field.getName(),
        ImmutableList.of(fieldReference.getFieldType()),
        null);
  }

  public static MethodReference syntheticNestStaticMethodAccessor(Method method) {
    MethodReference originalMethod = Reference.methodFromMethod(method);
    return Reference.methodFromDescriptor(
        originalMethod.getHolderClass(),
        NEST_ACCESS_STATIC_METHOD_NAME_PREFIX + method.getName(),
        originalMethod.getMethodDescriptor());
  }

  public final ClassReference syntheticNonStartupInStartupOutlineClass(Class<?> clazz, int id) {
    return syntheticNonStartupInStartupOutlineClass(Reference.classFromClass(clazz), id);
  }

  public abstract ClassReference syntheticNonStartupInStartupOutlineClass(
      ClassReference reference, int id);

  public static MethodReference syntheticPrivateInterfaceMethodAsCompanionMethod(Method method) {
    MethodReference originalMethod = Reference.methodFromMethod(method);
    ClassReference companionClassReference =
        syntheticCompanionClass(originalMethod.getHolderClass());
    return Reference.methodFromDescriptor(
        companionClassReference,
        PRIVATE_METHOD_PREFIX + method.getName(),
        originalMethod.getMethodDescriptor());
  }

  public static MethodReference syntheticStaticInterfaceMethodAsCompanionMethod(Method method) {
    MethodReference originalMethod = Reference.methodFromMethod(method);
    ClassReference companionClassReference =
        syntheticCompanionClass(originalMethod.getHolderClass());
    return Reference.methodFromDescriptor(
        companionClassReference, method.getName(), originalMethod.getMethodDescriptor());
  }

  public static ClassReference syntheticEnumUnboxingLocalUtilityClass(Class<?> clazz) {
    return Reference.classFromTypeName(
        clazz.getTypeName() + naming.ENUM_UNBOXING_LOCAL_UTILITY_CLASS.getDescriptor());
  }

  public static ClassReference syntheticEnumUnboxingSharedUtilityClass(Class<?> clazz) {
    return Reference.classFromTypeName(
        clazz.getTypeName() + naming.ENUM_UNBOXING_SHARED_UTILITY_CLASS.getDescriptor());
  }

  public static boolean isEnumUnboxingSharedUtilityClass(ClassReference reference) {
    return SyntheticNaming.isSynthetic(reference, null, naming.ENUM_UNBOXING_SHARED_UTILITY_CLASS);
  }

  public static boolean isInternalLambda(ClassReference reference) {
    return SyntheticNaming.isSynthetic(reference, Phase.INTERNAL, naming.LAMBDA);
  }

  public abstract boolean isExternalLambda(ClassReference reference);

  public abstract boolean isExternalStaticInterfaceCall(ClassReference reference);

  public static boolean isExternalTwrCloseMethod(ClassReference reference) {
    return SyntheticNaming.isSynthetic(reference, Phase.EXTERNAL, naming.TWR_CLOSE_RESOURCE);
  }

  public static boolean isMaybeExternalSuppressedExceptionMethod(ClassReference reference) {
    // The suppressed exception methods are grouped with the backports.
    return SyntheticNaming.isSynthetic(reference, Phase.EXTERNAL, naming.BACKPORT);
  }

  public abstract boolean isExternalOutlineClass(ClassReference reference);

  public abstract boolean isExternalApiOutlineClass(ClassReference reference);

  public static boolean isInitializerTypeArgument(ClassReference reference) {
    return SyntheticNaming.isSynthetic(reference, null, naming.INIT_TYPE_ARGUMENT);
  }

  public abstract boolean isExternalNonFixedInitializerTypeArgument(ClassReference reference);

  public static boolean isWrapper(ClassReference reference) {
    return SyntheticNaming.isSynthetic(reference, null, naming.WRAPPER)
        || SyntheticNaming.isSynthetic(reference, null, naming.VIVIFIED_WRAPPER);
  }

  public static Matcher<String> containsInternalSyntheticReference() {
    return containsString(SyntheticNaming.getPhaseSeparator(Phase.INTERNAL));
  }

  public static Matcher<String> containsExternalSyntheticReference() {
    return containsString(SyntheticNaming.getPhaseSeparator(Phase.EXTERNAL));
  }

  public static boolean isInternalThrowNSME(MethodReference method) {
    return SyntheticNaming.isSynthetic(method.getHolderClass(), Phase.INTERNAL, naming.THROW_NSME);
  }

  private static class DefaultSyntheticItemsTestUtils extends SyntheticItemsTestUtils {

    @Override
    public boolean isExternalOutlineClass(ClassReference reference) {
      return SyntheticNaming.isSynthetic(reference, Phase.EXTERNAL, naming.OUTLINE);
    }

    @Override
    public boolean isExternalApiOutlineClass(ClassReference reference) {
      return SyntheticNaming.isSynthetic(reference, Phase.EXTERNAL, naming.API_MODEL_OUTLINE);
    }

    @Override
    public boolean isExternalLambda(ClassReference reference) {
      return SyntheticNaming.isSynthetic(reference, Phase.EXTERNAL, naming.LAMBDA);
    }

    @Override
    public boolean isExternalNonFixedInitializerTypeArgument(ClassReference reference) {
      return SyntheticNaming.isSynthetic(
          reference, Phase.EXTERNAL, naming.NON_FIXED_INIT_TYPE_ARGUMENT);
    }

    @Override
    public boolean isExternalStaticInterfaceCall(ClassReference reference) {
      return SyntheticNaming.isSynthetic(reference, Phase.EXTERNAL, naming.STATIC_INTERFACE_CALL);
    }

    @Override
    public ClassReference syntheticApiConversionClass(ClassReference classReference, int id) {
      return syntheticClass(classReference, naming.API_CONVERSION, id);
    }

    @Override
    public ClassReference syntheticApiOutlineClass(ClassReference classReference, int id) {
      return syntheticClass(classReference, naming.API_MODEL_OUTLINE, id);
    }

    @Override
    public ClassReference syntheticAutoCloseableDispatcherClass(
        ClassReference classReference, int id) {
      return syntheticClass(classReference, naming.AUTOCLOSEABLE_DISPATCHER, id);
    }

    @Override
    public ClassReference syntheticAutoCloseableForwarderClass(
        ClassReference classReference, int id) {
      return syntheticClass(classReference, naming.AUTOCLOSEABLE_FORWARDER, id);
    }

    @Override
    public ClassReference syntheticBackportClass(ClassReference classReference, int id) {
      return syntheticClass(classReference, naming.BACKPORT, id);
    }

    @Override
    public MethodReference syntheticBackportMethod(
        ClassReference classReference, MethodReference originalMethod, int id) {
      throw new Unreachable();
    }

    @Override
    public ClassReference syntheticBackportWithForwardingClass(
        ClassReference classReference, int id) {
      return syntheticClass(classReference, naming.BACKPORT_WITH_FORWARDING, id);
    }

    @Override
    public ClassReference syntheticBottomUpOutlineClass(ClassReference clazz, int id) {
      return syntheticClass(clazz, naming.BOTTOM_UP_OUTLINE, id);
    }

    @Override
    public ClassReference syntheticLambdaClass(ClassReference clazz, int id) {
      return syntheticClass(clazz, naming.LAMBDA, id);
    }

    @Override
    public ClassReference syntheticNonStartupInStartupOutlineClass(
        ClassReference reference, int id) {
      return syntheticClass(reference, naming.NON_STARTUP_IN_STARTUP_OUTLINE, id);
    }

    @Override
    public ClassReference syntheticOutlineClass(ClassReference clazz, int id) {
      return syntheticClass(clazz, naming.OUTLINE, id);
    }

    @Override
    public ClassReference syntheticRecordHelperClass(ClassReference reference, int id) {
      return syntheticClass(reference, naming.RECORD_HELPER, id);
    }

    @Override
    public ClassReference syntheticThrowIAEClass(ClassReference classReference, int id) {
      return syntheticClass(classReference, naming.THROW_IAE, id);
    }

    @Override
    public ClassReference syntheticTwrCloseResourceClass(ClassReference reference, int id) {
      return syntheticClass(reference, naming.TWR_CLOSE_RESOURCE, id);
    }
  }

  public static class Builder extends SyntheticItemsTestUtils {

    private final Map<ClassReference, Map<SyntheticKind, Int2ObjectMap<Set<ClassReference>>>>
        syntheticClasses = new HashMap<>();
    private final Map<ClassReference, Map<SyntheticKind, Int2ObjectMap<Set<MethodReference>>>>
        syntheticMethods = new HashMap<>();

    public void add(
        SyntheticKind kind, int id, DexType syntheticContext, DexReference externalReference) {
      externalReference.accept(
          clazz -> addClass(kind, id, syntheticContext, clazz),
          field -> {
            assert false;
          },
          method -> addMethod(kind, id, syntheticContext, method));
    }

    private void addClass(
        SyntheticKind kind, int id, DexType syntheticContext, DexType externalClass) {
      Map<SyntheticKind, Int2ObjectMap<Set<ClassReference>>> syntheticsForContextMap =
          syntheticClasses.computeIfAbsent(
              syntheticContext.asClassReference(), ignoreKey(HashMap::new));
      Int2ObjectMap<Set<ClassReference>> idToExternalMethodsMap =
          syntheticsForContextMap.computeIfAbsent(kind, ignoreKey(Int2ObjectOpenHashMap::new));
      Set<ClassReference> externalClasses =
          idToExternalMethodsMap.computeIfAbsent(id, ignoreKey(HashSet::new));
      externalClasses.add(externalClass.asClassReference());
    }

    private void addMethod(
        SyntheticKind kind, int id, DexType syntheticContext, DexMethod externalMethod) {
      Map<SyntheticKind, Int2ObjectMap<Set<MethodReference>>> syntheticsForContextMap =
          syntheticMethods.computeIfAbsent(
              syntheticContext.asClassReference(), ignoreKey(HashMap::new));
      Int2ObjectMap<Set<MethodReference>> idToExternalMethodsMap =
          syntheticsForContextMap.computeIfAbsent(kind, ignoreKey(Int2ObjectOpenHashMap::new));
      Set<MethodReference> externalMethods =
          idToExternalMethodsMap.computeIfAbsent(id, ignoreKey(HashSet::new));
      externalMethods.add(externalMethod.asMethodReference());
    }

    private ClassReference lookupClass(ClassReference classReference, int id, SyntheticKind kind) {
      return internalLookup(classReference, id, kind, syntheticClasses);
    }

    private ClassReference lookupMethod(ClassReference classReference, int id, SyntheticKind kind) {
      MethodReference methodReference = internalLookup(classReference, id, kind, syntheticMethods);
      return methodReference != null ? methodReference.getHolderClass() : null;
    }

    private static <S> S internalLookup(
        ClassReference classReference,
        int id,
        SyntheticKind kind,
        Map<ClassReference, Map<SyntheticKind, Int2ObjectMap<Set<S>>>> synthetics) {
      Set<S> references =
          synthetics
              .getOrDefault(classReference, Collections.emptyMap())
              .getOrDefault(kind, Int2ObjectMaps.emptyMap())
              .getOrDefault(id, Collections.emptySet());
      if (references.size() == 1) {
        return references.iterator().next();
      }
      assert references.isEmpty();
      return null;
    }

    private MethodReference lookupMethod(
        ClassReference classReference,
        MethodReference methodReference,
        int id,
        SyntheticKind kind) {
      Set<MethodReference> references =
          syntheticMethods
              .getOrDefault(classReference, Collections.emptyMap())
              .getOrDefault(kind, Int2ObjectMaps.emptyMap())
              .getOrDefault(id, Collections.emptySet());
      for (MethodReference reference : references) {
        if (reference.getMethodDescriptor().equals(methodReference.getMethodDescriptor())) {
          return reference;
        }
      }
      return null;
    }

    @Override
    public boolean isExternalApiOutlineClass(ClassReference classReference) {
      return isSyntheticMethodOfKind(classReference, naming.API_MODEL_OUTLINE);
    }

    @Override
    public boolean isExternalLambda(ClassReference classReference) {
      return isSyntheticClassOfKind(classReference, naming.LAMBDA);
    }

    @Override
    public boolean isExternalNonFixedInitializerTypeArgument(ClassReference classReference) {
      return isSyntheticClassOfKind(classReference, naming.NON_FIXED_INIT_TYPE_ARGUMENT);
    }

    @Override
    public boolean isExternalOutlineClass(ClassReference classReference) {
      return isSyntheticMethodOfKind(classReference, naming.OUTLINE);
    }

    @Override
    public boolean isExternalStaticInterfaceCall(ClassReference classReference) {
      return isSyntheticMethodOfKind(classReference, naming.STATIC_INTERFACE_CALL);
    }

    private boolean isSyntheticClassOfKind(ClassReference classReference, SyntheticKind kind) {
      return internalIsSyntheticOfKind(classReference, kind, syntheticClasses, Function.identity());
    }

    private boolean isSyntheticMethodOfKind(ClassReference classReference, SyntheticKind kind) {
      return internalIsSyntheticOfKind(
          classReference, kind, syntheticMethods, MethodReference::getHolderClass);
    }

    private static <S> boolean internalIsSyntheticOfKind(
        ClassReference classReference,
        SyntheticKind kind,
        Map<ClassReference, Map<SyntheticKind, Int2ObjectMap<Set<S>>>> synthetics,
        Function<S, ClassReference> toContext) {
      for (Map<SyntheticKind, Int2ObjectMap<Set<S>>> map : synthetics.values()) {
        Int2ObjectMap<Set<S>> referencesOfKind = map.getOrDefault(kind, Int2ObjectMaps.emptyMap());
        for (Set<S> references : referencesOfKind.values()) {
          if (Iterables.any(
              references, reference -> toContext.apply(reference).equals(classReference))) {
            return true;
          }
        }
      }
      return false;
    }

    @Override
    public ClassReference syntheticApiConversionClass(ClassReference classReference, int id) {
      return lookupMethod(classReference, id, naming.API_CONVERSION);
    }

    @Override
    public ClassReference syntheticApiOutlineClass(ClassReference classReference, int id) {
      ClassReference result = lookupMethod(classReference, id, naming.API_MODEL_OUTLINE);
      return result != null
          ? result
          : lookupMethod(classReference, id, naming.API_MODEL_OUTLINE_WITHOUT_GLOBAL_MERGING);
    }

    @Override
    public ClassReference syntheticAutoCloseableDispatcherClass(
        ClassReference classReference, int id) {
      return lookupMethod(classReference, id, naming.AUTOCLOSEABLE_DISPATCHER);
    }

    @Override
    public ClassReference syntheticAutoCloseableForwarderClass(
        ClassReference classReference, int id) {
      return lookupMethod(classReference, id, naming.AUTOCLOSEABLE_FORWARDER);
    }

    @Override
    public ClassReference syntheticBackportClass(ClassReference classReference, int id) {
      return lookupMethod(classReference, id, naming.BACKPORT);
    }

    @Override
    public MethodReference syntheticBackportMethod(
        ClassReference classReference, MethodReference originalMethod, int id) {
      return lookupMethod(classReference, originalMethod, id, naming.BACKPORT);
    }

    @Override
    public ClassReference syntheticBackportWithForwardingClass(
        ClassReference classReference, int id) {
      return lookupMethod(classReference, id, naming.BACKPORT_WITH_FORWARDING);
    }

    @Override
    public ClassReference syntheticBottomUpOutlineClass(ClassReference classReference, int id) {
      return lookupMethod(classReference, id, naming.BOTTOM_UP_OUTLINE);
    }

    @Override
    public ClassReference syntheticLambdaClass(ClassReference classReference, int id) {
      return lookupClass(classReference, id, naming.LAMBDA);
    }

    @Override
    public ClassReference syntheticNonStartupInStartupOutlineClass(
        ClassReference classReference, int id) {
      return lookupMethod(classReference, id, naming.NON_STARTUP_IN_STARTUP_OUTLINE);
    }

    @Override
    public ClassReference syntheticOutlineClass(ClassReference classReference, int id) {
      return lookupMethod(classReference, id, naming.OUTLINE);
    }

    @Override
    public ClassReference syntheticRecordHelperClass(ClassReference classReference, int id) {
      return lookupMethod(classReference, id, naming.RECORD_HELPER);
    }

    @Override
    public ClassReference syntheticThrowIAEClass(ClassReference classReference, int id) {
      return lookupMethod(classReference, id, naming.THROW_IAE);
    }

    @Override
    public ClassReference syntheticTwrCloseResourceClass(ClassReference classReference, int id) {
      return lookupMethod(classReference, id, naming.TWR_CLOSE_RESOURCE);
    }
  }
}
