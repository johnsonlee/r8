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
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class RecordReflectionTest extends TestBase {

  private static final String EXPECTED_RESULT =
      StringUtils.lines(
          "true",
          "[]",
          "true",
          "[java.lang.String name, int age]",
          "true",
          "[java.lang.CharSequence name, int age]",
          "[S]",
          "false",
          "null");

  private final TestParameters parameters;

  public RecordReflectionTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withCfRuntimesStartingFromIncluding(CfVm.JDK17).build();
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addInnerClassesAndStrippedOuter(getClass())
        .run(parameters.getRuntime(), RecordReflection.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  @Test
  public void testR8Cf() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClassesAndStrippedOuter(getClass())
        .setMinApi(parameters)
        .addKeepMainRule(RecordReflection.class)
        .addKeepAllAttributes()
        .addKeepRules("-keep class * extends java.lang.Record { private final <fields>; }")
        .addLibraryProvider(JdkClassFileProvider.fromSystemJdk())
        .compile()
        .inspect(RecordTestUtils::assertRecordsAreRecords)
        .enableJVMPreview()
        .run(parameters.getRuntime(), RecordReflection.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  record Empty() {}

  record Person(String name, int age) {}

  record PersonGeneric<S extends CharSequence>(S name, int age) {}

  public class RecordReflection {

    public static void main(String[] args) {
      System.out.println(Empty.class.isRecord());
      System.out.println(Arrays.toString(Empty.class.getRecordComponents()));
      System.out.println(Person.class.isRecord());
      System.out.println(Arrays.toString(Person.class.getRecordComponents()));
      System.out.println(PersonGeneric.class.isRecord());
      System.out.println(Arrays.toString(PersonGeneric.class.getRecordComponents()));
      System.out.println(Arrays.toString(PersonGeneric.class.getTypeParameters()));
      System.out.println(Object.class.isRecord());
      System.out.println(Arrays.toString(Object.class.getRecordComponents()));
    }
  }
}
