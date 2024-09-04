#!/usr/bin/env python3
# Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import historic_run
import json
import os
import perf
import time
import utils

import sys

BENCHMARKS = perf.BENCHMARKS
TARGETS = ['r8-full']
NUM_COMMITS = 1000

FILES = [
    'chart.js', 'd8.html', 'dom.js', 'extensions.js', 'r8.html', 'retrace.html',
    'scales.js', 'state.js', 'stylesheet.css', 'url.js', 'utils.js'
]


def DownloadCloudBucket(dest):
    os.makedirs(dest)
    utils.download_file_from_cloud_storage(perf.GetGSLocation('*'),
                                           dest,
                                           concurrent=True,
                                           flags=['-R'])


def ParseJsonFromCloudStorage(filename, local_bucket):
    abs_path = os.path.join(local_bucket, filename)
    if not os.path.exists(abs_path):
        return None
    with open(abs_path, 'r') as f:
        lines = f.readlines()
        content = ''.join(lines)
        try:
            return json.loads(content)
        except:
            return None


def RecordBenchmarkResult(commit, benchmark, benchmark_info, local_bucket,
                          target, benchmarks):
    if not target in benchmark_info['targets']:
        return
    filename = perf.GetArtifactLocation(benchmark, target, commit.hash(),
                                        'result.json')
    benchmark_data = ParseJsonFromCloudStorage(filename, local_bucket)
    if benchmark_data:
        benchmarks[benchmark] = benchmark_data


def RecordBenchmarkResults(commit, benchmarks, benchmark_data):
    if benchmarks or benchmark_data:
        benchmark_data.append({
            'author': commit.author_name(),
            'hash': commit.hash(),
            'submitted': commit.committer_timestamp(),
            'title': commit.title(),
            'benchmarks': benchmarks
        })


def TrimBenchmarkResults(benchmark_data):
    new_benchmark_data_len = len(benchmark_data)
    while new_benchmark_data_len > 0:
        candidate_len = new_benchmark_data_len - 1
        if not benchmark_data[candidate_len]['benchmarks']:
            new_benchmark_data_len = candidate_len
        else:
            break
    return benchmark_data[0:new_benchmark_data_len]


def ArchiveBenchmarkResults(benchmark_data, dest, temp):
    # Serialize JSON to temp file.
    benchmark_data_file = os.path.join(temp, dest)
    with open(benchmark_data_file, 'w') as f:
        json.dump(benchmark_data, f)

    # Write output files to public bucket.
    perf.ArchiveOutputFile(benchmark_data_file,
                           dest,
                           header='Cache-Control:no-store')


def run():
    # Get the N most recent commits sorted by newest first.
    top = utils.get_sha1_from_revision('origin/main')
    bottom = utils.get_nth_sha1_from_revision(NUM_COMMITS - 1, 'origin/main')
    commits = historic_run.enumerate_git_commits(top, bottom)
    assert len(commits) == NUM_COMMITS

    # Download all benchmark data from the cloud bucket to a temp folder.
    with utils.TempDir() as temp:
        local_bucket = os.path.join(temp, perf.BUCKET)
        DownloadCloudBucket(local_bucket)

        # Aggregate all the result.json files into a single file that has the
        # same format as tools/perf/benchmark_data.json.
        d8_benchmark_data = []
        r8_benchmark_data = []
        retrace_benchmark_data = []
        for commit in commits:
            d8_benchmarks = {}
            r8_benchmarks = {}
            retrace_benchmarks = {}
            for benchmark, benchmark_info in BENCHMARKS.items():
                RecordBenchmarkResult(commit, benchmark, benchmark_info,
                                      local_bucket, 'd8', d8_benchmarks)
                RecordBenchmarkResult(commit, benchmark, benchmark_info,
                                      local_bucket, 'r8-full', r8_benchmarks)
                RecordBenchmarkResult(commit, benchmark, benchmark_info,
                                      local_bucket, 'retrace',
                                      retrace_benchmarks)
            RecordBenchmarkResults(commit, d8_benchmarks, d8_benchmark_data)
            RecordBenchmarkResults(commit, r8_benchmarks, r8_benchmark_data)
            RecordBenchmarkResults(commit, retrace_benchmarks,
                                   retrace_benchmark_data)

        # Trim data.
        d8_benchmark_data = TrimBenchmarkResults(d8_benchmark_data)
        r8_benchmark_data = TrimBenchmarkResults(r8_benchmark_data)
        retrace_benchmark_data = TrimBenchmarkResults(retrace_benchmark_data)

        # Write output files to public bucket.
        ArchiveBenchmarkResults(d8_benchmark_data, 'd8_benchmark_data.json',
                                temp)
        ArchiveBenchmarkResults(r8_benchmark_data, 'r8_benchmark_data.json',
                                temp)
        ArchiveBenchmarkResults(retrace_benchmark_data,
                                'retrace_benchmark_data.json', temp)

        # Write remaining files to public bucket.
        for file in FILES:
            dest = os.path.join(utils.TOOLS_DIR, 'perf', file)
            perf.ArchiveOutputFile(dest, file)


def main():
    run()


if __name__ == '__main__':
    sys.exit(main())
