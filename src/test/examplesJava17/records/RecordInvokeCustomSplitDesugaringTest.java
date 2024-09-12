// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package records;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static com.android.tools.r8.DiagnosticsMatcher.diagnosticType;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.errors.DuplicateTypeInProgramAndLibraryDiagnostic;
import com.android.tools.r8.errors.DuplicateTypesDiagnostic;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringDiagnostic;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.ZipUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import records.RecordInvokeCustom.Empty;
import records.RecordInvokeCustom.Person;

@RunWith(Parameterized.class)
public class RecordInvokeCustomSplitDesugaringTest extends TestBase {

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
          "%s[name=Jane Doe, age=42]");
  private static final String EXPECTED_RESULT_D8 =
      String.format(EXPECTED_RESULT, "Empty", "Person");

  private final TestParameters parameters;

  public RecordInvokeCustomSplitDesugaringTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimesAndAllApiLevels().build();
  }

  @Test
  public void testD8() throws Exception {
    Path desugared =
        testForD8(Backend.CF)
            .addInnerClassesAndStrippedOuter(getClass())
            .setMinApi(parameters)
            .compile()
            .writeToZip();
    testForD8(parameters.getBackend())
        .addProgramFiles(desugared)
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), RecordInvokeCustom.class)
        .assertSuccessWithOutput(EXPECTED_RESULT_D8);
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    Path desugared =
        testForD8(Backend.CF)
            .addInnerClassesAndStrippedOuter(getClass())
            .setMinApi(parameters)
            .compile()
            .writeToZip();
    if (isRecordsFullyDesugaredForR8(parameters)) {
      assertTrue(ZipUtils.containsEntry(desugared, "com/android/tools/r8/RecordTag.class"));
    } else {
      assertFalse(ZipUtils.containsEntry(desugared, "com/android/tools/r8/RecordTag.class"));
    }
    String[] minifiedNames = {null, null};
    testForR8(parameters.getBackend())
        .addProgramFiles(desugared)
        .setMinApi(parameters)
        .addKeepMainRule(RecordInvokeCustom.class)
        .allowDiagnosticMessages()
        .compileWithExpectedDiagnostics(
            // Class com.android.tools.r8.RecordTag in desugared input is seen as java.lang.Record
            // when reading causing the duplicate class. From Android V the issue is solved by
            // partial desugaring.
            diagnostics -> {
              if (parameters.getApiLevel().isEqualTo(AndroidApiLevel.U)) {
                diagnostics
                    .assertNoErrors()
                    .assertInfosMatch(
                        allOf(
                            diagnosticType(DuplicateTypesDiagnostic.class),
                            diagnosticType(DuplicateTypeInProgramAndLibraryDiagnostic.class),
                            diagnosticMessage(containsString("java.lang.Record"))))
                    .assertWarningsMatch(
                        allOf(
                            diagnosticType(StringDiagnostic.class),
                            diagnosticMessage(containsString("java.lang.Record"))));
              } else {
                diagnostics.assertNoMessages();
              }
            })
        .inspect(
            i -> {
              minifiedNames[0] =
                  extractSimpleFinalName(i, "records.RecordInvokeCustomSplitDesugaringTest$Empty");
              minifiedNames[1] =
                  extractSimpleFinalName(i, "records.RecordInvokeCustomSplitDesugaringTest$Person");
            })
        .run(parameters.getRuntime(), RecordInvokeCustom.class)
        .assertSuccessWithOutput(
            String.format(EXPECTED_RESULT, minifiedNames[0], minifiedNames[1]));
  }

  private static String extractSimpleFinalName(CodeInspector i, String name) {
    String finalName = i.clazz(name).getFinalName();
    return finalName.split("\\.")[1];
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
