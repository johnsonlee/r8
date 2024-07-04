// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.keeprules;

import com.android.tools.r8.keepanno.ast.AccessVisibility;
import com.android.tools.r8.keepanno.ast.KeepArrayTypePattern;
import com.android.tools.r8.keepanno.ast.KeepClassItemPattern;
import com.android.tools.r8.keepanno.ast.KeepEdgeException;
import com.android.tools.r8.keepanno.ast.KeepEdgeMetaInfo;
import com.android.tools.r8.keepanno.ast.KeepFieldAccessPattern;
import com.android.tools.r8.keepanno.ast.KeepFieldNamePattern;
import com.android.tools.r8.keepanno.ast.KeepFieldPattern;
import com.android.tools.r8.keepanno.ast.KeepInstanceOfPattern;
import com.android.tools.r8.keepanno.ast.KeepMemberAccessPattern;
import com.android.tools.r8.keepanno.ast.KeepMemberPattern;
import com.android.tools.r8.keepanno.ast.KeepMethodAccessPattern;
import com.android.tools.r8.keepanno.ast.KeepMethodNamePattern;
import com.android.tools.r8.keepanno.ast.KeepMethodParametersPattern;
import com.android.tools.r8.keepanno.ast.KeepMethodPattern;
import com.android.tools.r8.keepanno.ast.KeepMethodReturnTypePattern;
import com.android.tools.r8.keepanno.ast.KeepOptions;
import com.android.tools.r8.keepanno.ast.KeepOptions.KeepOption;
import com.android.tools.r8.keepanno.ast.KeepPackageComponentPattern;
import com.android.tools.r8.keepanno.ast.KeepPackagePattern;
import com.android.tools.r8.keepanno.ast.KeepPrimitiveTypePattern;
import com.android.tools.r8.keepanno.ast.KeepQualifiedClassNamePattern;
import com.android.tools.r8.keepanno.ast.KeepStringPattern;
import com.android.tools.r8.keepanno.ast.KeepTypePattern;
import com.android.tools.r8.keepanno.ast.KeepUnqualfiedClassNamePattern;
import com.android.tools.r8.keepanno.ast.ModifierPattern;
import com.android.tools.r8.keepanno.ast.OptionalPattern;
import com.android.tools.r8.keepanno.utils.Unimplemented;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

public abstract class RulePrintingUtils {

  public static final String IF = "-if";
  public static final String KEEP = "-keep";
  public static final String KEEP_CLASS_MEMBERS = "-keepclassmembers";
  public static final String KEEP_CLASSES_WITH_MEMBERS = "-keepclasseswithmembers";
  public static final String KEEP_ATTRIBUTES = "-keepattributes";
  public static final String CHECK_DISCARD = "-checkdiscard";

  public static void printHeader(StringBuilder builder, KeepEdgeMetaInfo metaInfo) {
    if (metaInfo.hasContext()) {
      builder.append("# context: ").append(metaInfo.getContextDescriptorString()).append('\n');
    }
    if (metaInfo.hasDescription()) {
      String escapedDescription = escapeLineBreaks(metaInfo.getDescriptionString());
      builder.append("# description: ").append(escapedDescription).append('\n');
    }
  }

  public static String escapeChar(char c) {
    if (c == '\n') {
      return "\\n";
    }
    if (c == '\r') {
      return "\\r";
    }
    return null;
  }

  public static String escapeLineBreaks(String string) {
    char[] charArray = string.toCharArray();
    for (int i = 0; i < charArray.length; i++) {
      // We don't expect escape chars, so wait with constructing a new string until found.
      if (escapeChar(charArray[i]) != null) {
        StringBuilder builder = new StringBuilder(string.substring(0, i));
        for (int j = i; j < charArray.length; j++) {
          char c = charArray[j];
          String escaped = escapeChar(c);
          if (escaped != null) {
            builder.append(escaped);
          } else {
            builder.append(c);
          }
        }
        return builder.toString();
      }
    }
    return string;
  }

  public static void printKeepOptions(
      StringBuilder builder, KeepOptions options, KeepRuleExtractorOptions extractorOptions) {
    for (KeepOption option : KeepOption.values()) {
      if (options.isAllowed(option) && extractorOptions.isKeepOptionSupported(option)) {
        builder.append(",allow").append(getOptionString(option));
      }
    }
  }

  public static StringBuilder printClassHeader(
      StringBuilder builder,
      KeepClassItemPattern classPattern,
      BiConsumer<StringBuilder, KeepQualifiedClassNamePattern> printClassName) {
    OptionalPattern<KeepQualifiedClassNamePattern> annotatedByPattern =
        classPattern.getAnnotatedByPattern();
    if (annotatedByPattern.isPresent()) {
      builder.append("@");
      printClassName(annotatedByPattern.get(), RulePrinter.withoutBackReferences(builder));
      builder.append(" ");
    }
    builder.append("class ");
    printClassName.accept(builder, classPattern.getClassNamePattern());
    KeepInstanceOfPattern extendsPattern = classPattern.getInstanceOfPattern();
    if (!extendsPattern.isAny()) {
      builder.append(" extends ");
      printClassName(
          extendsPattern.getClassNamePattern(), RulePrinter.withoutBackReferences(builder));
    }
    return builder;
  }

  public static RulePrinter printMemberClause(
      KeepMemberPattern member, RulePrinter printer, KeepRuleExtractorOptions options) {
    if (member.isAllMembers()) {
      // Note: the rule language does not allow backref to a full member. A rule matching all
      // members via a binding must be split in two up front: one for methods and one for fields.
      return printer.appendWithoutBackReferenceAssert("*").append(";");
    }
    if (member.getAnnotatedByPattern().isPresent()) {
      printer.append("@");
      printClassName(member.getAnnotatedByPattern().get(), printer);
      printer.append(" ");
    }
    if (member.isMethod()) {
      return printMethod(member.asMethod(), printer, options);
    }
    if (member.isField()) {
      return printField(member.asField(), printer, options);
    }
    // The pattern is a restricted member pattern, e.g., it must apply to fields and methods
    // without any specifics not common to both. For now that is annotated-by and access patterns.
    assert !member.getAccessPattern().isAny() || member.getAnnotatedByPattern().isPresent();
    printMemberAccess(printer, member.getAccessPattern());
    return printer.appendWithoutBackReferenceAssert("*").append(";");
  }

  private static RulePrinter printField(
      KeepFieldPattern fieldPattern, RulePrinter printer, KeepRuleExtractorOptions options) {
    printFieldAccess(printer, fieldPattern.getAccessPattern());
    printType(
        printer.allowBackReferencesIf(options.hasFieldTypeBackReference()),
        fieldPattern.getTypePattern().asType());
    printer.append(" ");
    printFieldName(printer, fieldPattern.getNamePattern());
    return printer.append(";");
  }

  private static RulePrinter printMethod(
      KeepMethodPattern methodPattern, RulePrinter printer, KeepRuleExtractorOptions options) {
    printMethodAccess(printer, methodPattern.getAccessPattern());
    printReturnType(
        printer.allowBackReferencesIf(options.hasMethodReturnTypeBackReference()),
        methodPattern.getReturnTypePattern());
    printer.append(" ");
    printMethodName(printer, methodPattern.getNamePattern());
    printParameters(printer, methodPattern.getParametersPattern(), options);
    return printer.append(";");
  }

  private static RulePrinter printParameters(
      RulePrinter builder,
      KeepMethodParametersPattern parametersPattern,
      KeepRuleExtractorOptions options) {
    if (parametersPattern.isAny()) {
      return builder
          .allowBackReferencesIf(options.hasMethodParameterListBackReference())
          .appendAnyParameters();
    }
    builder.append("(");
    List<KeepTypePattern> patterns = parametersPattern.asList();
    for (int i = 0; i < patterns.size(); i++) {
      if (i > 0) {
        builder.append(", ");
      }
      printType(
          builder.allowBackReferencesIf(options.hasMethodParameterTypeBackReference()),
          patterns.get(i));
    }
    return builder.append(")");
  }

  private static RulePrinter printStringPattern(RulePrinter printer, KeepStringPattern pattern) {
    if (pattern.isExact()) {
      return printer.append(pattern.asExactString());
    }
    if (pattern.hasPrefix()) {
      printer.append(pattern.getPrefixString());
    }
    printer.appendStar();
    if (pattern.hasSuffix()) {
      printer.append(pattern.getSuffixString());
    }
    return printer;
  }

  private static RulePrinter printFieldName(RulePrinter builder, KeepFieldNamePattern namePattern) {
    return printStringPattern(builder, namePattern.asStringPattern());
  }

  private static RulePrinter printMethodName(
      RulePrinter builder, KeepMethodNamePattern namePattern) {
    return printStringPattern(builder, namePattern.asStringPattern());
  }

  private static RulePrinter printReturnType(
      RulePrinter builder, KeepMethodReturnTypePattern returnTypePattern) {
    if (returnTypePattern.isVoid()) {
      return builder.append("void");
    }
    return printType(builder, returnTypePattern.asType());
  }

  private static RulePrinter printType(RulePrinter printer, KeepTypePattern typePattern) {
    return typePattern.apply(
        printer::appendTripleStar,
        primitivePattern -> printPrimitiveType(printer, primitivePattern),
        arrayTypePattern -> printArrayType(printer, arrayTypePattern),
        classTypePattern -> {
          if (!classTypePattern.getInstanceOfPattern().isAny()) {
            throw new KeepEdgeException(
                "Type patterns with instance-of are not supported in rule extraction");
          }
          return printClassName(classTypePattern.getClassNamePattern(), printer);
        });
  }

  private static RulePrinter printPrimitiveType(
      RulePrinter printer, KeepPrimitiveTypePattern primitiveTypePattern) {
    if (primitiveTypePattern.isAny()) {
      // Matching any primitive type uses the wildcard syntax `%`
      return printer.appendPercent();
    }
    return printer.append(descriptorToJavaType(primitiveTypePattern.getDescriptor()));
  }

  private static RulePrinter printArrayType(
      RulePrinter printer, KeepArrayTypePattern arrayTypePattern) {
    // The "any" array is simply dimension one of any type. Just assert that to be true as the
    // general case will emit the correct syntax: ***[]
    assert !arrayTypePattern.isAny()
        || (arrayTypePattern.getDimensions() == 1 && arrayTypePattern.getBaseType().isAny());
    printType(printer, arrayTypePattern.getBaseType());
    for (int i = 0; i < arrayTypePattern.getDimensions(); i++) {
      printer.append("[]");
    }
    return printer;
  }

  public static RulePrinter printMemberAccess(
      RulePrinter printer, KeepMemberAccessPattern accessPattern) {
    if (accessPattern.isAny()) {
      // No text will match any access pattern.
      // Don't print the indent in this case.
      return printer;
    }
    printVisibilityModifiers(printer, accessPattern);
    printModifier(printer, accessPattern.getStaticPattern(), "static");
    printModifier(printer, accessPattern.getFinalPattern(), "final");
    printModifier(printer, accessPattern.getSyntheticPattern(), "synthetic");
    return printer;
  }

  public static void printVisibilityModifiers(
      RulePrinter printer, KeepMemberAccessPattern accessPattern) {
    if (accessPattern.isAnyVisibility()) {
      return;
    }
    Set<AccessVisibility> allowed = accessPattern.getAllowedAccessVisibilities();
    // Package private does not have an actual representation it must be matched by its absence.
    // Thus, in the case of package-private the match is the negation of those not-present.
    boolean negated = allowed.contains(AccessVisibility.PACKAGE_PRIVATE);
    for (AccessVisibility visibility : AccessVisibility.values()) {
      if (!visibility.equals(AccessVisibility.PACKAGE_PRIVATE)) {
        if (!negated == allowed.contains(visibility)) {
          if (negated) {
            printer.append("!");
          }
          printer.append(visibility.toSourceSyntax()).append(" ");
        }
      }
    }
  }

  public static void printModifier(
      RulePrinter printer, ModifierPattern modifierPattern, String syntax) {
    if (modifierPattern.isAny()) {
      return;
    }
    if (modifierPattern.isOnlyNegative()) {
      printer.append("!");
    }
    printer.append(syntax).append(" ");
  }

  public static RulePrinter printMethodAccess(
      RulePrinter printer, KeepMethodAccessPattern accessPattern) {
    printMemberAccess(printer, accessPattern);
    printModifier(printer, accessPattern.getSynchronizedPattern(), "synchronized");
    printModifier(printer, accessPattern.getBridgePattern(), "bridge");
    printModifier(printer, accessPattern.getNativePattern(), "native");
    printModifier(printer, accessPattern.getAbstractPattern(), "abstract");
    printModifier(printer, accessPattern.getStrictFpPattern(), "strictfp");
    return printer;
  }

  public static RulePrinter printFieldAccess(
      RulePrinter printer, KeepFieldAccessPattern accessPattern) {
    printMemberAccess(printer, accessPattern);
    RulePrintingUtils.printModifier(printer, accessPattern.getVolatilePattern(), "volatile");
    RulePrintingUtils.printModifier(printer, accessPattern.getTransientPattern(), "transient");
    return printer;
  }

  public static RulePrinter printClassName(
      KeepQualifiedClassNamePattern classNamePattern, RulePrinter printer) {
    if (classNamePattern.isAny()) {
      return printer.appendDoubleStar();
    }
    printPackagePrefix(classNamePattern.getPackagePattern(), printer);
    return printSimpleClassName(classNamePattern.getNamePattern(), printer);
  }

  private static RulePrinter printPackagePrefix(
      KeepPackagePattern packagePattern, RulePrinter builder) {
    if (packagePattern.isAny()) {
      return builder.appendDoubleStar().append(".");
    }
    if (packagePattern.isTop()) {
      return builder;
    }
    for (KeepPackageComponentPattern component : packagePattern.getComponents()) {
      if (component.isZeroOrMore()) {
        throw new KeepEdgeException("Unsupported use of zero-or-more package pattern");
      }
      printStringPattern(builder, component.getSinglePattern());
      builder.append(".");
    }
    return builder;
  }

  private static RulePrinter printSimpleClassName(
      KeepUnqualfiedClassNamePattern namePattern, RulePrinter builder) {
    return printStringPattern(builder, namePattern.asStringPattern());
  }

  private static String getOptionString(KeepOption option) {
    switch (option) {
      case SHRINKING:
        return "shrinking";
      case OPTIMIZING:
        return "optimization";
      case OBFUSCATING:
        return "obfuscation";
      case ACCESS_MODIFICATION:
        return "accessmodification";
      case ANNOTATION_REMOVAL:
        return "annotationremoval";
      case SIGNATURE_REMOVAL:
        return "signatureremoval";
      default:
        throw new Unimplemented();
    }
  }

  private static String descriptorToJavaType(String descriptor) {
    if (descriptor.isEmpty()) {
      throw new KeepEdgeException("Invalid empty type descriptor");
    }
    if (descriptor.length() == 1) {
      return primitiveDescriptorToJavaType(descriptor.charAt(0));
    }
    if (descriptor.charAt(0) == '[') {
      return arrayDescriptorToJavaType(descriptor);
    }
    return classDescriptorToJavaType(descriptor);
  }

  private static String primitiveDescriptorToJavaType(char descriptor) {
    switch (descriptor) {
      case 'Z':
        return "boolean";
      case 'B':
        return "byte";
      case 'S':
        return "short";
      case 'I':
        return "int";
      case 'J':
        return "long";
      case 'F':
        return "float";
      case 'D':
        return "double";
      default:
        throw new KeepEdgeException("Invalid primitive descriptor: " + descriptor);
    }
  }

  private static String classDescriptorToJavaType(String descriptor) {
    int last = descriptor.length() - 1;
    if (descriptor.charAt(0) != 'L' || descriptor.charAt(last) != ';') {
      throw new KeepEdgeException("Invalid class descriptor: " + descriptor);
    }
    return descriptor.substring(1, last).replace('/', '.');
  }

  private static String arrayDescriptorToJavaType(String descriptor) {
    for (int i = 0; i < descriptor.length(); i++) {
      char c = descriptor.charAt(i);
      if (c != '[') {
        StringBuilder builder = new StringBuilder();
        builder.append(descriptorToJavaType(descriptor.substring(i)));
        for (int j = 0; j < i; j++) {
          builder.append("[]");
        }
        return builder.toString();
      }
    }
    throw new KeepEdgeException("Invalid array descriptor: " + descriptor);
  }
}
