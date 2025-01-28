// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.partial.predicate;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexString;
import java.util.ArrayList;
import java.util.List;

public class R8PartialPredicateCollection {

  private final List<R8PartialPredicate> predicates = new ArrayList<>();

  public void add(R8PartialPredicate predicate) {
    predicates.add(predicate);
  }

  public boolean isEmpty() {
    return predicates.isEmpty();
  }

  public boolean test(DexProgramClass clazz) {
    DexString descriptor = clazz.getType().getDescriptor();
    for (R8PartialPredicate predicate : predicates) {
      if (predicate.test(descriptor)) {
        return true;
      }
    }
    return false;
  }

  public byte[] getDumpFileContent() {
    assert !isEmpty();
    StringBuilder builder = new StringBuilder();
    builder.append(predicates.iterator().next().serializeToString());
    for (int i = 1; i < predicates.size(); i++) {
      builder.append('\n').append(predicates.get(i).serializeToString());
    }
    return builder.toString().getBytes(UTF_8);
  }

  public String serializeToString() {
    throw new Unimplemented();
  }
}
