#!/usr/bin/env python3
# Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import argparse
import subprocess
import sys

DEFAULT_ITERATIONS = 10

def ParseOptions():
    result = argparse.ArgumentParser()
    result.add_argument('--app',
                        help='Specific app to measure.',
                        default='NowInAndroidApp')
    result.add_argument('--iterations',
                        help='How many iterations to run.',
                        type=int,
                        default=DEFAULT_ITERATIONS)
    return result.parse_known_args()

def main():
    (options, args) = ParseOptions()
    cmd = ['tools/run_benchmark.py', '--target', 'r8-full', '--benchmark',
           options.app]
    # Build and warmup
    subprocess.check_call(cmd)
    cmd.append('--no-build')
    for i in range(options.iterations):
        subprocess.check_call(cmd)

if __name__ == '__main__':
    sys.exit(main())
