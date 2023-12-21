// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.keepanno.asm;

import com.android.tools.r8.keepanno.asm.ClassSimpleNameParser.ClassSimpleNameProperty;
import com.android.tools.r8.keepanno.ast.KeepUnqualfiedClassNamePattern;
import com.android.tools.r8.keepanno.ast.ParsingContext;
import java.util.function.Consumer;

public class ClassSimpleNameParser
    extends PropertyParserBase<KeepUnqualfiedClassNamePattern, ClassSimpleNameProperty> {

  public ClassSimpleNameParser(ParsingContext parsingContext) {
    super(parsingContext);
  }

  public enum ClassSimpleNameProperty {
    NAME
  }

  @Override
  public boolean tryProperty(
      ClassSimpleNameProperty property,
      String name,
      Object value,
      Consumer<KeepUnqualfiedClassNamePattern> setValue) {
    switch (property) {
      case NAME:
        setValue.accept(KeepUnqualfiedClassNamePattern.exact((String) value));
        return true;
      default:
        return false;
    }
  }
}
