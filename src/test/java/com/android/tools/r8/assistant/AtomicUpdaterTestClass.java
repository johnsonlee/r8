// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.assistant;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

public class AtomicUpdaterTestClass {

  volatile int i;
  volatile long l;
  volatile Object o;

  public static void main(String[] args) {
    AtomicUpdaterTestClass inst = new AtomicUpdaterTestClass();

    AtomicIntegerFieldUpdater<AtomicUpdaterTestClass> intUpdater =
        AtomicIntegerFieldUpdater.newUpdater(AtomicUpdaterTestClass.class, "i");
    intUpdater.set(inst, 42);
    System.out.println(inst.i);

    AtomicLongFieldUpdater<AtomicUpdaterTestClass> longUpdater =
        AtomicLongFieldUpdater.newUpdater(AtomicUpdaterTestClass.class, "l");
    longUpdater.set(inst, 42L);
    System.out.println(inst.l);

    AtomicReferenceFieldUpdater<AtomicUpdaterTestClass, Object> referenceUpdater =
        AtomicReferenceFieldUpdater.newUpdater(AtomicUpdaterTestClass.class, Object.class, "o");
    referenceUpdater.set(inst, "42");
    System.out.println(inst.o);
  }
}
