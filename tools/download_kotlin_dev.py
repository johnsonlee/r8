#!/usr/bin/env python3
# Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import utils
if utils.is_python3():
    from html.parser import HTMLParser
    import urllib.request
    url_request = urllib.request
else:
    from HTMLParser import HTMLParser
    import urllib
    url_request = urllib
import os
import sys
import xml.etree.ElementTree


JETBRAINS_KOTLIN_MAVEN_URL = "https://maven.pkg.jetbrains.space/kotlin/p/" \
                             "kotlin/bootstrap/org/jetbrains/kotlin/"
KOTLIN_RELEASE_URL = JETBRAINS_KOTLIN_MAVEN_URL + "kotlin-compiler/"
KOTLINC_LIB = os.path.join(utils.THIRD_PARTY, "kotlin",
                   "kotlin-compiler-dev", "kotlinc", "lib")


def download_newest():
    response = url_request.urlopen(KOTLIN_RELEASE_URL)
    if response.getcode() != 200:
        raise Exception('Url: %s \n returned %s' %
                        (KOTLIN_RELEASE_URL, response.getcode()))
    content = str(response.read())
    release_candidates = []

    class HTMLContentParser(HTMLParser):

        def handle_data(self, data):
            if ('-dev-' in data):
                release_candidates.append(data)

    parser = HTMLContentParser()
    parser.feed(content)

    top_most_version = (0, 0, 0, 0)
    top_most_version_and_build = None

    for version in release_candidates:
        # The compiler version is on the form <major>.<minor>.<revision>-dev-<build>/
        version = version.replace('/', '')
        version_build_args = version.split('-')
        version_components = version_build_args[0].split('.')
        version_components.append(version_build_args[2])
        current_version = tuple(map(int, version_components))
        if (current_version > top_most_version):
            top_most_version = current_version
            top_most_version_and_build = version

    if (top_most_version_and_build is None):
        raise Exception('Url: %s \n returned %s' %
                        (KOTLIN_RELEASE_URL, response.getcode()))

    # Download checked in kotlin dev compiler before owerlaying with the new.
    # TODO(sgjesse): This should just ensure an empty directory instead of
    # relying on overlaying and reusing some jars.
    utils.DownloadFromGoogleCloudStorage(
        os.path.join(utils.THIRD_PARTY, "kotlin",
                     "kotlin-compiler-dev.tar.gz.sha1"))

    # Check POM for expected dependencies.
    check_pom(top_most_version_and_build)

    # We can now download all files related to the kotlin compiler version.
    print("Downloading version: " + top_most_version_and_build)

    download_and_save(
        JETBRAINS_KOTLIN_MAVEN_URL +
        "kotlin-compiler/{0}/kotlin-compiler-{0}.jar".format(
            top_most_version_and_build), KOTLINC_LIB, "kotlin-compiler.jar")
    download_and_save(
        JETBRAINS_KOTLIN_MAVEN_URL +
        "kotlin-stdlib/{0}/kotlin-stdlib-{0}.jar".format(
            top_most_version_and_build), KOTLINC_LIB, "kotlin-stdlib.jar")
    download_and_save(
        JETBRAINS_KOTLIN_MAVEN_URL +
        "kotlin-stdlib-jdk8/{0}/kotlin-stdlib-jdk8-{0}.jar".format(
            top_most_version_and_build), KOTLINC_LIB, "kotlin-stdlib-jdk8.jar")
    # POM file has dependency on version 1.6.10 - download latest anyway.
    download_and_save(
        JETBRAINS_KOTLIN_MAVEN_URL +
        "kotlin-reflect/{0}/kotlin-reflect-{0}.jar".format(
            top_most_version_and_build), KOTLINC_LIB, "kotlin-reflect.jar")
    download_and_save(
        JETBRAINS_KOTLIN_MAVEN_URL +
        "kotlin-script-runtime/{0}/kotlin-script-runtime-{0}.jar".format(
            top_most_version_and_build), KOTLINC_LIB,
        "kotlin-script-runtime.jar")
    download_and_save(
        "https://repo1.maven.org/maven2/org/jetbrains/kotlinx/kotlinx-coroutines-core-jvm/1.8.0/kotlinx-coroutines-core-jvm-1.8.0.jar", KOTLINC_LIB, "kotlinx-coroutines-core-jvm.jar")
    download_and_save(
        "https://repo1.maven.org/maven2/org/jetbrains/intellij/deps/trove4j/1.0.20200330/trove4j-1.0.20200330.jar", KOTLINC_LIB, "trove4j.jar")


def check_pom(top_most_version_and_build):
    # Download POM, and check the expected dependencies.
    download_and_save(
        JETBRAINS_KOTLIN_MAVEN_URL +
        "kotlin-compiler/{0}/kotlin-compiler-{0}.pom".format(
            top_most_version_and_build), KOTLINC_LIB, "kotlin-compiler.pom")
    pom_file = os.path.join(KOTLINC_LIB, "kotlin-compiler.pom")
    ns = "http://maven.apache.org/POM/4.0.0"
    xml.etree.ElementTree.register_namespace('', ns)
    tree = xml.etree.ElementTree.ElementTree()
    tree.parse(pom_file)
    #return tree.getroot().find("{%s}dependencies" % ns).text
    for dependency in tree.getroot().find("{%s}dependencies" % ns):
        groupId = dependency.find("{%s}groupId" % ns).text
        artifactId = dependency.find("{%s}artifactId" % ns).text
        version = dependency.find("{%s}version" % ns).text
        coordinates = (
            '{groupId}:{artifactId}:{version}'
                .format(groupId=groupId, artifactId=artifactId, version=version))
        print('Dependecy: ' + coordinates)
        expected_dependencies = set()
        for artifactId in ("kotlin-stdlib", "kotlin-stdlib-jdk8", "kotlin-script-runtime"):
            expected_dependencies.add(
                'org.jetbrains.kotlin:{artifactId}:{version}'
                    .format(artifactId=artifactId, version=top_most_version_and_build))
        expected_dependencies.add('org.jetbrains.kotlin:kotlin-reflect:1.6.10')
        expected_dependencies.add('org.jetbrains.intellij.deps:trove4j:1.0.20200330')
        expected_dependencies.add('org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.8.0')
        if not coordinates in expected_dependencies:
            raise Exception('Unexpected dependency: ' + coordinates)


def download_and_save(url, path, name):
    print('Downloading: ' + url)
    url_request.urlretrieve(url, os.path.join(path, name))


if __name__ == '__main__':
    sys.exit(download_newest())
