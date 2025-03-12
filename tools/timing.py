#!/usr/bin/env python3
# Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

from argparse import Namespace
import time

class Timing:

    def __init__(self):
        self.active_timing = None
        self.committed = []
        self.created_ns = time.time_ns()

    def begin(self, title):
        assert self.active_timing is None
        self.active_timing = Namespace(title=title, start_ns=time.time_ns())

    def end(self):
        assert self.active_timing is not None
        self.committed.append(Namespace(
            title=self.active_timing.title,
            duration_ns= time.time_ns() - self.active_timing.start_ns))
        self.active_timing = None

    def report(self):
        assert self.active_timing is None
        total_duration_ns = time.time_ns() - self.created_ns
        timed_duration_ns = 0
        for timing in self.committed:
            print(f'{timing.title}: {timing.duration_ns // 1_000_000}ms')
            timed_duration_ns = timed_duration_ns + timing.duration_ns
        unaccounted_ns = total_duration_ns - timed_duration_ns
        print(f'Unaccounted: {unaccounted_ns // 1_000_000}ms')
