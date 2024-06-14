// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import kotlin.metadata.Attributes;
import kotlin.metadata.KmProperty;
import kotlin.metadata.KmPropertyAccessorAttributes;
import kotlin.metadata.jvm.JvmAttributes;

public class KotlinFlagUtils {

  static void copyAllFlags(KmProperty src, KmProperty dest) {
    Attributes.setHasAnnotations(dest, Attributes.getHasAnnotations(src));
    Attributes.setVisibility(dest, Attributes.getVisibility(src));
    Attributes.setModality(dest, Attributes.getModality(src));
    Attributes.setKind(dest, Attributes.getKind(src));
    Attributes.setVar(dest, Attributes.isVar(src));
    Attributes.setConst(dest, Attributes.isConst(src));
    Attributes.setLateinit(dest, Attributes.isLateinit(src));
    Attributes.setHasConstant(dest, Attributes.getHasConstant(src));
    Attributes.setExternal(dest, Attributes.isExternal(src));
    Attributes.setDelegated(dest, Attributes.isDelegated(src));
    Attributes.setExpect(dest, Attributes.isExpect(src));

    JvmAttributes.setMovedFromInterfaceCompanion(
        dest, JvmAttributes.isMovedFromInterfaceCompanion(src));
  }

  static void copyAllFlags(KmPropertyAccessorAttributes src, KmPropertyAccessorAttributes dest) {
    Attributes.setHasAnnotations(dest, Attributes.getHasAnnotations(src));
    Attributes.setVisibility(dest, Attributes.getVisibility(src));
    Attributes.setModality(dest, Attributes.getModality(src));
    Attributes.setNotDefault(dest, Attributes.isNotDefault(src));
    Attributes.setExternal(dest, Attributes.isExternal(src));
    Attributes.setInline(dest, Attributes.isInline(src));
  }
}
