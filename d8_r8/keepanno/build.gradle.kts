// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

import com.google.protobuf.gradle.proto
import com.google.protobuf.gradle.ProtobufExtension
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform

plugins {
  `kotlin-dsl`
  id("dependencies-plugin")
}

// It seems like the use of a local maven repo does not allow adding the plugin with the id+version
// syntax. Also, for some reason the 'protobuf' extension object cannot be directly referenced.
// This configures the plugin "old style" and pulls out the extension object manually.
buildscript {
  dependencies {
    classpath("com.google.protobuf:protobuf-gradle-plugin:0.9.4")
  }
}
apply(plugin = "com.google.protobuf")
var os = DefaultNativePlatform.getCurrentOperatingSystem()
var protobuf = project.extensions.getByName("protobuf") as ProtobufExtension
protobuf.protoc {
  if (os.isLinux) {
    path = getRoot().resolveAll("third_party", "protoc", "linux-x86_64", "bin", "protoc").path
  } else if (os.isMacOsX) {
    path = getRoot().resolveAll("third_party", "protoc", "osx-x86_64", "bin", "protoc").path
  } else {
    assert(os.isWindows);
    path = getRoot().resolveAll("third_party", "protoc", "win64", "bin", "protoc.exe").path
  }
}

java {
  sourceSets.main.configure {
    java.srcDir(getRoot().resolveAll("src", "keepanno", "java"))
    proto {
      srcDir(getRoot().resolveAll("src", "keepanno", "proto"))
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
  compileOnly(Deps.asm)
  compileOnly(Deps.guava)
  compileOnly(Deps.protobuf)
}

tasks {
  val keepAnnoAnnotationsJar by registering(Jar::class) {
    dependsOn(gradle.includedBuild("shared").task(":downloadDeps"))
    from(sourceSets.main.get().output)
    include("com/android/tools/r8/keepanno/annotations/*")
    destinationDirectory.set(getRoot().resolveAll("build", "libs"))
    archiveFileName.set("keepanno-annotations.jar")
  }

  val keepAnnoJar by registering(Jar::class) {
    dependsOn(gradle.includedBuild("shared").task(":downloadDeps"))
    from(sourceSets.main.get().output)
  }

  val keepAnnoAnnotationsDoc by registering(Javadoc::class) {
    source = sourceSets.main.get().allJava
    include("com/android/tools/r8/keepanno/annotations/*")
  }
}
