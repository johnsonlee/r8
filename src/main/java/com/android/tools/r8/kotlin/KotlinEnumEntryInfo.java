// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.naming.NamingLens;
import kotlin.metadata.KmClass;

// Structure around a kotlin enum value that can be assigned to a field.
public class KotlinEnumEntryInfo implements KotlinFieldLevelInfo {

  private final String enumEntry;

  public KotlinEnumEntryInfo(String enumEntry) {
    this.enumEntry = enumEntry;
  }

  @Override
  public boolean isEnumEntry() {
    return true;
  }

  @Override
  public KotlinEnumEntryInfo asEnumEntry() {
    return this;
  }

  boolean rewrite(KmClass clazz, DexField field, NamingLens lens) {
    DexString dexString = lens.lookupName(field);
    String finalName = dexString.toString();
    clazz.getEnumEntries().add(finalName);
    return !finalName.equals(enumEntry);
  }

  @Override
  public void trace(KotlinMetadataUseRegistry registry) {
    // Do nothing.
  }

  public String getEnumEntry() {
    return enumEntry;
  }
}
