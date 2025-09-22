// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.D8TestBuilder;
import com.android.tools.r8.D8TestCompileResult;
import com.android.tools.r8.DexSegments;
import com.android.tools.r8.DexSegments.SegmentInfo;
import com.android.tools.r8.ResourceException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanUtils;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class DexCodeInvokeSuperDeduppingTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public boolean enableMappingOutput;

  @Parameters(name = "{0}, map output: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimesAndAllApiLevels().build(), BooleanUtils.values());
  }

  @Test
  public void testD8MappingOutput() throws Exception {
    testForD8(parameters.getBackend())
        .addProgramClasses(Base.class, FooBase.class, BarBase.class, Main.class)
        .addProgramClassFileData(getProgramClassFileData())
        .setMinApi(parameters)
        .release()
        .applyIf(enableMappingOutput, D8TestBuilder::internalEnableMappingOutput)
        .compile()
        .apply(this::inspectCodeSegment)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("FooBase", "BarBase");
  }

  private List<byte[]> getProgramClassFileData() throws IOException {
    return ImmutableList.of(
        transformer(Foo.class)
            .transformMethodInsnInMethod(
                "foo",
                (opcode, owner, name, descriptor, isInterface, visitor) -> {
                  if (opcode == Opcodes.INVOKESPECIAL) {
                    assertEquals(
                        "com/android/tools/r8/dex/DexCodeInvokeSuperDeduppingTest$FooBase", owner);
                    owner = binaryName(Base.class);
                  }
                  visitor.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                })
            .transform(),
        transformer(Bar.class)
            .transformMethodInsnInMethod(
                "bar",
                (opcode, owner, name, descriptor, isInterface, visitor) -> {
                  if (opcode == Opcodes.INVOKESPECIAL) {
                    assertEquals(
                        "com/android/tools/r8/dex/DexCodeInvokeSuperDeduppingTest$BarBase", owner);
                    owner = binaryName(Base.class);
                  }
                  visitor.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                })
            .transform());
  }

  private void inspectCodeSegment(D8TestCompileResult compileResult) throws Exception {
    SegmentInfo codeSegmentInfo = getCodeSegmentInfo(compileResult.writeToZip());
    int expectedCodeItems = 11;
    if (parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.S) && enableMappingOutput) {
      // Main.<init> and Base.<init> are canonicalized, and so is FooBase.<init> and BarBase.<init>.
      assertEquals(expectedCodeItems - 2, codeSegmentInfo.getItemCount());
    } else {
      assertEquals(expectedCodeItems, codeSegmentInfo.getItemCount());
    }
  }

  public SegmentInfo getCodeSegmentInfo(Path path)
      throws CompilationFailedException, ResourceException, IOException {
    DexSegments.Command command = DexSegments.Command.builder().addProgramFiles(path).build();
    Map<Integer, SegmentInfo> segmentInfoMap = DexSegments.runForTesting(command);
    return segmentInfoMap.get(Constants.TYPE_CODE_ITEM);
  }

  static class Main {

    public static void main(String[] args) {
      new Foo().foo();
      new Bar().bar();
    }
  }

  abstract static class Base {

    abstract void m();
  }

  static class FooBase extends Base {

    @Override
    void m() {
      System.out.println("FooBase");
    }
  }

  static class Foo extends FooBase {

    void foo() {
      // Symbolic holder transformed to Base class.
      super.m();
    }
  }

  static class BarBase extends Base {

    @Override
    void m() {
      System.out.println("BarBase");
    }
  }

  static class Bar extends BarBase {

    void bar() {
      // Symbolic holder transformed to Base class.
      super.m();
    }
  }
}
