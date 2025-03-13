// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
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
  sourceSets.main.configure {
    java {
      srcDir(root.resolveAll("src", "test", "testbase", "java"))
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

// If we depend on keepanno by referencing the project source outputs we get an error regarding
// incompatible java class file version. By depending on the jar we circumvent that.
val keepAnnoJarTask = projectTask("keepanno", "jar")
val keepAnnoCompileTask = projectTask("keepanno", "compileJava")
val mainCompileTask = projectTask("main", "compileJava")
val mainDepsJarTask = projectTask("main", "depsJar")
val resourceShrinkerJavaCompileTask = projectTask("resourceshrinker", "compileJava")
val resourceShrinkerKotlinCompileTask = projectTask("resourceshrinker", "compileKotlin")
val resourceShrinkerDepsJarTask = projectTask("resourceshrinker", "depsJar")

dependencies {
  implementation(keepAnnoJarTask.outputs.files)
  implementation(mainCompileTask.outputs.files)
  implementation(projectTask("main", "processResources").outputs.files)
  implementation(resourceShrinkerJavaCompileTask.outputs.files)
  implementation(resourceShrinkerKotlinCompileTask.outputs.files)
  implementation(resourceShrinkerDepsJarTask.outputs.files)
  implementation(Deps.asm)
  implementation(Deps.asmCommons)
  implementation(Deps.asmUtil)
  implementation(Deps.gson)
  implementation(Deps.guava)
  implementation(Deps.javassist)
  implementation(Deps.junit)
  implementation(Deps.kotlinStdLib)
  implementation(Deps.kotlinReflect)
  implementation(Deps.kotlinMetadata)
  implementation(resolve(ThirdPartyDeps.ddmLib,"ddmlib.jar"))
  implementation(resolve(ThirdPartyDeps.jasmin,"jasmin-2.4.jar"))
  implementation(resolve(ThirdPartyDeps.jdwpTests,"apache-harmony-jdwp-tests-host.jar"))
  implementation(Deps.fastUtil)
  implementation(Deps.smali)
  implementation(Deps.smaliUtil)
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
    dependsOn(gradle.includedBuild("keepanno").task(":jar"))
    dependsOn(gradle.includedBuild("resourceshrinker").task(":jar"))
    dependsOn(gradle.includedBuild("main").task(":compileJava"))
    dependsOn(gradle.includedBuild("main").task(":processResources"))
    dependsOn(gradle.includedBuild("shared").task(":downloadDeps"))
    dependsOn(gradle.includedBuild("shared").task(":downloadTestDeps"))
  }

  withType<JavaExec> {
    if (name.endsWith("main()")) {
      // IntelliJ pass the main execution through a stream which is
      // not compatible with gradle configuration cache.
      notCompatibleWithConfigurationCache("JavaExec created by IntelliJ")
    }
  }

  withType<KotlinCompile> {
    enabled = false
  }

  val testJar by registering(Jar::class) {
    from(sourceSets.main.get().output)
    // TODO(b/296486206): Seems like IntelliJ has a problem depending on test source sets. Renaming
    //  this from the default name (testbase.jar) will allow IntelliJ to find the resources in
    //  the jar and not show red underlines. However, navigation to base classes will not work.
    archiveFileName.set("not_named_testbase.jar")
  }

  val depsJar by registering(Jar::class) {
    dependsOn(gradle.includedBuild("shared").task(":downloadDeps"))
    dependsOn(gradle.includedBuild("shared").task(":downloadTestDeps"))
    dependsOn(gradle.includedBuild("keepanno").task(":jar"))
    dependsOn(gradle.includedBuild("resourceshrinker").task(":jar"))
    dependsOn(gradle.includedBuild("resourceshrinker").task(":depsJar"))
    from(testDependencies().map(::zipTree))
    from(resourceShrinkerDepsJarTask.outputs.getFiles().map(::zipTree))
    from(keepAnnoJarTask.outputs.getFiles().map(::zipTree))
    exclude("com/android/tools/r8/keepanno/annotations/**")
    exclude("androidx/annotation/keep/**")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    archiveFileName.set("deps.jar")
  }
}
