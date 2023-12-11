// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.enumunboxing.enummerging;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.enumunboxing.EnumUnboxingTestBase;
import com.android.tools.r8.graph.ClassAccessFlags;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.DescriptorUtils;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class NoEnumFlagEnumMergingTest extends EnumUnboxingTestBase {

  private final TestParameters parameters;
  private final boolean enumValueOptimization;
  private final EnumKeepRules enumKeepRules;

  @Parameters(name = "{0} valueOpt: {1} keep: {2}")
  public static List<Object[]> data() {
    return enumUnboxingTestParameters();
  }

  public NoEnumFlagEnumMergingTest(
      TestParameters parameters, boolean enumValueOptimization, EnumKeepRules enumKeepRules) {
    this.parameters = parameters;
    this.enumValueOptimization = enumValueOptimization;
    this.enumKeepRules = enumKeepRules;
  }

  @Test
  public void testEnumUnboxing() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramFiles(getEnumSubtypesOrOtherInputs(false))
        .addProgramClassFileData(getSubEnumProgramData(getEnumSubtypesOrOtherInputs(true)))
        .addKeepMainRule(Main.class)
        .addKeepRules(enumKeepRules.getKeepRules())
        .addEnumUnboxingInspector(inspector -> inspector.assertUnboxed(MyEnum2Cases.class))
        .enableInliningAnnotations()
        .addOptionsModification(opt -> enableEnumOptions(opt, enumValueOptimization))
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("336", "74", "96", "44");
  }

  private List<Path> getEnumSubtypesOrOtherInputs(boolean enumSubtypes) throws IOException {
    return ToolHelper.getClassFilesForInnerClasses(getClass()).stream()
        .filter(path -> isMyEnum2CasesSubtype(path) == enumSubtypes)
        .collect(Collectors.toList());
  }

  private boolean isMyEnum2CasesSubtype(Path c) {
    return c.toString().contains("MyEnum2Cases") && !c.toString().endsWith("MyEnum2Cases.class");
  }

  private List<byte[]> getSubEnumProgramData(List<Path> input) {
    // Some Kotlin enum subclasses don't have the enum flag set. See b/315186101.
    return input.stream()
        .map(
            path -> {
              try {
                String subtype = path.getFileName().toString();
                String subtypeNoClass =
                    subtype.substring(0, (subtype.length() - ".class".length()));
                String descr =
                    DescriptorUtils.replaceSimpleClassNameInDescriptor(
                        DescriptorUtils.javaTypeToDescriptor(MyEnum2Cases.class.getTypeName()),
                        subtypeNoClass);
                return transformer(path, Reference.classFromDescriptor(descr))
                    .setAccessFlags(ClassAccessFlags::unsetEnum)
                    .transform();
              } catch (IOException e) {
                throw new RuntimeException(e);
              }
            })
        .collect(Collectors.toList());
  }

  enum MyEnum2Cases {
    A(8) {
      @NeverInline
      @Override
      public long operate(long another) {
        return num * another;
      }
    },
    B(32) {
      @NeverInline
      @Override
      public long operate(long another) {
        return num + another;
      }
    };
    final long num;

    MyEnum2Cases(long num) {
      this.num = num;
    }

    public abstract long operate(long another);
  }

  static class Main {

    public static void main(String[] args) {
      System.out.println(MyEnum2Cases.A.operate(42));
      System.out.println(MyEnum2Cases.B.operate(42));
      System.out.println(indirect(MyEnum2Cases.A));
      System.out.println(indirect(MyEnum2Cases.B));
    }

    @NeverInline
    public static long indirect(MyEnum2Cases e) {
      return e.operate(12);
    }
  }
}
