#!/bin/bash
#
# Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

echo "This must run in frameworks/support of an androidx repo"
find . -name 'build.gradle' | xargs grep consumerProguardFiles | awk '{ print substr($1,1,length($1)-13) "proguard-rules.pro" }' | xargs cat
find . -name 'build.gradle.kts' | xargs grep consumerProguardFiles | awk '{ print substr($1,1,length($1)-17) "proguard-rules.pro" }' | xargs cat
