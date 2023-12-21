// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.keepanno.asm;

import com.android.tools.r8.keepanno.asm.TypeParser.TypeProperty;
import com.android.tools.r8.keepanno.ast.KeepMethodReturnTypePattern;
import com.android.tools.r8.keepanno.ast.KeepTypePattern;
import com.android.tools.r8.keepanno.ast.ParsingContext;

public class MethodReturnTypeParser
    extends ConvertingPropertyParser<KeepTypePattern, KeepMethodReturnTypePattern, TypeProperty> {

  public MethodReturnTypeParser(ParsingContext parsingContext) {
    super(new TypeParser(parsingContext), MethodReturnTypeParser::fromType);
  }

  // TODO(b/248408342): It may be problematic dealing with the "void" value at the point of return
  //  as it is not actually a type in the normal sense. Consider a set up where the "void" cases are
  //  special cased before dispatch to the underlying type parser.
  private static KeepMethodReturnTypePattern fromType(KeepTypePattern typePattern) {
    if (typePattern == null) {
      return null;
    }
    // Special-case method return types to allow void.
    String descriptor = typePattern.getDescriptor();
    if (descriptor.equals("V") || descriptor.equals("Lvoid;")) {
      return KeepMethodReturnTypePattern.voidType();
    }
    return KeepMethodReturnTypePattern.fromType(typePattern);
  }
}
