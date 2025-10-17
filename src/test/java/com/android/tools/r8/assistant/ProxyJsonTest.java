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
import com.android.tools.r8.assistant.postprocessing.model.ProxyNewProxyInstance;
import com.android.tools.r8.assistant.postprocessing.model.ReflectiveEvent;
import com.android.tools.r8.assistant.runtime.ReflectiveEventType;
import com.android.tools.r8.assistant.runtime.ReflectiveOperationJsonLogger;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.shaking.KeepInfoCollectionExported;
import com.android.tools.r8.utils.Box;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ProxyJsonTest extends TestBase {

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
        .addProgramClassesAndInnerClasses(ProxyTestClass.class)
        .addInstrumentationClasses(Instrumentation.class)
        .setCustomReflectiveOperationReceiver(Instrumentation.class)
        .setMinApi(parameters)
        .addOptionsModification(opt -> factoryBox.set(opt.itemFactory))
        .compile()
        .addVmArguments("-Dcom.android.tools.r8.reflectiveJsonLogger=" + path)
        .run(parameters.getRuntime(), ProxyTestClass.class)
        .assertSuccess();
    List<ReflectiveEvent> reflectiveEvents =
        new ReflectiveOperationJsonParser(factoryBox.get()).parse(path);
    assertEquals(1, reflectiveEvents.size());

    assertTrue(reflectiveEvents.get(0).isProxyNewProxyInstance());
    ProxyNewProxyInstance updater0 = reflectiveEvents.get(0).asProxyNewProxyInstance();
    assertEquals(ReflectiveEventType.PROXY_NEW_PROXY_INSTANCE, updater0.getEventType());
    assertEquals(ProxyTestClass.F.class.getName(), updater0.getInterfaces()[0].toSourceString());
    assertEquals(1, updater0.getInterfaces().length);

    Box<KeepInfoCollectionExported> keepInfoBox = new Box<>();
    testForR8(parameters)
        .addProgramClassesAndInnerClasses(ProxyTestClass.class)
        .addOptionsModification(
            opt -> opt.testing.finalKeepInfoCollectionConsumer = keepInfoBox::set)
        .setMinApi(parameters)
        .addKeepMainRule(ProxyTestClass.class)
        .run(parameters.getRuntime(), ProxyTestClass.class)
        .assertSuccess();

    KeepInfoCollectionExported keepInfoCollectionExported = keepInfoBox.get();

    assertTrue(updater0.isKeptBy(keepInfoCollectionExported));
  }

  public static class Instrumentation extends ReflectiveOperationJsonLogger {
    public Instrumentation() throws IOException {}
  }
}
