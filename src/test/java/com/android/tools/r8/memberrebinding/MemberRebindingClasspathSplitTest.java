// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.memberrebinding;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestBuilder;
import com.android.tools.r8.TestCompileResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ThrowableConsumer;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.graph.AccessFlags;
import com.android.tools.r8.memberrebinding.testclasses.MemberRebindingClasspathSplitTestClasses;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MemberRebindingClasspathSplitTest extends TestBase {
  @Parameter(0)
  public TestParameters parameters;

  private static class TestConfig {
    private final String desc;
    private final ThrowableConsumer<? super TestBuilder<?, ?>> addToClasspath;
    private final ThrowableConsumer<? super TestBuilder<?, ?>> addToProgrampath;
    private final ThrowableConsumer<? super TestCompileResult<?, ?>> addToRunClasspath;
    private final boolean expectFailure;

    private TestConfig(
        String desc,
        ThrowableConsumer<? super TestBuilder<?, ?>> addToClasspath,
        ThrowableConsumer<? super TestBuilder<?, ?>> addToProgrampath,
        ThrowableConsumer<? super TestCompileResult<?, ?>> addToRunClasspath,
        boolean expectFailure) {
      this.desc = desc;
      this.addToClasspath = addToClasspath;
      this.addToProgrampath = addToProgrampath;
      this.addToRunClasspath = addToRunClasspath;
      this.expectFailure = expectFailure;
    }

    @Override
    public String toString() {
      return desc;
    }
  }

  @Parameter(1)
  public TestConfig split;

  @Parameters(name = "{0}, classpathsplit: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(),
        ImmutableList.of(
            new TestConfig(
                "Both A and B on classpath",
                b -> {
                  b.addClasspathClasses(
                      MemberRebindingClasspathSplitTestClasses.getA(),
                      MemberRebindingClasspathSplitTestClasses.getB());
                },
                b -> {},
                b -> {
                  b.addRunClasspathClasses(
                      MemberRebindingClasspathSplitTestClasses.getA(),
                      MemberRebindingClasspathSplitTestClasses.getB());
                },
                false),
            new TestConfig(
                "Both A and B on classpath, m() bridge removed from B",
                b -> {
                  b.addClasspathClasses(MemberRebindingClasspathSplitTestClasses.getA());
                  b.addClasspathClassFileData(
                      transformer(MemberRebindingClasspathSplitTestClasses.getB())
                          .removeMethodsWithName("m")
                          .transform());
                },
                b -> {},
                b -> {
                  b.addRunClasspathClasses(MemberRebindingClasspathSplitTestClasses.getA());
                  b.addRunClasspathClassFileData(
                      transformer(MemberRebindingClasspathSplitTestClasses.getB())
                          .removeMethodsWithName("m")
                          .transform());
                },
                false),
            new TestConfig(
                "A on classpath and B on programpath",
                b -> {
                  b.addClasspathClasses(MemberRebindingClasspathSplitTestClasses.getA());
                },
                b -> {
                  b.addProgramClasses(MemberRebindingClasspathSplitTestClasses.getB());
                },
                b -> {
                  b.addRunClasspathClasses(MemberRebindingClasspathSplitTestClasses.getA());
                },
                false),
            new TestConfig(
                "A on classpath and B on programpath, m() bridge removed from B",
                b -> {
                  b.addClasspathClasses(MemberRebindingClasspathSplitTestClasses.getA());
                },
                b -> {
                  b.addProgramClassFileData(
                      transformer(MemberRebindingClasspathSplitTestClasses.getB())
                          .removeMethodsWithName("m")
                          .transform());
                },
                b -> {
                  b.addRunClasspathClasses(MemberRebindingClasspathSplitTestClasses.getA());
                },
                false),
            new TestConfig(
                "Both A and B on classpath but neither A or B is public",
                b -> {
                  b.addClasspathClasses(MemberRebindingClasspathSplitTestClasses.getA());
                  b.addClasspathClassFileData(
                      transformer(MemberRebindingClasspathSplitTestClasses.getB())
                          .setAccessFlags(AccessFlags::unsetPublic)
                          .transform());
                },
                b -> {},
                b -> {
                  b.addRunClasspathClasses(MemberRebindingClasspathSplitTestClasses.getA());
                  b.addRunClasspathClassFileData(
                      transformer(MemberRebindingClasspathSplitTestClasses.getB())
                          .setAccessFlags(AccessFlags::unsetPublic)
                          .transform());
                },
                true)));
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .apply(split.addToClasspath)
        .apply(split.addToProgrampath)
        .allowStdoutMessages()
        .addDontObfuscate()
        .addDontOptimize()
        .addKeepRules("-keep class " + Main.class.getTypeName() + " { void main(...); }")
        .compile()
        .apply(split.addToRunClasspath)
        .run(parameters.getRuntime(), Main.class)
        .applyIf(
            split.expectFailure && parameters.isDexRuntimeVersionOlderThanOrEqual(Version.V4_4_4),
            rr -> rr.assertFailureWithErrorThatThrows(NoClassDefFoundError.class),
            split.expectFailure,
            rr -> rr.assertFailureWithErrorThatThrows(IllegalAccessError.class),
            rr -> rr.assertSuccessWithOutputLines("A", "A"));
  }

  public static class C extends MemberRebindingClasspathSplitTestClasses.B {
    public String superm() {
      return super.m();
    }
  }

  public static class Main {
    public static void main(String[] strArr) {
      C c = new C();
      System.out.println(c.m());
      System.out.println(c.superm());
    }
  }
}
