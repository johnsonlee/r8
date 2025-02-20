// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

import org.gradle.api.JavaVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  `kotlin-dsl`
  `java-library`
  id("dependencies-plugin")
}

val root = getRoot()

java {
  sourceSets.test.configure {
    java {
      srcDir(root.resolveAll("src", "test", "bootstrap"))
    }
  }
  // We are using a new JDK to compile to an older language version, which is not directly
  // compatible with java toolchains.
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
  toolchain {
    languageVersion = JavaLanguageVersion.of(JvmCompatibility.release)
  }
}

val testbaseJavaCompileTask = projectTask("testbase", "compileJava")
val testbaseDepsJarTask = projectTask("testbase", "depsJar")

val keepAnnoJarTask = projectTask("keepanno", "jar")
val keepAnnoCompileTask = projectTask("keepanno", "compileJava")
val keepAnnoCompileKotlinTask = projectTask("keepanno", "compileKotlin")
val mainR8RelocatedTask = projectTask("main", "r8WithRelocatedDeps")
val resourceShrinkerJavaCompileTask = projectTask("resourceshrinker", "compileJava")
val resourceShrinkerKotlinCompileTask = projectTask("resourceshrinker", "compileKotlin")
val resourceShrinkerDepsJarTask = projectTask("resourceshrinker", "depsJar")

dependencies {
  implementation(keepAnnoJarTask.outputs.files)
  implementation(projectTask("main", "jar").outputs.files)
  implementation(resourceShrinkerJavaCompileTask.outputs.files)
  implementation(resourceShrinkerKotlinCompileTask.outputs.files)
  implementation(resourceShrinkerDepsJarTask.outputs.files)
  implementation(testbaseDepsJarTask.outputs.files)
  implementation(testbaseJavaCompileTask.outputs.files)
}

fun testDependencies() : FileCollection {
  return sourceSets
    .test
    .get()
    .compileClasspath
    .filter {
      "$it".contains("third_party")
        && !"$it".contains("errorprone")
        && !"$it".contains("third_party/gradle")
    }
}

tasks {
  withType<JavaCompile> {
    dependsOn(gradle.includedBuild("main").task(":jar"))
  }

  withType<KotlinCompile> {
    kotlinOptions {
      enabled = false
    }
  }

  withType<Test> {
    TestingState.setUpTestingState(this)
    dependsOn(mainR8RelocatedTask)
    systemProperty("TEST_DATA_LOCATION",
                   layout.buildDirectory.dir("classes/java/test").get().toString())
    systemProperty("TESTBASE_DATA_LOCATION",
                   testbaseJavaCompileTask.outputs.files.getAsPath().split(File.pathSeparator)[0])
    systemProperty(
      "BUILD_PROP_KEEPANNO_RUNTIME_PATH",
      extractClassesPaths(
        "keepanno/",
        keepAnnoCompileTask.outputs.files.asPath,
        keepAnnoCompileKotlinTask.outputs.files.asPath))
    systemProperty("R8_WITH_RELOCATED_DEPS", mainR8RelocatedTask.outputs.files.singleFile)
    systemProperty("BUILD_PROP_R8_RUNTIME_PATH", mainR8RelocatedTask.outputs.files.singleFile)
  }

  val testJar by registering(Jar::class) {
    from(sourceSets.test.get().output)
    // TODO(b/296486206): Seems like IntelliJ has a problem depending on test source sets.
    archiveFileName.set("not_named_tests_bootstrap.jar")
  }

  val depsJar by registering(Jar::class) {
    dependsOn(gradle.includedBuild("shared").task(":downloadDeps"))
    dependsOn(gradle.includedBuild("keepanno").task(":jar"))
    if (!project.hasProperty("no_internal")) {
      dependsOn(gradle.includedBuild("shared").task(":downloadDepsInternal"))
    }
    from(testDependencies().map(::zipTree))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    archiveFileName.set("deps.jar")
  }
}
