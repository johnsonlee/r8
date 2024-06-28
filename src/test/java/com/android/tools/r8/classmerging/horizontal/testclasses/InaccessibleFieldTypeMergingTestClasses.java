// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.classmerging.horizontal.testclasses;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoAccessModification;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.NoMethodStaticizing;
import com.android.tools.r8.NoVerticalClassMerging;

public class InaccessibleFieldTypeMergingTestClasses {

  @NoVerticalClassMerging
  public interface Greeter {

    void greet();
  }

  @NoAccessModification
  @NoHorizontalClassMerging
  static class FooGreeter implements Greeter {

    @NeverInline
    @NoMethodStaticizing
    @Override
    public void greet() {
      System.out.println("Foo!");
    }
  }

  public static class FooGreeterContainer {

    public FooGreeter f = new FooGreeter();
  }

  @NoAccessModification
  @NoHorizontalClassMerging
  static class BarGreeter implements Greeter {

    @NeverInline
    @NoMethodStaticizing
    @Override
    public void greet() {
      System.out.println("Bar!");
    }
  }

  public static class BarGreeterContainer {

    public BarGreeter f = new BarGreeter();
  }
}
