// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

import java.nio.file.Paths
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile


plugins {
  `kotlin-dsl`
  id("dependencies-plugin")
}

java {
  sourceCompatibility = JvmCompatibility.sourceCompatibility
  targetCompatibility = JvmCompatibility.targetCompatibility
  toolchain {
    languageVersion = JavaLanguageVersion.of(JvmCompatibility.release)
  }
}

dependencies { }

val keepAnnoCompileTask = projectTask("keepanno", "compileJava")
val keepAnnoCompileKotlinTask = projectTask("keepanno", "compileKotlin")
val keepAnnoSourcesTask = projectTask("keepanno", "sourcesJar")
val assistantJarTask = projectTask("assistant", "jar")
val mainDepsJarTask = projectTask("main", "depsJar")
val swissArmyKnifeTask = projectTask("main", "swissArmyKnife")
val r8WithRelocatedDepsTask = projectTask("main", "r8WithRelocatedDeps")
val mainSourcesTask = projectTask("main", "sourcesJar")
val resourceShrinkerSourcesTask = projectTask("resourceshrinker", "sourcesJar")
val javaTestBaseJarTask = projectTask("testbase", "testJar")
val javaTestBaseDepsJar = projectTask("testbase", "depsJar")
val java8TestJarTask = projectTask("tests_java_8", "testJar")
val java9TestJarTask = projectTask("tests_java_9", "testJar")
val java11TestJarTask = projectTask("tests_java_11", "testJar")
val java17TestJarTask = projectTask("tests_java_17", "testJar")
val java21TestJarTask = projectTask("tests_java_21", "testJar")
val bootstrapTestsDepsJarTask = projectTask("tests_bootstrap", "depsJar")
val bootstrapTestJarTask = projectTask("tests_bootstrap", "testJar")
val testsJava8SourceSetDependenciesTask = projectTask("tests_java_8", "sourceSetDependencyTask")
val keepAnnoAndroidXAnnotationsJar = projectTask("keepanno", "keepAnnoAndroidXAnnotationsJar")

tasks {
  withType<Exec> {
    doFirst {
      println("Executing command: ${commandLine.joinToString(" ")}")
    }
  }

  withType<KotlinCompile> {
    kotlinOptions {
      jvmTarget = "17"
    }
  }

  "clean" {
    dependsOn(gradle.includedBuild("tests_bootstrap").task(":clean"))
    dependsOn(gradle.includedBuild("tests_java_8").task(":clean"))
    dependsOn(gradle.includedBuild("tests_java_9").task(":clean"))
    dependsOn(gradle.includedBuild("tests_java_11").task(":clean"))
    dependsOn(gradle.includedBuild("tests_java_17").task(":clean"))
    dependsOn(gradle.includedBuild("tests_java_21").task(":clean"))
    dependsOn(gradle.includedBuild("tests_java_23").task(":clean"))
  }

  val packageTests by registering(Jar::class) {
    dependsOn(java8TestJarTask)
    dependsOn(java9TestJarTask)
    dependsOn(java11TestJarTask)
    dependsOn(java17TestJarTask)
    dependsOn(java21TestJarTask)
    dependsOn(bootstrapTestJarTask)
    from(java8TestJarTask.outputs.files.map(::zipTree))
    from(java9TestJarTask.outputs.files.map(::zipTree))
    from(java11TestJarTask.outputs.files.map(::zipTree))
    from(java17TestJarTask.outputs.files.map(::zipTree))
    from(java21TestJarTask.outputs.files.map(::zipTree))
    from(bootstrapTestJarTask.outputs.files.map(::zipTree))
    exclude("META-INF/*.kotlin_module", "**/*.kotlin_metadata")
    destinationDirectory.set(getRoot().resolveAll("build", "libs"))
    archiveFileName.set("r8tests.jar")
  }

  val packageTestDeps by registering(Jar::class) {
    dependsOn(bootstrapTestsDepsJarTask, javaTestBaseDepsJar, keepAnnoAndroidXAnnotationsJar)
    from(bootstrapTestsDepsJarTask.outputs.getFiles().map(::zipTree))
    from(javaTestBaseDepsJar.outputs.getFiles().map(::zipTree))
    from(keepAnnoAndroidXAnnotationsJar.outputs.getFiles().map(::zipTree))
    exclude("META-INF/*.kotlin_module", "**/*.kotlin_metadata")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    destinationDirectory.set(getRoot().resolveAll("build", "libs"))
    archiveFileName.set("test_deps_all.jar")
  }

  val packageTestBase by registering(Jar::class) {
    dependsOn(javaTestBaseJarTask)
    from(javaTestBaseJarTask.outputs.files.map(::zipTree))
    exclude("META-INF/*.kotlin_module", "**/*.kotlin_metadata")
    destinationDirectory.set(getRoot().resolveAll("build", "libs"))
    archiveFileName.set("r8test_base.jar")
  }

  val packageTestBaseExcludeKeep by registering(Jar::class) {
    dependsOn(packageTestBase)
    from(zipTree(packageTestBase.getSingleOutputFile()))
    // TODO(b/328353718): we have com.android.tools.r8.Keep in both test_base and main
    exclude("com/android/tools/r8/Keep.class")
    archiveFileName.set("r8test_base_no_keep.jar")
  }


  fun Exec.executeRelocator(jarProvider: TaskProvider<*>, artifactName: String) {
    dependsOn(r8WithRelocatedDepsTask, jarProvider)
    val outputJar = file(Paths.get("build", "libs", artifactName))
    outputs.file(outputJar)
    val r8WithRelocatedDepsJar = r8WithRelocatedDepsTask.getSingleOutputFile()
    val testJar = jarProvider.getSingleOutputFile()
    inputs.files(r8WithRelocatedDepsJar, testJar)
    commandLine = baseCompilerCommandLine(
      r8WithRelocatedDepsJar,
      "relocator",
      listOf("--input",
             "$testJar",
             "--output",
             "$outputJar",
             "--map",
             "kotlin.metadata.**->com.android.tools.r8.jetbrains.kotlin.metadata"))
  }

  // When testing R8 lib with relocated deps we must relocate kotlin.metadata in the tests, since
  // types from kotlin.metadata are used on the R8 main/R8 test boundary.
  //
  // This is not needed when testing R8 lib excluding deps since we simply include the deps on the
  // classpath at runtime.
  val relocateTestsForR8LibWithRelocatedDeps by registering(Exec::class) {
    executeRelocator(packageTests, "r8tests-relocated.jar")
  }

  val relocateTestBaseForR8LibWithRelocatedDeps by registering(Exec::class) {
    executeRelocator(packageTestBase, "r8testbase-relocated.jar")
  }

  fun Exec.generateKeepRulesForR8Lib(
          targetJarProvider: Task, testJarProviders: List<TaskProvider<*>>, artifactName: String) {
    dependsOn(
            mainDepsJarTask,
            packageTestDeps,
            r8WithRelocatedDepsTask,
            targetJarProvider)
    testJarProviders.forEach(::dependsOn)
    val mainDepsJar = mainDepsJarTask.getSingleOutputFile()
    val r8WithRelocatedDepsJar = r8WithRelocatedDepsTask.getSingleOutputFile()
    val targetJar = targetJarProvider.getSingleOutputFile()
    val testDepsJar = packageTestDeps.getSingleOutputFile()
    inputs.files(mainDepsJar, r8WithRelocatedDepsJar, targetJar, testDepsJar)
    inputs.files(testJarProviders.map{it.getSingleOutputFile()})
    val output = file(Paths.get("build", "libs", artifactName))
    outputs.file(output)
    val argList = mutableListOf("--keep-rules",
                    "--allowobfuscation",
                    "--lib",
                    "${getJavaHome(Jdk.JDK_21)}",
                    "--lib",
                    "$mainDepsJar",
                    "--lib",
                    "$testDepsJar",
                    "--target",
                    "$targetJar",
                    "--output",
                    "$output")
    testJarProviders.forEach{
      argList.add("--source")
      argList.add("${it.getSingleOutputFile()}")
    }
    commandLine = baseCompilerCommandLine(
            listOf("-Dcom.android.tools.r8.tracereferences.obfuscateAllEnums"),
            r8WithRelocatedDepsJar,
            "tracereferences",
            argList
    )
  }

  val generateKeepRulesForR8LibWithRelocatedDeps by registering(Exec::class) {
    generateKeepRulesForR8Lib(
            r8WithRelocatedDepsTask,
            listOf(relocateTestsForR8LibWithRelocatedDeps, relocateTestBaseForR8LibWithRelocatedDeps),
            "generated-keep-rules-r8lib.txt")
  }

  val generateKeepRulesForR8LibNoDeps by registering(Exec::class) {
    generateKeepRulesForR8Lib(
            swissArmyKnifeTask,
            listOf(packageTests, packageTestBase),
            "generated-keep-rules-r8lib-exclude-deps.txt")
  }

  fun Exec.assembleR8Lib(
    inputJarProvider: Task,
    generatedKeepRulesProvider: TaskProvider<Exec>,
    classpath: List<File>,
    artifactName: String) {
    dependsOn(generatedKeepRulesProvider, inputJarProvider, r8WithRelocatedDepsTask,
              assistantJarTask)
    val inputJar = inputJarProvider.getSingleOutputFile()
    val r8WithRelocatedDepsJar = r8WithRelocatedDepsTask.getSingleOutputFile()
    val assistantJar = assistantJarTask.getSingleOutputFile()
    val keepRuleFiles = listOf(
            getRoot().resolveAll("src", "main", "keep.txt"),
            getRoot().resolveAll("src", "main", "discard.txt"),
            generatedKeepRulesProvider.getSingleOutputFile(),
            // TODO(b/294351878): Remove once enum issue is fixed
            getRoot().resolveAll("src", "main", "keep_r8resourceshrinker.txt"))
    inputs.files(listOf(r8WithRelocatedDepsJar, inputJar,
                        getRoot().resolveAll("tools", "create_r8lib.py"))
                   .union(keepRuleFiles).union(classpath))
    val outputJar = getRoot().resolveAll("build", "libs", artifactName)
    outputs.file(outputJar)
    commandLine = createR8LibCommandLine(
      r8WithRelocatedDepsJar,
      inputJar,
      outputJar,
      keepRuleFiles,
      excludingDepsVariant = classpath.isNotEmpty(),
      debugVariant = false,
      classpath = classpath,
      replaceFromJar = assistantJar)
  }

  val assembleR8LibNoDeps by registering(Exec::class) {
    dependsOn(mainDepsJarTask)
    val mainDepsJar = mainDepsJarTask.getSingleOutputFile()
    assembleR8Lib(
            swissArmyKnifeTask,
            generateKeepRulesForR8LibNoDeps,
            listOf(mainDepsJar),
            "r8lib-exclude-deps.jar")
  }

  val assembleR8LibWithRelocatedDeps by registering(Exec::class) {
    assembleR8Lib(
            r8WithRelocatedDepsTask,
            generateKeepRulesForR8LibWithRelocatedDeps,
            listOf(),
            "r8lib.jar")
  }

  fun Task.generateTestKeepRulesForR8Lib(
          r8LibJarProvider: TaskProvider<Exec>, artifactName: String) {
    dependsOn(r8LibJarProvider)
    val r8LibJar = r8LibJarProvider.getSingleOutputFile()
    inputs.files(r8LibJar)
    val output = rootProject.buildDir.resolveAll("libs", artifactName)
    outputs.files(output)
    doLast {
      // TODO(b/299065371): We should be able to take in the partition map output.
      output.writeText(
              """-keep class ** { *; }
-dontshrink
-dontoptimize
-keepattributes *
-applymapping $r8LibJar.map
""")
    }
  }

  val generateTestKeepRulesR8LibWithRelocatedDeps by registering {
    generateTestKeepRulesForR8Lib(assembleR8LibWithRelocatedDeps, "r8lib-tests-keep.txt")
  }

  val generateTestKeepRulesR8LibNoDeps by registering {
    generateTestKeepRulesForR8Lib(assembleR8LibNoDeps, "r8lib-exclude-deps-tests-keep.txt")
  }

  fun Exec.rewriteTestsForR8Lib(
          keepRulesFileProvider: TaskProvider<Task>,
          r8JarProvider: Task,
          testJarProvider: TaskProvider<*>,
          artifactName: String,
          addTestBaseClasspath: Boolean) {
    dependsOn(
            keepRulesFileProvider,
            packageTestDeps,
            relocateTestsForR8LibWithRelocatedDeps,
            r8JarProvider,
            r8WithRelocatedDepsTask,
            testJarProvider,
            packageTestBaseExcludeKeep)
    val keepRulesFile = keepRulesFileProvider.getSingleOutputFile()
    val r8Jar = r8JarProvider.getSingleOutputFile()
    val r8WithRelocatedDepsJar = r8WithRelocatedDepsTask.getSingleOutputFile()
    val testBaseJar = packageTestBaseExcludeKeep.getSingleOutputFile()
    val testDepsJar = packageTestDeps.getSingleOutputFile()
    val testJar = testJarProvider.getSingleOutputFile()
    inputs.files(keepRulesFile, r8Jar, r8WithRelocatedDepsJar, testDepsJar, testJar)
    val outputJar = getRoot().resolveAll("build", "libs", artifactName)
    outputs.file(outputJar)
    val args = mutableListOf(
      "--classfile",
      "--debug",
      "--lib",
      "${getJavaHome(Jdk.JDK_21)}",
      "--classpath",
      "$r8Jar",
      "--classpath",
      "$testDepsJar",
      "--output",
      "$outputJar",
      "--pg-conf",
      "$keepRulesFile",
      "$testJar")
    if (addTestBaseClasspath) {
      args.add("--classpath")
      args.add("$testBaseJar")
    }
    commandLine = baseCompilerCommandLine(
            listOf("-Dcom.android.tools.r8.tracereferences.obfuscateAllEnums"),
            r8WithRelocatedDepsJar,
            "r8",
            args)
  }

  val rewriteTestsForR8LibWithRelocatedDeps by registering(Exec::class) {
    rewriteTestsForR8Lib(
            generateTestKeepRulesR8LibWithRelocatedDeps,
            r8WithRelocatedDepsTask,
            relocateTestsForR8LibWithRelocatedDeps,
            "r8libtestdeps-cf.jar",
            true)
  }

  val rewriteTestBaseForR8LibWithRelocatedDeps by registering(Exec::class) {
    rewriteTestsForR8Lib(
            generateTestKeepRulesR8LibWithRelocatedDeps,
            r8WithRelocatedDepsTask,
            relocateTestBaseForR8LibWithRelocatedDeps,
            "r8libtestbase-cf.jar",
            false)
  }

  val rewriteTestsForR8LibNoDeps by registering(Exec::class) {
    rewriteTestsForR8Lib(
            generateTestKeepRulesR8LibNoDeps,
            swissArmyKnifeTask,
            packageTests,
            "r8lib-exclude-deps-testdeps-cf.jar",
            true)
  }

  val cleanUnzipTests by registering(Delete::class) {
    dependsOn(packageTests)
    val outputDir = file("${buildDir}/unpacked/test")
    setDelete(outputDir)
  }

  val unzipTests by registering(Copy::class) {
    dependsOn(cleanUnzipTests, packageTests)
    val outputDir = file("${buildDir}/unpacked/test")
    from(zipTree(packageTests.getSingleOutputFile()))
    into(outputDir)
  }

  val unzipTestBase by registering(Copy::class) {
    dependsOn(cleanUnzipTests, packageTestBase)
    val outputDir = file("${buildDir}/unpacked/testbase")
    from(zipTree(packageTestBase.getSingleOutputFile()))
    into(outputDir)
  }

  fun Copy.unzipRewrittenTestsForR8Lib(
          rewrittenTestJarProvider: TaskProvider<Exec>, outDirName: String) {
    dependsOn(rewrittenTestJarProvider)
    val outputDir = file("$buildDir/unpacked/$outDirName")
    val rewrittenTestJar = rewrittenTestJarProvider.getSingleOutputFile()
    from(zipTree(rewrittenTestJar))
    into(outputDir)
  }

  val cleanUnzipRewrittenTestsForR8LibWithRelocatedDeps by registering(Delete::class) {
    val outputDir = file("${buildDir}/unpacked/rewrittentests-r8lib")
    setDelete(outputDir)
  }

  val unzipRewrittenTestsForR8LibWithRelocatedDeps by registering(Copy::class) {
    dependsOn(cleanUnzipRewrittenTestsForR8LibWithRelocatedDeps)
    unzipRewrittenTestsForR8Lib(rewriteTestsForR8LibWithRelocatedDeps, "rewrittentests-r8lib")
  }

  val cleanUnzipRewrittenTestsForR8LibNoDeps by registering(Delete::class) {
    val outputDir = file("${buildDir}/unpacked/rewrittentests-r8lib-exclude-deps")
    setDelete(outputDir)
  }

  val unzipRewrittenTestsForR8LibNoDeps by registering(Copy::class) {
    dependsOn(cleanUnzipRewrittenTestsForR8LibNoDeps)
    unzipRewrittenTestsForR8Lib(
            rewriteTestsForR8LibNoDeps, "rewrittentests-r8lib-exclude-deps")
  }

  fun Test.testR8Lib(r8Lib: TaskProvider<Exec>, unzipRewrittenTests: TaskProvider<Copy>) {
    println("NOTE: Number of processors " + Runtime.getRuntime().availableProcessors())
    println("NOTE: Max parallel forks " + maxParallelForks)
    dependsOn(
            packageTestDeps,
            r8Lib,
            r8WithRelocatedDepsTask,
            assembleR8LibNoDeps,
            testsJava8SourceSetDependenciesTask,
            rewriteTestBaseForR8LibWithRelocatedDeps,
            unzipRewrittenTests,
            unzipTests,
            unzipTestBase)
    val r8LibJar = r8Lib.getSingleOutputFile()
    val r8LibMappingFile = file(r8LibJar.toString() + ".map")
    val r8WithRelocatedDepsJar = r8WithRelocatedDepsTask.getSingleOutputFile()
    configure(isR8Lib = true, r8Jar = r8WithRelocatedDepsJar, r8LibMappingFile = r8LibMappingFile)

    // R8lib should be used instead of the main output and all the tests in r8 should be mapped and
    // exists in r8LibTestPath.
    classpath = files(
            packageTestDeps.get().getOutputs().getFiles(),
            r8LibJar,
            unzipRewrittenTests.get().getOutputs().getFiles(),
            rewriteTestBaseForR8LibWithRelocatedDeps.getSingleOutputFile())
    testClassesDirs = unzipRewrittenTests.get().getOutputs().getFiles()
    systemProperty("TEST_DATA_LOCATION", unzipTests.getSingleOutputFile())
    systemProperty("TESTBASE_DATA_LOCATION", unzipTestBase.getSingleOutputFile())

    systemProperty(
      "BUILD_PROP_KEEPANNO_RUNTIME_PATH",
      extractClassesPaths(
        "keepanno" + File.separator,
        keepAnnoCompileTask.outputs.files.asPath,
        keepAnnoCompileKotlinTask.outputs.files.asPath))
    systemProperty("BUILD_PROP_R8_RUNTIME_PATH", r8LibJar)
    systemProperty("R8_DEPS", mainDepsJarTask.getSingleOutputFile())
    systemProperty("com.android.tools.r8.artprofilerewritingcompletenesscheck", "true")
    systemProperty("R8_WITH_RELOCATED_DEPS", r8WithRelocatedDepsTask.outputs.files.singleFile)

    javaLauncher = getJavaLauncher(Jdk.JDK_21)

    reports.junitXml.outputLocation.set(getRoot().resolveAll("build", "test-results", "test"))
    reports.html.outputLocation.set(getRoot().resolveAll("build", "reports", "tests", "test"))
  }

  val testR8LibWithRelocatedDeps by registering(Test::class) {
    testR8Lib(assembleR8LibWithRelocatedDeps, unzipRewrittenTestsForR8LibWithRelocatedDeps)
  }

  val testR8LibNoDeps by registering(Test::class) {
    testR8Lib(assembleR8LibNoDeps, unzipRewrittenTestsForR8LibNoDeps)
  }

  val packageSources by registering(Jar::class) {
    dependsOn(mainSourcesTask)
    dependsOn(resourceShrinkerSourcesTask)
    dependsOn(keepAnnoSourcesTask)
    from(mainSourcesTask.outputs.files.map(::zipTree))
    from(resourceShrinkerSourcesTask.outputs.files.map(::zipTree))
    from(keepAnnoSourcesTask.outputs.files.map(::zipTree))
    archiveClassifier.set("sources")
    archiveFileName.set("r8-src.jar")
    destinationDirectory.set(getRoot().resolveAll("build", "libs"))
  }

  test {
    dependsOn(gradle.includedBuild("shared").task(":downloadDeps"))
    dependsOn(gradle.includedBuild("shared").task(":downloadTestDeps"))
    if (!project.hasProperty("no_internal")) {
      dependsOn(gradle.includedBuild("shared").task(":downloadDepsInternal"))
      dependsOn(gradle.includedBuild("shared").task(":downloadTestDepsInternal"))
    }
    if (project.hasProperty("r8lib")) {
      dependsOn(testR8LibWithRelocatedDeps)
    } else if (project.hasProperty("r8lib_no_deps")) {
      dependsOn(testR8LibNoDeps)
    } else {
      dependsOn(gradle.includedBuild("tests_java_8").task(":test"))
      dependsOn(gradle.includedBuild("tests_java_17").task(":test"))
      dependsOn(gradle.includedBuild("tests_java_21").task(":test"))
      dependsOn(gradle.includedBuild("tests_bootstrap").task(":test"))
    }
  }
}

fun Task.getSingleOutputFile(): File = getOutputs().getSingleOutputFile()

fun TaskOutputs.getSingleOutputFile(): File = getFiles().getSingleFile()

fun TaskProvider<*>.getSingleOutputFile(): File = get().getSingleOutputFile()
