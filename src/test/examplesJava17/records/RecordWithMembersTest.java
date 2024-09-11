// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package records;

import com.android.tools.r8.JdkClassFileProvider;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class RecordWithMembersTest extends TestBase {

  private static final String EXPECTED_RESULT =
      StringUtils.lines("BobX", "43", "FelixX", "-1", "print", "Bob43", "extra");

  private final TestParameters parameters;

  public RecordWithMembersTest(TestParameters parameters) {
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
        .run(parameters.getRuntime(), RecordWithMembers.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  @Test
  public void testD8() throws Exception {
    testForD8(parameters.getBackend())
        .addInnerClassesAndStrippedOuter(getClass())
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), RecordWithMembers.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    R8FullTestBuilder builder =
        testForR8(parameters.getBackend())
            .addInnerClassesAndStrippedOuter(getClass())
            .setMinApi(parameters)
            .addKeepMainRule(RecordWithMembers.class);
    if (parameters.isCfRuntime()) {
      builder
          .addLibraryProvider(JdkClassFileProvider.fromSystemJdk())
          .compile()
          .inspect(RecordTestUtils::assertRecordsAreRecords)
          .run(parameters.getRuntime(), RecordWithMembers.class)
          .assertSuccessWithOutput(EXPECTED_RESULT);
      return;
    }
    builder
        .run(parameters.getRuntime(), RecordWithMembers.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  record PersonWithConstructors(String name, int age) {

    public PersonWithConstructors(String name, int age) {
      this.name = name + "X";
      this.age = age;
    }

    public PersonWithConstructors(String name) {
      this(name, -1);
    }
  }

  record PersonWithMethods(String name, int age) {
    public static void staticPrint() {
      System.out.println("print");
    }

    @Override
    public String toString() {
      return name + age;
    }
  }

  record PersonWithFields(String name, int age) {

    // Extra instance fields are not allowed on records.
    public static String globalName;
  }

  public class RecordWithMembers {

    public static void main(String[] args) {
      personWithConstructorTest();
      personWithMethodsTest();
      personWithFieldsTest();
    }

    private static void personWithConstructorTest() {
      PersonWithConstructors bob = new PersonWithConstructors("Bob", 43);
      System.out.println(bob.name());
      System.out.println(bob.age());
      PersonWithConstructors felix = new PersonWithConstructors("Felix");
      System.out.println(felix.name());
      System.out.println(felix.age());
    }

    private static void personWithMethodsTest() {
      PersonWithMethods.staticPrint();
      PersonWithMethods bob = new PersonWithMethods("Bob", 43);
      System.out.println(bob.toString());
    }

    private static void personWithFieldsTest() {
      PersonWithFields.globalName = "extra";
      System.out.println(PersonWithFields.globalName);
    }
  }
}
