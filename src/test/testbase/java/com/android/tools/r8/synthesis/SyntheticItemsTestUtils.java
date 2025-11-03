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
import static com.android.tools.r8.synthesis.SyntheticNaming.EXTERNAL_SYNTHETIC_CLASS_SEPARATOR;
import static com.android.tools.r8.utils.MapUtils.ignoreKey;
import static org.hamcrest.CoreMatchers.containsString;

import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexItemFactory;
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
import com.android.tools.r8.utils.DescriptorUtils;
import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
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

  public static SyntheticItemsTestUtils getMinimalSyntheticItemsTestUtils() {
    return new MinimalSyntheticItemsTestUtils();
  }

  public static SyntheticItemsTestUtils getSyntheticItemsTestUtils(boolean minimalSyntheticNames) {
    return minimalSyntheticNames
        ? getMinimalSyntheticItemsTestUtils()
        : getDefaultSyntheticItemsTestUtils();
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
    ClassReference syntheticHolder = syntheticBackportClass(classReference, id);
    MethodReference originalMethod = Reference.methodFromMethod(method);
    return Reference.methodFromDescriptor(
        syntheticHolder.getDescriptor(),
        syntheticMethodName(),
        originalMethod.getMethodDescriptor());
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
    return Reference.methodFromDescriptor(
        syntheticBackportWithForwardingClass(clazz, id),
        syntheticMethodName(),
        methodWithReceiverForForwarding.getMethodDescriptor());
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

  public abstract String syntheticApiOutlineClassPrefix(Class<?> clazz);

  public final ClassReference syntheticBackportClass(Class<?> clazz, int id) {
    return syntheticBackportClass(Reference.classFromClass(clazz), id);
  }

  public abstract ClassReference syntheticBackportClass(ClassReference classReference, int id);

  public final ClassReference syntheticBackportWithForwardingClass(Class<?> clazz, int id) {
    return syntheticBackportWithForwardingClass(Reference.classFromClass(clazz), id);
  }

  public abstract ClassReference syntheticBackportWithForwardingClass(
      ClassReference classReference, int id);

  public static ClassReference syntheticRecordTagClass() {
    return Reference.classFromDescriptor(DexItemFactory.recordTagDescriptorString);
  }

  public static ClassReference syntheticRecordHelperClass(ClassReference reference, int id) {
    return syntheticClass(reference, naming.RECORD_HELPER, id);
  }

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

  public static boolean isExternalSynthetic(ClassReference reference) {
    for (SyntheticKind kind : naming.kinds()) {
      if (kind.isGlobal()) {
        continue;
      }
      if (kind.isFixedSuffixSynthetic()) {
        if (SyntheticNaming.isSynthetic(reference, null, kind)) {
          return true;
        }
      } else {
        if (SyntheticNaming.isSynthetic(reference, Phase.EXTERNAL, kind)) {
          return true;
        }
      }
    }
    return false;
  }

  public static boolean isInternalLambda(ClassReference reference) {
    return SyntheticNaming.isSynthetic(reference, Phase.INTERNAL, naming.LAMBDA);
  }

  public abstract boolean isExternalLambda(ClassReference reference);

  public static boolean isExternalStaticInterfaceCall(ClassReference reference) {
    return SyntheticNaming.isSynthetic(reference, Phase.EXTERNAL, naming.STATIC_INTERFACE_CALL);
  }

  public static boolean isExternalTwrCloseMethod(ClassReference reference) {
    return SyntheticNaming.isSynthetic(reference, Phase.EXTERNAL, naming.TWR_CLOSE_RESOURCE);
  }

  public static boolean isMaybeExternalSuppressedExceptionMethod(ClassReference reference) {
    // The suppressed exception methods are grouped with the backports.
    return SyntheticNaming.isSynthetic(reference, Phase.EXTERNAL, naming.BACKPORT);
  }

  public static boolean isExternalOutlineClass(ClassReference reference) {
    return SyntheticNaming.isSynthetic(reference, Phase.EXTERNAL, naming.OUTLINE);
  }

  public static boolean isExternalApiOutlineClass(ClassReference reference) {
    return SyntheticNaming.isSynthetic(reference, Phase.EXTERNAL, naming.API_MODEL_OUTLINE);
  }

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
    public boolean isExternalLambda(ClassReference reference) {
      return SyntheticNaming.isSynthetic(reference, Phase.EXTERNAL, naming.LAMBDA);
    }

    @Override
    public boolean isExternalNonFixedInitializerTypeArgument(ClassReference reference) {
      return SyntheticNaming.isSynthetic(
          reference, Phase.EXTERNAL, naming.NON_FIXED_INIT_TYPE_ARGUMENT);
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
    public String syntheticApiOutlineClassPrefix(Class<?> clazz) {
      return clazz.getTypeName()
          + EXTERNAL_SYNTHETIC_CLASS_SEPARATOR
          + naming.API_MODEL_OUTLINE.getDescriptor();
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
    public ClassReference syntheticThrowIAEClass(ClassReference classReference, int id) {
      return syntheticClass(classReference, naming.THROW_IAE, id);
    }

    @Override
    public ClassReference syntheticTwrCloseResourceClass(ClassReference reference, int id) {
      return syntheticClass(reference, naming.TWR_CLOSE_RESOURCE, id);
    }
  }

  private static class MinimalSyntheticItemsTestUtils extends SyntheticItemsTestUtils {

    @Override
    public boolean isExternalLambda(ClassReference reference) {
      throw new Unreachable();
    }

    @Override
    public boolean isExternalNonFixedInitializerTypeArgument(ClassReference reference) {
      throw new Unreachable();
    }

    @Override
    public ClassReference syntheticApiConversionClass(ClassReference classReference, int id) {
      return syntheticClassWithMinimalName(classReference, id);
    }

    @Override
    public ClassReference syntheticApiOutlineClass(ClassReference classReference, int id) {
      return syntheticClassWithMinimalName(classReference, id);
    }

    @Override
    public String syntheticApiOutlineClassPrefix(Class<?> clazz) {
      return clazz.getTypeName() + DescriptorUtils.INNER_CLASS_SEPARATOR;
    }

    @Override
    public ClassReference syntheticAutoCloseableDispatcherClass(
        ClassReference classReference, int id) {
      return syntheticClassWithMinimalName(classReference, id);
    }

    @Override
    public ClassReference syntheticAutoCloseableForwarderClass(
        ClassReference classReference, int id) {
      return syntheticClassWithMinimalName(classReference, id);
    }

    @Override
    public ClassReference syntheticBackportClass(ClassReference classReference, int id) {
      return syntheticClassWithMinimalName(classReference, id);
    }

    @Override
    public ClassReference syntheticBackportWithForwardingClass(
        ClassReference classReference, int id) {
      return syntheticClassWithMinimalName(classReference, id);
    }

    @Override
    public ClassReference syntheticBottomUpOutlineClass(ClassReference classReference, int id) {
      return syntheticClassWithMinimalName(classReference, id);
    }

    @Override
    public ClassReference syntheticLambdaClass(ClassReference classReference, int id) {
      return syntheticClassWithMinimalName(classReference, id);
    }

    @Override
    public ClassReference syntheticNonStartupInStartupOutlineClass(
        ClassReference classReference, int id) {
      return SyntheticItemsTestUtils.syntheticClassWithMinimalName(classReference, id);
    }

    @Override
    public ClassReference syntheticOutlineClass(ClassReference classReference, int id) {
      return syntheticClassWithMinimalName(classReference, id);
    }

    @Override
    public ClassReference syntheticThrowIAEClass(ClassReference classReference, int id) {
      return syntheticClassWithMinimalName(classReference, id);
    }

    @Override
    public ClassReference syntheticTwrCloseResourceClass(ClassReference classReference, int id) {
      return syntheticClassWithMinimalName(classReference, id);
    }
  }

  public static class Builder extends SyntheticItemsTestUtils {

    private final Map<ClassReference, Map<SyntheticKind, Int2ObjectMap<ClassReference>>>
        synthetics = new HashMap<>();

    public void add(SyntheticKind kind, int id, DexType syntheticContext, DexType externalType) {
      Map<SyntheticKind, Int2ObjectMap<ClassReference>> syntheticsForContextMap =
          synthetics.computeIfAbsent(syntheticContext.asClassReference(), ignoreKey(HashMap::new));
      Int2ObjectMap<ClassReference> idToExternalTypeMap =
          syntheticsForContextMap.computeIfAbsent(kind, ignoreKey(Int2ObjectOpenHashMap::new));
      ClassReference previousValue = idToExternalTypeMap.put(id, externalType.asClassReference());
      assert previousValue == null;
    }

    private ClassReference lookup(ClassReference classReference, int id, SyntheticKind kind) {
      return synthetics
          .getOrDefault(classReference, Collections.emptyMap())
          .getOrDefault(kind, Int2ObjectMaps.emptyMap())
          .get(id);
    }

    @Override
    public boolean isExternalLambda(ClassReference classReference) {
      return isSyntheticOfKind(classReference, naming.LAMBDA);
    }

    @Override
    public boolean isExternalNonFixedInitializerTypeArgument(ClassReference classReference) {
      return isSyntheticOfKind(classReference, naming.NON_FIXED_INIT_TYPE_ARGUMENT);
    }

    private boolean isSyntheticOfKind(ClassReference classReference, SyntheticKind kind) {
      for (Map<SyntheticKind, Int2ObjectMap<ClassReference>> map : synthetics.values()) {
        Int2ObjectMap<ClassReference> referencesOfKind =
            map.getOrDefault(kind, Int2ObjectMaps.emptyMap());
        if (referencesOfKind.containsValue(classReference)) {
          return true;
        }
      }
      return false;
    }

    @Override
    public ClassReference syntheticApiConversionClass(ClassReference classReference, int id) {
      return lookup(classReference, id, naming.API_CONVERSION);
    }

    @Override
    public ClassReference syntheticApiOutlineClass(ClassReference classReference, int id) {
      return lookup(classReference, id, naming.API_MODEL_OUTLINE);
    }

    @Override
    public String syntheticApiOutlineClassPrefix(Class<?> clazz) {
      throw new Unimplemented();
    }

    @Override
    public ClassReference syntheticAutoCloseableDispatcherClass(
        ClassReference classReference, int id) {
      return lookup(classReference, id, naming.AUTOCLOSEABLE_DISPATCHER);
    }

    @Override
    public ClassReference syntheticAutoCloseableForwarderClass(
        ClassReference classReference, int id) {
      return lookup(classReference, id, naming.AUTOCLOSEABLE_FORWARDER);
    }

    @Override
    public ClassReference syntheticBackportClass(ClassReference classReference, int id) {
      return lookup(classReference, id, naming.BACKPORT);
    }

    @Override
    public ClassReference syntheticBackportWithForwardingClass(
        ClassReference classReference, int id) {
      return lookup(classReference, id, naming.BACKPORT_WITH_FORWARDING);
    }

    @Override
    public ClassReference syntheticBottomUpOutlineClass(ClassReference classReference, int id) {
      return lookup(classReference, id, naming.BOTTOM_UP_OUTLINE);
    }

    @Override
    public ClassReference syntheticLambdaClass(ClassReference classReference, int id) {
      return lookup(classReference, id, naming.LAMBDA);
    }

    @Override
    public ClassReference syntheticNonStartupInStartupOutlineClass(
        ClassReference classReference, int id) {
      return lookup(classReference, id, naming.NON_STARTUP_IN_STARTUP_OUTLINE);
    }

    @Override
    public ClassReference syntheticOutlineClass(ClassReference classReference, int id) {
      return lookup(classReference, id, naming.OUTLINE);
    }

    @Override
    public ClassReference syntheticThrowIAEClass(ClassReference classReference, int id) {
      return lookup(classReference, id, naming.THROW_IAE);
    }

    @Override
    public ClassReference syntheticTwrCloseResourceClass(ClassReference classReference, int id) {
      return lookup(classReference, id, naming.TWR_CLOSE_RESOURCE);
    }
  }
}
