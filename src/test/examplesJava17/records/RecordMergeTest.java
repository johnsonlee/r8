// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package records;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticType;
import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentIf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.D8TestBuilder;
import com.android.tools.r8.D8TestCompileResult;
import com.android.tools.r8.GlobalSyntheticsTestingConsumer;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.errors.DuplicateTypesDiagnostic;
import com.android.tools.r8.errors.MissingGlobalSyntheticsConsumerDiagnostic;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.nio.file.Path;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class RecordMergeTest extends TestBase {

  private static final String EXPECTED_RESULT_1 =
      StringUtils.lines("BobX", "43", "FelixX", "-1", "print", "Bob43", "extra");

  private static final String EXPECTED_RESULT_2 =
      StringUtils.lines(
          "Jane Doe", "42", "true", "true", "true", "false", "false", "false", "false");

  private final TestParameters parameters;

  public RecordMergeTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  @Test
  public void testNoGlobalSyntheticsConsumer() throws Exception {
    D8TestBuilder builder =
        testForD8(parameters.getBackend())
            .addStrippedOuter(getClass())
            .addProgramClassesAndInnerClasses(RecordWithMembers.class)
            .addClasspathClassesAndInnerClasses(SimpleRecord.class)
            .setMinApi(parameters)
            .setIntermediate(true);
    if (isRecordsFullyDesugaredForD8(parameters)) {
      assertThrows(
          CompilationFailedException.class,
          () ->
              builder.compileWithExpectedDiagnostics(
                  diagnostics ->
                      diagnostics
                          .assertOnlyErrors()
                          .assertErrorsMatch(
                              diagnosticType(MissingGlobalSyntheticsConsumerDiagnostic.class))));
    } else {
      builder.compile();
    }
  }

  @Test
  public void testMergeDesugaredInputs() throws Exception {
    testMergeDesugaredInputsDexPerClass(false);
  }

  @Test
  public void testMergeDesugaredInputsDexPerClass() throws Exception {
    Assume.assumeTrue("CF is already run from the other test", parameters.isDexRuntime());
    testMergeDesugaredInputsDexPerClass(true);
  }

  private void testMergeDesugaredInputsDexPerClass(boolean filePerClass) throws Exception {
    GlobalSyntheticsTestingConsumer globals1 = new GlobalSyntheticsTestingConsumer();
    Path output1 =
        testForD8(parameters.getBackend())
            .addStrippedOuter(getClass())
            .addProgramClassesAndInnerClasses(RecordWithMembers.class)
            .addClasspathClassesAndInnerClasses(SimpleRecord.class)
            .setMinApi(parameters)
            .setIntermediate(true)
            .applyIf(
                filePerClass && !parameters.isCfRuntime(),
                b -> b.setOutputMode(OutputMode.DexFilePerClassFile))
            .apply(b -> b.getBuilder().setGlobalSyntheticsConsumer(globals1))
            .compile()
            .inspect(this::assertDoesNotHaveRecordTag)
            .writeToZip();

    GlobalSyntheticsTestingConsumer globals2 = new GlobalSyntheticsTestingConsumer();
    Path output2 =
        testForD8(parameters.getBackend())
            .addProgramClassesAndInnerClasses(SimpleRecord.class)
            .addClasspathClassesAndInnerClasses(getClass())
            .setMinApi(parameters)
            .setIntermediate(true)
            .applyIf(
                filePerClass && !parameters.isCfRuntime(),
                b -> b.setOutputMode(OutputMode.DexFilePerClassFile))
            .apply(b -> b.getBuilder().setGlobalSyntheticsConsumer(globals2))
            .compile()
            .inspect(this::assertDoesNotHaveRecordTag)
            .writeToZip();

    assertTrue(isRecordsFullyDesugaredForD8(parameters) ^ !globals1.hasGlobals());
    assertTrue(isRecordsFullyDesugaredForD8(parameters) ^ !globals2.hasGlobals());

    D8TestCompileResult result =
        testForD8(parameters.getBackend())
            .addProgramFiles(output1, output2)
            .apply(
                b ->
                    b.getBuilder()
                        .addGlobalSyntheticsResourceProviders(globals1.getProviders())
                        .addGlobalSyntheticsResourceProviders(globals2.getProviders()))
            .setMinApi(parameters)
            .compile()
            .inspect(this::assertHasRecordTag);

    result
        .run(parameters.getRuntime(), RecordWithMembers.class)
        .applyIf(
            isRecordsFullyDesugaredForD8(parameters)
                || runtimeWithRecordsSupport(parameters.getRuntime()),
            r -> r.assertSuccessWithOutput(EXPECTED_RESULT_1),
            r -> r.assertFailureWithErrorThatThrows(NoClassDefFoundError.class));
    result
        .run(parameters.getRuntime(), SimpleRecord.class)
        .applyIf(
            isRecordsFullyDesugaredForD8(parameters)
                || runtimeWithRecordsSupport(parameters.getRuntime()),
            r -> r.assertSuccessWithOutput(EXPECTED_RESULT_2),
            r -> r.assertFailureWithErrorThatThrows(NoClassDefFoundError.class));
  }

  @Test
  public void testMergeDesugaredAndNonDesugaredInputs() throws Exception {
    GlobalSyntheticsTestingConsumer globals1 = new GlobalSyntheticsTestingConsumer();
    Path output1 =
        testForD8(parameters.getBackend())
            .addProgramClassesAndInnerClasses(RecordWithMembers.class)
            .addClasspathClasses(getClass())
            .addClasspathClassesAndInnerClasses(SimpleRecord.class)
            .setMinApi(parameters)
            .setIntermediate(true)
            .apply(b -> b.getBuilder().setGlobalSyntheticsConsumer(globals1))
            .compile()
            .writeToZip();

    D8TestCompileResult result =
        testForD8(parameters.getBackend())
            .addStrippedOuter(getClass())
            .addProgramFiles(output1)
            .apply(
                b -> b.getBuilder().addGlobalSyntheticsResourceProviders(globals1.getProviders()))
            .addProgramClassesAndInnerClasses(SimpleRecord.class)
            .addClasspathClassesAndInnerClasses(getClass())
            .setMinApi(parameters)
            .compile();
    result
        .run(parameters.getRuntime(), RecordWithMembers.class)
        .applyIf(
            isRecordsFullyDesugaredForD8(parameters)
                || runtimeWithRecordsSupport(parameters.getRuntime()),
            r -> r.assertSuccessWithOutput(EXPECTED_RESULT_1),
            r -> r.assertFailureWithErrorThatThrows(NoClassDefFoundError.class));
    result
        .run(parameters.getRuntime(), SimpleRecord.class)
        .applyIf(
            isRecordsFullyDesugaredForD8(parameters)
                || runtimeWithRecordsSupport(parameters.getRuntime()),
            r -> r.assertSuccessWithOutput(EXPECTED_RESULT_2),
            r -> r.assertFailureWithErrorThatThrows(NoClassDefFoundError.class));
  }

  @Test
  public void testMergeNonIntermediates() throws Exception {
    Path output1 =
        testForD8(parameters.getBackend())
            .addStrippedOuter(getClass())
            .addProgramClassesAndInnerClasses(RecordWithMembers.class)
            .addClasspathClassesAndInnerClasses(SimpleRecord.class)
            .setMinApi(parameters)
            .compile()
            .inspect(this::assertHasRecordTag)
            .writeToZip();

    Path output2 =
        testForD8(parameters.getBackend())
            .addProgramClassesAndInnerClasses(SimpleRecord.class)
            .addClasspathClassesAndInnerClasses(getClass())
            .setMinApi(parameters)
            .compile()
            .inspect(this::assertHasRecordTag)
            .writeToZip();

    if (!isRecordsFullyDesugaredForD8(parameters)) {
      D8TestCompileResult result =
          testForD8(parameters.getBackend())
              .addProgramFiles(output1, output2)
              .setMinApi(parameters)
              .compile();
      result
          .run(parameters.getRuntime(), RecordWithMembers.class)
          .applyIf(
              isRecordsFullyDesugaredForD8(parameters)
                  || runtimeWithRecordsSupport(parameters.getRuntime()),
              r -> r.assertSuccessWithOutput(EXPECTED_RESULT_1),
              r -> r.assertFailureWithErrorThatThrows(NoClassDefFoundError.class));
      result
          .run(parameters.getRuntime(), SimpleRecord.class)
          .applyIf(
              isRecordsFullyDesugaredForD8(parameters)
                  || runtimeWithRecordsSupport(parameters.getRuntime()),
              r -> r.assertSuccessWithOutput(EXPECTED_RESULT_2),
              r -> r.assertFailureWithErrorThatThrows(NoClassDefFoundError.class));
    } else {
      assertThrows(
          CompilationFailedException.class,
          () ->
              testForD8(parameters.getBackend())
                  .addProgramFiles(output1, output2)
                  .setMinApi(parameters)
                  .compileWithExpectedDiagnostics(
                      diagnostics ->
                          diagnostics
                              .assertOnlyErrors()
                              .assertErrorsMatch(diagnosticType(DuplicateTypesDiagnostic.class))));
    }
  }

  private void assertHasRecordTag(CodeInspector inspector) {
    // Note: this should be asserting on record tag.
    assertThat(
        inspector.clazz("java.lang.Record"), isPresentIf(isRecordsFullyDesugaredForD8(parameters)));
  }

  private void assertDoesNotHaveRecordTag(CodeInspector inspector) {
    // Note: this should be asserting on record tag.
    assertThat(inspector.clazz("java.lang.Record"), isAbsent());
  }

  public class RecordWithMembers {
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

  public class SimpleRecord {

    record Person(String name, int age) {}

    public static void main(String[] args) {
      Person janeDoe = new Person("Jane Doe", 42);
      System.out.println(janeDoe.name());
      System.out.println(janeDoe.age());

      // Test equals with self.
      System.out.println(janeDoe.equals(janeDoe));

      // Test equals with structurally equals Person.
      Person otherJaneDoe = new Person("Jane Doe", 42);
      System.out.println(janeDoe.equals(otherJaneDoe));
      System.out.println(otherJaneDoe.equals(janeDoe));

      // Test equals with not-structually equals Person.
      Person johnDoe = new Person("John Doe", 42);
      System.out.println(janeDoe.equals(johnDoe));
      System.out.println(johnDoe.equals(janeDoe));

      // Test equals with Object and null.
      System.out.println(janeDoe.equals(new Object()));
      System.out.println(janeDoe.equals(null));
    }
  }
}
