// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.keepanno.asm;

import com.android.tools.r8.keepanno.asm.TypeParser.TypeProperty;
import com.android.tools.r8.keepanno.ast.KeepMethodParametersPattern;
import com.android.tools.r8.keepanno.ast.KeepTypePattern;
import com.android.tools.r8.keepanno.ast.ParsingContext;
import java.util.List;

public class MethodParametersParser
    extends ConvertingPropertyParser<
        List<KeepTypePattern>, KeepMethodParametersPattern, TypeProperty> {

  public MethodParametersParser(ParsingContext parsingContext) {
    super(
        new ArrayPropertyParser<>(parsingContext, TypeParser::new),
        MethodParametersParser::convert);
  }

  private static KeepMethodParametersPattern convert(List<KeepTypePattern> params) {
    KeepMethodParametersPattern.Builder builder = KeepMethodParametersPattern.builder();
    for (KeepTypePattern param : params) {
      builder.addParameterTypePattern(param);
    }
    return builder.build();
  }
}
