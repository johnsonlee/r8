// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.asm;

import com.android.tools.r8.keepanno.ast.AccessVisibility;
import com.android.tools.r8.keepanno.ast.AnnotationConstants;
import com.android.tools.r8.keepanno.ast.AnnotationConstants.AnnotationPattern;
import com.android.tools.r8.keepanno.ast.AnnotationConstants.Binding;
import com.android.tools.r8.keepanno.ast.AnnotationConstants.ClassNamePattern;
import com.android.tools.r8.keepanno.ast.AnnotationConstants.Condition;
import com.android.tools.r8.keepanno.ast.AnnotationConstants.Constraints;
import com.android.tools.r8.keepanno.ast.AnnotationConstants.Edge;
import com.android.tools.r8.keepanno.ast.AnnotationConstants.ExtractedAnnotation;
import com.android.tools.r8.keepanno.ast.AnnotationConstants.ExtractedAnnotations;
import com.android.tools.r8.keepanno.ast.AnnotationConstants.FieldAccess;
import com.android.tools.r8.keepanno.ast.AnnotationConstants.InstanceOfPattern;
import com.android.tools.r8.keepanno.ast.AnnotationConstants.Item;
import com.android.tools.r8.keepanno.ast.AnnotationConstants.Kind;
import com.android.tools.r8.keepanno.ast.AnnotationConstants.MemberAccess;
import com.android.tools.r8.keepanno.ast.AnnotationConstants.MethodAccess;
import com.android.tools.r8.keepanno.ast.AnnotationConstants.StringPattern;
import com.android.tools.r8.keepanno.ast.AnnotationConstants.Target;
import com.android.tools.r8.keepanno.ast.AnnotationConstants.TypePattern;
import com.android.tools.r8.keepanno.ast.KeepAnnotationPattern;
import com.android.tools.r8.keepanno.ast.KeepBindingReference;
import com.android.tools.r8.keepanno.ast.KeepBindings;
import com.android.tools.r8.keepanno.ast.KeepClassBindingReference;
import com.android.tools.r8.keepanno.ast.KeepClassItemPattern;
import com.android.tools.r8.keepanno.ast.KeepConsequences;
import com.android.tools.r8.keepanno.ast.KeepConstraint;
import com.android.tools.r8.keepanno.ast.KeepConstraints;
import com.android.tools.r8.keepanno.ast.KeepDeclaration;
import com.android.tools.r8.keepanno.ast.KeepEdge;
import com.android.tools.r8.keepanno.ast.KeepEdgeException;
import com.android.tools.r8.keepanno.ast.KeepEdgeMetaInfo;
import com.android.tools.r8.keepanno.ast.KeepFieldAccessPattern;
import com.android.tools.r8.keepanno.ast.KeepFieldPattern;
import com.android.tools.r8.keepanno.ast.KeepInstanceOfPattern;
import com.android.tools.r8.keepanno.ast.KeepItemPattern;
import com.android.tools.r8.keepanno.ast.KeepMemberAccessPattern;
import com.android.tools.r8.keepanno.ast.KeepMemberItemPattern;
import com.android.tools.r8.keepanno.ast.KeepMemberPattern;
import com.android.tools.r8.keepanno.ast.KeepMethodAccessPattern;
import com.android.tools.r8.keepanno.ast.KeepMethodParametersPattern;
import com.android.tools.r8.keepanno.ast.KeepMethodPattern;
import com.android.tools.r8.keepanno.ast.KeepMethodReturnTypePattern;
import com.android.tools.r8.keepanno.ast.KeepPackagePattern;
import com.android.tools.r8.keepanno.ast.KeepPreconditions;
import com.android.tools.r8.keepanno.ast.KeepQualifiedClassNamePattern;
import com.android.tools.r8.keepanno.ast.KeepStringPattern;
import com.android.tools.r8.keepanno.ast.KeepTarget;
import com.android.tools.r8.keepanno.ast.KeepTypePattern;
import com.android.tools.r8.keepanno.ast.ModifierPattern;
import com.android.tools.r8.keepanno.ast.OptionalPattern;
import com.android.tools.r8.keepanno.utils.Unimplemented;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public class KeepEdgeWriter implements Opcodes {

  /** Annotation visitor interface to allow usage from tests without type conflicts in r8lib. */
  public interface AnnotationVisitorInterface {
    int version();

    void visit(String name, Object value);

    void visitEnum(String name, String descriptor, String value);

    AnnotationVisitorInterface visitAnnotation(String name, String descriptor);

    AnnotationVisitorInterface visitArray(String name);

    void visitEnd();
  }

  private static AnnotationVisitor wrap(AnnotationVisitorInterface visitor) {
    if (visitor == null) {
      return null;
    }
    return new AnnotationVisitor(visitor.version()) {

      @Override
      public void visit(String name, Object value) {
        visitor.visit(name, value);
      }

      @Override
      public void visitEnum(String name, String descriptor, String value) {
        visitor.visitEnum(name, descriptor, value);
      }

      @Override
      public AnnotationVisitor visitAnnotation(String name, String descriptor) {
        AnnotationVisitorInterface v = visitor.visitAnnotation(name, descriptor);
        return v == visitor ? this : wrap(v);
      }

      @Override
      public AnnotationVisitor visitArray(String name) {
        AnnotationVisitorInterface v = visitor.visitArray(name);
        return v == visitor ? this : wrap(v);
      }

      @Override
      public void visitEnd() {
        visitor.visitEnd();
      }
    };
  }

  // Helper to ensure that any call creating a new annotation visitor is statically scoped with its
  // call to visit end.
  private static void withNewVisitor(AnnotationVisitor visitor, Consumer<AnnotationVisitor> fn) {
    fn.accept(visitor);
    visitor.visitEnd();
  }

  public static void writeExtractedEdges(
      List<KeepDeclaration> declarations,
      BiFunction<String, Boolean, AnnotationVisitorInterface> getVisitor) {
    if (declarations.isEmpty()) {
      return;
    }
    withNewVisitor(
        wrap(getVisitor.apply(ExtractedAnnotations.DESCRIPTOR, false)),
        containerVisitor ->
            withNewVisitor(
                containerVisitor.visitArray(ExtractedAnnotations.value),
                arrayVisitor ->
                    declarations.forEach(
                        decl ->
                            withNewVisitor(
                                arrayVisitor.visitAnnotation(null, ExtractedAnnotation.DESCRIPTOR),
                                extractVisitor -> writeExtractedEdge(extractVisitor, decl)))));
  }

  private static void writeExtractedEdge(AnnotationVisitor visitor, KeepDeclaration decl) {
    KeepEdgeMetaInfo metaInfo = decl.getMetaInfo();
    visitor.visit(ExtractedAnnotation.version, metaInfo.getVersion().toVersionString());
    visitor.visit(ExtractedAnnotation.context, metaInfo.getContextDescriptorString());
    decl.match(
        edge -> {
          withNewVisitor(
              visitor.visitAnnotation(ExtractedAnnotation.edge, Edge.DESCRIPTOR),
              v -> new KeepEdgeWriter().writeEdge(edge, v));
        },
        check -> {
          switch (check.getKind()) {
            case REMOVED:
              visitor.visit(ExtractedAnnotation.checkRemoved, true);
              break;
            case OPTIMIZED_OUT:
              visitor.visit(ExtractedAnnotation.checkOptimizedOut, true);
              break;
            default:
              throw new KeepEdgeException("Unexpected keep check kind: " + check.getKind());
          }
        });
  }

  private void writeEdge(KeepEdge edge, AnnotationVisitor visitor) {
    writeMetaInfo(visitor, edge.getMetaInfo());
    writeBindings(visitor, edge.getBindings());
    writePreconditions(visitor, edge.getPreconditions());
    writeConsequences(visitor, edge.getConsequences());
  }

  private void writeMetaInfo(AnnotationVisitor visitor, KeepEdgeMetaInfo metaInfo) {
    // The edge version and context is written in the extraction header.
    if (metaInfo.hasDescription()) {
      visitor.visit(Edge.description, metaInfo.getDescriptionString());
    }
  }

  private void writeBindings(AnnotationVisitor visitor, KeepBindings bindings) {
    if (bindings.isEmpty()) {
      return;
    }
    withNewVisitor(
        visitor.visitArray(Edge.bindings),
        arrayVisitor -> {
          bindings.forEach(
              (symbol, item) -> {
                withNewVisitor(
                    arrayVisitor.visitAnnotation(null, Binding.DESCRIPTOR),
                    bindingVisitor -> {
                      bindingVisitor.visit(Binding.bindingName, symbol.toString());
                      // The item is written directly into the binding annotation.
                      writeItem(bindingVisitor, item);
                    });
              });
        });
  }

  private void writeStringPattern(
      KeepStringPattern stringPattern, String propertyName, AnnotationVisitor visitor) {
    withNewVisitor(
        visitor.visitAnnotation(propertyName, AnnotationConstants.StringPattern.DESCRIPTOR),
        v -> {
          if (stringPattern.isAny()) {
            // The emtpy pattern matches any string.
            return;
          }
          if (stringPattern.isExact()) {
            v.visit(StringPattern.exact, stringPattern.asExactString());
            return;
          }
          if (stringPattern.hasPrefix()) {
            v.visit(StringPattern.startsWith, stringPattern.getPrefixString());
          }
          if (stringPattern.hasSuffix()) {
            v.visit(StringPattern.endsWith, stringPattern.getSuffixString());
          }
        });
  }

  private void writePreconditions(AnnotationVisitor visitor, KeepPreconditions preconditions) {
    if (preconditions.isAlways()) {
      return;
    }
    String ignoredArrayValueName = null;
    withNewVisitor(
        visitor.visitArray(Edge.preconditions),
        arrayVisitor ->
            preconditions.forEach(
                condition ->
                    withNewVisitor(
                        arrayVisitor.visitAnnotation(ignoredArrayValueName, Condition.DESCRIPTOR),
                        conditionVisitor ->
                            writeItemReference(conditionVisitor, condition.getItem()))));
  }

  private void writeConsequences(AnnotationVisitor visitor, KeepConsequences consequences) {
    assert !consequences.isEmpty();
    String ignoredArrayValueName = null;
    withNewVisitor(
        visitor.visitArray(Edge.consequences),
        arrayVisitor ->
            consequences.forEachTarget(
                target ->
                    withNewVisitor(
                        arrayVisitor.visitAnnotation(ignoredArrayValueName, Target.DESCRIPTOR),
                        targetVisitor -> writeTarget(target, targetVisitor))));
  }

  private void writeTarget(KeepTarget target, AnnotationVisitor visitor) {
    writeConstraints(visitor, target.getConstraints(), target.getItem());
    writeItemReference(visitor, target.getItem());
  }

  private void writeConstraints(
      AnnotationVisitor visitor, KeepConstraints constraints, KeepBindingReference item) {
    Set<KeepConstraint> typedConstraints;
    if (item.isClassType()) {
      typedConstraints = constraints.getClassConstraints();
    } else {
      typedConstraints = constraints.getMemberConstraints();
    }

    List<String> constraintEnumValues = new ArrayList<>();
    List<KeepAnnotationPattern> annotationConstraints = new ArrayList<>();
    for (KeepConstraint constraint : typedConstraints) {
      String value = constraint.getEnumValue();
      if (value != null) {
        constraintEnumValues.add(value);
        continue;
      }
      KeepAnnotationPattern annotationPattern = constraint.asAnnotationPattern();
      if (annotationPattern != null) {
        annotationConstraints.add(annotationPattern);
        continue;
      }
      throw new Unimplemented("Missing: " + constraint.getClass().toString());
    }
    // The default constraints is *not* the empty set, so always write it as defined.
    constraintEnumValues.sort(String::compareTo);
    withNewVisitor(
        visitor.visitArray(Target.constraints),
        arrayVisitor ->
            constraintEnumValues.forEach(
                c -> arrayVisitor.visitEnum(null, Constraints.DESCRIPTOR, c)));
    if (!annotationConstraints.isEmpty()) {
      if (annotationConstraints.size() > 1) {
        annotationConstraints.sort(
            Comparator.comparing((KeepAnnotationPattern p) -> p.getNamePattern().toString())
                .thenComparingInt(p -> p.includesClassRetention() ? 1 : 0)
                .thenComparingInt(p -> p.includesRuntimeRetention() ? 1 : 0));
      }
      withNewVisitor(
          visitor.visitArray(Target.constrainAnnotations),
          arrayVisitor ->
              annotationConstraints.forEach(
                  annotation -> {
                    withNewVisitor(
                        arrayVisitor.visitAnnotation(null, AnnotationPattern.DESCRIPTOR),
                        annoVisitor -> {
                          writeClassNamePattern(
                              annotation.getNamePattern(),
                              AnnotationPattern.namePattern,
                              annoVisitor);
                          assert annotation.includesClassRetention()
                              || annotation.includesRuntimeRetention();
                          withNewVisitor(
                              annoVisitor.visitArray(AnnotationPattern.retention),
                              retentionArrayVisitor -> {
                                if (annotation.includesClassRetention()) {
                                  retentionArrayVisitor.visitEnum(
                                      null,
                                      "Ljava/lang/annotation/RetentionPolicy;",
                                      RetentionPolicy.CLASS.name());
                                }
                                if (annotation.includesRuntimeRetention()) {
                                  retentionArrayVisitor.visitEnum(
                                      null,
                                      "Ljava/lang/annotation/RetentionPolicy;",
                                      RetentionPolicy.RUNTIME.name());
                                }
                              });
                        });
                  }));
    }
  }

  private void writeItemReference(
      AnnotationVisitor visitor, KeepBindingReference bindingReference) {
    String bindingProperty =
        bindingReference.isClassType() ? Item.classFromBinding : Item.memberFromBinding;
    visitor.visit(bindingProperty, bindingReference.getName().toString());
  }

  private void writeItem(AnnotationVisitor itemVisitor, KeepItemPattern item) {
    if (item.isClassItemPattern()) {
      writeClassItem(item.asClassItemPattern(), itemVisitor);
    } else {
      writeMemberItem(item.asMemberItemPattern(), itemVisitor);
    }
  }

  private void writeClassItem(
      KeepClassItemPattern classItemPattern, AnnotationVisitor itemVisitor) {
    writeAnnotatedBy(
        Item.classAnnotatedByClassNamePattern,
        classItemPattern.getAnnotatedByPattern(),
        itemVisitor);
    writeClassNamePattern(
        classItemPattern.getClassNamePattern(), Item.classNamePattern, itemVisitor);
    writeInstanceOfPattern(classItemPattern.getInstanceOfPattern(), itemVisitor);
  }

  private void writeInstanceOfPattern(
      KeepInstanceOfPattern instanceOfPattern, AnnotationVisitor visitor) {
    if (instanceOfPattern.isAny()) {
      return;
    }
    withNewVisitor(
        visitor.visitAnnotation(Item.instanceOfPattern, InstanceOfPattern.DESCRIPTOR),
        v -> {
          v.visit(InstanceOfPattern.inclusive, instanceOfPattern.isInclusive());
          writeClassNamePattern(
              instanceOfPattern.getClassNamePattern(), InstanceOfPattern.classNamePattern, v);
        });
  }

  private void writeMemberItem(
      KeepMemberItemPattern memberItemPattern, AnnotationVisitor itemVisitor) {
    KeepClassBindingReference bindingReference = memberItemPattern.getClassReference();
    itemVisitor.visit(Item.classFromBinding, bindingReference.getName().toString());
    writeMember(memberItemPattern.getMemberPattern(), itemVisitor);
  }

  private void writeMember(KeepMemberPattern memberPattern, AnnotationVisitor targetVisitor) {
    if (memberPattern.isAllMembers()) {
      // Due to the empty default being a class, we need to set the kind to members.
      targetVisitor.visitEnum(Target.kind, Kind.DESCRIPTOR, Kind.ONLY_MEMBERS);
    } else if (memberPattern.isMethod()) {
      writeMethod(memberPattern.asMethod(), targetVisitor);
    } else if (memberPattern.isField()) {
      writeField(memberPattern.asField(), targetVisitor);
    } else {
      writeGeneralMember(memberPattern, targetVisitor);
    }
  }

  private void writeGeneralMember(KeepMemberPattern member, AnnotationVisitor targetVisitor) {
    assert member.isGeneralMember();
    assert !member.isField();
    assert !member.isMethod();
    writeAnnotatedBy(
        Item.memberAnnotatedByClassNamePattern, member.getAnnotatedByPattern(), targetVisitor);
    writeGeneralMemberAccessPattern(Item.memberAccess, member.getAccessPattern(), targetVisitor);
  }

  private void writeField(KeepFieldPattern field, AnnotationVisitor targetVisitor) {
    writeAnnotatedBy(
        Item.fieldAnnotatedByClassNamePattern, field.getAnnotatedByPattern(), targetVisitor);
    writeFieldAccessPattern(Item.fieldAccess, field.getAccessPattern(), targetVisitor);
    writeStringPattern(
        field.getNamePattern().asStringPattern(), Item.fieldNamePattern, targetVisitor);
    if (!field.getTypePattern().isAny()) {
      writeTypePattern(field.getTypePattern().asType(), Item.fieldTypePattern, targetVisitor);
    }
  }

  private void writeMethod(KeepMethodPattern method, AnnotationVisitor targetVisitor) {
    writeAnnotatedBy(
        Item.methodAnnotatedByClassNamePattern, method.getAnnotatedByPattern(), targetVisitor);
    writeMethodAccessPattern(Item.methodAccess, method.getAccessPattern(), targetVisitor);
    writeStringPattern(
        method.getNamePattern().asStringPattern(), Item.methodNamePattern, targetVisitor);
    writeMethodReturnType(method.getReturnTypePattern(), targetVisitor);
    writeMethodParameters(method.getParametersPattern(), targetVisitor);
  }

  private void writeMethodParameters(
      KeepMethodParametersPattern parametersPattern, AnnotationVisitor targetVisitor) {
    if (parametersPattern.isAny()) {
      return;
    }
    withNewVisitor(
        targetVisitor.visitArray(Item.methodParameterTypePatterns),
        v ->
            parametersPattern
                .asList()
                .forEach(parameterPattern -> writeTypePattern(parameterPattern, null, v)));
  }

  private void writeMethodReturnType(
      KeepMethodReturnTypePattern returnTypePattern, AnnotationVisitor targetVisitor) {
    if (returnTypePattern.isAny()) {
      return;
    }
    if (returnTypePattern.isVoid()) {
      targetVisitor.visit(Item.methodReturnType, "void");
      return;
    }
    assert returnTypePattern.isType();
    writeTypePattern(returnTypePattern.asType(), Item.methodReturnTypePattern, targetVisitor);
  }

  private void writeTypePattern(
      KeepTypePattern typePattern, String propertyName, AnnotationVisitor targetVisitor) {
    withNewVisitor(
        targetVisitor.visitAnnotation(propertyName, TypePattern.DESCRIPTOR),
        v ->
            typePattern.match(
                () -> {
                  // The empty type pattern matches any type.
                },
                primitive -> {
                  if (primitive.isAny()) {
                    throw new Unimplemented("No support for any-primitive.");
                  }
                  v.visit(TypePattern.name, Type.getType(primitive.getDescriptor()).getClassName());
                },
                array -> {
                  v.visit(TypePattern.name, Type.getType(array.getDescriptor()).getClassName());
                },
                clazz -> {
                  writeClassNamePattern(clazz, TypePattern.classNamePattern, v);
                },
                instanceOf -> {
                  writeInstanceOfPattern(instanceOf, v);
                }));
  }

  private void writeClassNamePattern(
      KeepQualifiedClassNamePattern clazz, String propertyName, AnnotationVisitor visitor) {
    withNewVisitor(
        visitor.visitAnnotation(propertyName, ClassNamePattern.DESCRIPTOR),
        v -> {
          if (clazz.isAny()) {
            // The empty pattern matches any class-name
            return;
          }
          KeepPackagePattern packagePattern = clazz.getPackagePattern();
          if (!packagePattern.isAny()) {
            assert packagePattern.isExact();
            v.visit(ClassNamePattern.packageName, packagePattern.getExactPackageAsString());
          }
          writeStringPattern(
              clazz.getNamePattern().asStringPattern(), ClassNamePattern.unqualifiedNamePattern, v);
        });
  }

  private void writeAnnotatedBy(
      String propertyName,
      OptionalPattern<KeepQualifiedClassNamePattern> annotatedByPattern,
      AnnotationVisitor targetVisitor) {
    if (annotatedByPattern.isPresent()) {
      writeClassNamePattern(annotatedByPattern.get(), propertyName, targetVisitor);
    }
  }

  private void writeModifierEnumValue(
      ModifierPattern pattern, String value, String desc, AnnotationVisitor arrayVisitor) {
    if (pattern.isAny()) {
      return;
    }
    if (pattern.isOnlyPositive()) {
      arrayVisitor.visitEnum(null, desc, value);
      return;
    }
    assert pattern.isOnlyNegative();
    arrayVisitor.visitEnum(null, desc, MemberAccess.NEGATION_PREFIX + value);
  }

  private void writeGeneralMemberAccessPattern(
      String propertyName, KeepMemberAccessPattern accessPattern, AnnotationVisitor targetVisitor) {
    internalWriteAccessPattern(
        propertyName, accessPattern, targetVisitor, MemberAccess.DESCRIPTOR, v -> {});
  }

  private void writeFieldAccessPattern(
      String propertyName, KeepFieldAccessPattern accessPattern, AnnotationVisitor targetVisitor) {
    String enumDescriptor = FieldAccess.DESCRIPTOR;
    internalWriteAccessPattern(
        propertyName,
        accessPattern,
        targetVisitor,
        enumDescriptor,
        visitor -> {
          writeModifierEnumValue(
              accessPattern.getVolatilePattern(), FieldAccess.VOLATILE, enumDescriptor, visitor);
          writeModifierEnumValue(
              accessPattern.getTransientPattern(), FieldAccess.TRANSIENT, enumDescriptor, visitor);
        });
  }

  private void writeMethodAccessPattern(
      String propertyName, KeepMethodAccessPattern accessPattern, AnnotationVisitor targetVisitor) {
    String enumDescriptor = MethodAccess.DESCRIPTOR;
    internalWriteAccessPattern(
        propertyName,
        accessPattern,
        targetVisitor,
        enumDescriptor,
        visitor -> {
          writeModifierEnumValue(
              accessPattern.getSynchronizedPattern(),
              MethodAccess.SYNCHRONIZED,
              enumDescriptor,
              visitor);
          writeModifierEnumValue(
              accessPattern.getBridgePattern(), MethodAccess.BRIDGE, enumDescriptor, visitor);
          writeModifierEnumValue(
              accessPattern.getNativePattern(), MethodAccess.NATIVE, enumDescriptor, visitor);
          writeModifierEnumValue(
              accessPattern.getAbstractPattern(), MethodAccess.ABSTRACT, enumDescriptor, visitor);
          writeModifierEnumValue(
              accessPattern.getStrictFpPattern(), MethodAccess.STRICT_FP, enumDescriptor, visitor);
        });
  }

  private void internalWriteAccessPattern(
      String propertyName,
      KeepMemberAccessPattern accessPattern,
      AnnotationVisitor targetVisitor,
      String enumDescriptor,
      Consumer<AnnotationVisitor> typeSpecificSettings) {
    if (accessPattern.isAny()) {
      return;
    }
    withNewVisitor(
        targetVisitor.visitArray(propertyName),
        visitor -> {
          if (!accessPattern.isAnyVisibility()) {
            for (AccessVisibility visibility : accessPattern.getAllowedAccessVisibilities()) {
              switch (visibility) {
                case PUBLIC:
                  visitor.visitEnum(null, enumDescriptor, MemberAccess.PUBLIC);
                  break;
                case PROTECTED:
                  visitor.visitEnum(null, enumDescriptor, MemberAccess.PROTECTED);
                  break;
                case PACKAGE_PRIVATE:
                  visitor.visitEnum(null, enumDescriptor, MemberAccess.PACKAGE_PRIVATE);
                  break;
                case PRIVATE:
                  visitor.visitEnum(null, enumDescriptor, MemberAccess.PRIVATE);
                  break;
              }
            }
          }
          writeModifierEnumValue(
              accessPattern.getStaticPattern(), MemberAccess.STATIC, enumDescriptor, visitor);
          writeModifierEnumValue(
              accessPattern.getFinalPattern(), MemberAccess.FINAL, enumDescriptor, visitor);
          writeModifierEnumValue(
              accessPattern.getSyntheticPattern(), MemberAccess.SYNTHETIC, enumDescriptor, visitor);

          typeSpecificSettings.accept(visitor);
        });
  }
}
