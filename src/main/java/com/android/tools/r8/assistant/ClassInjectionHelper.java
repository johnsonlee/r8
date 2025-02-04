// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.assistant;

import com.android.tools.r8.R8Assistant;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.ExceptionDiagnostic;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.StringDiagnostic;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.io.InputStream;

public class ClassInjectionHelper {

  private final Reporter reporter;

  public ClassInjectionHelper(Reporter reporter) {
    this.reporter = reporter;
  }

  public byte[] getClassBytes(Class<?> clazz) {
    String classFilePath = DescriptorUtils.getPathFromJavaType(clazz);
    try (InputStream inputStream =
        R8Assistant.class.getClassLoader().getResourceAsStream(classFilePath)) {
      if (inputStream == null) {
        reporter.error(new StringDiagnostic("Could not open class file: " + classFilePath));
        return null;
      }
      return ByteStreams.toByteArray(inputStream);
    } catch (IOException e) {
      reporter.error(new ExceptionDiagnostic(e));
      return null;
    }
  }
}
