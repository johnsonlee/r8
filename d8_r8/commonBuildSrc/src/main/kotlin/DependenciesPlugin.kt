// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

import DependenciesPlugin.Companion.computeRoot
import java.io.File
import java.nio.file.Paths
import java.security.MessageDigest
import java.util.UUID
import kotlin.reflect.full.declaredMemberProperties
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.RegularFile
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.tasks.Jar
import org.gradle.jvm.toolchain.JavaInstallationMetadata
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaLauncher
import org.gradle.jvm.toolchain.internal.DefaultJavaLanguageVersion
import org.gradle.kotlin.dsl.register
import org.gradle.nativeplatform.platform.OperatingSystem
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform

class DependenciesPlugin : Plugin<Project> {

  override fun apply(target: Project) {
    // Setup all test tasks to listen after system properties passed in by test.py.
    val testTask = target.tasks.findByName("test") as Test?
    testTask?.configure(isR8Lib = false, r8Jar = null, r8LibMappingFile = null)
  }

  companion object {
    fun computeRoot(file: File): File {
      var parent = file
      while (!parent.getName().equals("d8_r8")) {
        parent = parent.getParentFile()
      }
      return parent.getParentFile()
    }
  }
}

enum class Jdk(val folder: String, val version: Int) {
  // Only include LTS and latest non-LTS GA.
  JDK_8("jdk8", 8),
  JDK_9("openjdk-9.0.4", 9), // JDK-9 not LTS, but still used.
  JDK_11("jdk-11", 11),
  JDK_17("jdk-17", 17),
  JDK_21("jdk-21", 21),
  JDK_24("jdk-24", 24);

  fun isJdk8(): Boolean {
    return this == JDK_8
  }

  fun getThirdPartyDependency(): ThirdPartyDependency {
    val os: OperatingSystem = DefaultNativePlatform.getCurrentOperatingSystem()
    val subFolder: String
    if (os.isLinux) {
      subFolder = if (isJdk8()) "linux-x86" else "linux"
    } else if (os.isMacOsX) {
      subFolder = if (isJdk8()) "darwin-x86" else "osx"
    } else {
      assert(os.isWindows())
      if (isJdk8()) {
        throw RuntimeException("No Jdk8 on Windows")
      }
      subFolder = "windows"
    }
    return ThirdPartyDependency(
      name,
      Paths.get("third_party", "openjdk", folder, subFolder).toFile(),
      Paths.get("third_party", "openjdk", folder, "$subFolder.tar.gz.sha1").toFile(),
    )
  }
}

fun Test.configure(isR8Lib: Boolean, r8Jar: File?, r8LibMappingFile: File? = null) {
  TestConfigurationHelper.setupTestTask(this, isR8Lib, r8Jar, r8LibMappingFile)
}

fun Project.getRoot(): File {
  return computeRoot(this.projectDir)
}

// See https://datatracker.ietf.org/doc/html/rfc4122#section-4.3 for the algorithm.
fun uuid5(namespace: UUID, name: String): UUID? {
  val md = MessageDigest.getInstance("SHA-1")
  md.update(uuidToBytes(namespace))
  md.update(name.encodeToByteArray())
  val sha1Bytes = md.digest()
  // Set version 5 (upper 4 bits of octet 6).
  sha1Bytes[6] = (sha1Bytes[6].toInt() and 0x0f).toByte()
  sha1Bytes[6] = (sha1Bytes[6].toInt() or 0x50).toByte()
  // Set two upper bits of octet 8 to 10.
  sha1Bytes[8] = (sha1Bytes[8].toInt() and 0x3f).toByte()
  sha1Bytes[8] = (sha1Bytes[8].toInt() or 0x80).toByte()
  return uuidFromBytes(sha1Bytes)
}

private fun uuidFromBytes(data: ByteArray): UUID? {
  assert(data.size >= 16)
  return UUID(toNetworkOrder(data, 0), toNetworkOrder(data, 8))
}

private fun uuidToBytes(uuid: UUID): ByteArray? {
  val result = ByteArray(16)
  fromNetworkByteOrder(uuid.mostSignificantBits, result, 0)
  fromNetworkByteOrder(uuid.leastSignificantBits, result, 8)
  return result
}

private fun toNetworkOrder(data: ByteArray, dataIndex: Int): Long {
  var result: Long = 0
  for (i in 0..7) result = result shl 8 or (data[dataIndex + i].toInt() and 0xff).toLong()
  return result
}

private fun fromNetworkByteOrder(value: Long, dest: ByteArray, destIndex: Int) {
  for (i in 0..7) dest[i + destIndex] = (value shr (7 - i) * 8 and 0xffL).toByte()
}

fun Project.header(title: String): String {
  return "****** ${title} ******"
}

/**
 * Builds a jar for each sub folder in a test source set.
 *
 * <p> As an example, src/test/examplesJava9 contains subfolders: backport, collectionof, ..., .
 * These are compiled to individual jars and placed in <repo-root>/build/test/examplesJava9/ as:
 * backport.jar, collectionof.jar, ..., .
 *
 * Calling this from a project will amend the task graph with the task named
 * getExamplesJarsTaskName(examplesName) such that it can be referenced from the test runners.
 */
fun Project.buildExampleJars(name: String): Task {
  val jarTasks: MutableList<Task> = mutableListOf()
  val testSourceSet =
    extensions
      .getByType(JavaPluginExtension::class.java)
      .sourceSets
      // The TEST_SOURCE_SET_NAME is the source set defined by writing java { sourcesets.test { ...
      // }}
      .getByName(SourceSet.TEST_SOURCE_SET_NAME)
  val destinationDir = getRoot().resolveAll("build", "test", name)
  val generateDir = getRoot().resolveAll("build", "generated", name)
  val classesOutput = destinationDir.resolve("classes")
  testSourceSet.java.destinationDirectory.set(classesOutput)
  testSourceSet.resources.destinationDirectory.set(destinationDir)
  testSourceSet.java.sourceDirectories.files.forEach { srcDir ->
    srcDir.listFiles(File::isDirectory)?.forEach { exampleDir ->
      arrayOf("compileTestJava", "debuginfo-all", "debuginfo-none").forEach { taskName ->
        if (!project.getTasksByName(taskName, false).isEmpty()) {
          var generationTask: Task? = null
          val compileOutput = getOutputName(classesOutput.toString(), taskName)
          if (exampleDir.resolve("TestGenerator.java").isFile) {
            val generatedOutput =
              Paths.get(getOutputName(generateDir.toString(), taskName), exampleDir.name).toString()
            generationTask =
              tasks
                .register<JavaExec>("generate-$name-${exampleDir.name}-$taskName") {
                  dependsOn(taskName)
                  mainClass.set("${exampleDir.name}.TestGenerator")
                  classpath = files(compileOutput, testSourceSet.compileClasspath)
                  args(compileOutput, generatedOutput)
                  outputs.dirs(generatedOutput)
                }
                .get()
          }
          jarTasks.add(
            tasks
              .register<Jar>("jar-$name-${exampleDir.name}-$taskName") {
                dependsOn(taskName)
                archiveFileName.set("${getOutputName(exampleDir.name, taskName)}.jar")
                destinationDirectory.set(destinationDir)
                duplicatesStrategy = DuplicatesStrategy.EXCLUDE
                if (generationTask != null) {
                  // If a generation task exists, we first take the generated output and add to the
                  // current jar. Running with DuplicatesStrategy.EXCLUDE ensure that we do not
                  // overwrite with the non-generated file.
                  dependsOn(generationTask)
                  from(generationTask.outputs.files.singleFile.parentFile) {
                    include("${exampleDir.name}/**/*.class")
                    exclude("**/TestGenerator*")
                  }
                }
                from(compileOutput) {
                  include("${exampleDir.name}/**/*.class")
                  exclude("**/TestGenerator*")
                  exclude("${exampleDir.name}/twr/**")
                }
                // Copy additional resources into the jar.
                from(exampleDir) {
                  exclude("**/*.java")
                  exclude("**/keep-rules*.txt")
                  into(exampleDir.name)
                }
              }
              .get()
          )
        }
      }
    }
  }
  return tasks.register(getExampleJarsTaskName(name)) { dependsOn(jarTasks.toTypedArray()) }.get()
}

fun getOutputName(dest: String, taskName: String): String {
  if (taskName.equals("compileTestJava")) {
    return dest
  }
  return "${dest}_${taskName.replace('-', '_')}"
}

fun Project.getExampleJarsTaskName(name: String): String {
  return "build-example-jars-$name"
}

fun Project.resolve(
  thirdPartyDependency: ThirdPartyDependency,
  vararg paths: String,
): ConfigurableFileCollection {
  return files(project.getRoot().resolve(thirdPartyDependency.path).resolveAll(*paths))
}

/**
 * When using composite builds, referecing tasks in other projects do not give a Task but a
 * TaskReference. To get outputs from other tasks we need to have a proper task and gradle do not
 * provide a way of getting a Task from a TaskReference. We use a trick where create a synthetic
 * task that depends on the task of interest, allowing us to look at the graph and obtain the actual
 * reference. Remove this code if gradle starts supporting this natively.
 */
fun Project.projectTask(project: String, taskName: String): Task {
  val name = "$project-reference-$taskName"
  val task =
    tasks.register(name) { dependsOn(gradle.includedBuild(project).task(":$taskName")) }.get()
  return task.taskDependencies.getDependencies(tasks.getByName(name)).iterator().next()
}

fun File.resolveAll(vararg xs: String): File {
  var that = this
  for (x in xs) {
    that = that.resolve(x)
  }
  return that
}

fun Project.getJavaHome(jdk: Jdk): File {
  val os: OperatingSystem = DefaultNativePlatform.getCurrentOperatingSystem()
  var osFolder = "linux"
  if (os.isWindows()) {
    osFolder = "windows"
  }
  if (os.isMacOsX) {
    osFolder = "osx/Contents/Home"
  }
  return getRoot().resolveAll("third_party", "openjdk", jdk.folder, osFolder)
}

fun Project.getCompilerPath(jdk: Jdk): String {
  val os: OperatingSystem = DefaultNativePlatform.getCurrentOperatingSystem()
  val binary = if (os.isWindows()) "javac.exe" else "javac"
  return getJavaHome(jdk).resolveAll("bin", binary).toString()
}

fun Project.getJavaPath(jdk: Jdk): String {
  val os: OperatingSystem = DefaultNativePlatform.getCurrentOperatingSystem()
  val binary = if (os.isWindows()) "java.exe" else "java"
  return getJavaHome(jdk).resolveAll("bin", binary).toString()
}

fun Project.getJavaLauncher(jdk: Jdk): JavaLauncher {
  return object : JavaLauncher {
    override fun getMetadata(): JavaInstallationMetadata {
      return object : JavaInstallationMetadata {
        override fun getLanguageVersion(): JavaLanguageVersion {
          return DefaultJavaLanguageVersion.of(jdk.version)
        }

        override fun getJavaRuntimeVersion(): String {
          return jdk.name
        }

        override fun getJvmVersion(): String {
          return jdk.name
        }

        override fun getVendor(): String {
          return "vendor"
        }

        override fun getInstallationPath(): Directory {
          return project.layout.projectDirectory.dir(getJavaHome(jdk).toString())
        }

        override fun isCurrentJvm(): Boolean {
          return false
        }
      }
    }

    override fun getExecutablePath(): RegularFile {
      return project.layout.projectDirectory.file(getJavaPath(jdk))
    }
  }
}

fun Project.getClasspath(vararg paths: File): String {
  val os: OperatingSystem = DefaultNativePlatform.getCurrentOperatingSystem()
  assert(!paths.isEmpty())
  val separator = if (os.isWindows()) ";" else ":"
  var classpath = paths.joinToString(separator = separator) { it -> it.toString() }
  return classpath
}

fun Project.baseCompilerCommandLine(
  jar: File,
  deps: File,
  compiler: String,
  args: List<String> = listOf(),
): List<String> {
  // Execute r8 commands against a stable r8 with dependencies.
  // TODO(b/139725780): See if we can remove or lower the heap size (-Xmx8g).
  return listOf(
    getJavaPath(Jdk.JDK_17),
    "-Xmx8g",
    "-ea",
    "-cp",
    getClasspath(jar, deps),
    "com.android.tools.r8.SwissArmyKnife",
    compiler,
  ) + args
}

fun Project.baseCompilerCommandLine(
  jvmArgs: List<String> = listOf(),
  jar: File,
  compiler: String,
  args: List<String> = listOf(),
): List<String> {
  // Execute r8 commands against a stable r8 with dependencies.
  // TODO(b/139725780): See if we can remove or lower the heap size (-Xmx8g).
  return listOf(getJavaPath(Jdk.JDK_17), "-Xmx8g", "-ea") +
    jvmArgs +
    listOf("-cp", "$jar", "com.android.tools.r8.SwissArmyKnife", compiler) +
    args
}

fun Project.baseCompilerCommandLine(
  jar: File,
  compiler: String,
  args: List<String> = listOf(),
): List<String> {
  // Execute r8 commands against a stable r8 with dependencies.
  // TODO(b/139725780): See if we can remove or lower the heap size (-Xmx8g).
  return listOf(
    getJavaPath(Jdk.JDK_17),
    "-Xmx8g",
    "-ea",
    "-cp",
    "$jar",
    "com.android.tools.r8.SwissArmyKnife",
    compiler,
  ) + args
}

fun Project.createR8LibCommandLine(
  r8Compiler: File,
  input: File,
  output: File,
  pgConf: List<File>,
  excludingDepsVariant: Boolean,
  debugVariant: Boolean,
  lib: List<File> = listOf(),
  classpath: List<File> = listOf(),
  pgInputMap: File? = null,
  replaceFromJar: File? = null,
  versionJar: File? = null,
): List<String> {
  return buildList {
    add("python3")
    add("${getRoot().resolve("tools").resolve("create_r8lib.py")}")
    add("--r8compiler")
    add("${r8Compiler}")
    add("--r8jar")
    add("${input}")
    add("--output")
    add("${output}")
    pgConf.forEach {
      add("--pg-conf")
      add("$it")
    }
    lib.forEach {
      add("--lib")
      add("$it")
    }
    classpath.forEach {
      add("--classpath")
      add("$it")
    }
    if (excludingDepsVariant) {
      add("--excldeps-variant")
    }
    if (debugVariant) {
      add("--debug-variant")
    }
    if (pgInputMap != null) {
      add("--pg-map")
      add("$pgInputMap")
    }
    if (replaceFromJar != null) {
      add("--replace-from-jar")
      add("$replaceFromJar")
    }
    if (versionJar != null) {
      add("--r8-version-jar")
      add("$versionJar")
    }
  }
}

object JvmCompatibility {
  val sourceCompatibility = JavaVersion.VERSION_11
  val targetCompatibility = JavaVersion.VERSION_11
  val release = 11
}

object Versions {
  const val asmVersion = "9.8"
  const val errorproneVersion = "2.18.0"
  const val fastUtilVersion = "7.2.1"
  const val gsonVersion = "2.10.1"
  const val guavaVersion = "32.1.2-jre"
  const val javassist = "3.29.2-GA"
  const val junitVersion = "4.13-beta-2"
  const val kotlinVersion = "1.9.20"
  const val kotlinMetadataVersion = "2.1.0"
  const val mockito = "2.10.0"
  const val smaliVersion = "3.0.3"
  const val protobufVersion = "3.19.3"
}

object Deps {
  val asm by lazy { "org.ow2.asm:asm:${Versions.asmVersion}" }
  val asmUtil by lazy { "org.ow2.asm:asm-util:${Versions.asmVersion}" }
  val asmCommons by lazy { "org.ow2.asm:asm-commons:${Versions.asmVersion}" }
  val errorprone by lazy { "com.google.errorprone:error_prone_core:${Versions.errorproneVersion}" }
  val fastUtil by lazy { "it.unimi.dsi:fastutil:${Versions.fastUtilVersion}" }
  val gson by lazy { "com.google.code.gson:gson:${Versions.gsonVersion}" }
  val guava by lazy { "com.google.guava:guava:${Versions.guavaVersion}" }
  val javassist by lazy { "org.javassist:javassist:${Versions.javassist}" }
  val junit by lazy { "junit:junit:${Versions.junitVersion}" }
  val kotlinMetadata by lazy {
    "org.jetbrains.kotlin:kotlin-metadata-jvm:${Versions.kotlinMetadataVersion}"
  }
  val kotlinStdLib by lazy { "org.jetbrains.kotlin:kotlin-stdlib:${Versions.kotlinVersion}" }
  val kotlinReflect by lazy { "org.jetbrains.kotlin:kotlin-reflect:${Versions.kotlinVersion}" }
  val mockito by lazy { "org.mockito:mockito-core:${Versions.mockito}" }
  val smali by lazy { "com.android.tools.smali:smali:${Versions.smaliVersion}" }
  val smaliUtil by lazy { "com.android.tools.smali:smali-util:${Versions.smaliVersion}" }
  val protobuf by lazy { "com.google.protobuf:protobuf-java:${Versions.protobufVersion}" }
}

object ThirdPartyDeps {
  val aapt2 =
    ThirdPartyDependency(
      "aapt2",
      Paths.get("third_party", "aapt2").toFile(),
      Paths.get("third_party", "aapt2.tar.gz.sha1").toFile(),
    )
  val agsa =
    ThirdPartyDependency(
      "agsa",
      Paths.get("third_party", "closedsource-apps", "agsa", "20250412-v16.14.47").toFile(),
      Paths.get("third_party", "closedsource-apps", "agsa", "20250412-v16.14.47.tar.gz.sha1")
        .toFile(),
      testOnly = true,
      type = DependencyType.X20,
    )
  val androidJars = getThirdPartyAndroidJars()
  val androidVMs = getThirdPartyAndroidVms()
  val apiDatabase =
    ThirdPartyDependency(
      "apiDatabase",
      Paths.get("third_party", "api_database", "api_database").toFile(),
      Paths.get("third_party", "api_database", "api_database.tar.gz.sha1").toFile(),
    )
  val artTests =
    ThirdPartyDependency(
      "art-tests",
      Paths.get("tests", "2017-10-04", "art").toFile(),
      Paths.get("tests", "2017-10-04", "art.tar.gz.sha1").toFile(),
    )
  val artTestsLegacy =
    ThirdPartyDependency(
      "art-tests-legacy",
      Paths.get("tests", "2016-12-19", "art").toFile(),
      Paths.get("tests", "2016-12-19", "art.tar.gz.sha1").toFile(),
    )
  val clank =
    ThirdPartyDependency(
      "clank",
      Paths.get("third_party", "chrome", "clank_google3_prebuilt").toFile(),
      Paths.get("third_party", "chrome", "clank_google3_prebuilt.tar.gz.sha1").toFile(),
      testOnly = true,
      type = DependencyType.X20,
    )
  val chrome =
    ThirdPartyDependency(
      "chrome",
      Paths.get("third_party", "chrome", "chrome_200430").toFile(),
      Paths.get("third_party", "chrome", "chrome_200430.tar.gz.sha1").toFile(),
      testOnly = true,
      type = DependencyType.X20,
    )
  val chromeBenchmark =
    ThirdPartyDependency(
      "chrome-benchmark",
      Paths.get("third_party", "opensource-apps", "chrome").toFile(),
      Paths.get("third_party", "opensource-apps", "chrome.tar.gz.sha1").toFile(),
    )
  val compilerApi =
    ThirdPartyDependency(
      "compiler-api",
      Paths.get("third_party", "binary_compatibility_tests", "compiler_api_tests").toFile(),
      Paths.get("third_party", "binary_compatibility_tests", "compiler_api_tests.tar.gz.sha1")
        .toFile(),
    )
  val composeExamplesChangedBitwiseValuePropagation =
    ThirdPartyDependency(
      "compose-examples-changed-bitwise-value-propagation",
      Paths.get(
          "third_party",
          "opensource-apps",
          "compose-examples",
          "changed-bitwise-value-propagation",
        )
        .toFile(),
      Paths.get(
          "third_party",
          "opensource-apps",
          "compose-examples",
          "changed-bitwise-value-propagation.tar.gz.sha1",
        )
        .toFile(),
    )
  val composeSamplesCrane =
    ThirdPartyDependency(
      "compose-samples-crane",
      Paths.get("third_party", "opensource-apps", "android", "compose-samples", "crane").toFile(),
      Paths.get("third_party", "opensource-apps", "android", "compose-samples", "crane.tar.gz.sha1")
        .toFile(),
    )
  val composeSamplesJetCaster =
    ThirdPartyDependency(
      "compose-samples-jetcaster",
      Paths.get("third_party", "opensource-apps", "android", "compose-samples", "jetcaster")
        .toFile(),
      Paths.get(
          "third_party",
          "opensource-apps",
          "android",
          "compose-samples",
          "jetcaster.tar.gz.sha1",
        )
        .toFile(),
    )
  val composeSamplesJetChat =
    ThirdPartyDependency(
      "compose-samples-jetchat",
      Paths.get("third_party", "opensource-apps", "android", "compose-samples", "jetchat").toFile(),
      Paths.get(
          "third_party",
          "opensource-apps",
          "android",
          "compose-samples",
          "jetchat.tar.gz.sha1",
        )
        .toFile(),
    )
  val composeSamplesJetLagged =
    ThirdPartyDependency(
      "compose-samples-jetlagged",
      Paths.get("third_party", "opensource-apps", "android", "compose-samples", "jetlagged")
        .toFile(),
      Paths.get(
          "third_party",
          "opensource-apps",
          "android",
          "compose-samples",
          "jetlagged.tar.gz.sha1",
        )
        .toFile(),
    )
  val composeSamplesJetNews =
    ThirdPartyDependency(
      "compose-samples-jetnews",
      Paths.get("third_party", "opensource-apps", "android", "compose-samples", "jetnews").toFile(),
      Paths.get(
          "third_party",
          "opensource-apps",
          "android",
          "compose-samples",
          "jetnews.tar.gz.sha1",
        )
        .toFile(),
    )
  val composeSamplesJetSnack =
    ThirdPartyDependency(
      "compose-samples-jetsnack",
      Paths.get("third_party", "opensource-apps", "android", "compose-samples", "jetsnack")
        .toFile(),
      Paths.get(
          "third_party",
          "opensource-apps",
          "android",
          "compose-samples",
          "jetsnack.tar.gz.sha1",
        )
        .toFile(),
    )
  val composeSamplesOwl =
    ThirdPartyDependency(
      "compose-samples-owl",
      Paths.get("third_party", "opensource-apps", "android", "compose-samples", "owl").toFile(),
      Paths.get("third_party", "opensource-apps", "android", "compose-samples", "owl.tar.gz.sha1")
        .toFile(),
    )
  val composeSamplesReply =
    ThirdPartyDependency(
      "compose-samples-reply",
      Paths.get("third_party", "opensource-apps", "android", "compose-samples", "reply").toFile(),
      Paths.get("third_party", "opensource-apps", "android", "compose-samples", "reply.tar.gz.sha1")
        .toFile(),
    )
  val coreLambdaStubs =
    ThirdPartyDependency(
      "coreLambdaStubs",
      Paths.get("third_party", "core-lambda-stubs").toFile(),
      Paths.get("third_party", "core-lambda-stubs.tar.gz.sha1").toFile(),
    )
  val customConversion =
    ThirdPartyDependency(
      "customConversion",
      Paths.get("third_party", "openjdk", "custom_conversion").toFile(),
      Paths.get("third_party", "openjdk", "custom_conversion.tar.gz.sha1").toFile(),
    )
  val dagger =
    ThirdPartyDependency(
      "dagger",
      Paths.get("third_party", "dagger", "2.41").toFile(),
      Paths.get("third_party", "dagger", "2.41.tar.gz.sha1").toFile(),
    )
  val dartSdk =
    ThirdPartyDependency(
      "dart-sdk",
      Paths.get("third_party", "dart-sdk").toFile(),
      Paths.get("third_party", "dart-sdk.tar.gz.sha1").toFile(),
    )
  val ddmLib =
    ThirdPartyDependency(
      "ddmlib",
      Paths.get("third_party", "ddmlib").toFile(),
      Paths.get("third_party", "ddmlib.tar.gz.sha1").toFile(),
    )
  val examples =
    ThirdPartyDependency(
      "examples",
      Paths.get("third_party", "examples").toFile(),
      Paths.get("third_party", "examples.tar.gz.sha1").toFile(),
    )
  val examplesAndroidN =
    ThirdPartyDependency(
      "examplesAndroidN",
      Paths.get("third_party", "examplesAndroidN").toFile(),
      Paths.get("third_party", "examplesAndroidN.tar.gz.sha1").toFile(),
    )
  val examplesAndroidO =
    ThirdPartyDependency(
      "examplesAndroidO",
      Paths.get("third_party", "examplesAndroidO").toFile(),
      Paths.get("third_party", "examplesAndroidO.tar.gz.sha1").toFile(),
    )
  val examplesAndroidOGenerated =
    ThirdPartyDependency(
      "examplesAndroidOGenerated",
      Paths.get("third_party", "examplesAndroidOGenerated").toFile(),
      Paths.get("third_party", "examplesAndroidOGenerated.tar.gz.sha1").toFile(),
    )
  val examplesAndroidOLegacy =
    ThirdPartyDependency(
      "examplesAndroidOLegacy",
      Paths.get("third_party", "examplesAndroidOLegacy").toFile(),
      Paths.get("third_party", "examplesAndroidOLegacy.tar.gz.sha1").toFile(),
    )
  val examplesAndroidP =
    ThirdPartyDependency(
      "examplesAndroidP",
      Paths.get("third_party", "examplesAndroidP").toFile(),
      Paths.get("third_party", "examplesAndroidP.tar.gz.sha1").toFile(),
    )
  val examplesAndroidPGenerated =
    ThirdPartyDependency(
      "examplesAndroidPGenerated",
      Paths.get("third_party", "examplesAndroidPGenerated").toFile(),
      Paths.get("third_party", "examplesAndroidPGenerated.tar.gz.sha1").toFile(),
    )
  val desugarJdkLibs =
    ThirdPartyDependency(
      "desugar-jdk-libs",
      Paths.get("third_party", "openjdk", "desugar_jdk_libs").toFile(),
      Paths.get("third_party", "openjdk", "desugar_jdk_libs.tar.gz.sha1").toFile(),
    )
  val desugarJdkLibsLegacy =
    ThirdPartyDependency(
      "desugar-jdk-libs-legacy",
      Paths.get("third_party", "openjdk", "desugar_jdk_libs_legacy").toFile(),
      Paths.get("third_party", "openjdk", "desugar_jdk_libs_legacy.tar.gz.sha1").toFile(),
    )
  val desugarLibraryReleases = getThirdPartyDesugarLibraryReleases()
  // TODO(b/289363570): This could probably be removed.
  val framework =
    ThirdPartyDependency(
      "framework",
      Paths.get("third_party", "framework").toFile(),
      Paths.get("third_party", "framework.tar.gz.sha1").toFile(),
      testOnly = true,
      type = DependencyType.X20,
    )
  val googleJavaFormat =
    ThirdPartyDependency(
      "google-java-format",
      Paths.get("third_party", "google-java-format").toFile(),
      Paths.get("third_party", "google-java-format.tar.gz.sha1").toFile(),
    )
  val googleJavaFormat_1_24 =
    ThirdPartyDependency(
      "google-java-format-1.24",
      Paths.get("third_party", "google", "google-java-format", "1.24.0").toFile(),
      Paths.get("third_party", "google", "google-java-format", "1.24.0.tar.gz.sha1").toFile(),
    )
  val googleKotlinFormat_0_54 =
    ThirdPartyDependency(
      "google-kotlin-format-0.54",
      Paths.get("third_party", "google", "google-kotlin-format", "0.54").toFile(),
      Paths.get("third_party", "google", "google-kotlin-format", "0.54.tar.gz.sha1").toFile(),
    )
  val googleYapf_20231013 =
    ThirdPartyDependency(
      "google-yapf-20231013",
      Paths.get("third_party", "google", "yapf", "20231013").toFile(),
      Paths.get("third_party", "google", "yapf", "20231013.tar.gz.sha1").toFile(),
    )
  val gson =
    ThirdPartyDependency(
      "gson",
      Paths.get("third_party", "gson", "gson-2.10.1").toFile(),
      Paths.get("third_party", "gson", "gson-2.10.1.tar.gz.sha1").toFile(),
    )
  val guavaJre =
    ThirdPartyDependency(
      "guava-jre",
      Paths.get("third_party", "guava", "guava-32.1.2-jre").toFile(),
      Paths.get("third_party", "guava", "guava-32.1.2-jre.tar.gz.sha1").toFile(),
    )
  val desugarJdkLibs11 =
    ThirdPartyDependency(
      "desugar-jdk-libs-11",
      Paths.get("third_party", "openjdk", "desugar_jdk_libs_11").toFile(),
      Paths.get("third_party", "openjdk", "desugar_jdk_libs_11.tar.gz.sha1").toFile(),
    )
  val gmscoreVersions = getGmsCoreVersions()
  val internalIssues = getInternalIssues()
  val jacoco =
    ThirdPartyDependency(
      "jacoco",
      Paths.get("third_party", "jacoco", "0.8.6").toFile(),
      Paths.get("third_party", "jacoco", "0.8.6.tar.gz.sha1").toFile(),
    )
  val jasmin =
    ThirdPartyDependency(
      "jasmin",
      Paths.get("third_party", "jasmin").toFile(),
      Paths.get("third_party", "jasmin.tar.gz.sha1").toFile(),
    )
  val jsr223 =
    ThirdPartyDependency(
      "jsr223",
      Paths.get("third_party", "jsr223-api-1.0").toFile(),
      Paths.get("third_party", "jsr223-api-1.0.tar.gz.sha1").toFile(),
    )
  val java8Runtime =
    ThirdPartyDependency(
      "openjdk-rt-1.8",
      Paths.get("third_party", "openjdk", "openjdk-rt-1.8").toFile(),
      Paths.get("third_party", "openjdk", "openjdk-rt-1.8.tar.gz.sha1").toFile(),
    )
  val jdks = getJdks()
  val jdk11Test =
    ThirdPartyDependency(
      "jdk-11-test",
      Paths.get("third_party", "openjdk", "jdk-11-test").toFile(),
      Paths.get("third_party", "openjdk", "jdk-11-test.tar.gz.sha1").toFile(),
    )
  val junit =
    ThirdPartyDependency(
      "junit",
      Paths.get("third_party", "junit").toFile(),
      Paths.get("third_party", "junit.tar.gz.sha1").toFile(),
    )
  val jdwpTests =
    ThirdPartyDependency(
      "jdwp-tests",
      Paths.get("third_party", "jdwp-tests").toFile(),
      Paths.get("third_party", "jdwp-tests.tar.gz.sha1").toFile(),
    )
  val kotlinCompilers = getThirdPartyKotlinCompilers()
  val kotlinR8TestResources =
    ThirdPartyDependency(
      "kotlinR8TestResources",
      Paths.get("third_party", "kotlinR8TestResources").toFile(),
      Paths.get("third_party", "kotlinR8TestResources.tar.gz.sha1").toFile(),
    )
  val kotlinxCoroutines =
    ThirdPartyDependency(
      "kotlinx-coroutines-1.3.6",
      Paths.get("third_party", "kotlinx-coroutines-1.3.6").toFile(),
      Paths.get("third_party", "kotlinx-coroutines-1.3.6.tar.gz.sha1").toFile(),
    )
  val multidex =
    ThirdPartyDependency(
      "multidex",
      Paths.get("third_party", "multidex").toFile(),
      Paths.get("third_party", "multidex.tar.gz.sha1").toFile(),
    )
  val nest =
    ThirdPartyDependency(
      "nest",
      Paths.get("third_party", "nest", "nest_20180926_7c6cfb").toFile(),
      Paths.get("third_party", "nest", "nest_20180926_7c6cfb.tar.gz.sha1").toFile(),
      testOnly = true,
      type = DependencyType.X20,
    )
  val nowinandroid =
    ThirdPartyDependency(
      "nowinandroid",
      Paths.get("third_party", "opensource-apps", "android", "nowinandroid").toFile(),
      Paths.get("third_party", "opensource-apps", "android", "nowinandroid.tar.gz.sha1").toFile(),
    )
  val proguards = getThirdPartyProguards()
  val proguardsettings =
    ThirdPartyDependency(
      "proguardsettings",
      Paths.get("third_party", "proguardsettings").toFile(),
      Paths.get("third_party", "proguardsettings.tar.gz.sha1").toFile(),
      testOnly = true,
      type = DependencyType.X20,
    )
  val protoc =
    ThirdPartyDependency(
      "protoc",
      Paths.get("third_party", "protoc").toFile(),
      Paths.get("third_party", "protoc.tar.gz.sha1").toFile(),
    )
  val protoRuntimeEdition2023 =
    ThirdPartyDependency(
      "protoRuntimeEdition2023",
      Paths.get("third_party", "proto", "runtime", "edition2023").toFile(),
      Paths.get("third_party", "proto", "runtime", "edition2023.tar.gz.sha1").toFile(),
      testOnly = true,
      type = DependencyType.X20,
    )
  val protoRuntimeLegacy =
    ThirdPartyDependency(
      "protoRuntimeLegacy",
      Paths.get("third_party", "proto", "runtime", "legacy").toFile(),
      Paths.get("third_party", "proto", "runtime", "legacy.tar.gz.sha1").toFile(),
      testOnly = true,
      type = DependencyType.X20,
    )
  val protoTestEdition2023 =
    ThirdPartyDependency(
      "protoTestEdition2023",
      Paths.get("third_party", "proto", "test", "edition2023").toFile(),
      Paths.get("third_party", "proto", "test", "edition2023.tar.gz.sha1").toFile(),
      testOnly = true,
      type = DependencyType.X20,
    )
  val protoTestProto2 =
    ThirdPartyDependency(
      "protoTestProto2",
      Paths.get("third_party", "proto", "test", "proto2").toFile(),
      Paths.get("third_party", "proto", "test", "proto2.tar.gz.sha1").toFile(),
      testOnly = true,
      type = DependencyType.X20,
    )
  val protoTestProto3 =
    ThirdPartyDependency(
      "protoTestProto3",
      Paths.get("third_party", "proto", "test", "proto3").toFile(),
      Paths.get("third_party", "proto", "test", "proto3.tar.gz.sha1").toFile(),
      testOnly = true,
      type = DependencyType.X20,
    )
  val r8 =
    ThirdPartyDependency(
      "r8",
      Paths.get("third_party", "r8").toFile(),
      Paths.get("third_party", "r8.tar.gz.sha1").toFile(),
    )
  val r8Mappings =
    ThirdPartyDependency(
      "r8-mappings",
      Paths.get("third_party", "r8mappings").toFile(),
      Paths.get("third_party", "r8mappings.tar.gz.sha1").toFile(),
    )
  val r8v2_0_74 =
    ThirdPartyDependency(
      "r8-v2-0-74",
      Paths.get("third_party", "r8-releases", "2.0.74").toFile(),
      Paths.get("third_party", "r8-releases", "2.0.74.tar.gz.sha1").toFile(),
    )
  val r8v3_2_54 =
    ThirdPartyDependency(
      "r8-v3-2-54",
      Paths.get("third_party", "r8-releases", "3.2.54").toFile(),
      Paths.get("third_party", "r8-releases", "3.2.54.tar.gz.sha1").toFile(),
    )
  val r8v8_0_46 =
    ThirdPartyDependency(
      "r8-v8-0-46",
      Paths.get("third_party", "r8-releases", "8.0.46").toFile(),
      Paths.get("third_party", "r8-releases", "8.0.46.tar.gz.sha1").toFile(),
    )
  val retraceBenchmark =
    ThirdPartyDependency(
      "retrace-benchmark",
      Paths.get("third_party", "retrace_benchmark").toFile(),
      Paths.get("third_party", "retrace_benchmark.tar.gz.sha1").toFile(),
    )
  val retraceBinaryCompatibility =
    ThirdPartyDependency(
      "retrace-binary-compatibility",
      Paths.get("third_party", "retrace", "binary_compatibility").toFile(),
      Paths.get("third_party", "retrace", "binary_compatibility.tar.gz.sha1").toFile(),
    )
  val retracePartitionFormats =
    ThirdPartyDependency(
      "retrace-partition-formats",
      Paths.get("third_party", "retrace", "partition_formats").toFile(),
      Paths.get("third_party", "retrace", "partition_formats.tar.gz.sha1").toFile(),
    )
  val retraceInternal =
    ThirdPartyDependency(
      "retrace-internal",
      Paths.get("third_party", "retrace_internal").toFile(),
      Paths.get("third_party", "retrace_internal.tar.gz.sha1").toFile(),
      testOnly = true,
      type = DependencyType.X20,
    )
  val rhino =
    ThirdPartyDependency(
      "rhino",
      Paths.get("third_party", "rhino-1.7.10").toFile(),
      Paths.get("third_party", "rhino-1.7.10.tar.gz.sha1").toFile(),
    )
  val rhinoAndroid =
    ThirdPartyDependency(
      "rhino-android",
      Paths.get("third_party", "rhino-android-1.1.1").toFile(),
      Paths.get("third_party", "rhino-android-1.1.1.tar.gz.sha1").toFile(),
    )
  val smali =
    ThirdPartyDependency(
      "smali",
      Paths.get("third_party", "smali").toFile(),
      Paths.get("third_party", "smali.tar.gz.sha1").toFile(),
    )
  val systemUI =
    ThirdPartyDependency(
      "systemUI",
      Paths.get("third_party", "closedsource-apps", "systemui").toFile(),
      Paths.get("third_party", "closedsource-apps", "systemui.tar.gz.sha1").toFile(),
      testOnly = true,
      type = DependencyType.X20,
    )
  val tivi =
    ThirdPartyDependency(
      "tivi",
      Paths.get("third_party", "opensource-apps", "tivi").toFile(),
      Paths.get("third_party", "opensource-apps", "tivi.tar.gz.sha1").toFile(),
    )
  val youtube1719 =
    ThirdPartyDependency(
      "youtube-17.19",
      Paths.get("third_party", "youtube", "youtube.android_17.19").toFile(),
      Paths.get("third_party", "youtube", "youtube.android_17.19.tar.gz.sha1").toFile(),
      testOnly = true,
      type = DependencyType.X20,
    )
}

fun getThirdPartyAndroidJars(): List<ThirdPartyDependency> {
  return listOf(
      "libcore_latest",
      "lib-main",
      "lib-v14",
      "lib-v15",
      "lib-v19",
      "lib-v21",
      "lib-v22",
      "lib-v23",
      "lib-v24",
      "lib-v25",
      "lib-v26",
      "lib-v27",
      "lib-v28",
      "lib-v29",
      "lib-v30",
      "lib-v31",
      "lib-v32",
      "lib-v33",
      "lib-v34",
      "lib-v35",
      "lib-v36",
    )
    .map(::getThirdPartyAndroidJar)
}

fun getThirdPartyAndroidJar(version: String): ThirdPartyDependency {
  return ThirdPartyDependency(
    version,
    Paths.get("third_party", "android_jar", version).toFile(),
    Paths.get("third_party", "android_jar", "$version.tar.gz.sha1").toFile(),
  )
}

fun getThirdPartyAndroidVms(): List<ThirdPartyDependency> {
  return listOf(
      listOf("host", "art-master"),
      listOf("host", "art-15.0.0-beta2"),
      listOf("host", "art-14.0.0-beta3"),
      listOf("host", "art-13.0.0"),
      listOf("host", "art-12.0.0-beta4"),
      listOf("art-10.0.0"),
      listOf("art-5.1.1"),
      listOf("art-6.0.1"),
      listOf("art-7.0.0"),
      listOf("art-8.1.0"),
      listOf("art-9.0.0"),
      listOf("art"),
      listOf("dalvik-4.0.4"),
      listOf("dalvik"),
    )
    .map(::getThirdPartyAndroidVm)
}

fun getThirdPartyAndroidVm(version: List<String>): ThirdPartyDependency {
  return ThirdPartyDependency(
    version.last(),
    Paths.get("tools", "linux", *version.slice(0..version.size - 2).toTypedArray(), version.last())
      .toFile(),
    Paths.get(
        "tools",
        "linux",
        *version.slice(0..version.size - 2).toTypedArray(),
        "${version.last()}.tar.gz.sha1",
      )
      .toFile(),
  )
}

fun getJdks(): List<ThirdPartyDependency> {
  val os: OperatingSystem = DefaultNativePlatform.getCurrentOperatingSystem()
  if (os.isLinux || os.isMacOsX) {
    return Jdk.values().map { it.getThirdPartyDependency() }
  } else {
    return Jdk.values().filter { !it.isJdk8() }.map { it.getThirdPartyDependency() }
  }
}

fun getThirdPartyProguards(): List<ThirdPartyDependency> {
  return listOf("proguard-7.0.0", "proguard-7.3.2", "proguard-7.4.1").map {
    ThirdPartyDependency(
      it,
      Paths.get("third_party", "proguard", it).toFile(),
      Paths.get("third_party", "proguard", "${it}.tar.gz.sha1").toFile(),
    )
  }
}

fun getThirdPartyKotlinCompilers(): List<ThirdPartyDependency> {
  return listOf(
      "kotlin-compiler-1.3.72",
      "kotlin-compiler-1.4.20",
      "kotlin-compiler-1.5.0",
      "kotlin-compiler-1.6.0",
      "kotlin-compiler-1.7.0",
      "kotlin-compiler-1.8.0",
      "kotlin-compiler-1.9.21",
      "kotlin-compiler-2.0.20",
      "kotlin-compiler-2.1.10",
      "kotlin-compiler-2.2.0",
      "kotlin-compiler-dev",
    )
    .map {
      ThirdPartyDependency(
        it,
        Paths.get("third_party", "kotlin", it).toFile(),
        Paths.get("third_party", "kotlin", "${it}.tar.gz.sha1").toFile(),
      )
    }
}

fun getThirdPartyDesugarLibraryReleases(): List<ThirdPartyDependency> {
  return listOf("1.0.9", "1.0.10", "1.1.0", "1.1.1", "1.1.5", "2.0.3").map {
    ThirdPartyDependency(
      "desugar-library-release-$it",
      Paths.get("third_party", "openjdk", "desugar_jdk_libs_releases", it).toFile(),
      Paths.get("third_party", "openjdk", "desugar_jdk_libs_releases", "${it}.tar.gz.sha1").toFile(),
    )
  }
}

fun getInternalIssues(): List<ThirdPartyDependency> {
  return listOf("issue-127524985").map {
    ThirdPartyDependency(
      "internal-$it",
      Paths.get("third_party", "internal", it).toFile(),
      Paths.get("third_party", "internal", "${it}.tar.gz.sha1").toFile(),
      testOnly = true,
      type = DependencyType.X20,
    )
  }
}

fun getGmsCoreVersions(): List<ThirdPartyDependency> {
  return listOf("gmscore_v10", "latest").map {
    ThirdPartyDependency(
      "gmscore-version-$it",
      Paths.get("third_party", "gmscore", it).toFile(),
      Paths.get("third_party", "gmscore", "${it}.tar.gz.sha1").toFile(),
      testOnly = true,
      type = DependencyType.X20,
    )
  }
}

private fun Project.allDependencies(): List<ThirdPartyDependency> {
  val allDeps = mutableListOf<ThirdPartyDependency>()
  ThirdPartyDeps::class.declaredMemberProperties.forEach {
    val value = it.get(ThirdPartyDeps)
    if (value is List<*>) {
      allDeps.addAll(value as List<ThirdPartyDependency>)
    } else {
      allDeps.add(value as ThirdPartyDependency)
    }
  }
  return allDeps
}

fun Project.allPublicDependencies(): List<ThirdPartyDependency> {
  return allDependencies().filter { x -> !x.testOnly && x.type == DependencyType.GOOGLE_STORAGE }
}

fun Project.allPublicTestDependencies(): List<ThirdPartyDependency> {
  return allDependencies().filter { x -> x.testOnly && x.type == DependencyType.GOOGLE_STORAGE }
}

fun Project.allInternalDependencies(): List<ThirdPartyDependency> {
  return allDependencies().filter { x -> !x.testOnly && x.type == DependencyType.X20 }
}

fun Project.allInternalTestDependencies(): List<ThirdPartyDependency> {
  return allDependencies().filter { x -> x.testOnly && x.type == DependencyType.X20 }
}

fun extractClassesPaths(prefix: String, vararg paths: String): String {
  val result: MutableList<String> = ArrayList()
  paths.forEach { it ->
    result.addAll(
      it.split(File.pathSeparator).filter { it.contains("${prefix}build${File.separator}classes") }
    )
  }
  return result.joinToString(File.pathSeparator)
}
