// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.regress.b316744331;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;

public class Regress316744331TestClass {

  String filename;
  FileReader m_instream;
  PrintWriter m_outstream;

  Regress316744331TestClass(String filename) {
    this.filename = filename;
  }

  public void foo() throws IOException {
    m_instream = new FileReader(filename);
    if (null == m_instream) {
      System.out.println("Reader is null!");
      return;
    }
    m_outstream =
        new PrintWriter(new java.io.BufferedWriter(new java.io.FileWriter(filename + ".java")));
    if (null == m_outstream) {
      System.out.println("Writer is null!");
      return;
    }
    System.out.println("No null fields");
  }

  public static void main(String[] args) throws IOException {
    // The debugger testing infra does not allow passing runtime arguments.
    // Classpath should have an entry in normal and debugger runs so use it as the "file".
    String cp = System.getProperty("java.class.path");
    int jarIndex = cp.indexOf(".jar");
    if (jarIndex < 0) {
      jarIndex = cp.indexOf(".zip");
    }
    int start = cp.lastIndexOf(File.pathSeparatorChar, jarIndex);
    String filename = cp.substring(Math.max(start, 0), jarIndex + 4);
    new Regress316744331TestClass(filename).foo();
  }
}
