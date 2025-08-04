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
import com.android.tools.r8.assistant.postprocessing.model.ClassGetName;
import com.android.tools.r8.assistant.postprocessing.model.ReflectiveEvent;
import com.android.tools.r8.assistant.runtime.ReflectiveOperationJsonLogger;
import com.android.tools.r8.assistant.runtime.ReflectiveOperationReceiver.NameLookupType;
import com.android.tools.r8.graph.DexItemFactory;
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
public class JavaLangClassJsonTest extends TestBase {

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
        .addProgramClasses(JavaLangClassTestClass.class, Foo.class, Bar.class)
        .addInstrumentationClasses(Instrumentation.class)
        .setCustomReflectiveOperationReceiver(Instrumentation.class)
        .setMinApi(parameters)
        .addOptionsModification(opt -> factoryBox.set(opt.itemFactory))
        .compile()
        .addVmArguments("-Dcom.android.tools.r8.reflectiveJsonLogger=" + path)
        .run(parameters.getRuntime(), JavaLangClassTestClass.class)
        .assertSuccess();
    List<ReflectiveEvent> reflectiveEvents =
        new ReflectiveOperationJsonParser(factoryBox.get()).parse(path);
    Assert.assertEquals(28, reflectiveEvents.size());

    assertTrue(reflectiveEvents.get(10).isClassGetName());
    ClassGetName updater0 = reflectiveEvents.get(10).asClassGetName();
    assertEquals(Foo.class.getName(), updater0.getType().toSourceString());
    assertEquals(NameLookupType.NAME, updater0.getNameLookupType());

    assertTrue(reflectiveEvents.get(11).isClassGetName());
    ClassGetName updater1 = reflectiveEvents.get(11).asClassGetName();
    assertEquals(Foo.class.getName(), updater1.getType().toSourceString());
    assertEquals(NameLookupType.CANONICAL_NAME, updater1.getNameLookupType());

    assertTrue(reflectiveEvents.get(12).isClassGetName());
    ClassGetName updater2 = reflectiveEvents.get(12).asClassGetName();
    assertEquals(Foo.class.getName(), updater2.getType().toSourceString());
    assertEquals(NameLookupType.SIMPLE_NAME, updater2.getNameLookupType());

    assertTrue(reflectiveEvents.get(13).isClassGetName());
    ClassGetName updater3 = reflectiveEvents.get(13).asClassGetName();
    assertEquals(Foo.class.getName(), updater3.getType().toSourceString());
    assertEquals(NameLookupType.TYPE_NAME, updater3.getNameLookupType());
  }

  public static class Instrumentation extends ReflectiveOperationJsonLogger {
    public Instrumentation() throws IOException {}
  }
}
