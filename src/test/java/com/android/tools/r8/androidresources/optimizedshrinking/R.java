// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.androidresources.optimizedshrinking;

// For testing the legacy shrinker support for R class indification we use a R class that is not
// an innner class (the legacy shrinker explicitly match on R$type names, so it will not
// match SomeTest$R$type).
public class R {
  public static class string {
    public static int bar;
    public static int foo;
    public static int unused_string;
  }

  public static class styleable {
    public static int[] our_styleable;
    public static int[] unused_styleable;
  }

  public static class drawable {

    public static int foobar;
    public static int unused_drawable;
  }
}
