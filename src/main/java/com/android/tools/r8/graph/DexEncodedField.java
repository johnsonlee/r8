// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;
import static com.android.tools.r8.ir.analysis.type.Nullability.maybeNull;
import static com.android.tools.r8.kotlin.KotlinMetadataUtils.getNoKotlinInfo;

import com.android.tools.r8.dex.MixedSectionCollection;
import com.android.tools.r8.graph.GenericSignature.FieldTypeSignature;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.SingleValue;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.TypeAndLocalInfoSupplier;
import com.android.tools.r8.ir.optimize.info.DefaultFieldOptimizationInfo;
import com.android.tools.r8.ir.optimize.info.FieldOptimizationInfo;
import com.android.tools.r8.ir.optimize.info.MutableFieldOptimizationInfo;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedbackSimple;
import com.android.tools.r8.kotlin.KotlinFieldLevelInfo;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.ConsumerUtils;
import com.android.tools.r8.utils.structural.StructuralItem;
import com.android.tools.r8.utils.structural.StructuralMapping;
import com.android.tools.r8.utils.structural.StructuralSpecification;
import java.util.function.Consumer;

public class DexEncodedField extends DexEncodedMember<DexEncodedField, DexField>
    implements StructuralItem<DexEncodedField> {
  public static final DexEncodedField[] EMPTY_ARRAY = {};

  public final FieldAccessFlags accessFlags;
  private DexValue staticValue;
  private final boolean deprecated;
  /** Generic signature information if the attribute is present in the input */
  private FieldTypeSignature genericSignature;

  private FieldOptimizationInfo optimizationInfo = DefaultFieldOptimizationInfo.getInstance();
  private KotlinFieldLevelInfo kotlinMemberInfo = getNoKotlinInfo();

  private static void specify(StructuralSpecification<DexEncodedField, ?> spec) {
    spec.withItem(DexEncodedField::getReference)
        .withItem(DexEncodedField::getAccessFlags)
        .withNullableItem(f -> f.staticValue)
        .withBool(DexEncodedField::isDeprecated)
        // TODO(b/171867022): The generic signature should be part of the definition.
        .withAssert(f -> f.genericSignature.hasNoSignature());
    // TODO(b/171867022): Should the optimization info and member info be part of the definition?
  }

  public DexEncodedField(DexField field, FieldAccessFlags accessFlags) {
    this(field, accessFlags, FieldTypeSignature.noSignature(), DexAnnotationSet.empty(), null);
  }

  public DexEncodedField(
      DexField field,
      FieldAccessFlags accessFlags,
      FieldTypeSignature genericSignature,
      DexAnnotationSet annotations,
      DexValue staticValue) {
    this(field, accessFlags, genericSignature, annotations, staticValue, false);
  }

  public DexEncodedField(
      DexField field,
      FieldAccessFlags accessFlags,
      FieldTypeSignature genericSignature,
      DexAnnotationSet annotations,
      DexValue staticValue,
      boolean deprecated) {
    this(field, accessFlags, genericSignature, annotations, staticValue, deprecated, false);
  }

  public DexEncodedField(
      DexField field,
      FieldAccessFlags accessFlags,
      FieldTypeSignature genericSignature,
      DexAnnotationSet annotations,
      DexValue staticValue,
      boolean deprecated,
      boolean d8R8Synthesized) {
    super(field, annotations, d8R8Synthesized);
    this.accessFlags = accessFlags;
    this.staticValue = staticValue;
    this.deprecated = deprecated;
    this.genericSignature = genericSignature;
    assert genericSignature != null;
    assert GenericSignatureUtils.verifyNoDuplicateGenericDefinitions(genericSignature, annotations);
  }

  @Override
  public StructuralMapping<DexEncodedField> getStructuralMapping() {
    return DexEncodedField::specify;
  }

  @Override
  public DexEncodedField self() {
    return this;
  }

  public DexType type() {
    return getReference().type;
  }

  public boolean isDeprecated() {
    return deprecated;
  }

  public boolean isProgramField(DexDefinitionSupplier definitions) {
    if (getReference().holder.isClassType()) {
      DexClass clazz = definitions.definitionFor(getReference().holder);
      return clazz != null && clazz.isProgramClass();
    }
    return false;
  }

  public FieldOptimizationInfo getOptimizationInfo() {
    return optimizationInfo;
  }

  public synchronized MutableFieldOptimizationInfo getMutableOptimizationInfo() {
    if (optimizationInfo.isDefaultFieldOptimizationInfo()) {
      MutableFieldOptimizationInfo mutableOptimizationInfo = new MutableFieldOptimizationInfo();
      optimizationInfo = mutableOptimizationInfo;
      return mutableOptimizationInfo;
    }
    assert optimizationInfo.isMutableFieldOptimizationInfo();
    return optimizationInfo.asMutableFieldOptimizationInfo();
  }

  public void setOptimizationInfo(MutableFieldOptimizationInfo info) {
    optimizationInfo = info;
  }

  @Override
  public KotlinFieldLevelInfo getKotlinInfo() {
    return kotlinMemberInfo;
  }

  @Override
  public void clearKotlinInfo() {
    kotlinMemberInfo = getNoKotlinInfo();
  }

  @Override
  public FieldAccessFlags getAccessFlags() {
    return accessFlags;
  }

  public void setKotlinMemberInfo(KotlinFieldLevelInfo kotlinMemberInfo) {
    assert this.kotlinMemberInfo == getNoKotlinInfo();
    this.kotlinMemberInfo = kotlinMemberInfo;
  }

  @Override
  void collectMixedSectionItems(MixedSectionCollection mixedItems) {
    annotations().collectMixedSectionItems(mixedItems);
  }

  @Override
  public String toString() {
    return "Encoded field " + getReference();
  }

  @Override
  public String toSmaliString() {
    return getReference().toSmaliString();
  }

  @Override
  public String toSourceString() {
    return getReference().toSourceString();
  }

  public DexType getType() {
    return getReference().getType();
  }

  public TypeElement getTypeElement(AppView<?> appView) {
    return getReference().getTypeElement(appView);
  }

  @Override
  public boolean isDexEncodedField() {
    return true;
  }

  @Override
  public DexEncodedField asDexEncodedField() {
    return this;
  }

  @Override
  public ProgramField asProgramMember(DexDefinitionSupplier definitions) {
    return asProgramField(definitions);
  }

  public ProgramField asProgramField(DexDefinitionSupplier definitions) {
    assert getHolderType().isClassType();
    DexProgramClass clazz = asProgramClassOrNull(definitions.definitionForHolder(getReference()));
    if (clazz != null) {
      return new ProgramField(clazz, this);
    }
    return null;
  }

  public boolean isEnum() {
    return accessFlags.isEnum();
  }

  public boolean isFinal() {
    return accessFlags.isFinal();
  }

  @Override
  public boolean isStatic() {
    return accessFlags.isStatic();
  }

  public boolean isPackagePrivate() {
    return accessFlags.isPackagePrivate();
  }

  public boolean isProtected() {
    return accessFlags.isProtected();
  }

  public boolean isPublic() {
    return accessFlags.isPublic();
  }

  @Override
  public boolean isStaticMember() {
    return isStatic();
  }

  public boolean isSynthetic() {
    return accessFlags.isSynthetic();
  }

  public boolean isVolatile() {
    return accessFlags.isVolatile();
  }

  public boolean hasExplicitStaticValue() {
    assert accessFlags.isStatic();
    return staticValue != null;
  }

  public void setStaticValue(DexValue staticValue) {
    assert accessFlags.isStatic();
    assert staticValue != null;
    this.staticValue = staticValue;
  }

  public void clearStaticValue() {
    assert accessFlags.isStatic();
    this.staticValue = null;
  }

  public DexValue getStaticValue() {
    assert accessFlags.isStatic();
    return staticValue == null ? DexValue.defaultForType(getReference().type) : staticValue;
  }

  /**
   * Returns a const instructions if this field is a compile time final const.
   *
   * <p>NOTE: It is the responsibility of the caller to check if this field is pinned or not.
   */
  public Instruction valueAsConstInstruction(
      IRCode code, DebugLocalInfo local, AppView<AppInfoWithLiveness> appView) {
    boolean isWritten = appView.appInfo().isFieldWrittenByFieldPutInstruction(this);
    if (!isWritten) {
      // Since the field is not written, we can simply return the default value for the type.
      DexValue value = isStatic() ? getStaticValue() : DexValue.defaultForType(getReference().type);
      return value.asConstInstruction(appView, code, local);
    }

    // Check if we have a single value for the field according to the field optimization info.
    AbstractValue abstractValue = getOptimizationInfo().getAbstractValue();
    if (abstractValue.isSingleValue()) {
      SingleValue singleValue = abstractValue.asSingleValue();
      if (singleValue.isSingleFieldValue()
          && singleValue.asSingleFieldValue().getField() == getReference()) {
        return null;
      }
      if (singleValue.isMaterializableInContext(appView, code.context())) {
        TypeElement type = TypeElement.fromDexType(getReference().type, maybeNull(), appView);
        return singleValue.createMaterializingInstruction(
            appView, code, TypeAndLocalInfoSupplier.create(type, local));
      }
    }

    // The only way to figure out whether the static value contains the final value is ensure the
    // value is not the default or check that <clinit> is not present.
    if (accessFlags.isFinal() && isStatic()) {
      DexClass clazz = appView.definitionFor(getReference().holder);
      if (clazz == null || clazz.hasClassInitializer()) {
        return null;
      }
      DexValue staticValue = getStaticValue();
      if (!staticValue.isDefault(getReference().type)) {
        return staticValue.asConstInstruction(appView, code, local);
      }
    }

    return null;
  }

  public DexEncodedField toTypeSubstitutedField(DexField field) {
    return toTypeSubstitutedField(field, ConsumerUtils.emptyConsumer());
  }

  public DexEncodedField toTypeSubstitutedField(DexField field, Consumer<Builder> consumer) {
    if (this.getReference() == field) {
      return this;
    }
    return builder(this).setField(field).apply(consumer).build();
  }

  public boolean validateDexValue(DexItemFactory factory) {
    if (!accessFlags.isStatic() || staticValue == null) {
      return true;
    }
    if (getReference().type.isPrimitiveType()) {
      assert staticValue.getType(factory) == getReference().type
          : "Static " + getReference() + " has invalid static value " + staticValue + ".";
    }
    if (staticValue.isDexValueNull()) {
      assert getReference().type.isReferenceType()
          : "Static " + getReference() + " has invalid null static value.";
    }
    // TODO(b/150593449): Support non primitive DexValue (String, enum) and add assertions.
    return true;
  }

  public FieldTypeSignature getGenericSignature() {
    return genericSignature;
  }

  public void setGenericSignature(FieldTypeSignature genericSignature) {
    assert genericSignature != null;
    this.genericSignature = genericSignature;
  }

  public void clearGenericSignature() {
    this.genericSignature = FieldTypeSignature.noSignature();
  }

  private static Builder builder(DexEncodedField from) {
    return new Builder(from);
  }

  public static class Builder {

    private DexField field;
    private DexAnnotationSet annotations;
    private FieldAccessFlags accessFlags;
    private FieldTypeSignature genericSignature;
    private DexValue staticValue;
    private FieldOptimizationInfo optimizationInfo;
    private boolean deprecated;
    private boolean d8R8Synthesized;
    private Consumer<DexEncodedField> buildConsumer = ConsumerUtils.emptyConsumer();

    Builder(DexEncodedField from) {
      // Copy all the mutable state of a DexEncodedField here.
      field = from.getReference();
      accessFlags = from.accessFlags.copy();
      // TODO(b/169923358): Consider removing the fieldSignature here.
      genericSignature = from.getGenericSignature();
      annotations = from.annotations();
      staticValue = from.staticValue;
      optimizationInfo =
          from.optimizationInfo.isDefaultFieldOptimizationInfo()
              ? DefaultFieldOptimizationInfo.getInstance()
              : from.optimizationInfo.mutableCopy();
      deprecated = from.isDeprecated();
      d8R8Synthesized = from.isD8R8Synthesized();
    }

    public Builder apply(Consumer<Builder> consumer) {
      consumer.accept(this);
      return this;
    }

    public Builder setAbstractValue(
        AbstractValue abstractValue, AppView<AppInfoWithLiveness> appView) {
      return addBuildConsumer(
          fixedUpField ->
              OptimizationFeedbackSimple.getInstance()
                  .recordFieldHasAbstractValue(fixedUpField, appView, abstractValue));
    }

    private Builder addBuildConsumer(Consumer<DexEncodedField> consumer) {
      this.buildConsumer = this.buildConsumer.andThen(consumer);
      return this;
    }

    public Builder setField(DexField field) {
      this.field = field;
      return this;
    }

    DexEncodedField build() {
      DexEncodedField dexEncodedField =
          new DexEncodedField(
              field,
              accessFlags,
              genericSignature,
              annotations,
              staticValue,
              deprecated,
              d8R8Synthesized);
      if (optimizationInfo.isMutableFieldOptimizationInfo()) {
        dexEncodedField.setOptimizationInfo(optimizationInfo.asMutableFieldOptimizationInfo());
      }
      buildConsumer.accept(dexEncodedField);
      return dexEncodedField;
    }
  }
}
