#!/usr/bin/env python3
# Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import argparse
import json
import os
import shutil
import subprocess
import sys

import utils

BUCKET = "r8-perf-results"
SAMPLE_BENCHMARK_RESULT_JSON = {
    'benchmark_name': '<benchmark_name>',
    'results': [{
        'code_size': 0,
        'runtime': 0
    }]
}

# Result structure on cloud storage
# gs://bucket/benchmark_results/APP/TARGET/GIT_HASH/results
#                                                   meta
# where results simply contains the result lines and
# meta contains information about the execution (machine)


def ParseOptions():
    result = argparse.ArgumentParser()
    result.add_argument('--app',
                        help='Specific app(s) to measure.',
                        action='append')
    result.add_argument('--iterations',
                        help='How many times run_benchmark is run.',
                        type=int,
                        default=1)
    result.add_argument('--iterations-inner',
                        help='How many iterations to run inside run_benchmark.',
                        type=int,
                        default=10)
    result.add_argument('--outdir',
                        help='Output directory for running locally.')
    result.add_argument('--skip-if-output-exists',
                        help='Skip if output exists.',
                        action='store_true',
                        default=False)
    result.add_argument('--target',
                        help='Specific target to run on.',
                        default='r8-full',
                        choices=['d8', 'r8-full', 'r8-force', 'r8-compat'])
    result.add_argument('--verbose',
                        help='To enable verbose logging.',
                        action='store_true',
                        default=False)
    result.add_argument('--version',
                        '-v',
                        help='Use R8 hash for the run (default local build)',
                        default=None)
    options, args = result.parse_known_args()
    options.apps = options.app or ['NowInAndroidApp', 'TiviApp']
    options.quiet = not options.verbose
    del options.app
    return options, args


def MergeBenchmarkResultJsonFiles(benchmark_result_json_files):
    merged_benchmark_result_json = None
    for benchmark_result_json_file in benchmark_result_json_files:
        benchmark_result_json = ParseBenchmarkResultJsonFile(
            benchmark_result_json_file)
        if merged_benchmark_result_json is None:
            merged_benchmark_result_json = benchmark_result_json
        else:
            MergeBenchmarkResultJsonFile(merged_benchmark_result_json,
                                         benchmark_result_json)
    return merged_benchmark_result_json


def MergeBenchmarkResultJsonFile(merged_benchmark_result_json,
                                 benchmark_result_json):
    assert benchmark_result_json.keys() == SAMPLE_BENCHMARK_RESULT_JSON.keys()
    assert merged_benchmark_result_json[
        'benchmark_name'] == benchmark_result_json['benchmark_name']
    merged_benchmark_result_json['results'].extend(
        benchmark_result_json['results'])


def ParseBenchmarkResultJsonFile(result_json_file):
    with open(result_json_file, 'r') as f:
        lines = f.readlines()
        return json.loads(''.join(lines))


def GetArtifactLocation(app, target, version, filename):
    version_or_head = version or utils.get_HEAD_sha1()
    return f'{app}/{target}/{version_or_head}/{filename}'


def GetGSLocation(filename):
    return f'gs://{BUCKET}/{filename}'


def ArchiveOutputFile(file, dest, outdir=None):
    if outdir:
        dest_in_outdir = os.path.join(outdir, dest)
        os.makedirs(os.path.dirname(dest_in_outdir), exist_ok=True)
        shutil.copyfile(file, dest_in_outdir)
    else:
        utils.upload_file_to_cloud_storage(file, GetGSLocation(dest))


# Usage with historic_run.py:
# ./tools/historic_run.py
#     --cmd "perf.py --skip-if-output-exists --version"
#     --timeout -1
#     --top 3373fd18453835bf49bff9f02523a507a2ebf317
#     --bottom 7486f01e0622cb5935b77a92b59ddf1ca8dbd2e2
def main():
    options, args = ParseOptions()
    with utils.TempDir() as temp:
        for app in options.apps:
            if options.skip_if_output_exists:
                if options.outdir:
                    raise NotImplementedError
                output = GetGSLocation(
                    GetArtifactLocation(app, options.target, options.version,
                                        'result.json'))
                if utils.cloud_storage_exists(output):
                    print(f'Skipping run, {output} already exists.')
                    continue

            base_cmd = [
                'tools/run_benchmark.py', '--benchmark', app, '--target',
                options.target
            ]
            if options.verbose:
                base_cmd.append('--verbose')
            if options.version:
                base_cmd.extend(['--version', options.version])

            # Build
            utils.Print(f'Preparing {app}', quiet=options.quiet)
            build_cmd = base_cmd + ['--iterations', '0']
            subprocess.check_output(build_cmd)

            # Run benchmark.
            benchmark_result_json_files = []
            for i in range(options.iterations):
                utils.Print(f'Benchmarking {app} ({i+1}/{options.iterations})',
                            quiet=options.quiet)
                benchhmark_result_file = os.path.join(temp, f'result_file_{i}')
                iteration_cmd = base_cmd + [
                    '--iterations',
                    str(options.iterations_inner), '--output',
                    benchhmark_result_file, '--no-build'
                ]
                subprocess.check_output(iteration_cmd)
                benchmark_result_json_files.append(benchhmark_result_file)

            # Merge results and write output.
            result_file = os.path.join(temp, 'result_file')
            with open(result_file, 'w') as f:
                json.dump(
                    MergeBenchmarkResultJsonFiles(benchmark_result_json_files),
                    f)
            ArchiveOutputFile(
                result_file,
                GetArtifactLocation(app, options.target, options.version,
                                    'result.json'), options.outdir)

            # Write metadata.
            if os.environ.get('SWARMING_BOT_ID'):
                meta_file = os.path.join(temp, "meta")
                with open(meta_file, 'w') as f:
                    f.write("Produced by: " + os.environ.get('SWARMING_BOT_ID'))
                ArchiveOutputFile(
                    meta_file,
                    GetArtifactLocation(app, options.target, options.version,
                                        'meta'), options.outdir)


if __name__ == '__main__':
    sys.exit(main())
