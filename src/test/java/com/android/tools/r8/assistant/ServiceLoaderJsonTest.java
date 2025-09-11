// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.assistant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.assistant.postprocessing.ReflectiveOperationJsonParser;
import com.android.tools.r8.assistant.postprocessing.model.ReflectiveEvent;
import com.android.tools.r8.assistant.postprocessing.model.ServiceLoaderLoad;
import com.android.tools.r8.assistant.runtime.ReflectiveOperationJsonLogger;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.shaking.KeepInfoCollectionExported;
import com.android.tools.r8.utils.Box;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ServiceLoaderJsonTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNativeMultidexDexRuntimes().withMaximumApiLevel().build();
  }

  @Test
  public void testInstrumentationWithCustomOracle() throws Exception {
    Path path = Paths.get(temp.newFile().getAbsolutePath());
    Box<DexItemFactory> factoryBox = new Box<>();
    testForAssistant()
        .addProgramClassesAndInnerClasses(ServiceLoaderTestClass.class)
        .addInstrumentationClasses(Instrumentation.class)
        .setCustomReflectiveOperationReceiver(Instrumentation.class)
        .setMinApi(parameters)
        .addOptionsModification(opt -> factoryBox.set(opt.itemFactory))
        .compile()
        .addVmArguments("-Dcom.android.tools.r8.reflectiveJsonLogger=" + path)
        .run(parameters.getRuntime(), ServiceLoaderTestClass.class)
        .assertSuccess();
    List<ReflectiveEvent> reflectiveEvents =
        new ReflectiveOperationJsonParser(factoryBox.get()).parse(path);
    Assert.assertEquals(3, reflectiveEvents.size());

    assertTrue(reflectiveEvents.get(0).isServiceLoaderLoad());
    ServiceLoaderLoad updater0 = reflectiveEvents.get(0).asServiceLoaderLoad();
    assertEquals(
        ServiceLoaderTestClass.NameService.class.getName(), updater0.getType().toSourceString());

    assertTrue(reflectiveEvents.get(1).isServiceLoaderLoad());
    ServiceLoaderLoad updater1 = reflectiveEvents.get(1).asServiceLoaderLoad();
    assertEquals(
        ServiceLoaderTestClass.NameService.class.getName(), updater1.getType().toSourceString());

    assertTrue(reflectiveEvents.get(2).isServiceLoaderLoad());
    ServiceLoaderLoad updater2 = reflectiveEvents.get(2).asServiceLoaderLoad();
    assertEquals(
        ServiceLoaderTestClass.NameService.class.getName(), updater2.getType().toSourceString());

    Box<KeepInfoCollectionExported> keepInfoBox = new Box<>();
    testForR8(parameters)
        .addProgramClassesAndInnerClasses(ServiceLoaderTestClass.class)
        .addOptionsModification(
            opt -> opt.testing.finalKeepInfoCollectionConsumer = keepInfoBox::set)
        .setMinApi(parameters)
        .addKeepMainRule(ServiceLoaderTestClass.class)
        .addKeepRules("-keep class " + ServiceLoaderTestClass.NameService.class.getName())
        .addKeepAttributeInnerClassesAndEnclosingMethod()
        .run(parameters.getRuntime(), ServiceLoaderTestClass.class)
        .assertSuccessWithOutputLines();
    KeepInfoCollectionExported keepInfoCollectionExported = keepInfoBox.get();

    assertTrue(updater0.isKeptBy(keepInfoCollectionExported));
    assertTrue(updater1.isKeptBy(keepInfoCollectionExported));
    assertTrue(updater2.isKeptBy(keepInfoCollectionExported));
  }

  public static class Instrumentation extends ReflectiveOperationJsonLogger {

    public Instrumentation() throws IOException {}
  }
}
