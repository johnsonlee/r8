// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.reflection;

import static com.android.tools.r8.utils.codeinspector.Matchers.isFinal;
import static com.android.tools.r8.utils.codeinspector.Matchers.isInterface;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.shaking.reflection.MockitoTest.Helpers.ShouldNotBeMergedImpl;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/** Tests for MockitoStub.mock() can MockitoStub.spy(). */
@RunWith(Parameterized.class)
public class MockitoTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultRuntimes().withMaximumApiLevel().build();
  }

  private static final List<String> EXPECTED_OUTPUT =
      Arrays.asList("A", "B", "C", "D", "E", "did thing");

  public static class MockitoStub {
    public static <T> T mock(Class<T> classToMock) {
      return null;
    }

    public static <T> T mock(String name, T... reified) {
      return null;
    }

    public static <T> T spy(Class<T> classToMock) {
      return null;
    }

    public static <T> T spy(T... reified) {
      return null;
    }

    public static <T> T spy(T classToMock) {
      return null;
    }
  }

  public static class Helpers {
    public static class A {
      @Override
      public String toString() {
        return "A";
      }
    }

    public static class B {
      @Override
      public String toString() {
        return "B";
      }
    }

    public static class C {
      @Override
      public String toString() {
        return "C";
      }
    }

    public static class D {
      @Override
      public String toString() {
        return "D";
      }
    }

    public static class E {
      @Override
      public String toString() {
        return "E";
      }
    }

    public interface ShouldNotBeMerged {
      void doThing();
    }

    public static class ShouldNotBeMergedImpl implements ShouldNotBeMerged {
      @Override
      public void doThing() {
        System.out.println("did thing");
      }
    }
  }

  public static class TestMain {

    @NeverInline
    private static void mock1() {
      MockitoStub.mock(Helpers.A.class);
      System.out.println(new Helpers.A());
    }

    @NeverInline
    private static void mock2() {
      Helpers.B b = MockitoStub.mock("");
      if (b == null) {
        System.out.println(new Helpers.B());
      }
    }

    @NeverInline
    private static void spy1() {
      MockitoStub.spy(Helpers.C.class);
      System.out.println(new Helpers.C());
    }

    @NeverInline
    private static void spy2() {
      Helpers.D d = MockitoStub.spy();
      if (d == null) {
        System.out.println(new Helpers.D());
      }
    }

    @NeverInline
    private static void spy3() {
      MockitoStub.spy(new Helpers.E());
      System.out.println(new Helpers.E());
    }

    @NeverInline
    private static void mockInterface() {
      Helpers.ShouldNotBeMerged iface = MockitoStub.mock(Helpers.ShouldNotBeMerged.class);
      if (iface == null) {
        new ShouldNotBeMergedImpl().doThing();
      }
    }

    public static void main(String[] args) {
      // Use different methods to ensure Enqueuer.traceInvokeStatic() triggers for each one.
      mock1();
      mock2();
      spy1();
      spy2();
      spy3();
      mockInterface();
    }
  }

  private static final String MOCKITO_DESCRIPTOR = "Lorg/mockito/Mockito;";

  private static byte[] rewriteTestMain() throws IOException {
    return transformer(TestMain.class)
        .replaceClassDescriptorInMethodInstructions(
            descriptor(MockitoStub.class), MOCKITO_DESCRIPTOR)
        .transform();
  }

  private static byte[] rewriteMockito() throws IOException {
    return transformer(MockitoStub.class).setClassDescriptor(MOCKITO_DESCRIPTOR).transform();
  }

  @Test
  public void testRuntime() throws Exception {
    byte[] mockitoClassBytes = rewriteMockito();
    testForRuntime(parameters)
        .addProgramClassesAndInnerClasses(Helpers.class)
        .addProgramClassFileData(rewriteTestMain())
        .addClasspathClassFileData(mockitoClassBytes)
        .addRunClasspathFiles(buildOnDexRuntime(parameters, mockitoClassBytes))
        .run(parameters.getRuntime(), TestMain.class)
        .assertSuccessWithOutputLines(EXPECTED_OUTPUT);
  }

  @Test
  public void testR8() throws Exception {
    byte[] mockitoClassBytes = rewriteMockito();
    testForR8(parameters.getBackend())
        .setMinApi(parameters)
        .addProgramClassesAndInnerClasses(Helpers.class)
        .addProgramClassFileData(rewriteTestMain())
        .addClasspathClassFileData(mockitoClassBytes)
        .enableInliningAnnotations()
        .addKeepMainRule(TestMain.class)
        .compile()
        .inspect(
            inspector -> {
              assertThat(inspector.clazz(Helpers.ShouldNotBeMerged.class), isInterface());
              inspector.forAllClasses(
                  clazz -> {
                    String className = clazz.getOriginalTypeName();
                    if (!className.endsWith("TestMain") && !className.endsWith("Impl")) {
                      assertThat(clazz.getOriginalTypeName(), clazz, not(isFinal()));
                    }
                  });
            })
        .addRunClasspathClassFileData(mockitoClassBytes)
        .run(parameters.getRuntime(), TestMain.class)
        .assertSuccessWithOutputLines(EXPECTED_OUTPUT);
  }
}
