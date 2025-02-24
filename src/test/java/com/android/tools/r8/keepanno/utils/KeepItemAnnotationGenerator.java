// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.keepanno.utils;

import static com.android.tools.r8.references.Reference.classFromClass;
import static com.android.tools.r8.references.Reference.classFromTypeName;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;

import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.cfmethodgeneration.CodeGenerationBase;
import com.android.tools.r8.keepanno.annotations.KeepItemKind;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.StringUtils.BraceType;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class KeepItemAnnotationGenerator {

  public static void main(String[] args) throws IOException {
    Generator.class.getClassLoader().setDefaultAssertionStatus(true);
    Generator.run(
        (file, content) -> {
          try {
            Files.write(file, content.getBytes(StandardCharsets.UTF_8), CREATE, TRUNCATE_EXISTING);
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        });
  }

  private static String getHeaderString(int year, String simpleNameOfGenerator) {
    String result =
        StringUtils.lines(
            "/*",
            " * Copyright " + year + " The Android Open Source Project",
            " *",
            " * Licensed under the Apache License, Version 2.0 (the \"License\");",
            " * you may not use this file except in compliance with the License.",
            " * You may obtain a copy of the License at",
            " *",
            " *      http://www.apache.org/licenses/LICENSE-2.0",
            " *",
            " * Unless required by applicable law or agreed to in writing, software",
            " * distributed under the License is distributed on an \"AS IS\" BASIS,",
            " * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.",
            " * See the License for the specific language governing permissions and",
            " * limitations under the License.",
            " */");
    if (simpleNameOfGenerator != null) {
      result +=
          StringUtils.lines(
              "",
              "// ***********************************************************************************",
              "// GENERATED FILE. DO NOT EDIT! See " + simpleNameOfGenerator + ".java.",
              "// ***********************************************************************************");
    }
    result +=
        StringUtils.lines(
            "",
            "// ***********************************************************************************",
            "// MAINTAINED AND TESTED IN THE R8 REPO. PLEASE MAKE CHANGES THERE AND REPLICATE.",
            "// ***********************************************************************************");
    return result;
  }

  public static class EnumReference {
    public final ClassReference enumClass;
    public final String enumValue;

    public EnumReference(ClassReference enumClass, String enumValue) {
      this.enumClass = enumClass;
      this.enumValue = enumValue;
    }

    public String name() {
      return enumValue;
    }
  }

  private static final ClassReference JAVA_STRING = classFromClass(String.class);
  private static final ClassReference JAVA_RETENTION_POLICY = classFromClass(RetentionPolicy.class);

  private static final String AST_PKG = "com.android.tools.r8.keepanno.ast";
  private static final String R8_ANNO_PKG = "com.android.tools.r8.keepanno.annotations";
  private static final String ANDROIDX_ANNO_PKG = "androidx.annotation.keep";

  private static ClassReference astClass(String unqualifiedName) {
    return classFromTypeName(AST_PKG + "." + unqualifiedName);
  }

  public static String quote(String str) {
    return "\"" + str + "\"";
  }

  public static String getUnqualifiedName(ClassReference clazz) {
    String binaryName = clazz.getBinaryName();
    return binaryName.substring(binaryName.lastIndexOf('/') + 1);
  }

  public static class GroupMember extends DocPrinterBase<GroupMember> {

    final String name;
    final String kotlinName;
    String valueType = null;
    String valueDefault = null;

    GroupMember(String name) {
      this(name, name);
    }

    GroupMember(String name, String kotlinName) {
      this.name = name;
      this.kotlinName = kotlinName;
    }

    public GroupMember setType(String type) {
      valueType = type;
      return this;
    }

    public GroupMember setValue(String value) {
      valueDefault = value;
      return this;
    }

    @Override
    public GroupMember self() {
      return this;
    }

    void generate(Generator generator) {
      printDoc(generator::println);
      if (isDeprecated()) {
        generator.println("@Deprecated");
      }
      if (generator.generateKotlin()) {
        if (kotlinValueDefault() == null) {
          generator.println("val " + kotlinName + ": " + kotlinValueType() + ",");
        } else {
          generator.println(
              "val " + kotlinName + ": " + kotlinValueType() + " = " + kotlinValueDefault() + ",");
        }
      } else {
        if (valueDefault == null) {
          generator.println(valueType + " " + name + "();");
        } else {
          generator.println(valueType + " " + name + "() default " + valueDefault + ";");
        }
      }
    }

    private String kotlinValueType() {
      if (valueType.equals("Class<?>")) {
        return "KClass<*>";
      }
      if (valueType.equals("boolean")) {
        return "Boolean";
      }
      if (valueType.endsWith("[]")) {
        return "Array<" + valueType.substring(0, valueType.length() - 2) + ">";
      }
      return valueType;
    }

    private String kotlinValueDefault() {
      if (valueDefault != null) {
        if (valueDefault.equals("Object.class")) {
          return "Object::class";
        }
        if (valueDefault.startsWith("{") && valueDefault.endsWith("}")) {
          return "[" + valueDefault.substring(1, valueDefault.length() - 1) + "]";
        }
      }
      return valueDefault;
    }

    public void generateConstants(Generator generator) {
      generator.println("public static final String " + name + " = " + quote(name) + ";");
    }

    public GroupMember requiredValue(ClassReference type) {
      assert valueDefault == null;
      return setType(getUnqualifiedName(type));
    }

    public GroupMember requiredArrayValue(ClassReference type) {
      assert valueDefault == null;
      return setType(getUnqualifiedName(type) + "[]");
    }

    public GroupMember requiredStringValue() {
      return requiredValue(JAVA_STRING);
    }

    public GroupMember defaultBooleanValue(boolean value) {
      setType("boolean");
      return setValue(value ? "true" : "false");
    }

    public GroupMember defaultValue(ClassReference type, String value) {
      setType(getUnqualifiedName(type));
      return setValue(value);
    }

    public GroupMember defaultArrayValue(ClassReference type, String value) {
      setType(getUnqualifiedName(type) + "[]");
      return setValue("{" + value + "}");
    }

    public GroupMember defaultEmptyString() {
      return defaultValue(JAVA_STRING, quote(""));
    }

    public GroupMember defaultObjectClass() {
      return setType("Class<?>").setValue("Object.class");
    }

    public GroupMember defaultArrayEmpty(ClassReference type) {
      return defaultArrayValue(type, "");
    }
  }

  public static class Group {

    final String name;
    final List<GroupMember> members = new ArrayList<>();
    final List<String> footers = new ArrayList<>();
    final LinkedHashMap<String, Group> mutuallyExclusiveGroups = new LinkedHashMap<>();

    boolean mutuallyExclusiveWithOtherGroups = false;

    private Group(String name) {
      this.name = name;
    }

    Group allowMutuallyExclusiveWithOtherGroups() {
      mutuallyExclusiveWithOtherGroups = true;
      return this;
    }

    Group addMember(GroupMember member) {
      members.add(member);
      return this;
    }

    Group addDocFooterParagraph(String footer) {
      footers.add(footer);
      return this;
    }

    void generate(Generator generator) {
      assert !members.isEmpty();
      for (GroupMember member : members) {
        if (member != members.get(0)) {
          generator.println();
        }
        List<String> mutuallyExclusiveProperties = new ArrayList<>();
        for (GroupMember other : members) {
          if (!member.name.equals(other.name)) {
            mutuallyExclusiveProperties.add(other.name);
          }
        }
        mutuallyExclusiveGroups.forEach(
            (unused, group) -> {
              group.members.forEach(m -> mutuallyExclusiveProperties.add(m.name));
            });
        if (mutuallyExclusiveProperties.size() == 1) {
          member.addParagraph(
              "Mutually exclusive with the property `"
                  + mutuallyExclusiveProperties.get(0)
                  + "` also defining "
                  + name
                  + ".");
        } else if (mutuallyExclusiveProperties.size() > 1) {
          member.addParagraph(
              "Mutually exclusive with the following other properties defining " + name + ":");
          member.addUnorderedList(mutuallyExclusiveProperties);
        }
        footers.forEach(member::addParagraph);
        member.generate(generator);
      }
    }

    void generateConstants(Generator generator) {
      if (mutuallyExclusiveWithOtherGroups || members.size() > 1) {
        StringBuilder camelCaseName = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
          char c = name.charAt(i);
          if (c == '-') {
            c = Character.toUpperCase(name.charAt(++i));
          }
          camelCaseName.append(c);
        }
        generator.println(
            "public static final String " + camelCaseName + "Group = " + quote(name) + ";");
      }
      for (GroupMember member : members) {
        member.generateConstants(generator);
      }
    }

    public void addMutuallyExclusiveGroups(Group... groups) {
      for (Group group : groups) {
        assert mutuallyExclusiveWithOtherGroups || group.mutuallyExclusiveWithOtherGroups;
        mutuallyExclusiveGroups.computeIfAbsent(
            group.name,
            k -> {
              // Mutually exclusive is bidirectional so link in with other group.
              group.mutuallyExclusiveGroups.put(name, this);
              return group;
            });
      }
    }
  }

  public static class Generator {

    final ClassReference ANNOTATION_CONSTANTS;

    final ClassReference STRING_PATTERN;
    final ClassReference TYPE_PATTERN;
    final ClassReference CLASS_NAME_PATTERN;
    final ClassReference INSTANCE_OF_PATTERN;
    final ClassReference ANNOTATION_PATTERN;
    final ClassReference USES_REFLECTION;
    final ClassReference USED_BY_REFLECTION;
    final ClassReference USED_BY_NATIVE;
    final ClassReference CHECK_REMOVED;
    final ClassReference CHECK_OPTIMIZED_OUT;
    final ClassReference KEEP_EDGE;
    final ClassReference KEEP_BINDING;
    final ClassReference KEEP_TARGET;
    final ClassReference KEEP_CONDITION;
    final ClassReference KEEP_FOR_API;

    final ClassReference KEEP_ITEM_KIND;
    final EnumReference KIND_ONLY_CLASS;
    final EnumReference KIND_ONLY_MEMBERS;
    final EnumReference KIND_ONLY_METHODS;
    final EnumReference KIND_ONLY_FIELDS;
    final EnumReference KIND_CLASS_AND_MEMBERS;
    final EnumReference KIND_CLASS_AND_METHODS;
    final EnumReference KIND_CLASS_AND_FIELDS;
    final List<EnumReference> KEEP_ITEM_KIND_VALUES;

    final ClassReference KEEP_CONSTRAINT;
    final EnumReference CONSTRAINT_LOOKUP;
    final EnumReference CONSTRAINT_NAME;
    final EnumReference CONSTRAINT_VISIBILITY_RELAX;
    final EnumReference CONSTRAINT_VISIBILITY_RESTRICT;
    final EnumReference CONSTRAINT_VISIBILITY_INVARIANT;
    final EnumReference CONSTRAINT_CLASS_INSTANTIATE;
    final EnumReference CONSTRAINT_METHOD_INVOKE;
    final EnumReference CONSTRAINT_FIELD_GET;
    final EnumReference CONSTRAINT_FIELD_SET;
    final EnumReference CONSTRAINT_METHOD_REPLACE;
    final EnumReference CONSTRAINT_FIELD_REPLACE;
    final EnumReference CONSTRAINT_NEVER_INLINE;
    final EnumReference CONSTRAINT_CLASS_OPEN_HIERARCHY;
    final EnumReference CONSTRAINT_GENERIC_SIGNATURE;
    final List<EnumReference> KEEP_CONSTRAINT_VALUES;

    final ClassReference MEMBER_ACCESS_FLAGS;
    final EnumReference MEMBER_ACCESS_PUBLIC;
    final EnumReference MEMBER_ACCESS_PROTECTED;
    final EnumReference MEMBER_ACCESS_PACKAGE_PRIVATE;
    final EnumReference MEMBER_ACCESS_PRIVATE;
    final EnumReference MEMBER_ACCESS_STATIC;
    final EnumReference MEMBER_ACCESS_FINAL;
    final EnumReference MEMBER_ACCESS_SYNTHETIC;
    final List<EnumReference> MEMBER_ACCESS_VALUES;

    final ClassReference METHOD_ACCESS_FLAGS;
    final EnumReference METHOD_ACCESS_SYNCHRONIZED;
    final EnumReference METHOD_ACCESS_BRIDGE;
    final EnumReference METHOD_ACCESS_NATIVE;
    final EnumReference METHOD_ACCESS_ABSTRACT;
    final EnumReference METHOD_ACCESS_STRICT_FP;
    final List<EnumReference> METHOD_ACCESS_VALUES;

    final ClassReference FIELD_ACCESS_FLAGS;
    final EnumReference FIELD_ACCESS_VOLATILE;
    final EnumReference FIELD_ACCESS_TRANSIENT;
    final List<EnumReference> FIELD_ACCESS_VALUES;

    final String KOTLIN_DEFAULT_INVALID_STRING_PATTERN;
    final String DEFAULT_INVALID_STRING_PATTERN;
    final String KOTLIN_DEFAULT_INVALID_TYPE_PATTERN;
    final String DEFAULT_INVALID_TYPE_PATTERN;
    final String KOTLIN_DEFAULT_INVALID_CLASS_NAME_PATTERN;
    final String DEFAULT_INVALID_CLASS_NAME_PATTERN;
    final String KOTLIN_DEFAULT_ANY_INSTANCE_OF_PATTERN;
    final String DEFAULT_ANY_INSTANCE_OF_PATTERN;

    final List<Class<?>> ANNOTATION_IMPORTS =
        ImmutableList.of(ElementType.class, Retention.class, RetentionPolicy.class, Target.class);
    final List<Class<?>> KOTLIN_ANNOTATION_IMPORTS =
        ImmutableList.of(kotlin.annotation.Retention.class, kotlin.annotation.Target.class);

    private final PrintStream writer;
    private final String pkg;
    private int indent = 0;

    public Generator(PrintStream writer, String pkg) {
      this.writer = writer;
      this.pkg = pkg;

      ANNOTATION_CONSTANTS = astClass("AnnotationConstants");

      STRING_PATTERN = annoClass("StringPattern");
      TYPE_PATTERN = annoClass("TypePattern");
      CLASS_NAME_PATTERN = annoClass("ClassNamePattern");
      INSTANCE_OF_PATTERN = annoClass("InstanceOfPattern");
      ANNOTATION_PATTERN = annoClass("AnnotationPattern");
      USES_REFLECTION = annoClass("UsesReflection");
      USED_BY_REFLECTION = annoClass("UsedByReflection");
      USED_BY_NATIVE = annoClass("UsedByNative");
      CHECK_REMOVED = annoClass("CheckRemoved");
      CHECK_OPTIMIZED_OUT = annoClass("CheckOptimizedOut");
      KEEP_EDGE = annoClass("KeepEdge");
      KEEP_BINDING = annoClass("KeepBinding");
      KEEP_TARGET = annoClass("KeepTarget");
      KEEP_CONDITION = annoClass("KeepCondition");
      KEEP_FOR_API = annoClass("KeepForApi");

      KEEP_ITEM_KIND = annoClass("KeepItemKind");
      KIND_ONLY_CLASS = enumRef(KEEP_ITEM_KIND, "ONLY_CLASS");
      KIND_ONLY_MEMBERS = enumRef(KEEP_ITEM_KIND, "ONLY_MEMBERS");
      KIND_ONLY_METHODS = enumRef(KEEP_ITEM_KIND, "ONLY_METHODS");
      KIND_ONLY_FIELDS = enumRef(KEEP_ITEM_KIND, "ONLY_FIELDS");
      KIND_CLASS_AND_MEMBERS = enumRef(KEEP_ITEM_KIND, "CLASS_AND_MEMBERS");
      KIND_CLASS_AND_METHODS = enumRef(KEEP_ITEM_KIND, "CLASS_AND_METHODS");
      KIND_CLASS_AND_FIELDS = enumRef(KEEP_ITEM_KIND, "CLASS_AND_FIELDS");
      KEEP_ITEM_KIND_VALUES =
          ImmutableList.of(
              KIND_ONLY_CLASS,
              KIND_ONLY_MEMBERS,
              KIND_ONLY_METHODS,
              KIND_ONLY_FIELDS,
              KIND_CLASS_AND_MEMBERS,
              KIND_CLASS_AND_METHODS,
              KIND_CLASS_AND_FIELDS);

      KEEP_CONSTRAINT = annoClass("KeepConstraint");
      CONSTRAINT_LOOKUP = enumRef(KEEP_CONSTRAINT, "LOOKUP");
      CONSTRAINT_NAME = enumRef(KEEP_CONSTRAINT, "NAME");
      CONSTRAINT_VISIBILITY_RELAX = enumRef(KEEP_CONSTRAINT, "VISIBILITY_RELAX");
      CONSTRAINT_VISIBILITY_RESTRICT = enumRef(KEEP_CONSTRAINT, "VISIBILITY_RESTRICT");
      CONSTRAINT_VISIBILITY_INVARIANT = enumRef(KEEP_CONSTRAINT, "VISIBILITY_INVARIANT");
      CONSTRAINT_CLASS_INSTANTIATE = enumRef(KEEP_CONSTRAINT, "CLASS_INSTANTIATE");
      CONSTRAINT_METHOD_INVOKE = enumRef(KEEP_CONSTRAINT, "METHOD_INVOKE");
      CONSTRAINT_FIELD_GET = enumRef(KEEP_CONSTRAINT, "FIELD_GET");
      CONSTRAINT_FIELD_SET = enumRef(KEEP_CONSTRAINT, "FIELD_SET");
      CONSTRAINT_METHOD_REPLACE = enumRef(KEEP_CONSTRAINT, "METHOD_REPLACE");
      CONSTRAINT_FIELD_REPLACE = enumRef(KEEP_CONSTRAINT, "FIELD_REPLACE");
      CONSTRAINT_NEVER_INLINE = enumRef(KEEP_CONSTRAINT, "NEVER_INLINE");
      CONSTRAINT_CLASS_OPEN_HIERARCHY = enumRef(KEEP_CONSTRAINT, "CLASS_OPEN_HIERARCHY");
      CONSTRAINT_GENERIC_SIGNATURE = enumRef(KEEP_CONSTRAINT, "GENERIC_SIGNATURE");
      KEEP_CONSTRAINT_VALUES =
          ImmutableList.of(
              CONSTRAINT_LOOKUP,
              CONSTRAINT_NAME,
              CONSTRAINT_VISIBILITY_RELAX,
              CONSTRAINT_VISIBILITY_RESTRICT,
              CONSTRAINT_VISIBILITY_INVARIANT,
              CONSTRAINT_CLASS_INSTANTIATE,
              CONSTRAINT_METHOD_INVOKE,
              CONSTRAINT_FIELD_GET,
              CONSTRAINT_FIELD_SET,
              CONSTRAINT_METHOD_REPLACE,
              CONSTRAINT_FIELD_REPLACE,
              CONSTRAINT_NEVER_INLINE,
              CONSTRAINT_CLASS_OPEN_HIERARCHY,
              CONSTRAINT_GENERIC_SIGNATURE);

      MEMBER_ACCESS_FLAGS = annoClass("MemberAccessFlags");
      MEMBER_ACCESS_PUBLIC = enumRef(MEMBER_ACCESS_FLAGS, "PUBLIC");
      MEMBER_ACCESS_PROTECTED = enumRef(MEMBER_ACCESS_FLAGS, "PROTECTED");
      MEMBER_ACCESS_PACKAGE_PRIVATE = enumRef(MEMBER_ACCESS_FLAGS, "PACKAGE_PRIVATE");
      MEMBER_ACCESS_PRIVATE = enumRef(MEMBER_ACCESS_FLAGS, "PRIVATE");
      MEMBER_ACCESS_STATIC = enumRef(MEMBER_ACCESS_FLAGS, "STATIC");
      MEMBER_ACCESS_FINAL = enumRef(MEMBER_ACCESS_FLAGS, "FINAL");
      MEMBER_ACCESS_SYNTHETIC = enumRef(MEMBER_ACCESS_FLAGS, "SYNTHETIC");
      MEMBER_ACCESS_VALUES =
          ImmutableList.of(
              MEMBER_ACCESS_PUBLIC,
              MEMBER_ACCESS_PROTECTED,
              MEMBER_ACCESS_PACKAGE_PRIVATE,
              MEMBER_ACCESS_PRIVATE,
              MEMBER_ACCESS_STATIC,
              MEMBER_ACCESS_FINAL,
              MEMBER_ACCESS_SYNTHETIC);

      METHOD_ACCESS_FLAGS = annoClass("MethodAccessFlags");
      METHOD_ACCESS_SYNCHRONIZED = enumRef(METHOD_ACCESS_FLAGS, "SYNCHRONIZED");
      METHOD_ACCESS_BRIDGE = enumRef(METHOD_ACCESS_FLAGS, "BRIDGE");
      METHOD_ACCESS_NATIVE = enumRef(METHOD_ACCESS_FLAGS, "NATIVE");
      METHOD_ACCESS_ABSTRACT = enumRef(METHOD_ACCESS_FLAGS, "ABSTRACT");
      METHOD_ACCESS_STRICT_FP = enumRef(METHOD_ACCESS_FLAGS, "STRICT_FP");
      METHOD_ACCESS_VALUES =
          ImmutableList.of(
              METHOD_ACCESS_SYNCHRONIZED,
              METHOD_ACCESS_BRIDGE,
              METHOD_ACCESS_NATIVE,
              METHOD_ACCESS_ABSTRACT,
              METHOD_ACCESS_STRICT_FP);

      FIELD_ACCESS_FLAGS = annoClass("FieldAccessFlags");
      FIELD_ACCESS_VOLATILE = enumRef(FIELD_ACCESS_FLAGS, "VOLATILE");
      FIELD_ACCESS_TRANSIENT = enumRef(FIELD_ACCESS_FLAGS, "TRANSIENT");
      FIELD_ACCESS_VALUES = ImmutableList.of(FIELD_ACCESS_VOLATILE, FIELD_ACCESS_TRANSIENT);

      KOTLIN_DEFAULT_INVALID_STRING_PATTERN = getUnqualifiedName(STRING_PATTERN) + "(exact = \"\")";
      DEFAULT_INVALID_STRING_PATTERN = "@" + getUnqualifiedName(STRING_PATTERN) + "(exact = \"\")";
      KOTLIN_DEFAULT_INVALID_TYPE_PATTERN = getUnqualifiedName(TYPE_PATTERN) + "(name = \"\")";
      DEFAULT_INVALID_TYPE_PATTERN = "@" + getUnqualifiedName(TYPE_PATTERN) + "(name = \"\")";
      KOTLIN_DEFAULT_INVALID_CLASS_NAME_PATTERN =
          getUnqualifiedName(CLASS_NAME_PATTERN) + "(unqualifiedName = \"\")";
      DEFAULT_INVALID_CLASS_NAME_PATTERN =
          "@" + getUnqualifiedName(CLASS_NAME_PATTERN) + "(unqualifiedName = \"\")";
      KOTLIN_DEFAULT_ANY_INSTANCE_OF_PATTERN = getUnqualifiedName(INSTANCE_OF_PATTERN) + "()";
      DEFAULT_ANY_INSTANCE_OF_PATTERN = "@" + getUnqualifiedName(INSTANCE_OF_PATTERN) + "()";
    }

    private ClassReference annoClass(String unqualifiedName) {
      return classFromTypeName(pkg + "." + unqualifiedName);
    }

    private EnumReference enumRef(ClassReference enumClass, String valueName) {
      return new EnumReference(enumClass, valueName);
    }


    public void withIndent(Runnable fn) {
      indent += 2;
      fn.run();
      indent -= 2;
    }

    private boolean generateKotlin() {
      return pkg.startsWith("androidx.");
    }

    private void println() {
      println("");
    }

    public void println(String line) {
      // Don't indent empty lines.
      if (line.length() > 0) {
        writer.print(Strings.repeat(" ", indent));
      }
      writer.print(line);
      writer.print('\n');
    }

    private void printCopyRight(int year, boolean forceR8Copyright) {
      if (pkg.equals(ANDROIDX_ANNO_PKG) && !forceR8Copyright) {
        println(getHeaderString(2025, KeepItemAnnotationGenerator.class.getSimpleName()));
      } else {
        println(
            CodeGenerationBase.getHeaderString(
                year, KeepItemAnnotationGenerator.class.getSimpleName()));
      }
    }

    private void printCopyRight(int year) {
      printCopyRight(year, false);
    }

    private void printPackage() {
      println("package " + this.pkg + (generateKotlin() ? "" : ";"));
      println();
    }

    private void printImports(Class<?>... imports) {
      printImports(Arrays.asList(imports));
    }

    private void printImports(List<Class<?>> imports) {
      for (Class<?> clazz : imports) {
        println("import " + clazz.getCanonicalName() + (generateKotlin() ? "" : ";"));
      }
    }

    private void printAnnotationImports() {
      printImports(generateKotlin() ? KOTLIN_ANNOTATION_IMPORTS : ANNOTATION_IMPORTS);
    }

    private void printOpenAnnotationClassTargettingAnnotations(String clazz) {
      if (generateKotlin()) {
        println("@Retention(AnnotationRetention.BINARY)");
        println("@Target(AnnotationTarget.ANNOTATION_CLASS)");
        println("public annotation class " + clazz + "(");
      } else {
        println("@Target(ElementType.ANNOTATION_TYPE)");
        println("@Retention(RetentionPolicy.CLASS)");
        println("public @interface " + clazz + " {");
      }
    }

    private void printOpenAnnotationClassTargettingClassFieldlMethodCtor(String clazz) {
      if (generateKotlin()) {
        println("@Retention(AnnotationRetention.BINARY)");
        println("@Target(");
        println("  AnnotationTarget.CLASS,");
        println("  AnnotationTarget.FIELD,");
        println("  AnnotationTarget.FUNCTION,");
        println("  AnnotationTarget.CONSTRUCTOR,");
        println(")");
        println("public annotation class " + clazz + "(");
      } else {
        println(
            "@Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD,"
                + " ElementType.CONSTRUCTOR})");
        println("@Retention(RetentionPolicy.CLASS)");
        println("public @interface " + clazz + " {");
      }
    }

    private void printOpenAnnotationClassTargettingAnnotations(ClassReference clazz) {
      printOpenAnnotationClassTargettingAnnotations(getUnqualifiedName(clazz));
    }

    private String defaultInvalidClassNamePattern() {
      return generateKotlin()
          ? KOTLIN_DEFAULT_INVALID_CLASS_NAME_PATTERN
          : DEFAULT_INVALID_CLASS_NAME_PATTERN;
    }

    private String defaultInvalidStringPattern() {
      return generateKotlin()
          ? KOTLIN_DEFAULT_INVALID_STRING_PATTERN
          : DEFAULT_INVALID_STRING_PATTERN;
    }

    private String defaultInvalidTypePattern() {
      return generateKotlin() ? KOTLIN_DEFAULT_INVALID_TYPE_PATTERN : DEFAULT_INVALID_TYPE_PATTERN;
    }

    private void printCloseAnnotationClass() {
      println(generateKotlin() ? ")" : "}");
    }

    private static String KIND_GROUP = "kind";
    private static String CONSTRAINTS_GROUP = "constraints";
    private static String CLASS_GROUP = "class";
    private static String CLASS_NAME_GROUP = "class-name";
    private static String INSTANCE_OF_GROUP = "instance-of";
    private static String CLASS_ANNOTATED_BY_GROUP = "class-annotated-by";
    private static String MEMBER_ANNOTATED_BY_GROUP = "member-annotated-by";
    private static String METHOD_ANNOTATED_BY_GROUP = "method-annotated-by";
    private static String FIELD_ANNOTATED_BY_GROUP = "field-annotated-by";
    private static String ANNOTATION_NAME_GROUP = "annotation-name";

    private Group createDescriptionGroup() {
      return new Group("description")
          .addMember(
              new GroupMember("description")
                  .setDocTitle("Optional description to document the reason for this annotation.")
                  .setDocReturn("The descriptive message. Defaults to no description.")
                  .defaultEmptyString());
    }

    private Group createBindingsGroup() {
      return new Group("bindings")
          .addMember(new GroupMember("bindings").defaultArrayEmpty(KEEP_BINDING));
    }

    private Group createPreconditionsGroup() {
      return new Group("preconditions")
          .addMember(
              new GroupMember("preconditions")
                  .setDocTitle(
                      "Conditions that should be satisfied for the annotation to be in effect.")
                  .setDocReturn(
                      "The list of preconditions. "
                          + "Defaults to no conditions, thus trivially/unconditionally satisfied.")
                  .defaultArrayEmpty(KEEP_CONDITION));
    }

    private Group createConsequencesGroup() {
      return new Group("consequences")
          .addMember(
              new GroupMember("consequences")
                  .setDocTitle("Consequences that must be kept if the annotation is in effect.")
                  .setDocReturn("The list of target consequences.")
                  .requiredArrayValue(KEEP_TARGET));
    }

    private Group createConsequencesAsValueGroup() {
      return new Group("consequences")
          .addMember(
              new GroupMember("value")
                  .setDocTitle("Consequences that must be kept if the annotation is in effect.")
                  .setDocReturn("The list of target consequences.")
                  .requiredArrayValue(KEEP_TARGET));
    }

    private Group createAdditionalPreconditionsGroup() {
      return new Group("additional-preconditions")
          .addMember(
              new GroupMember("additionalPreconditions")
                  .setDocTitle("Additional preconditions for the annotation to be in effect.")
                  .setDocReturn(
                      "The list of additional preconditions. "
                          + "Defaults to no additional preconditions.")
                  .defaultArrayEmpty(KEEP_CONDITION));
    }

    private Group createAdditionalTargetsGroup(String docTitle) {
      return new Group("additional-targets")
          .addMember(
              new GroupMember("additionalTargets")
                  .setDocTitle(docTitle)
                  .setDocReturn(
                      "List of additional target consequences. "
                          + "Defaults to no additional target consequences.")
                  .defaultArrayEmpty(KEEP_TARGET));
    }

    private Group stringPatternExactGroup() {
      return new Group("string-exact-pattern")
          .allowMutuallyExclusiveWithOtherGroups()
          .addMember(
              new GroupMember("exact")
                  .setDocTitle("Exact string content.")
                  .addParagraph("For example, {@code \"foo\"} or {@code \"java.lang.String\"}.")
                  .defaultEmptyString());
    }

    private Group stringPatternPrefixGroup() {
      return new Group("string-prefix-pattern")
          .addMember(
              new GroupMember("startsWith")
                  .setDocTitle("Matches strings beginning with the given prefix.")
                  .addParagraph(
                      "For example, {@code \"get\"} to match strings such as {@code"
                          + " \"getMyValue\"}.")
                  .defaultEmptyString());
    }

    private Group stringPatternSuffixGroup() {
      return new Group("string-suffix-pattern")
          .addMember(
              new GroupMember("endsWith")
                  .setDocTitle("Matches strings ending with the given suffix.")
                  .addParagraph(
                      "For example, {@code \"Setter\"} to match strings such as {@code"
                          + " \"myValueSetter\"}.")
                  .defaultEmptyString());
    }

    public Group typePatternGroup() {
      return new Group("type-pattern")
          .addMember(
              new GroupMember("name")
                  .setDocTitle("Exact type name as a string.")
                  .addParagraph("For example, {@code \"long\"} or {@code \"java.lang.String\"}.")
                  .defaultEmptyString())
          .addMember(
              new GroupMember("constant")
                  .setDocTitle("Exact type from a class constant.")
                  .addParagraph("For example, {@code String.class}.")
                  .defaultObjectClass())
          .addMember(
              new GroupMember("classNamePattern")
                  .setDocTitle("Classes matching the class-name pattern.")
                  .defaultValue(CLASS_NAME_PATTERN, defaultInvalidClassNamePattern()))
          .addMember(instanceOfPattern());
      // TODO(b/248408342): Add more injections on type pattern variants.
      // /** Exact type name as a string to match any array with that type as member. */
      // String arrayOf() default "";
      //
      // /** Exact type as a class constant to match any array with that type as member. */
      // Class<?> arrayOfConstant() default TypePattern.class;
      //
      // /** If true, the pattern matches any primitive type. Such as, boolean, int, etc. */
      // boolean anyPrimitive() default false;
      //
      // /** If true, the pattern matches any array type. */
      // boolean anyArray() default false;
      //
      // /** If true, the pattern matches any class type. */
      // boolean anyClass() default false;
      //
      // /** If true, the pattern matches any reference type, namely: arrays or classes. */
      // boolean anyReference() default false;
    }

    private Group instanceOfPatternInclusive() {
      return new Group("instance-of-inclusive")
          .addMember(
              new GroupMember("inclusive", "isInclusive")
                  .setDocTitle("True if the pattern should include the directly matched classes.")
                  .addParagraph(
                      "If false, the pattern is exclusive and only matches classes that are",
                      "strict subclasses of the pattern.")
                  .defaultBooleanValue(true));
    }

    private Group instanceOfPatternClassNamePattern() {
      return new Group("instance-of-class-name-pattern")
          .addMember(
              new GroupMember("classNamePattern")
                  .setDocTitle("Instances of classes matching the class-name pattern.")
                  .defaultValue(CLASS_NAME_PATTERN, defaultInvalidClassNamePattern()));
    }

    private Group classNamePatternFullNameGroup() {
      return new Group(CLASS_NAME_GROUP)
          .allowMutuallyExclusiveWithOtherGroups()
          .addMember(
              new GroupMember("name")
                  .setDocTitle(
                      "Define the " + CLASS_NAME_GROUP + " pattern by fully qualified class name.")
                  .setDocReturn("The qualified class name that defines the class.")
                  .defaultEmptyString())
          .addMember(
              new GroupMember("constant")
                  .setDocTitle(
                      "Define the "
                          + CLASS_NAME_GROUP
                          + " pattern by reference to a Class constant.")
                  .setDocReturn("The class-constant that defines the class.")
                  .defaultObjectClass());
    }

    private Group classNamePatternUnqualifiedNameGroup() {
      return new Group("class-unqualified-name")
          .addMember(
              new GroupMember("unqualifiedName")
                  .setDocTitle("Exact and unqualified name of the class or interface.")
                  .addParagraph(
                      "For example, the unqualified name of {@code com.example.MyClass} is {@code"
                          + " MyClass}.",
                      "Note that for inner classes a `$` will appear in the unqualified name,"
                          + "such as, {@code MyClass$MyInnerClass}.")
                  .addParagraph("The default matches any unqualified name.")
                  .defaultEmptyString())
          .addMember(
              new GroupMember("unqualifiedNamePattern")
                  .setDocTitle("Define the unqualified class-name pattern by a string pattern.")
                  .setDocReturn("The string pattern of the unqualified class name.")
                  .addParagraph("The default matches any unqualified name.")
                  .defaultValue(STRING_PATTERN, defaultInvalidStringPattern()));
    }

    private Group classNamePatternPackageGroup() {
      return new Group("class-package-name")
          .addMember(
              new GroupMember("packageName")
                  .setDocTitle("Exact package name of the class or interface.")
                  .addParagraph(
                      "For example, the package of {@code com.example.MyClass} is {@code"
                          + " com.example}.")
                  .addParagraph("The default matches any package.")
                  .defaultEmptyString());
    }

    private Group getKindGroup() {
      return new Group(KIND_GROUP).addMember(getKindMember());
    }

    private GroupMember getKindMember() {
      return new GroupMember("kind")
          .defaultValue(KEEP_ITEM_KIND, "KeepItemKind.DEFAULT")
          .setDocTitle("Specify the kind of this item pattern.")
          .setDocReturn("The kind for this pattern.")
          .addParagraph("Possible values are:")
          .addUnorderedList(
              docEnumLink(KIND_ONLY_CLASS),
              docEnumLink(KIND_ONLY_MEMBERS),
              docEnumLink(KIND_ONLY_METHODS),
              docEnumLink(KIND_ONLY_FIELDS),
              docEnumLink(KIND_CLASS_AND_MEMBERS),
              docEnumLink(KIND_CLASS_AND_METHODS),
              docEnumLink(KIND_CLASS_AND_FIELDS))
          .addParagraph(
              "If unspecified the default kind for an item depends on its member patterns:")
          .addUnorderedList(
              docEnumLink(KIND_ONLY_CLASS) + " if no member patterns are defined",
              docEnumLink(KIND_ONLY_METHODS) + " if method patterns are defined",
              docEnumLink(KIND_ONLY_FIELDS) + " if field patterns are defined",
              docEnumLink(KIND_ONLY_MEMBERS) + " otherwise.");
    }

    private void forEachKeepConstraintGroups(Consumer<Group> fn) {
      fn.accept(getKeepConstraintsGroup());
      fn.accept(new Group("constrain-annotations").addMember(constrainAnnotations()));
    }

    private Group getKeepConstraintsGroup() {
      return new Group(CONSTRAINTS_GROUP).addMember(constraints()).addMember(constraintAdditions());
    }

    private GroupMember constraints() {
      return new GroupMember("constraints")
          .setDocTitle("Define the usage constraints of the target.")
          .addParagraph("The specified constraints must remain valid for the target.")
          .addParagraph(
              "The default constraints depend on the kind of the target.",
              "For all targets the default constraints include:")
          .addUnorderedList(
              docEnumLink(CONSTRAINT_LOOKUP),
              docEnumLink(CONSTRAINT_NAME),
              docEnumLink(CONSTRAINT_VISIBILITY_RELAX))
          .addParagraph("For classes the default constraints also include:")
          .addUnorderedList(docEnumLink(CONSTRAINT_CLASS_INSTANTIATE))
          .addParagraph("For methods the default constraints also include:")
          .addUnorderedList(docEnumLink(CONSTRAINT_METHOD_INVOKE))
          .addParagraph("For fields the default constraints also include:")
          .addUnorderedList(docEnumLink(CONSTRAINT_FIELD_GET), docEnumLink(CONSTRAINT_FIELD_SET))
          .setDocReturn("Usage constraints for the target.")
          .defaultArrayEmpty(KEEP_CONSTRAINT);
    }

    private GroupMember constraintAdditions() {
      return new GroupMember("constraintAdditions")
          .setDocTitle("Add additional usage constraints of the target.")
          .addParagraph(
              "The specified constraints must remain valid for the target",
              "in addition to the default constraints.")
          .addParagraph("The default constraints are documented in " + docLink(constraints()))
          .setDocReturn("Additional usage constraints for the target.")
          .defaultArrayEmpty(KEEP_CONSTRAINT);
    }

    private GroupMember constrainAnnotations() {
      return new GroupMember("constrainAnnotations")
          .setDocTitle("Patterns for annotations that must remain on the item.")
          .addParagraph(
              "The annotations matching any of the patterns must remain on the item",
              "if the annotation types remain in the program.")
          .addParagraph(
              "Note that if the annotation types themselves are unused/removed,",
              "then their references on the item will be removed too.",
              "If the annotation types themselves are used reflectively then they too need a",
              "keep annotation or rule to ensure they remain in the program.")
          .addParagraph(
              "By default no annotation patterns are defined and no annotations are required to",
              "remain.")
          .setDocReturn("Annotation patterns")
          .defaultArrayEmpty(ANNOTATION_PATTERN);
    }

    private Group annotationNameGroup() {
      return new Group(ANNOTATION_NAME_GROUP)
          .addMember(annotationName())
          .addMember(annotationConstant())
          .addMember(annotationNamePattern())
          .addDocFooterParagraph(
              "If none are specified the default is to match any annotation name.");
    }

    private GroupMember annotationName() {
      return new GroupMember("name")
          .setDocTitle(
              "Define the " + ANNOTATION_NAME_GROUP + " pattern by fully qualified class name.")
          .setDocReturn("The qualified class name that defines the annotation.")
          .defaultEmptyString();
    }

    private GroupMember annotationConstant() {
      return new GroupMember("constant")
          .setDocTitle(
              "Define the "
                  + ANNOTATION_NAME_GROUP
                  + " pattern by reference to a {@code Class} constant.")
          .setDocReturn("The Class constant that defines the annotation.")
          .defaultObjectClass();
    }

    private GroupMember annotationNamePattern() {
      return new GroupMember("namePattern")
          .setDocTitle(
              "Define the "
                  + ANNOTATION_NAME_GROUP
                  + " pattern by reference to a class-name pattern.")
          .setDocReturn("The class-name pattern that defines the annotation.")
          .defaultValue(CLASS_NAME_PATTERN, defaultInvalidClassNamePattern());
    }

    private static GroupMember annotationRetention() {
      return new GroupMember("retention")
          .setDocTitle("Specify which retention policies must be set for the annotations.")
          .addParagraph("Matches annotations with matching retention policies")
          .setDocReturn("Retention policies. By default {@code RetentionPolicy.RUNTIME}.")
          .defaultArrayValue(JAVA_RETENTION_POLICY, "RetentionPolicy.RUNTIME");
    }

    private GroupMember bindingName() {
      return new GroupMember("bindingName")
          .setDocTitle(
              "Name with which other bindings, conditions or targets "
                  + "can reference the bound item pattern.")
          .setDocReturn("Name of the binding.")
          .requiredStringValue();
    }

    private GroupMember classFromBinding() {
      return new GroupMember("classFromBinding")
          .setDocTitle("Define the " + CLASS_GROUP + " pattern by reference to a binding.")
          .setDocReturn("The name of the binding that defines the class.")
          .defaultEmptyString();
    }

    private Group createClassBindingGroup() {
      return new Group(CLASS_GROUP)
          .allowMutuallyExclusiveWithOtherGroups()
          .addMember(classFromBinding())
          .addDocFooterParagraph("If none are specified the default is to match any class.");
    }

    private GroupMember className() {
      return new GroupMember("className")
          .setDocTitle("Define the " + CLASS_NAME_GROUP + " pattern by fully qualified class name.")
          .setDocReturn("The qualified class name that defines the class.")
          .defaultEmptyString();
    }

    private GroupMember classConstant() {
      return new GroupMember("classConstant")
          .setDocTitle(
              "Define the " + CLASS_NAME_GROUP + " pattern by reference to a Class constant.")
          .setDocReturn("The class-constant that defines the class.")
          .defaultObjectClass();
    }

    private GroupMember classNamePattern() {
      return new GroupMember("classNamePattern")
          .setDocTitle(
              "Define the " + CLASS_NAME_GROUP + " pattern by reference to a class-name pattern.")
          .setDocReturn("The class-name pattern that defines the class.")
          .defaultValue(CLASS_NAME_PATTERN, defaultInvalidClassNamePattern());
    }

    private Group createClassNamePatternGroup() {
      return new Group(CLASS_NAME_GROUP)
          .addMember(className())
          .addMember(classConstant())
          .addMember(classNamePattern())
          .addDocFooterParagraph("If none are specified the default is to match any class name.");
    }

    private GroupMember instanceOfClassName() {
      return new GroupMember("instanceOfClassName")
          .setDocTitle(
              "Define the "
                  + INSTANCE_OF_GROUP
                  + " pattern as classes that are instances of the fully qualified class name.")
          .setDocReturn("The qualified class name that defines what instance-of the class must be.")
          .defaultEmptyString();
    }

    private GroupMember instanceOfClassConstant() {
      return new GroupMember("instanceOfClassConstant")
          .setDocTitle(
              "Define the "
                  + INSTANCE_OF_GROUP
                  + " pattern as classes that are instances the referenced Class constant.")
          .setDocReturn("The class constant that defines what instance-of the class must be.")
          .defaultObjectClass();
    }

    private String getInstanceOfExclusiveDoc() {
      return "The pattern is exclusive in that it does not match classes that are"
          + " instances of the pattern, but only those that are instances of classes that"
          + " are subclasses of the pattern.";
    }

    private GroupMember instanceOfClassNameExclusive() {
      return new GroupMember("instanceOfClassNameExclusive")
          .setDocTitle(
              "Define the "
                  + INSTANCE_OF_GROUP
                  + " pattern as classes that are instances of the fully qualified class name.")
          .setDocReturn("The qualified class name that defines what instance-of the class must be.")
          .addParagraph(getInstanceOfExclusiveDoc())
          .defaultEmptyString();
    }

    private GroupMember instanceOfClassConstantExclusive() {
      return new GroupMember("instanceOfClassConstantExclusive")
          .setDocTitle(
              "Define the "
                  + INSTANCE_OF_GROUP
                  + " pattern as classes that are instances the referenced Class constant.")
          .addParagraph(getInstanceOfExclusiveDoc())
          .setDocReturn("The class constant that defines what instance-of the class must be.")
          .defaultObjectClass();
    }

    private GroupMember instanceOfPattern() {
      return new GroupMember("instanceOfPattern")
          .setDocTitle("Define the " + INSTANCE_OF_GROUP + " with a pattern.")
          .setDocReturn("The pattern that defines what instance-of the class must be.")
          .defaultValue(
              INSTANCE_OF_PATTERN,
              generateKotlin()
                  ? KOTLIN_DEFAULT_ANY_INSTANCE_OF_PATTERN
                  : DEFAULT_ANY_INSTANCE_OF_PATTERN);
    }

    private Group createClassInstanceOfPatternGroup() {
      return new Group(INSTANCE_OF_GROUP)
          .addMember(instanceOfClassName())
          .addMember(instanceOfClassNameExclusive())
          .addMember(instanceOfClassConstant())
          .addMember(instanceOfClassConstantExclusive())
          .addMember(instanceOfPattern())
          .addDocFooterParagraph(
              "If none are specified the default is to match any class instance.");
    }

    private String annotatedByDefaultDocFooter(String name) {
      return "If none are specified the default is to match any "
          + name
          + " regardless of what the "
          + name
          + " is annotated by.";
    }

    private Group createAnnotatedByPatternGroup(String name, String groupName) {
      return new Group(groupName)
          .addMember(
              new GroupMember(name + "AnnotatedByClassName")
                  .setDocTitle(
                      "Define the " + groupName + " pattern by fully qualified class name.")
                  .setDocReturn("The qualified class name that defines the annotation.")
                  .defaultEmptyString())
          .addMember(
              new GroupMember(name + "AnnotatedByClassConstant")
                  .setDocTitle(
                      "Define the " + groupName + " pattern by reference to a Class constant.")
                  .setDocReturn("The class-constant that defines the annotation.")
                  .defaultObjectClass())
          .addMember(
              new GroupMember(name + "AnnotatedByClassNamePattern")
                  .setDocTitle(
                      "Define the " + groupName + " pattern by reference to a class-name pattern.")
                  .setDocReturn("The class-name pattern that defines the annotation.")
                  .defaultValue(CLASS_NAME_PATTERN, defaultInvalidClassNamePattern()));
    }

    private Group createClassAnnotatedByPatternGroup() {
      String name = "class";
      return createAnnotatedByPatternGroup(name, CLASS_ANNOTATED_BY_GROUP)
          .addDocFooterParagraph(annotatedByDefaultDocFooter(name));
    }

    private Group createMemberBindingGroup() {
      return new Group("member")
          .allowMutuallyExclusiveWithOtherGroups()
          .addMember(
              new GroupMember("memberFromBinding")
                  .setDocTitle("Define the member pattern in full by a reference to a binding.")
                  .addParagraph(
                      "Mutually exclusive with all other class and member pattern properties.",
                      "When a member binding is referenced this item is defined to be that item,",
                      "including its class and member patterns.")
                  .setDocReturn("The binding name that defines the member.")
                  .defaultEmptyString());
    }

    private Group createMemberAnnotatedByGroup() {
      String name = "member";
      return createAnnotatedByPatternGroup(name, MEMBER_ANNOTATED_BY_GROUP)
          .addDocFooterParagraph(getMutuallyExclusiveForMemberProperties())
          .addDocFooterParagraph(annotatedByDefaultDocFooter(name));
    }

    private Group createMemberAccessGroup() {
      return new Group("member-access")
          .addMember(
              new GroupMember("memberAccess")
                  .setDocTitle("Define the member-access pattern by matching on access flags.")
                  .addParagraph(getMutuallyExclusiveForMemberProperties())
                  .setDocReturn("The member access-flag constraints that must be met.")
                  .defaultArrayEmpty(MEMBER_ACCESS_FLAGS));
    }

    private String getMutuallyExclusiveForMemberProperties() {
      return "Mutually exclusive with all field and method properties "
          + "as use restricts the match to both types of members.";
    }

    private String getMutuallyExclusiveForMethodProperties() {
      return "Mutually exclusive with all field properties.";
    }

    private String getMutuallyExclusiveForFieldProperties() {
      return "Mutually exclusive with all method properties.";
    }

    private String getMethodDefaultDoc(String suffix) {
      return "If none, and other properties define this item as a method, the default matches "
          + suffix
          + ".";
    }

    private String getFieldDefaultDoc(String suffix) {
      return "If none, and other properties define this item as a field, the default matches "
          + suffix
          + ".";
    }

    private Group createMethodAnnotatedByGroup() {
      String name = "method";
      return createAnnotatedByPatternGroup(name, METHOD_ANNOTATED_BY_GROUP)
          .addDocFooterParagraph(getMutuallyExclusiveForMethodProperties())
          .addDocFooterParagraph(annotatedByDefaultDocFooter(name));
    }

    private Group createMethodAccessGroup() {
      return new Group("method-access")
          .addMember(
              new GroupMember("methodAccess")
                  .setDocTitle("Define the method-access pattern by matching on access flags.")
                  .addParagraph(getMutuallyExclusiveForMethodProperties())
                  .addParagraph(getMethodDefaultDoc("any method-access flags"))
                  .setDocReturn("The method access-flag constraints that must be met.")
                  .defaultArrayEmpty(METHOD_ACCESS_FLAGS));
    }

    private Group createMethodNameGroup() {
      return new Group("method-name")
          .addMember(
              new GroupMember("methodName")
                  .setDocTitle("Define the method-name pattern by an exact method name.")
                  .addParagraph(getMutuallyExclusiveForMethodProperties())
                  .addParagraph(getMethodDefaultDoc("any method name"))
                  .setDocReturn("The exact method name of the method.")
                  .defaultEmptyString())
          .addMember(
              new GroupMember("methodNamePattern")
                  .setDocTitle("Define the method-name pattern by a string pattern.")
                  .addParagraph(getMutuallyExclusiveForMethodProperties())
                  .addParagraph(getMethodDefaultDoc("any method name"))
                  .setDocReturn("The string pattern of the method name.")
                  .defaultValue(STRING_PATTERN, defaultInvalidStringPattern()));
    }

    private Group createMethodReturnTypeGroup() {
      return new Group("return-type")
          .addMember(
              new GroupMember("methodReturnType")
                  .setDocTitle(
                      "Define the method return-type pattern by a fully qualified type or 'void'.")
                  .addParagraph(getMutuallyExclusiveForMethodProperties())
                  .addParagraph(getMethodDefaultDoc("any return type"))
                  .setDocReturn("The qualified type name of the method return type.")
                  .defaultEmptyString())
          .addMember(
              new GroupMember("methodReturnTypeConstant")
                  .setDocTitle("Define the method return-type pattern by a class constant.")
                  .addParagraph(getMutuallyExclusiveForMethodProperties())
                  .addParagraph(getMethodDefaultDoc("any return type"))
                  .setDocReturn("A class constant denoting the type of the method return type.")
                  .defaultObjectClass())
          .addMember(
              new GroupMember("methodReturnTypePattern")
                  .setDocTitle("Define the method return-type pattern by a type pattern.")
                  .addParagraph(getMutuallyExclusiveForMethodProperties())
                  .addParagraph(getMethodDefaultDoc("any return type"))
                  .setDocReturn("The pattern of the method return type.")
                  .defaultValue(TYPE_PATTERN, defaultInvalidTypePattern()));
    }

    private Group createMethodParametersGroup() {
      return new Group("parameters")
          .addMember(
              new GroupMember("methodParameters")
                  .setDocTitle(
                      "Define the method parameters pattern by a list of fully qualified types.")
                  .addParagraph(getMutuallyExclusiveForMethodProperties())
                  .addParagraph(getMethodDefaultDoc("any parameters"))
                  .setDocReturn("The list of qualified type names of the method parameters.")
                  .defaultArrayValue(JAVA_STRING, quote("")))
          .addMember(
              new GroupMember("methodParameterTypePatterns")
                  .setDocTitle(
                      "Define the method parameters pattern by a list of patterns on types.")
                  .addParagraph(getMutuallyExclusiveForMethodProperties())
                  .addParagraph(getMethodDefaultDoc("any parameters"))
                  .setDocReturn("The list of type patterns for the method parameters.")
                  .defaultArrayValue(TYPE_PATTERN, defaultInvalidTypePattern()));
    }

    private Group createFieldAnnotatedByGroup() {
      String name = "field";
      return createAnnotatedByPatternGroup(name, FIELD_ANNOTATED_BY_GROUP)
          .addDocFooterParagraph(getMutuallyExclusiveForFieldProperties())
          .addDocFooterParagraph(annotatedByDefaultDocFooter(name));
    }

    private Group createFieldAccessGroup() {
      return new Group("field-access")
          .addMember(
              new GroupMember("fieldAccess")
                  .setDocTitle("Define the field-access pattern by matching on access flags.")
                  .addParagraph(getMutuallyExclusiveForFieldProperties())
                  .addParagraph(getFieldDefaultDoc("any field-access flags"))
                  .setDocReturn("The field access-flag constraints that must be met.")
                  .defaultArrayEmpty(FIELD_ACCESS_FLAGS));
    }

    private Group createFieldNameGroup() {
      return new Group("field-name")
          .addMember(
              new GroupMember("fieldName")
                  .setDocTitle("Define the field-name pattern by an exact field name.")
                  .addParagraph(getMutuallyExclusiveForFieldProperties())
                  .addParagraph(getFieldDefaultDoc("any field name"))
                  .setDocReturn("The exact field name of the field.")
                  .defaultEmptyString())
          .addMember(
              new GroupMember("fieldNamePattern")
                  .setDocTitle("Define the field-name pattern by a string pattern.")
                  .addParagraph(getMutuallyExclusiveForFieldProperties())
                  .addParagraph(getFieldDefaultDoc("any field name"))
                  .setDocReturn("The string pattern of the field name.")
                  .defaultValue(STRING_PATTERN, defaultInvalidStringPattern()));
    }

    private Group createFieldTypeGroup() {
      return new Group("field-type")
          .addMember(
              new GroupMember("fieldType")
                  .setDocTitle("Define the field-type pattern by a fully qualified type.")
                  .addParagraph(getMutuallyExclusiveForFieldProperties())
                  .addParagraph(getFieldDefaultDoc("any type"))
                  .setDocReturn("The qualified type name for the field type.")
                  .defaultEmptyString())
          .addMember(
              new GroupMember("fieldTypeConstant")
                  .setDocTitle("Define the field-type pattern by a class constant.")
                  .addParagraph(getMutuallyExclusiveForFieldProperties())
                  .addParagraph(getFieldDefaultDoc("any type"))
                  .setDocReturn("The class constant for the field type.")
                  .defaultObjectClass())
          .addMember(
              new GroupMember("fieldTypePattern")
                  .setDocTitle("Define the field-type pattern by a pattern on types.")
                  .addParagraph(getMutuallyExclusiveForFieldProperties())
                  .addParagraph(getFieldDefaultDoc("any type"))
                  .setDocReturn("The type pattern for the field type.")
                  .defaultValue(TYPE_PATTERN, defaultInvalidTypePattern()));
    }

    private void generateClassAndMemberPropertiesWithClassAndMemberBinding() {
      internalGenerateClassAndMemberPropertiesWithBinding(true);
    }

    private void generateClassAndMemberPropertiesWithClassBinding() {
      internalGenerateClassAndMemberPropertiesWithBinding(false);
    }

    private void internalGenerateClassAndMemberPropertiesWithBinding(boolean includeMemberBinding) {
      // Class properties.
      {
        Group bindingGroup = createClassBindingGroup();
        Group classNameGroup = createClassNamePatternGroup();
        Group classInstanceOfGroup = createClassInstanceOfPatternGroup();
        Group classAnnotatedByGroup = createClassAnnotatedByPatternGroup();
        bindingGroup.addMutuallyExclusiveGroups(
            classNameGroup, classInstanceOfGroup, classAnnotatedByGroup);

        bindingGroup.generate(this);
        println();
        classNameGroup.generate(this);
        println();
        classInstanceOfGroup.generate(this);
        println();
        classAnnotatedByGroup.generate(this);
        println();
      }

      // Member binding properties.
      Group memberBindingGroup = null;
      if (includeMemberBinding) {
        memberBindingGroup = createMemberBindingGroup();
        memberBindingGroup.generate(this);
        println();
      }

      // The remaining member properties.
      internalGenerateMemberPropertiesNoBinding(memberBindingGroup);
    }

    private Group maybeLink(Group group, Group maybeExclusiveGroup) {
      if (maybeExclusiveGroup != null) {
        maybeExclusiveGroup.addMutuallyExclusiveGroups(group);
      }
      return group;
    }

    private void generateMemberPropertiesNoBinding() {
      internalGenerateMemberPropertiesNoBinding(null);
    }

    private void internalGenerateMemberPropertiesNoBinding(Group memberBindingGroup) {
      // General member properties.
      maybeLink(createMemberAnnotatedByGroup(), memberBindingGroup).generate(this);
      println();
      maybeLink(createMemberAccessGroup(), memberBindingGroup).generate(this);
      println();

      // Method properties.
      maybeLink(createMethodAnnotatedByGroup(), memberBindingGroup).generate(this);
      println();
      maybeLink(createMethodAccessGroup(), memberBindingGroup).generate(this);
      println();
      maybeLink(createMethodNameGroup(), memberBindingGroup).generate(this);
      println();
      maybeLink(createMethodReturnTypeGroup(), memberBindingGroup).generate(this);
      println();
      maybeLink(createMethodParametersGroup(), memberBindingGroup).generate(this);
      println();

      // Field properties.
      maybeLink(createFieldAnnotatedByGroup(), memberBindingGroup).generate(this);
      println();
      maybeLink(createFieldAccessGroup(), memberBindingGroup).generate(this);
      println();
      maybeLink(createFieldNameGroup(), memberBindingGroup).generate(this);
      println();
      maybeLink(createFieldTypeGroup(), memberBindingGroup).generate(this);
    }

    private void generateStringPattern() {
      printCopyRight(2024);
      printPackage();
      printAnnotationImports();
      DocPrinter.printer()
          .setDocTitle("A pattern structure for matching strings.")
          .addParagraph("If no properties are set, the default pattern matches any string.")
          .printDoc(this::println);
      printOpenAnnotationClassTargettingAnnotations(STRING_PATTERN);
      println();
      withIndent(
          () -> {
            Group exactGroup = stringPatternExactGroup();
            Group prefixGroup = stringPatternPrefixGroup();
            Group suffixGroup = stringPatternSuffixGroup();
            exactGroup.addMutuallyExclusiveGroups(prefixGroup, suffixGroup);
            exactGroup.generate(this);
            println();
            prefixGroup.generate(this);
            println();
            suffixGroup.generate(this);
          });
      printCloseAnnotationClass();
    }

    private void generateTypePattern() {
      printCopyRight(2023);
      printPackage();
      printAnnotationImports();
      if (generateKotlin()) {
        println("import kotlin.reflect.KClass");
      }
      DocPrinter.printer()
          .setDocTitle("A pattern structure for matching types.")
          .addParagraph("If no properties are set, the default pattern matches any type.")
          .addParagraph("All properties on this annotation are mutually exclusive.")
          .printDoc(this::println);
      printOpenAnnotationClassTargettingAnnotations(TYPE_PATTERN);
      println();
      withIndent(() -> typePatternGroup().generate(this));
      printCloseAnnotationClass();
    }

    private void generateClassNamePattern() {
      printCopyRight(2023);
      printPackage();
      printAnnotationImports();
      if (generateKotlin()) {
        println("import kotlin.reflect.KClass");
      }
      DocPrinter.printer()
          .setDocTitle("A pattern structure for matching names of classes and interfaces.")
          .addParagraph(
              "If no properties are set, the default pattern matches any name of a class or"
                  + " interface.")
          .printDoc(this::println);
      printOpenAnnotationClassTargettingAnnotations(CLASS_NAME_PATTERN);
      println();
      withIndent(
          () -> {
            Group exactNameGroup = classNamePatternFullNameGroup();
            Group unqualifiedNameGroup = classNamePatternUnqualifiedNameGroup();
            Group packageGroup = classNamePatternPackageGroup();
            exactNameGroup.addMutuallyExclusiveGroups(unqualifiedNameGroup, packageGroup);

            exactNameGroup.generate(this);
            println();
            unqualifiedNameGroup.generate(this);
            println();
            packageGroup.generate(this);
          });
      printCloseAnnotationClass();
    }

    private void generateInstanceOfPattern() {
      printCopyRight(2024);
      printPackage();
      printAnnotationImports();
      DocPrinter.printer()
          .setDocTitle("A pattern structure for matching instances of classes and interfaces.")
          .addParagraph("If no properties are set, the default pattern matches any instance.")
          .printDoc(this::println);
      printOpenAnnotationClassTargettingAnnotations(INSTANCE_OF_PATTERN);
      println();
      withIndent(
          () -> {
            instanceOfPatternInclusive().generate(this);
            println();
            instanceOfPatternClassNamePattern().generate(this);
          });
      printCloseAnnotationClass();
    }

    private void generateAnnotationPattern() {
      printCopyRight(2024);
      printPackage();
      printImports(RetentionPolicy.class);
      printAnnotationImports();
      if (generateKotlin()) {
        println("import kotlin.reflect.KClass");
      }
      DocPrinter.printer()
          .setDocTitle("A pattern structure for matching annotations.")
          .addParagraph(
              "If no properties are set, the default pattern matches any annotation",
              "with a runtime retention policy.")
          .printDoc(this::println);
      printOpenAnnotationClassTargettingAnnotations(ANNOTATION_PATTERN);
      println();
      withIndent(
          () -> {
            annotationNameGroup().generate(this);
            println();
            annotationRetention().generate(this);
          });
      printCloseAnnotationClass();
    }

    private void generateKeepBinding() {
      printCopyRight(2022);
      printPackage();
      printAnnotationImports();
      if (generateKotlin()) {
        println("import kotlin.reflect.KClass");
      }
      DocPrinter.printer()
          .setDocTitle("A binding of a keep item.")
          .addParagraph(
              "Bindings allow referencing the exact instance of a match from a condition in other "
                  + " conditions and/or targets. It can also be used to reduce duplication of"
                  + " targets by sharing patterns.")
          .addParagraph("An item can be:")
          .addUnorderedList(
              "a pattern on classes;", "a pattern on methods; or", "a pattern on fields.")
          .printDoc(this::println);
      printOpenAnnotationClassTargettingAnnotations("KeepBinding");
      println();
      withIndent(
          () -> {
            bindingName().generate(this);
            println();
            getKindGroup().generate(this);
            println();
            generateClassAndMemberPropertiesWithClassBinding();
          });
      printCloseAnnotationClass();
    }

    private void generateKeepTarget() {
      printCopyRight(2022);
      printPackage();
      printAnnotationImports();
      if (generateKotlin()) {
        println("import kotlin.reflect.KClass");
      }
      DocPrinter.printer()
          .setDocTitle("A target for a keep edge.")
          .addParagraph(
              "The target denotes an item along with options for what to keep. An item can be:")
          .addUnorderedList(
              "a pattern on classes;", "a pattern on methods; or", "a pattern on fields.")
          .printDoc(this::println);
      printOpenAnnotationClassTargettingAnnotations("KeepTarget");
      println();
      withIndent(
          () -> {
            getKindGroup().generate(this);
            println();
            forEachKeepConstraintGroups(
                g -> {
                  g.generate(this);
                  println();
                });
            generateClassAndMemberPropertiesWithClassAndMemberBinding();
          });
      printCloseAnnotationClass();
    }

    private void generateKeepCondition() {
      printCopyRight(2022);
      printPackage();
      printAnnotationImports();
      if (generateKotlin()) {
        println("import kotlin.reflect.KClass");
      }
      DocPrinter.printer()
          .setDocTitle("A condition for a keep edge.")
          .addParagraph(
              "The condition denotes an item used as a precondition of a rule. An item can be:")
          .addUnorderedList(
              "a pattern on classes;", "a pattern on methods; or", "a pattern on fields.")
          .printDoc(this::println);
      printOpenAnnotationClassTargettingAnnotations("KeepCondition");
      println();
      withIndent(
          () -> {
            generateClassAndMemberPropertiesWithClassAndMemberBinding();
          });
      printCloseAnnotationClass();
    }

    private void generateKeepForApi() {
      printCopyRight(2023);
      printPackage();
      printAnnotationImports();
      if (generateKotlin()) {
        println("import kotlin.reflect.KClass");
      }
      DocPrinter.printer()
          .setDocTitle(
              "Annotation to mark a class, field or method as part of a library API surface.")
          .addParagraph(
              "When a class is annotated, member patterns can be used to define which members are"
                  + " to be kept. When no member patterns are specified the default pattern matches"
                  + " all public and protected members.")
          .addParagraph(
              "When a member is annotated, the member patterns cannot be used as the annotated"
                  + " member itself fully defines the item to be kept (i.e., itself).")
          .printDoc(this::println);
      printOpenAnnotationClassTargettingClassFieldlMethodCtor("KeepForApi");
      println();
      withIndent(
          () -> {
            createDescriptionGroup().generate(this);
            println();
            createAdditionalTargetsGroup(
                    "Additional targets to be kept as part of the API surface.")
                .generate(this);
            println();
            GroupMember kindProperty = getKindMember();
            kindProperty
                .clearDocLines()
                .addParagraph(
                    "Default kind is",
                    docEnumLink(KIND_CLASS_AND_MEMBERS) + ",",
                    "meaning the annotated class and/or member is to be kept.",
                    "When annotating a class this can be set to",
                    docEnumLink(KIND_ONLY_CLASS),
                    "to avoid patterns on any members.",
                    "That can be useful when the API members are themselves explicitly annotated.")
                .addParagraph(
                    "It is not possible to use",
                    docEnumLink(KIND_ONLY_CLASS),
                    "if annotating a member. Also, it is never valid to use kind",
                    docEnumLink(KIND_ONLY_MEMBERS),
                    "as the API surface must keep the class if any member is to be accessible.")
                .generate(this);
            println();
            generateMemberPropertiesNoBinding();
          });
      printCloseAnnotationClass();
    }

    private void generateUsesReflection() {
      printCopyRight(2022);
      printPackage();
      printAnnotationImports();
      DocPrinter.printer()
          .setDocTitle(
              "Annotation to declare the reflective usages made by a class, method or field.")
          .addParagraph(
              "The annotation's 'value' is a list of targets to be kept if the annotated item is"
                  + " used. The annotated item is a precondition for keeping any of the specified"
                  + " targets. Thus, if an annotated method is determined to be unused by the"
                  + " program, the annotation itself will not be in effect and the targets will not"
                  + " be kept (assuming nothing else is otherwise keeping them).")
          .addParagraph(
              "The annotation's 'additionalPreconditions' is optional and can specify additional"
                  + " conditions that should be satisfied for the annotation to be in effect.")
          .addParagraph(
              "The translation of the "
                  + docLink(USES_REFLECTION)
                  + " annotation into a "
                  + docLink(KEEP_EDGE)
                  + " is as follows:")
          .addParagraph(
              "Assume the item of the annotation is denoted by 'CTX' and referred to as its"
                  + " context.")
          .addCodeBlock(
              annoUnqualifiedName(USES_REFLECTION)
                  + "(value = targets, [additionalPreconditions = preconditions])",
              "==>",
              annoUnqualifiedName(KEEP_EDGE) + "(",
              "  consequences = targets,",
              "  preconditions = {createConditionFromContext(CTX)} + preconditions",
              ")",
              "",
              "where",
              "  KeepCondition createConditionFromContext(ctx) {",
              "    if (ctx.isClass()) {",
              "      return new KeepCondition(classTypeName = ctx.getClassTypeName());",
              "    }",
              "    if (ctx.isMethod()) {",
              "      return new KeepCondition(",
              "        classTypeName = ctx.getClassTypeName(),",
              "        methodName = ctx.getMethodName(),",
              "        methodReturnType = ctx.getMethodReturnType(),",
              "        methodParameterTypes = ctx.getMethodParameterTypes());",
              "    }",
              "    if (ctx.isField()) {",
              "      return new KeepCondition(",
              "        classTypeName = ctx.getClassTypeName(),",
              "        fieldName = ctx.getFieldName()",
              "        fieldType = ctx.getFieldType());",
              "    }",
              "    // unreachable",
              "  }")
          .printDoc(this::println);
      printOpenAnnotationClassTargettingClassFieldlMethodCtor(getUnqualifiedName(USES_REFLECTION));
      println();
      withIndent(
          () -> {
            createDescriptionGroup().generate(this);
            println();
            createConsequencesAsValueGroup().generate(this);
            println();
            createAdditionalPreconditionsGroup().generate(this);
          });
      printCloseAnnotationClass();
    }

    private void generateUsedByX(String annotationClassName, String doc) {
      printCopyRight(2023);
      printPackage();
      printAnnotationImports();
      if (generateKotlin()) {
        println("import kotlin.reflect.KClass");
      }
      DocPrinter.printer()
          .setDocTitle("Annotation to mark a class, field or method as being " + doc + ".")
          .addParagraph(
              "Note: Before using this annotation, consider if instead you can annotate the code"
                  + " that is doing reflection with "
                  + docLink(USES_REFLECTION)
                  + ". Annotating the"
                  + " reflecting code is generally more clear and maintainable, and it also"
                  + " naturally gives rise to edges that describe just the reflected aspects of the"
                  + " program. The "
                  + docLink(USED_BY_REFLECTION)
                  + " annotation is suitable for cases where"
                  + " the reflecting code is not under user control, or in migrating away from"
                  + " rules.")
          .addParagraph(
              "When a class is annotated, member patterns can be used to define which members are"
                  + " to be kept. When no member patterns are specified the default pattern is to"
                  + " match just the class.")
          .addParagraph(
              "When a member is annotated, the member patterns cannot be used as the annotated"
                  + " member itself fully defines the item to be kept (i.e., itself).")
          .printDoc(this::println);
      printOpenAnnotationClassTargettingClassFieldlMethodCtor(annotationClassName);
      println();
      withIndent(
          () -> {
            createDescriptionGroup().generate(this);
            println();
            createPreconditionsGroup().generate(this);
            println();
            createAdditionalTargetsGroup(
                    "Additional targets to be kept in addition to the annotated class/members.")
                .generate(this);
            println();
            GroupMember kindProperty = getKindMember();
            kindProperty
                .clearDocLines()
                .addParagraph("If unspecified the default kind depends on the annotated item.")
                .addParagraph("When annotating a class the default kind is:")
                .addUnorderedList(
                    docEnumLink(KIND_ONLY_CLASS) + " if no member patterns are defined;",
                    docEnumLink(KIND_CLASS_AND_METHODS) + " if method patterns are defined;",
                    docEnumLink(KIND_CLASS_AND_FIELDS) + " if field patterns are defined;",
                    docEnumLink(KIND_CLASS_AND_MEMBERS) + " otherwise.")
                .addParagraph(
                    "When annotating a method the default kind is: "
                        + docEnumLink(KIND_ONLY_METHODS))
                .addParagraph(
                    "When annotating a field the default kind is: " + docEnumLink(KIND_ONLY_FIELDS))
                .addParagraph(
                    "It is not possible to use "
                        + docEnumLink(KIND_ONLY_CLASS)
                        + " if annotating a member.")
                .generate(this);
            println();
            forEachKeepConstraintGroups(
                g -> {
                  g.generate(this);
                  println();
                });
            generateMemberPropertiesNoBinding();
          });
      printCloseAnnotationClass();
    }

    private static String annoUnqualifiedName(ClassReference clazz) {
      return "@" + getUnqualifiedName(clazz);
    }

    private static String docLink(ClassReference clazz) {
      return "{@link " + getUnqualifiedName(clazz) + "}";
    }

    private static String docLink(GroupMember member) {
      return "{@link #" + member.name + "}";
    }

    private static String docEnumLink(EnumReference enumRef) {
      return "{@link " + getUnqualifiedName(enumRef.enumClass) + "#" + enumRef.enumValue + "}";
    }

    private static String docEnumLinkList(EnumReference... values) {
      return StringUtils.join(", ", values, v -> docEnumLink(v), BraceType.TUBORG);
    }

    private void generateConstants() {
      // The generated AnnotationConstants class is part of R8.
      printCopyRight(2023, true);
      println("package com.android.tools.r8.keepanno.ast;");
      printImports();
      DocPrinter.printer()
          .setDocTitle(
              "Utility class for referencing the various keep annotations and their structure.")
          .addParagraph(
              "Use of these references avoids polluting the Java namespace with imports of the java"
                  + " annotations which overlap in name with the actual semantic AST types.")
          .printDoc(this::println);
      println("public final class AnnotationConstants {");
      withIndent(
          () -> {
            // Root annotations.
            generateKeepEdgeConstants();
            generateKeepForApiConstants();
            generateUsesReflectionConstants();
            generateUsedByReflectionConstants();
            generateUsedByNativeConstants();
            generateCheckRemovedConstants();
            generateCheckOptimizedOutConstants();
            // Common item fields.
            generateItemConstants();
            // Inner annotation classes.
            generateBindingConstants();
            generateConditionConstants();
            generateTargetConstants();
            generateKindConstants();
            generateConstraintConstants();
            generateMemberAccessConstants();
            generateMethodAccessConstants();
            generateFieldAccessConstants();

            generateStringPatternConstants();
            generateTypePatternConstants();
            generateClassNamePatternConstants();
            generateInstanceOfPatternConstants();
            generateAnnotationPatternConstants();
          });
      println("}");
    }

    void generateGroupConstants(ClassReference annoType, List<Group> groups) {
      generateAnnotationConstants(annoType);
      groups.forEach(g -> g.generateConstants(this));
    }

    private void generateAnnotationConstants(ClassReference clazz) {
      String desc = clazz.getDescriptor();
      String desc_legacy =
          "Lcom/android/tools/r8/keepanno/annotations"
              + desc.substring(desc.lastIndexOf(DescriptorUtils.DESCRIPTOR_PACKAGE_SEPARATOR));
      println("private static final String DESCRIPTOR = " + quote(desc) + ";");
      println("private static final String DESCRIPTOR_LEGACY = " + quote(desc_legacy) + ";");
      println("public static boolean isDescriptor(String descriptor) {");
      println("  return DESCRIPTOR.equals(descriptor) || DESCRIPTOR_LEGACY.equals(descriptor);");
      println("}");
      println("public static String getDescriptor() {");
      println("  return DESCRIPTOR;");
      println("}");
    }

    List<Group> getKeepEdgeGroups() {
      return ImmutableList.of(
          createDescriptionGroup(),
          createBindingsGroup(),
          createPreconditionsGroup(),
          createConsequencesGroup());
    }

    private void generateKeepEdgeConstants() {
      println("public static final class Edge {");
      withIndent(() -> generateGroupConstants(KEEP_EDGE, getKeepEdgeGroups()));
      println("}");
      println();
    }

    List<Group> getKeepForApiGroups() {
      return ImmutableList.of(
          createDescriptionGroup(), createAdditionalTargetsGroup("."), createMemberAccessGroup());
    }

    private void generateKeepForApiConstants() {
      println("public static final class ForApi {");
      withIndent(() -> generateGroupConstants(KEEP_FOR_API, getKeepForApiGroups()));
      println("}");
      println();
    }

    List<Group> getUsesReflectionGroups() {
      return ImmutableList.of(
          createDescriptionGroup(),
          createConsequencesAsValueGroup(),
          createAdditionalPreconditionsGroup());
    }

    private void generateUsesReflectionConstants() {
      println("public static final class UsesReflection {");
      withIndent(() -> generateGroupConstants(USES_REFLECTION, getUsesReflectionGroups()));
      println("}");
      println();
    }

    List<Group> getUsedByReflectionGroups() {
      ImmutableList.Builder<Group> builder = ImmutableList.builder();
      builder.addAll(getItemGroups());
      builder.add(getKindGroup());
      forEachExtraUsedByReflectionGroup(builder::add);
      forEachKeepConstraintGroups(builder::add);
      return builder.build();
    }

    private void forEachExtraUsedByReflectionGroup(Consumer<Group> fn) {
      fn.accept(createDescriptionGroup());
      fn.accept(createPreconditionsGroup());
      fn.accept(createAdditionalTargetsGroup("."));
    }

    private void generateUsedByReflectionConstants() {
      println("public static final class UsedByReflection {");
      withIndent(
          () -> {
            generateAnnotationConstants(USED_BY_REFLECTION);
            forEachExtraUsedByReflectionGroup(g -> g.generateConstants(this));
          });
      println("}");
      println();
    }

    List<Group> getUsedByNativeGroups() {
      return getUsedByReflectionGroups();
    }

    private void generateUsedByNativeConstants() {
      println("public static final class UsedByNative {");
      withIndent(
          () -> {
            generateAnnotationConstants(USED_BY_NATIVE);
            println("// Content is the same as " + getUnqualifiedName(USED_BY_REFLECTION) + ".");
          });
      println("}");
      println();
    }

    private void generateCheckRemovedConstants() {
      println("public static final class CheckRemoved {");
      withIndent(
          () -> {
            generateAnnotationConstants(CHECK_REMOVED);
          });
      println("}");
      println();
    }

    private void generateCheckOptimizedOutConstants() {
      println("public static final class CheckOptimizedOut {");
      withIndent(
          () -> {
            generateAnnotationConstants(CHECK_OPTIMIZED_OUT);
          });
      println("}");
      println();
    }

    private List<Group> getItemGroups() {
      return ImmutableList.of(
          // Bindings.
          createClassBindingGroup(),
          createMemberBindingGroup(),
          // Classes.
          createClassNamePatternGroup(),
          createClassInstanceOfPatternGroup(),
          createClassAnnotatedByPatternGroup(),
          // Members.
          createMemberAnnotatedByGroup(),
          createMemberAccessGroup(),
          // Methods.
          createMethodAnnotatedByGroup(),
          createMethodAccessGroup(),
          createMethodNameGroup(),
          createMethodReturnTypeGroup(),
          createMethodParametersGroup(),
          // Fields.
          createFieldAnnotatedByGroup(),
          createFieldAccessGroup(),
          createFieldNameGroup(),
          createFieldTypeGroup());
    }

    private void generateItemConstants() {
      DocPrinter.printer()
          .setDocTitle("Item properties common to binding items, conditions and targets.")
          .printDoc(this::println);
      println("public static final class Item {");
      withIndent(() -> getItemGroups().forEach(g -> g.generateConstants(this)));
      println("}");
      println();
    }

    List<Group> getBindingGroups() {
      return ImmutableList.<Group>builder()
          .addAll(getItemGroups())
          .add(new Group("binding-name").addMember(bindingName()))
          .build();
    }

    private void generateBindingConstants() {
      println("public static final class Binding {");
      withIndent(
          () -> {
            generateAnnotationConstants(KEEP_BINDING);
            bindingName().generateConstants(this);
          });
      println("}");
      println();
    }

    List<Group> getConditionGroups() {
      return getItemGroups();
    }

    private void generateConditionConstants() {
      println("public static final class Condition {");
      withIndent(
          () -> {
            generateAnnotationConstants(KEEP_CONDITION);
          });
      println("}");
      println();
    }

    List<Group> getTargetGroups() {
      ImmutableList.Builder<Group> builder = ImmutableList.builder();
      builder.addAll(getItemGroups());
      forEachExtraTargetGroup(builder::add);
      return builder.build();
    }

    private void forEachExtraTargetGroup(Consumer<Group> fn) {
      fn.accept(getKindGroup());
      forEachKeepConstraintGroups(fn);
    }

    private void generateTargetConstants() {
      println("public static final class Target {");
      withIndent(
          () -> {
            generateAnnotationConstants(KEEP_TARGET);
            forEachExtraTargetGroup(g -> g.generateConstants(this));
          });
      println("}");
      println();
    }

    private void generateKindConstants() {
      println("public static final class Kind {");
      withIndent(
          () -> {
            generateAnnotationConstants(KEEP_ITEM_KIND);
            for (KeepItemKind value : KeepItemKind.values()) {
              if (value != KeepItemKind.DEFAULT) {
                println(
                    "public static final String "
                        + value.name()
                        + " = "
                        + quote(value.name())
                        + ";");
              }
            }
          });
      println("}");
      println();
    }

    private void generateConstraintConstants() {
      println("public static final class Constraints {");
      withIndent(
          () -> {
            generateAnnotationConstants(KEEP_CONSTRAINT);
            for (EnumReference constraint : KEEP_CONSTRAINT_VALUES) {
              println(
                  "public static final String "
                      + constraint.enumValue
                      + " = "
                      + quote(constraint.enumValue)
                      + ";");
            }
          });
      println("}");
      println();
    }

    private boolean isAccessPropertyNegation(EnumReference enumReference) {
      return enumReference.name().startsWith("NON_");
    }

    private boolean isMemberAccessProperty(EnumReference enumReference) {
      for (EnumReference memberAccessValue : MEMBER_ACCESS_VALUES) {
        if (memberAccessValue.enumValue.equals(enumReference.enumValue)) {
          return true;
        }
      }
      return false;
    }

    private void generateMemberAccessConstants() {
      println("public static final class MemberAccess {");
      withIndent(
          () -> {
            generateAnnotationConstants(MEMBER_ACCESS_FLAGS);
            println("public static final String NEGATION_PREFIX = \"NON_\";");
            for (EnumReference value : MEMBER_ACCESS_VALUES) {
              assert !isAccessPropertyNegation(value);
              assert isMemberAccessProperty(value);
              println(
                  "public static final String " + value.name() + " = " + quote(value.name()) + ";");
            }
          });
      println("}");
      println();
    }

    private void generateMethodAccessConstants() {
      println("public static final class MethodAccess {");
      withIndent(
          () -> {
            generateAnnotationConstants(METHOD_ACCESS_FLAGS);
            for (EnumReference value : METHOD_ACCESS_VALUES) {
              assert !isAccessPropertyNegation(value);
              assert !isMemberAccessProperty(value);
              println(
                  "public static final String " + value.name() + " = " + quote(value.name()) + ";");
            }
          });
      println("}");
      println();
    }

    private void generateFieldAccessConstants() {
      println("public static final class FieldAccess {");
      withIndent(
          () -> {
            generateAnnotationConstants(FIELD_ACCESS_FLAGS);
            for (EnumReference value : FIELD_ACCESS_VALUES) {
              assert !isAccessPropertyNegation(value);
              assert !isMemberAccessProperty(value);
              println(
                  "public static final String " + value.name() + " = " + quote(value.name()) + ";");
            }
          });
      println("}");
      println();
    }

    List<Group> getStringPatternGroups() {
      return ImmutableList.of(
          stringPatternExactGroup(), stringPatternPrefixGroup(), stringPatternSuffixGroup());
    }

    private void generateStringPatternConstants() {
      println("public static final class StringPattern {");
      withIndent(
          () -> {
            generateAnnotationConstants(STRING_PATTERN);
            getStringPatternGroups().forEach(g -> g.generateConstants(this));
          });
      println("}");
      println();
    }

    List<Group> getTypePatternGroups() {
      return ImmutableList.of(typePatternGroup());
    }

    private void generateTypePatternConstants() {
      println("public static final class TypePattern {");
      withIndent(
          () -> {
            generateAnnotationConstants(TYPE_PATTERN);
            getTypePatternGroups().forEach(g -> g.generateConstants((this)));
          });
      println("}");
      println();
    }

    private void generateClassNamePatternConstants() {
      println("public static final class ClassNamePattern {");
      withIndent(
          () -> {
            generateAnnotationConstants(CLASS_NAME_PATTERN);
            classNamePatternFullNameGroup().generateConstants(this);
            classNamePatternUnqualifiedNameGroup().generateConstants(this);
            classNamePatternPackageGroup().generateConstants(this);
          });
      println("}");
      println();
    }

    private void generateInstanceOfPatternConstants() {
      println("public static final class InstanceOfPattern {");
      withIndent(
          () -> {
            generateAnnotationConstants(INSTANCE_OF_PATTERN);
            instanceOfPatternInclusive().generateConstants(this);
            instanceOfPatternClassNamePattern().generateConstants(this);
          });
      println("}");
      println();
    }

    List<Group> getAnnotationPatternGroups() {
      return ImmutableList.of(
          annotationNameGroup(), new Group("retention").addMember(annotationRetention()));
    }

    private void generateAnnotationPatternConstants() {
      println("public static final class AnnotationPattern {");
      withIndent(
          () -> {
            generateAnnotationConstants(ANNOTATION_PATTERN);
            getAnnotationPatternGroups().forEach(g -> g.generateConstants(this));
          });
      println("}");
      println();
    }

    private static void copyNonGeneratedMethods() throws IOException {
      String[] nonGeneratedClasses =
          new String[] {
            "CheckOptimizedOut",
            "CheckRemoved",
            "ClassAccessFlags",
            "FieldAccessFlags",
            "KeepConstraint",
            "KeepEdge",
            "KeepItemKind",
            "KeepOption",
            "MemberAccessFlags",
            "MethodAccessFlags"
          };
      for (String clazz : nonGeneratedClasses) {
        Path from = source(classFromTypeName(R8_ANNO_PKG + "." + clazz));
        Path to = source(classFromTypeName(ANDROIDX_ANNO_PKG + "." + clazz));
        Path fromResolved = Paths.get(ToolHelper.getProjectRoot()).resolve(from);
        Path toResolved = Paths.get(ToolHelper.getProjectRoot()).resolve(to);
        List<String> lines = FileUtils.readAllLines(fromResolved);
        List<String> out = new ArrayList<>();
        out.add(getHeaderString(2025, null));

        // Remove the R8 Copyright header.
        lines = lines.subList(3, lines.size());
        out.addAll(
            lines.stream()
                .map(s -> StringUtils.replaceAll(s, R8_ANNO_PKG, ANDROIDX_ANNO_PKG))
                .collect(Collectors.toList()));
        FileUtils.writeTextFile(toResolved, out);
      }
    }

    private static void writeFile(Path file, Consumer<Generator> fn, BiConsumer<Path, String> write)
        throws IOException {
      ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
      PrintStream printStream = new PrintStream(byteStream);
      Generator generator = new Generator(printStream, R8_ANNO_PKG);
      fn.accept(generator);
      String formatted = byteStream.toString();
      if (file.toString().endsWith(".kt")) {
        formatted = CodeGenerationBase.kotlinFormatRawOutput(formatted);
      } else if (file.toString().endsWith(".java")) {
        formatted = CodeGenerationBase.javaFormatRawOutput(formatted);
      }
      Path resolved = Paths.get(ToolHelper.getProjectRoot()).resolve(file);
      write.accept(resolved, formatted);
    }

    private static void writeFile(
        String pkg,
        Function<Generator, ClassReference> f,
        Consumer<Generator> fn,
        BiConsumer<Path, String> write)
        throws IOException {
      ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
      PrintStream printStream = new PrintStream(byteStream);
      Generator generator = new Generator(printStream, pkg);
      fn.accept(generator);
      String formatted = byteStream.toString();
      Path file = source(f.apply(generator));
      if (file.toString().endsWith(".kt")) {
        formatted = CodeGenerationBase.kotlinFormatRawOutput(formatted);
      } else if (file.toString().endsWith(".java")) {
        formatted = CodeGenerationBase.javaFormatRawOutput(formatted);
      }
      Path resolved = Paths.get(ToolHelper.getProjectRoot()).resolve(file);
      write.accept(resolved, formatted);
    }

    public static Path source(ClassReference clazz) {
      Path keepAnnoSourcePath = Paths.get("src", "keepanno", "java");
      if (clazz.getBinaryName().startsWith("androidx/")) {
        return keepAnnoSourcePath.resolve(clazz.getBinaryName() + ".kt");
      } else {
        return keepAnnoSourcePath.resolve(clazz.getBinaryName() + ".java");
      }
    }

    public static void run(BiConsumer<Path, String> write) throws IOException {
      Path projectRoot = Paths.get(ToolHelper.getProjectRoot());
      writeFile(
          Paths.get("doc/keepanno-guide.md"),
          generator -> KeepAnnoMarkdownGenerator.generateMarkdownDoc(generator, projectRoot),
          write);

      writeFile(
          ANDROIDX_ANNO_PKG,
          generator -> generator.ANNOTATION_CONSTANTS,
          Generator::generateConstants,
          write);
      // Create a copy of the non-generated classes in the androidx namespace.
      // This is currently disabled. Will potentially be re-introduced by converting these classes
      // to Kotlin in the R8 keep anno namespace.
      // copyNonGeneratedMethods();
      for (String pkg : new String[] {R8_ANNO_PKG, ANDROIDX_ANNO_PKG}) {
        writeFile(
            pkg, generator -> generator.STRING_PATTERN, Generator::generateStringPattern, write);
        writeFile(pkg, generator -> generator.TYPE_PATTERN, Generator::generateTypePattern, write);
        writeFile(
            pkg,
            generator -> generator.CLASS_NAME_PATTERN,
            Generator::generateClassNamePattern,
            write);
        writeFile(
            pkg,
            generator -> generator.INSTANCE_OF_PATTERN,
            Generator::generateInstanceOfPattern,
            write);
        writeFile(
            pkg,
            generator -> generator.ANNOTATION_PATTERN,
            Generator::generateAnnotationPattern,
            write);
        writeFile(pkg, generator -> generator.KEEP_BINDING, Generator::generateKeepBinding, write);
        writeFile(pkg, generator -> generator.KEEP_TARGET, Generator::generateKeepTarget, write);
        writeFile(
            pkg, generator -> generator.KEEP_CONDITION, Generator::generateKeepCondition, write);
        writeFile(pkg, generator -> generator.KEEP_FOR_API, Generator::generateKeepForApi, write);
        writeFile(
            pkg, generator -> generator.USES_REFLECTION, Generator::generateUsesReflection, write);
        writeFile(
            pkg,
            generator -> generator.USED_BY_REFLECTION,
            generator -> generator.generateUsedByX("UsedByReflection", "accessed reflectively"),
            write);
        writeFile(
            pkg,
            generator -> generator.USED_BY_NATIVE,
            generator ->
                generator.generateUsedByX("UsedByNative", "accessed from native code via JNI"),
            write);
      }
    }
  }
}
