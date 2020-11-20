// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.b171982443;

import static com.android.tools.r8.KotlinCompilerTool.KOTLINC;
import static com.android.tools.r8.desugar.b171982443.Caller.callI;
import static org.hamcrest.CoreMatchers.equalTo;

import com.android.tools.r8.KotlinTestBase;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.KotlinTargetVersion;
import com.android.tools.r8.utils.DescriptorUtils;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

// This is a reproduction of b/171982443.
@RunWith(Parameterized.class)
public class DefaultMethodInvokeReprocessingTest extends KotlinTestBase {

  private static final String PKG =
      DefaultMethodInvokeReprocessingTest.class.getPackage().getName();
  private static final String PKG_PREFIX = DescriptorUtils.getBinaryNameFromJavaType(PKG);
  private static final String[] EXPECTED = new String[] {"A::foo", "Hello1", "Hello2", "Hello3"};
  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimes().withAllApiLevels().build(),
        KotlinTargetVersion.values());
  }

  public DefaultMethodInvokeReprocessingTest(
      TestParameters parameters, KotlinTargetVersion targetVersion) {
    super(targetVersion);
    this.parameters = parameters;
  }

  private static final Map<KotlinTargetVersion, Path> libJarMap = new HashMap<>();

  @BeforeClass
  public static void createLibJar() throws Exception {
    for (KotlinTargetVersion kotlinTargetVersion : KotlinTargetVersion.values()) {
      libJarMap.put(
          kotlinTargetVersion,
          kotlinc(KOTLINC, kotlinTargetVersion)
              .addSourceFiles(getKotlinFileInTest(PKG_PREFIX, "Simple"))
              .compile());
    }
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addInnerClasses(DefaultMethodInvokeReprocessingTest.class)
        .addProgramClassFileData(Caller.dump())
        .addProgramFiles(ToolHelper.getKotlinStdlibJar())
        .addProgramFiles(libJarMap.get(targetVersion))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(DefaultMethodInvokeReprocessingTest.class)
        .addProgramFiles(libJarMap.get(targetVersion))
        .addProgramFiles(ToolHelper.getKotlinStdlibJar())
        .addProgramClassFileData(Caller.dump())
        .setMinApi(parameters.getApiLevel())
        .addKeepMainRule(Main.class)
        .addKeepClassAndMembersRules(I.class)
        .enableInliningAnnotations()
        .allowDiagnosticWarningMessages()
        .compile()
        .assertAllWarningMessagesMatch(equalTo("Resource 'META-INF/MANIFEST.MF' already exists."))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  public interface I {

    default void print() {
      System.out.println("I::foo");
    }
  }

  public static class A implements I {

    @Override
    @NeverInline
    public void print() {
      System.out.println("A::foo");
    }
  }

  public static class Main {

    public static void main(String[] args) {
      callI(args.length == 0 ? new A() : new I() {});
    }
  }
}
