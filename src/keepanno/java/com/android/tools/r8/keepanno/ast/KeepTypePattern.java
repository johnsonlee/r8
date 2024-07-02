// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.ast;

import com.android.tools.r8.keepanno.proto.KeepSpecProtos.TypePattern;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public abstract class KeepTypePattern {

  public static KeepTypePattern any() {
    return Any.getInstance();
  }

  public static KeepTypePattern fromPrimitive(KeepPrimitiveTypePattern type) {
    return type.isAny() ? PrimitiveType.ANY : PrimitiveType.PRIMITIVES.get(type.getDescriptor());
  }

  public static KeepTypePattern fromArray(KeepArrayTypePattern type) {
    return new ArrayType(type);
  }

  public static KeepTypePattern fromClass(KeepQualifiedClassNamePattern type) {
    return new ClassType(type);
  }

  public static KeepTypePattern fromInstanceOf(KeepInstanceOfPattern pattern) {
    return new KeepInstanceOf(pattern);
  }

  public static KeepTypePattern fromDescriptor(String typeDescriptor) {
    char c = typeDescriptor.charAt(0);
    if (c == 'L') {
      int end = typeDescriptor.length() - 1;
      if (typeDescriptor.charAt(end) != ';') {
        throw new KeepEdgeException("Invalid type descriptor: " + typeDescriptor);
      }
      return fromClass(KeepQualifiedClassNamePattern.exactFromDescriptor(typeDescriptor));
    }
    if (c == '[') {
      int dim = 1;
      while (typeDescriptor.charAt(dim) == '[') {
        dim++;
      }
      KeepTypePattern baseType = fromDescriptor(typeDescriptor.substring(dim));
      return fromArray(new KeepArrayTypePattern(baseType, dim));
    }
    PrimitiveType primitiveType = PrimitiveType.PRIMITIVES.get(typeDescriptor);
    if (primitiveType != null) {
      return primitiveType;
    }
    throw new KeepEdgeException("Invalid type descriptor: " + typeDescriptor);
  }

  public abstract <T> T apply(
      Supplier<T> onAny,
      Function<KeepPrimitiveTypePattern, T> onPrimitive,
      Function<KeepArrayTypePattern, T> onArray,
      Function<KeepQualifiedClassNamePattern, T> onClass,
      Function<KeepInstanceOfPattern, T> onInstanceOf);

  public final void match(
      Runnable onAny,
      Consumer<KeepPrimitiveTypePattern> onPrimitive,
      Consumer<KeepArrayTypePattern> onArray,
      Consumer<KeepQualifiedClassNamePattern> onClass,
      Consumer<KeepInstanceOfPattern> onInstanceOf) {
    apply(
        AstUtils.toVoidSupplier(onAny),
        AstUtils.toVoidFunction(onPrimitive),
        AstUtils.toVoidFunction(onArray),
        AstUtils.toVoidFunction(onClass),
        AstUtils.toVoidFunction(onInstanceOf));
  }

  public boolean isAny() {
    return false;
  }

  private static class Any extends KeepTypePattern {

    private static final Any INSTANCE = new Any();

    public static Any getInstance() {
      return INSTANCE;
    }

    @Override
    public <T> T apply(
        Supplier<T> onAny,
        Function<KeepPrimitiveTypePattern, T> onPrimitive,
        Function<KeepArrayTypePattern, T> onArray,
        Function<KeepQualifiedClassNamePattern, T> onClass,
        Function<KeepInstanceOfPattern, T> onInstanceOf) {
      return onAny.get();
    }

    @Override
    public boolean isAny() {
      return true;
    }

    @Override
    public boolean equals(Object obj) {
      return this == obj;
    }

    @Override
    public int hashCode() {
      return System.identityHashCode(this);
    }

    @Override
    public String toString() {
      return "<any>";
    }
  }

  private static class PrimitiveType extends KeepTypePattern {

    private static final PrimitiveType ANY = new PrimitiveType(KeepPrimitiveTypePattern.getAny());
    private static final Map<String, PrimitiveType> PRIMITIVES = populate();

    private static Map<String, PrimitiveType> populate() {
      ImmutableMap.Builder<String, PrimitiveType> builder = ImmutableMap.builder();
      KeepPrimitiveTypePattern.forEachPrimitive(
          primitive -> {
            builder.put(primitive.getDescriptor(), new PrimitiveType(primitive));
          });
      return builder.build();
    }

    private final KeepPrimitiveTypePattern type;

    private PrimitiveType(KeepPrimitiveTypePattern type) {
      this.type = type;
    }

    @Override
    public boolean equals(Object obj) {
      return this == obj;
    }

    @Override
    public int hashCode() {
      return System.identityHashCode(this);
    }

    @Override
    public String toString() {
      return type.getDescriptor();
    }

    @Override
    public <T> T apply(
        Supplier<T> onAny,
        Function<KeepPrimitiveTypePattern, T> onPrimitive,
        Function<KeepArrayTypePattern, T> onArray,
        Function<KeepQualifiedClassNamePattern, T> onClass,
        Function<KeepInstanceOfPattern, T> onInstanceOf) {
      return onPrimitive.apply(type);
    }
  }

  private static class ClassType extends KeepTypePattern {
    private final KeepQualifiedClassNamePattern type;

    public ClassType(KeepQualifiedClassNamePattern type) {
      this.type = type;
    }

    @Override
    public <T> T apply(
        Supplier<T> onAny,
        Function<KeepPrimitiveTypePattern, T> onPrimitive,
        Function<KeepArrayTypePattern, T> onArray,
        Function<KeepQualifiedClassNamePattern, T> onClass,
        Function<KeepInstanceOfPattern, T> onInstanceOf) {
      return onClass.apply(type);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof ClassType)) {
        return false;
      }
      ClassType classType = (ClassType) o;
      return Objects.equals(type, classType.type);
    }

    @Override
    public int hashCode() {
      return Objects.hash(type);
    }

    @Override
    public String toString() {
      return type.toString();
    }
  }

  private static class ArrayType extends KeepTypePattern {
    private final KeepArrayTypePattern type;

    public ArrayType(KeepArrayTypePattern type) {
      this.type = type;
    }

    @Override
    public <T> T apply(
        Supplier<T> onAny,
        Function<KeepPrimitiveTypePattern, T> onPrimitive,
        Function<KeepArrayTypePattern, T> onArray,
        Function<KeepQualifiedClassNamePattern, T> onClass,
        Function<KeepInstanceOfPattern, T> onInstanceOf) {
      return onArray.apply(type);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof ArrayType)) {
        return false;
      }
      ArrayType arrayType = (ArrayType) o;
      return Objects.equals(type, arrayType.type);
    }

    @Override
    public int hashCode() {
      return Objects.hash(type);
    }

    @Override
    public String toString() {
      return type.toString();
    }
  }

  private static class KeepInstanceOf extends KeepTypePattern {
    private final KeepInstanceOfPattern instanceOf;

    private KeepInstanceOf(KeepInstanceOfPattern instanceOf) {
      this.instanceOf = instanceOf;
    }

    @Override
    public <T> T apply(
        Supplier<T> onAny,
        Function<KeepPrimitiveTypePattern, T> onPrimitive,
        Function<KeepArrayTypePattern, T> onArray,
        Function<KeepQualifiedClassNamePattern, T> onClass,
        Function<KeepInstanceOfPattern, T> onInstanceOf) {
      return onInstanceOf.apply(instanceOf);
    }
  }

  public static KeepTypePattern fromProto(TypePattern typeProto) {
    if (typeProto.hasPrimitive()) {
      return KeepTypePattern.fromPrimitive(
          KeepPrimitiveTypePattern.fromProto(typeProto.getPrimitive()));
    }
    if (typeProto.hasArray()) {
      return KeepTypePattern.fromArray(KeepArrayTypePattern.fromProto(typeProto.getArray()));
    }
    if (typeProto.hasClazz()) {
      return KeepTypePattern.fromClass(
          KeepQualifiedClassNamePattern.fromProto(typeProto.getClazz()));
    }
    if (typeProto.hasInstanceOf()) {
      return KeepTypePattern.fromInstanceOf(
          KeepInstanceOfPattern.fromProto(typeProto.getInstanceOf()));
    }
    return KeepTypePattern.any();
  }

  public TypePattern.Builder buildProto() {
    TypePattern.Builder builder = TypePattern.newBuilder();
    match(
        () -> {
          // The unset oneof is "any type".
        },
        primitive -> builder.setPrimitive(primitive.buildProto()),
        array -> builder.setArray(array.buildProto()),
        clazz -> builder.setClazz(clazz.buildProto()),
        instanceOf -> {
          if (instanceOf.isAny()) {
            // Note that an "any" instance-of pattern should match any class-type
            // TODO(b/350647134): This should become evident when introducing a class-pattern.
            //  When doing so, consider also if/how to match a general reference type.
            builder.setClazz(KeepQualifiedClassNamePattern.any().buildProto());
          } else {
            instanceOf.buildProto(builder::setInstanceOf);
          }
        });
    return builder;
  }
}
