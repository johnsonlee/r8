#!/usr/bin/env python3
# Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import argparse
import sys


def ParseOptions():
    parser = argparse.ArgumentParser()
    parser.add_argument('--map',
                        help='Input mapping file to use for synthesizing new '
                             'mapping file.',
                        required=True)
    parser.add_argument('--output',
                        help='Path where to write output mapping file.',
                        required=True)
    parser.add_argument('--output-size',
                        '--output_size',
                        help='The desired output size in MB.',
                        required=True,
                        type=int)
    return parser.parse_args()


def encode(s):
    return s.encode('utf-8')


def IsSectionStart(line):
    return ' -> ' in line and line.endswith(':\n')


def ParseSectionStart(line):
    split = line.strip()[:-1].split(' -> ')
    assert len(split) == 2
    return (split[0], split[1])


def EnsureUnique(name, indices):
    index = indices.get(name, 0)
    indices[name] = index + 1
    return name if index == 0 else name + '$' + str(index)


def main():
    options = ParseOptions()
    header = []
    sections = []
    with open(options.map) as f:
        past_header = False
        current_section = None
        lineno = -1
        for line in f.readlines():
            lineno = lineno + 1
            if not past_header:
                if line.startswith('#'):
                    header.append(encode(line))
                    continue
                else:
                    past_header = True
            if len(line.strip()) == 0:
                continue
            if IsSectionStart(line):
                if current_section is not None:
                    sections.append(current_section)
                current_section = [ParseSectionStart(line)]
            else:
                assert current_section is not None, lineno
                current_section.append(encode(line))
        if current_section is not None:
            sections.append(current_section)

    left_indices = {}
    right_indices = {}
    newline = encode('\n')
    with open(options.output, 'wb') as f:
        size = 0
        for line in header:
            f.write(line)
            size = size + len(line)
        target_size = options.output_size * 1024 * 1024
        while size < target_size:
            for section in sections:
                (left, right) = section[0]
                section_header = encode(EnsureUnique(left, left_indices) + ' -> ' + EnsureUnique(right, right_indices) + ':\n')
                f.write(section_header)
                size = size + len(section_header)
                for line in section[1:]:
                    f.write(line)
                    size = size + len(line)
                f.write(newline)
                size = size + len(newline)
                if size >= target_size:
                    break


if __name__ == '__main__':
    sys.exit(main())
