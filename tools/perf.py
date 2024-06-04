#!/usr/bin/env python3
# Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import argparse
import os
import subprocess
import sys

import utils

DEFAULT_ITERATIONS = 10
BUCKET = "r8-perf-results"

# Result structure on cloud storage
# gs://bucket/benchmark_results/APP/TARGET/GIT_HASH/results
#                                                   meta
# where results simply contains the result lines and
# meta contains information about the execution (machine)

def ParseOptions():
    result = argparse.ArgumentParser()
    result.add_argument('--app',
                        help='Specific app to measure.',
                        default='NowInAndroidApp')
    result.add_argument('--target',
                        help='Specific target to run on.',
                        default='r8-full',
                        choices=['d8', 'r8-full', 'r8-force', 'r8-compat'])
    result.add_argument('--iterations',
                        help='How many iterations to run.',
                        type=int,
                        default=DEFAULT_ITERATIONS)
    return result.parse_known_args()


def ParseOutput(output, options, log_array):
    for line in output.decode('utf-8').splitlines():
        print("   -- " + line)
        # Output lines look like:
        #    Benchmark results for NowInAndroidApp on target r8-full
        #      warmup reporting mode: average
        #      warmup iterations: 1
        #      warmup total time: 58580 ms
        #      benchmark reporting mode: average
        #      benchmark iterations: 1
        #      benchmark total time: 39613 ms
        #    NowInAndroidApp(RunTimeRaw): 39154 ms
        #    NowInAndroidApp(CodeSize): 5102196
        if line.startswith(options.app + "("):
            log_array.append(line)


def GetGSLocation(app, target, filename):
    return "gs://%s/%s/%s/%s/%s" % (BUCKET, app, target,
                                    utils.get_HEAD_sha1(), filename)

def main():
    (options, args) = ParseOptions()
    cmd = ['tools/run_benchmark.py', '--target', options.target,
           '--benchmark', options.app]
    log_array = []
    # Build and warmup
    output = subprocess.check_output(cmd)
    ParseOutput(output, options, log_array)
    cmd.append('--no-build')
    for i in range(options.iterations):
        output = subprocess.check_output(cmd)
        ParseOutput(output, options, log_array)
    with utils.TempDir() as temp:
        result_file = os.path.join(temp, "result_file")
        with open(result_file, 'w') as f:
            for l in log_array:
                f.write(l + "\n")
        utils.upload_file_to_cloud_storage(result_file,
            GetGSLocation(options.app, options.target, 'results'))
        if os.environ.get('SWARMING_BOT_ID'):
            meta_file = os.path.join(temp, "meta")
            with open(meta_file, 'w') as f:
                f.write("Produced by: " + os.environ.get('SWARMING_BOT_ID'))
            utils.upload_file_to_cloud_storage(meta_file,
                GetGSLocation(options.app, options.target, 'meta'))


if __name__ == '__main__':
    sys.exit(main())
