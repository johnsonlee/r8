// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package nesthostexample;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.D8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class NestOnProgramOnClassPathTest extends TestBase {

  public NestOnProgramOnClassPathTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  @Test
  public void testD8MethodBridgesPresent() throws Exception {
    parameters.assumeDexRuntime();
    Class<?> nestHost = BasicNestHostWithInnerClassMethods.class;
    // 1 inner class.
    D8TestCompileResult singleInner =
        compileClassesWithD8ProgramClasses(
            nestHost, BasicNestHostWithInnerClassMethods.BasicNestedClass.class);
    singleInner.inspect(inspector -> assertThisNumberOfBridges(inspector, 2));
    // Outer class.
    D8TestCompileResult host = compileClassesWithD8ProgramClasses(nestHost, nestHost);
    host.inspect(inspector -> assertThisNumberOfBridges(inspector, 2));
    // 2 inner classes.
    D8TestCompileResult multipleInner =
        compileClassesWithD8ProgramClasses(
            NestHostExample.class,
            NestHostExample.StaticNestMemberInner.class,
            NestHostExample.StaticNestMemberInner.StaticNestMemberInnerInner.class);
    multipleInner.inspect(inspector -> assertThisNumberOfBridges(inspector, 5));
  }

  @Test
  public void testD8ConstructorBridgesPresent() throws Exception {
    parameters.assumeDexRuntime();
    Class<?> nestHost = BasicNestHostWithInnerClassConstructors.class;
    D8TestCompileResult inner =
        compileClassesWithD8ProgramClasses(
            nestHost, BasicNestHostWithInnerClassConstructors.BasicNestedClass.class);
    inner.inspect(
        inspector -> {
          assertThisNumberOfBridges(inspector, 3);
          assertNestConstructor(inspector);
        });
    D8TestCompileResult host = compileClassesWithD8ProgramClasses(nestHost, nestHost);
    host.inspect(
        inspector -> {
          assertThisNumberOfBridges(inspector, 1);
          assertNestConstructor(inspector);
        });
  }

  @Test
  public void testD8ConstructorNestMergeCorrect() throws Exception {
    // Multiple Nest Constructor classes have to be merged here.
    parameters.assumeDexRuntime();
    Class<?> nestHost = BasicNestHostWithInnerClassConstructors.class;
    D8TestCompileResult inner =
        compileClassesWithD8ProgramClasses(
            nestHost, BasicNestHostWithInnerClassConstructors.BasicNestedClass.class);
    D8TestCompileResult host = compileClassesWithD8ProgramClasses(nestHost, nestHost);
    testForD8()
        .addProgramFiles(inner.writeToZip(), host.writeToZip())
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), nestHost)
        .assertSuccessWithOutputLines(BasicNestHostWithInnerClassConstructors.getExpectedResult());
  }

  private D8TestCompileResult compileClassesWithD8ProgramClasses(
      Class<?> nestHost, Class<?>... classes) throws Exception {
    return testForD8()
        .setMinApi(parameters)
        .addProgramClasses(classes)
        .addClasspathClasses(nestHost.getNestMembers())
        .compile();
  }

  private static void assertNestConstructor(CodeInspector inspector) {
    assertTrue(inspector.allClasses().stream().anyMatch(FoundClassSubject::isSynthetic));
  }

  private static void assertThisNumberOfBridges(CodeInspector inspector, int numBridges) {
    for (FoundClassSubject clazz : inspector.allClasses()) {
      if (!clazz.isSynthetic()) {
        assertEquals(numBridges, clazz.allMethods(FoundMethodSubject::isSynthetic).size());
      }
    }
  }
}
