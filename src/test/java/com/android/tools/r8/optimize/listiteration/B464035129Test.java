// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.listiteration;

import static com.android.tools.r8.utils.InternalOptions.ASM_VERSION;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.transformers.ClassTransformer;
import com.android.tools.r8.utils.StringUtils;
import java.util.ArrayList;
import java.util.Iterator;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class B464035129Test extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private static final String EXPECTED_OUTPUT = StringUtils.lines("Hello, world!");

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addProgramClassFileData(getProgramClassFileData())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8(parameters)
        .addProgramClassFileData(getProgramClassFileData())
        .addOptionsModification(
            options ->
                options.getTestingOptions().listIterationRewritingRewriteCustomIterators = true)
        .release()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .addProgramClassFileData(getProgramClassFileData())
        .addKeepMainRule(Main.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  private static byte[] getProgramClassFileData() {
    return transformer(Main.class)
        .addClassTransformer(
            new ClassTransformer() {

              @Override
              public MethodVisitor visitMethod(
                  int access,
                  String name,
                  String descriptor,
                  String signature,
                  String[] exceptions) {
                MethodVisitor mv =
                    super.visitMethod(access, name, descriptor, signature, exceptions);
                if (!name.equals("main")) {
                  return mv;
                }
                return new MethodVisitor(ASM_VERSION, mv) {

                  boolean seenIteratorCall;
                  Label labelLoopBodyEntry;
                  Label extraLabelLoopBodyEntry;
                  int labelLoopBodyEntryJumps;

                  @Override
                  public void visitMethodInsn(
                      int opcode,
                      String owner,
                      String name,
                      String descriptor,
                      boolean isInterface) {
                    if (name.equals("iterator")) {
                      seenIteratorCall = true;
                    }
                    super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                  }

                  @Override
                  public void visitLabel(Label label) {
                    if (seenIteratorCall && labelLoopBodyEntry == null) {
                      labelLoopBodyEntry = label;
                    }
                    super.visitLabel(label);
                  }

                  @Override
                  public void visitFrame(
                      int type, int numLocal, Object[] local, int numStack, Object[] stack) {
                    super.visitFrame(type, numLocal, local, numStack, stack);
                    if (labelLoopBodyEntry != null && extraLabelLoopBodyEntry == null) {
                      // This is the frame for the loop body entry.
                      assertTrue(seenIteratorCall);
                      // Emit a nop for a block to materialize.
                      super.visitInsn(Opcodes.NOP);
                      // Inject a new label with the same frame.
                      extraLabelLoopBodyEntry = new Label();
                      super.visitLabel(extraLabelLoopBodyEntry);
                      super.visitFrame(3, 0, null, 0, null);
                    }
                  }

                  @Override
                  public void visitJumpInsn(int opcode, Label label) {
                    if (label == labelLoopBodyEntry) {
                      assertNotNull(extraLabelLoopBodyEntry);
                      if (labelLoopBodyEntryJumps == 1) {
                        label = extraLabelLoopBodyEntry;
                      }
                      labelLoopBodyEntryJumps++;
                    }
                    super.visitJumpInsn(opcode, label);
                  }

                  @Override
                  public void visitInsn(int opcode) {
                    if (opcode == Opcodes.RETURN) {
                      assertEquals(2, labelLoopBodyEntryJumps);
                    }
                    super.visitInsn(opcode);
                  }
                };
              }
            })
        .transform();
  }

  static class Main {

    public static void main(String[] args) {
      // Implemented using transformer.
      ArrayList<String> list = new ArrayList<>();
      list.add("Hello");
      list.add(", world!");
      StringBuilder sb = new StringBuilder();
      Iterator<String> iterator = list.iterator();
      // This is the point of the loop body entry block.
      // This test transforms the code from looking like:
      //        new           #10                 // class java/lang/StringBuilder
      //        invokespecial #11                 // Method java/lang/StringBuilder."<init>":()V
      //        astore        4
      //        aload_2
      //        invokeinterface #12,  1           // InterfaceMethod java/util/Iterator.hasNext:()Z
      //
      // ... to:
      //        new           #10                 // class java/lang/StringBuilder
      //        invokespecial #11                 // Method java/lang/StringBuilder."<init>":()V
      //        astore        4
      //        nop
      //        aload_2
      //        invokeinterface #12,  1           // InterfaceMethod java/util/Iterator.hasNext:()Z
      //
      // The `continue` instruction below is then rewritten to jump to the injected nop instruction.
      while (iterator.hasNext()) {
        String current = iterator.next();
        sb.append(current);
        if (System.currentTimeMillis() > 0) {
          // Materialize a jump in the CF code. This jump will be rewritten to an injected label.
          continue;
        }
      }
      System.out.println(sb);
    }
  }
}
