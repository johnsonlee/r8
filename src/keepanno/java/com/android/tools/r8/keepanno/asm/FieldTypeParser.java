// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.keepanno.asm;

import com.android.tools.r8.keepanno.asm.TypeParser.TypeProperty;
import com.android.tools.r8.keepanno.ast.KeepFieldTypePattern;
import com.android.tools.r8.keepanno.ast.KeepTypePattern;
import com.android.tools.r8.keepanno.ast.ParsingContext;

public class FieldTypeParser
    extends ConvertingPropertyParser<KeepTypePattern, KeepFieldTypePattern, TypeProperty> {

  public FieldTypeParser(ParsingContext parsingContext) {
    super(new TypeParser(parsingContext), KeepFieldTypePattern::fromType);
  }
}
