// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.keepanno.asm;

import com.android.tools.r8.keepanno.asm.PackageNameParser.PackageNameProperty;
import com.android.tools.r8.keepanno.ast.KeepPackagePattern;
import com.android.tools.r8.keepanno.ast.ParsingContext;
import java.util.function.Consumer;

public class PackageNameParser
    extends PropertyParserBase<KeepPackagePattern, PackageNameProperty, PackageNameParser> {

  public PackageNameParser(ParsingContext parsingContext) {
    super(parsingContext);
  }

  public enum PackageNameProperty {
    NAME
  }

  @Override
  public PackageNameParser self() {
    return this;
  }

  @Override
  public boolean tryProperty(
      PackageNameProperty property,
      String name,
      Object value,
      Consumer<KeepPackagePattern> setValue) {
    switch (property) {
      case NAME:
        setValue.accept(KeepPackagePattern.exact((String) value));
        return true;
      default:
        return false;
    }
  }
}
