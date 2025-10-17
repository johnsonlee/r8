// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.jdk21.assistant;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.assistant.postprocessing.ReflectiveOperationJsonParser;
import com.android.tools.r8.assistant.postprocessing.model.ClassFlagEvent;
import com.android.tools.r8.assistant.postprocessing.model.ReflectiveEvent;
import com.android.tools.r8.assistant.runtime.ReflectiveEventType;
import com.android.tools.r8.assistant.runtime.ReflectiveOperationJsonLogger;
import com.android.tools.r8.assistant.runtime.ReflectiveOperationReceiver.ClassFlag;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.jdk21.assistant.JavaLangTestClass21.Foo;
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
public class JavaLangClass21JsonTest extends TestBase {

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
        .addProgramClasses(JavaLangTestClass21.class, Foo.class)
        .addStrippedOuter(getClass())
        .addInstrumentationClasses(Instrumentation.class)
        .setCustomReflectiveOperationReceiver(Instrumentation.class)
        .setMinApi(parameters)
        .addOptionsModification(opt -> factoryBox.set(opt.itemFactory))
        .compile()
        .addVmArguments("-Dcom.android.tools.r8.reflectiveJsonLogger=" + path)
        .run(parameters.getRuntime(), JavaLangTestClass21.class)
        .assertSuccess();
    List<ReflectiveEvent> reflectiveEvents =
        new ReflectiveOperationJsonParser(factoryBox.get()).parse(path);
    assertEquals(14, reflectiveEvents.size());

    ClassFlag[] expectedClassFlags = {
      ClassFlag.ANNOTATION,
      ClassFlag.ANONYMOUS_CLASS,
      ClassFlag.ARRAY,
      ClassFlag.ENUM,
      ClassFlag.HIDDEN,
      ClassFlag.INTERFACE,
      ClassFlag.LOCAL_CLASS,
      ClassFlag.MEMBER_CLASS,
      ClassFlag.PRIMITIVE,
      ClassFlag.RECORD,
      ClassFlag.SEALED,
      ClassFlag.SYNTHETIC
    };

    for (int i = 2; i < reflectiveEvents.size(); i++) {
      ReflectiveEvent event = reflectiveEvents.get(i);
      ClassFlagEvent classFlagEvent = event.asClassFlagEvent();
      assertEquals(ReflectiveEventType.CLASS_FLAG, classFlagEvent.getEventType());
      assertEquals(expectedClassFlags[i - 2], classFlagEvent.getClassFlag());
    }
  }

  public static class Instrumentation extends ReflectiveOperationJsonLogger {

    public Instrumentation() throws IOException {}
  }
}
