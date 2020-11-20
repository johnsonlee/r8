// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.b171982443;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.desugar.b171982443.DefaultMethodInvokeReprocessingTest.I;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class Caller implements Opcodes {

  @NeverInline
  public static void callI(I i) {
    // We will inline the kotlin function that has kotlin style lambdas. That will trigger
    // the LambdaMerger and force it to reprocess this method again. That is a problem if we
    // have desugared I.print() to a default method, since the mapping will be applied for the
    // i.print() call below
    i.print();
    // Since the test cannot reference kotlin code during compilation, we insert the call
    // manually.
    // SimpleKt.tBirdTaker();
  }

  public static byte[] dump() {

    ClassWriter classWriter = new ClassWriter(0);
    MethodVisitor methodVisitor;
    AnnotationVisitor annotationVisitor0;

    classWriter.visit(
        V1_8,
        ACC_PUBLIC | ACC_SUPER,
        "com/android/tools/r8/desugar/b171982443/Caller",
        null,
        "java/lang/Object",
        null);
    classWriter.visitSource("Caller.java", null);
    classWriter.visitInnerClass(
        "com/android/tools/r8/desugar/b171982443/DefaultMethodInvokeReprocessingTest$I",
        "com/android/tools/r8/desugar/b171982443/DefaultMethodInvokeReprocessingTest",
        "I",
        ACC_PUBLIC | ACC_STATIC | ACC_ABSTRACT | ACC_INTERFACE);

    {
      methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(10, label0);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
      methodVisitor.visitInsn(RETURN);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLocalVariable(
          "this", "Lcom/android/tools/r8/desugar/b171982443/Caller;", null, label0, label1, 0);
      methodVisitor.visitMaxs(1, 1);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor =
          classWriter.visitMethod(
              ACC_PUBLIC | ACC_STATIC,
              "callI",
              "(Lcom/android/tools/r8/desugar/b171982443/DefaultMethodInvokeReprocessingTest$I;)V",
              null,
              null);
      {
        annotationVisitor0 =
            methodVisitor.visitAnnotation("Lcom/android/tools/r8/NeverInline;", false);
        annotationVisitor0.visitEnd();
      }
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(19, label0);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitMethodInsn(
          INVOKEINTERFACE,
          "com/android/tools/r8/desugar/b171982443/DefaultMethodInvokeReprocessingTest$I",
          "print",
          "()V",
          true);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLineNumber(22, label1);
      methodVisitor.visitMethodInsn(
          INVOKESTATIC,
          "com/android/tools/r8/desugar/b171982443/SimpleKt",
          "tBirdTaker",
          "()V",
          false);
      Label label2 = new Label();
      methodVisitor.visitLabel(label2);
      methodVisitor.visitLineNumber(23, label2);
      methodVisitor.visitInsn(RETURN);
      Label label3 = new Label();
      methodVisitor.visitLabel(label3);
      methodVisitor.visitLocalVariable(
          "i",
          "Lcom/android/tools/r8/desugar/b171982443/DefaultMethodInvokeReprocessingTest$I;",
          null,
          label0,
          label3,
          0);
      methodVisitor.visitMaxs(1, 1);
      methodVisitor.visitEnd();
    }
    classWriter.visitEnd();

    return classWriter.toByteArray();
  }
}
