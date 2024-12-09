#!/usr/bin/env python3
# Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import argparse
import compiledump
import json
import os
import shutil
import subprocess
import sys

import utils
if utils.is_bot():
    import upload_benchmark_data_to_google_storage

# A collection of benchmarks that should be run on the perf bot.
EXTERNAL_BENCHMARKS = {
    'ChromeApp': {
        'targets': ['r8-full']
    },
    'CraneApp': {
        'targets': ['r8-full']
    },
    'HelloWorld': {
        'targets': ['d8']
    },
    'HelloWorldNoLib': {
        'targets': ['d8']
    },
    'HelloWorldCf': {
        'targets': ['d8']
    },
    'HelloWorldCfNoLib': {
        'targets': ['d8']
    },
    'JetLaggedApp': {
        'targets': ['r8-full']
    },
    'JetNewsApp': {
        'targets': ['r8-full']
    },
    'JetCasterApp': {
        'targets': ['r8-full']
    },
    'JetChatApp': {
        'targets': ['r8-full']
    },
    'JetSnackApp': {
        'targets': ['r8-full']
    },
    'NowInAndroidApp': {
        'targets': ['d8', 'r8-full']
    },
    'NowInAndroidAppRelease': {
        'targets': ['d8']
    },
    'NowInAndroidAppIncremental': {
        'targets': ['d8'],
        'subBenchmarks': {
            'd8': ['Dex', 'Merge']
        }
    },
    'NowInAndroidAppNoJ$': {
        'targets': ['d8']
    },
    'NowInAndroidAppNoJ$Incremental': {
        'targets': ['d8'],
        'subBenchmarks': {
            'd8': ['Dex']
        }
    },
    'NowInAndroidAppNoJ$Release': {
        'targets': ['d8']
    },
    'OwlApp': {
        'targets': ['r8-full']
    },
    'R8': {
        'targets': ['retrace']
    },
    'ReplyApp': {
        'targets': ['r8-full']
    },
    'TiviApp': {
        'targets': ['r8-full']
    },
}
# A collection of internal benchmarks that should be run on the internal bot.
INTERNAL_BENCHMARKS = {
    'SystemUIApp': {'targets': ['r8-full']},
}
# A collection of benchmarks that should not be run on the bots, but can be used
# for running locally.
LOCAL_BENCHMARKS = {
    'SystemUIAppTreeShaking': {'targets': ['r8-full']}}
ALL_BENCHMARKS = {}
ALL_BENCHMARKS.update(EXTERNAL_BENCHMARKS)
ALL_BENCHMARKS.update(INTERNAL_BENCHMARKS)
ALL_BENCHMARKS.update(LOCAL_BENCHMARKS)
BUCKET = "r8-perf-results"
SAMPLE_BENCHMARK_RESULT_JSON = {
    'benchmark_name': '<benchmark_name>',
    'results': [{
        'code_size': 0,
        'runtime': 0
    }]
}


# Result structure on cloud storage
# gs://bucket/benchmark_results/APP/TARGET/GIT_HASH/result.json
#                                                   meta
# where results simply contains the result lines and
# meta contains information about the execution (machine)
def ParseOptions():
    result = argparse.ArgumentParser()
    result.add_argument('--benchmark',
                        help='Specific benchmark(s) to measure.',
                        action='append')
    result.add_argument('--internal',
                        help='Run internal benchmarks.',
                        action='store_true',
                        default=False)
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
    result.add_argument(
        '--target',
        help='Specific target to run on.',
        choices=['d8', 'r8-full', 'r8-force', 'r8-compat', 'retrace'])
    result.add_argument('--verbose',
                        help='To enable verbose logging.',
                        action='store_true',
                        default=False)
    result.add_argument('--version',
                        '-v',
                        help='Use R8 hash for the run (default local build)')
    result.add_argument('--version-jar',
                        help='The r8lib.jar for the given version.')
    options, args = result.parse_known_args()
    if options.benchmark:
        options.benchmarks = options.benchmark
    elif options.internal:
        options.benchmarks = INTERNAL_BENCHMARKS.keys()
    else:
        options.benchmarks = EXTERNAL_BENCHMARKS.keys()
    options.quiet = not options.verbose
    del options.benchmark
    return options, args


def Build(options):
    utils.Print('Building', quiet=options.quiet)
    target = options.target or 'r8-full'
    build_cmd = GetRunCmd('N/A', target, options, ['--iterations', '0'])
    subprocess.check_call(build_cmd)


def GetRunCmd(benchmark, target, options, args, r8jar=None):
    base_cmd = [
        'tools/run_benchmark.py', '--benchmark', benchmark, '--target', target
    ]
    if options.verbose:
        base_cmd.append('--verbose')
    if options.version and r8jar is not None:
        base_cmd.extend(
            ['--version', options.version, '--version-jar', r8jar, '--nolib'])
    return base_cmd + args


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


def GetArtifactLocation(benchmark, target, version, filename):
    version_or_head = version or utils.get_HEAD_sha1()
    return f'{benchmark}/{target}/{version_or_head}/{filename}'


def GetGSLocation(filename, bucket=BUCKET):
    return f'gs://{bucket}/{filename}'


def ArchiveBenchmarkResult(benchmark, target, benchmark_result_json_files,
                           options, temp):
    result_file = os.path.join(temp, 'result_file')
    with open(result_file, 'w') as f:
        json.dump(MergeBenchmarkResultJsonFiles(benchmark_result_json_files), f)
    ArchiveOutputFile(result_file,
                      GetArtifactLocation(benchmark, target, options.version,
                                          'result.json'),
                      outdir=options.outdir)


def ArchiveOutputFile(file, dest, bucket=BUCKET, header=None, outdir=None):
    if outdir:
        dest_in_outdir = os.path.join(outdir, dest)
        os.makedirs(os.path.dirname(dest_in_outdir), exist_ok=True)
        shutil.copyfile(file, dest_in_outdir)
    else:
        utils.upload_file_to_cloud_storage(file,
                                           GetGSLocation(dest, bucket=bucket),
                                           header=header)


# Usage with historic_run.py:
# ./tools/historic_run.py
#     --cmd "perf.py --skip-if-output-exists --version"
#     --timeout -1
#     --top 3373fd18453835bf49bff9f02523a507a2ebf317
#     --bottom 7486f01e0622cb5935b77a92b59ddf1ca8dbd2e2
def main():
    options, args = ParseOptions()
    Build(options)
    any_failed = False
    with utils.TempDir() as temp:
        if options.version:
            # Download r8.jar once instead of once per run_benchmark.py invocation.
            download_options = argparse.Namespace(no_build=True, nolib=True)
            r8jar = options.version_jar or compiledump.download_distribution(
                options.version, download_options, temp)
        else:
            r8jar = None
        for benchmark in options.benchmarks:
            benchmark_info = ALL_BENCHMARKS[benchmark]
            targets = [options.target
                      ] if options.target else benchmark_info['targets']
            for target in targets:
                sub_benchmarks = benchmark_info.get('subBenchmarks', {})
                sub_benchmarks_for_target = sub_benchmarks.get(target, [])

                if options.skip_if_output_exists:
                    assert len(sub_benchmarks_for_target) == 0, 'Unimplemented'
                    if options.outdir:
                        raise NotImplementedError
                    output = GetGSLocation(
                        GetArtifactLocation(benchmark, target, options.version,
                                            'result.json'))
                    if utils.cloud_storage_exists(output):
                        print(f'Skipping run, {output} already exists.')
                        continue

                # Run benchmark.
                if sub_benchmarks_for_target:
                    benchmark_result_json_files = {}
                    for sub_benchmark in sub_benchmarks_for_target:
                        benchmark_result_json_files[sub_benchmark] = []
                else:
                    benchmark_result_json_files = []

                # Prepare out dir.
                temp_benchmark_target = os.path.join(temp, benchmark, target)
                os.makedirs(temp_benchmark_target)

                failed = False
                for i in range(options.iterations):
                    utils.Print(
                        f'Benchmarking {benchmark} ({i+1}/{options.iterations})',
                        quiet=options.quiet)
                    if sub_benchmarks_for_target:
                        benchmark_result_file = os.path.join(
                            temp_benchmark_target, f'result_{i}')
                        os.makedirs(benchmark_result_file)
                    else:
                        benchmark_result_file = os.path.join(
                            temp_benchmark_target, f'result_file_{i}')
                    iteration_cmd = GetRunCmd(benchmark, target, options, [
                        '--iterations',
                        str(options.iterations_inner), '--output',
                        benchmark_result_file, '--no-build'
                    ], r8jar)
                    try:
                        subprocess.check_call(iteration_cmd)
                        if sub_benchmarks_for_target:
                            for sub_benchmark in sub_benchmarks_for_target:
                                sub_benchmark_result_file = os.path.join(
                                    benchmark_result_file,
                                    benchmark + sub_benchmark)
                                benchmark_result_json_files[
                                    sub_benchmark].append(
                                        sub_benchmark_result_file)
                        else:
                            benchmark_result_json_files.append(
                                benchmark_result_file)
                    except subprocess.CalledProcessError as e:
                        failed = True
                        any_failed = True
                        break

                if failed:
                    continue

                # Merge results and write output.
                if sub_benchmarks_for_target:
                    for sub_benchmark in sub_benchmarks_for_target:
                        ArchiveBenchmarkResult(
                            benchmark + sub_benchmark, target,
                            benchmark_result_json_files[sub_benchmark], options,
                            temp)
                else:
                    ArchiveBenchmarkResult(benchmark, target,
                                           benchmark_result_json_files, options,
                                           temp)

                # Write metadata.
                if utils.is_bot():
                    meta_file = os.path.join(temp, "meta")
                    with open(meta_file, 'w') as f:
                        f.write("Produced by: " +
                                os.environ.get('SWARMING_BOT_ID'))
                    ArchiveOutputFile(meta_file,
                                      GetArtifactLocation(
                                          benchmark, target, options.version,
                                          'meta'),
                                      outdir=options.outdir)

    # Only upload benchmark data when running on the perf bot.
    if utils.is_bot() and not options.internal:
        upload_benchmark_data_to_google_storage.run_bucket()

    if any_failed:
        return 1


if __name__ == '__main__':
    sys.exit(main())
