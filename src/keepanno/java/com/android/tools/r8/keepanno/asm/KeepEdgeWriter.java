// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.asm;

import com.android.tools.r8.keepanno.ast.AccessVisibility;
import com.android.tools.r8.keepanno.ast.AnnotationConstants;
import com.android.tools.r8.keepanno.ast.AnnotationConstants.AnnotationPattern;
import com.android.tools.r8.keepanno.ast.AnnotationConstants.Condition;
import com.android.tools.r8.keepanno.ast.AnnotationConstants.Edge;
import com.android.tools.r8.keepanno.ast.AnnotationConstants.Extracted;
import com.android.tools.r8.keepanno.ast.AnnotationConstants.Item;
import com.android.tools.r8.keepanno.ast.AnnotationConstants.MemberAccess;
import com.android.tools.r8.keepanno.ast.AnnotationConstants.Target;
import com.android.tools.r8.keepanno.ast.KeepClassItemPattern;
import com.android.tools.r8.keepanno.ast.KeepConsequences;
import com.android.tools.r8.keepanno.ast.KeepEdge;
import com.android.tools.r8.keepanno.ast.KeepEdgeMetaInfo;
import com.android.tools.r8.keepanno.ast.KeepFieldPattern;
import com.android.tools.r8.keepanno.ast.KeepItemPattern;
import com.android.tools.r8.keepanno.ast.KeepMemberAccessPattern;
import com.android.tools.r8.keepanno.ast.KeepMemberItemPattern;
import com.android.tools.r8.keepanno.ast.KeepMemberPattern;
import com.android.tools.r8.keepanno.ast.KeepMethodPattern;
import com.android.tools.r8.keepanno.ast.KeepPreconditions;
import com.android.tools.r8.keepanno.ast.KeepQualifiedClassNamePattern;
import com.android.tools.r8.keepanno.ast.ModifierPattern;
import com.android.tools.r8.keepanno.ast.OptionalPattern;
import com.android.tools.r8.keepanno.utils.Unimplemented;
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

  public static void writeExtractedEdge(
      KeepEdge edge, BiFunction<String, Boolean, AnnotationVisitorInterface> getVisitor) {
    withNewVisitor(
        wrap(getVisitor.apply(Extracted.DESCRIPTOR, false)),
        extractVisitor -> {
          extractVisitor.visit("version", edge.getMetaInfo().getVersion().toVersionString());
          extractVisitor.visit("context", edge.getMetaInfo().getContextDescriptorString());
          withNewVisitor(
              extractVisitor.visitArray("edges"),
              edgeVisitor ->
                  writeEdgeInternal(
                      edge, (desc, visible) -> edgeVisitor.visitAnnotation(null, desc)));
        });
  }

  public static void writeEdge(
      KeepEdge edge, BiFunction<String, Boolean, AnnotationVisitorInterface> getVisitor) {
    writeEdgeInternal(edge, (descriptor, visible) -> wrap(getVisitor.apply(descriptor, visible)));
  }

  public static void writeEdgeInternal(
      KeepEdge edge, BiFunction<String, Boolean, AnnotationVisitor> getVisitor) {
    withNewVisitor(
        getVisitor.apply(Edge.DESCRIPTOR, false),
        visitor -> new KeepEdgeWriter().writeEdge(edge, visitor));
  }

  private void writeEdge(KeepEdge edge, AnnotationVisitor visitor) {
    writeMetaInfo(visitor, edge.getMetaInfo());
    writePreconditions(visitor, edge.getPreconditions());
    writeConsequences(visitor, edge.getConsequences());
  }

  private void writeMetaInfo(AnnotationVisitor visitor, KeepEdgeMetaInfo metaInfo) {}

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
                        conditionVisitor -> {
                          if (condition.getItem().isBindingReference()) {
                            throw new Unimplemented();
                          }
                          writeItem(conditionVisitor, condition.getItem().asItemPattern());
                        })));
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
                        targetVisitor -> {
                          if (target.getItem().isBindingReference()) {
                            throw new Unimplemented();
                          }
                          writeItem(targetVisitor, target.getItem().asItemPattern());
                        })));
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
    KeepQualifiedClassNamePattern namePattern = classItemPattern.getClassNamePattern();
    if (namePattern.isExact()) {
      Type typeConstant = Type.getType(namePattern.getExactDescriptor());
      itemVisitor.visit(AnnotationConstants.Item.className, typeConstant.getClassName());
    } else {
      throw new Unimplemented();
    }
    if (!classItemPattern.getInstanceOfPattern().isAny()) {
      throw new Unimplemented();
    }
  }

  private void writeMemberItem(
      KeepMemberItemPattern memberItemPattern, AnnotationVisitor itemVisitor) {
    if (memberItemPattern.getClassReference().isBindingReference()) {
      throw new Unimplemented();
    }
    writeClassItem(memberItemPattern.getClassReference().asClassItemPattern(), itemVisitor);
    writeMember(memberItemPattern.getMemberPattern(), itemVisitor);
  }

  private void writeMember(KeepMemberPattern memberPattern, AnnotationVisitor targetVisitor) {
    if (memberPattern.isAllMembers()) {
      throw new Unimplemented();
    }
    if (memberPattern.isMethod()) {
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
    writeAccessPattern(Item.memberAccess, member.getAccessPattern(), targetVisitor);
  }

  private void writeField(KeepFieldPattern field, AnnotationVisitor targetVisitor) {
    String exactFieldName = field.getNamePattern().asExactString();
    if (exactFieldName != null) {
      targetVisitor.visit(Item.fieldName, exactFieldName);
    } else {
      throw new Unimplemented();
    }
    if (!field.getAccessPattern().isAny()) {
      throw new Unimplemented();
    }
    if (!field.getTypePattern().isAny()) {
      throw new Unimplemented();
    }
  }

  private void writeMethod(KeepMethodPattern method, AnnotationVisitor targetVisitor) {
    String exactMethodName = method.getNamePattern().asExactString();
    if (exactMethodName != null) {
      targetVisitor.visit(Item.methodName, exactMethodName);
    } else {
      throw new Unimplemented();
    }
    if (!method.getAccessPattern().isAny()) {
      throw new Unimplemented();
    }
    if (!method.getReturnTypePattern().isAny()) {
      if ((method.getNamePattern().isInstanceInitializer()
              || method.getNamePattern().isClassInitializer())
          && method.getReturnTypePattern().isVoid()) {
        // constructors have implicit void return.
      } else {
        throw new Unimplemented();
      }
    }
    if (!method.getParametersPattern().isAny()) {
      throw new Unimplemented();
    }
  }

  private void writeAnnotatedBy(
      String propertyName,
      OptionalPattern<KeepQualifiedClassNamePattern> annotatedByPattern,
      AnnotationVisitor targetVisitor) {
    if (annotatedByPattern.isAbsent()) {
      return;
    }
    withNewVisitor(
        targetVisitor.visitAnnotation(propertyName, AnnotationPattern.DESCRIPTOR),
        visitor -> {
          throw new Unimplemented("...");
        });
  }

  private void writeModifierEnumValue(
      ModifierPattern pattern, String value, String desc, AnnotationVisitor arrayVisitor) {
    if (pattern.isAny()) {
      return;
    }
    if (pattern.isOnlyPositive()) {
      arrayVisitor.visitEnum(null, desc, value);
    }
    assert pattern.isOnlyNegative();
    arrayVisitor.visitEnum(null, desc, MemberAccess.NEGATION_PREFIX + value);
  }

  private void writeAccessPattern(
      String propertyName, KeepMemberAccessPattern accessPattern, AnnotationVisitor targetVisitor) {
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
                  visitor.visitEnum(null, MemberAccess.DESCRIPTOR, MemberAccess.PUBLIC);
                  break;
                case PROTECTED:
                  visitor.visitEnum(null, MemberAccess.DESCRIPTOR, MemberAccess.PROTECTED);
                  break;
                case PACKAGE_PRIVATE:
                  visitor.visitEnum(null, MemberAccess.DESCRIPTOR, MemberAccess.PACKAGE_PRIVATE);
                  break;
                case PRIVATE:
                  visitor.visitEnum(null, MemberAccess.DESCRIPTOR, MemberAccess.PRIVATE);
                  break;
              }
            }
          }
          writeModifierEnumValue(
              accessPattern.getStaticPattern(),
              MemberAccess.STATIC,
              MemberAccess.DESCRIPTOR,
              visitor);
          writeModifierEnumValue(
              accessPattern.getFinalPattern(),
              MemberAccess.FINAL,
              MemberAccess.DESCRIPTOR,
              visitor);
          writeModifierEnumValue(
              accessPattern.getSyntheticPattern(),
              MemberAccess.SYNTHETIC,
              MemberAccess.DESCRIPTOR,
              visitor);
        });
  }
}
