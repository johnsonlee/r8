// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package twr.twrcloseresource.asm;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.RecordComponentVisitor;

public class IfaceDump implements Opcodes {

  public static byte[] dump() throws Exception {

    ClassWriter classWriter = new ClassWriter(0);
    FieldVisitor fieldVisitor;
    RecordComponentVisitor recordComponentVisitor;
    MethodVisitor methodVisitor;
    AnnotationVisitor annotationVisitor0;

    classWriter.visit(
        V9,
        ACC_ABSTRACT | ACC_INTERFACE,
        "twr/twrcloseresource/Iface",
        null,
        "java/lang/Object",
        null);

    classWriter.visitSource("TwrCloseResourceDuplication$BarDump.java", null);

    {
      methodVisitor =
          classWriter.visitMethod(ACC_PUBLIC, "iFoo", "(Ljava/lang/String;)V", null, null);
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
      Label label9 = new Label();
      Label label10 = new Label();
      methodVisitor.visitTryCatchBlock(label8, label9, label10, "java/lang/Throwable");
      Label label11 = new Label();
      methodVisitor.visitTryCatchBlock(label8, label9, label11, null);
      Label label12 = new Label();
      methodVisitor.visitTryCatchBlock(label10, label12, label11, null);
      Label label13 = new Label();
      Label label14 = new Label();
      Label label15 = new Label();
      methodVisitor.visitTryCatchBlock(label13, label14, label15, "java/lang/Throwable");
      Label label16 = new Label();
      methodVisitor.visitTryCatchBlock(label13, label14, label16, null);
      Label label17 = new Label();
      methodVisitor.visitTryCatchBlock(label15, label17, label16, null);
      Label label18 = new Label();
      Label label19 = new Label();
      methodVisitor.visitTryCatchBlock(label5, label18, label19, "java/lang/Exception");
      Label label20 = new Label();
      Label label21 = new Label();
      Label label22 = new Label();
      methodVisitor.visitTryCatchBlock(label20, label21, label22, "java/lang/Throwable");
      Label label23 = new Label();
      methodVisitor.visitTryCatchBlock(label20, label21, label23, null);
      Label label24 = new Label();
      methodVisitor.visitTryCatchBlock(label22, label24, label23, null);
      Label label25 = new Label();
      Label label26 = new Label();
      Label label27 = new Label();
      methodVisitor.visitTryCatchBlock(label25, label26, label27, null);
      Label label28 = new Label();
      methodVisitor.visitTryCatchBlock(label27, label28, label27, null);
      Label label29 = new Label();
      Label label30 = new Label();
      methodVisitor.visitTryCatchBlock(label29, label30, label30, "java/lang/Throwable");
      Label label31 = new Label();
      Label label32 = new Label();
      methodVisitor.visitTryCatchBlock(label29, label31, label32, null);
      Label label33 = new Label();
      Label label34 = new Label();
      methodVisitor.visitTryCatchBlock(label33, label34, label34, "java/lang/Exception");
      methodVisitor.visitLabel(label5);
      methodVisitor.visitLineNumber(82, label5);
      methodVisitor.visitTypeInsn(NEW, "java/util/jar/JarFile");
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitVarInsn(ALOAD, 1);
      methodVisitor.visitMethodInsn(
          INVOKESPECIAL, "java/util/jar/JarFile", "<init>", "(Ljava/lang/String;)V", false);
      methodVisitor.visitVarInsn(ASTORE, 2);
      Label label35 = new Label();
      methodVisitor.visitLabel(label35);
      methodVisitor.visitInsn(ACONST_NULL);
      methodVisitor.visitVarInsn(ASTORE, 3);
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(83, label0);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitLdcInsn("iA");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLineNumber(84, label1);
      methodVisitor.visitVarInsn(ALOAD, 3);
      methodVisitor.visitVarInsn(ALOAD, 2);
      methodVisitor.visitMethodInsn(
          INVOKESTATIC,
          "twr/twrcloseresource/Iface",
          "$closeResource",
          "(Ljava/lang/Throwable;Ljava/lang/AutoCloseable;)V",
          true);
      methodVisitor.visitJumpInsn(GOTO, label6);
      methodVisitor.visitLabel(label2);
      methodVisitor.visitLineNumber(82, label2);
      methodVisitor.visitFrame(
          Opcodes.F_FULL,
          4,
          new Object[] {
            "twr/twrcloseresource/Iface",
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
      methodVisitor.visitLineNumber(84, label3);
      methodVisitor.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] {"java/lang/Throwable"});
      methodVisitor.visitVarInsn(ASTORE, 5);
      methodVisitor.visitLabel(label4);
      methodVisitor.visitVarInsn(ALOAD, 3);
      methodVisitor.visitVarInsn(ALOAD, 2);
      methodVisitor.visitMethodInsn(
          INVOKESTATIC,
          "twr/twrcloseresource/Iface",
          "$closeResource",
          "(Ljava/lang/Throwable;Ljava/lang/AutoCloseable;)V",
          true);
      methodVisitor.visitVarInsn(ALOAD, 5);
      methodVisitor.visitInsn(ATHROW);
      methodVisitor.visitLabel(label6);
      methodVisitor.visitLineNumber(91, label6);
      methodVisitor.visitFrame(Opcodes.F_CHOP, 2, null, 0, null);
      Label label36 = new Label();
      methodVisitor.visitJumpInsn(GOTO, label36);
      methodVisitor.visitLabel(label7);
      methodVisitor.visitLineNumber(84, label7);
      methodVisitor.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] {"java/lang/Exception"});
      methodVisitor.visitVarInsn(ASTORE, 2);
      Label label37 = new Label();
      methodVisitor.visitLabel(label37);
      methodVisitor.visitLineNumber(85, label37);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitLdcInsn("iB");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
      Label label38 = new Label();
      methodVisitor.visitLabel(label38);
      methodVisitor.visitLineNumber(86, label38);
      methodVisitor.visitTypeInsn(NEW, "java/util/jar/JarFile");
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitVarInsn(ALOAD, 1);
      methodVisitor.visitMethodInsn(
          INVOKESPECIAL, "java/util/jar/JarFile", "<init>", "(Ljava/lang/String;)V", false);
      methodVisitor.visitVarInsn(ASTORE, 3);
      Label label39 = new Label();
      methodVisitor.visitLabel(label39);
      methodVisitor.visitInsn(ACONST_NULL);
      methodVisitor.visitVarInsn(ASTORE, 4);
      methodVisitor.visitLabel(label8);
      methodVisitor.visitLineNumber(87, label8);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitLdcInsn("iC");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
      methodVisitor.visitLabel(label9);
      methodVisitor.visitLineNumber(88, label9);
      methodVisitor.visitVarInsn(ALOAD, 4);
      methodVisitor.visitVarInsn(ALOAD, 3);
      methodVisitor.visitMethodInsn(
          INVOKESTATIC,
          "twr/twrcloseresource/Iface",
          "$closeResource",
          "(Ljava/lang/Throwable;Ljava/lang/AutoCloseable;)V",
          true);
      Label label40 = new Label();
      methodVisitor.visitJumpInsn(GOTO, label40);
      methodVisitor.visitLabel(label10);
      methodVisitor.visitLineNumber(86, label10);
      methodVisitor.visitFrame(
          Opcodes.F_FULL,
          5,
          new Object[] {
            "twr/twrcloseresource/Iface",
            "java/lang/String",
            "java/lang/Exception",
            "java/util/jar/JarFile",
            "java/lang/Throwable"
          },
          1,
          new Object[] {"java/lang/Throwable"});
      methodVisitor.visitVarInsn(ASTORE, 5);
      methodVisitor.visitVarInsn(ALOAD, 5);
      methodVisitor.visitVarInsn(ASTORE, 4);
      methodVisitor.visitVarInsn(ALOAD, 5);
      methodVisitor.visitInsn(ATHROW);
      methodVisitor.visitLabel(label11);
      methodVisitor.visitLineNumber(88, label11);
      methodVisitor.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] {"java/lang/Throwable"});
      methodVisitor.visitVarInsn(ASTORE, 6);
      methodVisitor.visitLabel(label12);
      methodVisitor.visitVarInsn(ALOAD, 4);
      methodVisitor.visitVarInsn(ALOAD, 3);
      methodVisitor.visitMethodInsn(
          INVOKESTATIC,
          "twr/twrcloseresource/Iface",
          "$closeResource",
          "(Ljava/lang/Throwable;Ljava/lang/AutoCloseable;)V",
          true);
      methodVisitor.visitVarInsn(ALOAD, 6);
      methodVisitor.visitInsn(ATHROW);
      methodVisitor.visitLabel(label40);
      methodVisitor.visitLineNumber(89, label40);
      methodVisitor.visitFrame(Opcodes.F_CHOP, 2, null, 0, null);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitLdcInsn("iD");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
      Label label41 = new Label();
      methodVisitor.visitLabel(label41);
      methodVisitor.visitLineNumber(90, label41);
      methodVisitor.visitTypeInsn(NEW, "java/lang/RuntimeException");
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitMethodInsn(
          INVOKESPECIAL, "java/lang/RuntimeException", "<init>", "()V", false);
      methodVisitor.visitInsn(ATHROW);
      methodVisitor.visitLabel(label36);
      methodVisitor.visitLineNumber(92, label36);
      methodVisitor.visitFrame(Opcodes.F_CHOP, 1, null, 0, null);
      methodVisitor.visitTypeInsn(NEW, "java/util/jar/JarFile");
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitVarInsn(ALOAD, 1);
      methodVisitor.visitMethodInsn(
          INVOKESPECIAL, "java/util/jar/JarFile", "<init>", "(Ljava/lang/String;)V", false);
      methodVisitor.visitVarInsn(ASTORE, 2);
      Label label42 = new Label();
      methodVisitor.visitLabel(label42);
      methodVisitor.visitInsn(ACONST_NULL);
      methodVisitor.visitVarInsn(ASTORE, 3);
      methodVisitor.visitLabel(label13);
      methodVisitor.visitLineNumber(93, label13);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitLdcInsn("iE");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
      methodVisitor.visitLabel(label14);
      methodVisitor.visitLineNumber(94, label14);
      methodVisitor.visitVarInsn(ALOAD, 3);
      methodVisitor.visitVarInsn(ALOAD, 2);
      methodVisitor.visitMethodInsn(
          INVOKESTATIC,
          "twr/twrcloseresource/Iface",
          "$closeResource",
          "(Ljava/lang/Throwable;Ljava/lang/AutoCloseable;)V",
          true);
      methodVisitor.visitJumpInsn(GOTO, label18);
      methodVisitor.visitLabel(label15);
      methodVisitor.visitLineNumber(92, label15);
      methodVisitor.visitFrame(
          Opcodes.F_FULL,
          4,
          new Object[] {
            "twr/twrcloseresource/Iface",
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
      methodVisitor.visitLabel(label16);
      methodVisitor.visitLineNumber(94, label16);
      methodVisitor.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] {"java/lang/Throwable"});
      methodVisitor.visitVarInsn(ASTORE, 7);
      methodVisitor.visitLabel(label17);
      methodVisitor.visitVarInsn(ALOAD, 3);
      methodVisitor.visitVarInsn(ALOAD, 2);
      methodVisitor.visitMethodInsn(
          INVOKESTATIC,
          "twr/twrcloseresource/Iface",
          "$closeResource",
          "(Ljava/lang/Throwable;Ljava/lang/AutoCloseable;)V",
          true);
      methodVisitor.visitVarInsn(ALOAD, 7);
      methodVisitor.visitInsn(ATHROW);
      methodVisitor.visitLabel(label18);
      methodVisitor.visitLineNumber(97, label18);
      methodVisitor.visitFrame(Opcodes.F_CHOP, 2, null, 0, null);
      methodVisitor.visitJumpInsn(GOTO, label33);
      methodVisitor.visitLabel(label19);
      methodVisitor.visitLineNumber(95, label19);
      methodVisitor.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] {"java/lang/Exception"});
      methodVisitor.visitVarInsn(ASTORE, 2);
      Label label43 = new Label();
      methodVisitor.visitLabel(label43);
      methodVisitor.visitLineNumber(96, label43);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitLdcInsn("iF");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
      methodVisitor.visitLabel(label33);
      methodVisitor.visitLineNumber(98, label33);
      methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      methodVisitor.visitTypeInsn(NEW, "java/util/jar/JarFile");
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitVarInsn(ALOAD, 1);
      methodVisitor.visitMethodInsn(
          INVOKESPECIAL, "java/util/jar/JarFile", "<init>", "(Ljava/lang/String;)V", false);
      methodVisitor.visitVarInsn(ASTORE, 2);
      Label label44 = new Label();
      methodVisitor.visitLabel(label44);
      methodVisitor.visitInsn(ACONST_NULL);
      methodVisitor.visitVarInsn(ASTORE, 3);
      methodVisitor.visitLabel(label29);
      methodVisitor.visitLineNumber(99, label29);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitLdcInsn("iG");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
      methodVisitor.visitLabel(label25);
      methodVisitor.visitLineNumber(100, label25);
      methodVisitor.visitTypeInsn(NEW, "java/util/jar/JarFile");
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitVarInsn(ALOAD, 1);
      methodVisitor.visitMethodInsn(
          INVOKESPECIAL, "java/util/jar/JarFile", "<init>", "(Ljava/lang/String;)V", false);
      methodVisitor.visitVarInsn(ASTORE, 4);
      Label label45 = new Label();
      methodVisitor.visitLabel(label45);
      methodVisitor.visitInsn(ACONST_NULL);
      methodVisitor.visitVarInsn(ASTORE, 5);
      methodVisitor.visitLabel(label20);
      methodVisitor.visitLineNumber(101, label20);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitLdcInsn("iH");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
      methodVisitor.visitLabel(label21);
      methodVisitor.visitLineNumber(102, label21);
      methodVisitor.visitVarInsn(ALOAD, 5);
      methodVisitor.visitVarInsn(ALOAD, 4);
      methodVisitor.visitMethodInsn(
          INVOKESTATIC,
          "twr/twrcloseresource/Iface",
          "$closeResource",
          "(Ljava/lang/Throwable;Ljava/lang/AutoCloseable;)V",
          true);
      methodVisitor.visitJumpInsn(GOTO, label26);
      methodVisitor.visitLabel(label22);
      methodVisitor.visitLineNumber(100, label22);
      methodVisitor.visitFrame(
          Opcodes.F_FULL,
          6,
          new Object[] {
            "twr/twrcloseresource/Iface",
            "java/lang/String",
            "java/util/jar/JarFile",
            "java/lang/Throwable",
            "java/util/jar/JarFile",
            "java/lang/Throwable"
          },
          1,
          new Object[] {"java/lang/Throwable"});
      methodVisitor.visitVarInsn(ASTORE, 6);
      methodVisitor.visitVarInsn(ALOAD, 6);
      methodVisitor.visitVarInsn(ASTORE, 5);
      methodVisitor.visitVarInsn(ALOAD, 6);
      methodVisitor.visitInsn(ATHROW);
      methodVisitor.visitLabel(label23);
      methodVisitor.visitLineNumber(102, label23);
      methodVisitor.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] {"java/lang/Throwable"});
      methodVisitor.visitVarInsn(ASTORE, 8);
      methodVisitor.visitLabel(label24);
      methodVisitor.visitVarInsn(ALOAD, 5);
      methodVisitor.visitVarInsn(ALOAD, 4);
      methodVisitor.visitMethodInsn(
          INVOKESTATIC,
          "twr/twrcloseresource/Iface",
          "$closeResource",
          "(Ljava/lang/Throwable;Ljava/lang/AutoCloseable;)V",
          true);
      methodVisitor.visitVarInsn(ALOAD, 8);
      methodVisitor.visitInsn(ATHROW);
      methodVisitor.visitLabel(label26);
      methodVisitor.visitLineNumber(103, label26);
      methodVisitor.visitFrame(Opcodes.F_CHOP, 2, null, 0, null);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitLdcInsn("iI");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
      Label label46 = new Label();
      methodVisitor.visitLabel(label46);
      methodVisitor.visitLineNumber(104, label46);
      methodVisitor.visitTypeInsn(NEW, "java/lang/RuntimeException");
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitMethodInsn(
          INVOKESPECIAL, "java/lang/RuntimeException", "<init>", "()V", false);
      methodVisitor.visitInsn(ATHROW);
      methodVisitor.visitLabel(label27);
      methodVisitor.visitLineNumber(103, label27);
      methodVisitor.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] {"java/lang/Throwable"});
      methodVisitor.visitVarInsn(ASTORE, 9);
      methodVisitor.visitLabel(label28);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitLdcInsn("iI");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
      Label label47 = new Label();
      methodVisitor.visitLabel(label47);
      methodVisitor.visitLineNumber(104, label47);
      methodVisitor.visitTypeInsn(NEW, "java/lang/RuntimeException");
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitMethodInsn(
          INVOKESPECIAL, "java/lang/RuntimeException", "<init>", "()V", false);
      methodVisitor.visitInsn(ATHROW);
      methodVisitor.visitLabel(label30);
      methodVisitor.visitLineNumber(98, label30);
      methodVisitor.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] {"java/lang/Throwable"});
      methodVisitor.visitVarInsn(ASTORE, 4);
      methodVisitor.visitVarInsn(ALOAD, 4);
      methodVisitor.visitVarInsn(ASTORE, 3);
      methodVisitor.visitVarInsn(ALOAD, 4);
      methodVisitor.visitInsn(ATHROW);
      methodVisitor.visitLabel(label32);
      methodVisitor.visitLineNumber(106, label32);
      methodVisitor.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] {"java/lang/Throwable"});
      methodVisitor.visitVarInsn(ASTORE, 10);
      methodVisitor.visitLabel(label31);
      methodVisitor.visitVarInsn(ALOAD, 3);
      methodVisitor.visitVarInsn(ALOAD, 2);
      methodVisitor.visitMethodInsn(
          INVOKESTATIC,
          "twr/twrcloseresource/Iface",
          "$closeResource",
          "(Ljava/lang/Throwable;Ljava/lang/AutoCloseable;)V",
          true);
      methodVisitor.visitVarInsn(ALOAD, 10);
      methodVisitor.visitInsn(ATHROW);
      methodVisitor.visitLabel(label34);
      methodVisitor.visitFrame(
          Opcodes.F_FULL,
          2,
          new Object[] {"twr/twrcloseresource/Iface", "java/lang/String"},
          1,
          new Object[] {"java/lang/Exception"});
      methodVisitor.visitVarInsn(ASTORE, 2);
      Label label48 = new Label();
      methodVisitor.visitLabel(label48);
      methodVisitor.visitLineNumber(107, label48);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitLdcInsn("iJ");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
      Label label49 = new Label();
      methodVisitor.visitLabel(label49);
      methodVisitor.visitLineNumber(109, label49);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitLdcInsn("iK");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
      Label label50 = new Label();
      methodVisitor.visitLabel(label50);
      methodVisitor.visitLineNumber(110, label50);
      methodVisitor.visitInsn(RETURN);
      Label label51 = new Label();
      methodVisitor.visitLabel(label51);
      methodVisitor.visitLocalVariable("a", "Ljava/util/jar/JarFile;", null, label35, label6, 2);
      methodVisitor.visitLocalVariable("a", "Ljava/util/jar/JarFile;", null, label39, label40, 3);
      methodVisitor.visitLocalVariable("e", "Ljava/lang/Exception;", null, label37, label36, 2);
      methodVisitor.visitLocalVariable("a", "Ljava/util/jar/JarFile;", null, label42, label18, 2);
      methodVisitor.visitLocalVariable("e", "Ljava/lang/Exception;", null, label43, label33, 2);
      methodVisitor.visitLocalVariable("b", "Ljava/util/jar/JarFile;", null, label45, label26, 4);
      methodVisitor.visitLocalVariable("a", "Ljava/util/jar/JarFile;", null, label44, label34, 2);
      methodVisitor.visitLocalVariable("e", "Ljava/lang/Exception;", null, label48, label49, 2);
      methodVisitor.visitLocalVariable(
          "this", "Ltwrcloseresource/Iface;", null, label5, label51, 0);
      methodVisitor.visitLocalVariable("arg", "Ljava/lang/String;", null, label5, label51, 1);
      methodVisitor.visitMaxs(3, 11);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor =
          classWriter.visitMethod(
              ACC_PUBLIC | ACC_STATIC, "iBar", "(Ljava/lang/String;)V", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      Label label1 = new Label();
      methodVisitor.visitTryCatchBlock(label0, label1, label1, "java/lang/Throwable");
      Label label2 = new Label();
      Label label3 = new Label();
      methodVisitor.visitTryCatchBlock(label0, label2, label3, null);
      Label label4 = new Label();
      Label label5 = new Label();
      methodVisitor.visitTryCatchBlock(label4, label5, label5, "java/lang/Exception");
      Label label6 = new Label();
      Label label7 = new Label();
      methodVisitor.visitTryCatchBlock(label6, label7, label7, "java/lang/Throwable");
      Label label8 = new Label();
      Label label9 = new Label();
      methodVisitor.visitTryCatchBlock(label6, label8, label9, null);
      Label label10 = new Label();
      Label label11 = new Label();
      methodVisitor.visitTryCatchBlock(label10, label11, label11, "java/lang/Exception");
      Label label12 = new Label();
      Label label13 = new Label();
      methodVisitor.visitTryCatchBlock(label12, label13, label13, "java/lang/Throwable");
      Label label14 = new Label();
      Label label15 = new Label();
      methodVisitor.visitTryCatchBlock(label12, label14, label15, null);
      Label label16 = new Label();
      Label label17 = new Label();
      methodVisitor.visitTryCatchBlock(label16, label17, label17, "java/lang/Exception");
      Label label18 = new Label();
      Label label19 = new Label();
      methodVisitor.visitTryCatchBlock(label18, label19, label19, "java/lang/Throwable");
      Label label20 = new Label();
      Label label21 = new Label();
      methodVisitor.visitTryCatchBlock(label18, label20, label21, null);
      Label label22 = new Label();
      Label label23 = new Label();
      methodVisitor.visitTryCatchBlock(label22, label23, label23, "java/lang/Exception");
      methodVisitor.visitLabel(label4);
      methodVisitor.visitLineNumber(113, label4);
      methodVisitor.visitTypeInsn(NEW, "java/util/jar/JarFile");
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitMethodInsn(
          INVOKESPECIAL, "java/util/jar/JarFile", "<init>", "(Ljava/lang/String;)V", false);
      methodVisitor.visitVarInsn(ASTORE, 1);
      Label label24 = new Label();
      methodVisitor.visitLabel(label24);
      methodVisitor.visitInsn(ACONST_NULL);
      methodVisitor.visitVarInsn(ASTORE, 2);
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(114, label0);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitLdcInsn("i1");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
      Label label25 = new Label();
      methodVisitor.visitLabel(label25);
      methodVisitor.visitLineNumber(115, label25);
      methodVisitor.visitTypeInsn(NEW, "java/lang/RuntimeException");
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitMethodInsn(
          INVOKESPECIAL, "java/lang/RuntimeException", "<init>", "()V", false);
      methodVisitor.visitInsn(ATHROW);
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLineNumber(113, label1);
      methodVisitor.visitFrame(
          Opcodes.F_FULL,
          3,
          new Object[] {"java/lang/String", "java/util/jar/JarFile", "java/lang/Throwable"},
          1,
          new Object[] {"java/lang/Throwable"});
      methodVisitor.visitVarInsn(ASTORE, 3);
      methodVisitor.visitVarInsn(ALOAD, 3);
      methodVisitor.visitVarInsn(ASTORE, 2);
      methodVisitor.visitVarInsn(ALOAD, 3);
      methodVisitor.visitInsn(ATHROW);
      methodVisitor.visitLabel(label3);
      methodVisitor.visitLineNumber(116, label3);
      methodVisitor.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] {"java/lang/Throwable"});
      methodVisitor.visitVarInsn(ASTORE, 4);
      methodVisitor.visitLabel(label2);
      methodVisitor.visitVarInsn(ALOAD, 2);
      methodVisitor.visitVarInsn(ALOAD, 1);
      methodVisitor.visitMethodInsn(
          INVOKESTATIC,
          "twr/twrcloseresource/Iface",
          "$closeResource",
          "(Ljava/lang/Throwable;Ljava/lang/AutoCloseable;)V",
          true);
      methodVisitor.visitVarInsn(ALOAD, 4);
      methodVisitor.visitInsn(ATHROW);
      methodVisitor.visitLabel(label5);
      methodVisitor.visitFrame(
          Opcodes.F_FULL,
          1,
          new Object[] {"java/lang/String"},
          1,
          new Object[] {"java/lang/Exception"});
      methodVisitor.visitVarInsn(ASTORE, 1);
      Label label26 = new Label();
      methodVisitor.visitLabel(label26);
      methodVisitor.visitLineNumber(117, label26);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitLdcInsn("i2");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
      methodVisitor.visitLabel(label10);
      methodVisitor.visitLineNumber(119, label10);
      methodVisitor.visitTypeInsn(NEW, "java/util/jar/JarFile");
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitMethodInsn(
          INVOKESPECIAL, "java/util/jar/JarFile", "<init>", "(Ljava/lang/String;)V", false);
      methodVisitor.visitVarInsn(ASTORE, 1);
      Label label27 = new Label();
      methodVisitor.visitLabel(label27);
      methodVisitor.visitInsn(ACONST_NULL);
      methodVisitor.visitVarInsn(ASTORE, 2);
      methodVisitor.visitLabel(label6);
      methodVisitor.visitLineNumber(120, label6);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitLdcInsn("i3");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
      Label label28 = new Label();
      methodVisitor.visitLabel(label28);
      methodVisitor.visitLineNumber(121, label28);
      methodVisitor.visitTypeInsn(NEW, "java/lang/RuntimeException");
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitMethodInsn(
          INVOKESPECIAL, "java/lang/RuntimeException", "<init>", "()V", false);
      methodVisitor.visitInsn(ATHROW);
      methodVisitor.visitLabel(label7);
      methodVisitor.visitLineNumber(119, label7);
      methodVisitor.visitFrame(
          Opcodes.F_FULL,
          3,
          new Object[] {"java/lang/String", "java/util/jar/JarFile", "java/lang/Throwable"},
          1,
          new Object[] {"java/lang/Throwable"});
      methodVisitor.visitVarInsn(ASTORE, 3);
      methodVisitor.visitVarInsn(ALOAD, 3);
      methodVisitor.visitVarInsn(ASTORE, 2);
      methodVisitor.visitVarInsn(ALOAD, 3);
      methodVisitor.visitInsn(ATHROW);
      methodVisitor.visitLabel(label9);
      methodVisitor.visitLineNumber(122, label9);
      methodVisitor.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] {"java/lang/Throwable"});
      methodVisitor.visitVarInsn(ASTORE, 5);
      methodVisitor.visitLabel(label8);
      methodVisitor.visitVarInsn(ALOAD, 2);
      methodVisitor.visitVarInsn(ALOAD, 1);
      methodVisitor.visitMethodInsn(
          INVOKESTATIC,
          "twr/twrcloseresource/Iface",
          "$closeResource",
          "(Ljava/lang/Throwable;Ljava/lang/AutoCloseable;)V",
          true);
      methodVisitor.visitVarInsn(ALOAD, 5);
      methodVisitor.visitInsn(ATHROW);
      methodVisitor.visitLabel(label11);
      methodVisitor.visitFrame(
          Opcodes.F_FULL,
          1,
          new Object[] {"java/lang/String"},
          1,
          new Object[] {"java/lang/Exception"});
      methodVisitor.visitVarInsn(ASTORE, 1);
      Label label29 = new Label();
      methodVisitor.visitLabel(label29);
      methodVisitor.visitLineNumber(123, label29);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitLdcInsn("i4");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
      methodVisitor.visitLabel(label16);
      methodVisitor.visitLineNumber(125, label16);
      methodVisitor.visitTypeInsn(NEW, "java/util/jar/JarFile");
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitMethodInsn(
          INVOKESPECIAL, "java/util/jar/JarFile", "<init>", "(Ljava/lang/String;)V", false);
      methodVisitor.visitVarInsn(ASTORE, 1);
      Label label30 = new Label();
      methodVisitor.visitLabel(label30);
      methodVisitor.visitInsn(ACONST_NULL);
      methodVisitor.visitVarInsn(ASTORE, 2);
      methodVisitor.visitLabel(label12);
      methodVisitor.visitLineNumber(126, label12);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitLdcInsn("i5");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
      Label label31 = new Label();
      methodVisitor.visitLabel(label31);
      methodVisitor.visitLineNumber(127, label31);
      methodVisitor.visitTypeInsn(NEW, "java/lang/RuntimeException");
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitMethodInsn(
          INVOKESPECIAL, "java/lang/RuntimeException", "<init>", "()V", false);
      methodVisitor.visitInsn(ATHROW);
      methodVisitor.visitLabel(label13);
      methodVisitor.visitLineNumber(125, label13);
      methodVisitor.visitFrame(
          Opcodes.F_FULL,
          3,
          new Object[] {"java/lang/String", "java/util/jar/JarFile", "java/lang/Throwable"},
          1,
          new Object[] {"java/lang/Throwable"});
      methodVisitor.visitVarInsn(ASTORE, 3);
      methodVisitor.visitVarInsn(ALOAD, 3);
      methodVisitor.visitVarInsn(ASTORE, 2);
      methodVisitor.visitVarInsn(ALOAD, 3);
      methodVisitor.visitInsn(ATHROW);
      methodVisitor.visitLabel(label15);
      methodVisitor.visitLineNumber(128, label15);
      methodVisitor.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] {"java/lang/Throwable"});
      methodVisitor.visitVarInsn(ASTORE, 6);
      methodVisitor.visitLabel(label14);
      methodVisitor.visitVarInsn(ALOAD, 2);
      methodVisitor.visitVarInsn(ALOAD, 1);
      methodVisitor.visitMethodInsn(
          INVOKESTATIC,
          "twr/twrcloseresource/Iface",
          "$closeResource",
          "(Ljava/lang/Throwable;Ljava/lang/AutoCloseable;)V",
          true);
      methodVisitor.visitVarInsn(ALOAD, 6);
      methodVisitor.visitInsn(ATHROW);
      methodVisitor.visitLabel(label17);
      methodVisitor.visitFrame(
          Opcodes.F_FULL,
          1,
          new Object[] {"java/lang/String"},
          1,
          new Object[] {"java/lang/Exception"});
      methodVisitor.visitVarInsn(ASTORE, 1);
      Label label32 = new Label();
      methodVisitor.visitLabel(label32);
      methodVisitor.visitLineNumber(129, label32);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitLdcInsn("i6");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
      methodVisitor.visitLabel(label22);
      methodVisitor.visitLineNumber(131, label22);
      methodVisitor.visitTypeInsn(NEW, "java/util/jar/JarFile");
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitMethodInsn(
          INVOKESPECIAL, "java/util/jar/JarFile", "<init>", "(Ljava/lang/String;)V", false);
      methodVisitor.visitVarInsn(ASTORE, 1);
      Label label33 = new Label();
      methodVisitor.visitLabel(label33);
      methodVisitor.visitInsn(ACONST_NULL);
      methodVisitor.visitVarInsn(ASTORE, 2);
      methodVisitor.visitLabel(label18);
      methodVisitor.visitLineNumber(132, label18);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitLdcInsn("i7");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
      Label label34 = new Label();
      methodVisitor.visitLabel(label34);
      methodVisitor.visitLineNumber(133, label34);
      methodVisitor.visitTypeInsn(NEW, "java/lang/RuntimeException");
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitMethodInsn(
          INVOKESPECIAL, "java/lang/RuntimeException", "<init>", "()V", false);
      methodVisitor.visitInsn(ATHROW);
      methodVisitor.visitLabel(label19);
      methodVisitor.visitLineNumber(131, label19);
      methodVisitor.visitFrame(
          Opcodes.F_FULL,
          3,
          new Object[] {"java/lang/String", "java/util/jar/JarFile", "java/lang/Throwable"},
          1,
          new Object[] {"java/lang/Throwable"});
      methodVisitor.visitVarInsn(ASTORE, 3);
      methodVisitor.visitVarInsn(ALOAD, 3);
      methodVisitor.visitVarInsn(ASTORE, 2);
      methodVisitor.visitVarInsn(ALOAD, 3);
      methodVisitor.visitInsn(ATHROW);
      methodVisitor.visitLabel(label21);
      methodVisitor.visitLineNumber(134, label21);
      methodVisitor.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] {"java/lang/Throwable"});
      methodVisitor.visitVarInsn(ASTORE, 7);
      methodVisitor.visitLabel(label20);
      methodVisitor.visitVarInsn(ALOAD, 2);
      methodVisitor.visitVarInsn(ALOAD, 1);
      methodVisitor.visitMethodInsn(
          INVOKESTATIC,
          "twr/twrcloseresource/Iface",
          "$closeResource",
          "(Ljava/lang/Throwable;Ljava/lang/AutoCloseable;)V",
          true);
      methodVisitor.visitVarInsn(ALOAD, 7);
      methodVisitor.visitInsn(ATHROW);
      methodVisitor.visitLabel(label23);
      methodVisitor.visitFrame(
          Opcodes.F_FULL,
          1,
          new Object[] {"java/lang/String"},
          1,
          new Object[] {"java/lang/Exception"});
      methodVisitor.visitVarInsn(ASTORE, 1);
      Label label35 = new Label();
      methodVisitor.visitLabel(label35);
      methodVisitor.visitLineNumber(135, label35);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitLdcInsn("i8");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
      Label label36 = new Label();
      methodVisitor.visitLabel(label36);
      methodVisitor.visitLineNumber(137, label36);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitLdcInsn("i99");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
      Label label37 = new Label();
      methodVisitor.visitLabel(label37);
      methodVisitor.visitLineNumber(138, label37);
      methodVisitor.visitInsn(RETURN);
      Label label38 = new Label();
      methodVisitor.visitLabel(label38);
      methodVisitor.visitLocalVariable("a", "Ljava/util/jar/JarFile;", null, label24, label5, 1);
      methodVisitor.visitLocalVariable("e", "Ljava/lang/Exception;", null, label26, label10, 1);
      methodVisitor.visitLocalVariable("a", "Ljava/util/jar/JarFile;", null, label27, label11, 1);
      methodVisitor.visitLocalVariable("e", "Ljava/lang/Exception;", null, label29, label16, 1);
      methodVisitor.visitLocalVariable("a", "Ljava/util/jar/JarFile;", null, label30, label17, 1);
      methodVisitor.visitLocalVariable("e", "Ljava/lang/Exception;", null, label32, label22, 1);
      methodVisitor.visitLocalVariable("a", "Ljava/util/jar/JarFile;", null, label33, label23, 1);
      methodVisitor.visitLocalVariable("e", "Ljava/lang/Exception;", null, label35, label36, 1);
      methodVisitor.visitLocalVariable("arg", "Ljava/lang/String;", null, label4, label38, 0);
      methodVisitor.visitMaxs(3, 8);
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
      methodVisitor.visitLineNumber(84, label3);
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
