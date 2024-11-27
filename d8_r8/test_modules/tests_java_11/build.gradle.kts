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
    java.srcDir(root.resolveAll("src", "test", "examplesJava11"))
  }
  sourceCompatibility = JavaVersion.VERSION_11
  targetCompatibility = JavaVersion.VERSION_11
}

val testbaseJavaCompileTask = projectTask("testbase", "compileJava")
val testbaseDepsJarTask = projectTask("testbase", "depsJar")
val mainCompileTask = projectTask("main", "compileJava")

dependencies {
  implementation(files(testbaseDepsJarTask.outputs.files.getSingleFile()))
  implementation(testbaseJavaCompileTask.outputs.files)
  implementation(mainCompileTask.outputs.files)
  implementation(projectTask("main", "processResources").outputs.files)
}

// We just need to register the examples jars for it to be referenced by other modules.
val buildExampleJars = buildExampleJars("examplesJava11")

tasks {
  withType<JavaCompile> {
    dependsOn(gradle.includedBuild("shared").task(":downloadDeps"))
    options.setFork(true)
    options.forkOptions.memoryMaximumSize = "3g"
    options.forkOptions.executable = getCompilerPath(Jdk.JDK_11)
  }

  withType<Test> {
    notCompatibleWithConfigurationCache(
      "Failure storing the configuration cache: cannot serialize object of type 'org.gradle.api.internal.project.DefaultProject', a subtype of 'org.gradle.api.Project', as these are not supported with the configuration cache")
    TestingState.setUpTestingState(this)
    javaLauncher = getJavaLauncher(Jdk.JDK_11)
    systemProperty("TEST_DATA_LOCATION",
      // This should be
      //   layout.buildDirectory.dir("classes/java/test").get().toString()
      // once the use of 'buildExampleJars' above is removed.
                   getRoot().resolveAll("build", "test", "examplesJava11", "classes"))
    systemProperty("TESTBASE_DATA_LOCATION",
                   testbaseJavaCompileTask.outputs.files.getAsPath().split(File.pathSeparator)[0])
  }

  val testJar by registering(Jar::class) {
    from(sourceSets.test.get().output)
    // TODO(b/296486206): Seems like IntelliJ has a problem depending on test source sets. Renaming
    //  this from the default name (tests_java_8.jar) will allow IntelliJ to find the resources in
    //  the jar and not show red underlines. However, navigation to base classes will not work.
    archiveFileName.set("not_named_tests_java_11.jar")
  }
}

