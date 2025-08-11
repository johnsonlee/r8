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
import com.android.tools.r8.assistant.postprocessing.model.ClassGetMember;
import com.android.tools.r8.assistant.postprocessing.model.ClassGetMembers;
import com.android.tools.r8.assistant.postprocessing.model.ClassGetName;
import com.android.tools.r8.assistant.postprocessing.model.ReflectiveEvent;
import com.android.tools.r8.assistant.runtime.ReflectiveEventType;
import com.android.tools.r8.assistant.runtime.ReflectiveOperationJsonLogger;
import com.android.tools.r8.assistant.runtime.ReflectiveOperationReceiver.NameLookupType;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.references.Reference;
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

    assertTrue(reflectiveEvents.get(3).isClassGetMember());
    ClassGetMember updater00 = reflectiveEvents.get(3).asClassGetMember();
    assertEquals(ReflectiveEventType.CLASS_GET_DECLARED_METHOD, updater00.getEventType());
    assertEquals(
        Reference.methodFromMethod(Foo.class.getDeclaredMethod("barr")),
        updater00.getMember().asDexMethod().asMethodReference());

    assertTrue(reflectiveEvents.get(4).isClassGetMember());
    ClassGetMember updater01 = reflectiveEvents.get(4).asClassGetMember();
    assertEquals(ReflectiveEventType.CLASS_GET_DECLARED_FIELD, updater01.getEventType());
    assertEquals(
        Reference.fieldFromField(Foo.class.getDeclaredField("a")),
        updater01.getMember().asDexField().asFieldReference());

    assertTrue(reflectiveEvents.get(6).isClassGetMembers());
    ClassGetMembers updater02 = reflectiveEvents.get(6).asClassGetMembers();
    assertEquals(ReflectiveEventType.CLASS_GET_DECLARED_METHODS, updater02.getEventType());
    assertEquals(Foo.class.getName(), updater02.getHolder().toSourceString());

    assertTrue(reflectiveEvents.get(7).isClassGetMembers());
    ClassGetMembers updater03 = reflectiveEvents.get(7).asClassGetMembers();
    assertEquals(ReflectiveEventType.CLASS_GET_DECLARED_FIELDS, updater03.getEventType());
    assertEquals(Foo.class.getName(), updater03.getHolder().toSourceString());

    assertTrue(reflectiveEvents.get(8).isClassGetMember());
    ClassGetMember updater04 = reflectiveEvents.get(8).asClassGetMember();
    assertEquals(ReflectiveEventType.CLASS_GET_DECLARED_CONSTRUCTOR, updater04.getEventType());
    assertEquals(
        Reference.methodFromMethod(Foo.class.getDeclaredConstructor()),
        updater04.getMember().asDexMethod().asMethodReference());

    assertTrue(reflectiveEvents.get(9).isClassGetMembers());
    ClassGetMembers updater05 = reflectiveEvents.get(9).asClassGetMembers();
    assertEquals(ReflectiveEventType.CLASS_GET_DECLARED_CONSTRUCTORS, updater05.getEventType());
    assertEquals(Foo.class.getName(), updater05.getHolder().toSourceString());

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

    assertTrue(reflectiveEvents.get(19).isClassGetMembers());
    ClassGetMembers updater19 = reflectiveEvents.get(19).asClassGetMembers();
    assertEquals(ReflectiveEventType.CLASS_GET_METHODS, updater19.getEventType());
    assertEquals(Bar.class.getName(), updater19.getHolder().toSourceString());

    assertTrue(reflectiveEvents.get(20).isClassGetMembers());
    ClassGetMembers updater20 = reflectiveEvents.get(20).asClassGetMembers();
    assertEquals(ReflectiveEventType.CLASS_GET_FIELDS, updater20.getEventType());
    assertEquals(Bar.class.getName(), updater20.getHolder().toSourceString());

    assertTrue(reflectiveEvents.get(21).isClassGetMembers());
    ClassGetMembers updater21 = reflectiveEvents.get(21).asClassGetMembers();
    assertEquals(ReflectiveEventType.CLASS_GET_CONSTRUCTORS, updater21.getEventType());
    assertEquals(Bar.class.getName(), updater21.getHolder().toSourceString());

    assertTrue(reflectiveEvents.get(22).isClassGetMember());
    ClassGetMember updater22 = reflectiveEvents.get(22).asClassGetMember();
    assertEquals(ReflectiveEventType.CLASS_GET_METHOD, updater22.getEventType());
    assertEquals(
        Reference.methodFromMethod(Bar.class.getMethod("bar")),
        updater22.getMember().asDexMethod().asMethodReference());

    assertTrue(reflectiveEvents.get(23).isClassGetMember());
    ClassGetMember updater23 = reflectiveEvents.get(23).asClassGetMember();
    assertEquals(ReflectiveEventType.CLASS_GET_FIELD, updater23.getEventType());
    assertEquals(
        Reference.fieldFromField(Bar.class.getField("i")),
        updater23.getMember().asDexField().asFieldReference());

    assertTrue(reflectiveEvents.get(24).isClassGetMember());
    ClassGetMember updater24 = reflectiveEvents.get(24).asClassGetMember();
    assertEquals(ReflectiveEventType.CLASS_GET_CONSTRUCTOR, updater24.getEventType());
    assertEquals(
        Reference.methodFromMethod(Bar.class.getConstructor()),
        updater24.getMember().asDexMethod().asMethodReference());
  }

  public static class Instrumentation extends ReflectiveOperationJsonLogger {
    public Instrumentation() throws IOException {}
  }
}
