// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.keepanno.asm;

import com.android.tools.r8.keepanno.asm.ConstraintsParser.ConstraintsProperty;
import com.android.tools.r8.keepanno.ast.AnnotationConstants.Target;
import com.android.tools.r8.keepanno.ast.KeepConstraints;
import com.android.tools.r8.keepanno.ast.KeepOptions;
import com.android.tools.r8.keepanno.ast.ParsingContext;
import java.util.function.Consumer;
import org.objectweb.asm.AnnotationVisitor;

public class ConstraintsParser extends PropertyParserBase<KeepConstraints, ConstraintsProperty> {

  public enum ConstraintsProperty {
    CONSTRAINTS,
    ADDITIONS,
    ALLOW,
    DISALLOW
  }

  public ConstraintsParser(ParsingContext parsingContext) {
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
      case ALLOW:
        return new KeepOptionsVisitor(
            parsingContext,
            options ->
                setValue.accept(
                    KeepConstraints.fromLegacyOptions(
                        KeepOptions.allowBuilder().addAll(options).build())));
      case DISALLOW:
        return new KeepOptionsVisitor(
            parsingContext,
            options ->
                setValue.accept(
                    KeepConstraints.fromLegacyOptions(
                        KeepOptions.disallowBuilder().addAll(options).build())));
      default:
        return null;
    }
  }
}
