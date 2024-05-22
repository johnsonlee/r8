#!/usr/bin/env python3
# Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import argparse
import sys


def ParseOptions():
    result = argparse.ArgumentParser()
    result.add_argument('--app',
                        help='Specific app to meassure, default all',
                        default='all')
    return result.parse_known_args()

def main():
    (options, args) = ParseOptions()
    print('Running performance on app %s' % options.app)

if __name__ == '__main__':
    sys.exit(main())
