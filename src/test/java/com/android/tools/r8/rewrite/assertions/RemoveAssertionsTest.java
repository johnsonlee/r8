// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.rewrite.assertions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.function.Function;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

// This ASM class visitor has been adapted from
// https://chromium.googlesource.com/chromium/src/+/164e81fcd0828b40f5496e9025349ea728cde7f5/build/android/bytecode/java/org/chromium/bytecode/AssertionEnablerClassAdapter.java
// See b/110887293.

/**
 * An ClassVisitor for replacing Java ASSERT statements with a function by modifying Java bytecode.
 *
 * We do this in two steps, first step is to enable assert.
 * Following bytecode is generated for each class with ASSERT statements:
 * 0: ldc #8 // class CLASSNAME
 * 2: invokevirtual #9 // Method java/lang/Class.desiredAssertionStatus:()Z
 * 5: ifne 12
 * 8: iconst_1
 * 9: goto 13
 * 12: iconst_0
 * 13: putstatic #2 // Field $assertionsDisabled:Z
 * Replaces line #13 to the following:
 * 13: pop
 * Consequently, $assertionsDisabled is assigned the default value FALSE.
 * This is done in the first if statement in overridden visitFieldInsn. We do this per per-assert.
 *
 * Second step is to replace assert statement with a function:
 * The followed instructions are generated by a java assert statement:
 * getstatic     #3     // Field $assertionsDisabled:Z
 * ifne          118    // Jump to instruction as if assertion if not enabled
 * ...
 * ifne          19
 * new           #4     // class java/lang/AssertionError
 * dup
 * ldc           #5     // String (don't have this line if no assert message given)
 * invokespecial #6     // Method java/lang/AssertionError.
 * athrow
 * Replace athrow with:
 * invokestatic  #7     // Method org/chromium/base/JavaExceptionReporter.assertFailureHandler
 * goto          118
 * JavaExceptionReporter.assertFailureHandler is a function that handles the AssertionError,
 * 118 is the instruction to execute as if assertion if not enabled.
 */
class AssertionEnablerClassAdapter extends ClassVisitor {
  AssertionEnablerClassAdapter(ClassVisitor visitor) {
    super(Opcodes.ASM6, visitor);
  }

  @Override
  public MethodVisitor visitMethod(final int access, final String name, String desc,
      String signature, String[] exceptions) {
    return new RewriteAssertMethodVisitor(
        Opcodes.ASM5, super.visitMethod(access, name, desc, signature, exceptions));
  }

  static class RewriteAssertMethodVisitor extends MethodVisitor {
    static final String ASSERTION_DISABLED_NAME = "$assertionsDisabled";
    static final String INSERT_INSTRUCTION_NAME = "assertFailureHandler";
    static final String INSERT_INSTRUCTION_DESC =
        Type.getMethodDescriptor(Type.VOID_TYPE, Type.getObjectType("java/lang/AssertionError"));
    static final boolean INSERT_INSTRUCTION_ITF = false;

    boolean mStartLoadingAssert;
    Label mGotoLabel;

    public RewriteAssertMethodVisitor(int api, MethodVisitor mv) {
      super(api, mv);
    }

    @Override
    public void visitFieldInsn(int opcode, String owner, String name, String desc) {
      if (opcode == Opcodes.PUTSTATIC && name.equals(ASSERTION_DISABLED_NAME)) {
        super.visitInsn(Opcodes.POP); // enable assert
      } else if (opcode == Opcodes.GETSTATIC && name.equals(ASSERTION_DISABLED_NAME)) {
        mStartLoadingAssert = true;
        super.visitFieldInsn(opcode, owner, name, desc);
      } else {
        super.visitFieldInsn(opcode, owner, name, desc);
      }
    }

    @Override
    public void visitJumpInsn(int opcode, Label label) {
      if (mStartLoadingAssert && opcode == Opcodes.IFNE && mGotoLabel == null) {
        mGotoLabel = label;
      }
      super.visitJumpInsn(opcode, label);
    }

    @Override
    public void visitInsn(int opcode) {
      if (!mStartLoadingAssert || opcode != Opcodes.ATHROW) {
        super.visitInsn(opcode);
      } else {
        super.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            ChromuimAssertionHookMock.class.getCanonicalName().replace('.', '/'),
            INSERT_INSTRUCTION_NAME,
            INSERT_INSTRUCTION_DESC,
            INSERT_INSTRUCTION_ITF);
        super.visitJumpInsn(Opcodes.GOTO, mGotoLabel);
        mStartLoadingAssert = false;
        mGotoLabel = null;
      }
    }
  }
}

public class RemoveAssertionsTest extends TestBase {

  @Test
  public void test() throws Exception {
    // Run with R8, but avoid inlining to really validate that the methods "condition"
    // and "<clinit>" are gone.
    Class testClass = ClassWithAssertions.class;
    AndroidApp app = compileWithR8(
        ImmutableList.of(testClass),
        keepMainProguardConfiguration(testClass, true, false),
        options -> options.enableInlining = false);
    CodeInspector x = new CodeInspector(app);

    ClassSubject clazz = x.clazz(ClassWithAssertions.class);
    assertTrue(clazz.isPresent());
    MethodSubject conditionMethod =
        clazz.method(new MethodSignature("condition", "boolean", new String[]{}));
    assertTrue(!conditionMethod.isPresent());
    MethodSubject clinit =
        clazz.method(new MethodSignature(Constants.CLASS_INITIALIZER_NAME, "void", new String[]{}));
    assertTrue(!clinit.isPresent());
  }

  private Path buildTestToCf(Consumer<InternalOptions> consumer) throws Exception {
    Path outputJar = temp.getRoot().toPath().resolve("output.jar");
    R8Command command =
        ToolHelper.prepareR8CommandBuilder(readClasses(ClassWithAssertions.class))
            .setMode(CompilationMode.DEBUG)
            .setDisableTreeShaking(true)
            .setDisableMinification(true)
            .addLibraryFiles(ToolHelper.getJava8RuntimeJar())
            .setOutput(outputJar, OutputMode.ClassFile)
            .build();
    ToolHelper.runR8(command, consumer);
    return outputJar;
  }

  @Test
  public void testCfOutput() throws Exception {
    String main = ClassWithAssertions.class.getCanonicalName();
    ProcessResult result;
    // Assertion is hit.
    result = ToolHelper.runJava(buildTestToCf(options -> {}), "-ea", main, "0");
    assertEquals(1, result.exitCode);
    assertEquals("1\n".replace("\n", System.lineSeparator()), result.stdout);
    // Assertion is not hit.
    result = ToolHelper.runJava(buildTestToCf(options -> {}), "-ea", main, "1");
    assertEquals(0, result.exitCode);
    assertEquals("1\n2\n".replace("\n", System.lineSeparator()), result.stdout);
    // Assertion is hit, but removed.
    result = ToolHelper.runJava(
        buildTestToCf(
            options -> options.disableAssertions = true), "-ea", main, "0");
    assertEquals(0, result.exitCode);
    assertEquals("1\n2\n".replace("\n", System.lineSeparator()), result.stdout);
  }

  private byte[] identity(byte[] classBytes) {
    return classBytes;
  }

  private byte[] chromiumAssertionEnabler(byte[] classBytes) {
    ClassWriter writer = new ClassWriter(0);
    new ClassReader(classBytes).accept(new AssertionEnablerClassAdapter(writer), 0);
    return writer.toByteArray();
  }

  private AndroidApp runRegress110887293(Function<byte[], byte[]> rewriter) throws Exception {
    return ToolHelper.runR8(
        R8Command.builder()
            .addClassProgramData(
                rewriter.apply(ToolHelper.getClassAsBytes(ClassWithAssertions.class)),
                Origin.unknown())
            .addClassProgramData(
                ToolHelper.getClassAsBytes(ChromuimAssertionHookMock.class), Origin.unknown())
            .setProgramConsumer(DexIndexedConsumer.emptyConsumer())
            .setMode(CompilationMode.DEBUG)
            .setDisableTreeShaking(true)
            .setDisableMinification(true)
            .build());
  }

  @Test
  public void regress110887293() throws Exception {
    AndroidApp app;
    // Assertions removed for default assertion code.
    app = runRegress110887293(this::identity);
    assertEquals("1\n2\n", runOnArt(app, ClassWithAssertions.class.getCanonicalName(), "0"));
    assertEquals("1\n2\n", runOnArt(app, ClassWithAssertions.class.getCanonicalName(), "1"));
    // Assertions not removed when default assertion code is not present.
    app = runRegress110887293(this::chromiumAssertionEnabler);
    assertEquals(
        "1\nGot AssertionError java.lang.AssertionError\n2\n".replace("\n", System.lineSeparator()),
        runOnArt(app, ClassWithAssertions.class.getCanonicalName(), "0"));
    assertEquals(
        "1\n2\n".replace("\n", System.lineSeparator()),
        runOnArt(app, ClassWithAssertions.class.getCanonicalName(), "1"));
  }
}
