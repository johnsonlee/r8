#!/usr/bin/env python3
# Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import argparse
import os
import shutil
import sys
import utils


KOTLIN_EXTENSION = '.kt'


def parse_options():
    parser = argparse.ArgumentParser(description='Update androidx keep annotations')
    parser.add_argument('--androidx',
                        metavar=('<path>'),
                        required=True,
                        help='Path to the androidx checkout')
    return parser.parse_args()


def main():
    args = parse_options()

    source_dir = os.path.join(
        utils.REPO_ROOT,
        'src',
        'keepanno',
        'java',
        'androidx',
        'annotation',
        'keep')
    dest_dir = os.path.join(
        args.androidx,
        'frameworks',
        'support',
        'annotation',
        'annotation-keep',
        'src',
        'commonMain',
        'kotlin',
        'androidx',
        'annotation',
        'keep')

    for root, dirnames, filenames in os.walk(source_dir):
        if root != source_dir:
            print('Unexpected subdirectory under {source_dir}.'
                .format(source_dir=source_dir))
        if len(dirnames) > 0:
            print('Unexpected subdirectories under {dirnames}.'
                .format(dirnames=dirnames))
        for filename in filenames:
            if not filename.endswith(KOTLIN_EXTENSION):
                print('Unexpected non Kotlin file {filename}.'
                    .format(filename=os.path.join(root, filename)))
                sys.exit(1)

            shutil.copyfile(
                os.path.join(root, filename),
                os.path.join(dest_dir, filename))


if __name__ == '__main__':
    sys.exit(main())
