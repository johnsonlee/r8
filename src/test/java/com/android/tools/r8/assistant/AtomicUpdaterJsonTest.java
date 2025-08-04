// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.assistant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.assistant.JavaLangClassTestClass.Bar;
import com.android.tools.r8.assistant.JavaLangClassTestClass.Foo;
import com.android.tools.r8.assistant.postprocessing.ReflectiveOperationJsonParser;
import com.android.tools.r8.assistant.postprocessing.model.AtomicFieldUpdaterNewUpdater;
import com.android.tools.r8.assistant.postprocessing.model.ReflectiveEvent;
import com.android.tools.r8.assistant.runtime.ReflectiveOperationJsonLogger;
import com.android.tools.r8.graph.DexItemFactory;
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
public class AtomicUpdaterJsonTest extends TestBase {

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
        .addProgramClasses(AtomicUpdaterTestClass.class, Foo.class, Bar.class)
        .addInstrumentationClasses(Instrumentation.class)
        .setCustomReflectiveOperationReceiver(Instrumentation.class)
        .setMinApi(parameters)
        .addOptionsModification(opt -> factoryBox.set(opt.itemFactory))
        .compile()
        .addVmArguments("-Dcom.android.tools.r8.reflectiveJsonLogger=" + path)
        .run(parameters.getRuntime(), AtomicUpdaterTestClass.class)
        .assertSuccess();
    List<ReflectiveEvent> reflectiveEvents =
        new ReflectiveOperationJsonParser(factoryBox.get()).parse(path);
    assertEquals(3, reflectiveEvents.size());
    String name = AtomicUpdaterTestClass.class.getName();

    assertTrue(reflectiveEvents.get(0).isAtomicFieldUpdaterNewUpdater());
    AtomicFieldUpdaterNewUpdater updater0 =
        reflectiveEvents.get(0).asAtomicFieldUpdaterNewUpdater();
    assertEquals("int " + name + ".i", updater0.getField().toSourceString());

    assertTrue(reflectiveEvents.get(1).isAtomicFieldUpdaterNewUpdater());
    AtomicFieldUpdaterNewUpdater updater1 =
        reflectiveEvents.get(1).asAtomicFieldUpdaterNewUpdater();
    assertEquals("long " + name + ".l", updater1.getField().toSourceString());

    assertTrue(reflectiveEvents.get(2).isAtomicFieldUpdaterNewUpdater());
    AtomicFieldUpdaterNewUpdater updater2 =
        reflectiveEvents.get(2).asAtomicFieldUpdaterNewUpdater();
    assertEquals("java.lang.Object " + name + ".o", updater2.getField().toSourceString());
  }

  public static class Instrumentation extends ReflectiveOperationJsonLogger {
    public Instrumentation() throws IOException {}
  }
}
