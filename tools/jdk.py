#!/usr/bin/env python3
# Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import os
import sys

import defines

JDK_DIRS = os.path.join(defines.THIRD_PARTY, 'openjdk')

ALL_JDKS = ['openjdk-9.0.4', 'jdk-11', 'jdk-17', 'jdk-21', 'jdk-25']


def GetDefaultJdkHome():
    return GetJdk11Home()


def GetJdkHome(name):
    if name == 'jdk8':
        return GetJdk8Home()
    third_party_jdk_root = os.path.join(JDK_DIRS, name)
    if not os.path.exists(third_party_jdk_root):
        raise Exception('No JDKs found in ' + third_party_jdk_root)
    os_root = GetOSJavaHome(third_party_jdk_root)
    if not os.path.exists(os_root):
        raise Exception('No platform JDK found in ' + os_root)
    return os_root


def GetOSJavaHome(root):
    if defines.IsLinux():
        return os.path.join(root, 'linux')
    elif defines.IsOsX():
        return os.path.join(root, 'osx', 'Contents', 'Home')
    elif defines.IsWindows():
        return os.path.join(root, 'windows')
    else:
        raise Exception(
            'Unsupported platform'
            ' (not detected as either of Linux, macOS or Windows)')


def GetAllJdkDirs():
    dirs = []
    for jdk in ALL_JDKS:
        root = os.path.join(JDK_DIRS, jdk)
        if defines.IsLinux():
            root = os.path.join(root, 'linux')
        elif defines.IsOsX():
            root = os.path.join(root, 'osx')
        elif defines.IsWindows():
            root = os.path.join(root, 'windows')
        else:
            raise Exception(
                'Unsupported platform'
                ' (not detected as either of Linux, macOS or Windows)')
        # Some jdks are not available on windows, don't try to get these.
        if os.path.exists(root + '.tar.gz.sha1'):
            dirs.append(root)
    return dirs


def GetJdk17Home():
    return GetJdkHome('jdk-17')


def GetJdk11Home():
    return GetJdkHome('jdk-11')


def GetJdk9Home():
    return GetJdkHome('openjdk-9.0.4')


def GetJdk8Home():
    root = os.path.join(JDK_DIRS, 'jdk8')
    if defines.IsLinux():
        return os.path.join(root, 'linux-x86')
    elif defines.IsOsX():
        return os.path.join(root, 'darwin-x86')
    else:
        return os.environ['JAVA_HOME']


def GetJavaExecutable(jdkHome=None):
    jdkHome = jdkHome if jdkHome else GetDefaultJdkHome()
    executable = 'java.exe' if defines.IsWindows() else 'java'
    return os.path.join(jdkHome, 'bin', executable) if jdkHome else executable


def GetJavacExecutable(jdkHome=None):
    jdkHome = jdkHome if jdkHome else GetDefaultJdkHome()
    executable = 'javac.exe' if defines.IsWindows() else 'javac'
    return os.path.join(jdkHome, 'bin', executable) if jdkHome else executable


def GetJstackExecutable(jdkHome=None):
    jdkHome = jdkHome if jdkHome else GetDefaultJdkHome()
    executable = 'jstack.exe' if defines.IsWindows() else 'jstack'
    return os.path.join(jdkHome, 'bin', executable) if jdkHome else executable


def Main():
    print(GetDefaultJdkHome())


if __name__ == '__main__':
    sys.exit(Main())
