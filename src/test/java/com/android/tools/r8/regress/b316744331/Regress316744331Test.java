// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.regress.b316744331;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.debug.DebugTestBase;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.StringUtils;
import org.apache.harmony.jpda.tests.framework.jdwp.JDWPConstants.Tag;
import org.apache.harmony.jpda.tests.framework.jdwp.Value;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class Regress316744331Test extends DebugTestBase {

  static final String EXPECTED = StringUtils.lines("No null fields");

  static final int ENTRY_LINE = 23;
  static final int IN_STREAM_CHECK_LINE = ENTRY_LINE + 1;
  static final int IN_STREAM_IS_NULL_LINE = ENTRY_LINE + 2;
  static final int OUT_STREAM_CHECK_LINE = ENTRY_LINE + 7;
  static final int OUT_STREAM_IS_NULL_LINE = ENTRY_LINE + 8;
  static final int NORMAL_EXIT_LINE = ENTRY_LINE + 11;

  static final Value NULL_VALUE = Value.createObjectValue(Tag.OBJECT_TAG, 0);

  private final TestParameters parameters;
  private final MethodReference fooMethod;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withDefaultCfRuntime()
        .withDefaultDexRuntime()
        .withAllApiLevels()
        .build();
  }

  public Regress316744331Test(TestParameters parameters) {
    this.parameters = parameters;
    try {
      this.fooMethod = Reference.methodFromMethod(Regress316744331TestClass.class.getMethod("foo"));
    } catch (NoSuchMethodException e) {
      throw new RuntimeException(e);
    }
  }

  private TestBuilder<? extends SingleTestRunResult<?>, ?> getTestBuilder() {
    return testForRuntime(parameters)
        .addClasspathClasses(Regress316744331TestClass.class)
        .addProgramClasses(Regress316744331TestClass.class);
  }

  @Test
  public void testReference() throws Exception {
    getTestBuilder()
        .run(parameters.getRuntime(), Regress316744331TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testModifyInStreamField() throws Throwable {
    runDebugTest(
        getTestBuilder().debugConfig(parameters.getRuntime()),
        Regress316744331TestClass.class,
        breakpoint(fooMethod, ENTRY_LINE),
        run(),
        checkLine(ENTRY_LINE),
        inspect(t -> assertEquals(NULL_VALUE, t.getFieldOnThis("m_instream", null))),
        stepOver(),
        checkLine(IN_STREAM_CHECK_LINE),
        inspect(t -> assertNotEquals(NULL_VALUE, t.getFieldOnThis("m_instream", null))),
        inspect(t -> t.setFieldOnThis("m_instream", null, NULL_VALUE)),
        // Install a break point on the possible exits.
        breakpoint(fooMethod, IN_STREAM_IS_NULL_LINE),
        breakpoint(fooMethod, NORMAL_EXIT_LINE),
        run(),
        // TODO(b/316744331): D8 incorrectly optimizing out the code after the null check.
        checkLine(parameters.isCfRuntime() ? IN_STREAM_IS_NULL_LINE : NORMAL_EXIT_LINE),
        run());
  }

  @Test
  public void testModifyOutStreamField() throws Throwable {
    runDebugTest(
        getTestBuilder().debugConfig(parameters.getRuntime()),
        Regress316744331TestClass.class,
        breakpoint(fooMethod, OUT_STREAM_CHECK_LINE),
        run(),
        checkLine(OUT_STREAM_CHECK_LINE),
        inspect(t -> assertNotEquals(NULL_VALUE, t.getFieldOnThis("m_outstream", null))),
        inspect(t -> t.setFieldOnThis("m_outstream", null, NULL_VALUE)),
        // Install a break point on the possible exits.
        breakpoint(fooMethod, OUT_STREAM_IS_NULL_LINE),
        breakpoint(fooMethod, NORMAL_EXIT_LINE),
        run(),
        // TODO(b/316744331): D8 incorrectly optimizing out the code after the null check.
        checkLine(parameters.isCfRuntime() ? OUT_STREAM_IS_NULL_LINE : NORMAL_EXIT_LINE),
        run());
  }
}
