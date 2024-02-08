// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.keepanno.asm;

import com.android.tools.r8.keepanno.asm.BooleanParser.BooleanProperty;
import com.android.tools.r8.keepanno.ast.ParsingContext;
import java.util.function.Consumer;

public class BooleanParser extends PropertyParserBase<Boolean, BooleanProperty> {

  public BooleanParser(ParsingContext parsingContext) {
    super(parsingContext);
  }

  public enum BooleanProperty {
    BOOL
  }

  @Override
  public boolean tryProperty(
      BooleanProperty property, String name, Object value, Consumer<Boolean> setValue) {
    if (BooleanProperty.BOOL.equals(property) && value instanceof Boolean) {
      setValue.accept((Boolean) value);
      return true;
    }
    return false;
  }
}
