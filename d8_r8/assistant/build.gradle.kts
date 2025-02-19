// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

plugins {
  `kotlin-dsl`
  id("dependencies-plugin")
}

dependencies {
  compileOnly(":keepanno")
}

java {
  sourceSets.main.configure {
    java.srcDir(getRoot().resolveAll("src", "assistant", "java"))
  }
  sourceCompatibility = JvmCompatibility.sourceCompatibility
  targetCompatibility = JvmCompatibility.targetCompatibility
  toolchain {
    languageVersion = JavaLanguageVersion.of(JvmCompatibility.release)
  }
  withSourcesJar()
}

tasks.withType<Jar> {
  destinationDirectory.set(getRoot().resolveAll("build", "libs"))
  archiveFileName.set("assistant.jar")
}
