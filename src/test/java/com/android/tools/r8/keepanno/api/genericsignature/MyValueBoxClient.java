// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.keepanno.api.genericsignature;

import com.android.tools.r8.keepanno.annotations.KeepItemKind;
import com.android.tools.r8.keepanno.annotations.UsedByReflection;
import java.util.Arrays;
import java.util.List;

public class MyValueBoxClient {

  @UsedByReflection(kind = KeepItemKind.CLASS_AND_METHODS)
  public static void main(String[] args) {
    System.out.println(
        new MyValueBox<>(Arrays.asList("Hello", "world"))
            .run((List<String> xs) -> String.join(", ", xs)));

    System.out.println(
        new MyOtherValueBox<>(Arrays.asList("hello", "again!"))
            .run((List<String> xs) -> String.join(", ", xs)));
  }
}
