// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.resolution.interfacetargets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestAppViewBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.LookupResult;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

// Regression for b/249859332
@RunWith(Parameterized.class)
public class MultipleMaximallySpecificTest extends TestBase {

  private static final String EXPECTED_UPTO_JDK9 = StringUtils.lines("A.foo", "Got ICCE");
  private static final String EXPECTED_AFTER_JDK9 = StringUtils.lines("A.foo", "Got AME");

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public MultipleMaximallySpecificTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testResolution() throws Exception {
    assumeTrue(parameters.isOrSimulateNoneRuntime());
    AppView<AppInfoWithLiveness> appView =
        TestAppViewBuilder.builder()
            .addProgramClasses(getInputClasses())
            .addProgramClassFileData(getTransformedClasses())
            .addLibraryFiles(parameters.getDefaultRuntimeLibrary())
            .addTestingAnnotations()
            .addKeepMainRule(Main.class)
            .setMinApi(AndroidApiLevel.LATEST)
            .buildWithLiveness();
    AppInfoWithLiveness appInfo = appView.appInfo();
    DexMethod fooI = buildNullaryVoidMethod(I.class, "foo", appInfo.dexItemFactory());
    DexMethod fooJ = buildNullaryVoidMethod(J.class, "foo", appInfo.dexItemFactory());
    DexMethod fooK = buildNullaryVoidMethod(K.class, "foo", appInfo.dexItemFactory());
    DexMethod fooA = buildNullaryVoidMethod(A.class, "foo", appInfo.dexItemFactory());
    DexMethod fooB = buildNullaryVoidMethod(B.class, "foo", appInfo.dexItemFactory());
    DexString fooName = fooA.getName();
    DexProto fooProto = fooA.getProto();

    // Resolving on A succeeds and is the override placed in A.
    MethodResolutionResult resolveFooA =
        appInfo.resolveMethodOnClass(fooA.holder, fooProto, fooName);
    assertTrue(resolveFooA.isSingleResolution());

    MethodResolutionResult resolveFooB =
        appInfo.resolveMethodOnClass(fooB.holder, fooProto, fooName);
    MethodResolutionResult resolveFooK = appInfo.resolveMethodOnInterface(fooK.holder, fooK);

    // TODO(b/249859332): These should not be failed resolution results, but rather multi results.
    //  This is the likely cause of the issue as it may lead to replacing the call-site by ICCE.
    for (MethodResolutionResult resolution : ImmutableList.of(resolveFooB, resolveFooK)) {
      assertTrue(resolution.isFailedResolution());
      Set<DexType> typesCausingFailures = new HashSet<>();
      Set<DexMethod> methodCausingFailures = new HashSet<>();
      resolution
          .asFailedResolution()
          .forEachFailureDependency(
              typesCausingFailures::add, m -> methodCausingFailures.add(m.getReference()));
      assertEquals(ImmutableSet.of(fooI, fooJ), methodCausingFailures);
      assertEquals(
          ImmutableSet.of(fooI.getHolderType(), fooJ.getHolderType()), typesCausingFailures);

      // TODO(b/249859332): Why is it possible to do 'lookup' on a failed result, and since
      // possible,
      //  why are the cause-of-failure methods not present in the lookup result?
      DexProgramClass context =
          appView.definitionForProgramType(buildType(Main.class, appInfo.dexItemFactory()));
      LookupResult lookupResult = resolution.lookupVirtualDispatchTargets(context, appView);
      assertTrue(lookupResult.isLookupResultSuccess());
      assertFalse(lookupResult.asLookupResultSuccess().hasLambdaTargets());
      Set<MethodReference> targets = new HashSet<>();
      lookupResult
          .asLookupResultSuccess()
          .forEach(
              target -> targets.add(target.getReference().asMethodReference()), lambda -> fail());
      assertEquals(ImmutableSet.of(), targets);
    }
  }

  private boolean isDesugaring() {
    return parameters.isDexRuntime()
        && parameters.getApiLevel().isLessThan(apiLevelWithDefaultInterfaceMethodsSupport());
  }

  private boolean isNewCfRuntime() {
    return parameters.isCfRuntime() && parameters.asCfRuntime().isNewerThan(CfVm.JDK9);
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(getInputClasses())
        .addProgramClassFileData(getTransformedClasses())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(isNewCfRuntime() ? EXPECTED_AFTER_JDK9 : EXPECTED_UPTO_JDK9);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(getInputClasses())
        .addProgramClassFileData(getTransformedClasses())
        .enableNoVerticalClassMergingAnnotations()
        .enableNeverClassInliningAnnotations()
        .addKeepMainRule(Main.class)
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        // TODO(b/249859332): This should be same as for reference.
        .assertFailureWithErrorThatThrows(IncompatibleClassChangeError.class);
  }

  private Collection<Class<?>> getInputClasses() {
    return ImmutableList.of(I.class, J.class, A.class, B.class, Main.class);
  }

  private Collection<byte[]> getTransformedClasses() throws Exception {
    return ImmutableList.of(transformer(K.class).setImplements(I.class, J.class).transform());
  }

  @NoVerticalClassMerging
  public interface I {

    default void foo() {
      System.out.println("I.foo");
    }
  }

  @NoVerticalClassMerging
  public interface J {

    default void foo() {
      System.out.println("J.foo");
    }
  }

  @NoVerticalClassMerging
  public interface K extends I /* and J via transformer */ {}

  @NeverClassInline
  public static class A implements K {

    @Override
    public void foo() {
      System.out.println("A.foo");
    }
  }

  @NeverClassInline
  public static class B implements K {}

  public static class Main {

    public static K getK(int x) {
      return x == 0 ? new A() : new B();
    }

    public static void main(String[] args) {
      getK(args.length).foo();
      try {
        getK(args.length + 1).foo();
      } catch (AbstractMethodError e) {
        System.out.println("Got AME");
      } catch (IncompatibleClassChangeError e) {
        System.out.println("Got ICCE");
      }
    }
  }
}
