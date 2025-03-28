// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.java23.switchpatternmatching;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.RecordComponentVisitor;
import org.objectweb.asm.Type;

/**
 * This is generated as a dump since the Main class has to be javac compiled withg --enable-preview.
 *
 * <p>Dump generated from:
 *
 * <pre>
 * package com.android.tools.r8.java23.switchpatternmatching;
 *
 * public class DexIntValuePrimitiveSwitchMain {
 *
 *     static void booleanSwitch(boolean b) {
 *         switch (b) {
 *             case true -> {
 *                 System.out.println("true");
 *             }
 *             default -> {
 *                 System.out.println("false");
 *             }
 *         }
 *     }
 *
 *     static void doubleSwitch(double d) {
 *         switch (d) {
 *             case 42.0 -> {
 *                 System.out.println("42");
 *             }
 *             case Double f2 when f2 > 0 -> {
 *                 System.out.println("positif");
 *             }
 *             default -> {
 *                 System.out.println("negatif");
 *             }
 *         }
 *     }
 *
 *     static void floatSwitch(float f) {
 *         switch (f) {
 *             case 42.0f -> {
 *                 System.out.println("42");
 *             }
 *             case Float f2 when f2 > 0 -> {
 *                 System.out.println("positif");
 *             }
 *             default -> {
 *                 System.out.println("negatif");
 *             }
 *         }
 *     }
 *
 *     static void longSwitch(long l) {
 *         switch (l) {
 *             case 42L -> {
 *                 System.out.println("42");
 *             }
 *             case Long i2 when i2 > 0 -> {
 *                 System.out.println("positif");
 *             }
 *             default -> {
 *                 System.out.println("negatif");
 *             }
 *         }
 *     }
 *
 *     static void intSwitch(int i) {
 *         switch (i) {
 *             case 42 -> {
 *                 System.out.println("42");
 *             }
 *             case int i2 when i2 > 0 -> {
 *                 System.out.println("positif");
 *             }
 *             default -> {
 *                 System.out.println("negatif");
 *             }
 *         }
 *     }
 *
 *     static void shortSwitch(short s) {
 *         switch (s) {
 *             case 42 -> {
 *                 System.out.println("42");
 *             }
 *             case short s2 when s2 > 0 -> {
 *                 System.out.println("positif");
 *             }
 *             default -> {
 *                 System.out.println("negatif");
 *             }
 *         }
 *     }
 *
 *     static void charSwitch(char c) {
 *         switch (c) {
 *             case 'c' -> {
 *                 System.out.println("c");
 *             }
 *             case char c2 when Character.isUpperCase(c2) -> {
 *                 System.out.println("upper");
 *             }
 *             default -> {
 *                 System.out.println("lower");
 *             }
 *         }
 *     }
 *
 *     static void byteSwitch(byte b) {
 *         switch (b) {
 *             case 42 -> {
 *                 System.out.println("42");
 *             }
 *             case byte b2 when b2 > 0 -> {
 *                 System.out.println("positif");
 *             }
 *             default -> {
 *                 System.out.println("negatif");
 *             }
 *         }
 *     }
 *
 *     public static void main(String[] args) {
 *         intSwitch(42);
 *         intSwitch(12);
 *         intSwitch(-1);
 *
 *         charSwitch('c');
 *         charSwitch('X');
 *         charSwitch('x');
 *
 *         byteSwitch((byte) 42);
 *         byteSwitch((byte) 12);
 *         byteSwitch((byte) -1);
 *
 *         shortSwitch((short) 42);
 *         shortSwitch((short) 12);
 *         shortSwitch((short) -1);
 *
 *         longSwitch(42L);
 *         longSwitch(12L);
 *         longSwitch(-1L);
 *
 *         floatSwitch(42.0f);
 *         floatSwitch(12.0f);
 *         floatSwitch(-1.0f);
 *
 *         doubleSwitch(42.0);
 *         doubleSwitch(12.0);
 *         doubleSwitch(-1.0);
 *
 *         booleanSwitch(true);
 *         booleanSwitch(false);
 *     }
 * }
 * </pre>
 */
public class DexIntValuePrimitiveSwitchMainDump implements Opcodes {

  public static byte[] dump() throws Exception {

    ClassWriter classWriter = new ClassWriter(0);
    FieldVisitor fieldVisitor;
    RecordComponentVisitor recordComponentVisitor;
    MethodVisitor methodVisitor;
    AnnotationVisitor annotationVisitor0;

    classWriter.visit(
        -65469,
        ACC_PUBLIC | ACC_SUPER,
        "com/android/tools/r8/java23/switchpatternmatching/DexIntValuePrimitiveSwitchMain",
        null,
        "java/lang/Object",
        null);

    classWriter.visitSource("DexIntValuePrimitiveSwitchMain.java", null);

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
      methodVisitor.visitLineNumber(7, label0);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(1, 1);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor = classWriter.visitMethod(ACC_STATIC, "booleanSwitch", "(Z)V", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(10, label0);
      methodVisitor.visitVarInsn(ILOAD, 0);
      methodVisitor.visitVarInsn(ISTORE, 1);
      methodVisitor.visitInsn(ICONST_0);
      methodVisitor.visitVarInsn(ISTORE, 2);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitFrame(
          Opcodes.F_APPEND, 2, new Object[] {Opcodes.INTEGER, Opcodes.INTEGER}, 0, null);
      methodVisitor.visitVarInsn(ILOAD, 1);
      methodVisitor.visitVarInsn(ILOAD, 2);
      methodVisitor.visitInvokeDynamicInsn(
          "typeSwitch",
          "(ZI)I",
          new Handle(
              Opcodes.H_INVOKESTATIC,
              "java/lang/runtime/SwitchBootstraps",
              "typeSwitch",
              "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;",
              false),
          new Object[] {
            new ConstantDynamic(
                "TRUE",
                "Ljava/lang/Boolean;",
                new Handle(
                    Opcodes.H_INVOKESTATIC,
                    "java/lang/invoke/ConstantBootstraps",
                    "getStaticFinal",
                    "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/Object;",
                    false),
                new Object[] {})
          });
      Label label2 = new Label();
      Label label3 = new Label();
      methodVisitor.visitLookupSwitchInsn(label3, new int[] {0}, new Label[] {label2});
      methodVisitor.visitLabel(label2);
      methodVisitor.visitLineNumber(12, label2);
      methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitLdcInsn("true");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
      Label label4 = new Label();
      methodVisitor.visitLabel(label4);
      methodVisitor.visitLineNumber(13, label4);
      Label label5 = new Label();
      methodVisitor.visitJumpInsn(GOTO, label5);
      methodVisitor.visitLabel(label3);
      methodVisitor.visitLineNumber(15, label3);
      methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitLdcInsn("false");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
      methodVisitor.visitLabel(label5);
      methodVisitor.visitLineNumber(18, label5);
      methodVisitor.visitFrame(Opcodes.F_CHOP, 2, null, 0, null);
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(2, 3);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor = classWriter.visitMethod(ACC_STATIC, "doubleSwitch", "(D)V", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(21, label0);
      methodVisitor.visitVarInsn(DLOAD, 0);
      methodVisitor.visitVarInsn(DSTORE, 2);
      methodVisitor.visitInsn(ICONST_0);
      methodVisitor.visitVarInsn(ISTORE, 4);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitFrame(
          Opcodes.F_APPEND, 2, new Object[] {Opcodes.DOUBLE, Opcodes.INTEGER}, 0, null);
      methodVisitor.visitVarInsn(DLOAD, 2);
      methodVisitor.visitVarInsn(ILOAD, 4);
      methodVisitor.visitInvokeDynamicInsn(
          "typeSwitch",
          "(DI)I",
          new Handle(
              Opcodes.H_INVOKESTATIC,
              "java/lang/runtime/SwitchBootstraps",
              "typeSwitch",
              "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;",
              false),
          new Object[] {new Double("42.0"), Type.getType("Ljava/lang/Double;")});
      Label label2 = new Label();
      Label label3 = new Label();
      Label label4 = new Label();
      methodVisitor.visitLookupSwitchInsn(label4, new int[] {0, 1}, new Label[] {label2, label3});
      methodVisitor.visitLabel(label2);
      methodVisitor.visitLineNumber(23, label2);
      methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitLdcInsn("42");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
      Label label5 = new Label();
      methodVisitor.visitLabel(label5);
      methodVisitor.visitLineNumber(24, label5);
      Label label6 = new Label();
      methodVisitor.visitJumpInsn(GOTO, label6);
      methodVisitor.visitLabel(label3);
      methodVisitor.visitLineNumber(25, label3);
      methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      methodVisitor.visitVarInsn(DLOAD, 2);
      methodVisitor.visitMethodInsn(
          INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false);
      methodVisitor.visitVarInsn(ASTORE, 5);
      methodVisitor.visitVarInsn(ALOAD, 5);
      methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Double", "doubleValue", "()D", false);
      methodVisitor.visitInsn(DCONST_0);
      methodVisitor.visitInsn(DCMPL);
      Label label7 = new Label();
      methodVisitor.visitJumpInsn(IFGT, label7);
      methodVisitor.visitInsn(ICONST_2);
      methodVisitor.visitVarInsn(ISTORE, 4);
      methodVisitor.visitJumpInsn(GOTO, label1);
      methodVisitor.visitLabel(label7);
      methodVisitor.visitLineNumber(26, label7);
      methodVisitor.visitFrame(Opcodes.F_APPEND, 1, new Object[] {"java/lang/Double"}, 0, null);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitLdcInsn("positif");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
      Label label8 = new Label();
      methodVisitor.visitLabel(label8);
      methodVisitor.visitLineNumber(27, label8);
      methodVisitor.visitJumpInsn(GOTO, label6);
      methodVisitor.visitLabel(label4);
      methodVisitor.visitLineNumber(29, label4);
      methodVisitor.visitFrame(Opcodes.F_CHOP, 1, null, 0, null);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitLdcInsn("negatif");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
      Label label9 = new Label();
      methodVisitor.visitLabel(label9);
      methodVisitor.visitLineNumber(30, label9);
      methodVisitor.visitJumpInsn(GOTO, label6);
      methodVisitor.visitLabel(label6);
      methodVisitor.visitLineNumber(32, label6);
      methodVisitor.visitFrame(Opcodes.F_CHOP, 2, null, 0, null);
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(4, 6);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor = classWriter.visitMethod(ACC_STATIC, "floatSwitch", "(F)V", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(35, label0);
      methodVisitor.visitVarInsn(FLOAD, 0);
      methodVisitor.visitVarInsn(FSTORE, 1);
      methodVisitor.visitInsn(ICONST_0);
      methodVisitor.visitVarInsn(ISTORE, 2);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitFrame(
          Opcodes.F_APPEND, 2, new Object[] {Opcodes.FLOAT, Opcodes.INTEGER}, 0, null);
      methodVisitor.visitVarInsn(FLOAD, 1);
      methodVisitor.visitVarInsn(ILOAD, 2);
      methodVisitor.visitInvokeDynamicInsn(
          "typeSwitch",
          "(FI)I",
          new Handle(
              Opcodes.H_INVOKESTATIC,
              "java/lang/runtime/SwitchBootstraps",
              "typeSwitch",
              "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;",
              false),
          new Object[] {new Float("42.0"), Type.getType("Ljava/lang/Float;")});
      Label label2 = new Label();
      Label label3 = new Label();
      Label label4 = new Label();
      methodVisitor.visitLookupSwitchInsn(label4, new int[] {0, 1}, new Label[] {label2, label3});
      methodVisitor.visitLabel(label2);
      methodVisitor.visitLineNumber(37, label2);
      methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitLdcInsn("42");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
      Label label5 = new Label();
      methodVisitor.visitLabel(label5);
      methodVisitor.visitLineNumber(38, label5);
      Label label6 = new Label();
      methodVisitor.visitJumpInsn(GOTO, label6);
      methodVisitor.visitLabel(label3);
      methodVisitor.visitLineNumber(39, label3);
      methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      methodVisitor.visitVarInsn(FLOAD, 1);
      methodVisitor.visitMethodInsn(
          INVOKESTATIC, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;", false);
      methodVisitor.visitVarInsn(ASTORE, 3);
      methodVisitor.visitVarInsn(ALOAD, 3);
      methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Float", "floatValue", "()F", false);
      methodVisitor.visitInsn(FCONST_0);
      methodVisitor.visitInsn(FCMPL);
      Label label7 = new Label();
      methodVisitor.visitJumpInsn(IFGT, label7);
      methodVisitor.visitInsn(ICONST_2);
      methodVisitor.visitVarInsn(ISTORE, 2);
      methodVisitor.visitJumpInsn(GOTO, label1);
      methodVisitor.visitLabel(label7);
      methodVisitor.visitLineNumber(40, label7);
      methodVisitor.visitFrame(Opcodes.F_APPEND, 1, new Object[] {"java/lang/Float"}, 0, null);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitLdcInsn("positif");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
      Label label8 = new Label();
      methodVisitor.visitLabel(label8);
      methodVisitor.visitLineNumber(41, label8);
      methodVisitor.visitJumpInsn(GOTO, label6);
      methodVisitor.visitLabel(label4);
      methodVisitor.visitLineNumber(43, label4);
      methodVisitor.visitFrame(Opcodes.F_CHOP, 1, null, 0, null);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitLdcInsn("negatif");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
      Label label9 = new Label();
      methodVisitor.visitLabel(label9);
      methodVisitor.visitLineNumber(44, label9);
      methodVisitor.visitJumpInsn(GOTO, label6);
      methodVisitor.visitLabel(label6);
      methodVisitor.visitLineNumber(46, label6);
      methodVisitor.visitFrame(Opcodes.F_CHOP, 2, null, 0, null);
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(2, 4);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor = classWriter.visitMethod(ACC_STATIC, "longSwitch", "(J)V", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(49, label0);
      methodVisitor.visitVarInsn(LLOAD, 0);
      methodVisitor.visitVarInsn(LSTORE, 2);
      methodVisitor.visitInsn(ICONST_0);
      methodVisitor.visitVarInsn(ISTORE, 4);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitFrame(
          Opcodes.F_APPEND, 2, new Object[] {Opcodes.LONG, Opcodes.INTEGER}, 0, null);
      methodVisitor.visitVarInsn(LLOAD, 2);
      methodVisitor.visitVarInsn(ILOAD, 4);
      methodVisitor.visitInvokeDynamicInsn(
          "typeSwitch",
          "(JI)I",
          new Handle(
              Opcodes.H_INVOKESTATIC,
              "java/lang/runtime/SwitchBootstraps",
              "typeSwitch",
              "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;",
              false),
          new Object[] {new Long(42L), Type.getType("Ljava/lang/Long;")});
      Label label2 = new Label();
      Label label3 = new Label();
      Label label4 = new Label();
      methodVisitor.visitLookupSwitchInsn(label4, new int[] {0, 1}, new Label[] {label2, label3});
      methodVisitor.visitLabel(label2);
      methodVisitor.visitLineNumber(51, label2);
      methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitLdcInsn("42");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
      Label label5 = new Label();
      methodVisitor.visitLabel(label5);
      methodVisitor.visitLineNumber(52, label5);
      Label label6 = new Label();
      methodVisitor.visitJumpInsn(GOTO, label6);
      methodVisitor.visitLabel(label3);
      methodVisitor.visitLineNumber(53, label3);
      methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      methodVisitor.visitVarInsn(LLOAD, 2);
      methodVisitor.visitMethodInsn(
          INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false);
      methodVisitor.visitVarInsn(ASTORE, 5);
      methodVisitor.visitVarInsn(ALOAD, 5);
      methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Long", "longValue", "()J", false);
      methodVisitor.visitInsn(LCONST_0);
      methodVisitor.visitInsn(LCMP);
      Label label7 = new Label();
      methodVisitor.visitJumpInsn(IFGT, label7);
      methodVisitor.visitInsn(ICONST_2);
      methodVisitor.visitVarInsn(ISTORE, 4);
      methodVisitor.visitJumpInsn(GOTO, label1);
      methodVisitor.visitLabel(label7);
      methodVisitor.visitLineNumber(54, label7);
      methodVisitor.visitFrame(Opcodes.F_APPEND, 1, new Object[] {"java/lang/Long"}, 0, null);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitLdcInsn("positif");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
      Label label8 = new Label();
      methodVisitor.visitLabel(label8);
      methodVisitor.visitLineNumber(55, label8);
      methodVisitor.visitJumpInsn(GOTO, label6);
      methodVisitor.visitLabel(label4);
      methodVisitor.visitLineNumber(57, label4);
      methodVisitor.visitFrame(Opcodes.F_CHOP, 1, null, 0, null);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitLdcInsn("negatif");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
      Label label9 = new Label();
      methodVisitor.visitLabel(label9);
      methodVisitor.visitLineNumber(58, label9);
      methodVisitor.visitJumpInsn(GOTO, label6);
      methodVisitor.visitLabel(label6);
      methodVisitor.visitLineNumber(60, label6);
      methodVisitor.visitFrame(Opcodes.F_CHOP, 2, null, 0, null);
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(4, 6);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor = classWriter.visitMethod(ACC_STATIC, "intSwitch", "(I)V", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(63, label0);
      methodVisitor.visitVarInsn(ILOAD, 0);
      methodVisitor.visitVarInsn(ISTORE, 1);
      methodVisitor.visitInsn(ICONST_0);
      methodVisitor.visitVarInsn(ISTORE, 2);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitFrame(
          Opcodes.F_APPEND, 2, new Object[] {Opcodes.INTEGER, Opcodes.INTEGER}, 0, null);
      methodVisitor.visitVarInsn(ILOAD, 1);
      methodVisitor.visitVarInsn(ILOAD, 2);
      methodVisitor.visitInvokeDynamicInsn(
          "typeSwitch",
          "(II)I",
          new Handle(
              Opcodes.H_INVOKESTATIC,
              "java/lang/runtime/SwitchBootstraps",
              "typeSwitch",
              "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;",
              false),
          new Object[] {
            new Integer(42),
            new ConstantDynamic(
                "I",
                "Ljava/lang/Class;",
                new Handle(
                    Opcodes.H_INVOKESTATIC,
                    "java/lang/invoke/ConstantBootstraps",
                    "primitiveClass",
                    "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/Class;",
                    false),
                new Object[] {})
          });
      Label label2 = new Label();
      Label label3 = new Label();
      Label label4 = new Label();
      methodVisitor.visitLookupSwitchInsn(label4, new int[] {0, 1}, new Label[] {label2, label3});
      methodVisitor.visitLabel(label2);
      methodVisitor.visitLineNumber(65, label2);
      methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitLdcInsn("42");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
      Label label5 = new Label();
      methodVisitor.visitLabel(label5);
      methodVisitor.visitLineNumber(66, label5);
      Label label6 = new Label();
      methodVisitor.visitJumpInsn(GOTO, label6);
      methodVisitor.visitLabel(label3);
      methodVisitor.visitLineNumber(67, label3);
      methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      methodVisitor.visitVarInsn(ILOAD, 1);
      methodVisitor.visitVarInsn(ISTORE, 3);
      methodVisitor.visitVarInsn(ILOAD, 3);
      Label label7 = new Label();
      methodVisitor.visitJumpInsn(IFGT, label7);
      methodVisitor.visitInsn(ICONST_2);
      methodVisitor.visitVarInsn(ISTORE, 2);
      methodVisitor.visitJumpInsn(GOTO, label1);
      methodVisitor.visitLabel(label7);
      methodVisitor.visitLineNumber(68, label7);
      methodVisitor.visitFrame(Opcodes.F_APPEND, 1, new Object[] {Opcodes.INTEGER}, 0, null);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitLdcInsn("positif");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
      Label label8 = new Label();
      methodVisitor.visitLabel(label8);
      methodVisitor.visitLineNumber(69, label8);
      methodVisitor.visitJumpInsn(GOTO, label6);
      methodVisitor.visitLabel(label4);
      methodVisitor.visitLineNumber(71, label4);
      methodVisitor.visitFrame(Opcodes.F_CHOP, 1, null, 0, null);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitLdcInsn("negatif");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
      Label label9 = new Label();
      methodVisitor.visitLabel(label9);
      methodVisitor.visitLineNumber(72, label9);
      methodVisitor.visitJumpInsn(GOTO, label6);
      methodVisitor.visitLabel(label6);
      methodVisitor.visitLineNumber(74, label6);
      methodVisitor.visitFrame(Opcodes.F_CHOP, 2, null, 0, null);
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(2, 4);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor = classWriter.visitMethod(ACC_STATIC, "shortSwitch", "(S)V", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(77, label0);
      methodVisitor.visitVarInsn(ILOAD, 0);
      methodVisitor.visitVarInsn(ISTORE, 1);
      methodVisitor.visitInsn(ICONST_0);
      methodVisitor.visitVarInsn(ISTORE, 2);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitFrame(
          Opcodes.F_APPEND, 2, new Object[] {Opcodes.INTEGER, Opcodes.INTEGER}, 0, null);
      methodVisitor.visitVarInsn(ILOAD, 1);
      methodVisitor.visitVarInsn(ILOAD, 2);
      methodVisitor.visitInvokeDynamicInsn(
          "typeSwitch",
          "(SI)I",
          new Handle(
              Opcodes.H_INVOKESTATIC,
              "java/lang/runtime/SwitchBootstraps",
              "typeSwitch",
              "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;",
              false),
          new Object[] {
            new Integer(42),
            new ConstantDynamic(
                "S",
                "Ljava/lang/Class;",
                new Handle(
                    Opcodes.H_INVOKESTATIC,
                    "java/lang/invoke/ConstantBootstraps",
                    "primitiveClass",
                    "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/Class;",
                    false),
                new Object[] {})
          });
      Label label2 = new Label();
      Label label3 = new Label();
      Label label4 = new Label();
      methodVisitor.visitLookupSwitchInsn(label4, new int[] {0, 1}, new Label[] {label2, label3});
      methodVisitor.visitLabel(label2);
      methodVisitor.visitLineNumber(79, label2);
      methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitLdcInsn("42");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
      Label label5 = new Label();
      methodVisitor.visitLabel(label5);
      methodVisitor.visitLineNumber(80, label5);
      Label label6 = new Label();
      methodVisitor.visitJumpInsn(GOTO, label6);
      methodVisitor.visitLabel(label3);
      methodVisitor.visitLineNumber(81, label3);
      methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      methodVisitor.visitVarInsn(ILOAD, 1);
      methodVisitor.visitVarInsn(ISTORE, 3);
      methodVisitor.visitVarInsn(ILOAD, 3);
      Label label7 = new Label();
      methodVisitor.visitJumpInsn(IFGT, label7);
      methodVisitor.visitInsn(ICONST_2);
      methodVisitor.visitVarInsn(ISTORE, 2);
      methodVisitor.visitJumpInsn(GOTO, label1);
      methodVisitor.visitLabel(label7);
      methodVisitor.visitLineNumber(82, label7);
      methodVisitor.visitFrame(Opcodes.F_APPEND, 1, new Object[] {Opcodes.INTEGER}, 0, null);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitLdcInsn("positif");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
      Label label8 = new Label();
      methodVisitor.visitLabel(label8);
      methodVisitor.visitLineNumber(83, label8);
      methodVisitor.visitJumpInsn(GOTO, label6);
      methodVisitor.visitLabel(label4);
      methodVisitor.visitLineNumber(85, label4);
      methodVisitor.visitFrame(Opcodes.F_CHOP, 1, null, 0, null);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitLdcInsn("negatif");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
      Label label9 = new Label();
      methodVisitor.visitLabel(label9);
      methodVisitor.visitLineNumber(86, label9);
      methodVisitor.visitJumpInsn(GOTO, label6);
      methodVisitor.visitLabel(label6);
      methodVisitor.visitLineNumber(88, label6);
      methodVisitor.visitFrame(Opcodes.F_CHOP, 2, null, 0, null);
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(2, 4);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor = classWriter.visitMethod(ACC_STATIC, "charSwitch", "(C)V", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(91, label0);
      methodVisitor.visitVarInsn(ILOAD, 0);
      methodVisitor.visitVarInsn(ISTORE, 1);
      methodVisitor.visitInsn(ICONST_0);
      methodVisitor.visitVarInsn(ISTORE, 2);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitFrame(
          Opcodes.F_APPEND, 2, new Object[] {Opcodes.INTEGER, Opcodes.INTEGER}, 0, null);
      methodVisitor.visitVarInsn(ILOAD, 1);
      methodVisitor.visitVarInsn(ILOAD, 2);
      methodVisitor.visitInvokeDynamicInsn(
          "typeSwitch",
          "(CI)I",
          new Handle(
              Opcodes.H_INVOKESTATIC,
              "java/lang/runtime/SwitchBootstraps",
              "typeSwitch",
              "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;",
              false),
          new Object[] {
            new Integer(99),
            new ConstantDynamic(
                "C",
                "Ljava/lang/Class;",
                new Handle(
                    Opcodes.H_INVOKESTATIC,
                    "java/lang/invoke/ConstantBootstraps",
                    "primitiveClass",
                    "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/Class;",
                    false),
                new Object[] {})
          });
      Label label2 = new Label();
      Label label3 = new Label();
      Label label4 = new Label();
      methodVisitor.visitLookupSwitchInsn(label4, new int[] {0, 1}, new Label[] {label2, label3});
      methodVisitor.visitLabel(label2);
      methodVisitor.visitLineNumber(93, label2);
      methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitLdcInsn("c");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
      Label label5 = new Label();
      methodVisitor.visitLabel(label5);
      methodVisitor.visitLineNumber(94, label5);
      Label label6 = new Label();
      methodVisitor.visitJumpInsn(GOTO, label6);
      methodVisitor.visitLabel(label3);
      methodVisitor.visitLineNumber(95, label3);
      methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      methodVisitor.visitVarInsn(ILOAD, 1);
      methodVisitor.visitVarInsn(ISTORE, 3);
      methodVisitor.visitVarInsn(ILOAD, 3);
      methodVisitor.visitMethodInsn(
          INVOKESTATIC, "java/lang/Character", "isUpperCase", "(C)Z", false);
      Label label7 = new Label();
      methodVisitor.visitJumpInsn(IFNE, label7);
      methodVisitor.visitInsn(ICONST_2);
      methodVisitor.visitVarInsn(ISTORE, 2);
      methodVisitor.visitJumpInsn(GOTO, label1);
      methodVisitor.visitLabel(label7);
      methodVisitor.visitLineNumber(96, label7);
      methodVisitor.visitFrame(Opcodes.F_APPEND, 1, new Object[] {Opcodes.INTEGER}, 0, null);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitLdcInsn("upper");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
      Label label8 = new Label();
      methodVisitor.visitLabel(label8);
      methodVisitor.visitLineNumber(97, label8);
      methodVisitor.visitJumpInsn(GOTO, label6);
      methodVisitor.visitLabel(label4);
      methodVisitor.visitLineNumber(99, label4);
      methodVisitor.visitFrame(Opcodes.F_CHOP, 1, null, 0, null);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitLdcInsn("lower");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
      Label label9 = new Label();
      methodVisitor.visitLabel(label9);
      methodVisitor.visitLineNumber(100, label9);
      methodVisitor.visitJumpInsn(GOTO, label6);
      methodVisitor.visitLabel(label6);
      methodVisitor.visitLineNumber(102, label6);
      methodVisitor.visitFrame(Opcodes.F_CHOP, 2, null, 0, null);
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(2, 4);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor = classWriter.visitMethod(ACC_STATIC, "byteSwitch", "(B)V", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(105, label0);
      methodVisitor.visitVarInsn(ILOAD, 0);
      methodVisitor.visitVarInsn(ISTORE, 1);
      methodVisitor.visitInsn(ICONST_0);
      methodVisitor.visitVarInsn(ISTORE, 2);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitFrame(
          Opcodes.F_APPEND, 2, new Object[] {Opcodes.INTEGER, Opcodes.INTEGER}, 0, null);
      methodVisitor.visitVarInsn(ILOAD, 1);
      methodVisitor.visitVarInsn(ILOAD, 2);
      methodVisitor.visitInvokeDynamicInsn(
          "typeSwitch",
          "(BI)I",
          new Handle(
              Opcodes.H_INVOKESTATIC,
              "java/lang/runtime/SwitchBootstraps",
              "typeSwitch",
              "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;",
              false),
          new Object[] {
            new Integer(42),
            new ConstantDynamic(
                "B",
                "Ljava/lang/Class;",
                new Handle(
                    Opcodes.H_INVOKESTATIC,
                    "java/lang/invoke/ConstantBootstraps",
                    "primitiveClass",
                    "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/Class;",
                    false),
                new Object[] {})
          });
      Label label2 = new Label();
      Label label3 = new Label();
      Label label4 = new Label();
      methodVisitor.visitLookupSwitchInsn(label4, new int[] {0, 1}, new Label[] {label2, label3});
      methodVisitor.visitLabel(label2);
      methodVisitor.visitLineNumber(107, label2);
      methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitLdcInsn("42");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
      Label label5 = new Label();
      methodVisitor.visitLabel(label5);
      methodVisitor.visitLineNumber(108, label5);
      Label label6 = new Label();
      methodVisitor.visitJumpInsn(GOTO, label6);
      methodVisitor.visitLabel(label3);
      methodVisitor.visitLineNumber(109, label3);
      methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      methodVisitor.visitVarInsn(ILOAD, 1);
      methodVisitor.visitVarInsn(ISTORE, 3);
      methodVisitor.visitVarInsn(ILOAD, 3);
      Label label7 = new Label();
      methodVisitor.visitJumpInsn(IFGT, label7);
      methodVisitor.visitInsn(ICONST_2);
      methodVisitor.visitVarInsn(ISTORE, 2);
      methodVisitor.visitJumpInsn(GOTO, label1);
      methodVisitor.visitLabel(label7);
      methodVisitor.visitLineNumber(110, label7);
      methodVisitor.visitFrame(Opcodes.F_APPEND, 1, new Object[] {Opcodes.INTEGER}, 0, null);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitLdcInsn("positif");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
      Label label8 = new Label();
      methodVisitor.visitLabel(label8);
      methodVisitor.visitLineNumber(111, label8);
      methodVisitor.visitJumpInsn(GOTO, label6);
      methodVisitor.visitLabel(label4);
      methodVisitor.visitLineNumber(113, label4);
      methodVisitor.visitFrame(Opcodes.F_CHOP, 1, null, 0, null);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitLdcInsn("negatif");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
      Label label9 = new Label();
      methodVisitor.visitLabel(label9);
      methodVisitor.visitLineNumber(114, label9);
      methodVisitor.visitJumpInsn(GOTO, label6);
      methodVisitor.visitLabel(label6);
      methodVisitor.visitLineNumber(116, label6);
      methodVisitor.visitFrame(Opcodes.F_CHOP, 2, null, 0, null);
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(2, 4);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor =
          classWriter.visitMethod(
              ACC_PUBLIC | ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(119, label0);
      methodVisitor.visitIntInsn(BIPUSH, 42);
      methodVisitor.visitMethodInsn(
          INVOKESTATIC,
          "com/android/tools/r8/java23/switchpatternmatching/DexIntValuePrimitiveSwitchMain",
          "intSwitch",
          "(I)V",
          false);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLineNumber(120, label1);
      methodVisitor.visitIntInsn(BIPUSH, 12);
      methodVisitor.visitMethodInsn(
          INVOKESTATIC,
          "com/android/tools/r8/java23/switchpatternmatching/DexIntValuePrimitiveSwitchMain",
          "intSwitch",
          "(I)V",
          false);
      Label label2 = new Label();
      methodVisitor.visitLabel(label2);
      methodVisitor.visitLineNumber(121, label2);
      methodVisitor.visitInsn(ICONST_M1);
      methodVisitor.visitMethodInsn(
          INVOKESTATIC,
          "com/android/tools/r8/java23/switchpatternmatching/DexIntValuePrimitiveSwitchMain",
          "intSwitch",
          "(I)V",
          false);
      Label label3 = new Label();
      methodVisitor.visitLabel(label3);
      methodVisitor.visitLineNumber(123, label3);
      methodVisitor.visitIntInsn(BIPUSH, 99);
      methodVisitor.visitMethodInsn(
          INVOKESTATIC,
          "com/android/tools/r8/java23/switchpatternmatching/DexIntValuePrimitiveSwitchMain",
          "charSwitch",
          "(C)V",
          false);
      Label label4 = new Label();
      methodVisitor.visitLabel(label4);
      methodVisitor.visitLineNumber(124, label4);
      methodVisitor.visitIntInsn(BIPUSH, 88);
      methodVisitor.visitMethodInsn(
          INVOKESTATIC,
          "com/android/tools/r8/java23/switchpatternmatching/DexIntValuePrimitiveSwitchMain",
          "charSwitch",
          "(C)V",
          false);
      Label label5 = new Label();
      methodVisitor.visitLabel(label5);
      methodVisitor.visitLineNumber(125, label5);
      methodVisitor.visitIntInsn(BIPUSH, 120);
      methodVisitor.visitMethodInsn(
          INVOKESTATIC,
          "com/android/tools/r8/java23/switchpatternmatching/DexIntValuePrimitiveSwitchMain",
          "charSwitch",
          "(C)V",
          false);
      Label label6 = new Label();
      methodVisitor.visitLabel(label6);
      methodVisitor.visitLineNumber(127, label6);
      methodVisitor.visitIntInsn(BIPUSH, 42);
      methodVisitor.visitMethodInsn(
          INVOKESTATIC,
          "com/android/tools/r8/java23/switchpatternmatching/DexIntValuePrimitiveSwitchMain",
          "byteSwitch",
          "(B)V",
          false);
      Label label7 = new Label();
      methodVisitor.visitLabel(label7);
      methodVisitor.visitLineNumber(128, label7);
      methodVisitor.visitIntInsn(BIPUSH, 12);
      methodVisitor.visitMethodInsn(
          INVOKESTATIC,
          "com/android/tools/r8/java23/switchpatternmatching/DexIntValuePrimitiveSwitchMain",
          "byteSwitch",
          "(B)V",
          false);
      Label label8 = new Label();
      methodVisitor.visitLabel(label8);
      methodVisitor.visitLineNumber(129, label8);
      methodVisitor.visitInsn(ICONST_M1);
      methodVisitor.visitMethodInsn(
          INVOKESTATIC,
          "com/android/tools/r8/java23/switchpatternmatching/DexIntValuePrimitiveSwitchMain",
          "byteSwitch",
          "(B)V",
          false);
      Label label9 = new Label();
      methodVisitor.visitLabel(label9);
      methodVisitor.visitLineNumber(131, label9);
      methodVisitor.visitIntInsn(BIPUSH, 42);
      methodVisitor.visitMethodInsn(
          INVOKESTATIC,
          "com/android/tools/r8/java23/switchpatternmatching/DexIntValuePrimitiveSwitchMain",
          "shortSwitch",
          "(S)V",
          false);
      Label label10 = new Label();
      methodVisitor.visitLabel(label10);
      methodVisitor.visitLineNumber(132, label10);
      methodVisitor.visitIntInsn(BIPUSH, 12);
      methodVisitor.visitMethodInsn(
          INVOKESTATIC,
          "com/android/tools/r8/java23/switchpatternmatching/DexIntValuePrimitiveSwitchMain",
          "shortSwitch",
          "(S)V",
          false);
      Label label11 = new Label();
      methodVisitor.visitLabel(label11);
      methodVisitor.visitLineNumber(133, label11);
      methodVisitor.visitInsn(ICONST_M1);
      methodVisitor.visitMethodInsn(
          INVOKESTATIC,
          "com/android/tools/r8/java23/switchpatternmatching/DexIntValuePrimitiveSwitchMain",
          "shortSwitch",
          "(S)V",
          false);
      Label label12 = new Label();
      methodVisitor.visitLabel(label12);
      methodVisitor.visitLineNumber(135, label12);
      methodVisitor.visitLdcInsn(new Long(42L));
      methodVisitor.visitMethodInsn(
          INVOKESTATIC,
          "com/android/tools/r8/java23/switchpatternmatching/DexIntValuePrimitiveSwitchMain",
          "longSwitch",
          "(J)V",
          false);
      Label label13 = new Label();
      methodVisitor.visitLabel(label13);
      methodVisitor.visitLineNumber(136, label13);
      methodVisitor.visitLdcInsn(new Long(12L));
      methodVisitor.visitMethodInsn(
          INVOKESTATIC,
          "com/android/tools/r8/java23/switchpatternmatching/DexIntValuePrimitiveSwitchMain",
          "longSwitch",
          "(J)V",
          false);
      Label label14 = new Label();
      methodVisitor.visitLabel(label14);
      methodVisitor.visitLineNumber(137, label14);
      methodVisitor.visitLdcInsn(new Long(-1L));
      methodVisitor.visitMethodInsn(
          INVOKESTATIC,
          "com/android/tools/r8/java23/switchpatternmatching/DexIntValuePrimitiveSwitchMain",
          "longSwitch",
          "(J)V",
          false);
      Label label15 = new Label();
      methodVisitor.visitLabel(label15);
      methodVisitor.visitLineNumber(139, label15);
      methodVisitor.visitLdcInsn(new Float("42.0"));
      methodVisitor.visitMethodInsn(
          INVOKESTATIC,
          "com/android/tools/r8/java23/switchpatternmatching/DexIntValuePrimitiveSwitchMain",
          "floatSwitch",
          "(F)V",
          false);
      Label label16 = new Label();
      methodVisitor.visitLabel(label16);
      methodVisitor.visitLineNumber(140, label16);
      methodVisitor.visitLdcInsn(new Float("12.0"));
      methodVisitor.visitMethodInsn(
          INVOKESTATIC,
          "com/android/tools/r8/java23/switchpatternmatching/DexIntValuePrimitiveSwitchMain",
          "floatSwitch",
          "(F)V",
          false);
      Label label17 = new Label();
      methodVisitor.visitLabel(label17);
      methodVisitor.visitLineNumber(141, label17);
      methodVisitor.visitLdcInsn(new Float("-1.0"));
      methodVisitor.visitMethodInsn(
          INVOKESTATIC,
          "com/android/tools/r8/java23/switchpatternmatching/DexIntValuePrimitiveSwitchMain",
          "floatSwitch",
          "(F)V",
          false);
      Label label18 = new Label();
      methodVisitor.visitLabel(label18);
      methodVisitor.visitLineNumber(143, label18);
      methodVisitor.visitLdcInsn(new Double("42.0"));
      methodVisitor.visitMethodInsn(
          INVOKESTATIC,
          "com/android/tools/r8/java23/switchpatternmatching/DexIntValuePrimitiveSwitchMain",
          "doubleSwitch",
          "(D)V",
          false);
      Label label19 = new Label();
      methodVisitor.visitLabel(label19);
      methodVisitor.visitLineNumber(144, label19);
      methodVisitor.visitLdcInsn(new Double("12.0"));
      methodVisitor.visitMethodInsn(
          INVOKESTATIC,
          "com/android/tools/r8/java23/switchpatternmatching/DexIntValuePrimitiveSwitchMain",
          "doubleSwitch",
          "(D)V",
          false);
      Label label20 = new Label();
      methodVisitor.visitLabel(label20);
      methodVisitor.visitLineNumber(145, label20);
      methodVisitor.visitLdcInsn(new Double("-1.0"));
      methodVisitor.visitMethodInsn(
          INVOKESTATIC,
          "com/android/tools/r8/java23/switchpatternmatching/DexIntValuePrimitiveSwitchMain",
          "doubleSwitch",
          "(D)V",
          false);
      Label label21 = new Label();
      methodVisitor.visitLabel(label21);
      methodVisitor.visitLineNumber(147, label21);
      methodVisitor.visitInsn(ICONST_1);
      methodVisitor.visitMethodInsn(
          INVOKESTATIC,
          "com/android/tools/r8/java23/switchpatternmatching/DexIntValuePrimitiveSwitchMain",
          "booleanSwitch",
          "(Z)V",
          false);
      Label label22 = new Label();
      methodVisitor.visitLabel(label22);
      methodVisitor.visitLineNumber(148, label22);
      methodVisitor.visitInsn(ICONST_0);
      methodVisitor.visitMethodInsn(
          INVOKESTATIC,
          "com/android/tools/r8/java23/switchpatternmatching/DexIntValuePrimitiveSwitchMain",
          "booleanSwitch",
          "(Z)V",
          false);
      Label label23 = new Label();
      methodVisitor.visitLabel(label23);
      methodVisitor.visitLineNumber(149, label23);
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(2, 1);
      methodVisitor.visitEnd();
    }
    classWriter.visitEnd();

    return classWriter.toByteArray();
  }
}
