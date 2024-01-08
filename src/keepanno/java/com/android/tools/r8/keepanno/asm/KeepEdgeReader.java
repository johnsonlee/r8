// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.asm;

import com.android.tools.r8.keepanno.asm.ClassNameParser.ClassNameProperty;
import com.android.tools.r8.keepanno.asm.InstanceOfParser.InstanceOfProperties;
import com.android.tools.r8.keepanno.asm.OptionsParser.OptionsProperty;
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
import com.android.tools.r8.keepanno.ast.KeepBindingReference;
import com.android.tools.r8.keepanno.ast.KeepBindings;
import com.android.tools.r8.keepanno.ast.KeepBindings.KeepBindingSymbol;
import com.android.tools.r8.keepanno.ast.KeepCheck;
import com.android.tools.r8.keepanno.ast.KeepCheck.KeepCheckKind;
import com.android.tools.r8.keepanno.ast.KeepClassItemPattern;
import com.android.tools.r8.keepanno.ast.KeepClassItemReference;
import com.android.tools.r8.keepanno.ast.KeepCondition;
import com.android.tools.r8.keepanno.ast.KeepConsequences;
import com.android.tools.r8.keepanno.ast.KeepDeclaration;
import com.android.tools.r8.keepanno.ast.KeepEdge;
import com.android.tools.r8.keepanno.ast.KeepEdgeMetaInfo;
import com.android.tools.r8.keepanno.ast.KeepFieldAccessPattern;
import com.android.tools.r8.keepanno.ast.KeepFieldNamePattern;
import com.android.tools.r8.keepanno.ast.KeepFieldPattern;
import com.android.tools.r8.keepanno.ast.KeepFieldTypePattern;
import com.android.tools.r8.keepanno.ast.KeepInstanceOfPattern;
import com.android.tools.r8.keepanno.ast.KeepItemPattern;
import com.android.tools.r8.keepanno.ast.KeepItemReference;
import com.android.tools.r8.keepanno.ast.KeepMemberAccessPattern;
import com.android.tools.r8.keepanno.ast.KeepMemberAccessPattern.BuilderBase;
import com.android.tools.r8.keepanno.ast.KeepMemberItemPattern;
import com.android.tools.r8.keepanno.ast.KeepMemberPattern;
import com.android.tools.r8.keepanno.ast.KeepMethodAccessPattern;
import com.android.tools.r8.keepanno.ast.KeepMethodNamePattern;
import com.android.tools.r8.keepanno.ast.KeepMethodParametersPattern;
import com.android.tools.r8.keepanno.ast.KeepMethodPattern;
import com.android.tools.r8.keepanno.ast.KeepMethodReturnTypePattern;
import com.android.tools.r8.keepanno.ast.KeepOptions;
import com.android.tools.r8.keepanno.ast.KeepOptions.KeepOption;
import com.android.tools.r8.keepanno.ast.KeepPreconditions;
import com.android.tools.r8.keepanno.ast.KeepQualifiedClassNamePattern;
import com.android.tools.r8.keepanno.ast.KeepStringPattern;
import com.android.tools.r8.keepanno.ast.KeepTarget;
import com.android.tools.r8.keepanno.ast.KeepTypePattern;
import com.android.tools.r8.keepanno.ast.ParsingContext;
import com.android.tools.r8.keepanno.ast.ParsingContext.AnnotationParsingContext;
import com.android.tools.r8.keepanno.ast.ParsingContext.ClassParsingContext;
import com.android.tools.r8.keepanno.ast.ParsingContext.FieldParsingContext;
import com.android.tools.r8.keepanno.ast.ParsingContext.MethodParsingContext;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
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

  public static List<KeepDeclaration> readKeepEdges(byte[] classFileBytes) {
    ClassReader reader = new ClassReader(classFileBytes);
    List<KeepDeclaration> declarations = new ArrayList<>();
    reader.accept(new KeepEdgeClassVisitor(declarations::add), ClassReader.SKIP_CODE);
    return declarations;
  }

  private static KeepClassItemReference classReferenceFromName(String className) {
    return KeepClassItemReference.fromClassNamePattern(
        KeepQualifiedClassNamePattern.exact(className));
  }

  private static KeepOptions getKeepOptionsOrDefault(KeepOptions options) {
    // TODO(b/248408342): These should be constraints computed/filtered based on the item type but
    //   currently the constraints default to the same set of options.
    if (options != null) {
      return options;
    }
    return KeepOptions.disallowBuilder()
        // LOOKUP (same for any type of item).
        .add(KeepOption.SHRINKING)
        // NAME (same for any type of item).
        .add(KeepOption.OBFUSCATING)
        // CLASS_INSTANTIATE / METHOD_INVOKE / FIELD_GET & FIELD_SET:
        .add(KeepOption.OPTIMIZING)
        .add(KeepOption.ACCESS_MODIFICATION)
        // ACCESS_ALLOW - currently no options needed.
        .build();
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
    private final Parent<KeepDeclaration> parent;
    private String className;
    private ClassParsingContext parsingContext;

    KeepEdgeClassVisitor(Parent<KeepDeclaration> parent) {
      super(ASM_VERSION);
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
      parsingContext = new ClassParsingContext(className);
    }

    private AnnotationParsingContext annotationParsingContext(String descriptor) {
      return parsingContext.annotation(descriptor);
    }

    @Override
    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
      // Skip any visible annotations as @KeepEdge is not runtime visible.
      if (visible) {
        return null;
      }
      if (descriptor.equals(Edge.DESCRIPTOR)) {
        return new KeepEdgeVisitor(
            annotationParsingContext(descriptor), parent::accept, this::setContext);
      }
      if (descriptor.equals(AnnotationConstants.UsesReflection.DESCRIPTOR)) {
        KeepClassItemPattern classItem =
            KeepClassItemPattern.builder()
                .setClassNamePattern(KeepQualifiedClassNamePattern.exact(className))
                .build();
        return new UsesReflectionVisitor(
            annotationParsingContext(descriptor), parent::accept, this::setContext, classItem);
      }
      if (descriptor.equals(AnnotationConstants.ForApi.DESCRIPTOR)) {
        return new ForApiClassVisitor(
            annotationParsingContext(descriptor), parent::accept, this::setContext, className);
      }
      if (descriptor.equals(AnnotationConstants.UsedByReflection.DESCRIPTOR)
          || descriptor.equals(AnnotationConstants.UsedByNative.DESCRIPTOR)) {
        return new UsedByReflectionClassVisitor(
            annotationParsingContext(descriptor),
            parent::accept,
            this::setContext,
            className);
      }
      if (descriptor.equals(AnnotationConstants.CheckRemoved.DESCRIPTOR)) {
        return new CheckRemovedClassVisitor(
            annotationParsingContext(descriptor),
            parent::accept,
            this::setContext,
            className,
            KeepCheckKind.REMOVED);
      }
      if (descriptor.equals(AnnotationConstants.CheckOptimizedOut.DESCRIPTOR)) {
        return new CheckRemovedClassVisitor(
            annotationParsingContext(descriptor),
            parent::accept,
            this::setContext,
            className,
            KeepCheckKind.OPTIMIZED_OUT);
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
      return new KeepEdgeMethodVisitor(parsingContext, parent::accept, className, name, descriptor);
    }

    @Override
    public FieldVisitor visitField(
        int access, String name, String descriptor, String signature, Object value) {
      return new KeepEdgeFieldVisitor(parsingContext, parent::accept, className, name, descriptor);
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

    private KeepMemberItemPattern createMethodItemContext() {
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
          .setClassReference(classReferenceFromName(className))
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
      // Skip any visible annotations as @KeepEdge is not runtime visible.
      if (visible) {
        return null;
      }
      if (descriptor.equals(Edge.DESCRIPTOR)) {
        return new KeepEdgeVisitor(
            annotationParsingContext(descriptor), parent::accept, this::setContext);
      }
      if (descriptor.equals(AnnotationConstants.UsesReflection.DESCRIPTOR)) {
        return new UsesReflectionVisitor(
            annotationParsingContext(descriptor),
            parent::accept,
            this::setContext,
            createMethodItemContext());
      }
      if (descriptor.equals(AnnotationConstants.ForApi.DESCRIPTOR)) {
        return new ForApiMemberVisitor(
            annotationParsingContext(descriptor),
            parent::accept,
            this::setContext,
            createMethodItemContext());
      }
      if (descriptor.equals(AnnotationConstants.UsedByReflection.DESCRIPTOR)
          || descriptor.equals(AnnotationConstants.UsedByNative.DESCRIPTOR)) {
        return new UsedByReflectionMemberVisitor(
            annotationParsingContext(descriptor),
            parent::accept,
            this::setContext,
            createMethodItemContext());
      }
      if (descriptor.equals(AnnotationConstants.CheckRemoved.DESCRIPTOR)) {
        return new CheckRemovedMemberVisitor(
            annotationParsingContext(descriptor),
            parent::accept,
            this::setContext,
            createMethodItemContext(),
            KeepCheckKind.REMOVED);
      }
      if (descriptor.equals(AnnotationConstants.CheckOptimizedOut.DESCRIPTOR)) {
        return new CheckRemovedMemberVisitor(
            annotationParsingContext(descriptor),
            parent::accept,
            this::setContext,
            createMethodItemContext(),
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
    private final String fieldDescriptor;
    private final FieldParsingContext parsingContext;

    KeepEdgeFieldVisitor(
        ClassParsingContext classParsingContext,
        Parent<KeepEdge> parent,
        String className,
        String fieldName,
        String fieldDescriptor) {
      super(ASM_VERSION);
      this.parent = parent;
      this.className = className;
      this.fieldName = fieldName;
      this.fieldDescriptor = fieldDescriptor;
      this.parsingContext =
          new FieldParsingContext(classParsingContext, fieldName, fieldDescriptor);
    }

    private AnnotationParsingContext annotationParsingContext(String descriptor) {
      return parsingContext.annotation(descriptor);
    }

    private KeepMemberItemPattern createMemberItemContext() {
      KeepFieldTypePattern typePattern =
          KeepFieldTypePattern.fromType(KeepTypePattern.fromDescriptor(fieldDescriptor));
      return KeepMemberItemPattern.builder()
          .setClassReference(classReferenceFromName(className))
          .setMemberPattern(
              KeepFieldPattern.builder()
                  .setNamePattern(KeepFieldNamePattern.exact(fieldName))
                  .setTypePattern(typePattern)
                  .build())
          .build();
    }

    private void setContext(KeepEdgeMetaInfo.Builder builder) {
      builder.setContextFromFieldDescriptor(
          KeepEdgeReaderUtils.getDescriptorFromJavaType(className), fieldName, fieldDescriptor);
    }

    @Override
    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
      // Skip any visible annotations as @KeepEdge is not runtime visible.
      if (visible) {
        return null;
      }
      if (descriptor.equals(Edge.DESCRIPTOR)) {
        return new KeepEdgeVisitor(annotationParsingContext(descriptor), parent, this::setContext);
      }
      if (descriptor.equals(AnnotationConstants.UsesReflection.DESCRIPTOR)) {
        return new UsesReflectionVisitor(
            annotationParsingContext(descriptor),
            parent,
            this::setContext,
            createMemberItemContext());
      }
      if (descriptor.equals(AnnotationConstants.ForApi.DESCRIPTOR)) {
        return new ForApiMemberVisitor(
            annotationParsingContext(descriptor),
            parent,
            this::setContext,
            createMemberItemContext());
      }
      if (descriptor.equals(AnnotationConstants.UsedByReflection.DESCRIPTOR)
          || descriptor.equals(AnnotationConstants.UsedByNative.DESCRIPTOR)) {
        return new UsedByReflectionMemberVisitor(
            annotationParsingContext(descriptor),
            parent,
            this::setContext,
            createMemberItemContext());
      }
      return null;
    }
  }

  // Interface for providing AST result(s) for a sub-tree back up to its parent.
  public interface Parent<T> {
    void accept(T result);
  }

  private static class UserBindingsHelper {
    private final KeepBindings.Builder builder = KeepBindings.builder();
    private final Map<String, KeepBindingSymbol> userNames = new HashMap<>();

    public KeepBindingSymbol resolveUserBinding(String name) {
      return userNames.computeIfAbsent(name, builder::create);
    }

    public void defineUserBinding(String name, KeepItemPattern item) {
      builder.addBinding(resolveUserBinding(name), item);
    }

    public KeepBindingSymbol defineFreshBinding(String name, KeepItemPattern item) {
      KeepBindingSymbol symbol = builder.generateFreshSymbol(name);
      builder.addBinding(symbol, item);
      return symbol;
    }

    public KeepItemPattern getItemForBinding(KeepBindingReference bindingReference) {
      return builder.getItemForBinding(bindingReference.getName());
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
      if (name.equals(Edge.bindings)) {
        return new KeepBindingsVisitor(parsingContext, bindingsHelper);
      }
      if (name.equals(Edge.preconditions)) {
        return new KeepPreconditionsVisitor(
            parsingContext, builder::setPreconditions, bindingsHelper);
      }
      if (name.equals(Edge.consequences)) {
        return new KeepConsequencesVisitor(
            parsingContext, builder::setConsequences, bindingsHelper);
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
      visitEnum(null, Kind.DESCRIPTOR, Kind.CLASS_AND_MEMBERS);
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
            parsingContext,
            additionalConsequences -> {
              additionalConsequences.forEachTarget(consequences::addTarget);
            },
            bindingsHelper);
      }
      return super.visitArray(name);
    }

    @Override
    public void visitEnd() {
      if (!getKind().isOnlyClass() && isDefaultMemberDeclaration()) {
        // If no member declarations have been made, set public & protected as the default.
        AnnotationVisitor v = visitArray(Item.memberAccess);
        v.visitEnum(null, MemberAccess.DESCRIPTOR, MemberAccess.PUBLIC);
        v.visitEnum(null, MemberAccess.DESCRIPTOR, MemberAccess.PROTECTED);
      }
      super.visitEnd();
      Collection<KeepItemReference> items = getItemsWithoutBinding();
      for (KeepItemReference item : items) {
        if (item.isBindingReference()) {
          throw parsingContext.error("cannot reference bindings");
        }
        KeepClassItemPattern classItemPattern = item.asClassItemPattern();
        if (classItemPattern == null) {
          assert item.isMemberItemReference();
          classItemPattern = item.asMemberItemPattern().getClassReference().asClassItemPattern();
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
        consequences.addTarget(KeepTarget.builder().setItemReference(item).build());
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
        KeepMemberItemPattern context) {
      super(parsingContext);
      this.parsingContext = parsingContext;
      this.parent = parent;
      addContext.accept(metaInfoBuilder);
      // Create a binding for the context such that the class and member are shared.
      KeepClassItemPattern classContext = context.getClassReference().asClassItemPattern();
      KeepBindingSymbol bindingSymbol = bindingsHelper.defineFreshBinding("CONTEXT", classContext);
      KeepClassItemReference classReference =
          KeepBindingReference.forClass(bindingSymbol).toClassItemReference();
      consequences.addTarget(
          KeepTarget.builder()
              .setItemPattern(
                  KeepMemberItemPattern.builder()
                      .copyFrom(context)
                      .setClassReference(classReference)
                      .build())
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
            parsingContext,
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
    private final OptionsParser optionsParser;

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
      // The class context/holder is the annotated class.
      visit(Item.className, className);
      optionsParser = new OptionsParser(parsingContext);
      optionsParser.setProperty(Target.constraints, OptionsProperty.CONSTRAINTS);
      optionsParser.setProperty(Target.allow, OptionsProperty.ALLOW);
      optionsParser.setProperty(Target.disallow, OptionsProperty.DISALLOW);
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
      if (name.equals(Edge.preconditions)) {
        return new KeepPreconditionsVisitor(
            parsingContext, builder::setPreconditions, bindingsHelper);
      }
      if (name.equals(UsedByReflection.additionalTargets)) {
        return new KeepConsequencesVisitor(
            parsingContext,
            additionalConsequences -> {
              additionalConsequences.forEachTarget(consequences::addTarget);
            },
            bindingsHelper);
      }
      AnnotationVisitor visitor = optionsParser.tryParseArray(name, unused -> {});
      if (visitor != null) {
        return visitor;
      }
      return super.visitArray(name);
    }

    @Override
    public void visitEnd() {
      if (getKind() == null && !isDefaultMemberDeclaration()) {
        // If no explict kind is set and member declarations have been made, keep the class too.
        visitEnum(null, Kind.DESCRIPTOR, Kind.CLASS_AND_MEMBERS);
      }
      super.visitEnd();
      Collection<KeepItemReference> items = getItemsWithoutBinding();
      for (KeepItemReference item : items) {
        if (item.isBindingReference()) {
          // TODO(b/248408342): The edge can have preconditions so it should support bindings!
          throw parsingContext.error("cannot reference bindings");
        }
        KeepItemPattern itemPattern = item.asItemPattern();
        KeepClassItemPattern holderPattern =
            itemPattern.isClassItemPattern()
                ? itemPattern.asClassItemPattern()
                : itemPattern.asMemberItemPattern().getClassReference().asClassItemPattern();
        String descriptor = KeepEdgeReaderUtils.getDescriptorFromClassTypeName(className);
        String itemDescriptor = holderPattern.getClassNamePattern().getExactDescriptor();
        if (!descriptor.equals(itemDescriptor)) {
          throw parsingContext.error("must reference its class context " + className);
        }
        if (!holderPattern.getInstanceOfPattern().isAny()) {
          throw parsingContext.error("cannot define an 'extends' pattern.");
        }
        consequences.addTarget(
            KeepTarget.builder()
                .setItemPattern(itemPattern)
                .setOptions(getKeepOptionsOrDefault(optionsParser.getValue()))
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
   * Parsing of @UsedByReflection or @UsedByNative on a member context.
   *
   * <p>When used on a member context the annotation does not allow member related patterns.
   */
  private static class UsedByReflectionMemberVisitor extends AnnotationVisitorBase {

    private final ParsingContext parsingContext;
    private final Parent<KeepEdge> parent;
    private final KeepItemPattern context;
    private final KeepEdge.Builder builder = KeepEdge.builder();
    private final KeepEdgeMetaInfo.Builder metaInfoBuilder = KeepEdgeMetaInfo.builder();
    private final UserBindingsHelper bindingsHelper = new UserBindingsHelper();
    private final KeepConsequences.Builder consequences = KeepConsequences.builder();
    private ItemKind kind = KeepEdgeReader.ItemKind.ONLY_MEMBERS;
    private final OptionsParser optionsParser;

    UsedByReflectionMemberVisitor(
        AnnotationParsingContext parsingContext,
        Parent<KeepEdge> parent,
        Consumer<KeepEdgeMetaInfo.Builder> addContext,
        KeepItemPattern context) {
      super(parsingContext);
      this.parsingContext = parsingContext;
      this.parent = parent;
      this.context = context;
      addContext.accept(metaInfoBuilder);
      optionsParser = new OptionsParser(parsingContext);
      optionsParser.setProperty(Target.constraints, OptionsProperty.CONSTRAINTS);
      optionsParser.setProperty(Target.allow, OptionsProperty.ALLOW);
      optionsParser.setProperty(Target.disallow, OptionsProperty.DISALLOW);
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
      if (!descriptor.equals(AnnotationConstants.Kind.DESCRIPTOR)) {
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
      if (name.equals(Edge.preconditions)) {
        return new KeepPreconditionsVisitor(
            parsingContext, builder::setPreconditions, bindingsHelper);
      }
      if (name.equals(UsedByReflection.additionalTargets)) {
        return new KeepConsequencesVisitor(
            parsingContext,
            additionalConsequences -> {
              additionalConsequences.forEachTarget(consequences::addTarget);
            },
            bindingsHelper);
      }
      AnnotationVisitor visitor = optionsParser.tryParseArray(name, unused -> {});
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
      assert context.isMemberItemPattern();
      KeepMemberItemPattern memberContext = context.asMemberItemPattern();
      if (kind.includesClass()) {
        consequences.addTarget(
            KeepTarget.builder().setItemReference(memberContext.getClassReference()).build());
      }
      validateConsistentKind(memberContext.getMemberPattern());
      consequences.addTarget(
          KeepTarget.builder()
              .setOptions(getKeepOptionsOrDefault(optionsParser.getValue()))
              .setItemPattern(context)
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
        KeepItemPattern context) {
      super(parsingContext);
      this.parsingContext = parsingContext;
      this.parent = parent;
      preconditions.addCondition(KeepCondition.builder().setItemPattern(context).build());
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
      if (name.equals(AnnotationConstants.UsesReflection.value)) {
        return new KeepConsequencesVisitor(
            parsingContext, builder::setConsequences, bindingsHelper);
      }
      if (name.equals(AnnotationConstants.UsesReflection.additionalPreconditions)) {
        return new KeepPreconditionsVisitor(
            parsingContext,
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

  private static class KeepBindingsVisitor extends AnnotationVisitorBase {
    private final ParsingContext parsingContext;
    private final UserBindingsHelper helper;

    public KeepBindingsVisitor(ParsingContext parsingContext, UserBindingsHelper helper) {
      super(parsingContext);
      this.parsingContext = parsingContext;
      this.helper = helper;
    }

    @Override
    public AnnotationVisitor visitAnnotation(String name, String descriptor) {
      if (descriptor.equals(AnnotationConstants.Binding.DESCRIPTOR)) {
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
        ParsingContext parsingContext,
        Parent<KeepPreconditions> parent,
        UserBindingsHelper bindingsHelper) {
      super(parsingContext);
      this.parsingContext = parsingContext;
      this.parent = parent;
      this.bindingsHelper = bindingsHelper;
    }

    @Override
    public AnnotationVisitor visitAnnotation(String name, String descriptor) {
      if (descriptor.equals(Condition.DESCRIPTOR)) {
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
        ParsingContext parsingContext,
        Parent<KeepConsequences> parent,
        UserBindingsHelper bindingsHelper) {
      super(parsingContext);
      this.parsingContext = parsingContext;
      this.parent = parent;
      this.bindingsHelper = bindingsHelper;
    }

    @Override
    public AnnotationVisitor visitAnnotation(String name, String descriptor) {
      if (descriptor.equals(Target.DESCRIPTOR)) {
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
      KeepItemVisitorBase itemVisitor =
          new KeepItemVisitorBase(parsingContext) {
            @Override
            public UserBindingsHelper getBindingsHelper() {
              throw parsingContext.error("bindings not supported");
            }
          };
      itemVisitor.visit(Item.className, className);
      itemVisitor.visitEnd();
      parent.accept(
          KeepCheck.builder()
              .setMetaInfo(metaInfoBuilder.build())
              .setKind(kind)
              .setItemPattern(itemVisitor.getItemReference().asItemPattern())
              .build());
    }
  }

  /** Parsing of @CheckRemoved and @CheckOptimizedOut on a class context. */
  private static class CheckRemovedMemberVisitor extends AnnotationVisitorBase {

    private final Parent<KeepDeclaration> parent;
    private final KeepItemPattern context;
    private final KeepEdgeMetaInfo.Builder metaInfoBuilder = KeepEdgeMetaInfo.builder();
    private final KeepCheckKind kind;

    CheckRemovedMemberVisitor(
        AnnotationParsingContext parsingContext,
        Parent<KeepDeclaration> parent,
        Consumer<KeepEdgeMetaInfo.Builder> addContext,
        KeepItemPattern context,
        KeepCheckKind kind) {
      super(parsingContext);
      this.parent = parent;
      this.context = context;
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
      parent.accept(
          KeepCheck.builder()
              .setMetaInfo(metaInfoBuilder.build())
              .setKind(kind)
              .setItemPattern(context)
              .build());
    }
  }

  abstract static class Declaration {

    boolean isDefault() {
      for (Declaration declaration : declarations()) {
        if (!declaration.isDefault()) {
          return false;
        }
      }
      return true;
    }

    List<Declaration> declarations() {
      return Collections.emptyList();
    }

    List<PropertyParser<?, ?>> parsers() {
      return Collections.emptyList();
    }

    private void ignore(Object arg) {}

    boolean tryParse(String name, Object value) {
      for (Declaration declaration : declarations()) {
        if (declaration.tryParse(name, value)) {
          return true;
        }
      }
      for (PropertyParser<?, ?> parser : parsers()) {
        if (parser.tryParse(name, value, this::ignore)) {
          return true;
        }
      }
      return false;
    }

    AnnotationVisitor tryParseArray(String name) {
      for (Declaration declaration : declarations()) {
        AnnotationVisitor visitor = declaration.tryParseArray(name);
        if (visitor != null) {
          return visitor;
        }
      }
      for (PropertyParser<?, ?> parser : parsers()) {
        AnnotationVisitor visitor = parser.tryParseArray(name, this::ignore);
        if (visitor != null) {
          return visitor;
        }
      }
      return null;
    }

    AnnotationVisitor tryParseAnnotation(String name, String descriptor) {
      for (Declaration declaration : declarations()) {
        AnnotationVisitor visitor = declaration.tryParseAnnotation(name, descriptor);
        if (visitor != null) {
          return visitor;
        }
      }
      for (PropertyParser<?, ?> parser : parsers()) {
        AnnotationVisitor visitor = parser.tryParseAnnotation(name, descriptor, this::ignore);
        if (visitor != null) {
          return visitor;
        }
      }
      return null;
    }
  }

  private static class ClassDeclaration extends Declaration {

    private final ParsingContext parsingContext;
    private final Supplier<UserBindingsHelper> getBindingsHelper;

    private KeepClassItemReference boundClassItemReference = null;
    private final ClassNameParser classNameParser;
    private final ClassNameParser annotatedByParser;
    private final InstanceOfParser instanceOfParser;
    private final List<PropertyParser<?, ?>> parsers;

    public ClassDeclaration(
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
      instanceOfParser.setProperty(Item.instanceOfClassName, InstanceOfProperties.NAME);
      instanceOfParser.setProperty(Item.instanceOfClassConstant, InstanceOfProperties.CONSTANT);
      instanceOfParser.setProperty(
          Item.instanceOfClassNameExclusive, InstanceOfProperties.NAME_EXCL);
      instanceOfParser.setProperty(
          Item.instanceOfClassConstantExclusive, InstanceOfProperties.CONSTANT_EXCL);
      instanceOfParser.setProperty(Item.extendsClassName, InstanceOfProperties.NAME_EXCL);
      instanceOfParser.setProperty(Item.extendsClassConstant, InstanceOfProperties.CONSTANT_EXCL);

      parsers = ImmutableList.of(classNameParser, annotatedByParser, instanceOfParser);
    }

    @Override
    public List<PropertyParser<?, ?>> parsers() {
      return parsers;
    }

    private boolean isBindingReferenceDefined() {
      return boundClassItemReference != null;
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
    boolean isDefault() {
      return !isBindingReferenceDefined() && super.isDefault();
    }

    private KeepClassItemReference getValue() {
      checkAllowedDefinitions();
      if (isBindingReferenceDefined()) {
        return boundClassItemReference;
      }
      if (classPatternsAreDeclared()) {
        return KeepClassItemPattern.builder()
            .setClassNamePattern(
                classNameParser.getValueOrDefault(KeepQualifiedClassNamePattern.any()))
            .setAnnotatedByPattern(
                annotatedByParser.getValueOrDefault(KeepQualifiedClassNamePattern.any()))
            .setInstanceOfPattern(instanceOfParser.getValueOrDefault(KeepInstanceOfPattern.any()))
            .build()
            .toClassItemReference();
      }
      assert isDefault();
      return KeepClassItemPattern.any().toClassItemReference();
    }

    public void setBindingReference(KeepClassItemReference bindingReference) {
      if (isBindingReferenceDefined()) {
        throw parsingContext.error(
            "Cannot reference multiple class bindings for a single class item");
      }
      this.boundClassItemReference = bindingReference;
    }

    @Override
    boolean tryParse(String name, Object value) {
      if (name.equals(Item.classFromBinding) && value instanceof String) {
        KeepBindingSymbol symbol = getBindingsHelper.get().resolveUserBinding((String) value);
        setBindingReference(KeepBindingReference.forClass(symbol).toClassItemReference());
        return true;
      }
      return super.tryParse(name, value);
    }
  }

  private static class MethodDeclaration extends Declaration {

    private final ParsingContext parsingContext;
    private KeepMethodAccessPattern.Builder accessBuilder = null;
    private KeepMethodPattern.Builder builder = null;
    private final StringPatternParser nameParser;
    private final MethodReturnTypeParser returnTypeParser;
    private final MethodParametersParser parametersParser;

    private final List<PropertyParser<?, ?>> parsers;

    private MethodDeclaration(ParsingContext parsingContext) {
      this.parsingContext = parsingContext;

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

      parsers = ImmutableList.of(nameParser, returnTypeParser, parametersParser);
    }

    @Override
    List<PropertyParser<?, ?>> parsers() {
      return parsers;
    }

    private KeepMethodPattern.Builder getBuilder() {
      if (builder == null) {
        builder = KeepMethodPattern.builder();
      }
      return builder;
    }

    @Override
    boolean isDefault() {
      return accessBuilder == null && builder == null && super.isDefault();
    }

    private KeepMethodPattern getValue() {
      if (accessBuilder != null) {
        getBuilder().setAccessPattern(accessBuilder.build());
      }
      if (!nameParser.isDefault()) {
        KeepStringPattern namePattern = nameParser.getValue();
        getBuilder().setNamePattern(KeepMethodNamePattern.fromStringPattern(namePattern));
      }
      if (!returnTypeParser.isDefault()) {
        getBuilder().setReturnTypePattern(returnTypeParser.getValue());
      }
      if (!parametersParser.isDefault()) {
        getBuilder().setParametersPattern(parametersParser.getValue());
      }
      return builder != null ? builder.build() : null;
    }

    @Override
    AnnotationVisitor tryParseArray(String name) {
      if (name.equals(Item.methodAccess)) {
        accessBuilder = KeepMethodAccessPattern.builder();
        return new MethodAccessVisitor(parsingContext, accessBuilder);
      }
      return super.tryParseArray(name);
    }
  }

  private static class FieldDeclaration extends Declaration {

    private final ParsingContext parsingContext;
    private final StringPatternParser nameParser;
    private final FieldTypeParser typeParser;
    private KeepFieldAccessPattern.Builder accessBuilder = null;
    private KeepFieldPattern.Builder builder = null;
    private final List<PropertyParser<?, ?>> parsers;

    public FieldDeclaration(ParsingContext parsingContext) {
      this.parsingContext = parsingContext;
      nameParser = new StringPatternParser(parsingContext.group(Item.fieldNameGroup));
      nameParser.setProperty(Item.fieldName, StringProperty.EXACT);
      nameParser.setProperty(Item.fieldNamePattern, StringProperty.PATTERN);

      typeParser = new FieldTypeParser(parsingContext.group(Item.fieldTypeGroup));
      typeParser.setProperty(Item.fieldTypePattern, TypeProperty.TYPE_PATTERN);
      typeParser.setProperty(Item.fieldType, TypeProperty.TYPE_NAME);
      typeParser.setProperty(Item.fieldTypeConstant, TypeProperty.TYPE_CONSTANT);

      parsers = ImmutableList.of(nameParser, typeParser);
    }

    @Override
    List<PropertyParser<?, ?>> parsers() {
      return parsers;
    }

    private KeepFieldPattern.Builder getBuilder() {
      if (builder == null) {
        builder = KeepFieldPattern.builder();
      }
      return builder;
    }

    @Override
    boolean isDefault() {
      return accessBuilder == null && builder == null;
    }

    private KeepFieldPattern getValue() {
      if (accessBuilder != null) {
        getBuilder().setAccessPattern(accessBuilder.build());
      }
      if (!nameParser.isDefault()) {
        getBuilder().setNamePattern(KeepFieldNamePattern.fromStringPattern(nameParser.getValue()));
      }
      if (!typeParser.isDefault()) {
        getBuilder().setTypePattern(typeParser.getValue());
      }
      return builder != null ? builder.build() : null;
    }

    @Override
    AnnotationVisitor tryParseArray(String name) {
      if (name.equals(Item.fieldAccess)) {
        accessBuilder = KeepFieldAccessPattern.builder();
        return new FieldAccessVisitor(parsingContext, accessBuilder);
      }
      return super.tryParseArray(name);
    }
  }

  private static class MemberDeclaration extends Declaration {

    private final ParsingContext parsingContext;
    private KeepMemberAccessPattern.Builder accessBuilder = null;
    private final MethodDeclaration methodDeclaration;
    private final FieldDeclaration fieldDeclaration;
    private final List<Declaration> declarations;

    MemberDeclaration(ParsingContext parsingContext) {
      this.parsingContext = parsingContext.group(Item.memberGroup);
      methodDeclaration = new MethodDeclaration(parsingContext);
      fieldDeclaration = new FieldDeclaration(parsingContext);
      declarations = ImmutableList.of(methodDeclaration, fieldDeclaration);
    }

    @Override
    List<Declaration> declarations() {
      return declarations;
    }

    @Override
    public boolean isDefault() {
      return accessBuilder == null && methodDeclaration.isDefault() && fieldDeclaration.isDefault();
    }

    private KeepMemberPattern getValue() {
      KeepMethodPattern method = methodDeclaration.getValue();
      KeepFieldPattern field = fieldDeclaration.getValue();
      if (accessBuilder != null) {
        if (method != null || field != null) {
          throw parsingContext.error(
              "Cannot define common member access as well as field or method pattern");
        }
        return KeepMemberPattern.memberBuilder().setAccessPattern(accessBuilder.build()).build();
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
      return KeepMemberPattern.none();
    }

    @Override
    AnnotationVisitor tryParseArray(String name) {
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
    private final ClassDeclaration classDeclaration;
    private final MemberDeclaration memberDeclaration;


    public abstract UserBindingsHelper getBindingsHelper();

    // Constructed item available once visitEnd has been called.
    private KeepItemReference itemReference = null;

    KeepItemVisitorBase(ParsingContext parsingContext) {
      super(parsingContext);
      this.parsingContext = parsingContext;
      classDeclaration = new ClassDeclaration(parsingContext, this::getBindingsHelper);
      memberDeclaration = new MemberDeclaration(parsingContext);
    }

    public Collection<KeepItemReference> getItemsWithoutBinding() {
      if (itemReference == null) {
        throw parsingContext.error("Item reference not finalized. Missing call to visitEnd()");
      }
      if (itemReference.isBindingReference()) {
        return Collections.singletonList(itemReference);
      }
      // Kind is only null if item is a "binding reference".
      if (kind == null) {
        throw parsingContext.error("Unexpected state: unknown kind for an item pattern");
      }
      if (kind.includesClassAndMembers()) {
        assert !itemReference.isBindingReference();
        KeepItemPattern itemPattern = itemReference.asItemPattern();
        KeepClassItemReference classReference;
        KeepMemberItemPattern memberPattern;
        if (itemPattern.isClassItemPattern()) {
          classReference = itemPattern.asClassItemPattern().toClassItemReference();
          memberPattern =
              KeepMemberItemPattern.builder()
                  .setClassReference(classReference)
                  .setMemberPattern(KeepMemberPattern.allMembers())
                  .build();
        } else {
          memberPattern = itemPattern.asMemberItemPattern();
          classReference = memberPattern.getClassReference();
        }
        return ImmutableList.of(classReference, memberPattern.toItemReference());
      } else {
        return Collections.singletonList(itemReference);
      }
    }

    public Collection<KeepItemReference> getItemsWithBinding() {
      if (itemReference == null) {
        throw parsingContext.error("Item reference not finalized. Missing call to visitEnd()");
      }
      if (itemReference.isBindingReference()) {
        return Collections.singletonList(itemReference);
      }
      // Kind is only null if item is a "binding reference".
      if (kind == null) {
        throw parsingContext.error("Unexpected state: unknown kind for an item pattern");
      }
      if (kind.includesClassAndMembers()) {
        KeepItemPattern itemPattern = itemReference.asItemPattern();
        // Ensure we have a member item linked to the correct class.
        KeepMemberItemPattern memberItemPattern;
        if (itemPattern.isClassItemPattern()) {
          memberItemPattern =
              KeepMemberItemPattern.builder()
                  .setClassReference(itemPattern.asClassItemPattern().toClassItemReference())
                  .build();
        } else {
          memberItemPattern = itemPattern.asMemberItemPattern();
        }
        // If the class is not a binding, introduce the binding and rewrite the member.
        KeepClassItemReference classItemReference = memberItemPattern.getClassReference();
        if (classItemReference.isClassItemPattern()) {
          KeepClassItemPattern classItemPattern = classItemReference.asClassItemPattern();
          KeepBindingSymbol symbol =
              getBindingsHelper().defineFreshBinding("CLASS", classItemPattern);
          classItemReference = KeepBindingReference.forClass(symbol).toClassItemReference();
          memberItemPattern =
              KeepMemberItemPattern.builder()
                  .copyFrom(memberItemPattern)
                  .setClassReference(classItemReference)
                  .build();
        }
        assert classItemReference.isBindingReference();
        assert memberItemPattern.getClassReference().equals(classItemReference);
        return ImmutableList.of(classItemReference, memberItemPattern.toItemReference());
      } else {
        return Collections.singletonList(itemReference);
      }
    }

    public KeepItemReference getItemReference() {
      if (itemReference == null) {
        throw parsingContext.error("Item reference not finalized. Missing call to visitEnd()");
      }
      return itemReference;
    }

    public ItemKind getKind() {
      return kind;
    }

    public boolean isDefaultMemberDeclaration() {
      return memberDeclaration.isDefault();
    }

    @Override
    public void visitEnum(String name, String descriptor, String value) {
      if (!descriptor.equals(AnnotationConstants.Kind.DESCRIPTOR)) {
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

    @Override
    public void visitEnd() {
      if (memberBindingReference != null) {
        if (!classDeclaration.isDefault()
            || !memberDeclaration.getValue().isNone()
            || kind != null) {
          throw parsingContext.error(
              "Cannot define an item explicitly and via a member-binding reference");
        }
        KeepBindingSymbol symbol = getBindingsHelper().resolveUserBinding(memberBindingReference);
        itemReference = KeepBindingReference.forMember(symbol).toItemReference();
      } else {
        KeepMemberPattern memberPattern = memberDeclaration.getValue();
        // If no explicit kind is set, extract it based on the member pattern.
        if (kind == null) {
          if (memberPattern.isMethod()) {
            kind = ItemKind.ONLY_METHODS;
          } else if (memberPattern.isField()) {
            kind = ItemKind.ONLY_FIELDS;
          } else if (memberPattern.isGeneralMember()) {
            kind = ItemKind.ONLY_MEMBERS;
          } else {
            assert memberPattern.isNone();
            kind = ItemKind.ONLY_CLASS;
          }
        }

        if (kind.isOnlyClass() && !memberPattern.isNone()) {
          throw parsingContext.error("Item pattern for members is incompatible with kind " + kind);
        }

        // Refine the member pattern to be as precise as the specified kind.
        if (kind.requiresMethods() && !memberPattern.isMethod()) {
          if (memberPattern.isGeneralMember()) {
            memberPattern =
                KeepMethodPattern.builder()
                    .setAccessPattern(
                        KeepMethodAccessPattern.builder()
                            .copyOfMemberAccess(memberPattern.getAccessPattern())
                            .build())
                    .build();
          } else if (memberPattern.isNone()) {
            memberPattern = KeepMethodPattern.allMethods();
          } else {
            assert memberPattern.isField();
            throw parsingContext.error("Item pattern for fields is incompatible with kind " + kind);
          }
        }

        if (kind.requiresFields() && !memberPattern.isField()) {
          if (memberPattern.isGeneralMember()) {
            memberPattern =
                KeepFieldPattern.builder()
                    .setAccessPattern(
                        KeepFieldAccessPattern.builder()
                            .copyOfMemberAccess(memberPattern.getAccessPattern())
                            .build())
                    .build();
          } else if (memberPattern.isNone()) {
            memberPattern = KeepFieldPattern.allFields();
          } else {
            assert memberPattern.isMethod();
            throw parsingContext.error(
                "Item pattern for methods is incompatible with kind " + kind);
          }
        }

        if (kind.requiresMembers() && memberPattern.isNone()) {
          memberPattern = KeepMemberPattern.allMembers();
        }

        KeepClassItemReference classReference = classDeclaration.getValue();
        if (kind.isOnlyClass()) {
          itemReference = classReference;
        } else {
          KeepItemPattern itemPattern =
              KeepMemberItemPattern.builder()
                  .setClassReference(classReference)
                  .setMemberPattern(memberPattern)
                  .build();
          itemReference = itemPattern.toItemReference();
        }
      }
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
      KeepItemReference item = getItemReference();
      // The language currently disallows aliasing bindings, thus a binding cannot directly be
      // defined by a reference to another binding.
      if (item.isBindingReference()) {
        throw parsingContext.error(
            "Invalid binding reference to '"
                + item.asBindingReference()
                + "' in binding definition of '"
                + bindingName
                + "'");
      }
      helper.defineUserBinding(bindingName, item.asItemPattern());
    }
  }

  private static class KeepTargetVisitor extends KeepItemVisitorBase {

    private final Parent<KeepTarget> parent;
    private final UserBindingsHelper bindingsHelper;
    private final OptionsParser optionsParser;
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
      optionsParser = new OptionsParser(parsingContext);
      optionsParser.setProperty(Target.constraints, OptionsProperty.CONSTRAINTS);
      optionsParser.setProperty(Target.allow, OptionsProperty.ALLOW);
      optionsParser.setProperty(Target.disallow, OptionsProperty.DISALLOW);
    }

    @Override
    public UserBindingsHelper getBindingsHelper() {
      return bindingsHelper;
    }

    @Override
    public AnnotationVisitor visitArray(String name) {
      AnnotationVisitor visitor = optionsParser.tryParseArray(name, unused -> {});
      if (visitor != null) {
        return visitor;
      }
      return super.visitArray(name);
    }

    @Override
    public void visitEnd() {
      super.visitEnd();
      builder.setOptions(getKeepOptionsOrDefault(optionsParser.getValue()));
      for (KeepItemReference item : getItemsWithBinding()) {
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
      if (!descriptor.equals(AnnotationConstants.MemberAccess.DESCRIPTOR)) {
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
      if (!descriptor.equals(AnnotationConstants.MethodAccess.DESCRIPTOR)) {
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
        super.visitEnum(ignore, MemberAccess.DESCRIPTOR, value);
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
      if (!descriptor.equals(AnnotationConstants.FieldAccess.DESCRIPTOR)) {
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
        super.visitEnum(ignore, MemberAccess.DESCRIPTOR, value);
      }
    }
  }
}
