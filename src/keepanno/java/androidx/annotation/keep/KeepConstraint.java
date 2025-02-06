/*
 * Copyright 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// ***********************************************************************************
// MAINTAINED AND TESTED IN THE R8 REPO. PLEASE MAKE CHANGES THERE AND REPLICATE.
// ***********************************************************************************


package androidx.annotation.keep;

/**
 * Usage constraints for how a target is used and what must be kept.
 *
 * <p>Providing the constraints on how an item is being used allows shrinkers to optimize or remove
 * aspects of the item that do not change that usage constraint.
 *
 * <p>For example, invoking a method reflectively does not use any annotations on that method, and
 * it is safe to remove the annotations. However, it would not be safe to remove parameters from the
 * method.
 */
public enum KeepConstraint {
  /**
   * Indicates that the target item is being looked up reflectively.
   *
   * <p>Looking up an item reflectively requires that it remains on its expected context, which for
   * a method or field means it must remain on its defining class. In other words, the item cannot
   * be removed or moved.
   *
   * <p>Note that looking up a member does not imply that the holder class of the member can be
   * looked up. If both the class and the member need to be looked, make sure to have a target for
   * both.
   *
   * <p>Note also that the item can be looked up within its context but no constraint is placed on
   * its name, accessibility or any other properties of the item.
   *
   * <p>If assumptions are being made about other aspects, additional constraints and targets should
   * be added to the keep annotation.
   */
  LOOKUP,

  /**
   * Indicates that the name of the target item is being used.
   *
   * <p>This usage constraint is needed if the target is being looked up reflectively by using its
   * name. Setting it will prohibit renaming of the target item.
   *
   * <p>Note that preserving the name of a member does not imply that the holder class of the member
   * will preserve its qualified or simple name. If both the class and the member need to preserve
   * their names, make sure to have a target for both.
   *
   * <p>Note that preserving the name of a member does not preserve the types of its parameters or
   * its return type for methods or the type for fields.
   */
  NAME,

  /**
   * Indicates that the visibility of the target must be at least as visible as declared.
   *
   * <p>Setting this constraint ensures that any (reflective) access to the target that is allowed
   * remains valid. In other words, a public class, field or method must remain public. For a
   * non-public target its visibility may be relaxed in the direction: {@code private ->
   * package-private -> protected -> public}.
   *
   * <p>Note that this constraint does not place any restrictions on any other accesses flags than
   * visibility. In particular, flags such a static, final and abstract may change.
   *
   * <p>Used together with {@link #VISIBILITY_RESTRICT} the visibility will remain invariant.
   */
  VISIBILITY_RELAX,

  /**
   * Indicates that the visibility of the target must be at most as visible as declared.
   *
   * <p>Setting this constraint ensures that any (reflective) access to the target that would fail
   * will continue to fail. In other words, a private class, field or method must remain private.
   * Concretely the visibility of the target item may be restricted in the direction: {@code public
   * -> protected -> package-private -> private}.
   *
   * <p>Note that this constraint does not place any restrictions on any other accesses flags than
   * visibility. In particular, flags such a static, final and abstract may change.
   *
   * <p>Used together with {@link #VISIBILITY_RELAX} the visibility will remain invariant.
   */
  VISIBILITY_RESTRICT,

  /**
   * Indicates that the visibility of the target must remain as declared.
   *
   * <p>Note that this constraint does not place any restrictions on any other accesses flags than
   * visibility. In particular, flags such a static, final and abstract may change.
   *
   * <p>This is equivalent to using both {@link #VISIBILITY_RELAX} and {@link #VISIBILITY_RESTRICT}.
   */
  VISIBILITY_INVARIANT,

  /**
   * Indicates that the class target is being instantiated reflectively.
   *
   * <p>This usage constraint is only valid on class targets.
   *
   * <p>Being instantiated prohibits reasoning about the class instances at compile time. In other
   * words, the compiler must assume the class to be possibly instantiated at all times.
   *
   * <p>Note that the constraint {@link KeepConstraint#LOOKUP} is needed to reflectively obtain a
   * class. This constraint only implies that if the class is referenced in the program it may be
   * instantiated.
   */
  CLASS_INSTANTIATE,

  /**
   * Indicates that the method target is being invoked reflectively.
   *
   * <p>This usage constraint is only valid on method targets.
   *
   * <p>To be invoked reflectively the method must retain the structure of the method. Thus, unused
   * arguments cannot be removed. However, it does not imply preserving the types of its parameters
   * or its return type. If the parameter types are being obtained reflectively then those need a
   * keep target independent of the method.
   *
   * <p>Note that the constraint {@link KeepConstraint#LOOKUP} is needed to reflectively obtain a
   * method reference. This constraint only implies that if the method is referenced in the program
   * it may be reflectively invoked.
   */
  METHOD_INVOKE,

  /**
   * Indicates that the field target is reflectively read from.
   *
   * <p>This usage constraint is only valid on field targets.
   *
   * <p>A field that has its value read from, requires that the field value must remain. Thus, if
   * field remains, its value cannot be replaced. It can still be removed in full, be inlined and
   * its value reasoned about at compile time.
   *
   * <p>Note that the constraint {@link KeepConstraint#LOOKUP} is needed to reflectively obtain
   * access to the field. This constraint only implies that if a field is referenced then it may be
   * reflectively read.
   */
  FIELD_GET,

  /**
   * Indicates that the field target is reflectively written to.
   *
   * <p>This usage constraint is only valid on field targets.
   *
   * <p>A field that has its value written to, requires that the field value must be treated as
   * unknown.
   *
   * <p>Note that the constraint {@link KeepConstraint#LOOKUP} is needed to reflectively obtain
   * access to the field. This constraint only implies that if a field is referenced then it may be
   * reflectively written.
   */
  FIELD_SET,

  /**
   * Indicates that the target method can be replaced by an alternative definition at runtime.
   *
   * <p>This usage constraint is only valid on method targets.
   *
   * <p>Replacing a method implies that the concrete implementation of the target cannot be known at
   * compile time. Thus, it cannot be moved or otherwise assumed to have particular properties.
   * Being able to replace the method still allows the compiler to fully remove it if it is
   * statically found to be unused.
   *
   * <p>Note also that no restriction is placed on the method name. To ensure the same name add
   * {@link #NAME} to the constraint set.
   */
  METHOD_REPLACE,

  /**
   * Indicates that the target field can be replaced by an alternative definition at runtime.
   *
   * <p>This usage constraint is only valid on field targets.
   *
   * <p>Replacing a field implies that the concrete implementation of the target cannot be known at
   * compile time. Thus, it cannot be moved or otherwise assumed to have particular properties.
   * Being able to replace the method still allows the compiler to fully remove it if it is
   * statically found to be unused.
   *
   * <p>Note also that no restriction is placed on the field name. To ensure the same name add
   * {@link #NAME} to the constraint set.
   */
  FIELD_REPLACE,

  /**
   * Indicates that the target item must never be inlined or merged.
   *
   * <p>This ensures that if the item is actually used in the program it will remain in some form.
   * For example, a method may still be renamed, but it will be present as a frame in stack traces
   * produced by the runtime (before potentially being retraced). For classes, they too may be
   * renamed, but will not have been merged with other classes or have their allocations fully
   * eliminated (aka class inlining).
   *
   * <p>For members this also ensures that the field value or method body cannot be reasoned about
   * outside the item itself. For example, a field value cannot be assumed to be a particular value,
   * and a method cannot be assumed to have particular properties for callers, such as always
   * throwing or a constant return value.
   */
  NEVER_INLINE,

  /**
   * Indicates that the class hierarchy below the target class may be extended at runtime.
   *
   * <p>This ensures that new subtypes of the target class can later be linked and/or class loaded
   * at runtime.
   *
   * <p>This does not ensure that the class remains if it is otherwise dead code and can be fully
   * removed.
   *
   * <p>Note that this constraint does not ensure that particular methods remain on the target
   * class. If methods or fields of the target class are being targeted by a subclass that was
   * classloaded or linked later, then keep annotations are needed for those targets too. Such
   * non-visible uses requires the same annotations to preserve as for reflective uses.
   */
  CLASS_OPEN_HIERARCHY,

  /**
   * Indicates that the target item must retain its generic signature if present.
   *
   * <p>This ensures that the generic signature remains, but does not prohibit rewriting of the
   * generic signature. For example, if a type present in the generic signature is renamed, the
   * generic signature will also be updated to now refer to the type by its renamed name.
   *
   * <p>Note that this constraint does not otherwise restrict what can be done to the target, such
   * as removing the target completely, inlining it, etc.
   */
  GENERIC_SIGNATURE,
}
