// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.resolution.singletarget;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertNull;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.LookupResult;
import com.android.tools.r8.graph.ResolutionResult;
import com.android.tools.r8.ir.analysis.type.ClassTypeLatticeElement;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InstantiatedLowerBoundTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public InstantiatedLowerBoundTest(TestParameters parameters) {
    // Empty to satisfy construction of none-runtime.
  }

  @Test
  public void testSingleTargetLowerBoundInstantiated() throws Exception {
    AppView<AppInfoWithLiveness> appView =
        computeAppViewWithLiveness(
            buildClasses(A.class, B.class, Main.class).build(),
            factory -> new ArrayList<>(buildKeepRuleForClassAndMethods(Main.class, factory)));
    AppInfoWithLiveness appInfo = appView.appInfo();
    DexType typeA = buildType(A.class, appInfo.dexItemFactory());
    DexType typeB = buildType(B.class, appInfo.dexItemFactory());
    DexType typeMain = buildType(Main.class, appInfo.dexItemFactory());
    DexMethod fooA = buildNullaryVoidMethod(A.class, "foo", appInfo.dexItemFactory());
    ClassTypeLatticeElement latticeB =
        ClassTypeLatticeElement.create(typeB, Nullability.definitelyNotNull(), appView);
    DexEncodedMethod singleTarget =
        appInfo.lookupSingleVirtualTarget(fooA, typeMain, false, t -> false, typeA, latticeB);
    assertNotNull(singleTarget);
    DexMethod fooB = buildNullaryVoidMethod(B.class, "foo", appInfo.dexItemFactory());
    assertEquals(fooB, singleTarget.method);
  }

  @Test
  public void testSingleTargetLowerBoundInMiddleInstantiated() throws Exception {
    AppView<AppInfoWithLiveness> appView =
        computeAppViewWithLiveness(
            buildClasses(A.class, B.class, C.class, Main.class).build(),
            factory -> new ArrayList<>(buildKeepRuleForClassAndMethods(Main.class, factory)));
    AppInfoWithLiveness appInfo = appView.appInfo();
    DexType typeA = buildType(A.class, appInfo.dexItemFactory());
    DexType typeB = buildType(B.class, appInfo.dexItemFactory());
    DexType typeMain = buildType(Main.class, appInfo.dexItemFactory());
    DexMethod fooA = buildNullaryVoidMethod(A.class, "foo", appInfo.dexItemFactory());
    ClassTypeLatticeElement latticeB =
        ClassTypeLatticeElement.create(typeB, Nullability.definitelyNotNull(), appView);
    DexEncodedMethod singleTarget =
        appInfo.lookupSingleVirtualTarget(fooA, typeMain, false, t -> false, typeA, latticeB);
    assertNotNull(singleTarget);
    DexMethod fooB = buildNullaryVoidMethod(B.class, "foo", appInfo.dexItemFactory());
    assertEquals(fooB, singleTarget.method);
  }

  @Test
  public void testSingleTargetLowerAllInstantiated() throws Exception {
    AppView<AppInfoWithLiveness> appView =
        computeAppViewWithLiveness(
            buildClasses(A.class, B.class, C.class, MainAllInstantiated.class).build(),
            factory ->
                new ArrayList<>(
                    buildKeepRuleForClassAndMethods(MainAllInstantiated.class, factory)));
    AppInfoWithLiveness appInfo = appView.appInfo();
    DexType typeA = buildType(A.class, appInfo.dexItemFactory());
    DexType typeC = buildType(C.class, appInfo.dexItemFactory());
    DexType typeMain = buildType(MainAllInstantiated.class, appInfo.dexItemFactory());
    DexMethod fooA = buildNullaryVoidMethod(A.class, "foo", appInfo.dexItemFactory());
    DexMethod fooB = buildNullaryVoidMethod(B.class, "foo", appInfo.dexItemFactory());
    DexMethod fooC = buildNullaryVoidMethod(C.class, "foo", appInfo.dexItemFactory());
    ResolutionResult resolution = appInfo.resolveMethod(typeA, fooA);
    DexProgramClass context = appView.definitionForProgramType(typeMain);
    DexProgramClass upperBound = appView.definitionForProgramType(typeA);
    DexProgramClass lowerBound = appView.definitionForProgramType(typeC);
    LookupResult lookupResult =
        resolution.lookupVirtualDispatchTargets(context, appInfo, upperBound, lowerBound);
    Set<DexMethod> expected = Sets.newIdentityHashSet();
    expected.add(fooA);
    expected.add(fooB);
    expected.add(fooC);
    assertTrue(lookupResult.isLookupResultSuccess());
    Set<DexMethod> actual = Sets.newIdentityHashSet();
    lookupResult
        .asLookupResultSuccess()
        .forEach(
            clazzAndMethod -> actual.add(clazzAndMethod.getMethod().method),
            lambdaTarget -> {
              assert false;
            });
    assertEquals(expected, actual);
    ClassTypeLatticeElement latticeC =
        ClassTypeLatticeElement.create(typeC, Nullability.definitelyNotNull(), appView);
    DexEncodedMethod singleTarget =
        appInfo.lookupSingleVirtualTarget(fooA, typeMain, false, t -> false, typeA, latticeC);
    assertNull(singleTarget);
  }

  public static class A {

    public void foo() {
      System.out.println("A.foo");
    }
  }

  public static class B extends A {

    @Override
    public void foo() {
      System.out.println("B.foo");
    }
  }

  public static class C extends B {

    @Override
    public void foo() {
      System.out.println("C.foo");
    }
  }

  public static class Main {

    public static void main(String[] args) {
      new B();
    }
  }

  public static class MainAllInstantiated {

    public static void main(String[] args) {
      new A();
      new B();
      new C();
    }
  }
}
