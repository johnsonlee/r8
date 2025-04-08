// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DirectMappedDexApplication;
import com.android.tools.r8.graph.ImmediateAppSubtypingInfo;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class R8Shaking2LookupTest extends TestBase {

  private DexItemFactory dexItemFactory;
  private AppInfoWithClassHierarchy appInfo;
  private ImmediateAppSubtypingInfo subtypingInfo;

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  @Before
  public void readApp() throws IOException, ExecutionException {
    DirectMappedDexApplication program =
        ToolHelper.buildApplication(
            ImmutableList.of(ToolHelper.EXAMPLES_BUILD_DIR + "shaking2.jar"));
    dexItemFactory = program.dexItemFactory;
    AppView<AppInfoWithClassHierarchy> appView = AppView.createForR8(program);
    appInfo = appView.appInfo();
    subtypingInfo = ImmediateAppSubtypingInfo.create(appView);
  }

  private void validateSubtype(DexClass super_type, DexClass sub_type) {
    assertNotEquals(super_type, sub_type);
    assertTrue(getTransitiveProgramSubclasses(super_type).contains(sub_type));
    assertTrue(appInfo.isSubtype(sub_type, super_type));
    assertFalse(getTransitiveProgramSubclasses(sub_type).contains(super_type));
    assertFalse(appInfo.isSubtype(super_type, sub_type));
  }

  private Set<DexClass> getTransitiveProgramSubclasses(DexClass clazz) {
    Set<DexClass> subclasses = Sets.newIdentityHashSet();
    subtypingInfo.forEachTransitiveSubclass(clazz, subclasses::add);
    return subclasses;
  }

  private void validateSubtypeSize(DexClass clazz, int size) {
    assertEquals(size, getTransitiveProgramSubclasses(clazz).size());
  }

  @Test
  public void testLookup() {
    DexClass object_type = definitionFor("Ljava/lang/Object;");
    DexClass interface_type = definitionFor("Lshaking2/Interface;");
    DexClass superInterface1_type = definitionFor("Lshaking2/SuperInterface1;");
    DexClass superInterface2_type = definitionFor("Lshaking2/SuperInterface2;");
    DexClass superclass_type = definitionFor("Lshaking2/SuperClass;");
    DexClass subClass1_type = definitionFor("Lshaking2/SubClass1;");
    DexClass subClass2_type = definitionFor("Lshaking2/SubClass2;");
    validateSubtype(object_type, interface_type);
    validateSubtypeSize(interface_type, 4);
    validateSubtype(interface_type, superclass_type);
    validateSubtype(superInterface1_type, subClass1_type);
    validateSubtype(superInterface2_type, subClass2_type);
    validateSubtype(superclass_type, subClass2_type);
    validateSubtype(superInterface1_type, interface_type);
    validateSubtype(superInterface2_type, interface_type);
    validateSubtypeSize(subClass2_type, 0);
  }

  private DexClass definitionFor(String descriptor) {
    return appInfo.definitionFor(dexItemFactory.createType(descriptor));
  }
}
