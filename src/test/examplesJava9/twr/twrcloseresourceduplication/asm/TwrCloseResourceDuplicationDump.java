// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

/**
 * Dump of class file for twr.twrcloseresourceduplication.TwrCloseResourceDuplication
 *
 * <p>*
 *
 * <p>This is used to get the JDK-9 javac code with `$closeResource`, which `-target 9` for later
 * javac versions does not produce.
 *
 * <p>package twr.twrcloseresourceduplication;
 *
 * <p>import java.util.jar.JarFile;
 *
 * <p>public class TwrCloseResourceDuplication {
 *
 * <p>public static class Foo {
 *
 * <p>void foo(String name) { try (JarFile f = new JarFile(name)) { System.out.println("foo opened
 * 1"); } catch (Exception e) { System.out.println("foo caught from 1: " +
 * e.getClass().getSimpleName()); } finally { System.out.println("foo post close 1"); } try (JarFile
 * f = new JarFile(name)) { System.out.println("foo opened 2"); throw new RuntimeException(); }
 * catch (Exception e) { System.out.println("foo caught from 2: " + e.getClass().getSimpleName()); }
 * finally { System.out.println("foo post close 2"); } } }
 *
 * <p>public static class Bar {
 *
 * <p>void bar(String name) { try (JarFile f = new JarFile(name)) { System.out.println("bar opened
 * 1"); } catch (Exception e) { System.out.println("bar caught from 1: " +
 * e.getClass().getSimpleName()); } finally { System.out.println("bar post close 1"); } try (JarFile
 * f = new JarFile(name)) { System.out.println("bar opened 2"); throw new RuntimeException(); }
 * catch (Exception e) { System.out.println("bar caught from 2: " + e.getClass().getSimpleName()); }
 * finally { System.out.println("bar post close 2"); } } }
 *
 * <p>public static void main(String[] args) { new Foo().foo(args[0]); new Bar().bar(args[0]); } }
 */
package twr.twrcloseresourceduplication.asm;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.RecordComponentVisitor;

public class TwrCloseResourceDuplicationDump implements Opcodes {

  public static byte[] dump() throws Exception {

    ClassWriter classWriter = new ClassWriter(0);
    FieldVisitor fieldVisitor;
    RecordComponentVisitor recordComponentVisitor;
    MethodVisitor methodVisitor;
    AnnotationVisitor annotationVisitor0;

    classWriter.visit(
        V9,
        ACC_PUBLIC | ACC_SUPER,
        "twr/twrcloseresourceduplication/TwrCloseResourceDuplication",
        null,
        "java/lang/Object",
        null);

    classWriter.visitSource("TwrCloseResourceDuplication.java", null);

    classWriter.visitInnerClass(
        "twr/twrcloseresourceduplication/TwrCloseResourceDuplication$Bar",
        "twr/twrcloseresourceduplication/TwrCloseResourceDuplication",
        "Bar",
        ACC_PUBLIC | ACC_STATIC);

    classWriter.visitInnerClass(
        "twr/twrcloseresourceduplication/TwrCloseResourceDuplication$Foo",
        "twr/twrcloseresourceduplication/TwrCloseResourceDuplication",
        "Foo",
        ACC_PUBLIC | ACC_STATIC);

    {
      methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(8, label0);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
      methodVisitor.visitInsn(RETURN);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLocalVariable(
          "this",
          "Ltwrcloseresourceduplication/TwrCloseResourceDuplication;",
          null,
          label0,
          label1,
          0);
      methodVisitor.visitMaxs(1, 1);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor =
          classWriter.visitMethod(
              ACC_PUBLIC | ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(53, label0);
      methodVisitor.visitTypeInsn(
          NEW, "twr/twrcloseresourceduplication/TwrCloseResourceDuplication$Foo");
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitMethodInsn(
          INVOKESPECIAL,
          "twr/twrcloseresourceduplication/TwrCloseResourceDuplication$Foo",
          "<init>",
          "()V",
          false);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitInsn(ICONST_0);
      methodVisitor.visitInsn(AALOAD);
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL,
          "twr/twrcloseresourceduplication/TwrCloseResourceDuplication$Foo",
          "foo",
          "(Ljava/lang/String;)V",
          false);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLineNumber(54, label1);
      methodVisitor.visitTypeInsn(
          NEW, "twr/twrcloseresourceduplication/TwrCloseResourceDuplication$Bar");
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitMethodInsn(
          INVOKESPECIAL,
          "twr/twrcloseresourceduplication/TwrCloseResourceDuplication$Bar",
          "<init>",
          "()V",
          false);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitInsn(ICONST_0);
      methodVisitor.visitInsn(AALOAD);
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL,
          "twr/twrcloseresourceduplication/TwrCloseResourceDuplication$Bar",
          "bar",
          "(Ljava/lang/String;)V",
          false);
      Label label2 = new Label();
      methodVisitor.visitLabel(label2);
      methodVisitor.visitLineNumber(55, label2);
      methodVisitor.visitInsn(RETURN);
      Label label3 = new Label();
      methodVisitor.visitLabel(label3);
      methodVisitor.visitLocalVariable("args", "[Ljava/lang/String;", null, label0, label3, 0);
      methodVisitor.visitMaxs(3, 1);
      methodVisitor.visitEnd();
    }
    classWriter.visitEnd();

    return classWriter.toByteArray();
  }
}
