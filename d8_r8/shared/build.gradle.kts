// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

plugins {
  `kotlin-dsl`
  id("dependencies-plugin")
}

val enableDownloadDeps = !project.hasProperty("disable_download_deps")

tasks {

  val downloadDeps by registering(DownloadAllDependenciesTask::class) {
    this.setDependencies(getRoot(), allPublicDependencies())
    onlyIf { enableDownloadDeps }
  }

  val downloadTestDeps by registering(DownloadAllDependenciesTask::class) {
    this.setDependencies(getRoot(), allPublicTestDependencies())
    onlyIf { enableDownloadDeps }
  }

  val downloadDepsInternal by registering(DownloadAllDependenciesTask::class) {
    this.setDependencies(getRoot(), allInternalDependencies())
    onlyIf { enableDownloadDeps }
  }

  val downloadTestDepsInternal by registering(DownloadAllDependenciesTask::class) {
    this.setDependencies(getRoot(), allInternalTestDependencies())
    onlyIf { enableDownloadDeps }
  }
}
