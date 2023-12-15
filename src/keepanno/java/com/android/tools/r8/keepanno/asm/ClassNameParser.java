// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.keepanno.asm;

import com.android.tools.r8.keepanno.asm.ClassNameParser.ClassNameProperty;
import com.android.tools.r8.keepanno.asm.ClassSimpleNameParser.ClassSimpleNameProperty;
import com.android.tools.r8.keepanno.asm.PackageNameParser.PackageNameProperty;
import com.android.tools.r8.keepanno.ast.AnnotationConstants.ClassNamePattern;
import com.android.tools.r8.keepanno.ast.KeepPackagePattern;
import com.android.tools.r8.keepanno.ast.KeepQualifiedClassNamePattern;
import com.android.tools.r8.keepanno.ast.KeepUnqualfiedClassNamePattern;
import com.google.common.collect.ImmutableList;
import java.util.function.Consumer;
import org.objectweb.asm.AnnotationVisitor;

public class ClassNameParser
    extends PropertyParserBase<KeepQualifiedClassNamePattern, ClassNameProperty, ClassNameParser> {

  public enum ClassNameProperty {
    PATTERN
  }

  @Override
  public ClassNameParser self() {
    return this;
  }

  @Override
  AnnotationVisitor tryPropertyAnnotation(
      ClassNameProperty property,
      String name,
      String descriptor,
      Consumer<KeepQualifiedClassNamePattern> setValue) {
    switch (property) {
      case PATTERN:
        {
          PackageNameParser packageParser =
              new PackageNameParser()
                  .setProperty(PackageNameProperty.NAME, ClassNamePattern.packageName);
          ClassSimpleNameParser nameParser =
              new ClassSimpleNameParser()
                  .setProperty(ClassSimpleNameProperty.NAME, ClassNamePattern.simpleName);
          return new ParserVisitor(
              descriptor,
              ImmutableList.of(packageParser, nameParser),
              () ->
                  setValue.accept(
                      KeepQualifiedClassNamePattern.builder()
                          .setPackagePattern(
                              packageParser.getValueOrDefault(KeepPackagePattern.any()))
                          .setNamePattern(
                              nameParser.getValueOrDefault(KeepUnqualfiedClassNamePattern.any()))
                          .build()));
        }
      default:
        return null;
    }
  }
}
