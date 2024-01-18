// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.keepanno.asm;

import com.android.tools.r8.keepanno.asm.ConstraintPropertiesParser.ConstraintsProperty;
import com.android.tools.r8.keepanno.ast.AnnotationConstants.Target;
import com.android.tools.r8.keepanno.ast.KeepConstraints;
import com.android.tools.r8.keepanno.ast.ParsingContext;
import java.util.function.Consumer;
import org.objectweb.asm.AnnotationVisitor;

public class ConstraintPropertiesParser
    extends PropertyParserBase<KeepConstraints, ConstraintsProperty> {

  public enum ConstraintsProperty {
    CONSTRAINTS,
    ADDITIONS
  }

  public ConstraintPropertiesParser(ParsingContext parsingContext) {
    super(parsingContext.group(Target.constraintsGroup));
  }

  @Override
  AnnotationVisitor tryPropertyArray(
      ConstraintsProperty property, String name, Consumer<KeepConstraints> setValue) {
    ParsingContext parsingContext = getParsingContext().property(name);
    switch (property) {
      case ADDITIONS:
        return new KeepConstraintsVisitor(
            parsingContext,
            constraints -> setValue.accept(KeepConstraints.defaultAdditions(constraints)));
      case CONSTRAINTS:
        return new KeepConstraintsVisitor(parsingContext, setValue::accept);
      default:
        return null;
    }
  }
}
