// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.keepanno.asm;

import com.android.tools.r8.keepanno.asm.OptionsParser.OptionsProperty;
import com.android.tools.r8.keepanno.ast.AnnotationConstants.Target;
import com.android.tools.r8.keepanno.ast.KeepOptions;
import com.android.tools.r8.keepanno.ast.ParsingContext;
import java.util.function.Consumer;
import org.objectweb.asm.AnnotationVisitor;

public class OptionsParser extends PropertyParserBase<KeepOptions, OptionsProperty> {

  public enum OptionsProperty {
    CONSTRAINTS,
    ALLOW,
    DISALLOW
  }

  public OptionsParser(ParsingContext parsingContext) {
    super(parsingContext.group(Target.constraintsGroup));
  }

  @Override
  AnnotationVisitor tryPropertyArray(
      OptionsProperty property, String name, Consumer<KeepOptions> setValue) {
    switch (property) {
      case CONSTRAINTS:
        return new KeepConstraintsVisitor(
            getParsingContext(),
            options -> setValue.accept(KeepOptions.disallowBuilder().addAll(options).build()));
      case ALLOW:
        return new KeepOptionsVisitor(
            getParsingContext(),
            options -> setValue.accept(KeepOptions.allowBuilder().addAll(options).build()));
      case DISALLOW:
        return new KeepOptionsVisitor(
            getParsingContext(),
            options -> setValue.accept(KeepOptions.disallowBuilder().addAll(options).build()));
      default:
        return null;
    }
  }
}
