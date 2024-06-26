// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import static com.android.tools.r8.utils.InternalOptions.ASM_VERSION;

import java.io.IOException;
import java.io.InputStream;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;

public class CfUtils {
  private static class ClassNameExtractor extends ClassVisitor {
    private String className;

    private ClassNameExtractor() {
      super(ASM_VERSION);
    }

    @Override
    public void visit(
        int version,
        int access,
        String name,
        String signature,
        String superName,
        String[] interfaces) {
      className = name;
    }

    String getClassInternalType() {
      return className;
    }
  }

  public static String extractClassName(byte[] ccc) {
    return DescriptorUtils.getJavaTypeFromBinaryName(
        extractClassInternalType(new ClassReader(ccc)));
  }

  public static String extractClassDescriptor(byte[] ccc) {
    return "L" + extractClassInternalType(new ClassReader(ccc)) + ";";
  }

  public static String extractClassDescriptor(InputStream input) throws IOException {
    return DescriptorUtils.getDescriptorFromClassBinaryName(
        extractClassInternalType(new ClassReader(input)));
  }

  private static String extractClassInternalType(ClassReader reader) {
    ClassNameExtractor extractor = new ClassNameExtractor();
    reader.accept(
        extractor, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    return extractor.getClassInternalType();
  }
}
