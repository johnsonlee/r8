// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.assistant;

import java.util.ServiceLoader;

public class ServiceLoaderTestClass {

  volatile int i;
  volatile long l;
  volatile Object o;

  public static void main(String[] args) {
    ServiceLoader<NameService> loader = ServiceLoader.load(NameService.class);
    for (NameService nameService : loader) {
      System.out.println(nameService.getName());
    }

    ServiceLoader<NameService> loader2 = ServiceLoader.loadInstalled(NameService.class);
    for (NameService nameService : loader2) {
      System.out.println(nameService.getName());
    }

    ServiceLoader<NameService> loader3 =
        ServiceLoader.load(NameService.class, ServiceLoaderTestClass.class.getClassLoader());
    for (NameService nameService : loader3) {
      System.out.println(nameService.getName());
    }
  }

  public interface NameService {

    String getName();
  }

  public class DummyService implements NameService {

    @Override
    public String getName() {
      return "dummy";
    }
  }
}
