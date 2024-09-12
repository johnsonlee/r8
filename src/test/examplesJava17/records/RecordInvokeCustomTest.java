// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package records;

import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.desugar.LibraryFilesHelper;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import records.RecordInvokeCustom.Empty;
import records.RecordInvokeCustom.Person;

@RunWith(Parameterized.class)
public class RecordInvokeCustomTest extends TestBase {

  private static final String EXPECTED_RESULT =
      StringUtils.lines(
          "%s[]",
          "true",
          "true",
          "true",
          "true",
          "true",
          "false",
          "true",
          "true",
          "false",
          "false",
          "%s[%s=Jane Doe, %s=42]");
  private static final String EXPECTED_RESULT_D8 =
      String.format(EXPECTED_RESULT, "Empty", "Person", "name", "age");
  private static final String EXPECTED_RESULT_R8 =
      String.format(EXPECTED_RESULT, "a", "b", "a", "b");

  private final TestParameters parameters;

  public RecordInvokeCustomTest(TestParameters parameters) {
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
        .run(parameters.getRuntime(), RecordInvokeCustom.class)
        .assertSuccessWithOutput(EXPECTED_RESULT_D8);
  }

  @Test
  public void testD8() throws Exception {
    testForD8(parameters.getBackend())
        .addInnerClassesAndStrippedOuter(getClass())
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), RecordInvokeCustom.class)
        .assertSuccessWithOutput(EXPECTED_RESULT_D8);
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    R8FullTestBuilder builder =
        testForR8(parameters.getBackend())
            .addInnerClassesAndStrippedOuter(getClass())
            .setMinApi(parameters)
            .addKeepMainRule(RecordInvokeCustom.class);
    if (parameters.isCfRuntime()) {
      builder
          .addLibraryFiles(LibraryFilesHelper.getJdk15LibraryFiles(temp))
          .compile()
          .inspect(RecordTestUtils::assertRecordsAreRecords)
          .run(parameters.getRuntime(), RecordInvokeCustom.class)
          .assertSuccessWithOutput(EXPECTED_RESULT_R8);
      return;
    }
    builder
        .run(parameters.getRuntime(), RecordInvokeCustom.class)
        .assertSuccessWithOutput(EXPECTED_RESULT_R8);
  }

  record Empty() {}

  record Person(String name, int age) {}

  public class RecordInvokeCustom {

    public static void main(String[] args) {
      emptyTest();
      equalityTest();
      toStringTest();
    }

    private static void emptyTest() {
      Empty empty1 = new Empty();
      Empty empty2 = new Empty();
      System.out.println(empty1.toString());
      System.out.println(empty1.equals(empty2));
      System.out.println(empty1.hashCode() == empty2.hashCode());
      System.out.println(empty1.toString().equals(empty2.toString()));
    }

    private static void toStringTest() {
      Person janeDoe = new Person("Jane Doe", 42);
      System.out.println(janeDoe.toString());
    }

    private static void equalityTest() {
      Person jane1 = new Person("Jane Doe", 42);
      Person jane2 = new Person("Jane Doe", 42);
      String nonIdenticalString = "Jane " + (System.currentTimeMillis() > 0 ? "Doe" : "Zan");
      Person jane3 = new Person(nonIdenticalString, 42);
      Person bob = new Person("Bob", 42);
      Person youngJane = new Person("Jane Doe", 22);
      System.out.println(jane1.equals(jane2));
      System.out.println(jane1.toString().equals(jane2.toString()));
      System.out.println(nonIdenticalString == "Jane Doe"); // false.
      System.out.println(nonIdenticalString.equals("Jane Doe")); // true.
      System.out.println(jane1.equals(jane3));
      System.out.println(jane1.equals(bob));
      System.out.println(jane1.equals(youngJane));
    }
  }
}
