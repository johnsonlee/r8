// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package records;

import com.android.tools.r8.JdkClassFileProvider;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.utils.StringUtils;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class EmptyRecordAnnotationTest extends TestBase {

  private static final String EXPECTED_RESULT_NATIVE_OR_PARTIALLY_DESUGARED_RECORD =
      StringUtils.lines("class java.lang.Record", "class records.EmptyRecordAnnotationTest$Empty");
  private static final String EXPECTED_RESULT_DESUGARED_RECORD =
      StringUtils.lines(
          "class com.android.tools.r8.RecordTag", "class records.EmptyRecordAnnotationTest$Empty");

  private final TestParameters parameters;

  public EmptyRecordAnnotationTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withCfRuntimesStartingFromIncluding(CfVm.JDK17)
        .withDexRuntimes()
        .withAllApiLevelsAlsoForCf()
        .build();
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addInnerClassesAndStrippedOuter(getClass())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_RESULT_NATIVE_OR_PARTIALLY_DESUGARED_RECORD);
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8(parameters.getBackend())
        .addInnerClassesAndStrippedOuter(getClass())
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        .applyIf(
            isRecordsFullyDesugaredForD8(parameters),
            r -> r.assertSuccessWithOutput(EXPECTED_RESULT_DESUGARED_RECORD),
            r -> r.assertSuccessWithOutput(EXPECTED_RESULT_NATIVE_OR_PARTIALLY_DESUGARED_RECORD));
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    testForR8(parameters.getBackend())
        .addLibraryProvider(JdkClassFileProvider.fromSystemJdk())
        .addInnerClassesAndStrippedOuter(getClass())
        .setMinApi(parameters)
        .addKeepRules("-keep class records.EmptyRecordAnnotationTest$TestClass { *; }")
        .addKeepRules("-keepattributes *Annotation*")
        .addKeepRules("-keep class records.EmptyRecordAnnotationTest$Empty")
        .addKeepMainRule(TestClass.class)
        // This is used to avoid renaming com.android.tools.r8.RecordTag.
        .applyIf(
            isRecordsFullyDesugaredForR8(parameters),
            b -> b.addKeepRules("-keep class java.lang.Record"))
        .compile()
        .applyIf(parameters.isCfRuntime(), r -> r.inspect(RecordTestUtils::assertRecordsAreRecords))
        .run(parameters.getRuntime(), TestClass.class)
        .applyIf(
            isRecordsFullyDesugaredForR8(parameters),
            r -> r.assertSuccessWithOutput(EXPECTED_RESULT_DESUGARED_RECORD),
            r -> r.assertSuccessWithOutput(EXPECTED_RESULT_NATIVE_OR_PARTIALLY_DESUGARED_RECORD));
  }

  record Empty() {}

  @Retention(RetentionPolicy.RUNTIME)
  @interface ClassAnnotation {
    Class<? extends Record> theClass();
  }

  public class TestClass {

    @ClassAnnotation(theClass = Record.class)
    public static void annotatedMethod1() {}

    @ClassAnnotation(theClass = Empty.class)
    public static void annotatedMethod2() {}

    public static void main(String[] args) throws Exception {
      Class<?> annotatedMethod1Content =
          TestClass.class
              .getDeclaredMethod("annotatedMethod1")
              .getAnnotation(ClassAnnotation.class)
              .theClass();
      System.out.println(annotatedMethod1Content);
      Class<?> annotatedMethod2Content =
          TestClass.class
              .getDeclaredMethod("annotatedMethod2")
              .getAnnotation(ClassAnnotation.class)
              .theClass();
      System.out.println(annotatedMethod2Content);
    }
  }
}
