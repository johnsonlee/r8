// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package enclosingmethod;

public class OuterClass {
  // Named member class.
  public class AClass {

  }

  static {
    // Named local class. Will have an enclosing-method annotation with a zero method by being
    // defined in the static initializer.
    class LocalClass extends AbstractClass {

      @Override
      public int anInt() {
        return 7;
      }
    }

    // Anonymous inner class. Will have the same zero-method enclosing-method annotation.
    print(new AbstractClass() {
      @Override
      public int anInt() {
        return 42;
      }
    });
    print(new LocalClass());
  }

  public void aMethod() {
    // Local class with a non-zero-method enclosing-method annotation.
    class AnotherClass extends AbstractClass {

      @Override
      public int anInt() {
        return 48;
      }
    }

    // Anonymous inner class with a non-zero-method enclosing-method annotation.
    print(new AbstractClass() {
      @Override
      public int anInt() {
        return 42;
      }
    });
    print(new AnotherClass());
  }

  private static void print(AbstractClass anInstance) {
    System.out.println(anInstance.anInt());
    System.out.println(anInstance.getClass().getEnclosingClass());
    System.out.println(anInstance.getClass().getEnclosingMethod());
    System.out.println(anInstance.getClass().isAnonymousClass());
    System.out.println(anInstance.getClass().isLocalClass());
    System.out.println(anInstance.getClass().isMemberClass());
  }
}
