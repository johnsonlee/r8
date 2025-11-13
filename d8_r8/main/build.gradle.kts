// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

import com.google.gson.Gson
import java.io.ByteArrayOutputStream
import java.net.URI
import java.nio.charset.Charset
import java.nio.file.Files.readString
import java.nio.file.Paths
import java.util.UUID
import javax.inject.Inject
import net.ltgt.gradle.errorprone.errorprone
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.api.tasks.bundling.Jar
import org.gradle.process.ExecOperations
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.spdx.sbom.gradle.SpdxSbomTask
import org.spdx.sbom.gradle.extensions.DefaultSpdxSbomTaskExtension

plugins {
  `kotlin-dsl`
  id("dependencies-plugin")
  id("net.ltgt.errorprone") version "3.0.1"
  id("org.spdx.sbom") version "0.4.0"
}

// Properties that you can set in your ~/.gradle/gradle.properties:

// Causes builds to not fail on warnings.
val treatWarningsAsErrors = !project.hasProperty("disable_warnings_as_errors")

// Disable Error Prone checks (can make compiles marginally faster).
var enableErrorProne = !project.hasProperty("disable_errorprone")

// Use a separate sourceSet for files that have been modified when doing incremental builds.
// Speeds up compile times where the list of files isn't changed from 1-2 minutes -> 1-2 seconds.
//
// Modified files are determined using git, and the list of modified files never shrinks (since
// that would cause build errors). However, it is safe to fully reset the list of modified files,
// which you can do by deleting d8_r8/main/build/turbo-paths.txt.
//
// What's the catch?
// Unmodified sources that depend on modified ones will *not be rebuilt* when modified sources
// change. This is where the speed-up comes from, but can lead to runtime crashes if signatures
// change without references to them being updated.
// Be sure to fix problems reported by IntelliJ when using this mode.
var enableTurboBuilds = project.hasProperty("enable_r8_turbo_builds")

val MAIN_JAVA_PATH_PREFIX = "src/main/java/"

interface TurboPathsValueSourceParameters : ValueSourceParameters {
  val pathPrefix: Property<String>
  val turboPathsFile: Property<File>
  val extraGlobs: ListProperty<String>
  val mainOutputDir: Property<File>
}

enum class TurboReason {
  FIRST_BUILD,
  PATHS_CHANGED,
  PATHS_UNCHANGED,
  CORRUPT_FILE,
  TOO_MANY_PATHS,
}

data class TurboState(val paths: List<String>, val reason: TurboReason)

abstract class TurboPathsValueSource : ValueSource<TurboState, TurboPathsValueSourceParameters> {
  @get:Inject abstract val execOperations: ExecOperations

  fun isDirectoryEmpty(path: File): Boolean {
    if (!path.exists()) {
      return true
    }

    val files = path.listFiles()
    return files == null || files.isEmpty()
  }

  override fun obtain(): TurboState? {
    val prefix = parameters.pathPrefix.get()
    val turboPathsFile = parameters.turboPathsFile.get()
    val extraGlobs = parameters.extraGlobs.get()
    val mainOutputDir = parameters.mainOutputDir.get()

    // Check for first build (since the turbo sourceSet requires the main one
    // to have been built already).
    if (isDirectoryEmpty(mainOutputDir)) {
      return TurboState(listOf(), TurboReason.FIRST_BUILD)
    }

    var mergeBase = "origin/main"
    val pathSet: MutableSet<String> = mutableSetOf()

    if (turboPathsFile.exists()) {
      val lines = turboPathsFile.readLines()
      if (!lines.isEmpty() && lines[0].startsWith("mergebase=")) {
        mergeBase = lines[0].removePrefix("mergebase=")
        pathSet.addAll(lines.drop(1))
      } else {
        // Corrupt file.
        turboPathsFile.delete()
        return TurboState(listOf(), TurboReason.CORRUPT_FILE)
      }
    }

    val prevNumSource = pathSet.size
    val output = ByteArrayOutputStream()
    execOperations.exec {
      commandLine = listOf("git", "diff", "--name-only", "--merge-base", mergeBase)
      standardOutput = output
    }
    val result = String(output.toByteArray(), Charset.defaultCharset())
    val gitPaths =
      result
        .lines()
        .filter { it.startsWith(prefix) && it.endsWith(".java") }
        .map { it.trim().removePrefix(prefix) }
    pathSet.addAll(gitPaths)

    val ret = pathSet.toMutableList()
    ret.sort()
    // Allow users to specify extra globs.
    ret += extraGlobs

    if (mergeBase == "origin/main") {
      output.reset()
      execOperations.exec {
        commandLine = listOf("git", "rev-parse", "origin/main")
        standardOutput = output
      }
      mergeBase = String(output.toByteArray(), Charset.defaultCharset()).trim()
    }

    if (pathSet.size > 200 && gitPaths.size < 40) {
      // File has gotten too big. Start fresh.
      turboPathsFile.delete()
      return TurboState(listOf(), TurboReason.TOO_MANY_PATHS)
    }

    turboPathsFile.writeText("mergebase=$mergeBase\n" + ret.joinToString("\n"))
    val changed = prevNumSource != pathSet.size
    val reason =
      if (pathSet.isEmpty()) TurboReason.FIRST_BUILD
      else if (changed) TurboReason.PATHS_CHANGED else TurboReason.PATHS_UNCHANGED
    return TurboState(ret, reason)
  }
}

val turboPathsProvider: Provider<TurboState> =
  providers.of(TurboPathsValueSource::class.java) {
    parameters.pathPrefix.set(MAIN_JAVA_PATH_PREFIX)

    // Wipe this file to remove files from the active set.
    parameters.turboPathsFile.set(layout.buildDirectory.file("turbo-paths.txt").get().asFile)

    parameters.extraGlobs.set(
      project.findProperty("turbo_build_globs")?.toString()?.split(',') ?: emptyList()
    )

    parameters.mainOutputDir.set(sourceSets["main"].java.destinationDirectory.get().getAsFile())
  }

// Add all changed files to the "turbo" source set.
val turboState = if (enableTurboBuilds) turboPathsProvider.get() else null

if (turboState != null) {
  val numFiles = turboState.paths.size
  val msg =
    when (turboState.reason) {
      TurboReason.FIRST_BUILD -> "First build detected. Build will be slow."
      TurboReason.PATHS_CHANGED -> "Paths in active set have changed. Build will be slow."
      TurboReason.PATHS_UNCHANGED -> "Paths unchanged. Size=$numFiles. Build should be fast!"
      TurboReason.CORRUPT_FILE -> "turbo-paths.txt was invalid. Build will be slow."
      TurboReason.TOO_MANY_PATHS -> "Paths were compacted. Build will be slow."
    }
  logger.warn("Turbo: $msg")
} else {
  logger.warn("Turbo: enable_r8_turbo_builds=false")
}

java {
  sourceSets {
    val srcDir = getRoot().resolveAll("src", "main", "java")

    main {
      resources.srcDirs(getRoot().resolveAll("third_party", "api_database", "api_database"))
      java {
        srcDir(srcDir)
        if (turboState != null && !turboState.paths.isEmpty()) {
          exclude(turboState.paths)
        }
      }
    }

    // Must be created unconditionally so that other targets can depend on it.
    create("turbo") {
      java {
        srcDir(srcDir)
        if (turboState != null && !turboState.paths.isEmpty()) {
          include(turboState.paths)
        } else {
          exclude("*")
        }
      }
    }
  }

  sourceCompatibility = JvmCompatibility.sourceCompatibility
  targetCompatibility = JvmCompatibility.targetCompatibility
  toolchain {
    languageVersion = JavaLanguageVersion.of(JvmCompatibility.release)
  }
  withSourcesJar()
}

dependencies {
  implementation(":assistant")
  implementation(":keepanno")
  implementation(":resourceshrinker")
  compileOnly(Deps.androidxCollection)
  compileOnly(Deps.androidxTracingDriver)
  compileOnly(Deps.androidxTracingDriverWire)
  compileOnly(Deps.asm)
  compileOnly(Deps.asmCommons)
  compileOnly(Deps.asmUtil)
  compileOnly(Deps.fastUtil)
  compileOnly(Deps.gson)
  compileOnly(Deps.guava)
  compileOnly(Deps.kotlinMetadata)
  compileOnly(Deps.protobuf)
  errorprone(Deps.errorprone)
}

if (enableTurboBuilds) {
  tasks.named("compileJava") {
    // Makes compileTurboJava run first, but does not cause compileJava to re-run if
    // compileTurboJava changes.
    dependsOn(tasks.named("compileTurboJava"))
  }

  // Does not include main's output directory, which must also be added when compilation avoidance
  // causes only a subset of sources to be recompiled.
  val mainClasspath = sourceSets["main"].compileClasspath.getAsPath()

  tasks.named<JavaCompile>("compileTurboJava") {
    // Add the main's classes to the classpath without letting gradle know about this dependency
    // (as it's a circular one).
    options.compilerArgs.add("-classpath")
    options.compilerArgs.add(
      "" +
        sourceSets["turbo"].java.destinationDirectory.get() +
        File.pathSeparator +
        mainClasspath +
        File.pathSeparator +
        sourceSets["main"].java.destinationDirectory.get()
    )
  }

  tasks.named<JavaCompile>("compileJava") {
    // Add the turbo's classes to the classpath without letting gradle know about this dependency
    // (or else it will cause it to rebuild whenever files in it change).
    options.compilerArgs.add("-classpath")
    options.compilerArgs.add(
      "" +
        sourceSets["main"].java.destinationDirectory.get() +
        File.pathSeparator +
        mainClasspath +
        File.pathSeparator +
        sourceSets["turbo"].java.destinationDirectory.get()
    )
  }
}

if (project.hasProperty("spdxVersion")) {
  project.version = project.property("spdxVersion")!!
}

spdxSbom {
  targets {
    create("r8") {
      // Use of both compileClasspath and runtimeClasspath due to how the
      // dependencies jar is built and dependencies above therefore use
      // compileOnly for actual runtime dependencies.
      configurations.set(listOf("compileClasspath", "runtimeClasspath"))
      scm {
        uri.set("https://r8.googlesource.com/r8/")
        if (project.hasProperty("spdxRevision")) {
          revision.set(project.property("spdxRevision").toString())
        }
      }
      document {
        name.set("R8 Compiler Suite")
        // Generate version 5 UUID from fixed namespace UUID and name generated from revision
        // (git hash) and artifact name.
        if (project.hasProperty("spdxRevision")) {
          namespace.set(
            "https://spdx.google/"
              + uuid5(
              UUID.fromString("df17ea25-709b-4edc-8dc1-d3ca82c74e8e"),
              project.property("spdxRevision").toString() + "-r8"
            )
          )
        }
        creator.set("Organization: Google LLC")
        packageSupplier.set("Organization: Google LLC")
      }
    }
  }
}

val assistantJarTask = projectTask("assistant", "jar")
val keepAnnoJarTask = projectTask("keepanno", "jar")
val keepAnnoDepsJarExceptAsm = projectTask("keepanno", "depsJarExceptAsm")
val keepAnnoToolsJar = projectTask("keepanno", "toolsJar")
val resourceShrinkerJarTask = projectTask("resourceshrinker", "jar")
val resourceShrinkerDepsTask = projectTask("resourceshrinker", "depsJar")

fun mainJarDependencies() : FileCollection {
  return sourceSets
    .main
    .get()
    .compileClasspath
    .filter({ "$it".contains("third_party")
              && "$it".contains("dependencies")
              && !"$it".contains("errorprone")
    })
}

tasks {
  jar {
    from(sourceSets["turbo"].output)
  }

  withType<Exec> {
    doFirst {
      println("Executing command: ${commandLine.joinToString(" ")}")
    }
  }

  withType<ProcessResources> {
    dependsOn(gradle.includedBuild("shared").task(":downloadDeps"))
  }

  withType<SpdxSbomTask> {
    taskExtension.set(object : DefaultSpdxSbomTaskExtension() {
      override fun mapRepoUri(input: URI?, moduleId: ModuleVersionIdentifier): URI? {

        // Locate the file origin.json with URL for download location.
        fun getOriginJson() : java.nio.file.Path {
          var repositoryDir =
              moduleId.group.replace('.', '/') + "/" + moduleId.name + "/" + moduleId.version
          return Paths.get("third_party", "dependencies", repositoryDir, "origin.json");
        }

        // Simple data model of the content of origin.json generated by the tool to download
        // and create a local repository. E.g.:
        /*
            {
              "artifacts": [
                {
                  "file": "org/ow2/asm/asm/9.5/asm-9.5.pom",
                  "repo": "https://repo1.maven.org/maven2/",
                  "artifact": "org.ow2.asm:asm:pom:9.5"
                },
                {
                  "file": "org/ow2/asm/asm/9.5/asm-9.5.jar",
                  "repo": "https://repo1.maven.org/maven2/",
                  "artifact": "org.ow2.asm:asm:jar:9.5"
                }
              ]
            }
        */
        data class Artifact(val file: String, val repo: String, val artifact: String)
        data class Artifacts(val artifacts: List<Artifact>)

        // Read origin.json.
        val json = readString(getOriginJson());
        val artifacts = Gson().fromJson(json, Artifacts::class.java);
        return URI.create(artifacts.artifacts.get(0).repo)
      }
    })
  }

  val consolidatedLicense by registering {
    dependsOn(gradle.includedBuild("shared").task(":downloadDeps"))
    dependsOn(gradle.includedBuild("shared").task(":downloadTestDeps"))
    val root = getRoot()
    val r8License = root.resolve("LICENSE")
    val libraryLicense = root.resolve("LIBRARY-LICENSE")
    val libraryLicenseFiles = fileTree(root.resolve("library-licensing"))
    inputs.files(
      listOf(r8License, libraryLicense),
      libraryLicenseFiles,
      mainJarDependencies().map(::zipTree))

    val license = getRoot().resolveAll("build", "generatedLicense", "LICENSE")
    outputs.files(license)
    val dependencies = mutableListOf<String>()
    configurations
      .findByName("runtimeClasspath")!!
      .resolvedConfiguration
      .resolvedArtifacts
      .forEach {
        val identifier = it.id.componentIdentifier
        if (identifier is ModuleComponentIdentifier) {
          dependencies.add("${identifier.group}:${identifier.module}")
        }
      }

    doLast {
      val libraryLicenses = libraryLicense.readText()
      dependencies.forEach {
        if (!libraryLicenses.contains("- artifact: $it")) {
          throw GradleException("No license for $it in LIBRARY_LICENSE")
        }
      }
      license.getParentFile().mkdirs()
      license.createNewFile()
      license.writeText(buildString {
        append("This file lists all licenses for code distributed.\n")
        .append("All non-library code has the following 3-Clause BSD license.\n")
        .append("\n")
        .append("\n")
        .append(r8License.readText())
        .append("\n")
        .append("\n")
        .append("Summary of distributed libraries:\n")
        .append("\n")
        .append(libraryLicenses)
        .append("\n")
        .append("\n")
        .append("Licenses details:\n")
        libraryLicenseFiles.sorted().forEach { file ->
          append("\n").append("\n").append(file.readText())
        }
      })
    }
  }

  val swissArmyKnife by registering(Jar::class) {
    dependsOn(keepAnnoJarTask)
    dependsOn(assistantJarTask)
    dependsOn(resourceShrinkerJarTask)
    dependsOn(gradle.includedBuild("shared").task(":downloadDeps"))
    from(sourceSets.main.get().output)
    exclude("com/android/tools/r8/threading/providers/**")
    from(keepAnnoJarTask.outputs.files.map(::zipTree))
    from(assistantJarTask.outputs.files.map(::zipTree))
    from(resourceShrinkerJarTask.outputs.files.map(::zipTree))
    from(getRoot().resolve("LICENSE"))
    entryCompression = ZipEntryCompression.STORED
    manifest {
      attributes["Main-Class"] = "com.android.tools.r8.SwissArmyKnife"
    }
    exclude("META-INF/*.kotlin_module")
    exclude("**/*.kotlin_metadata")
    exclude("keepspec.proto")
    destinationDirectory.set(getRoot().resolveAll("build", "libs"))
    archiveFileName.set("r8-full-exclude-deps.jar")
  }

  val threadingModuleBlockingJar by registering(Zip::class) {
    from(sourceSets.main.get().output)
    include("com/android/tools/r8/threading/providers/blocking/**")
    destinationDirectory.set(getRoot().resolveAll("build", "libs"))
    archiveFileName.set("threading-module-blocking.jar")
  }

  val threadingModuleSingleThreadedJar by registering(Zip::class) {
    from(sourceSets.main.get().output)
    include("com/android/tools/r8/threading/providers/singlethreaded/**")
    destinationDirectory.set(getRoot().resolveAll("build", "libs"))
    archiveFileName.set("threading-module-single-threaded.jar")
  }

  val depsJar by registering(Zip::class) {
    from(sourceSets["turbo"].output)
    dependsOn(gradle.includedBuild("shared").task(":downloadDeps"))
    dependsOn(resourceShrinkerDepsTask)
    dependsOn(threadingModuleBlockingJar)
    dependsOn(threadingModuleSingleThreadedJar)
    from(threadingModuleBlockingJar.get().outputs.getFiles().map(::zipTree))
    from(threadingModuleSingleThreadedJar.get().outputs.getFiles().map(::zipTree))
    from(mainJarDependencies().map(::zipTree))
    from(resourceShrinkerDepsTask.outputs.files.map(::zipTree))
    from(consolidatedLicense)
    exclude("**/module-info.class")
    exclude("**/*.kotlin_metadata")
    exclude("META-INF/*.kotlin_module")
    exclude("META-INF/com.android.tools/**")
    exclude("META-INF/LICENSE*")
    exclude("META-INF/MANIFEST.MF")
    exclude("META-INF/kotlinx_coroutines_core.version")
    exclude("META-INF/androidx/**/LICENSE.txt")
    exclude("META-INF/maven/**")
    exclude("META-INF/proguard/**")
    exclude("META-INF/versions/**")
    exclude("META-INF/services/kotlin.reflect.**")
    exclude("**/*.xml")
    exclude("com/android/version.properties")
    exclude("NOTICE")
    exclude("README.md")
    exclude("javax/annotation/**")
    exclude("wireless/**")
    exclude("google/protobuf/**")
    exclude("DebugProbesKt.bin")

    // Disabling compression makes this step go from 4s -> 2s as of Nov 2025,
    // as measured by "gradle --profile".
    entryCompression = ZipEntryCompression.STORED

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    archiveFileName.set("deps.jar")
  }

val swissArmyKnifeWithoutLicense by registering(Zip::class) {
    dependsOn(swissArmyKnife)
    from(swissArmyKnife.get().outputs.files.map(::zipTree))
    exclude("LICENSE")
    exclude("androidx/")
    exclude("androidx/annotation/")
    exclude("androidx/annotation/keep/**")
    archiveFileName.set("swiss-army-no-license.jar")
}

fun relocateDepsExceptAsm(pkg: String): List<String> {
  return listOf("--map",
                "android.aapt.**->${pkg}.android.aapt",
                "--map",
                "androidx.annotation.**->${pkg}.androidx.annotation",
                "--map",
                "androidx.collection.**->${pkg}.androidx.collection",
                "--map",
                "androidx.tracing.**->${pkg}.androidx.tracing",
                "--map",
                "com.android.**->${pkg}.com.android",
                "--map",
                "com.android.build.shrinker.**->${pkg}.resourceshrinker",
                "--map",
                "com.google.common.**->${pkg}.com.google.common",
                "--map",
                "com.google.gson.**->${pkg}.com.google.gson",
                "--map",
                "com.google.thirdparty.**->${pkg}.com.google.thirdparty",
                "--map",
                "com.squareup.wire.**->${pkg}.com.squareup.wire",
                "--map",
                "it.unimi.dsi.fastutil.**->${pkg}.it.unimi.dsi.fastutil",
                "--map",
                "kotlin.**->${pkg}.jetbrains.kotlin",
                "--map",
                "kotlinx.**->${pkg}.jetbrains.kotlinx",
                "--map",
                "okio.**->${pkg}.okio",
                "--map",
                "org.jetbrains.**->${pkg}.org.jetbrains",
                "--map",
                "org.intellij.**->${pkg}.org.intellij",
                "--map",
                "org.checkerframework.**->${pkg}.org.checkerframework",
                "--map",
                "com.google.j2objc.**->${pkg}.com.google.j2objc",
                "--map",
                "com.google.protobuf.**->${pkg}.com.google.protobuf",
                "--map",
                "perfetto.protos.**->${pkg}.perfetto.protos",
                "--map",
                "org.jspecify.annotations.**->${pkg}.org.jspecify.annotations",
                "--map",
                "_COROUTINE.**->${pkg}._COROUTINE")
}

val r8WithRelocatedDeps by registering(Exec::class) {
    dependsOn(depsJar)
    dependsOn(swissArmyKnifeWithoutLicense)
    val swissArmy = swissArmyKnifeWithoutLicense.get().outputs.files.singleFile
    val deps = depsJar.get().outputs.files.singleFile
    inputs.files(listOf(swissArmy, deps))
    val output = getRoot().resolveAll("build", "libs", "r8.jar")
    outputs.file(output)
    val pkg = "com.android.tools.r8"
    commandLine = baseCompilerCommandLine(
      swissArmy,
      deps,
      "relocator",
      listOf("--input",
             "$swissArmy",
             "--input",
             "$deps",
             "--output",
             "$output",
             // Add identity mapping to enforce no relocation of things already in package
             // com.android.tools.r8.
              "--map",
             "com.android.tools.r8.**->${pkg}",
              // Add identity for the public annotation surface of keepanno
              "--map",
             "com.android.tools.r8.keepanno.annotations.**->${pkg}.keepanno.annotations",
              // Explicitly move all other keepanno utilities.
              "--map",
             "com.android.tools.r8.keepanno.**->${pkg}.relocated.keepanno",
             "--map",
             "org.objectweb.asm.**->${pkg}.org.objectweb.asm")
             + relocateDepsExceptAsm(pkg)
      )
  }


val keepAnnoToolsWithRelocatedDeps by registering(Exec::class) {
    dependsOn(depsJar)
    dependsOn(swissArmyKnifeWithoutLicense)
    dependsOn(keepAnnoDepsJarExceptAsm)
    dependsOn(keepAnnoToolsJar)
    val swissArmy = swissArmyKnifeWithoutLicense.get().outputs.files.singleFile
    val deps = depsJar.get().outputs.files.singleFile
    val keepAnnoDeps = keepAnnoDepsJarExceptAsm.outputs.files.singleFile
    val tools = keepAnnoToolsJar.outputs.files.singleFile
    inputs.files(listOf(tools, keepAnnoDeps))
    val output = getRoot().resolveAll("build", "libs", "keepanno-tools.jar")
    outputs.file(output)
    val pkg = "com.android.tools.r8.keepanno"
    commandLine = baseCompilerCommandLine(
      swissArmy,
      deps,
      "relocator",
      listOf("--input",
             "$tools",
             "--input",
             "$keepAnnoDeps",
             "--output",
             "$output",
             // Add identity mapping to enforce no relocation of things already in package
             // com.android.tools.r8.keepanno
              "--map",
             "com.android.tools.r8.keepanno.**->${pkg}")
             + relocateDepsExceptAsm(pkg)
      )
  }
}

tasks.withType<KotlinCompile> {
  enabled = false
}

fun enableCheck(task: JavaCompile, warning: String) {
  if (treatWarningsAsErrors) {
    task.options.errorprone.error(warning)
  } else {
    task.options.errorprone.warn(warning)
  }
}

tasks.withType<JavaCompile> {
  dependsOn(gradle.includedBuild("shared").task(":downloadDeps"))
  println("NOTE: Running with JDK: " + org.gradle.internal.jvm.Jvm.current().javaHome)

  // Enable error prone for D8/R8 main sources.
  options.errorprone.isEnabled.set(enableErrorProne)

  if (enableErrorProne) {
    // Non-default / Experimental checks - explicitly enforced.
    enableCheck(this, "RemoveUnusedImports")
    enableCheck(this, "InconsistentOverloads")
    enableCheck(this, "MissingDefault")
    enableCheck(this, "MultipleTopLevelClasses")
    enableCheck(this, "NarrowingCompoundAssignment")

    // Warnings that cause unwanted edits (e.g., inability to write informative asserts).
    options.errorprone.disable("AlreadyChecked")

    // JavaDoc related warnings. Would be nice to resolve but of no real consequence.
    options.errorprone.disable("InvalidLink")
    options.errorprone.disable("InvalidBlockTag")
    options.errorprone.disable("InvalidInlineTag")
    options.errorprone.disable("EmptyBlockTag")
    options.errorprone.disable("MissingSummary")
    options.errorprone.disable("UnrecognisedJavadocTag")
    options.errorprone.disable("AlmostJavadoc")

    // Moving away from identity and canonical items is not planned.
    options.errorprone.disable("IdentityHashMapUsage")

    if (treatWarningsAsErrors) {
      options.errorprone.allErrorsAsWarnings = true
    }
  }

  // Make all warnings errors. Warnings that we have chosen not to fix (or suppress) are disabled
  // outright below.
  if (treatWarningsAsErrors) {
    options.compilerArgs.add("-Werror")
  }

  // Increase number of reported errors to 1000 (default is 100).
  options.compilerArgs.add("-Xmaxerrs")
  options.compilerArgs.add("1000")
}
