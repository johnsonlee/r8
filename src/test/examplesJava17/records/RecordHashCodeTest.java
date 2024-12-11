// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package records;

import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.JdkClassFileProvider;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RecordHashCodeTest extends TestBase {

  private static final String EXPECTED_RESULT =
      StringUtils.lines(
          "true", "true", "true", "true", "false", "false", "true", "true", "false", "false");

  @Parameter public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  private boolean isCfRuntimeWithNativeRecordSupport() {
    return parameters.isCfRuntime()
        && parameters.asCfRuntime().isNewerThanOrEqual(CfVm.JDK17)
        && parameters.getApiLevel().equals(AndroidApiLevel.B);
  }

  @Test
  public void testReference() throws Exception {
    assumeTrue(isCfRuntimeWithNativeRecordSupport());
    testForJvm(parameters)
        .addInnerClassesAndStrippedOuter(getClass())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  @Test
  public void testD8() throws Exception {
    testForD8(parameters.getBackend())
        .addInnerClassesAndStrippedOuter(getClass())
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .applyIf(
            isRecordsFullyDesugaredForD8(parameters)
                || runtimeWithRecordsSupport(parameters.getRuntime()),
            r -> r.assertSuccessWithOutput(EXPECTED_RESULT),
            r -> r.assertFailureWithErrorThatThrows(NoClassDefFoundError.class));
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    assumeTrue(parameters.isDexRuntime() || isCfRuntimeWithNativeRecordSupport());
    R8FullTestBuilder builder =
        testForR8(parameters.getBackend())
            .addInnerClassesAndStrippedOuter(getClass())
            .setMinApi(parameters)
            .addInliningAnnotations()
            .addKeepMainRule(TestClass.class);
    if (parameters.isCfRuntime()) {
      builder
          .addLibraryProvider(JdkClassFileProvider.fromSystemJdk())
          .run(parameters.getRuntime(), TestClass.class)
          .assertSuccessWithOutput(EXPECTED_RESULT);
      return;
    }
    builder.run(parameters.getRuntime(), TestClass.class).assertSuccessWithOutput(EXPECTED_RESULT);
  }

  @Test
  public void testR8DontShrinkDontObfuscate() throws Exception {
    parameters.assumeR8TestParameters();
    assumeTrue(parameters.isDexRuntime() || isCfRuntimeWithNativeRecordSupport());
    R8FullTestBuilder builder =
        testForR8(parameters.getBackend())
            .addInnerClassesAndStrippedOuter(getClass())
            .setMinApi(parameters)
            .addDontShrink()
            .addDontObfuscate()
            .addInliningAnnotations()
            .addKeepMainRule(TestClass.class);
    if (parameters.isCfRuntime()) {
      builder
          .addLibraryProvider(JdkClassFileProvider.fromSystemJdk())
          .run(parameters.getRuntime(), TestClass.class)
          .assertSuccessWithOutput(EXPECTED_RESULT);
      return;
    }
    builder.run(parameters.getRuntime(), TestClass.class).assertSuccessWithOutput(EXPECTED_RESULT);
  }

  public static class TestClass {

    record Person(String name, int age) {}

    record Food(String name, int sweetness, int saltyness) {}

    public static void main(String[] args) {
      Person janeDoe = new Person("Jane Doe", 42);
      System.out.println(equals(janeDoe.hashCode(), janeDoe.hashCode()));
      System.out.println(equalsHash(janeDoe, janeDoe));
      Person otherJaneDoe = new Person("Jane Doe", 42);
      System.out.println(equals(janeDoe.hashCode(), otherJaneDoe.hashCode()));
      System.out.println(equalsHash(janeDoe, otherJaneDoe));
      Person johnDoe = new Person("John Doe", 45);
      System.out.println(equals(janeDoe.hashCode(), johnDoe.hashCode()));
      System.out.println(equalsHash(janeDoe, johnDoe));

      Food bread = new Food("bread", 1, 100);
      System.out.println(equals(bread.hashCode(), bread.hashCode()));
      System.out.println(equalsHash(bread, bread));
      Food biscuit = new Food("biscuit", 50, 20);
      System.out.println(equals(bread.hashCode(), biscuit.hashCode()));
      System.out.println(equalsHash(bread, biscuit));
    }

    @NeverInline
    public static boolean equals(int i1, int i2) {
      return System.currentTimeMillis() > 0 ? i1 == i2 : false;
    }

    @NeverInline
    public static boolean equalsHash(Record r1, Record r2) {
      return System.currentTimeMillis() > 0 ? equals(r1.hashCode(), r2.hashCode()) : false;
    }
  }
}
