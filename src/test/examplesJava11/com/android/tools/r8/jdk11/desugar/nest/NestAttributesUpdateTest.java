// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.jdk11.desugar.nest;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertSame;
import static junit.framework.TestCase.assertTrue;

import com.android.tools.r8.Jdk9TestUtils;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ThrowableConsumer;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.NestMemberClassAttribute;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class NestAttributesUpdateTest extends TestBase {

  private static final Class<?> MERGING_OUTER_CLASS = BasicNestHostClassMerging.class;
  private static final Class<?> PRUNING_OUTER_CLASS = BasicNestHostTreePruning.class;
  private static final String MERGING_EXPECTED_RESULT = StringUtils.lines("OuterMiddleInner");
  private static final String PRUNING_EXPECTED_RESULT = StringUtils.lines("NotPruned");

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withCfRuntimesStartingFromIncluding(CfVm.JDK11).build();
  }

  @Test
  public void testClassMergingNestMemberRemoval() throws Exception {
    testNestAttributesCorrect(
        MERGING_OUTER_CLASS,
        MERGING_OUTER_CLASS,
        MERGING_EXPECTED_RESULT,
        3,
        ThrowableConsumer.empty());
  }

  @Test
  public void testClassMergingNestHostRemoval() throws Exception {
    testNestAttributesCorrect(
        BasicNestHostClassMerging.MiddleOuter.class,
        MERGING_OUTER_CLASS,
        MERGING_EXPECTED_RESULT,
        2,
        builder ->
            builder.addOptionsModification(
                internalOptions -> {
                  // The test makes an invoke to StringConcatFactory which is not known to DEX and
                  // we therefore fail to merge the classes.
                  internalOptions.apiModelingOptions().disableApiCallerIdentification();
                }));
  }

  @Test
  public void testTreePruningNestMemberRemoval() throws Exception {
    testNestAttributesCorrect(
        PRUNING_OUTER_CLASS,
        PRUNING_OUTER_CLASS,
        PRUNING_EXPECTED_RESULT,
        2,
        ThrowableConsumer.empty());
  }

  @Test
  public void testTreePruningNestHostRemoval() throws Exception {
    testNestAttributesCorrect(
        BasicNestHostTreePruning.Pruned.class,
        PRUNING_OUTER_CLASS,
        PRUNING_EXPECTED_RESULT,
        1,
        ThrowableConsumer.empty());
  }

  public void testNestAttributesCorrect(
      Class<?> mainClass,
      Class<?> outerNestClass,
      String expectedResult,
      int expectedNumClassesLeft,
      ThrowableConsumer<R8FullTestBuilder> testBuilderConsumer)
      throws Exception {
    testNestAttributesCorrect(
        mainClass,
        outerNestClass,
        expectedResult,
        true,
        expectedNumClassesLeft,
        testBuilderConsumer);
    testNestAttributesCorrect(
        mainClass,
        outerNestClass,
        expectedResult,
        false,
        expectedNumClassesLeft,
        testBuilderConsumer);
  }

  public void testNestAttributesCorrect(
      Class<?> mainClass,
      Class<?> outerNestClass,
      String expectedResult,
      boolean minification,
      int expectedNumClassesLeft,
      ThrowableConsumer<R8FullTestBuilder> testBuilderConsumer)
      throws Exception {
    testForR8(parameters.getBackend())
        .addKeepMainRule(mainClass)
        .addDontObfuscateUnless(minification)
        .addOptionsModification(
            options -> {
              // Disable optimizations else additional classes are removed since they become unused.
              options.enableClassInlining = false;
            })
        .addProgramClassesAndInnerClasses(outerNestClass)
        .applyIf(parameters.isCfRuntime(), Jdk9TestUtils.addJdk9LibraryFiles(temp))
        .addKeepPackageNamesRule(getClass().getPackageName())
        .addInliningAnnotations()
        .apply(testBuilderConsumer)
        .compile()
        .inspect(
            inspector -> {
              assertEquals(expectedNumClassesLeft, inspector.allClasses().size());
              assertNestAttributesCorrect(inspector);
            })
        .run(parameters.getRuntime(), mainClass)
        .assertSuccessWithOutput(expectedResult);
  }

  public static void assertNestAttributesCorrect(CodeInspector inspector) {
    assertFalse(inspector.allClasses().isEmpty());
    for (FoundClassSubject classSubject : inspector.allClasses()) {
      DexClass clazz = classSubject.getDexProgramClass();
      if (clazz.isInANest()) {
        if (clazz.isNestHost()) {
          // All members are present with the clazz as host
          for (NestMemberClassAttribute attr : clazz.getNestMembersClassAttributes()) {
            ClassSubject inner = inspector.clazz(attr.getNestMember().toSourceString());
            assertNotNull(
                "The nest member "
                    + attr.getNestMember().toSourceString()
                    + " of "
                    + clazz.type.toSourceString()
                    + " is missing",
                inner.getDexProgramClass());
            assertSame(inner.getDexProgramClass().getNestHost(), clazz.type);
          }
        } else {
          // Nest host is present and with the clazz as member
          ClassSubject host = inspector.clazz(clazz.getNestHost().toSourceString());
          assertNotNull(
              "The nest host "
                  + clazz.getNestHost().toSourceString()
                  + " of "
                  + clazz.type.toSourceString()
                  + " is missing",
              host.getDexProgramClass());
          assertTrue(
              host.getDexProgramClass().getNestMembersClassAttributes().stream()
                  .anyMatch(attr -> attr.getNestMember() == clazz.type));
        }
      }
    }
  }
}
