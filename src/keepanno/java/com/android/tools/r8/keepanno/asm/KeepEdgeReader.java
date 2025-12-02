// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.asm;

import com.android.tools.r8.keepanno.asm.ClassNameParser.ClassNameProperty;
import com.android.tools.r8.keepanno.asm.ConstraintPropertiesParser.ConstraintsProperty;
import com.android.tools.r8.keepanno.asm.InstanceOfParser.InstanceOfProperties;
import com.android.tools.r8.keepanno.asm.StringPatternParser.StringProperty;
import com.android.tools.r8.keepanno.asm.TypeParser.TypeProperty;
import com.android.tools.r8.keepanno.ast.AccessVisibility;
import com.android.tools.r8.keepanno.ast.AnnotationConstants;
import com.android.tools.r8.keepanno.ast.AnnotationConstants.Binding;
import com.android.tools.r8.keepanno.ast.AnnotationConstants.Condition;
import com.android.tools.r8.keepanno.ast.AnnotationConstants.Edge;
import com.android.tools.r8.keepanno.ast.AnnotationConstants.FieldAccess;
import com.android.tools.r8.keepanno.ast.AnnotationConstants.ForApi;
import com.android.tools.r8.keepanno.ast.AnnotationConstants.Item;
import com.android.tools.r8.keepanno.ast.AnnotationConstants.Kind;
import com.android.tools.r8.keepanno.ast.AnnotationConstants.MemberAccess;
import com.android.tools.r8.keepanno.ast.AnnotationConstants.MethodAccess;
import com.android.tools.r8.keepanno.ast.AnnotationConstants.Target;
import com.android.tools.r8.keepanno.ast.AnnotationConstants.UsedByReflection;
import com.android.tools.r8.keepanno.ast.AnnotationConstants.UsesReflectionToAccessField;
import com.android.tools.r8.keepanno.ast.AnnotationConstants.UsesReflectionToAccessMethod;
import com.android.tools.r8.keepanno.ast.AnnotationConstants.UsesReflectionToConstruct;
import com.android.tools.r8.keepanno.ast.KeepAnnotationPattern;
import com.android.tools.r8.keepanno.ast.KeepBindingReference;
import com.android.tools.r8.keepanno.ast.KeepBindings;
import com.android.tools.r8.keepanno.ast.KeepBindings.KeepBindingSymbol;
import com.android.tools.r8.keepanno.ast.KeepCheck;
import com.android.tools.r8.keepanno.ast.KeepCheck.KeepCheckKind;
import com.android.tools.r8.keepanno.ast.KeepClassBindingReference;
import com.android.tools.r8.keepanno.ast.KeepClassItemPattern;
import com.android.tools.r8.keepanno.ast.KeepClassPattern;
import com.android.tools.r8.keepanno.ast.KeepCondition;
import com.android.tools.r8.keepanno.ast.KeepConsequences;
import com.android.tools.r8.keepanno.ast.KeepConstraint;
import com.android.tools.r8.keepanno.ast.KeepConstraints;
import com.android.tools.r8.keepanno.ast.KeepDeclaration;
import com.android.tools.r8.keepanno.ast.KeepEdge;
import com.android.tools.r8.keepanno.ast.KeepEdgeMetaInfo;
import com.android.tools.r8.keepanno.ast.KeepFieldAccessPattern;
import com.android.tools.r8.keepanno.ast.KeepFieldNamePattern;
import com.android.tools.r8.keepanno.ast.KeepFieldPattern;
import com.android.tools.r8.keepanno.ast.KeepFieldTypePattern;
import com.android.tools.r8.keepanno.ast.KeepInstanceOfPattern;
import com.android.tools.r8.keepanno.ast.KeepItemPattern;
import com.android.tools.r8.keepanno.ast.KeepMemberAccessPattern;
import com.android.tools.r8.keepanno.ast.KeepMemberAccessPattern.BuilderBase;
import com.android.tools.r8.keepanno.ast.KeepMemberBindingReference;
import com.android.tools.r8.keepanno.ast.KeepMemberItemPattern;
import com.android.tools.r8.keepanno.ast.KeepMemberPattern;
import com.android.tools.r8.keepanno.ast.KeepMethodAccessPattern;
import com.android.tools.r8.keepanno.ast.KeepMethodNamePattern;
import com.android.tools.r8.keepanno.ast.KeepMethodParametersPattern;
import com.android.tools.r8.keepanno.ast.KeepMethodPattern;
import com.android.tools.r8.keepanno.ast.KeepMethodReturnTypePattern;
import com.android.tools.r8.keepanno.ast.KeepPreconditions;
import com.android.tools.r8.keepanno.ast.KeepQualifiedClassNamePattern;
import com.android.tools.r8.keepanno.ast.KeepStringPattern;
import com.android.tools.r8.keepanno.ast.KeepTarget;
import com.android.tools.r8.keepanno.ast.KeepTypePattern;
import com.android.tools.r8.keepanno.ast.OptionalPattern;
import com.android.tools.r8.keepanno.ast.ParsingContext;
import com.android.tools.r8.keepanno.ast.ParsingContext.AnnotationParsingContext;
import com.android.tools.r8.keepanno.ast.ParsingContext.ClassParsingContext;
import com.android.tools.r8.keepanno.ast.ParsingContext.FieldParsingContext;
import com.android.tools.r8.keepanno.ast.ParsingContext.MethodParsingContext;
import com.android.tools.r8.keepanno.ast.ParsingContext.PropertyParsingContext;
import com.android.tools.r8.keepanno.utils.DescriptorUtils;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public class KeepEdgeReader implements Opcodes {

  public static int ASM_VERSION = ASM9;

  public static boolean isClassKeepAnnotation(String descriptor, boolean visible) {
    return !visible && isEmbeddedAnnotation(descriptor);
  }

  public static boolean isFieldKeepAnnotation(String descriptor, boolean visible) {
    return !visible && isEmbeddedAnnotation(descriptor);
  }

  public static boolean isMethodKeepAnnotation(String descriptor, boolean visible) {
    return !visible && isEmbeddedAnnotation(descriptor);
  }

  private static boolean isEmbeddedAnnotation(String descriptor) {
    if (AnnotationConstants.Edge.isDescriptor(descriptor)
        || AnnotationConstants.UsesReflection.isDescriptor(descriptor)
        || AnnotationConstants.UsesReflectionToConstruct.isDescriptor(descriptor)
        || AnnotationConstants.UsesReflectionToAccessMethod.isDescriptor(descriptor)
        || AnnotationConstants.UsesReflectionToAccessField.isDescriptor(descriptor)
        || AnnotationConstants.UnconditionallyKeep.isDescriptor(descriptor)
        || AnnotationConstants.ForApi.isDescriptor(descriptor)
        || AnnotationConstants.UsedByReflection.isDescriptor(descriptor)
        || AnnotationConstants.UsedByNative.isDescriptor(descriptor)
        || AnnotationConstants.CheckRemoved.isDescriptor(descriptor)
        || AnnotationConstants.CheckOptimizedOut.isDescriptor(descriptor)
        || AnnotationConstants.UsesReflectionToConstruct.isKotlinRepeatableContainerDescriptor(
            descriptor)
        || AnnotationConstants.UsesReflectionToAccessMethod.isKotlinRepeatableContainerDescriptor(
            descriptor)
        || AnnotationConstants.UsesReflectionToAccessField.isKotlinRepeatableContainerDescriptor(
            descriptor)) {
      return true;
    }
    return false;
  }

  private static String kotlinTypeToJavaType(String kotlinType) {
    switch (kotlinType) {
      case "Boolean":
        return "boolean";
      case "Byte":
        return "byte";
      case "Short":
        return "short";
      case "Char":
        return "char";
      case "Int":
        return "int";
      case "Long":
        return "long";
      case "Float":
        return "float";
      case "Double":
        return "double";
      default:
        return kotlinType;
    }
  }

  public static List<KeepDeclaration> readKeepEdges(byte[] classFileBytes) {
    return internalReadKeepEdges(classFileBytes, true);
  }

  public static ClassVisitor getClassVisitor(Parent<KeepDeclaration> consumer) {
    return new KeepEdgeClassVisitor(true, consumer);
  }

  private static List<KeepDeclaration> internalReadKeepEdges(
      byte[] classFileBytes, boolean readEmbedded) {
    ClassReader reader = new ClassReader(classFileBytes);
    List<KeepDeclaration> declarations = new ArrayList<>();
    reader.accept(new KeepEdgeClassVisitor(readEmbedded, declarations::add), ClassReader.SKIP_CODE);
    return declarations;
  }

  public static AnnotationVisitor createClassKeepAnnotationVisitor(
      String descriptor,
      boolean visible,
      boolean readEmbedded,
      String className,
      AnnotationParsingContext parsingContext,
      Consumer<KeepDeclaration> callback) {
    return KeepEdgeClassVisitor.createAnnotationVisitor(
        descriptor,
        visible,
        readEmbedded,
        callback,
        parsingContext,
        className,
        builder -> {
          builder.setContextFromClassDescriptor(
              KeepEdgeReaderUtils.getDescriptorFromClassTypeName(className));
        });
  }

  public static AnnotationVisitor createFieldKeepAnnotationVisitor(
      String descriptor,
      boolean visible,
      boolean readEmbedded,
      String className,
      String fieldName,
      String fieldTypeDescriptor,
      AnnotationParsingContext parsingContext,
      Consumer<KeepDeclaration> callback) {
    return KeepEdgeFieldVisitor.createAnnotationVisitor(
        descriptor,
        visible,
        readEmbedded,
        callback::accept,
        parsingContext,
        className,
        fieldName,
        fieldTypeDescriptor,
        builder -> {
          builder.setContextFromFieldDescriptor(
              KeepEdgeReaderUtils.getDescriptorFromClassTypeName(className),
              fieldName,
              fieldTypeDescriptor);
        });
  }

  public static AnnotationVisitor createMethodKeepAnnotationVisitor(
      String descriptor,
      boolean visible,
      boolean readEmbedded,
      String className,
      String methodName,
      String methodDescriptor,
      AnnotationParsingContext parsingContext,
      Consumer<KeepDeclaration> callback) {
    return KeepEdgeMethodVisitor.createAnnotationVisitor(
        descriptor,
        visible,
        readEmbedded,
        callback::accept,
        parsingContext,
        className,
        methodName,
        methodDescriptor,
        (KeepEdgeMetaInfo.Builder builder) -> {
          builder.setContextFromMethodDescriptor(
              KeepEdgeReaderUtils.getDescriptorFromClassTypeName(className),
              methodName,
              methodDescriptor);
        });
  }

  private static KeepClassBindingReference classReferenceFromName(
      String className, UserBindingsHelper bindingsHelper) {
    return bindingsHelper.defineFreshClassBinding(
        KeepClassItemPattern.builder()
            .setClassNamePattern(KeepQualifiedClassNamePattern.exact(className))
            .build());
  }

  /** Internal copy of the user-facing KeepItemKind */
  public enum ItemKind {
    ONLY_CLASS,
    ONLY_MEMBERS,
    ONLY_METHODS,
    ONLY_FIELDS,
    CLASS_AND_MEMBERS,
    CLASS_AND_METHODS,
    CLASS_AND_FIELDS;

    private static ItemKind fromString(String name) {
      switch (name) {
        case Kind.ONLY_CLASS:
          return ONLY_CLASS;
        case Kind.ONLY_MEMBERS:
          return ONLY_MEMBERS;
        case Kind.ONLY_METHODS:
          return ONLY_METHODS;
        case Kind.ONLY_FIELDS:
          return ONLY_FIELDS;
        case Kind.CLASS_AND_MEMBERS:
          return CLASS_AND_MEMBERS;
        case Kind.CLASS_AND_METHODS:
          return CLASS_AND_METHODS;
        case Kind.CLASS_AND_FIELDS:
          return CLASS_AND_FIELDS;
        default:
          return null;
      }
    }

    private boolean isOnlyClass() {
      return equals(ONLY_CLASS);
    }

    private boolean requiresMembers() {
      // If requiring members it is fine to have the more specific methods or fields.
      return includesMembers();
    }

    private boolean requiresMethods() {
      return equals(ONLY_METHODS) || equals(CLASS_AND_METHODS);
    }

    private boolean requiresFields() {
      return equals(ONLY_FIELDS) || equals(CLASS_AND_FIELDS);
    }

    private boolean includesClassAndMembers() {
      return includesClass() && includesMembers();
    }

    private boolean includesClass() {
      return equals(ONLY_CLASS)
          || equals(CLASS_AND_MEMBERS)
          || equals(CLASS_AND_METHODS)
          || equals(CLASS_AND_FIELDS);
    }

    private boolean includesMembers() {
      return !equals(ONLY_CLASS);
    }

    private boolean includesMethod() {
      return equals(ONLY_MEMBERS)
          || equals(ONLY_METHODS)
          || equals(CLASS_AND_MEMBERS)
          || equals(CLASS_AND_METHODS);
    }

    private boolean includesField() {
      return equals(ONLY_MEMBERS)
          || equals(ONLY_FIELDS)
          || equals(CLASS_AND_MEMBERS)
          || equals(CLASS_AND_FIELDS);
    }
  }

  private static class KeepEdgeClassVisitor extends ClassVisitor {
    private final boolean readEmbedded;
    private final Parent<KeepDeclaration> parent;
    private String className;
    private ClassParsingContext parsingContext;

    public KeepEdgeClassVisitor(boolean readEmbedded, Parent<KeepDeclaration> parent) {
      super(ASM_VERSION);
      this.readEmbedded = readEmbedded;
      this.parent = parent;
    }

    private static String binaryNameToTypeName(String binaryName) {
      return binaryName.replace('/', '.');
    }

    @Override
    public void visit(
        int version,
        int access,
        String name,
        String signature,
        String superName,
        String[] interfaces) {
      super.visit(version, access, name, signature, superName, interfaces);
      className = binaryNameToTypeName(name);
      parsingContext = ClassParsingContext.fromName(className);
    }

    private AnnotationParsingContext annotationParsingContext(String descriptor) {
      return parsingContext.annotation(descriptor);
    }

    @Override
    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
      return createAnnotationVisitor(
          descriptor,
          visible,
          readEmbedded,
          parent::accept,
          annotationParsingContext(descriptor),
          className,
          this::setContext);
    }

    private static AnnotationVisitorBase createAnnotationVisitor(
        String descriptor,
        boolean visible,
        boolean readEmbedded,
        Consumer<KeepDeclaration> parent,
        AnnotationParsingContext parsingContext,
        String className,
        Consumer<KeepEdgeMetaInfo.Builder> setContext) {
      // Skip any visible annotations as keep annotations are not runtime visible.
      if (visible) {
        return null;
      }

      if (!readEmbedded || !isEmbeddedAnnotation(descriptor)) {
        return null;
      }
      if (Edge.isDescriptor(descriptor)) {
        return new KeepEdgeVisitor(parsingContext, parent::accept, setContext);
      }
      if (AnnotationConstants.UsesReflection.isDescriptor(descriptor)) {
        return new UsesReflectionVisitor(
            parsingContext,
            parent::accept,
            setContext,
            bindingsHelper ->
                KeepClassItemPattern.builder()
                    .setClassNamePattern(KeepQualifiedClassNamePattern.exact(className))
                    .build());
      }
      if (AnnotationConstants.UsesReflectionToConstruct.isDescriptor(descriptor)) {
        return new UsesReflectionToConstructVisitor(
            parsingContext,
            parent::accept,
            setContext,
            bindingsHelper ->
                KeepClassItemPattern.builder()
                    .setClassNamePattern(KeepQualifiedClassNamePattern.exact(className))
                    .build());
      }
      if (AnnotationConstants.UsesReflectionToAccessMethod.isDescriptor(descriptor)) {
        return new UsesReflectionToAccessMethodVisitor(
            parsingContext,
            parent::accept,
            setContext,
            bindingsHelper ->
                KeepClassItemPattern.builder()
                    .setClassNamePattern(KeepQualifiedClassNamePattern.exact(className))
                    .build());
      }
      if (AnnotationConstants.UsesReflectionToAccessField.isDescriptor(descriptor)) {
        return new UsesReflectionToAccessFieldVisitor(
            parsingContext,
            parent::accept,
            setContext,
            bindingsHelper ->
                KeepClassItemPattern.builder()
                    .setClassNamePattern(KeepQualifiedClassNamePattern.exact(className))
                    .build());
      }
      if (AnnotationConstants.UnconditionallyKeep.isDescriptor(descriptor)) {
        return new UnconditionallyKeepClassVisitor(
            parsingContext, parent::accept, setContext, className);
      }
      if (ForApi.isDescriptor(descriptor)) {
        return new ForApiClassVisitor(parsingContext, parent::accept, setContext, className);
      }
      if (UsedByReflection.isDescriptor(descriptor)
          || AnnotationConstants.UsedByNative.isDescriptor(descriptor)) {
        return new UsedByReflectionClassVisitor(
            parsingContext, parent::accept, setContext, className);
      }
      if (AnnotationConstants.CheckRemoved.isDescriptor(descriptor)) {
        return new CheckRemovedClassVisitor(
            parsingContext, parent::accept, setContext, className, KeepCheckKind.REMOVED);
      }
      if (AnnotationConstants.CheckOptimizedOut.isDescriptor(descriptor)) {
        return new CheckRemovedClassVisitor(
            parsingContext, parent::accept, setContext, className, KeepCheckKind.OPTIMIZED_OUT);
      }
      return null;
    }

    private void setContext(KeepEdgeMetaInfo.Builder builder) {
      builder.setContextFromClassDescriptor(
          KeepEdgeReaderUtils.getDescriptorFromJavaType(className));
    }

    @Override
    public MethodVisitor visitMethod(
        int access, String name, String descriptor, String signature, String[] exceptions) {
      if (readEmbedded) {
        return new KeepEdgeMethodVisitor(
            parsingContext, parent::accept, className, name, descriptor);
      }
      return null;
    }

    @Override
    public FieldVisitor visitField(
        int access, String name, String descriptor, String signature, Object value) {
      if (readEmbedded) {
        return new KeepEdgeFieldVisitor(
            parsingContext, parent::accept, className, name, descriptor);
      }
      return null;
    }
  }

  private static class KeepEdgeMethodVisitor extends MethodVisitor {
    private final Parent<KeepDeclaration> parent;
    private final String className;
    private final String methodName;
    private final String methodDescriptor;
    private final MethodParsingContext parsingContext;

    KeepEdgeMethodVisitor(
        ClassParsingContext classParsingContext,
        Parent<KeepDeclaration> parent,
        String className,
        String methodName,
        String methodDescriptor) {
      super(ASM_VERSION);
      this.parent = parent;
      this.className = className;
      this.methodName = methodName;
      this.methodDescriptor = methodDescriptor;
      this.parsingContext =
          new MethodParsingContext(classParsingContext, methodName, methodDescriptor);
    }

    private static KeepMemberItemPattern createMethodItemContext(
        String className,
        String methodName,
        String methodDescriptor,
        UserBindingsHelper bindingsHelper) {
      String returnTypeDescriptor = Type.getReturnType(methodDescriptor).getDescriptor();
      Type[] argumentTypes = Type.getArgumentTypes(methodDescriptor);
      KeepMethodParametersPattern.Builder builder = KeepMethodParametersPattern.builder();
      for (Type type : argumentTypes) {
        builder.addParameterTypePattern(KeepTypePattern.fromDescriptor(type.getDescriptor()));
      }
      KeepMethodReturnTypePattern returnTypePattern =
          "V".equals(returnTypeDescriptor)
              ? KeepMethodReturnTypePattern.voidType()
              : KeepMethodReturnTypePattern.fromType(
                  KeepTypePattern.fromDescriptor(returnTypeDescriptor));

      return KeepMemberItemPattern.builder()
          .setClassReference(classReferenceFromName(className, bindingsHelper))
          .setMemberPattern(
              KeepMethodPattern.builder()
                  .setNamePattern(KeepMethodNamePattern.exact(methodName))
                  .setReturnTypePattern(returnTypePattern)
                  .setParametersPattern(builder.build())
                  .build())
          .build();
    }

    private AnnotationParsingContext annotationParsingContext(String descriptor) {
      return parsingContext.annotation(descriptor);
    }

    @Override
    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
      return createAnnotationVisitor(
          descriptor,
          visible,
          true,
          parent::accept,
          annotationParsingContext(descriptor),
          className,
          methodName,
          methodDescriptor,
          this::setContext);
    }

    public static AnnotationVisitor createAnnotationVisitor(
        String descriptor,
        boolean visible,
        boolean readEmbedded,
        Consumer<KeepDeclaration> parent,
        AnnotationParsingContext parsingContext,
        String className,
        String methodName,
        String methodDescriptor,
        Consumer<KeepEdgeMetaInfo.Builder> setContext) {
      // Skip any visible annotations as @KeepEdge is not runtime visible.
      if (visible) {
        return null;
      }
      if (!readEmbedded) {
        // Only the embedded annotations can be on methods.
        return null;
      }
      if (Edge.isDescriptor(descriptor)) {
        return new KeepEdgeVisitor(parsingContext, parent::accept, setContext);
      }
      if (AnnotationConstants.UsesReflection.isDescriptor(descriptor)) {
        return new UsesReflectionVisitor(
            parsingContext,
            parent::accept,
            setContext,
            bindingsHelper ->
                createMethodItemContext(className, methodName, methodDescriptor, bindingsHelper));
      }
      if (AnnotationConstants.UsesReflectionToConstruct.isDescriptor(descriptor)) {
        return new UsesReflectionToConstructVisitor(
            parsingContext,
            parent::accept,
            setContext,
            bindingsHelper ->
                createMethodItemContext(className, methodName, methodDescriptor, bindingsHelper));
      }
      if (AnnotationConstants.UsesReflectionToConstruct.isKotlinRepeatableContainerDescriptor(
          descriptor)) {
        return new UsesReflectionForInstantiationContainerVisitor(
            parsingContext,
            parent::accept,
            setContext,
            bindingsHelper ->
                createMethodItemContext(className, methodName, methodDescriptor, bindingsHelper));
      }
      if (AnnotationConstants.UsesReflectionToAccessMethod.isDescriptor(descriptor)) {
        return new UsesReflectionToAccessMethodVisitor(
            parsingContext,
            parent::accept,
            setContext,
            bindingsHelper ->
                createMethodItemContext(className, methodName, methodDescriptor, bindingsHelper));
      }
      if (AnnotationConstants.UsesReflectionToAccessMethod.isKotlinRepeatableContainerDescriptor(
          descriptor)) {
        return new UsesReflectionToAccessMethodContainerVisitor(
            parsingContext,
            parent::accept,
            setContext,
            bindingsHelper ->
                createMethodItemContext(className, methodName, methodDescriptor, bindingsHelper));
      }
      if (AnnotationConstants.UsesReflectionToAccessField.isDescriptor(descriptor)) {
        return new UsesReflectionToAccessFieldVisitor(
            parsingContext,
            parent::accept,
            setContext,
            bindingsHelper ->
                createMethodItemContext(className, methodName, methodDescriptor, bindingsHelper));
      }
      if (AnnotationConstants.UsesReflectionToAccessField.isKotlinRepeatableContainerDescriptor(
          descriptor)) {
        return new UsesReflectionToAccessFieldContainerVisitor(
            parsingContext,
            parent::accept,
            setContext,
            bindingsHelper ->
                createMethodItemContext(className, methodName, methodDescriptor, bindingsHelper));
      }
      if (AnnotationConstants.UnconditionallyKeep.isDescriptor(descriptor)) {
        return new UnconditionallyKeepMemberVisitor(
            parsingContext,
            parent::accept,
            setContext,
            bindingsHelper ->
                createMethodItemContext(className, methodName, methodDescriptor, bindingsHelper));
      }
      if (AnnotationConstants.ForApi.isDescriptor(descriptor)) {
        return new ForApiMemberVisitor(
            parsingContext,
            parent::accept,
            setContext,
            bindingsHelper ->
                createMethodItemContext(className, methodName, methodDescriptor, bindingsHelper));
      }
      if (AnnotationConstants.UsedByReflection.isDescriptor(descriptor)
          || AnnotationConstants.UsedByNative.isDescriptor(descriptor)) {
        return new UsedByReflectionMemberVisitor(
            parsingContext,
            parent::accept,
            setContext,
            bindingsHelper ->
                createMethodItemContext(className, methodName, methodDescriptor, bindingsHelper));
      }
      if (AnnotationConstants.CheckRemoved.isDescriptor(descriptor)) {
        return new CheckRemovedMemberVisitor(
            parsingContext,
            parent::accept,
            setContext,
            bindingsHelper ->
                createMethodItemContext(className, methodName, methodDescriptor, bindingsHelper),
            KeepCheckKind.REMOVED);
      }
      if (AnnotationConstants.CheckOptimizedOut.isDescriptor(descriptor)) {
        return new CheckRemovedMemberVisitor(
            parsingContext,
            parent::accept,
            setContext,
            bindingsHelper ->
                createMethodItemContext(className, methodName, methodDescriptor, bindingsHelper),
            KeepCheckKind.OPTIMIZED_OUT);
      }
      return null;
    }

    private void setContext(KeepEdgeMetaInfo.Builder builder) {
      builder.setContextFromMethodDescriptor(
          KeepEdgeReaderUtils.getDescriptorFromJavaType(className), methodName, methodDescriptor);
    }
  }

  private static class KeepEdgeFieldVisitor extends FieldVisitor {
    private final Parent<KeepEdge> parent;
    private final String className;
    private final String fieldName;
    private final String fieldTypeDescriptor;
    private final FieldParsingContext parsingContext;

    KeepEdgeFieldVisitor(
        ClassParsingContext classParsingContext,
        Parent<KeepEdge> parent,
        String className,
        String fieldName,
        String fieldTypeDescriptor) {
      super(ASM_VERSION);
      this.parent = parent;
      this.className = className;
      this.fieldName = fieldName;
      this.fieldTypeDescriptor = fieldTypeDescriptor;
      this.parsingContext =
          new FieldParsingContext(classParsingContext, fieldName, fieldTypeDescriptor);
    }

    private AnnotationParsingContext annotationParsingContext(String descriptor) {
      return parsingContext.annotation(descriptor);
    }

    private static KeepMemberItemPattern createMemberItemContext(
        String className,
        String fieldName,
        String fieldTypeDescriptor,
        UserBindingsHelper bindingsHelper) {
      KeepFieldTypePattern typePattern =
          KeepFieldTypePattern.fromType(KeepTypePattern.fromDescriptor(fieldTypeDescriptor));
      return KeepMemberItemPattern.builder()
          .setClassReference(classReferenceFromName(className, bindingsHelper))
          .setMemberPattern(
              KeepFieldPattern.builder()
                  .setNamePattern(KeepFieldNamePattern.exact(fieldName))
                  .setTypePattern(typePattern)
                  .build())
          .build();
    }

    private void setContext(KeepEdgeMetaInfo.Builder builder) {
      builder.setContextFromFieldDescriptor(
          KeepEdgeReaderUtils.getDescriptorFromJavaType(className), fieldName, fieldTypeDescriptor);
    }

    @Override
    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
      return createAnnotationVisitor(
          descriptor,
          visible,
          true,
          parent::accept,
          annotationParsingContext(descriptor),
          className,
          fieldName,
          fieldTypeDescriptor,
          this::setContext);
    }

    public static AnnotationVisitor createAnnotationVisitor(
        String descriptor,
        boolean visible,
        boolean readEmbedded,
        Consumer<KeepEdge> parent,
        AnnotationParsingContext parsingContext,
        String className,
        String fieldName,
        String fieldTypeDescriptor,
        Consumer<KeepEdgeMetaInfo.Builder> setContext) {
      // Skip any visible annotations as @KeepEdge is not runtime visible.
      if (visible) {
        return null;
      }
      if (!readEmbedded) {
        // Only the embedded annotations can be on fields.
        return null;
      }
      if (Edge.isDescriptor(descriptor)) {
        return new KeepEdgeVisitor(parsingContext, parent::accept, setContext);
      }
      if (AnnotationConstants.UsesReflection.isDescriptor(descriptor)) {
        return new UsesReflectionVisitor(
            parsingContext,
            parent::accept,
            setContext,
            bindingsHelper ->
                createMemberItemContext(className, fieldName, fieldTypeDescriptor, bindingsHelper));
      }
      if (AnnotationConstants.UnconditionallyKeep.isDescriptor(descriptor)) {
        return new UnconditionallyKeepMemberVisitor(
            parsingContext,
            parent::accept,
            setContext,
            bindingsHelper ->
                createMemberItemContext(className, fieldName, fieldTypeDescriptor, bindingsHelper));
      }
      if (ForApi.isDescriptor(descriptor)) {
        return new ForApiMemberVisitor(
            parsingContext,
            parent::accept,
            setContext,
            bindingsHelper ->
                createMemberItemContext(className, fieldName, fieldTypeDescriptor, bindingsHelper));
      }
      if (UsedByReflection.isDescriptor(descriptor)
          || AnnotationConstants.UsedByNative.isDescriptor(descriptor)) {
        return new UsedByReflectionMemberVisitor(
            parsingContext,
            parent::accept,
            setContext,
            bindingsHelper ->
                createMemberItemContext(className, fieldName, fieldTypeDescriptor, bindingsHelper));
      }
      return null;
    }
  }

  // Interface for providing AST result(s) for a sub-tree back up to its parent.
  public interface Parent<T> {
    void accept(T result);
  }

  public static class UserBindingsHelper {
    private final KeepBindings.Builder builder = KeepBindings.builder();
    private final Map<String, KeepBindingSymbol> userNames = new HashMap<>();

    public boolean isUserBinding(KeepBindingSymbol symbol) {
      return userNames.get(symbol.toString()) == symbol;
    }

    public KeepBindingSymbol resolveUserBinding(String name) {
      return userNames.computeIfAbsent(name, builder::create);
    }

    public void defineUserBinding(String name, KeepItemPattern item) {
      builder.addBinding(resolveUserBinding(name), item);
    }

    public void replaceWithUserBinding(
        String name, KeepBindingReference replacedBinding, ParsingContext parsingContext) {
      if (isUserBinding(replacedBinding.getName())) {
        // The language currently disallows aliasing bindings, thus a binding cannot directly be
        // defined by a reference to another binding.
        throw parsingContext.error(
            "Invalid binding reference to '"
                + replacedBinding
                + "' in binding definition of '"
                + name
                + "'");
      }
      KeepItemPattern item = builder.deleteBinding(replacedBinding.getName());
      defineUserBinding(name, item);
    }

    public KeepClassBindingReference defineFreshClassBinding(KeepClassItemPattern itemPattern) {
      KeepBindingSymbol symbol = defineFreshBinding("CLASS", itemPattern);
      return KeepClassBindingReference.forClass(symbol);
    }

    public KeepMemberBindingReference defineFreshMemberBinding(KeepMemberItemPattern itemPattern) {
      KeepBindingSymbol symbol = defineFreshBinding("MEMBER", itemPattern);
      return KeepMemberBindingReference.forMember(symbol);
    }

    public KeepBindingReference defineFreshItemBinding(String name, KeepItemPattern itemPattern) {
      KeepBindingSymbol symbol = defineFreshBinding(name, itemPattern);
      return KeepBindingReference.forItem(symbol, itemPattern);
    }

    private KeepBindingSymbol defineFreshBinding(String name, KeepItemPattern item) {
      KeepBindingSymbol symbol = builder.generateFreshSymbol(name);
      builder.addBinding(symbol, item);
      return symbol;
    }

    public KeepItemPattern getItem(KeepBindingReference bindingReference) {
      return bindingReference.isClassType()
          ? getClassItem(bindingReference.asClassBindingReference())
          : getMemberItem(bindingReference.asMemberBindingReference());
    }

    public KeepClassItemPattern getClassItem(KeepClassBindingReference reference) {
      KeepItemPattern item = builder.getItemForBinding(reference.getName());
      assert item != null;
      return item.asClassItemPattern();
    }

    public KeepMemberItemPattern getMemberItem(KeepMemberBindingReference reference) {
      KeepItemPattern item = builder.getItemForBinding(reference.getName());
      assert item != null;
      return item.asMemberItemPattern();
    }

    public KeepClassItemPattern getClassItemForReference(KeepClassBindingReference itemReference) {
      return getItem(itemReference).asClassItemPattern();
    }

    public KeepBindings build() {
      return builder.build();
    }
  }

  private static class KeepEdgeVisitor extends AnnotationVisitorBase {

    private final ParsingContext parsingContext;
    private final Parent<KeepEdge> parent;
    private final KeepEdge.Builder builder = KeepEdge.builder();
    private final KeepEdgeMetaInfo.Builder metaInfoBuilder = KeepEdgeMetaInfo.builder();
    private final UserBindingsHelper bindingsHelper = new UserBindingsHelper();

    KeepEdgeVisitor(
        AnnotationParsingContext parsingContext,
        Parent<KeepEdge> parent,
        Consumer<KeepEdgeMetaInfo.Builder> addContext) {
      super(parsingContext);
      this.parsingContext = parsingContext;
      this.parent = parent;
      addContext.accept(metaInfoBuilder);
    }

    @Override
    public void visit(String name, Object value) {
      if (name.equals(Edge.description) && value instanceof String) {
        metaInfoBuilder.setDescription((String) value);
        return;
      }
      super.visit(name, value);
    }

    @Override
    public AnnotationVisitor visitArray(String name) {
      PropertyParsingContext propertyParsingContext = parsingContext.property(name);
      if (name.equals(Edge.bindings)) {
        return new KeepBindingsVisitor(propertyParsingContext, bindingsHelper);
      }
      if (name.equals(Edge.preconditions)) {
        return new KeepPreconditionsVisitor(
            propertyParsingContext, builder::setPreconditions, bindingsHelper);
      }
      if (name.equals(Edge.consequences)) {
        return new KeepConsequencesVisitor(
            propertyParsingContext, builder::setConsequences, bindingsHelper);
      }
      return super.visitArray(name);
    }

    @Override
    public void visitEnd() {
      parent.accept(
          builder.setMetaInfo(metaInfoBuilder.build()).setBindings(bindingsHelper.build()).build());
    }
  }

  /**
   * Parsing of @KeepForApi on a class context.
   *
   * <p>When used on a class context the annotation allows the member related content of a normal
   * item. This parser extends the base item visitor and throws an error if any class specific
   * properties are encountered.
   */
  private static class ForApiClassVisitor extends KeepItemVisitorBase {

    private final ParsingContext parsingContext;
    private final String className;
    private final Parent<KeepEdge> parent;
    private final KeepEdge.Builder builder = KeepEdge.builder();
    private final KeepConsequences.Builder consequences = KeepConsequences.builder();
    private final KeepEdgeMetaInfo.Builder metaInfoBuilder = KeepEdgeMetaInfo.builder();
    private final UserBindingsHelper bindingsHelper = new UserBindingsHelper();

    ForApiClassVisitor(
        AnnotationParsingContext parsingContext,
        Parent<KeepEdge> parent,
        Consumer<KeepEdgeMetaInfo.Builder> addContext,
        String className) {
      super(parsingContext);
      this.parsingContext = parsingContext;
      this.className = className;
      this.parent = parent;
      addContext.accept(metaInfoBuilder);
      // The class context/holder is the annotated class.
      visit(Item.className, className);
      // The default kind is to target the class and its members.
      visitEnum(null, Kind.getDescriptor(), Kind.CLASS_AND_MEMBERS);
    }

    @Override
    public UserBindingsHelper getBindingsHelper() {
      return bindingsHelper;
    }

    @Override
    public void visit(String name, Object value) {
      if (name.equals(Edge.description) && value instanceof String) {
        metaInfoBuilder.setDescription((String) value);
        return;
      }
      super.visit(name, value);
    }

    @Override
    public AnnotationVisitor visitArray(String name) {
      if (name.equals(ForApi.additionalTargets)) {
        return new KeepConsequencesVisitor(
            parsingContext.property(name),
            additionalConsequences -> {
              additionalConsequences.forEachTarget(consequences::addTarget);
            },
            bindingsHelper);
      }
      return super.visitArray(name);
    }

    @Override
    public boolean useBindingForClassAndMembers() {
      // KeepForApi targets should be disjunctive CLASS_AND_MEMBERS
      return false;
    }

    @Override
    public void visitEnd() {
      if (!getKind().isOnlyClass() && isDefaultMemberDeclaration()) {
        // If no member declarations have been made, set public & protected as the default.
        AnnotationVisitor v = visitArray(Item.memberAccess);
        v.visitEnum(null, MemberAccess.getDescriptor(), MemberAccess.PUBLIC);
        v.visitEnum(null, MemberAccess.getDescriptor(), MemberAccess.PROTECTED);
      }
      super.visitEnd();
      // Currently there is no way of changing constraints on KeepForApi.
      // Default constraints should retain the expected meta-data, such as signatures, annotations
      // exception-throws etc.
      KeepConstraints apiConstraints = KeepConstraints.all();

      // TODO(b/399021897): Remove temporary system property when @KeepForApi supports not retaining
      //  runtime invisible annotations.
      String unkeepInvisibleAnnotationsInKeepForApi =
          System.getProperty(
              "com.android.tools.r8.keepanno.unkeepInvisibleAnnotationsInKeepForApi");
      if (unkeepInvisibleAnnotationsInKeepForApi != null) {
        KeepConstraints.Builder constraintsBuilder = KeepConstraints.builder();
        for (KeepConstraint constraint : apiConstraints.getConstraints()) {
          if (constraint == KeepConstraint.annotationsAll()) {
            constraintsBuilder.add(KeepConstraint.annotationsAllWithRuntimeRetention());
          } else {
            assert constraint != KeepConstraint.annotationsAllWithClassRetention();
            constraintsBuilder.add(constraint);
          }
        }
        apiConstraints = constraintsBuilder.build();
      }

      Collection<KeepBindingReference> items = getItems();
      for (KeepBindingReference bindingReference : items) {
        KeepItemPattern item = bindingsHelper.getItem(bindingReference);
        KeepClassItemPattern classItemPattern = item.asClassItemPattern();
        if (classItemPattern == null) {
          assert item.isMemberItemPattern();
          KeepClassBindingReference classReference = item.asMemberItemPattern().getClassReference();
          classItemPattern = bindingsHelper.getClassItemForReference(classReference);
        }
        String descriptor = KeepEdgeReaderUtils.getDescriptorFromClassTypeName(className);
        String itemDescriptor = classItemPattern.getClassNamePattern().getExactDescriptor();
        if (!descriptor.equals(itemDescriptor)) {
          throw parsingContext.error("must reference its class context " + className);
        }
        if (classItemPattern.isMemberItemPattern() && items.size() == 1) {
          throw parsingContext.error("kind must include its class");
        }
        if (!classItemPattern.getInstanceOfPattern().isAny()) {
          throw parsingContext.error("cannot define an 'extends' pattern.");
        }
        consequences.addTarget(
            KeepTarget.builder()
                .setItemReference(bindingReference)
                .setConstraints(apiConstraints)
                .build());
      }
      parent.accept(
          builder
              .setMetaInfo(metaInfoBuilder.build())
              .setBindings(bindingsHelper.build())
              .setConsequences(consequences.build())
              .build());
    }
  }

  /**
   * Parsing of @KeepForApi on a member context.
   *
   * <p>When used on a member context the annotation does not allow member related patterns.
   */
  private static class ForApiMemberVisitor extends AnnotationVisitorBase {

    private final ParsingContext parsingContext;
    private final Parent<KeepEdge> parent;
    private final KeepEdge.Builder builder = KeepEdge.builder();
    private final KeepEdgeMetaInfo.Builder metaInfoBuilder = KeepEdgeMetaInfo.builder();
    private final UserBindingsHelper bindingsHelper = new UserBindingsHelper();
    private final KeepConsequences.Builder consequences = KeepConsequences.builder();

    ForApiMemberVisitor(
        AnnotationParsingContext parsingContext,
        Parent<KeepEdge> parent,
        Consumer<KeepEdgeMetaInfo.Builder> addContext,
        Function<UserBindingsHelper, KeepMemberItemPattern> contextBuilder) {
      super(parsingContext);
      this.parsingContext = parsingContext;
      this.parent = parent;
      addContext.accept(metaInfoBuilder);
      KeepMemberItemPattern context = contextBuilder.apply(bindingsHelper);
      KeepClassBindingReference classReference = context.getClassReference();
      consequences.addTarget(
          KeepTarget.builder()
              .setItemReference(bindingsHelper.defineFreshMemberBinding(context))
              .build());
      consequences.addTarget(KeepTarget.builder().setItemReference(classReference).build());
    }

    @Override
    public void visit(String name, Object value) {
      if (name.equals(Edge.description) && value instanceof String) {
        metaInfoBuilder.setDescription((String) value);
        return;
      }
      super.visit(name, value);
    }

    @Override
    public AnnotationVisitor visitArray(String name) {
      if (name.equals(ForApi.additionalTargets)) {
        return new KeepConsequencesVisitor(
            parsingContext.property(name),
            additionalConsequences -> {
              additionalConsequences.forEachTarget(consequences::addTarget);
            },
            bindingsHelper);
      }
      return super.visitArray(name);
    }

    @Override
    public void visitEnd() {
      parent.accept(
          builder
              .setMetaInfo(metaInfoBuilder.build())
              .setBindings(bindingsHelper.build())
              .setConsequences(consequences.build())
              .build());
    }
  }

  public static class ConstraintDeclarationParser extends DeclarationParser<KeepConstraints> {
    private final ConstraintPropertiesParser constraintsParser;
    private final ArrayPropertyParser<
            KeepAnnotationPattern, AnnotationPatternParser.AnnotationProperty>
        annotationsParser;

    public ConstraintDeclarationParser(ParsingContext parsingContext) {
      constraintsParser = new ConstraintPropertiesParser(parsingContext);
      constraintsParser.setProperty(Target.constraints, ConstraintsProperty.CONSTRAINTS);
      constraintsParser.setProperty(Target.constraintAdditions, ConstraintsProperty.ADDITIONS);

      annotationsParser = new ArrayPropertyParser<>(parsingContext, AnnotationPatternParser::new);
      annotationsParser.setProperty(
          Target.constrainAnnotations, AnnotationPatternParser.AnnotationProperty.PATTERN);
      annotationsParser.setValueCheck(this::verifyAnnotationList);
    }

    private void verifyAnnotationList(
        List<KeepAnnotationPattern> annotationList, ParsingContext parsingContext) {
      if (annotationList.isEmpty()) {
        throw parsingContext.error("Expected non-empty array of annotation patterns");
      }
    }

    @Override
    List<Parser<?>> parsers() {
      return ImmutableList.of(constraintsParser, annotationsParser);
    }

    public KeepConstraints getValueOrDefault(KeepConstraints defaultValue) {
      return isDeclared() ? getValue() : defaultValue;
    }

    public KeepConstraints getValue() {
      if (isDefault()) {
        return null;
      }
      // If only the constraints are set then those are the constraints as is.
      if (annotationsParser.isDefault()) {
        assert constraintsParser.isDeclared();
        return constraintsParser.getValue();
      }
      KeepConstraints.Builder builder;
      if (constraintsParser.isDeclared()) {
        // If constraints are set use it as the initial set.
        builder = KeepConstraints.builder().copyFrom(constraintsParser.getValue());
        assert builder.verifyNoAnnotations();
      } else {
        // If only the annotations are set, add them as an extension of the defaults.
        builder = KeepConstraints.builder().copyFrom(KeepConstraints.defaultConstraints());
      }
      annotationsParser
          .getValue()
          .forEach(pattern -> builder.add(KeepConstraint.annotation(pattern)));
      return builder.build();
    }
  }

  /**
   * Parsing of @UsedByReflection or @UsedByNative on a class context.
   *
   * <p>When used on a class context the annotation allows the member related content of a normal
   * item. This parser extends the base item visitor and throws an error if any class specific
   * properties are encountered.
   */
  private static class UsedByReflectionClassVisitor extends KeepItemVisitorBase {

    private final ParsingContext parsingContext;
    private final String className;
    private final Parent<KeepEdge> parent;
    private final KeepEdge.Builder builder = KeepEdge.builder();
    private final KeepConsequences.Builder consequences = KeepConsequences.builder();
    private final KeepEdgeMetaInfo.Builder metaInfoBuilder = KeepEdgeMetaInfo.builder();
    private final UserBindingsHelper bindingsHelper = new UserBindingsHelper();
    private final ConstraintDeclarationParser constraintsParser;

    UsedByReflectionClassVisitor(
        AnnotationParsingContext parsingContext,
        Parent<KeepEdge> parent,
        Consumer<KeepEdgeMetaInfo.Builder> addContext,
        String className) {
      super(parsingContext);
      this.parsingContext = parsingContext;
      this.className = className;
      this.parent = parent;
      addContext.accept(metaInfoBuilder);
      constraintsParser = new ConstraintDeclarationParser(parsingContext);
      // The class context/holder is the annotated class.
      visit(Item.className, className);
    }

    @Override
    public UserBindingsHelper getBindingsHelper() {
      return bindingsHelper;
    }

    @Override
    public void visit(String name, Object value) {
      if (name.equals(Edge.description) && value instanceof String) {
        metaInfoBuilder.setDescription((String) value);
        return;
      }
      super.visit(name, value);
    }

    @Override
    public AnnotationVisitor visitArray(String name) {
      PropertyParsingContext propertyParsingContext = parsingContext.property(name);
      if (name.equals(Edge.preconditions)) {
        return new KeepPreconditionsVisitor(
            propertyParsingContext, builder::setPreconditions, bindingsHelper);
      }
      if (name.equals(UsedByReflection.additionalTargets)) {
        return new KeepConsequencesVisitor(
            propertyParsingContext,
            additionalConsequences -> {
              additionalConsequences.forEachTarget(consequences::addTarget);
            },
            bindingsHelper);
      }
      AnnotationVisitor visitor = constraintsParser.tryParseArray(name, unused -> {});
      if (visitor != null) {
        return visitor;
      }
      return super.visitArray(name);
    }

    @Override
    public boolean useBindingForClassAndMembers() {
      // When annotating a class with UsedByReflection and including member patterns, don't
      // require the member patterns to match to retain the class.
      return false;
    }

    @Override
    public void visitEnd() {
      if (getKind() == null && !isDefaultMemberDeclaration()) {
        // If no explict kind is set and member declarations have been made, keep the class too.
        visitEnum(null, Kind.getDescriptor(), Kind.CLASS_AND_MEMBERS);
      }
      super.visitEnd();
      for (KeepBindingReference bindingReference : getItems()) {
        verifyItemStructure(bindingReference);
        consequences.addTarget(
            KeepTarget.builder()
                .setItemReference(bindingReference)
                .setConstraints(
                    constraintsParser.getValueOrDefault(KeepConstraints.defaultConstraints()))
                .build());
      }
      parent.accept(
          builder
              .setMetaInfo(metaInfoBuilder.build())
              .setBindings(bindingsHelper.build())
              .setConsequences(consequences.build())
              .build());
    }

    private void verifyItemStructure(KeepBindingReference bindingReference) {
      KeepItemPattern itemPattern = bindingsHelper.getItem(bindingReference);
      KeepClassItemPattern holderPattern =
          itemPattern.isClassItemPattern()
              ? itemPattern.asClassItemPattern()
              : bindingsHelper.getClassItemForReference(
                  itemPattern.asMemberItemPattern().getClassReference());
      String descriptor = KeepEdgeReaderUtils.getDescriptorFromClassTypeName(className);
      String itemDescriptor = holderPattern.getClassNamePattern().getExactDescriptor();
      if (!descriptor.equals(itemDescriptor)) {
        throw parsingContext.error("must reference its class context " + className);
      }
      if (!holderPattern.getInstanceOfPattern().isAny()) {
        throw parsingContext.error("cannot define an 'extends' pattern.");
      }
    }
  }

  /**
   * Parsing of @UsedByReflection or @UsedByNative on a member context.
   *
   * <p>When used on a member context the annotation does not allow member related patterns.
   */
  private static class UsedByReflectionMemberVisitor extends AnnotationVisitorBase {

    private final ParsingContext parsingContext;
    private final Parent<KeepEdge> parent;
    private final KeepMemberItemPattern context;
    private final KeepEdge.Builder builder = KeepEdge.builder();
    private final KeepEdgeMetaInfo.Builder metaInfoBuilder = KeepEdgeMetaInfo.builder();
    private final UserBindingsHelper bindingsHelper = new UserBindingsHelper();
    private final KeepConsequences.Builder consequences = KeepConsequences.builder();
    private ItemKind kind = KeepEdgeReader.ItemKind.ONLY_MEMBERS;
    private final ConstraintDeclarationParser constraintsParser;

    UsedByReflectionMemberVisitor(
        AnnotationParsingContext parsingContext,
        Parent<KeepEdge> parent,
        Consumer<KeepEdgeMetaInfo.Builder> addContext,
        Function<UserBindingsHelper, KeepMemberItemPattern> contextBuilder) {
      super(parsingContext);
      this.parsingContext = parsingContext;
      this.parent = parent;
      this.context = contextBuilder.apply(bindingsHelper);
      addContext.accept(metaInfoBuilder);
      constraintsParser = new ConstraintDeclarationParser(parsingContext);
    }

    @Override
    public void visit(String name, Object value) {
      if (name.equals(Edge.description) && value instanceof String) {
        metaInfoBuilder.setDescription((String) value);
        return;
      }
      super.visit(name, value);
    }

    @Override
    public void visitEnum(String name, String descriptor, String value) {
      if (!Kind.isDescriptor(descriptor)) {
        super.visitEnum(name, descriptor, value);
      }
      KeepEdgeReader.ItemKind kind = KeepEdgeReader.ItemKind.fromString(value);
      if (kind != null) {
        this.kind = kind;
      } else {
        super.visitEnum(name, descriptor, value);
      }
    }

    @Override
    public AnnotationVisitor visitArray(String name) {
      PropertyParsingContext propertyParsingContext = parsingContext.property(name);
      if (name.equals(Edge.preconditions)) {
        return new KeepPreconditionsVisitor(
            propertyParsingContext, builder::setPreconditions, bindingsHelper);
      }
      if (name.equals(UsedByReflection.additionalTargets)) {
        return new KeepConsequencesVisitor(
            propertyParsingContext,
            additionalConsequences -> {
              additionalConsequences.forEachTarget(consequences::addTarget);
            },
            bindingsHelper);
      }
      AnnotationVisitor visitor = constraintsParser.tryParseArray(name, unused -> {});
      if (visitor != null) {
        return visitor;
      }
      return super.visitArray(name);
    }

    @Override
    public void visitEnd() {
      if (kind.isOnlyClass()) {
        throw parsingContext.error("kind must include its member");
      }
      KeepMemberItemPattern memberContext = context.asMemberItemPattern();
      KeepMemberBindingReference contextBinding =
          bindingsHelper.defineFreshMemberBinding(memberContext);
      if (kind.includesClass()) {
        consequences.addTarget(
            KeepTarget.builder().setItemReference(memberContext.getClassReference()).build());
      }
      validateConsistentKind(memberContext.getMemberPattern());
      consequences.addTarget(
          KeepTarget.builder()
              .setConstraints(
                  constraintsParser.getValueOrDefault(KeepConstraints.defaultConstraints()))
              .setItemReference(contextBinding)
              .build());
      parent.accept(
          builder
              .setMetaInfo(metaInfoBuilder.build())
              .setBindings(bindingsHelper.build())
              .setConsequences(consequences.build())
              .build());
    }

    private void validateConsistentKind(KeepMemberPattern memberPattern) {
      if (memberPattern.isGeneralMember()) {
        throw parsingContext.error("Unexpected general pattern for context.");
      }
      if (memberPattern.isMethod() && !kind.includesMethod()) {
        throw parsingContext.error("Kind " + kind + " cannot be use when annotating a method");
      }
      if (memberPattern.isField() && !kind.includesField()) {
        throw parsingContext.error("Kind " + kind + " cannot be use when annotating a field");
      }
    }
  }

  private static class UsesReflectionVisitor extends AnnotationVisitorBase {

    private final ParsingContext parsingContext;
    private final Parent<KeepEdge> parent;
    private final KeepEdge.Builder builder = KeepEdge.builder();
    private final KeepPreconditions.Builder preconditions = KeepPreconditions.builder();
    private final KeepEdgeMetaInfo.Builder metaInfoBuilder = KeepEdgeMetaInfo.builder();
    private final UserBindingsHelper bindingsHelper = new UserBindingsHelper();

    UsesReflectionVisitor(
        AnnotationParsingContext parsingContext,
        Parent<KeepEdge> parent,
        Consumer<KeepEdgeMetaInfo.Builder> addContext,
        Function<UserBindingsHelper, KeepItemPattern> contextBuilder) {
      super(parsingContext);
      this.parsingContext = parsingContext;
      this.parent = parent;
      KeepItemPattern context = contextBuilder.apply(bindingsHelper);
      KeepBindingReference contextBinding =
          bindingsHelper.defineFreshItemBinding("CONTEXT", context);
      preconditions.addCondition(KeepCondition.builder().setItemReference(contextBinding).build());
      addContext.accept(metaInfoBuilder);
    }

    @Override
    public void visit(String name, Object value) {
      if (name.equals(Edge.description) && value instanceof String) {
        metaInfoBuilder.setDescription((String) value);
        return;
      }
      super.visit(name, value);
    }

    @Override
    public AnnotationVisitor visitArray(String name) {
      PropertyParsingContext propertyParsingContext = parsingContext.property(name);
      if (name.equals(AnnotationConstants.UsesReflection.value)) {
        return new KeepConsequencesVisitor(
            propertyParsingContext, builder::setConsequences, bindingsHelper);
      }
      if (name.equals(AnnotationConstants.UsesReflection.additionalPreconditions)) {
        return new KeepPreconditionsVisitor(
            propertyParsingContext,
            additionalPreconditions -> {
              additionalPreconditions.forEach(preconditions::addCondition);
            },
            bindingsHelper);
      }
      return super.visitArray(name);
    }

    @Override
    public void visitEnd() {
      parent.accept(
          builder
              .setMetaInfo(metaInfoBuilder.build())
              .setBindings(bindingsHelper.build())
              .setPreconditions(preconditions.build())
              .build());
    }
  }

  private static class ParametersClassVisitor extends AnnotationVisitorBase {
    private final ParsingContext parsingContext;
    private final Consumer<KeepMethodParametersPattern> consumer;
    private final KeepMethodParametersPattern.Builder builder =
        KeepMethodParametersPattern.builder();

    public ParametersClassVisitor(
        PropertyParsingContext parsingContext, Consumer<KeepMethodParametersPattern> consumer) {
      super(parsingContext);
      this.parsingContext = parsingContext;
      this.consumer = consumer;
    }

    @Override
    public void visit(String name, Object value) {
      assert name == null;
      if (value instanceof String) {
        builder.addParameterTypePattern(KeepTypePattern.fromDescriptor("L" + value + ";"));
      } else if (value instanceof Type) {
        builder.addParameterTypePattern(
            KeepTypePattern.fromDescriptor(((Type) value).getDescriptor()));
      } else {
        super.visit(name, value);
      }
    }

    @Override
    public void visitEnd() {
      consumer.accept(builder.build());
    }
  }

  private static class ParametersClassNamesVisitor extends AnnotationVisitorBase {
    private final ParsingContext parsingContext;
    private final Consumer<KeepMethodParametersPattern> consumer;
    private final KeepMethodParametersPattern.Builder builder =
        KeepMethodParametersPattern.builder();

    public ParametersClassNamesVisitor(
        PropertyParsingContext parsingContext, Consumer<KeepMethodParametersPattern> consumer) {
      super(parsingContext);
      this.parsingContext = parsingContext;
      this.consumer = consumer;
    }

    @Override
    public void visit(String name, Object value) {
      assert name == null;
      if (value instanceof String) {
        builder.addParameterTypePattern(
            KeepTypePattern.fromDescriptor(
                DescriptorUtils.javaTypeToDescriptor(kotlinTypeToJavaType((String) value))));
      } else {
        super.visit(name, value);
      }
    }

    @Override
    public void visitEnd() {
      consumer.accept(builder.build());
    }
  }

  private static class UsesReflectionForInstantiationContainerVisitor
      extends AnnotationVisitorBase {

    private final AnnotationParsingContext parsingContext;
    private final Parent<KeepEdge> parent;
    Consumer<KeepEdgeMetaInfo.Builder> addContext;
    Function<UserBindingsHelper, KeepItemPattern> contextBuilder;

    UsesReflectionForInstantiationContainerVisitor(
        AnnotationParsingContext parsingContext,
        Parent<KeepEdge> parent,
        Consumer<KeepEdgeMetaInfo.Builder> addContext,
        Function<UserBindingsHelper, KeepItemPattern> contextBuilder) {
      super(parsingContext);
      this.parsingContext = parsingContext;
      this.parent = parent;
      this.addContext = addContext;
      this.contextBuilder = contextBuilder;
    }

    @Override
    public AnnotationVisitor visitArray(String name) {
      if (name.equals("value")) {
        return new UsesReflectionForInstantiationsVisitor(
            parsingContext, parent, addContext, contextBuilder);
      }
      return super.visitArray(name);
    }

    @Override
    public void visitEnd() {
      super.visitEnd();
    }
  }

  private static class UsesReflectionForInstantiationsVisitor extends AnnotationVisitorBase {
    private final AnnotationParsingContext parsingContext;
    private final Parent<KeepEdge> parent;
    Consumer<KeepEdgeMetaInfo.Builder> addContext;
    Function<UserBindingsHelper, KeepItemPattern> contextBuilder;

    public UsesReflectionForInstantiationsVisitor(
        AnnotationParsingContext parsingContext,
        Parent<KeepEdge> parent,
        Consumer<KeepEdgeMetaInfo.Builder> addContext,
        Function<UserBindingsHelper, KeepItemPattern> contextBuilder) {
      super(parsingContext);
      this.parsingContext = parsingContext;
      this.parent = parent;
      this.addContext = addContext;
      this.contextBuilder = contextBuilder;
    }

    @Override
    public AnnotationVisitor visitAnnotation(String name, String descriptor) {
      assert name == null;
      if (AnnotationConstants.UsesReflectionToConstruct.isDescriptor(descriptor)) {
        return new UsesReflectionToConstructVisitor(
            parsingContext, parent, addContext, contextBuilder);
      }
      return super.visitAnnotation(name, descriptor);
    }
  }

  private static class UsesReflectionToXXXVisitor extends AnnotationVisitorBase {

    private static final String UsesReflectionToXXXClassConstant = "classConstant";
    private static final String UsesReflectionToXXXClassName = "className";
    private static final String UsesReflectionToXXXIncludeSubclasses = "includeSubclasses";

    protected final ParsingContext parsingContext;

    protected KeepQualifiedClassNamePattern qualifiedName;
    protected boolean includeSubclasses = false;

    UsesReflectionToXXXVisitor(AnnotationParsingContext parsingContext) {
      super(parsingContext);
      this.parsingContext = parsingContext;
      assert UsesReflectionToXXXClassConstant.equals(UsesReflectionToConstruct.classConstant);
      assert UsesReflectionToXXXClassConstant.equals(UsesReflectionToAccessMethod.classConstant);
      assert UsesReflectionToXXXClassConstant.equals(UsesReflectionToAccessField.classConstant);
      assert UsesReflectionToXXXClassName.equals(UsesReflectionToConstruct.className);
      assert UsesReflectionToXXXClassName.equals(UsesReflectionToAccessMethod.className);
      assert UsesReflectionToXXXClassName.equals(UsesReflectionToAccessField.className);
      assert UsesReflectionToXXXIncludeSubclasses.equals(
          UsesReflectionToConstruct.includeSubclasses);
      assert UsesReflectionToXXXIncludeSubclasses.equals(
          UsesReflectionToAccessMethod.includeSubclasses);
      assert UsesReflectionToXXXIncludeSubclasses.equals(
          UsesReflectionToAccessField.includeSubclasses);
    }

    protected boolean maybeVisitQualifiedName(String name, Object value) {
      if (name.equals(UsesReflectionToXXXClassConstant) && value instanceof Type) {
        qualifiedName =
            KeepQualifiedClassNamePattern.exactFromDescriptor(((Type) value).getDescriptor());
        return true;
      }
      if (name.equals(UsesReflectionToXXXClassName) && value instanceof String) {
        qualifiedName = KeepQualifiedClassNamePattern.exact((String) value);
        return true;
      }
      return false;
    }

    protected boolean maybeVisitIncludeSubclasses(String name, Object value) {
      if (name.equals(UsesReflectionToXXXIncludeSubclasses) && value instanceof Boolean) {
        includeSubclasses = (Boolean) value;
        return true;
      }
      return false;
    }

    protected KeepClassItemPattern getKeepClassItemPattern() {
      return includeSubclasses
          ? KeepClassItemPattern.builder()
              .setInstanceOfPattern(
                  KeepInstanceOfPattern.builder()
                      .classPattern(qualifiedName)
                      .setInclusive(true)
                      .build())
              .build()
          : KeepClassItemPattern.builder()
              .setClassPattern(
                  KeepClassPattern.builder().setClassNamePattern(qualifiedName).build())
              .build();
    }
  }

  private static class UsesReflectionToConstructVisitor extends UsesReflectionToXXXVisitor {

    private final Parent<KeepEdge> parent;
    private final KeepEdge.Builder builder = KeepEdge.builder();
    private final KeepPreconditions.Builder preconditions = KeepPreconditions.builder();
    private final KeepEdgeMetaInfo.Builder metaInfoBuilder = KeepEdgeMetaInfo.builder();
    private KeepMethodParametersPattern parameters = KeepMethodParametersPattern.any();
    private final UserBindingsHelper bindingsHelper = new UserBindingsHelper();

    UsesReflectionToConstructVisitor(
        AnnotationParsingContext parsingContext,
        Parent<KeepEdge> parent,
        Consumer<KeepEdgeMetaInfo.Builder> addContext,
        Function<UserBindingsHelper, KeepItemPattern> contextBuilder) {
      super(parsingContext);
      this.parent = parent;
      KeepItemPattern context = contextBuilder.apply(bindingsHelper);
      KeepBindingReference contextBinding =
          bindingsHelper.defineFreshItemBinding("CONTEXT", context);
      preconditions.addCondition(KeepCondition.builder().setItemReference(contextBinding).build());
      addContext.accept(metaInfoBuilder);
    }

    @Override
    public void visit(String name, Object value) {
      if (maybeVisitQualifiedName(name, value)) {
        return;
      }
      if (maybeVisitIncludeSubclasses(name, value)) {
        return;
      }
      super.visit(name, value);
    }

    @Override
    public AnnotationVisitor visitArray(String name) {
      PropertyParsingContext propertyParsingContext = parsingContext.property(name);
      if (name.equals(UsesReflectionToConstruct.parameterTypes)) {
        return new ParametersClassVisitor(
            propertyParsingContext, parameters -> this.parameters = parameters);
      }
      if (name.equals(UsesReflectionToConstruct.parameterTypeNames)) {
        return new ParametersClassNamesVisitor(
            propertyParsingContext, parameters -> this.parameters = parameters);
      }
      return super.visitArray(name);
    }

    @Override
    public void visitEnd() {
      KeepClassBindingReference classBinding =
          bindingsHelper.defineFreshClassBinding(getKeepClassItemPattern());
      KeepMemberBindingReference memberBinding =
          bindingsHelper.defineFreshMemberBinding(
              KeepMemberItemPattern.builder()
                  .setClassReference(classBinding)
                  .setMemberPattern(
                      KeepMethodPattern.builder()
                          .setNamePattern(KeepMethodNamePattern.instanceInitializer())
                          .setParametersPattern(parameters)
                          .setReturnTypeVoid()
                          .build())
                  .build());

      KeepConsequences.Builder consequencesBuilder =
          KeepConsequences.builder()
              .addTarget(KeepTarget.builder().setItemReference(classBinding).build())
              .addTarget(KeepTarget.builder().setItemReference(memberBinding).build());

      parent.accept(
          builder
              .setMetaInfo(metaInfoBuilder.build())
              .setBindings(bindingsHelper.build())
              .setPreconditions(preconditions.build())
              .setConsequences(consequencesBuilder.build())
              .build());
    }
  }

  private static class UsesReflectionToAccessMethodContainerVisitor extends AnnotationVisitorBase {

    private final AnnotationParsingContext parsingContext;
    private final Parent<KeepEdge> parent;
    Consumer<KeepEdgeMetaInfo.Builder> addContext;
    Function<UserBindingsHelper, KeepItemPattern> contextBuilder;

    UsesReflectionToAccessMethodContainerVisitor(
        AnnotationParsingContext parsingContext,
        Parent<KeepEdge> parent,
        Consumer<KeepEdgeMetaInfo.Builder> addContext,
        Function<UserBindingsHelper, KeepItemPattern> contextBuilder) {
      super(parsingContext);
      this.parsingContext = parsingContext;
      this.parent = parent;
      this.addContext = addContext;
      this.contextBuilder = contextBuilder;
    }

    @Override
    public AnnotationVisitor visitArray(String name) {
      if (name.equals("value")) {
        return new UsesReflectionToAccessMethodContainerElementVisitor(
            parsingContext, parent, addContext, contextBuilder);
      }
      return super.visitArray(name);
    }

    @Override
    public void visit(String name, Object value) {
      super.visit(name, value);
    }

    @Override
    public void visitEnd() {
      super.visitEnd();
    }
  }

  private static class UsesReflectionToAccessMethodContainerElementVisitor
      extends AnnotationVisitorBase {
    private final AnnotationParsingContext parsingContext;
    private final Parent<KeepEdge> parent;
    Consumer<KeepEdgeMetaInfo.Builder> addContext;
    Function<UserBindingsHelper, KeepItemPattern> contextBuilder;

    public UsesReflectionToAccessMethodContainerElementVisitor(
        AnnotationParsingContext parsingContext,
        Parent<KeepEdge> parent,
        Consumer<KeepEdgeMetaInfo.Builder> addContext,
        Function<UserBindingsHelper, KeepItemPattern> contextBuilder) {
      super(parsingContext);
      this.parsingContext = parsingContext;
      this.parent = parent;
      this.addContext = addContext;
      this.contextBuilder = contextBuilder;
    }

    @Override
    public AnnotationVisitor visitAnnotation(String name, String descriptor) {
      assert name == null;
      if (AnnotationConstants.UsesReflectionToAccessMethod.isDescriptor(descriptor)) {
        return new UsesReflectionToAccessMethodVisitor(
            parsingContext, parent, addContext, contextBuilder);
      }
      return super.visitAnnotation(name, descriptor);
    }

    @Override
    public void visit(String name, Object value) {
      super.visit(name, value);
    }
  }

  private static class UsesReflectionToAccessMethodVisitor extends UsesReflectionToXXXVisitor {

    private final Parent<KeepEdge> parent;
    private final KeepEdge.Builder builder = KeepEdge.builder();
    private final KeepPreconditions.Builder preconditions = KeepPreconditions.builder();
    private final KeepEdgeMetaInfo.Builder metaInfoBuilder = KeepEdgeMetaInfo.builder();
    private KeepMethodParametersPattern parameters = KeepMethodParametersPattern.any();
    private final UserBindingsHelper bindingsHelper = new UserBindingsHelper();

    private KeepQualifiedClassNamePattern qualifiedName;
    private boolean includeSubclasses = false;
    private KeepMethodNamePattern methodName;
    private KeepMethodNamePattern methodNameKotlinDefault;
    private KeepMethodReturnTypePattern returnType = KeepMethodReturnTypePattern.any();

    UsesReflectionToAccessMethodVisitor(
        AnnotationParsingContext parsingContext,
        Parent<KeepEdge> parent,
        Consumer<KeepEdgeMetaInfo.Builder> addContext,
        Function<UserBindingsHelper, KeepItemPattern> contextBuilder) {
      super(parsingContext);
      this.parent = parent;
      KeepItemPattern context = contextBuilder.apply(bindingsHelper);
      KeepBindingReference contextBinding =
          bindingsHelper.defineFreshItemBinding("CONTEXT", context);
      preconditions.addCondition(KeepCondition.builder().setItemReference(contextBinding).build());
      addContext.accept(metaInfoBuilder);
    }

    @Override
    public void visit(String name, Object value) {
      if (maybeVisitQualifiedName(name, value)) {
        return;
      }
      if (maybeVisitIncludeSubclasses(name, value)) {
        return;
      }
      if (name.equals(UsesReflectionToAccessMethod.methodName) && value instanceof String) {
        methodName = KeepMethodNamePattern.exact((String) value);
        methodNameKotlinDefault = KeepMethodNamePattern.exact(value + "$default");
        return;
      }
      if (name.equals(UsesReflectionToAccessMethod.returnType) && value instanceof Type) {
        returnType =
            KeepMethodReturnTypePattern.fromType(
                KeepTypePattern.fromDescriptor(((Type) value).getDescriptor()));
        return;
      }
      if (name.equals(UsesReflectionToAccessMethod.returnTypeName) && value instanceof String) {
        if (value.equals("void") || value.equals("Unit")) {
          returnType = KeepMethodReturnTypePattern.voidType();
        } else {
          returnType =
              KeepMethodReturnTypePattern.fromType(
                  KeepTypePattern.fromClass(
                      KeepClassPattern.fromName(
                          KeepQualifiedClassNamePattern.exactFromDescriptor(
                              DescriptorUtils.javaTypeToDescriptor(
                                  kotlinTypeToJavaType((String) value))))));
        }
        return;
      }
      super.visit(name, value);
    }

    @Override
    public AnnotationVisitor visitArray(String name) {
      PropertyParsingContext propertyParsingContext = parsingContext.property(name);
      if (name.equals(UsesReflectionToAccessMethod.parameterTypes)) {
        return new ParametersClassVisitor(
            propertyParsingContext, parameters -> this.parameters = parameters);
      }
      if (name.equals(UsesReflectionToAccessMethod.parameterTypeNames)) {
        return new ParametersClassNamesVisitor(
            propertyParsingContext, parameters -> this.parameters = parameters);
      }
      return super.visitArray(name);
    }

    @Override
    public void visitEnd() {
      KeepClassBindingReference classBinding =
          bindingsHelper.defineFreshClassBinding(getKeepClassItemPattern());
      KeepMemberBindingReference memberBinding =
          bindingsHelper.defineFreshMemberBinding(
              KeepMemberItemPattern.builder()
                  .setClassReference(classBinding)
                  .setMemberPattern(
                      KeepMethodPattern.builder()
                          .setNamePattern(methodName)
                          .setParametersPattern(parameters)
                          .setReturnTypePattern(returnType)
                          .build())
                  .build());
      // Use a fresh class binding for the Kotlin $default method as otherwise this
      // method will be part of the required structure of the class matched. See test
      // KeepConjunctiveBindingsTest.
      KeepClassBindingReference classBindingForKotlinDefaultMethod =
          bindingsHelper.defineFreshClassBinding(getKeepClassItemPattern());
      KeepMemberBindingReference memberBindingKotlinDefault =
          bindingsHelper.defineFreshMemberBinding(
              KeepMemberItemPattern.builder()
                  .setClassReference(classBindingForKotlinDefaultMethod)
                  .setMemberPattern(
                      KeepMethodPattern.builder()
                          .setNamePattern(methodNameKotlinDefault)
                          // For the $default method keep any signature.
                          .setParametersPattern(KeepMethodParametersPattern.any())
                          .setReturnTypePattern(returnType)
                          .build())
                  .build());

      KeepConsequences.Builder consequencesBuilder =
          KeepConsequences.builder()
              .addTarget(KeepTarget.builder().setItemReference(classBinding).build())
              .addTarget(KeepTarget.builder().setItemReference(memberBinding).build())
              .addTarget(
                  KeepTarget.builder().setItemReference(classBindingForKotlinDefaultMethod).build())
              .addTarget(KeepTarget.builder().setItemReference(memberBindingKotlinDefault).build());

      parent.accept(
          builder
              .setMetaInfo(metaInfoBuilder.build())
              .setBindings(bindingsHelper.build())
              .setPreconditions(preconditions.build())
              .setConsequences(consequencesBuilder.build())
              .build());
    }
  }

  private static class UsesReflectionToAccessFieldContainerVisitor extends AnnotationVisitorBase {

    private final AnnotationParsingContext parsingContext;
    private final Parent<KeepEdge> parent;
    private final Consumer<KeepEdgeMetaInfo.Builder> addContext;
    private final Function<UserBindingsHelper, KeepItemPattern> contextBuilder;

    UsesReflectionToAccessFieldContainerVisitor(
        AnnotationParsingContext parsingContext,
        Parent<KeepEdge> parent,
        Consumer<KeepEdgeMetaInfo.Builder> addContext,
        Function<UserBindingsHelper, KeepItemPattern> contextBuilder) {
      super(parsingContext);
      this.parsingContext = parsingContext;
      this.parent = parent;
      this.addContext = addContext;
      this.contextBuilder = contextBuilder;
    }

    @Override
    public AnnotationVisitor visitArray(String name) {
      if (name.equals("value")) {
        return new UsesReflectionToAccessFieldContainerElementVisitor(
            parsingContext, parent, addContext, contextBuilder);
      }
      return super.visitArray(name);
    }

    @Override
    public void visit(String name, Object value) {
      super.visit(name, value);
    }

    @Override
    public void visitEnd() {
      super.visitEnd();
    }
  }

  private static class UsesReflectionToAccessFieldContainerElementVisitor
      extends AnnotationVisitorBase {
    private final AnnotationParsingContext parsingContext;
    private final Parent<KeepEdge> parent;
    Consumer<KeepEdgeMetaInfo.Builder> addContext;
    Function<UserBindingsHelper, KeepItemPattern> contextBuilder;

    public UsesReflectionToAccessFieldContainerElementVisitor(
        AnnotationParsingContext parsingContext,
        Parent<KeepEdge> parent,
        Consumer<KeepEdgeMetaInfo.Builder> addContext,
        Function<UserBindingsHelper, KeepItemPattern> contextBuilder) {
      super(parsingContext);
      this.parsingContext = parsingContext;
      this.parent = parent;
      this.addContext = addContext;
      this.contextBuilder = contextBuilder;
    }

    @Override
    public AnnotationVisitor visitAnnotation(String name, String descriptor) {
      assert name == null;
      if (AnnotationConstants.UsesReflectionToAccessField.isDescriptor(descriptor)) {
        return new UsesReflectionToAccessFieldVisitor(
            parsingContext, parent, addContext, contextBuilder);
      }
      return super.visitAnnotation(name, descriptor);
    }

    @Override
    public void visit(String name, Object value) {
      super.visit(name, value);
    }
  }

  private static class UsesReflectionToAccessFieldVisitor extends UsesReflectionToXXXVisitor {

    private final Parent<KeepEdge> parent;
    private final KeepEdge.Builder builder = KeepEdge.builder();
    private final KeepPreconditions.Builder preconditions = KeepPreconditions.builder();
    private final KeepEdgeMetaInfo.Builder metaInfoBuilder = KeepEdgeMetaInfo.builder();
    private KeepMethodParametersPattern parameters = KeepMethodParametersPattern.any();
    private final UserBindingsHelper bindingsHelper = new UserBindingsHelper();

    private KeepQualifiedClassNamePattern qualifiedName;
    private boolean includeSubclasses = false;
    private KeepFieldNamePattern fieldName;
    private KeepFieldTypePattern fieldType = KeepFieldTypePattern.any();

    UsesReflectionToAccessFieldVisitor(
        AnnotationParsingContext parsingContext,
        Parent<KeepEdge> parent,
        Consumer<KeepEdgeMetaInfo.Builder> addContext,
        Function<UserBindingsHelper, KeepItemPattern> contextBuilder) {
      super(parsingContext);
      this.parent = parent;
      KeepItemPattern context = contextBuilder.apply(bindingsHelper);
      KeepBindingReference contextBinding =
          bindingsHelper.defineFreshItemBinding("CONTEXT", context);
      preconditions.addCondition(KeepCondition.builder().setItemReference(contextBinding).build());
      addContext.accept(metaInfoBuilder);
    }

    @Override
    public void visit(String name, Object value) {
      if (maybeVisitQualifiedName(name, value)) {
        return;
      }
      if (maybeVisitIncludeSubclasses(name, value)) {
        return;
      }
      if (name.equals(UsesReflectionToAccessField.fieldName) && value instanceof String) {
        fieldName = KeepFieldNamePattern.exact((String) value);
        return;
      }
      if (name.equals(UsesReflectionToAccessField.fieldType) && value instanceof Type) {
        fieldType =
            KeepFieldTypePattern.fromType(
                KeepTypePattern.fromDescriptor(((Type) value).getDescriptor()));
        return;
      }
      if (name.equals(UsesReflectionToAccessField.fieldTypeName) && value instanceof String) {
        fieldType =
            KeepFieldTypePattern.fromType(
                KeepTypePattern.fromClass(
                    KeepClassPattern.fromName(
                        KeepQualifiedClassNamePattern.exact((String) value))));
        return;
      }
      super.visit(name, value);
    }

    @Override
    public void visitEnd() {
      KeepClassBindingReference classBinding =
          bindingsHelper.defineFreshClassBinding(getKeepClassItemPattern());
      KeepMemberBindingReference memberBinding =
          bindingsHelper.defineFreshMemberBinding(
              KeepMemberItemPattern.builder()
                  .setClassReference(classBinding)
                  .setMemberPattern(
                      KeepFieldPattern.builder()
                          .setNamePattern(fieldName)
                          .setTypePattern(fieldType)
                          .build())
                  .build());

      KeepConsequences.Builder consequencesBuilder =
          KeepConsequences.builder()
              .addTarget(KeepTarget.builder().setItemReference(classBinding).build())
              .addTarget(KeepTarget.builder().setItemReference(memberBinding).build())
              .addTarget(KeepTarget.builder().setItemReference(memberBinding).build());

      parent.accept(
          builder
              .setMetaInfo(metaInfoBuilder.build())
              .setBindings(bindingsHelper.build())
              .setPreconditions(preconditions.build())
              .setConsequences(consequencesBuilder.build())
              .build());
    }
  }

  private static class UnconditionallyKeepClassVisitor extends AnnotationVisitorBase {

    private final Parent<KeepEdge> parent;
    private final KeepEdge.Builder builder = KeepEdge.builder();
    private final KeepEdgeMetaInfo.Builder metaInfoBuilder = KeepEdgeMetaInfo.builder();
    private final UserBindingsHelper bindingsHelper = new UserBindingsHelper();
    private final KeepConsequences.Builder consequences = KeepConsequences.builder();

    UnconditionallyKeepClassVisitor(
        AnnotationParsingContext parsingContext,
        Parent<KeepEdge> parent,
        Consumer<KeepEdgeMetaInfo.Builder> addContext,
        String className) {
      super(parsingContext);
      this.parent = parent;
      addContext.accept(metaInfoBuilder);
      KeepClassBindingReference kotlinMetadataBinding =
          bindingsHelper.defineFreshClassBinding(
              KeepClassItemPattern.builder()
                  .setClassPattern(KeepClassPattern.exact(className))
                  .build());
      consequences.addTarget(KeepTarget.builder().setItemReference(kotlinMetadataBinding).build());
    }

    @Override
    public void visitEnd() {
      parent.accept(
          builder
              .setMetaInfo(metaInfoBuilder.build())
              .setBindings(bindingsHelper.build())
              .setConsequences(consequences.build())
              .build());
    }
  }

  private static class UnconditionallyKeepMemberVisitor extends AnnotationVisitorBase {

    private final Parent<KeepEdge> parent;
    private final KeepEdge.Builder builder = KeepEdge.builder();
    private final KeepEdgeMetaInfo.Builder metaInfoBuilder = KeepEdgeMetaInfo.builder();
    private final UserBindingsHelper bindingsHelper = new UserBindingsHelper();
    private final KeepConsequences.Builder consequences = KeepConsequences.builder();

    UnconditionallyKeepMemberVisitor(
        AnnotationParsingContext parsingContext,
        Parent<KeepEdge> parent,
        Consumer<KeepEdgeMetaInfo.Builder> addContext,
        Function<UserBindingsHelper, KeepMemberItemPattern> contextBuilder) {
      super(parsingContext);
      this.parent = parent;
      addContext.accept(metaInfoBuilder);
      KeepMemberItemPattern context = contextBuilder.apply(bindingsHelper);
      KeepClassBindingReference classReference = context.getClassReference();
      consequences.addTarget(
          KeepTarget.builder()
              .setItemReference(bindingsHelper.defineFreshMemberBinding(context))
              .build());
      consequences.addTarget(KeepTarget.builder().setItemReference(classReference).build());
    }

    @Override
    public void visitEnd() {
      parent.accept(
          builder
              .setMetaInfo(metaInfoBuilder.build())
              .setBindings(bindingsHelper.build())
              .setConsequences(consequences.build())
              .build());
    }
  }

  private static class KeepBindingsVisitor extends AnnotationVisitorBase {
    private final ParsingContext parsingContext;
    private final UserBindingsHelper helper;

    public KeepBindingsVisitor(PropertyParsingContext parsingContext, UserBindingsHelper helper) {
      super(parsingContext);
      this.parsingContext = parsingContext;
      this.helper = helper;
    }

    @Override
    public AnnotationVisitor visitAnnotation(String name, String descriptor) {
      assert name == null;
      if (AnnotationConstants.Binding.isDescriptor(descriptor)) {
        return new KeepBindingVisitor(parsingContext.annotation(descriptor), helper);
      }
      return super.visitAnnotation(name, descriptor);
    }
  }

  private static class KeepPreconditionsVisitor extends AnnotationVisitorBase {
    private final ParsingContext parsingContext;
    private final Parent<KeepPreconditions> parent;
    private final KeepPreconditions.Builder builder = KeepPreconditions.builder();
    private final UserBindingsHelper bindingsHelper;

    public KeepPreconditionsVisitor(
        PropertyParsingContext parsingContext,
        Parent<KeepPreconditions> parent,
        UserBindingsHelper bindingsHelper) {
      super(parsingContext);
      this.parsingContext = parsingContext;
      this.parent = parent;
      this.bindingsHelper = bindingsHelper;
    }

    @Override
    public AnnotationVisitor visitAnnotation(String name, String descriptor) {
      assert name == null;
      if (Condition.isDescriptor(descriptor)) {
        return new KeepConditionVisitor(
            parsingContext.annotation(descriptor), builder::addCondition, bindingsHelper);
      }
      return super.visitAnnotation(name, descriptor);
    }

    @Override
    public void visitEnd() {
      parent.accept(builder.build());
    }
  }

  private static class KeepConsequencesVisitor extends AnnotationVisitorBase {
    private final ParsingContext parsingContext;
    private final Parent<KeepConsequences> parent;
    private final KeepConsequences.Builder builder = KeepConsequences.builder();
    private final UserBindingsHelper bindingsHelper;

    public KeepConsequencesVisitor(
        PropertyParsingContext parsingContext,
        Parent<KeepConsequences> parent,
        UserBindingsHelper bindingsHelper) {
      super(parsingContext);
      this.parsingContext = parsingContext;
      this.parent = parent;
      this.bindingsHelper = bindingsHelper;
    }

    @Override
    public AnnotationVisitor visitAnnotation(String name, String descriptor) {
      assert name == null;
      if (Target.isDescriptor(descriptor)) {
        return KeepTargetVisitor.create(
            parsingContext.annotation(descriptor), builder::addTarget, bindingsHelper);
      }
      return super.visitAnnotation(name, descriptor);
    }

    @Override
    public void visitEnd() {
      parent.accept(builder.build());
    }
  }

  /** Parsing of @CheckRemoved and @CheckOptimizedOut on a class context. */
  private static class CheckRemovedClassVisitor extends AnnotationVisitorBase {

    private final ParsingContext parsingContext;
    private final Parent<KeepCheck> parent;
    private final KeepEdgeMetaInfo.Builder metaInfoBuilder = KeepEdgeMetaInfo.builder();
    private final String className;
    private final KeepCheckKind kind;

    public CheckRemovedClassVisitor(
        AnnotationParsingContext parsingContext,
        Parent<KeepCheck> parent,
        Consumer<KeepEdgeMetaInfo.Builder> addContext,
        String className,
        KeepCheckKind kind) {
      super(parsingContext);
      this.parsingContext = parsingContext;
      this.parent = parent;
      this.className = className;
      this.kind = kind;
      addContext.accept(metaInfoBuilder);
    }

    @Override
    public void visit(String name, Object value) {
      if (name.equals(Edge.description) && value instanceof String) {
        metaInfoBuilder.setDescription((String) value);
        return;
      }
      super.visit(name, value);
    }

    @Override
    public void visitEnd() {
      UserBindingsHelper bindingsHelper = new UserBindingsHelper();
      KeepItemVisitorBase itemVisitor =
          new KeepItemVisitorBase(parsingContext) {
            @Override
            public UserBindingsHelper getBindingsHelper() {
              return bindingsHelper;
            }
          };
      itemVisitor.visit(Item.className, className);
      itemVisitor.visitEnd();
      parent.accept(
          KeepCheck.builder()
              .setMetaInfo(metaInfoBuilder.build())
              .setKind(kind)
              .setBindings(itemVisitor.getBindingsHelper().build())
              .setItemReference(itemVisitor.getItemReference())
              .build());
    }
  }

  /** Parsing of @CheckRemoved and @CheckOptimizedOut on a class context. */
  private static class CheckRemovedMemberVisitor extends AnnotationVisitorBase {

    private final Parent<KeepDeclaration> parent;
    private final KeepMemberBindingReference context;
    private final KeepEdgeMetaInfo.Builder metaInfoBuilder = KeepEdgeMetaInfo.builder();
    private final KeepCheckKind kind;
    private final UserBindingsHelper bindingsHelper = new UserBindingsHelper();

    CheckRemovedMemberVisitor(
        AnnotationParsingContext parsingContext,
        Parent<KeepDeclaration> parent,
        Consumer<KeepEdgeMetaInfo.Builder> addContext,
        Function<UserBindingsHelper, KeepMemberItemPattern> contextBuilder,
        KeepCheckKind kind) {
      super(parsingContext);
      this.parent = parent;
      this.context = bindingsHelper.defineFreshMemberBinding(contextBuilder.apply(bindingsHelper));
      this.kind = kind;
      addContext.accept(metaInfoBuilder);
    }

    @Override
    public void visit(String name, Object value) {
      if (name.equals(Edge.description) && value instanceof String) {
        metaInfoBuilder.setDescription((String) value);
        return;
      }
      super.visit(name, value);
    }

    @Override
    public void visitEnd() {
      super.visitEnd();
      parent.accept(buildKeepCheckFromItem(metaInfoBuilder.build(), kind, context, bindingsHelper));
    }

    private static KeepCheck buildKeepCheckFromItem(
        KeepEdgeMetaInfo metaInfo,
        KeepCheckKind kind,
        KeepBindingReference itemReference,
        UserBindingsHelper bindingsHelper) {
      return KeepCheck.builder()
          .setMetaInfo(metaInfo)
          .setKind(kind)
          .setBindings(bindingsHelper.build())
          .setItemReference(itemReference)
          .build();
    }
  }

  private static class ClassDeclarationParser extends DeclarationParser<KeepClassBindingReference> {

    private final ParsingContext parsingContext;
    private final Supplier<UserBindingsHelper> getBindingsHelper;

    private KeepClassBindingReference boundClassReference = null;
    private final ClassNameParser classNameParser;
    private final ClassNameParser annotatedByParser;
    private final InstanceOfParser instanceOfParser;
    private final List<Parser<?>> parsers;

    public ClassDeclarationParser(
        ParsingContext parsingContext, Supplier<UserBindingsHelper> getBindingsHelper) {
      this.parsingContext = parsingContext.group(Item.classGroup);
      this.getBindingsHelper = getBindingsHelper;
      classNameParser = new ClassNameParser(parsingContext.group(Item.classNameGroup));
      classNameParser.setProperty(Item.className, ClassNameProperty.NAME);
      classNameParser.setProperty(Item.classConstant, ClassNameProperty.CONSTANT);
      classNameParser.setProperty(Item.classNamePattern, ClassNameProperty.PATTERN);

      annotatedByParser = new ClassNameParser(parsingContext.group(Item.classAnnotatedByGroup));
      annotatedByParser.setProperty(Item.classAnnotatedByClassName, ClassNameProperty.NAME);
      annotatedByParser.setProperty(Item.classAnnotatedByClassConstant, ClassNameProperty.CONSTANT);
      annotatedByParser.setProperty(
          Item.classAnnotatedByClassNamePattern, ClassNameProperty.PATTERN);

      instanceOfParser = new InstanceOfParser(parsingContext);
      instanceOfParser.setProperty(Item.instanceOfPattern, InstanceOfProperties.PATTERN);
      instanceOfParser.setProperty(Item.instanceOfClassName, InstanceOfProperties.NAME);
      instanceOfParser.setProperty(Item.instanceOfClassConstant, InstanceOfProperties.CONSTANT);
      instanceOfParser.setProperty(
          Item.instanceOfClassNameExclusive, InstanceOfProperties.NAME_EXCL);
      instanceOfParser.setProperty(
          Item.instanceOfClassConstantExclusive, InstanceOfProperties.CONSTANT_EXCL);

      parsers = ImmutableList.of(classNameParser, annotatedByParser, instanceOfParser);
    }

    @Override
    public List<Parser<?>> parsers() {
      return parsers;
    }

    private boolean isBindingReferenceDefined() {
      return boundClassReference != null;
    }

    private boolean classPatternsAreDeclared() {
      return classNameParser.isDeclared()
          || annotatedByParser.isDeclared()
          || instanceOfParser.isDeclared();
    }

    private void checkAllowedDefinitions() {
      if (isBindingReferenceDefined() && classPatternsAreDeclared()) {
        throw parsingContext.error(
            "Cannot reference a class binding and class patterns for a single class item");
      }
    }

    @Override
    public boolean isDeclared() {
      return isBindingReferenceDefined() || super.isDeclared();
    }

    public KeepClassBindingReference getValue() {
      checkAllowedDefinitions();
      if (isBindingReferenceDefined()) {
        return boundClassReference;
      }
      KeepClassItemPattern classItemPattern;
      if (classPatternsAreDeclared()) {
        classItemPattern =
            KeepClassItemPattern.builder()
                .setClassNamePattern(
                    classNameParser.getValueOrDefault(KeepQualifiedClassNamePattern.any()))
                .setAnnotatedByPattern(OptionalPattern.ofNullable(annotatedByParser.getValue()))
                .setInstanceOfPattern(
                    instanceOfParser.getValueOrDefault(KeepInstanceOfPattern.any()))
                .build();
      } else {
        assert isDefault();
        classItemPattern = KeepClassItemPattern.any();
      }
      return getBindingsHelper.get().defineFreshClassBinding(classItemPattern);
    }

    public void setBindingReference(KeepClassBindingReference bindingReference) {
      if (isBindingReferenceDefined()) {
        throw parsingContext.error(
            "Cannot reference multiple class bindings for a single class item");
      }
      this.boundClassReference = bindingReference;
    }

    @Override
    public boolean tryParse(String name, Object value) {
      if (name.equals(Item.classFromBinding) && value instanceof String) {
        KeepBindingSymbol symbol = getBindingsHelper.get().resolveUserBinding((String) value);
        setBindingReference(KeepBindingReference.forClass(symbol));
        return true;
      }
      return super.tryParse(name, value);
    }
  }

  private static class MethodDeclarationParser extends DeclarationParser<KeepMethodPattern> {

    private final ParsingContext parsingContext;
    private KeepMethodAccessPattern.Builder accessBuilder = null;
    private KeepMethodPattern.Builder builder = null;
    private final ClassNameParser annotatedByParser;
    private final StringPatternParser nameParser;
    private final MethodReturnTypeParser returnTypeParser;
    private final MethodParametersParser parametersParser;

    private final List<Parser<?>> parsers;

    private MethodDeclarationParser(ParsingContext parsingContext) {
      this.parsingContext = parsingContext;

      annotatedByParser = new ClassNameParser(parsingContext.group(Item.methodAnnotatedByGroup));
      annotatedByParser.setProperty(Item.methodAnnotatedByClassName, ClassNameProperty.NAME);
      annotatedByParser.setProperty(
          Item.methodAnnotatedByClassConstant, ClassNameProperty.CONSTANT);
      annotatedByParser.setProperty(
          Item.methodAnnotatedByClassNamePattern, ClassNameProperty.PATTERN);

      nameParser = new StringPatternParser(parsingContext.group(Item.methodNameGroup));
      nameParser.setProperty(Item.methodName, StringProperty.EXACT);
      nameParser.setProperty(Item.methodNamePattern, StringProperty.PATTERN);

      returnTypeParser = new MethodReturnTypeParser(parsingContext.group(Item.returnTypeGroup));
      returnTypeParser.setProperty(Item.methodReturnType, TypeProperty.TYPE_NAME);
      returnTypeParser.setProperty(Item.methodReturnTypeConstant, TypeProperty.TYPE_CONSTANT);
      returnTypeParser.setProperty(Item.methodReturnTypePattern, TypeProperty.TYPE_PATTERN);

      parametersParser = new MethodParametersParser(parsingContext.group(Item.parametersGroup));
      parametersParser.setProperty(Item.methodParameters, TypeProperty.TYPE_NAME);
      parametersParser.setProperty(Item.methodParameterTypePatterns, TypeProperty.TYPE_PATTERN);

      parsers = ImmutableList.of(annotatedByParser, nameParser, returnTypeParser, parametersParser);
    }

    @Override
    List<Parser<?>> parsers() {
      return parsers;
    }

    private KeepMethodPattern.Builder getBuilder() {
      if (builder == null) {
        builder = KeepMethodPattern.builder();
      }
      return builder;
    }

    @Override
    public boolean isDeclared() {
      return accessBuilder != null || builder != null || super.isDeclared();
    }

    public KeepMethodPattern getValue() {
      if (accessBuilder != null) {
        getBuilder().setAccessPattern(accessBuilder.build());
      }
      if (annotatedByParser.isDeclared()) {
        getBuilder().setAnnotatedByPattern(OptionalPattern.of(annotatedByParser.getValue()));
      }
      if (nameParser.isDeclared()) {
        KeepStringPattern namePattern = nameParser.getValue();
        getBuilder().setNamePattern(KeepMethodNamePattern.fromStringPattern(namePattern));
      }
      if (returnTypeParser.isDeclared()) {
        getBuilder().setReturnTypePattern(returnTypeParser.getValue());
      }
      if (parametersParser.isDeclared()) {
        getBuilder().setParametersPattern(parametersParser.getValue());
      }
      return builder != null ? builder.build() : null;
    }

    @Override
    public AnnotationVisitor tryParseArray(String name) {
      if (name.equals(Item.methodAccess)) {
        accessBuilder = KeepMethodAccessPattern.builder();
        return new MethodAccessVisitor(parsingContext, accessBuilder);
      }
      return super.tryParseArray(name);
    }
  }

  private static class FieldDeclarationParser extends DeclarationParser<KeepFieldPattern> {

    private final ParsingContext parsingContext;
    private final ClassNameParser annotatedByParser;
    private final StringPatternParser nameParser;
    private final FieldTypeParser typeParser;
    private KeepFieldAccessPattern.Builder accessBuilder = null;
    private KeepFieldPattern.Builder builder = null;
    private final List<Parser<?>> parsers;

    public FieldDeclarationParser(ParsingContext parsingContext) {
      this.parsingContext = parsingContext;
      annotatedByParser = new ClassNameParser(parsingContext.group(Item.fieldAnnotatedByGroup));
      annotatedByParser.setProperty(Item.fieldAnnotatedByClassName, ClassNameProperty.NAME);
      annotatedByParser.setProperty(Item.fieldAnnotatedByClassConstant, ClassNameProperty.CONSTANT);
      annotatedByParser.setProperty(
          Item.fieldAnnotatedByClassNamePattern, ClassNameProperty.PATTERN);

      nameParser = new StringPatternParser(parsingContext.group(Item.fieldNameGroup));
      nameParser.setProperty(Item.fieldName, StringProperty.EXACT);
      nameParser.setProperty(Item.fieldNamePattern, StringProperty.PATTERN);

      typeParser = new FieldTypeParser(parsingContext.group(Item.fieldTypeGroup));
      typeParser.setProperty(Item.fieldTypePattern, TypeProperty.TYPE_PATTERN);
      typeParser.setProperty(Item.fieldType, TypeProperty.TYPE_NAME);
      typeParser.setProperty(Item.fieldTypeConstant, TypeProperty.TYPE_CONSTANT);

      parsers = ImmutableList.of(annotatedByParser, nameParser, typeParser);
    }

    @Override
    public List<Parser<?>> parsers() {
      return parsers;
    }

    private KeepFieldPattern.Builder getBuilder() {
      if (builder == null) {
        builder = KeepFieldPattern.builder();
      }
      return builder;
    }

    @Override
    public boolean isDeclared() {
      return accessBuilder != null || builder != null || super.isDeclared();
    }

    public KeepFieldPattern getValue() {
      if (accessBuilder != null) {
        getBuilder().setAccessPattern(accessBuilder.build());
      }
      if (annotatedByParser.isDeclared()) {
        getBuilder().setAnnotatedByPattern(OptionalPattern.of(annotatedByParser.getValue()));
      }
      if (nameParser.isDeclared()) {
        getBuilder().setNamePattern(KeepFieldNamePattern.fromStringPattern(nameParser.getValue()));
      }
      if (typeParser.isDeclared()) {
        getBuilder().setTypePattern(typeParser.getValue());
      }
      return builder != null ? builder.build() : null;
    }

    @Override
    public AnnotationVisitor tryParseArray(String name) {
      if (name.equals(Item.fieldAccess)) {
        accessBuilder = KeepFieldAccessPattern.builder();
        return new FieldAccessVisitor(parsingContext, accessBuilder);
      }
      return super.tryParseArray(name);
    }
  }

  private static class MemberDeclarationParser extends DeclarationParser<KeepMemberPattern> {

    private final ParsingContext parsingContext;
    private KeepMemberAccessPattern.Builder accessBuilder = null;
    private final ClassNameParser annotatedByParser;

    private final MethodDeclarationParser methodDeclaration;
    private final FieldDeclarationParser fieldDeclaration;
    private final List<Parser<?>> parsers;

    MemberDeclarationParser(ParsingContext parsingContext) {
      this.parsingContext = parsingContext.group(Item.memberGroup);

      annotatedByParser = new ClassNameParser(parsingContext.group(Item.memberAnnotatedByGroup));
      annotatedByParser.setProperty(Item.memberAnnotatedByClassName, ClassNameProperty.NAME);
      annotatedByParser.setProperty(
          Item.memberAnnotatedByClassConstant, ClassNameProperty.CONSTANT);
      annotatedByParser.setProperty(
          Item.memberAnnotatedByClassNamePattern, ClassNameProperty.PATTERN);

      methodDeclaration = new MethodDeclarationParser(parsingContext);
      fieldDeclaration = new FieldDeclarationParser(parsingContext);
      parsers = ImmutableList.of(annotatedByParser, methodDeclaration, fieldDeclaration);
    }

    @Override
    public List<Parser<?>> parsers() {
      return parsers;
    }

    @Override
    public boolean isDeclared() {
      return accessBuilder != null || super.isDeclared();
    }

    public KeepMemberPattern getValue() {
      KeepMethodPattern method = methodDeclaration.getValue();
      KeepFieldPattern field = fieldDeclaration.getValue();
      if (accessBuilder != null || annotatedByParser.isDeclared()) {
        if (method != null || field != null) {
          throw parsingContext.error(
              "Cannot define common member access as well as field or method pattern");
        }
        KeepMemberPattern.Builder builder = KeepMemberPattern.memberBuilder();
        if (accessBuilder != null) {
          builder.setAccessPattern(accessBuilder.build());
        }
        builder.setAnnotatedByPattern(OptionalPattern.ofNullable(annotatedByParser.getValue()));
        return builder.build();
      }
      if (method != null && field != null) {
        throw parsingContext.error("Cannot define both a field and a method pattern");
      }
      if (method != null) {
        return method;
      }
      if (field != null) {
        return field;
      }
      return null;
    }

    @Override
    public AnnotationVisitor tryParseArray(String name) {
      if (name.equals(Item.memberAccess)) {
        accessBuilder = KeepMemberAccessPattern.memberBuilder();
        return new MemberAccessVisitor(parsingContext, accessBuilder);
      }
      return super.tryParseArray(name);
    }
  }

  private abstract static class KeepItemVisitorBase extends AnnotationVisitorBase {
    private final ParsingContext parsingContext;
    private String memberBindingReference = null;
    private ItemKind kind = null;
    private final ClassDeclarationParser classDeclaration;
    private final MemberDeclarationParser memberDeclaration;

    public abstract UserBindingsHelper getBindingsHelper();

    public boolean useBindingForClassAndMembers() {
      return true;
    }

    // Constructed bindings available once visitEnd has been called.
    private List<KeepBindingReference> itemReferences = null;

    KeepItemVisitorBase(ParsingContext parsingContext) {
      super(parsingContext);
      this.parsingContext = parsingContext;
      classDeclaration = new ClassDeclarationParser(parsingContext, this::getBindingsHelper);
      memberDeclaration = new MemberDeclarationParser(parsingContext);
    }

    public Collection<KeepBindingReference> getItems() {
      if (itemReferences == null || kind == null) {
        throw parsingContext.error("Items not finalized. Missing call to visitEnd()");
      }
      return itemReferences;
    }

    public KeepBindingReference getItemReference() {
      if (itemReferences == null) {
        throw parsingContext.error("Item reference not finalized. Missing call to visitEnd()");
      }
      if (itemReferences.size() > 1) {
        throw parsingContext.error("Ambiguous item reference.");
      }
      return itemReferences.get(0);
    }

    public ItemKind getKind() {
      return kind;
    }

    public boolean isDefaultMemberDeclaration() {
      return memberDeclaration.isDefault();
    }

    @Override
    public void visitEnum(String name, String descriptor, String value) {
      if (!AnnotationConstants.Kind.isDescriptor(descriptor)) {
        super.visitEnum(name, descriptor, value);
      }
      ItemKind kind = ItemKind.fromString(value);
      if (kind != null) {
        this.kind = kind;
      } else {
        super.visitEnum(name, descriptor, value);
      }
    }

    @Override
    public void visit(String name, Object value) {
      if (name.equals(Item.memberFromBinding) && value instanceof String) {
        memberBindingReference = (String) value;
        return;
      }
      if (classDeclaration.tryParse(name, value)
          || memberDeclaration.tryParse(name, value)) {
        return;
      }
      super.visit(name, value);
    }

    @Override
    public AnnotationVisitor visitAnnotation(String name, String descriptor) {
      AnnotationVisitor visitor = classDeclaration.tryParseAnnotation(name, descriptor);
      if (visitor != null) {
        return visitor;
      }
      visitor = memberDeclaration.tryParseAnnotation(name, descriptor);
      if (visitor != null) {
        return visitor;
      }
      return super.visitAnnotation(name, descriptor);
    }

    @Override
    public AnnotationVisitor visitArray(String name) {
      AnnotationVisitor visitor = memberDeclaration.tryParseArray(name);
      if (visitor != null) {
        return visitor;
      }
      return super.visitArray(name);
    }

    private void visitEndWithMemberBindingReference() {
      if (classDeclaration.isDeclared() || memberDeclaration.isDeclared()) {
        throw parsingContext.error(
            "Cannot define an item explicitly and via a member-binding reference");
      }
      KeepBindingSymbol symbol = getBindingsHelper().resolveUserBinding(memberBindingReference);
      KeepMemberBindingReference memberBinding = KeepBindingReference.forMember(symbol);

      // If no explicit kind is set, the member binding implies it is just members.
      if (kind == null) {
        kind = ItemKind.ONLY_MEMBERS;
      }

      if (!kind.includesClass()) {
        itemReferences = Collections.singletonList(memberBinding);
        return;
      }

      KeepClassBindingReference holderReference =
          getBindingsHelper().getItem(memberBinding).asMemberItemPattern().getClassReference();
      itemReferences =
          ImmutableList.of(ensureCorrectBindingForMemberHolder(holderReference), memberBinding);
    }

    private KeepClassBindingReference ensureCorrectBindingForMemberHolder(
        KeepClassBindingReference bindingReference) {
      return useBindingForClassAndMembers()
          ? bindingReference
          : getBindingsHelper()
              .defineFreshClassBinding(getBindingsHelper().getClassItem(bindingReference));
    }

    @Override
    public void visitEnd() {
      // Item defined by binding reference.
      if (memberBindingReference != null) {
        visitEndWithMemberBindingReference();
        return;
      }

      // If no explicit kind is set, extract it based on the member pattern.
      KeepMemberPattern memberPattern = memberDeclaration.getValue();
      if (kind == null) {
        if (memberPattern == null) {
          kind = ItemKind.ONLY_CLASS;
        } else if (memberPattern.isMethod()) {
          kind = ItemKind.ONLY_METHODS;
        } else if (memberPattern.isField()) {
          kind = ItemKind.ONLY_FIELDS;
        } else if (memberPattern.isGeneralMember()) {
          kind = ItemKind.ONLY_MEMBERS;
        } else {
          assert false;
        }
      }

      // If the pattern is only for a class set it and exit.
      if (kind.isOnlyClass()) {
        if (memberDeclaration.isDeclared()) {
          throw parsingContext.error("Item pattern for members is incompatible with kind " + kind);
        }
        itemReferences = Collections.singletonList(classDeclaration.getValue());
        return;
      }

      // At this point the pattern must include members.
      // If no explicit member pattern is defined the implicit pattern is all members.
      // Then refine the member pattern to be as precise as the specified kind.
      assert kind.requiresMembers();
      if (memberPattern == null) {
        memberPattern = KeepMemberPattern.allMembers();
      }

      if (kind.requiresMethods() && !memberPattern.isMethod()) {
        if (memberPattern.isGeneralMember()) {
          memberPattern = KeepMethodPattern.builder().copyFromMemberPattern(memberPattern).build();
        } else {
          assert memberPattern.isField();
          throw parsingContext.error("Item pattern for fields is incompatible with kind " + kind);
        }
      }

      if (kind.requiresFields() && !memberPattern.isField()) {
        if (memberPattern.isGeneralMember()) {
          memberPattern = KeepFieldPattern.builder().copyFromMemberPattern(memberPattern).build();
        } else {
          assert memberPattern.isMethod();
          throw parsingContext.error("Item pattern for methods is incompatible with kind " + kind);
        }
      }

      ImmutableList.Builder<KeepBindingReference> builder = ImmutableList.builder();
      KeepClassBindingReference classReference = classDeclaration.getValue();
      builder.add(
          getBindingsHelper()
              .defineFreshMemberBinding(
                  KeepMemberItemPattern.builder()
                      .setClassReference(classReference)
                      .setMemberPattern(memberPattern)
                      .build()));
      if (kind.includesClass()) {
        builder.add(ensureCorrectBindingForMemberHolder(classReference));
      }
      itemReferences = builder.build();
    }
  }

  private static class KeepBindingVisitor extends KeepItemVisitorBase {

    private final ParsingContext parsingContext;
    private final UserBindingsHelper helper;
    private String bindingName;

    public KeepBindingVisitor(AnnotationParsingContext parsingContext, UserBindingsHelper helper) {
      super(parsingContext);
      this.parsingContext = parsingContext;
      this.helper = helper;
    }

    @Override
    public UserBindingsHelper getBindingsHelper() {
      return helper;
    }

    @Override
    public void visit(String name, Object value) {
      if (name.equals(Binding.bindingName) && value instanceof String) {
        bindingName = (String) value;
        return;
      }
      super.visit(name, value);
    }

    @Override
    public void visitEnd() {
      super.visitEnd();
      KeepBindingReference bindingReference = getItemReference();
      helper.replaceWithUserBinding(bindingName, bindingReference, parsingContext);
    }
  }

  private static class KeepTargetVisitor extends KeepItemVisitorBase {

    private final Parent<KeepTarget> parent;
    private final UserBindingsHelper bindingsHelper;
    private final ConstraintDeclarationParser constraintsParser;
    private final KeepTarget.Builder builder = KeepTarget.builder();

    static KeepTargetVisitor create(
        AnnotationParsingContext parsingContext,
        Parent<KeepTarget> parent,
        UserBindingsHelper bindingsHelper) {
      return new KeepTargetVisitor(parsingContext, parent, bindingsHelper);
    }

    private KeepTargetVisitor(
        AnnotationParsingContext parsingContext,
        Parent<KeepTarget> parent,
        UserBindingsHelper bindingsHelper) {
      super(parsingContext);
      this.parent = parent;
      this.bindingsHelper = bindingsHelper;
      constraintsParser = new ConstraintDeclarationParser(parsingContext);
    }

    @Override
    public UserBindingsHelper getBindingsHelper() {
      return bindingsHelper;
    }

    @Override
    public AnnotationVisitor visitArray(String name) {
      AnnotationVisitor visitor = constraintsParser.tryParseArray(name, unused -> {});
      if (visitor != null) {
        return visitor;
      }
      return super.visitArray(name);
    }

    @Override
    public void visitEnd() {
      super.visitEnd();
      builder.setConstraints(
          constraintsParser.getValueOrDefault(KeepConstraints.defaultConstraints()));
      for (KeepBindingReference item : getItems()) {
        parent.accept(builder.setItemReference(item).build());
      }
    }
  }

  private static class KeepConditionVisitor extends KeepItemVisitorBase {

    private final Parent<KeepCondition> parent;
    private final UserBindingsHelper bindingsHelper;

    public KeepConditionVisitor(
        AnnotationParsingContext parsingContext,
        Parent<KeepCondition> parent,
        UserBindingsHelper bindingsHelper) {
      super(parsingContext);
      this.parent = parent;
      this.bindingsHelper = bindingsHelper;
    }

    @Override
    public UserBindingsHelper getBindingsHelper() {
      return bindingsHelper;
    }

    @Override
    public void visitEnd() {
      super.visitEnd();
      parent.accept(KeepCondition.builder().setItemReference(getItemReference()).build());
    }
  }

  private static class MemberAccessVisitor extends AnnotationVisitorBase {
    private KeepMemberAccessPattern.BuilderBase<?, ?> builder;

    public MemberAccessVisitor(ParsingContext parsingContext, BuilderBase<?, ?> builder) {
      super(parsingContext);
      this.builder = builder;
    }

    static boolean withNormalizedAccessFlag(String flag, BiPredicate<String, Boolean> fn) {
      boolean allow = !flag.startsWith(MemberAccess.NEGATION_PREFIX);
      return allow
          ? fn.test(flag, true)
          : fn.test(flag.substring(MemberAccess.NEGATION_PREFIX.length()), false);
    }

    @Override
    public void visitEnum(String ignore, String descriptor, String value) {
      if (!AnnotationConstants.MemberAccess.isDescriptor(descriptor)) {
        super.visitEnum(ignore, descriptor, value);
      }
      boolean handled =
          withNormalizedAccessFlag(
              value,
              (flag, allow) -> {
                AccessVisibility visibility = getAccessVisibilityFromString(flag);
                if (visibility != null) {
                  builder.setAccessVisibility(visibility, allow);
                  return true;
                }
                switch (flag) {
                  case MemberAccess.STATIC:
                    builder.setStatic(allow);
                    return true;
                  case MemberAccess.FINAL:
                    builder.setFinal(allow);
                    return true;
                  case MemberAccess.SYNTHETIC:
                    builder.setSynthetic(allow);
                    return true;
                  default:
                    return false;
                }
              });
      if (!handled) {
        super.visitEnum(ignore, descriptor, value);
      }
    }

    private AccessVisibility getAccessVisibilityFromString(String value) {
      switch (value) {
        case MemberAccess.PUBLIC:
          return AccessVisibility.PUBLIC;
        case MemberAccess.PROTECTED:
          return AccessVisibility.PROTECTED;
        case MemberAccess.PACKAGE_PRIVATE:
          return AccessVisibility.PACKAGE_PRIVATE;
        case MemberAccess.PRIVATE:
          return AccessVisibility.PRIVATE;
        default:
          return null;
      }
    }
  }

  private static class MethodAccessVisitor extends MemberAccessVisitor {

    private KeepMethodAccessPattern.Builder methodAccessBuilder;

    public MethodAccessVisitor(
        ParsingContext parsingContext, KeepMethodAccessPattern.Builder builder) {
      super(parsingContext, builder);
      this.methodAccessBuilder = builder;
    }

    @Override
    public void visitEnum(String ignore, String descriptor, String value) {
      if (!AnnotationConstants.MethodAccess.isDescriptor(descriptor)) {
        super.visitEnum(ignore, descriptor, value);
      }
      boolean handled =
          withNormalizedAccessFlag(
              value,
              (flag, allow) -> {
                switch (flag) {
                  case MethodAccess.SYNCHRONIZED:
                    methodAccessBuilder.setSynchronized(allow);
                    return true;
                  case MethodAccess.BRIDGE:
                    methodAccessBuilder.setBridge(allow);
                    return true;
                  case MethodAccess.NATIVE:
                    methodAccessBuilder.setNative(allow);
                    return true;
                  case MethodAccess.ABSTRACT:
                    methodAccessBuilder.setAbstract(allow);
                    return true;
                  case MethodAccess.STRICT_FP:
                    methodAccessBuilder.setStrictFp(allow);
                    return true;
                  default:
                    return false;
                }
              });
      if (!handled) {
        // Continue visitation with the "member" descriptor to allow matching the common values.
        super.visitEnum(ignore, MemberAccess.getDescriptor(), value);
      }
    }
  }

  private static class FieldAccessVisitor extends MemberAccessVisitor {

    private KeepFieldAccessPattern.Builder fieldAccessBuilder;

    public FieldAccessVisitor(
        ParsingContext parsingContext, KeepFieldAccessPattern.Builder builder) {
      super(parsingContext, builder);
      this.fieldAccessBuilder = builder;
    }

    @Override
    public void visitEnum(String ignore, String descriptor, String value) {
      if (!AnnotationConstants.FieldAccess.isDescriptor(descriptor)) {
        super.visitEnum(ignore, descriptor, value);
      }
      boolean handled =
          withNormalizedAccessFlag(
              value,
              (flag, allow) -> {
                switch (flag) {
                  case FieldAccess.VOLATILE:
                    fieldAccessBuilder.setVolatile(allow);
                    return true;
                  case FieldAccess.TRANSIENT:
                    fieldAccessBuilder.setTransient(allow);
                    return true;
                  default:
                    return false;
                }
              });
      if (!handled) {
        // Continue visitation with the "member" descriptor to allow matching the common values.
        super.visitEnum(ignore, MemberAccess.getDescriptor(), value);
      }
    }
  }

}
