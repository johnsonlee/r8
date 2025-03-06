// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static com.android.tools.r8.androidapi.AndroidApiLevelDatabaseHelper.notModeledFields;
import static com.android.tools.r8.androidapi.AndroidApiLevelDatabaseHelper.notModeledMethods;
import static com.android.tools.r8.androidapi.AndroidApiLevelDatabaseHelper.notModeledTypes;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDiagnosticMessagesImpl;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.androidapi.AndroidApiLevelCompute;
import com.android.tools.r8.androidapi.AndroidApiLevelCompute.DefaultAndroidApiLevelCompute;
import com.android.tools.r8.androidapi.AndroidApiLevelHashingDatabaseImpl;
import com.android.tools.r8.apimodel.AndroidApiVersionsXmlParser.ParsedApiClass;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexLibraryClass;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.IntBox;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ZipUtils;
import com.google.common.collect.ImmutableList;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class AndroidApiHashingDatabaseBuilderGeneratorTest extends TestBase {

  protected final TestParameters parameters;
  private static final Path API_DATABASE_FOLDER =
      Paths.get(ToolHelper.THIRD_PARTY_DIR, "api_database");
  private static final Path API_DATABASE =
      API_DATABASE_FOLDER
          .resolve("api_database")
          .resolve("resources")
          .resolve("new_api_database.ser");

  // Update the API_LEVEL below to have the database generated for a new api level.
  private static final AndroidApiLevel API_LEVEL = AndroidApiLevel.API_DATABASE_LEVEL;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public AndroidApiHashingDatabaseBuilderGeneratorTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private static class GenerateDatabaseResourceFilesResult {

    private final Path apiLevels;

    public GenerateDatabaseResourceFilesResult(Path apiLevels) {
      this.apiLevels = apiLevels;
    }
  }

  private static GenerateDatabaseResourceFilesResult generateResourcesFiles() throws Exception {
    return generateResourcesFiles(
        AndroidApiVersionsXmlParser.builder()
            .setApiVersionsXml(ToolHelper.getApiVersionsXmlFile(API_LEVEL))
            .setAndroidJar(ToolHelper.getAndroidJar(API_LEVEL))
            .setApiLevel(API_LEVEL)
            .build()
            .run(),
        API_LEVEL);
  }

  private static GenerateDatabaseResourceFilesResult generateResourcesFiles(
      List<ParsedApiClass> apiClasses, AndroidApiLevel androidJarApiLevel) throws Exception {
    TemporaryFolder temp = new TemporaryFolder();
    temp.create();
    Path apiLevels = temp.newFile("new_api_levels.ser").toPath();
    AndroidApiHashingDatabaseBuilderGenerator.generate(apiClasses, apiLevels, androidJarApiLevel);
    return new GenerateDatabaseResourceFilesResult(apiLevels);
  }

  @Test
  public void testCanParseApiVersionsXml() throws Exception {
    // This tests makes a rudimentary check on the number of classes, fields and methods in
    // api-versions.xml to ensure that the runtime tests do not vacuously succeed.
    List<ParsedApiClass> parsedApiClasses =
        AndroidApiVersionsXmlParser.builder()
            .setApiVersionsXml(ToolHelper.getApiVersionsXmlFile(API_LEVEL))
            .setAndroidJar(ToolHelper.getAndroidJar(API_LEVEL))
            .setApiLevel(API_LEVEL)
            .build()
            .run();
    IntBox numberOfFields = new IntBox(0);
    IntBox numberOfMethods = new IntBox(0);
    parsedApiClasses.forEach(
        apiClass -> {
          apiClass.visitFieldReferences(
              ((apiLevel, fieldReferences) ->
                  fieldReferences.forEach(field -> numberOfFields.increment())));
          apiClass.visitMethodReferences(
              ((AndroidApiLevel apiLevel, List<MethodReference> methodReferences) ->
                  methodReferences.forEach(field -> numberOfMethods.increment())));
        });
    // These numbers will change when updating api-versions.xml
    assertEquals(6222, parsedApiClasses.size());
    assertEquals(31430, numberOfFields.get());
    assertEquals(48025, numberOfMethods.get());
  }

  private static String sampleVersion4ApiVersionsXml =
      "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
          + "<api version=\"4\">\n"
          + "        <sdk id=\"36\" shortname=\"B-ext\" name=\"Baklava Extensions\"\n"
          + "             reference=\"android/os/Build$VERSION_CODES$BAKLAVA\"/>\n"
          + "\n"
          + "        <!-- This class was introduced in Android R -->\n"
          + "        <class name=\"android/os/ext/SdkExtensions\" since=\"30.0\">\n"
          + "                <extends name=\"java/lang/Object\"/>\n"
          + "                <!-- This method was introduced in Android S. It was \"backported\""
          + " to Android R via the R extension,\n"
          + "                     version 2. It also exists in later extensions, including the"
          + " Baklava extension (id 36). -->\n"
          + "                <method name=\"getAllExtensionVersions()Ljava/util/Map;\""
          + " since=\"31.0\"\n"
          + "                        sdks=\"30:2,31:2,33:4,34:7,35:12,36:16,0:31.0\"/>\n"
          + "                <method name=\"getExtensionVersion(I)I\"/>\n"
          + "                <!-- This field was introduced in Android U. It was \"backported\""
          + " to Android R via the R extension,\n"
          + "                     version 4. It also exists in later extensions, including the"
          + " Baklava extension (id 36). -->\n"
          + "                <field name=\"AD_SERVICES\" since=\"34.0\""
          + " sdks=\"30:4,31:4,33:4,34:7,35:12,36:16,0:34.0\"/>\n"
          + "        </class>\n"
          + "\n"
          + "        <!-- This class was introduced in Baklava. It does not exist in any SDK"
          + " extension. -->\n"
          + "        <class name=\"android/os/PlatformOnly\" since=\"36.0\">\n"
          + "                <extends name=\"java/lang/Object\"/>\n"
          + "                <method name=\"foo(I)V\" />\n"
          + "        </class>\n"
          + "</api>\n";

  static class SdkExtensions {
    int AD_SERVICES;
  }

  static class PlatformOnly {}

  private static void mockAndroidJarForSampleVersion4ApiVersionsXml(Path outputPath)
      throws Exception {
    try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(outputPath))) {
      ZipUtils.writeToZipStream(
          out,
          "android/os/ext/SdkExtensions.class",
          transformer(SdkExtensions.class)
              .setClassDescriptor("Landroid/os/ext/SdkExtensions;")
              .transform(),
          ZipEntry.STORED);
      ZipUtils.writeToZipStream(
          out,
          "android/os/PlatformOnly.class",
          transformer(PlatformOnly.class)
              .setClassDescriptor("Landroid/os/PlatformOnly;")
              .transform(),
          ZipEntry.STORED);
    }
  }

  @Test
  public void testApiVersionsXmlVersion4() throws Exception {
    Path apiVersionsXml = temp.newFile("api-versions.xml").toPath();
    FileUtils.writeTextFile(apiVersionsXml, sampleVersion4ApiVersionsXml);
    Path apiLibrary = temp.newFile("android.jar").toPath();
    mockAndroidJarForSampleVersion4ApiVersionsXml(apiLibrary);
    List<ParsedApiClass> parsedApiClasses =
        AndroidApiVersionsXmlParser.builder()
            .setApiVersionsXml(apiVersionsXml)
            .setAndroidJar(apiLibrary)
            .setApiLevel(API_LEVEL)
            .setIgnoreExemptionList(true)
            .build()
            .run();
    assertEquals(2, parsedApiClasses.size());
    assertEquals(
        parsedApiClasses.get(0).getClassReference(),
        Reference.classFromDescriptor("Landroid/os/ext/SdkExtensions;"));
    assertEquals(parsedApiClasses.get(0).getApiLevel(), AndroidApiLevel.R);
    parsedApiClasses
        .get(0)
        .visitMethodReferences(
            (apiLevel, methods) -> {
              if (apiLevel.equals(AndroidApiLevel.R)) {
                assertEquals(
                    methods,
                    ImmutableList.of(
                        Reference.methodFromDescriptor(
                            "Landroid/os/ext/SdkExtensions;", "getExtensionVersion", "(I)I")));
              } else if (apiLevel.equals(AndroidApiLevel.S)) {
                assertEquals(
                    methods,
                    ImmutableList.of(
                        Reference.methodFromDescriptor(
                            "Landroid/os/ext/SdkExtensions;",
                            "getAllExtensionVersions",
                            "()Ljava/util/Map;")));
              } else {
                fail();
              }
            });
    parsedApiClasses
        .get(0)
        .visitFieldReferences(
            (apiLevel, fields) -> {
              if (apiLevel.equals(AndroidApiLevel.U)) {
                assertEquals(
                    fields,
                    ImmutableList.of(
                        Reference.field(
                            Reference.classFromDescriptor("Landroid/os/ext/SdkExtensions;"),
                            "AD_SERVICES",
                            Reference.typeFromDescriptor("I"))));
              } else {
                fail();
              }
            });
    assertEquals(
        parsedApiClasses.get(1).getClassReference(),
        Reference.classFromDescriptor("Landroid/os/PlatformOnly;"));
    assertEquals(parsedApiClasses.get(1).getApiLevel(), AndroidApiLevel.BAKLAVA);
    parsedApiClasses
        .get(1)
        .visitMethodReferences(
            (apiLevel, methods) -> {
              if (apiLevel.equals(AndroidApiLevel.BAKLAVA)) {
                assertEquals(
                    methods,
                    ImmutableList.of(
                        Reference.methodFromDescriptor(
                            "Landroid/os/PlatformOnly;", "foo", "(I)V")));
              } else {
                fail();
              }
            });
    assertEquals(1, parsedApiClasses.get(1).getTotalMemberCount());
  }

  @Test
  public void testDatabaseGenerationUpToDate() throws Exception {
    GenerateDatabaseResourceFilesResult result = generateResourcesFiles();
    assertTrue(TestBase.filesAreEqual(result.apiLevels, API_DATABASE));
  }

  @Test
  public void testAmendedClassesToApiDatabase() throws Exception {
    Path androidJar = ToolHelper.getAndroidJar(API_LEVEL);
    AppView<AppInfoWithClassHierarchy> appView =
        computeAppViewWithClassHierarchy(AndroidApp.builder().addLibraryFile(androidJar).build());
    AndroidApiLevelCompute androidApiLevelCompute = DefaultAndroidApiLevelCompute.create(appView);
    assertTrue(androidApiLevelCompute.isEnabled());
    ensureAllPublicMethodsAreMapped(appView, androidApiLevelCompute);
  }

  private static void ensureAllPublicMethodsAreMapped(
      AppView<AppInfoWithClassHierarchy> appView, AndroidApiLevelCompute apiLevelCompute) {
    Set<String> notModeledTypes = notModeledTypes();
    Set<String> notModeledFields = notModeledFields();
    Set<String> notModeledMethods = notModeledMethods();
    for (DexLibraryClass clazz : appView.app().asDirect().libraryClasses()) {
      if (notModeledTypes.contains(clazz.getClassReference().getTypeName())) {
        continue;
      }
      assertTrue(
          apiLevelCompute
              .computeApiLevelForLibraryReference(clazz.getReference())
              .isKnownApiLevel());
      clazz.forEachClassField(
          field -> {
            if (field.getAccessFlags().isPublic()
                && !field.toSourceString().contains("this$0")
                && !notModeledFields.contains(field.toSourceString())) {
              assertTrue(
                  apiLevelCompute
                      .computeApiLevelForLibraryReference(field.getReference())
                      .isKnownApiLevel());
            }
          });
      clazz.forEachClassMethod(
          method -> {
            if (method.getAccessFlags().isPublic()
                && !notModeledMethods.contains(method.toSourceString())) {
              assertTrue(
                  method.toSourceString()
                      + " not found in API database. Did you forget to run main method in this"
                      + " class to regenerate it?",
                  apiLevelCompute
                      .computeApiLevelForLibraryReference(method.getReference())
                      .isKnownApiLevel());
            }
          });
    }
  }

  @Test
  public void testCanLookUpAllParsedApiClassesAndMembers() throws Exception {
    List<ParsedApiClass> parsedApiClasses =
        AndroidApiVersionsXmlParser.builder()
            .setApiVersionsXml(ToolHelper.getApiVersionsXmlFile(API_LEVEL))
            .setAndroidJar(ToolHelper.getAndroidJar(API_LEVEL))
            .setApiLevel(API_LEVEL)
            .build()
            .run();
    DexItemFactory factory = new DexItemFactory();
    TestDiagnosticMessagesImpl diagnosticsHandler = new TestDiagnosticMessagesImpl();
    AndroidApiLevelHashingDatabaseImpl androidApiLevelDatabase =
        new AndroidApiLevelHashingDatabaseImpl(
            ImmutableList.of(), new InternalOptions(), diagnosticsHandler);
    parsedApiClasses.forEach(
        parsedApiClass -> {
          DexType type = factory.createType(parsedApiClass.getClassReference().getDescriptor());
          AndroidApiLevel apiLevel = androidApiLevelDatabase.getTypeApiLevel(type);
          assertEquals(parsedApiClass.getApiLevel(), apiLevel);
          parsedApiClass.visitMethodReferences(
              (methodApiLevel, methodReferences) ->
                  methodReferences.forEach(
                      methodReference -> {
                        DexMethod method = factory.createMethod(methodReference);
                        AndroidApiLevel androidApiLevel;
                        if (factory.objectMembers.isObjectMember(method)) {
                          androidApiLevel = AndroidApiLevel.B;
                        } else {
                          androidApiLevel = androidApiLevelDatabase.getMethodApiLevel(method);
                        }
                        androidApiLevel.isLessThanOrEqualTo(methodApiLevel);
                      }));
          parsedApiClass.visitFieldReferences(
              (fieldApiLevel, fieldReferences) ->
                  fieldReferences.forEach(
                      fieldReference -> {
                        DexField field = factory.createField(fieldReference);
                        androidApiLevelDatabase
                            .getFieldApiLevel(field)
                            .isLessThanOrEqualTo(fieldApiLevel);
                      }));
        });
    diagnosticsHandler.assertNoMessages();
  }

  /**
   * Main entry point for building a database over references in framework to the api level they
   * were introduced. Running main will generate a new jar and run tests on it to ensure it is
   * compatible with R8 sources and works as expected.
   *
   * <p>The generated jar depends on r8NoManifestWithoutDeps.
   *
   * <p>If the generated jar passes tests it will be moved and overwrite
   * third_party/api_database/new_api_database.ser.
   */
  public static void main(String[] args) throws Exception {
    GenerateDatabaseResourceFilesResult result = generateResourcesFiles();
    API_DATABASE.toFile().mkdirs();
    Files.move(result.apiLevels, API_DATABASE, REPLACE_EXISTING);
    System.out.println(
        "Updated file in: "
            + API_DATABASE
            + "\nRemember to upload to cloud storage:"
            + "\n(cd "
            + API_DATABASE_FOLDER
            + " && upload_to_google_storage.py -a --bucket r8-deps "
            + API_DATABASE_FOLDER.getFileName()
            + ")");
  }
}
