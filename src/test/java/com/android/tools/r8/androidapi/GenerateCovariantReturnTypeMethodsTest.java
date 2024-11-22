// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.androidapi;

import static com.android.tools.r8.apimodel.JavaSourceCodePrinter.Type.fromType;
import static com.android.tools.r8.utils.MapUtils.ignoreKey;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.apimodel.JavaSourceCodePrinter;
import com.android.tools.r8.apimodel.JavaSourceCodePrinter.JavaSourceCodeMethodPrinter;
import com.android.tools.r8.apimodel.JavaSourceCodePrinter.KnownType;
import com.android.tools.r8.apimodel.JavaSourceCodePrinter.MethodParameter;
import com.android.tools.r8.apimodel.JavaSourceCodePrinter.ParameterizedType;
import com.android.tools.r8.cfmethodgeneration.MethodGenerationBase;
import com.android.tools.r8.graph.DexAnnotation;
import com.android.tools.r8.graph.DexAnnotationElement;
import com.android.tools.r8.graph.DexEncodedAnnotation;
import com.android.tools.r8.graph.DexValue;
import com.android.tools.r8.graph.DexValue.DexValueAnnotation;
import com.android.tools.r8.graph.DexValue.DexValueArray;
import com.android.tools.r8.graph.DexValue.DexValueType;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.references.TypeReference;
import com.android.tools.r8.utils.Action;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.ClassReferenceUtils;
import com.android.tools.r8.utils.EntryUtils;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.MethodReferenceUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import com.google.common.collect.ImmutableList;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class GenerateCovariantReturnTypeMethodsTest extends TestBase {

  private static final String COVARIANT_RETURN_TYPE_ANNOTATION_NAME =
      "dalvik.annotation.codegen.CovariantReturnType";
  private static final String COVARIANT_RETURN_TYPES_ANNOTATION_NAME =
      "dalvik.annotation.codegen.CovariantReturnType$CovariantReturnTypes";

  private static final String CLASS_NAME = "CovariantReturnTypeMethods";
  private static final String PACKAGE_NAME = "com.android.tools.r8.androidapi";
  // When updating to support a new api level build libcore in aosp and update the cloud dependency.
  private static final Path PATH_TO_CORE_JAR =
      Paths.get(ToolHelper.THIRD_PARTY_DIR, "android_jar", "libcore_latest", "core-oj.jar");
  private static final Path DESTINATION_FILE =
      Paths.get(ToolHelper.MAIN_SOURCE_DIR)
          .resolve(PACKAGE_NAME.replace('.', '/'))
          .resolve(CLASS_NAME + ".java");
  private static final AndroidApiLevel GENERATED_FOR_API_LEVEL = AndroidApiLevel.BAKLAVA;

  @Parameter public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  @Test
  public void testLibCoreNeedsUpgrading() {
    assertEquals(GENERATED_FOR_API_LEVEL, AndroidApiLevel.API_DATABASE_LEVEL);
  }

  @Test
  public void testCanFindAnnotatedMethodsInJar() throws Exception {
    CovariantMethodsInJarResult covariantMethodsInJar = CovariantMethodsInJarResult.create();
    // These assertions are here to ensure we produce a sane result.
    assertEquals(11, covariantMethodsInJar.methodReferenceMap.keySet().size());
    assertEquals(
        71, covariantMethodsInJar.methodReferenceMap.values().stream().mapToLong(List::size).sum());
  }

  @Test
  public void testGeneratedCodeUpToDate() throws Exception {
    assertEquals(FileUtils.readTextFile(DESTINATION_FILE, StandardCharsets.UTF_8), generateCode());
  }

  public static String generateCode() throws Exception {
    CovariantMethodsInJarResult covariantMethodsInJar = CovariantMethodsInJarResult.create();
    List<Entry<ClassReference, List<MethodReferenceWithApiLevel>>> entries =
        new ArrayList<>(covariantMethodsInJar.methodReferenceMap.entrySet());
    entries.sort(Entry.comparingByKey(ClassReferenceUtils.getClassReferenceComparator()));
    JavaSourceCodePrinter printer =
        JavaSourceCodePrinter.builder()
            .setHeader(
                MethodGenerationBase.getHeaderString(
                    2022, GenerateCovariantReturnTypeMethodsTest.class.getSimpleName()))
            .setPackageName(PACKAGE_NAME)
            .setClassName(CLASS_NAME)
            .build();
    String javaSourceCode =
        printer
            .addMethod(
                "public static",
                null,
                "registerMethodsWithCovariantReturnType",
                ImmutableList.of(
                    MethodParameter.build(fromType(KnownType.DexItemFactory), "factory"),
                    MethodParameter.build(
                        ParameterizedType.fromType(
                            KnownType.Consumer, fromType(KnownType.DexMethod)),
                        "consumer")),
                methodPrinter ->
                    entries.forEach(
                        EntryUtils.accept(
                            (ignored, covariations) -> {
                              covariations.sort(
                                  Comparator.comparing(
                                      MethodReferenceWithApiLevel::getMethodReference,
                                      MethodReferenceUtils.getMethodReferenceComparator()));
                              covariations.forEach(
                                  covariant ->
                                      registerCovariantMethod(
                                          methodPrinter, covariant.methodReference));
                            })))
            .toString();
    Path tempFile = Files.createTempFile("output-", ".java");
    Files.write(tempFile, javaSourceCode.getBytes(StandardCharsets.UTF_8));
    return MethodGenerationBase.formatRawOutput(tempFile);
  }

  private static void registerCovariantMethod(
      JavaSourceCodeMethodPrinter methodPrinter, MethodReference covariant) {
    methodPrinter
        .addInstanceMethodCall(
            "consumer",
            "accept",
            () ->
                methodPrinter.addInstanceMethodCall(
                    "factory",
                    "createMethod",
                    callCreateType(methodPrinter, covariant.getHolderClass().getDescriptor()),
                    callCreateProto(
                        methodPrinter,
                        covariant.getReturnType().getDescriptor(),
                        covariant.getFormalTypes().stream()
                            .map(TypeReference::getDescriptor)
                            .collect(Collectors.toList())),
                    methodPrinter.literal(covariant.getMethodName())))
        .addSemicolon()
        .newLine();
  }

  private static Action callCreateType(
      JavaSourceCodeMethodPrinter methodPrinter, String descriptor) {
    return () ->
        methodPrinter.addInstanceMethodCall(
            "factory", "createType", methodPrinter.literal(descriptor));
  }

  private static Action callCreateProto(
      JavaSourceCodeMethodPrinter methodPrinter,
      String returnTypeDescriptor,
      Collection<String> args) {
    List<Action> actionList = new ArrayList<>();
    actionList.add(callCreateType(methodPrinter, returnTypeDescriptor));
    for (String arg : args) {
      actionList.add(callCreateType(methodPrinter, arg));
    }
    return () -> methodPrinter.addInstanceMethodCall("factory", "createProto", actionList);
  }

  public static void main(String[] args) throws Exception {
    Files.write(DESTINATION_FILE, generateCode().getBytes(StandardCharsets.UTF_8));
  }

  public static class CovariantMethodsInJarResult {
    private final Map<ClassReference, List<MethodReferenceWithApiLevel>> methodReferenceMap;

    private CovariantMethodsInJarResult(
        Map<ClassReference, List<MethodReferenceWithApiLevel>> methodReferenceMap) {
      this.methodReferenceMap = methodReferenceMap;
    }

    public static CovariantMethodsInJarResult create() throws Exception {
      Map<ClassReference, List<MethodReferenceWithApiLevel>> methodReferenceMap = new HashMap<>();
      CodeInspector inspector = new CodeInspector(PATH_TO_CORE_JAR);
      for (FoundClassSubject clazz : inspector.allClasses()) {
        clazz.forAllMethods(
            method -> {
              List<DexAnnotation> covariantAnnotations =
                  inspector.findAnnotations(
                      method.getMethod().annotations(),
                      annotation -> {
                        String typeName = annotation.getAnnotationType().getTypeName();
                        return typeName.equals(COVARIANT_RETURN_TYPE_ANNOTATION_NAME)
                            || typeName.equals(COVARIANT_RETURN_TYPES_ANNOTATION_NAME);
                      });
              if (!covariantAnnotations.isEmpty()) {
                MethodReference methodReference = method.asMethodReference();
                for (DexAnnotation covariantAnnotation : covariantAnnotations) {
                  createCovariantMethodReference(
                      methodReference, covariantAnnotation.annotation, methodReferenceMap);
                }
              }
            });
      }
      return new CovariantMethodsInJarResult(methodReferenceMap);
    }

    private static void createCovariantMethodReference(
        MethodReference methodReference,
        DexEncodedAnnotation covariantAnnotation,
        Map<ClassReference, List<MethodReferenceWithApiLevel>> methodReferenceMap) {
      if (covariantAnnotation
          .getType()
          .getTypeName()
          .equals(COVARIANT_RETURN_TYPE_ANNOTATION_NAME)) {
        DexAnnotationElement returnTypeElement = covariantAnnotation.getElement(0);
        assert returnTypeElement.getName().toString().equals("returnType");
        DexValueType newReturnType = returnTypeElement.getValue().asDexValueType();
        DexAnnotationElement presentAfterElement = covariantAnnotation.getElement(1);
        assert presentAfterElement.getName().toString().equals("presentAfter");
        AndroidApiLevel apiLevel =
            AndroidApiLevel.getAndroidApiLevel(
                presentAfterElement.getValue().asDexValueInt().getValue());
        methodReferenceMap
            .computeIfAbsent(methodReference.getHolderClass(), ignoreKey(ArrayList::new))
            .add(
                new MethodReferenceWithApiLevel(
                    Reference.method(
                        methodReference.getHolderClass(),
                        methodReference.getMethodName(),
                        methodReference.getFormalTypes(),
                        newReturnType.getValue().asClassReference()),
                    apiLevel));
      } else {
        assert covariantAnnotation
            .getType()
            .getTypeName()
            .equals(COVARIANT_RETURN_TYPES_ANNOTATION_NAME);
        DexAnnotationElement valuesElement = covariantAnnotation.getElement(0);
        assertEquals("value", valuesElement.getName().toString());
        DexValueArray array = valuesElement.getValue().asDexValueArray();
        if (array == null) {
          fail(
              String.format(
                  "Expected element \"value\" of CovariantReturnTypes annotation to "
                      + "be an array (method: \"%s\", was: %s)",
                  methodReference.toSourceString(),
                  valuesElement.getValue().getClass().getCanonicalName()));
        }

        // Handle the inner dalvik.annotation.codegen.CovariantReturnType annotations recursively.
        for (DexValue value : array.getValues()) {
          assert value.isDexValueAnnotation();
          DexValueAnnotation innerAnnotation = value.asDexValueAnnotation();
          createCovariantMethodReference(
              methodReference, innerAnnotation.value, methodReferenceMap);
        }
      }
    }

    public void visitCovariantMethodsForHolder(
        ClassReference reference, Consumer<MethodReferenceWithApiLevel> consumer) {
      List<MethodReferenceWithApiLevel> methodReferences = methodReferenceMap.get(reference);
      if (methodReferences != null) {
        methodReferences.stream()
            .sorted(
                Comparator.comparing(
                    MethodReferenceWithApiLevel::getMethodReference,
                    MethodReferenceUtils.getMethodReferenceComparator()))
            .forEach(consumer);
      }
    }
  }

  public static class MethodReferenceWithApiLevel {

    private final MethodReference methodReference;
    private final AndroidApiLevel apiLevel;

    private MethodReferenceWithApiLevel(MethodReference methodReference, AndroidApiLevel apiLevel) {
      this.methodReference = methodReference;
      this.apiLevel = apiLevel;
    }

    public MethodReference getMethodReference() {
      return methodReference;
    }

    public AndroidApiLevel getApiLevel() {
      return apiLevel;
    }
  }
}
