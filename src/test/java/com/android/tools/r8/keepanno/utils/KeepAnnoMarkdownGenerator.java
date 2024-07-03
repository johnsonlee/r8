// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.keepanno.utils;

import static com.android.tools.r8.keepanno.utils.KeepItemAnnotationGenerator.ANNOTATION_PATTERN;
import static com.android.tools.r8.keepanno.utils.KeepItemAnnotationGenerator.FIELD_ACCESS_FLAGS;
import static com.android.tools.r8.keepanno.utils.KeepItemAnnotationGenerator.FIELD_ACCESS_VALUES;
import static com.android.tools.r8.keepanno.utils.KeepItemAnnotationGenerator.KEEP_BINDING;
import static com.android.tools.r8.keepanno.utils.KeepItemAnnotationGenerator.KEEP_CONDITION;
import static com.android.tools.r8.keepanno.utils.KeepItemAnnotationGenerator.KEEP_CONSTRAINT;
import static com.android.tools.r8.keepanno.utils.KeepItemAnnotationGenerator.KEEP_CONSTRAINT_VALUES;
import static com.android.tools.r8.keepanno.utils.KeepItemAnnotationGenerator.KEEP_EDGE;
import static com.android.tools.r8.keepanno.utils.KeepItemAnnotationGenerator.KEEP_FOR_API;
import static com.android.tools.r8.keepanno.utils.KeepItemAnnotationGenerator.KEEP_ITEM_KIND;
import static com.android.tools.r8.keepanno.utils.KeepItemAnnotationGenerator.KEEP_ITEM_KIND_VALUES;
import static com.android.tools.r8.keepanno.utils.KeepItemAnnotationGenerator.KEEP_TARGET;
import static com.android.tools.r8.keepanno.utils.KeepItemAnnotationGenerator.MEMBER_ACCESS_FLAGS;
import static com.android.tools.r8.keepanno.utils.KeepItemAnnotationGenerator.MEMBER_ACCESS_VALUES;
import static com.android.tools.r8.keepanno.utils.KeepItemAnnotationGenerator.METHOD_ACCESS_FLAGS;
import static com.android.tools.r8.keepanno.utils.KeepItemAnnotationGenerator.METHOD_ACCESS_VALUES;
import static com.android.tools.r8.keepanno.utils.KeepItemAnnotationGenerator.STRING_PATTERN;
import static com.android.tools.r8.keepanno.utils.KeepItemAnnotationGenerator.TYPE_PATTERN;
import static com.android.tools.r8.keepanno.utils.KeepItemAnnotationGenerator.USED_BY_NATIVE;
import static com.android.tools.r8.keepanno.utils.KeepItemAnnotationGenerator.USED_BY_REFLECTION;
import static com.android.tools.r8.keepanno.utils.KeepItemAnnotationGenerator.USES_REFLECTION;
import static com.android.tools.r8.keepanno.utils.KeepItemAnnotationGenerator.getUnqualifiedName;
import static com.android.tools.r8.keepanno.utils.KeepItemAnnotationGenerator.quote;

import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.keepanno.doctests.ForApiDocumentationTest;
import com.android.tools.r8.keepanno.doctests.GenericSignaturePrinter;
import com.android.tools.r8.keepanno.doctests.MainMethodsDocumentationTest;
import com.android.tools.r8.keepanno.doctests.UsesReflectionAnnotationsDocumentationTest;
import com.android.tools.r8.keepanno.doctests.UsesReflectionDocumentationTest;
import com.android.tools.r8.keepanno.utils.KeepItemAnnotationGenerator.EnumReference;
import com.android.tools.r8.keepanno.utils.KeepItemAnnotationGenerator.Generator;
import com.android.tools.r8.keepanno.utils.KeepItemAnnotationGenerator.Group;
import com.android.tools.r8.keepanno.utils.KeepItemAnnotationGenerator.GroupMember;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class KeepAnnoMarkdownGenerator {

  public static void generateMarkdownDoc(Generator generator, Path projectRoot) {
    try {
      new KeepAnnoMarkdownGenerator(generator).internalGenerateMarkdownDoc(projectRoot);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static String JAVADOC_URL =
      "https://storage.googleapis.com/r8-releases/raw/main/docs/keepanno/javadoc/";

  private static final String TOC_MARKER = "[[[TOC]]]";

  private static final String INCLUDE_MD_START = "[[[INCLUDE";
  private static final String INCLUDE_MD_DOC_START = "[[[INCLUDE DOC";
  private static final String INCLUDE_MD_CODE_START = "[[[INCLUDE CODE";
  private static final String INCLUDE_MD_END = "]]]";

  private static final String INCLUDE_DOC_START = "INCLUDE DOC:";
  private static final String INCLUDE_DOC_END = "INCLUDE END";
  private static final String INCLUDE_CODE_START = "INCLUDE CODE:";
  private static final String INCLUDE_CODE_END = "INCLUDE END";

  private final Generator generator;
  private final Map<String, String> typeLinkReplacements;
  private Map<String, String> docReplacements = new HashMap<>();
  private Map<String, String> codeReplacements = new HashMap<>();

  public KeepAnnoMarkdownGenerator(Generator generator) {
    this.generator = generator;
    ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
    // Annotations.
    addAnnotationReplacements(KEEP_EDGE, builder, generator.getKeepEdgeGroups());
    addAnnotationReplacements(KEEP_BINDING, builder, generator.getBindingGroups());
    addAnnotationReplacements(KEEP_TARGET, builder, generator.getTargetGroups());
    addAnnotationReplacements(KEEP_CONDITION, builder, generator.getConditionGroups());
    addAnnotationReplacements(USES_REFLECTION, builder, generator.getUsesReflectionGroups());
    addAnnotationReplacements(USED_BY_REFLECTION, builder, generator.getUsedByReflectionGroups());
    addAnnotationReplacements(USED_BY_NATIVE, builder, generator.getUsedByNativeGroups());
    addAnnotationReplacements(KEEP_FOR_API, builder, generator.getKeepForApiGroups());
    addAnnotationReplacements(STRING_PATTERN, builder, generator.getStringPatternGroups());
    addAnnotationReplacements(TYPE_PATTERN, builder, generator.getTypePatternGroups());
    addAnnotationReplacements(ANNOTATION_PATTERN, builder, generator.getAnnotationPatternGroups());

    // Enums.
    addEnumReplacements(KEEP_ITEM_KIND, KEEP_ITEM_KIND_VALUES, builder);
    addEnumReplacements(KEEP_CONSTRAINT, KEEP_CONSTRAINT_VALUES, builder);
    addEnumReplacements(MEMBER_ACCESS_FLAGS, MEMBER_ACCESS_VALUES, builder);
    addEnumReplacements(METHOD_ACCESS_FLAGS, METHOD_ACCESS_VALUES, builder);
    addEnumReplacements(FIELD_ACCESS_FLAGS, FIELD_ACCESS_VALUES, builder);

    typeLinkReplacements = builder.build();
    populateCodeAndDocReplacements(
        UsesReflectionDocumentationTest.class,
        UsesReflectionAnnotationsDocumentationTest.class,
        ForApiDocumentationTest.class,
        MainMethodsDocumentationTest.class,
        GenericSignaturePrinter.class);
  }

  private static String getPrefix(ClassReference annoType) {
    return "`@" + getUnqualifiedName(annoType);
  }

  private static String getSuffix() {
    return "`";
  }

  private void addAnnotationReplacements(
      ClassReference annoType, ImmutableMap.Builder<String, String> builder, List<Group> groups) {
    String prefix = getPrefix(annoType);
    String suffix = getSuffix();
    builder.put(prefix + suffix, getMdAnnotationLink(annoType));
    for (Group group : groups) {
      for (GroupMember member : group.members) {
        builder.put(
            prefix + "#" + member.name + suffix, getMdAnnotationPropertyLink(annoType, member));
      }
    }
  }

  private void addEnumReplacements(
      ClassReference enumType,
      List<EnumReference> enumMembers,
      ImmutableMap.Builder<String, String> builder) {
    String prefix = getPrefix(enumType);
    String suffix = getSuffix();
    builder.put(prefix + suffix, getMdEnumLink(enumType));
    for (EnumReference enumMember : enumMembers) {
      builder.put(prefix + "#" + enumMember.name() + suffix, getMdEnumMemberLink(enumMember));
    }
  }

  private void populateCodeAndDocReplacements(Class<?>... classes) {
    for (Class<?> clazz : classes) {
      Path sourceFile = ToolHelper.getSourceFileForTestClass(clazz);
      extractMarkers(sourceFile);
    }
  }

  private void extractMarkers(Path sourceFile) {
    try {
      String text = FileUtils.readTextFile(sourceFile, StandardCharsets.UTF_8);
      extractMarkers(text, INCLUDE_DOC_START, INCLUDE_DOC_END, docReplacements);
      extractMarkers(text, INCLUDE_CODE_START, INCLUDE_CODE_END, codeReplacements);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static void extractMarkers(
      String text, String markerKeyStart, String markerKeyEnd, Map<String, String> replacementMap) {
    int index = text.indexOf(markerKeyStart);
    while (index >= 0) {
      int markerTitleEnd = text.indexOf('\n', index);
      if (markerTitleEnd < 0) {
        throw new RuntimeException("Failed to find end marker title");
      }
      int end = text.indexOf(markerKeyEnd, index);
      if (end < 0) {
        throw new RuntimeException("Failed to find end marker");
      }
      int endBeforeNewLine = text.lastIndexOf('\n', end);
      if (endBeforeNewLine < markerTitleEnd) {
        throw new RuntimeException("No new-line before end marker");
      }
      String markerTitle = text.substring(index + markerKeyStart.length(), markerTitleEnd);
      String includeContent = text.substring(markerTitleEnd + 1, endBeforeNewLine);
      String old = replacementMap.put(markerTitle.trim(), includeContent);
      if (old != null) {
        throw new RuntimeException("Duplicate definition of marker");
      }
      index = text.indexOf(markerKeyStart, end);
    }
  }

  private static String getClassJavaDocUrl(ClassReference clazz) {
    return JAVADOC_URL + clazz.getTypeName().replace('.', '/') + ".html";
  }

  private String getMdAnnotationLink(ClassReference clazz) {
    return "[@" + getUnqualifiedName(clazz) + "](" + getClassJavaDocUrl(clazz) + ")";
  }

  private String getMdAnnotationPropertyLink(ClassReference clazz, GroupMember method) {
    String methodName = method.name;
    String url = getClassJavaDocUrl(clazz) + "#" + methodName + "()";
    return "[@" + getUnqualifiedName(clazz) + "." + methodName + "](" + url + ")";
  }

  private String getMdEnumLink(ClassReference clazz) {
    return "[" + getUnqualifiedName(clazz) + "](" + getClassJavaDocUrl(clazz) + ")";
  }

  private String getMdEnumMemberLink(EnumReference enumMember) {
    ClassReference clazz = enumMember.enumClass;
    String enumName = enumMember.name();
    String url = getClassJavaDocUrl(clazz) + "#" + enumName;
    return "[" + getUnqualifiedName(clazz) + "." + enumName + "](" + url + ")";
  }

  private void println() {
    generator.println("");
  }

  private void println(String line) {
    generator.println(line);
  }

  private void internalGenerateMarkdownDoc(Path projectRoot) throws IOException {
    String relativeUnixPath = "doc/keepanno-guide.template.md";
    Path template = projectRoot.resolve(relativeUnixPath);
    println("[comment]: <> (DO NOT EDIT - GENERATED FILE)");
    println("[comment]: <> (Changes should be made in " + relativeUnixPath + ")");
    println();
    List<String> readAllLines = FileUtils.readAllLines(template);
    TableEntry root = new TableEntry(0, "root", "root", null);
    readAllLines = collectTableOfContents(readAllLines, root);

    for (int i = 0; i < readAllLines.size(); i++) {
      String line = readAllLines.get(i);
      try {
        if (line.trim().equals(TOC_MARKER)) {
          printTableOfContents(root);
        } else {
          processLine(line, generator);
        }
      } catch (Exception e) {
        System.err.println("Parse error on line " + (i + 1) + ":");
        System.err.println(line);
        System.err.println(e.getMessage());
      }
    }
  }

  private void printTableOfContents(TableEntry root) {
    println("## Table of contents");
    println();
    printTableSubEntries(root.subSections.values());
    println();
  }

  private void printTableSubEntries(Collection<TableEntry> entries) {
    for (TableEntry entry : entries) {
      println("- " + entry.getHrefLink());
      generator.withIndent(() -> printTableSubEntries(entry.subSections.values()));
    }
  }

  private List<String> collectTableOfContents(List<String> lines, TableEntry root) {
    Set<String> seen = new HashSet<>();
    TableEntry current = root;
    List<String> newLines = new ArrayList<>(lines.size());
    Iterator<String> iterator = lines.iterator();
    // Skip forward until the TOC insertion.
    while (iterator.hasNext()) {
      String line = iterator.next();
      newLines.add(line);
      if (line.trim().equals(TOC_MARKER)) {
        break;
      }
    }
    // Find TOC entries and replace the headings with links.
    while (iterator.hasNext()) {
      String line = iterator.next();
      int headingDepth = 0;
      for (int i = 0; i < line.length(); i++) {
        char c = line.charAt(i);
        if (c != '#') {
          headingDepth = i;
          break;
        }
      }
      if (headingDepth == 0) {
        newLines.add(line);
        continue;
      }
      String headingPrefix = line.substring(0, headingDepth);
      String headingContent = line.substring(headingDepth).trim();
      int splitIndex = headingContent.indexOf("](");
      if (splitIndex < 0 || !headingContent.startsWith("[") || !headingContent.endsWith(")")) {
        throw new RuntimeException("Invalid heading format. Use [Heading Text](heading-id)");
      }
      String headingText = headingContent.substring(1, splitIndex);
      String headingId = headingContent.substring(splitIndex + 2, headingContent.length() - 1);
      if (!seen.add(headingId)) {
        throw new RuntimeException("Duplicate heading id: " + headingText);
      }
      while (headingDepth <= current.depth) {
        current = current.parent;
      }
      TableEntry entry = new TableEntry(headingDepth, headingText, headingId, current);
      current.subSections.put(headingText, entry);
      current = entry;
      newLines.add(headingPrefix + " " + entry.getIdAnchor());
    }
    return newLines;
  }

  private String replaceCodeAndDocMarkers(String line) {
    String originalLine = line;
    line = line.trim();
    if (!line.startsWith(INCLUDE_MD_START)) {
      return originalLine;
    }
    int keyStartIndex = line.indexOf(':');
    if (!line.endsWith(INCLUDE_MD_END) || keyStartIndex < 0) {
      throw new RuntimeException("Invalid include directive");
    }
    String key = line.substring(keyStartIndex + 1, line.length() - INCLUDE_MD_END.length());
    if (line.startsWith(INCLUDE_MD_DOC_START)) {
      return replaceDoc(key);
    }
    if (line.startsWith(INCLUDE_MD_CODE_START)) {
      return replaceCode(key);
    }
    throw new RuntimeException("Unknown replacement marker");
  }

  private String replaceDoc(String key) {
    String replacement = docReplacements.get(key);
    if (replacement == null) {
      throw new RuntimeException("No replacement defined for " + key);
    }
    return unindentLines(replacement, new StringBuilder()).toString();
  }

  private String replaceCode(String key) {
    String replacement = codeReplacements.get(key);
    if (replacement == null) {
      throw new RuntimeException("No replacement defined for " + key);
    }
    StringBuilder builder = new StringBuilder();
    builder.append("```\n");
    unindentLines(replacement, builder);
    builder.append("```\n");
    return builder.toString();
  }

  private StringBuilder unindentLines(String replacement, StringBuilder builder) {
    int shortestSpacePrefix = Integer.MAX_VALUE;
    List<String> lines = StringUtils.split(replacement, '\n');
    lines = trimEmptyLines(lines);
    for (String line : lines) {
      if (!line.isEmpty()) {
        shortestSpacePrefix = Math.min(shortestSpacePrefix, findFirstNonSpaceIndex(line));
      }
    }
    for (String line : lines) {
      if (!line.isEmpty()) {
        builder.append(line.substring(shortestSpacePrefix));
      }
      builder.append('\n');
    }
    return builder;
  }

  private static List<String> trimEmptyLines(List<String> lines) {
    int startLineIndex = 0;
    int endLineIndex = lines.size() - 1;
    while (true) {
      String line = lines.get(startLineIndex);
      if (line.trim().isEmpty()) {
        startLineIndex++;
      } else {
        break;
      }
    }
    while (true) {
      String line = lines.get(endLineIndex);
      if (line.trim().isEmpty()) {
        --endLineIndex;
      } else {
        break;
      }
    }
    if (startLineIndex != 0 || endLineIndex != lines.size() - 1) {
      lines = lines.subList(startLineIndex, endLineIndex + 1);
    }
    return lines;
  }

  private int findFirstNonSpaceIndex(String line) {
    for (int i = 0; i < line.length(); i++) {
      if (line.charAt(i) != ' ') {
        return i;
      }
    }
    return line.length();
  }

  private String tryLinkReplacements(String line) {
    int index = line.indexOf("`@");
    if (index < 0) {
      return null;
    }
    int end = line.indexOf('`', index + 1);
    if (end < 0) {
      throw new RuntimeException("No end marker on line: " + line);
    }
    String typeLink = line.substring(index, end + 1);
    String replacement = typeLinkReplacements.get(typeLink);
    if (replacement == null) {
      throw new RuntimeException("Unknown type link: " + typeLink);
    }
    return line.replace(typeLink, replacement);
  }

  private void processLine(String line, Generator generator) {
    line = replaceCodeAndDocMarkers(line);
    String replacement = tryLinkReplacements(line);
    while (replacement != null) {
      line = replacement;
      replacement = tryLinkReplacements(line);
    }
    generator.println(line);
  }

  private static class TableEntry {
    final int depth;
    final String name;
    final String id;
    final TableEntry parent;
    final Map<String, TableEntry> subSections = new LinkedHashMap<>();

    public TableEntry(int depth, String name, String id, TableEntry parent) {
      this.depth = depth;
      this.name = name;
      this.id = id;
      this.parent = parent;
    }

    public String getHrefLink() {
      return "[" + name + "](#" + id + ")";
    }

    public String getIdAnchor() {
      return name + "<a name=" + quote(id) + "></a>";
    }
  }
}
