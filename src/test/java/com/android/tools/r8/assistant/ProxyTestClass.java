// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.assistant;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class ProxyTestClass {

  public static void main(String[] args) {
    InvocationHandler handler =
        new InvocationHandler() {
          @Override
          public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            return null;
          }
        };

    Class<?>[] interfaces = new Class<?>[1];
    interfaces[0] = F.class;
    Proxy.newProxyInstance(ClassLoader.getSystemClassLoader(), interfaces, handler);
  }

  interface F {
    int getInt();
  }
}
