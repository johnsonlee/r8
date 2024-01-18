// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import java.util.Iterator;
import java.util.function.Function;

public class FastMapUtils {

  public static <V> Int2ReferenceMap<V> destructiveMapValues(
      Int2ReferenceMap<V> map, Function<V, V> valueMapper) {
    Iterator<Int2ReferenceMap.Entry<V>> iterator = map.int2ReferenceEntrySet().iterator();
    while (iterator.hasNext()) {
      Int2ReferenceMap.Entry<V> entry = iterator.next();
      V newValue = valueMapper.apply(entry.getValue());
      if (newValue != null) {
        entry.setValue(newValue);
      } else {
        iterator.remove();
      }
    }
    return map;
  }

  public static <V> Int2ReferenceMap<V> mapInt2ReferenceOpenHashMapOrElse(
      Int2ReferenceMap<V> map,
      IntObjToObjFunction<V, V> valueMapper,
      Int2ReferenceMap<V> defaultValue) {
    Int2ReferenceMap<V> newMap = null;
    Iterator<Int2ReferenceMap.Entry<V>> iterator = map.int2ReferenceEntrySet().iterator();
    while (iterator.hasNext()) {
      Int2ReferenceMap.Entry<V> entry = iterator.next();
      int key = entry.getIntKey();
      V value = entry.getValue();
      V newValue = valueMapper.apply(key, value);
      if (newMap == null) {
        if (newValue == value) {
          continue;
        }
        // This is the first entry where the value has been changed. Create the new map and copy
        // over previous entries that did not change.
        Int2ReferenceMap<V> newFinalMap = new Int2ReferenceOpenHashMap<>(map.size());
        CollectionUtils.forEachUntilExclusive(
            map.int2ReferenceEntrySet(),
            previousEntry -> newFinalMap.put(previousEntry.getIntKey(), previousEntry.getValue()),
            entry);
        newMap = newFinalMap;
      }
      if (newValue != null) {
        newMap.put(key, newValue);
      } else {
        iterator.remove();
      }
    }
    return newMap != null ? newMap : defaultValue;
  }
}
