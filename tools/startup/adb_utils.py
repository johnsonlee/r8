#!/usr/bin/env python3
# Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import argparse
import os
import subprocess
import sys
import threading
import time

from enum import Enum

sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import adb
import profile_utils
import utils

DEVNULL = subprocess.DEVNULL


def parse_options(argv):
    result = argparse.ArgumentParser(description='Run adb utils.')
    result.add_argument('--capture-screen', help='Capture screen to given file')
    result.add_argument('--device-id', help='Device id (e.g., emulator-5554).')
    result.add_argument('--device-pin', help='Device pin code (e.g., 1234)')
    result.add_argument('--ensure-screen-off',
                        help='Ensure screen off',
                        action='store_true',
                        default=False)
    result.add_argument('--get-screen-state',
                        help='Get screen state',
                        action='store_true',
                        default=False)
    result.add_argument('--unlock',
                        help='Unlock device',
                        action='store_true',
                        default=False)
    options, args = result.parse_known_args(argv)
    return options, args


def main(argv):
    (options, args) = parse_options(argv)
    if options.capture_screen:
        capture_screen(options.capture_screen, options.device_id)
    if options.ensure_screen_off:
        ensure_screen_off(options.device_id)
    elif options.get_screen_state:
        print(get_screen_state(options.device_id))
    elif options.unlock:
        unlock(options.device_id, options.device_pin)


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
