// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.keepanno.doctests;

import com.android.tools.r8.keepanno.annotations.KeepConstraint;
import com.android.tools.r8.keepanno.annotations.KeepItemKind;
import com.android.tools.r8.keepanno.annotations.KeepTarget;
import com.android.tools.r8.keepanno.annotations.UsedByReflection;
import com.android.tools.r8.keepanno.annotations.UsesReflection;
import java.lang.reflect.Type;

/* INCLUDE DOC: GenericSignaturePrinter

Imagine we had code that is making use of the template parameters for implementations of a
generic interface. The code below assumes direct implementations of the `WrappedValue` interface
and simply prints the type parameter used.

Since we are reflecting on the class structure of implementations of `WrappedValue` we need to
keep it and any instance of it.

We must also preserve the generic signatures of these classes. We add the
`@KeepConstraint#GENERIC_SIGNATURE` constraint by using the `@KeepTarget#constraintAdditions`
property. This ensures that the default constraints are still in place in addition to the
constraint on generic signatures.

INCLUDE END */

// INCLUDE CODE: GenericSignaturePrinter
public class GenericSignaturePrinter {

  interface WrappedValue<T> {
    T getValue();
  }

  @UsesReflection(
      @KeepTarget(
          instanceOfClassConstant = WrappedValue.class,
          constraintAdditions = KeepConstraint.GENERIC_SIGNATURE))
  public static void printSignature(WrappedValue<?> obj) {
    Class<? extends WrappedValue> clazz = obj.getClass();
    for (Type iface : clazz.getGenericInterfaces()) {
      String typeName = iface.getTypeName();
      String param = typeName.substring(typeName.lastIndexOf('<') + 1, typeName.lastIndexOf('>'));
      System.out.println(clazz.getName() + " uses type " + param);
    }
  }

  // INCLUDE END

  static class MyBool implements WrappedValue<Boolean> {

    private boolean value;

    public MyBool(boolean value) {
      this.value = value;
    }

    @Override
    public Boolean getValue() {
      return value;
    }
  }

  static class MyString implements WrappedValue<String> {
    private String value;

    public MyString(String value) {
      this.value = value;
    }

    @Override
    public String getValue() {
      return value;
    }
  }

  static class TestClass {

    @UsedByReflection(kind = KeepItemKind.CLASS_AND_METHODS)
    public static void main(String[] args) throws Exception {
      GenericSignaturePrinter.printSignature(new MyString("foo"));
      GenericSignaturePrinter.printSignature(new MyBool(true));
    }
  }
}
