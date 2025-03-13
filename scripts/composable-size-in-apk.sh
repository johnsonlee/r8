#!/bin/bash
#
# Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

SCRIPTS_DIR=$(dirname "$0")
BUILD_DIR=$(realpath "$SCRIPTS_DIR/../build")

java -cp $BUILD_DIR/libs/r8.jar:$BUILD_DIR/libs/test_deps_all.jar \
  $SCRIPTS_DIR/ComposableSizeInApk.java $1
