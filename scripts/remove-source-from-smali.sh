#!/bin/bash
#
# Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

find $1 -name '*.smali' -exec sed -i '/^\.source/d' {} +
