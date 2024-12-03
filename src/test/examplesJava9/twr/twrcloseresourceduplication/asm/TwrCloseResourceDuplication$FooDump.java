// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package twr.twrcloseresourceduplication.asm;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.RecordComponentVisitor;

public class TwrCloseResourceDuplication$FooDump implements Opcodes {

  public static byte[] dump() throws Exception {

    ClassWriter classWriter = new ClassWriter(0);
    FieldVisitor fieldVisitor;
    RecordComponentVisitor recordComponentVisitor;
    MethodVisitor methodVisitor;
    AnnotationVisitor annotationVisitor0;

    classWriter.visit(
        V9,
        ACC_PUBLIC | ACC_SUPER,
        "twr/twrcloseresourceduplication/TwrCloseResourceDuplication$Foo",
        null,
        "java/lang/Object",
        null);

    classWriter.visitSource("TwrCloseResourceDuplication.java", null);

    classWriter.visitInnerClass(
        "twr/twrcloseresourceduplication/TwrCloseResourceDuplication$Foo",
        "twr/twrcloseresourceduplication/TwrCloseResourceDuplication",
        "Foo",
        ACC_PUBLIC | ACC_STATIC);

    classWriter.visitInnerClass(
        "java/lang/invoke/MethodHandles$Lookup",
        "java/lang/invoke/MethodHandles",
        "Lookup",
        ACC_PUBLIC | ACC_FINAL | ACC_STATIC);

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
          "this",
          "Ltwrcloseresourceduplication/TwrCloseResourceDuplication$Foo;",
          null,
          label0,
          label1,
          0);
      methodVisitor.visitMaxs(1, 1);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor = classWriter.visitMethod(0, "foo", "(Ljava/lang/String;)V", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      Label label1 = new Label();
      Label label2 = new Label();
      methodVisitor.visitTryCatchBlock(label0, label1, label2, "java/lang/Throwable");
      Label label3 = new Label();
      methodVisitor.visitTryCatchBlock(label0, label1, label3, null);
      Label label4 = new Label();
      methodVisitor.visitTryCatchBlock(label2, label4, label3, null);
      Label label5 = new Label();
      Label label6 = new Label();
      Label label7 = new Label();
      methodVisitor.visitTryCatchBlock(label5, label6, label7, "java/lang/Exception");
      Label label8 = new Label();
      methodVisitor.visitTryCatchBlock(label5, label6, label8, null);
      Label label9 = new Label();
      methodVisitor.visitTryCatchBlock(label7, label9, label8, null);
      Label label10 = new Label();
      methodVisitor.visitTryCatchBlock(label8, label10, label8, null);
      Label label11 = new Label();
      Label label12 = new Label();
      methodVisitor.visitTryCatchBlock(label11, label12, label12, "java/lang/Throwable");
      Label label13 = new Label();
      Label label14 = new Label();
      methodVisitor.visitTryCatchBlock(label11, label13, label14, null);
      Label label15 = new Label();
      Label label16 = new Label();
      methodVisitor.visitTryCatchBlock(label15, label16, label16, "java/lang/Exception");
      Label label17 = new Label();
      Label label18 = new Label();
      methodVisitor.visitTryCatchBlock(label15, label17, label18, null);
      Label label19 = new Label();
      methodVisitor.visitTryCatchBlock(label18, label19, label18, null);
      methodVisitor.visitLabel(label5);
      methodVisitor.visitLineNumber(13, label5);
      methodVisitor.visitTypeInsn(NEW, "java/util/jar/JarFile");
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitVarInsn(ALOAD, 1);
      methodVisitor.visitMethodInsn(
          INVOKESPECIAL, "java/util/jar/JarFile", "<init>", "(Ljava/lang/String;)V", false);
      methodVisitor.visitVarInsn(ASTORE, 2);
      Label label20 = new Label();
      methodVisitor.visitLabel(label20);
      methodVisitor.visitInsn(ACONST_NULL);
      methodVisitor.visitVarInsn(ASTORE, 3);
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(14, label0);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitLdcInsn("foo opened 1");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLineNumber(15, label1);
      methodVisitor.visitVarInsn(ALOAD, 3);
      methodVisitor.visitVarInsn(ALOAD, 2);
      methodVisitor.visitMethodInsn(
          INVOKESTATIC,
          "twr/twrcloseresourceduplication/TwrCloseResourceDuplication$Foo",
          "$closeResource",
          "(Ljava/lang/Throwable;Ljava/lang/AutoCloseable;)V",
          false);
      methodVisitor.visitJumpInsn(GOTO, label6);
      methodVisitor.visitLabel(label2);
      methodVisitor.visitLineNumber(13, label2);
      methodVisitor.visitFrame(
          Opcodes.F_FULL,
          4,
          new Object[] {
            "twr/twrcloseresourceduplication/TwrCloseResourceDuplication$Foo",
            "java/lang/String",
            "java/util/jar/JarFile",
            "java/lang/Throwable"
          },
          1,
          new Object[] {"java/lang/Throwable"});
      methodVisitor.visitVarInsn(ASTORE, 4);
      methodVisitor.visitVarInsn(ALOAD, 4);
      methodVisitor.visitVarInsn(ASTORE, 3);
      methodVisitor.visitVarInsn(ALOAD, 4);
      methodVisitor.visitInsn(ATHROW);
      methodVisitor.visitLabel(label3);
      methodVisitor.visitLineNumber(15, label3);
      methodVisitor.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] {"java/lang/Throwable"});
      methodVisitor.visitVarInsn(ASTORE, 5);
      methodVisitor.visitLabel(label4);
      methodVisitor.visitVarInsn(ALOAD, 3);
      methodVisitor.visitVarInsn(ALOAD, 2);
      methodVisitor.visitMethodInsn(
          INVOKESTATIC,
          "twr/twrcloseresourceduplication/TwrCloseResourceDuplication$Foo",
          "$closeResource",
          "(Ljava/lang/Throwable;Ljava/lang/AutoCloseable;)V",
          false);
      methodVisitor.visitVarInsn(ALOAD, 5);
      methodVisitor.visitInsn(ATHROW);
      methodVisitor.visitLabel(label6);
      methodVisitor.visitLineNumber(18, label6);
      methodVisitor.visitFrame(Opcodes.F_CHOP, 2, null, 0, null);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitLdcInsn("foo post close 1");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
      Label label21 = new Label();
      methodVisitor.visitLabel(label21);
      methodVisitor.visitLineNumber(19, label21);
      methodVisitor.visitJumpInsn(GOTO, label15);
      methodVisitor.visitLabel(label7);
      methodVisitor.visitLineNumber(15, label7);
      methodVisitor.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] {"java/lang/Exception"});
      methodVisitor.visitVarInsn(ASTORE, 2);
      Label label22 = new Label();
      methodVisitor.visitLabel(label22);
      methodVisitor.visitLineNumber(16, label22);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitVarInsn(ALOAD, 2);
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/Object", "getClass", "()Ljava/lang/Class;", false);
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/Class", "getSimpleName", "()Ljava/lang/String;", false);
      methodVisitor.visitInvokeDynamicInsn(
          "makeConcatWithConstants",
          "(Ljava/lang/String;)Ljava/lang/String;",
          new Handle(
              Opcodes.H_INVOKESTATIC,
              "java/lang/invoke/StringConcatFactory",
              "makeConcatWithConstants",
              "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;",
              false),
          new Object[] {"foo caught from 1: \u0001"});
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
      methodVisitor.visitLabel(label9);
      methodVisitor.visitLineNumber(18, label9);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitLdcInsn("foo post close 1");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
      Label label23 = new Label();
      methodVisitor.visitLabel(label23);
      methodVisitor.visitLineNumber(19, label23);
      methodVisitor.visitJumpInsn(GOTO, label15);
      methodVisitor.visitLabel(label8);
      methodVisitor.visitLineNumber(18, label8);
      methodVisitor.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] {"java/lang/Throwable"});
      methodVisitor.visitVarInsn(ASTORE, 6);
      methodVisitor.visitLabel(label10);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitLdcInsn("foo post close 1");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
      methodVisitor.visitVarInsn(ALOAD, 6);
      methodVisitor.visitInsn(ATHROW);
      methodVisitor.visitLabel(label15);
      methodVisitor.visitLineNumber(20, label15);
      methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      methodVisitor.visitTypeInsn(NEW, "java/util/jar/JarFile");
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitVarInsn(ALOAD, 1);
      methodVisitor.visitMethodInsn(
          INVOKESPECIAL, "java/util/jar/JarFile", "<init>", "(Ljava/lang/String;)V", false);
      methodVisitor.visitVarInsn(ASTORE, 2);
      Label label24 = new Label();
      methodVisitor.visitLabel(label24);
      methodVisitor.visitInsn(ACONST_NULL);
      methodVisitor.visitVarInsn(ASTORE, 3);
      methodVisitor.visitLabel(label11);
      methodVisitor.visitLineNumber(21, label11);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitLdcInsn("foo opened 2");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
      Label label25 = new Label();
      methodVisitor.visitLabel(label25);
      methodVisitor.visitLineNumber(22, label25);
      methodVisitor.visitTypeInsn(NEW, "java/lang/RuntimeException");
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitMethodInsn(
          INVOKESPECIAL, "java/lang/RuntimeException", "<init>", "()V", false);
      methodVisitor.visitInsn(ATHROW);
      methodVisitor.visitLabel(label12);
      methodVisitor.visitLineNumber(20, label12);
      methodVisitor.visitFrame(
          Opcodes.F_FULL,
          4,
          new Object[] {
            "twr/twrcloseresourceduplication/TwrCloseResourceDuplication$Foo",
            "java/lang/String",
            "java/util/jar/JarFile",
            "java/lang/Throwable"
          },
          1,
          new Object[] {"java/lang/Throwable"});
      methodVisitor.visitVarInsn(ASTORE, 4);
      methodVisitor.visitVarInsn(ALOAD, 4);
      methodVisitor.visitVarInsn(ASTORE, 3);
      methodVisitor.visitVarInsn(ALOAD, 4);
      methodVisitor.visitInsn(ATHROW);
      methodVisitor.visitLabel(label14);
      methodVisitor.visitLineNumber(23, label14);
      methodVisitor.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] {"java/lang/Throwable"});
      methodVisitor.visitVarInsn(ASTORE, 7);
      methodVisitor.visitLabel(label13);
      methodVisitor.visitVarInsn(ALOAD, 3);
      methodVisitor.visitVarInsn(ALOAD, 2);
      methodVisitor.visitMethodInsn(
          INVOKESTATIC,
          "twr/twrcloseresourceduplication/TwrCloseResourceDuplication$Foo",
          "$closeResource",
          "(Ljava/lang/Throwable;Ljava/lang/AutoCloseable;)V",
          false);
      methodVisitor.visitVarInsn(ALOAD, 7);
      methodVisitor.visitInsn(ATHROW);
      methodVisitor.visitLabel(label16);
      methodVisitor.visitFrame(
          Opcodes.F_FULL,
          2,
          new Object[] {
            "twr/twrcloseresourceduplication/TwrCloseResourceDuplication$Foo", "java/lang/String"
          },
          1,
          new Object[] {"java/lang/Exception"});
      methodVisitor.visitVarInsn(ASTORE, 2);
      Label label26 = new Label();
      methodVisitor.visitLabel(label26);
      methodVisitor.visitLineNumber(24, label26);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitVarInsn(ALOAD, 2);
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/Object", "getClass", "()Ljava/lang/Class;", false);
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/Class", "getSimpleName", "()Ljava/lang/String;", false);
      methodVisitor.visitInvokeDynamicInsn(
          "makeConcatWithConstants",
          "(Ljava/lang/String;)Ljava/lang/String;",
          new Handle(
              Opcodes.H_INVOKESTATIC,
              "java/lang/invoke/StringConcatFactory",
              "makeConcatWithConstants",
              "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;",
              false),
          new Object[] {"foo caught from 2: \u0001"});
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
      methodVisitor.visitLabel(label17);
      methodVisitor.visitLineNumber(26, label17);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitLdcInsn("foo post close 2");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
      Label label27 = new Label();
      methodVisitor.visitLabel(label27);
      methodVisitor.visitLineNumber(27, label27);
      Label label28 = new Label();
      methodVisitor.visitJumpInsn(GOTO, label28);
      methodVisitor.visitLabel(label18);
      methodVisitor.visitLineNumber(26, label18);
      methodVisitor.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] {"java/lang/Throwable"});
      methodVisitor.visitVarInsn(ASTORE, 8);
      methodVisitor.visitLabel(label19);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitLdcInsn("foo post close 2");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
      methodVisitor.visitVarInsn(ALOAD, 8);
      methodVisitor.visitInsn(ATHROW);
      methodVisitor.visitLabel(label28);
      methodVisitor.visitLineNumber(28, label28);
      methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      methodVisitor.visitInsn(RETURN);
      Label label29 = new Label();
      methodVisitor.visitLabel(label29);
      methodVisitor.visitLocalVariable("f", "Ljava/util/jar/JarFile;", null, label20, label6, 2);
      methodVisitor.visitLocalVariable("e", "Ljava/lang/Exception;", null, label22, label9, 2);
      methodVisitor.visitLocalVariable("f", "Ljava/util/jar/JarFile;", null, label24, label16, 2);
      methodVisitor.visitLocalVariable("e", "Ljava/lang/Exception;", null, label26, label17, 2);
      methodVisitor.visitLocalVariable(
          "this",
          "Ltwrcloseresourceduplication/TwrCloseResourceDuplication$Foo;",
          null,
          label5,
          label29,
          0);
      methodVisitor.visitLocalVariable("name", "Ljava/lang/String;", null, label5, label29, 1);
      methodVisitor.visitMaxs(3, 9);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor =
          classWriter.visitMethod(
              ACC_PRIVATE | ACC_STATIC | ACC_SYNTHETIC,
              "$closeResource",
              "(Ljava/lang/Throwable;Ljava/lang/AutoCloseable;)V",
              null,
              null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      Label label1 = new Label();
      Label label2 = new Label();
      methodVisitor.visitTryCatchBlock(label0, label1, label2, "java/lang/Throwable");
      Label label3 = new Label();
      methodVisitor.visitLabel(label3);
      methodVisitor.visitLineNumber(15, label3);
      methodVisitor.visitVarInsn(ALOAD, 0);
      Label label4 = new Label();
      methodVisitor.visitJumpInsn(IFNULL, label4);
      methodVisitor.visitLabel(label0);
      methodVisitor.visitVarInsn(ALOAD, 1);
      methodVisitor.visitMethodInsn(
          INVOKEINTERFACE, "java/lang/AutoCloseable", "close", "()V", true);
      methodVisitor.visitLabel(label1);
      Label label5 = new Label();
      methodVisitor.visitJumpInsn(GOTO, label5);
      methodVisitor.visitLabel(label2);
      methodVisitor.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] {"java/lang/Throwable"});
      methodVisitor.visitVarInsn(ASTORE, 2);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitVarInsn(ALOAD, 2);
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/Throwable", "addSuppressed", "(Ljava/lang/Throwable;)V", false);
      methodVisitor.visitJumpInsn(GOTO, label5);
      methodVisitor.visitLabel(label4);
      methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      methodVisitor.visitVarInsn(ALOAD, 1);
      methodVisitor.visitMethodInsn(
          INVOKEINTERFACE, "java/lang/AutoCloseable", "close", "()V", true);
      methodVisitor.visitLabel(label5);
      methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      methodVisitor.visitInsn(RETURN);
      Label label6 = new Label();
      methodVisitor.visitLabel(label6);
      methodVisitor.visitLocalVariable("x0", "Ljava/lang/Throwable;", null, label3, label6, 0);
      methodVisitor.visitLocalVariable("x1", "Ljava/lang/AutoCloseable;", null, label3, label6, 1);
      methodVisitor.visitMaxs(2, 3);
      methodVisitor.visitEnd();
    }
    classWriter.visitEnd();

    return classWriter.toByteArray();
  }
}
