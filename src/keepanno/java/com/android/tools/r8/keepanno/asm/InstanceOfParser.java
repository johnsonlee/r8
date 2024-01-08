// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.keepanno.asm;

import com.android.tools.r8.keepanno.asm.InstanceOfParser.InstanceOfProperties;
import com.android.tools.r8.keepanno.ast.AnnotationConstants.Item;
import com.android.tools.r8.keepanno.ast.KeepInstanceOfPattern;
import com.android.tools.r8.keepanno.ast.KeepQualifiedClassNamePattern;
import com.android.tools.r8.keepanno.ast.ParsingContext;
import java.util.function.Consumer;
import org.objectweb.asm.Type;

public class InstanceOfParser
    extends PropertyParserBase<KeepInstanceOfPattern, InstanceOfProperties> {

  public enum InstanceOfProperties {
    NAME,
    NAME_EXCL,
    CONSTANT,
    CONSTANT_EXCL
  }

  public InstanceOfParser(ParsingContext parsingContext) {
    super(parsingContext.group(Item.instanceOfGroup));
  }

  @Override
  public boolean tryProperty(
      InstanceOfProperties property,
      String name,
      Object value,
      Consumer<KeepInstanceOfPattern> setValue) {
    KeepInstanceOfPattern result = parse(property, value);
    if (result != null) {
      setValue.accept(result);
      return true;
    }
    return super.tryProperty(property, name, value, setValue);
  }

  private KeepInstanceOfPattern parse(InstanceOfProperties property, Object value) {
    switch (property) {
      case NAME:
        return KeepInstanceOfPattern.builder()
            .classPattern(KeepQualifiedClassNamePattern.exact(((String) value)))
            .build();
      case NAME_EXCL:
        return KeepInstanceOfPattern.builder()
            .classPattern(KeepQualifiedClassNamePattern.exact(((String) value)))
            .setInclusive(false)
            .build();
      case CONSTANT:
        return KeepInstanceOfPattern.builder()
            .classPattern(KeepQualifiedClassNamePattern.exact(((Type) value).getClassName()))
            .build();
      case CONSTANT_EXCL:
        return KeepInstanceOfPattern.builder()
            .classPattern(KeepQualifiedClassNamePattern.exact(((Type) value).getClassName()))
            .setInclusive(false)
            .build();
      default:
        return null;
    }
  }
}
