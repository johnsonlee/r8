// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.jdk17.records;

// The code needs to be completely outside the test for the test to work.
// If these classes are inner classes, age is not correctly simplified to a 42 constant field.
public class RecordKeepRules {

  record Person(int unused, String name, int age) {
    Person(String name, int age) {
      this(-1, name, age);
    }
  }

  public static void main(String[] args) {
    Person jane = new Person("Jane Doe", 42);
    Person bob = new Person("Bob", 42);
    System.out.println(jane);
    System.out.println(bob);
  }
}
