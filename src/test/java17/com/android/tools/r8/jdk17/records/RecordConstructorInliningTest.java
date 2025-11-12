// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.jdk17.records;

import static com.android.tools.r8.jdk17.records.RecordTestUtils.assertRecordsAreRecords;
import static com.android.tools.r8.utils.codeinspector.Matchers.isFinal;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.JdkClassFileProvider;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RecordConstructorInliningTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withCfRuntimesStartingFromIncluding(CfVm.JDK17)
        .withDexRuntimesAndAllApiLevels()
        .withPartialCompilation()
        .build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters)
        .addInnerClassesAndStrippedOuter(getClass())
        .addKeepClassAndMembersRules(Main.class)
        .applyIf(
            parameters.isCfRuntime(),
            b -> b.addLibraryProvider(JdkClassFileProvider.fromSystemJdk()))
        .compile()
        .inspectWithOptions(
            inspector -> {
              if (parameters.isCfRuntime()) {
                assertRecordsAreRecords(inspector);
              } else {
                ClassSubject personRecord = inspector.clazz(Person.class);
                assertThat(personRecord, isPresent());

                FieldSubject nameField = personRecord.uniqueFieldWithOriginalName("name");
                assertThat(nameField, isPresent());
                if (personRecord.getSuperType().getTypeName().equals("java.lang.Record")) {
                  assertThat(nameField, isFinal());
                }
              }
            },
            options -> options.testing.disableRecordApplicationReaderMap = true)
        .run(parameters.getRuntime(), Main.class, "Jane Doe")
        .assertSuccessWithOutputLines("Jane Doe");
  }

  static class Main {

    public static void main(String[] args) {
      printName(Person.create(args[0]));
    }

    private static void printName(Person person) {
      System.out.println(person.name);
    }
  }

  record Person(String name) {

    static Person create(String name) {
      return new Person(name);
    }
  }
}
