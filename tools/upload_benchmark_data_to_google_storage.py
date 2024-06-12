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

APPS = ['NowInAndroidApp', 'TiviApp']
TARGETS = ['r8-full']
NUM_COMMITS = 250

BUCKET_PUBLIC = 'r8-test-results'
INDEX_HTML = os.path.join(utils.TOOLS_DIR, 'perf/index.html')


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


def main():
    # Get the N most recent commits sorted by newest first.
    top = utils.get_sha1_from_revision('origin/main')
    bottom = utils.get_nth_sha1_from_revision(NUM_COMMITS - 1, 'origin/main')
    commits = historic_run.enumerate_git_commits(top, bottom)
    assert len(commits) == NUM_COMMITS

    # Download all benchmark data from the cloud bucket to a temp folder.
    with utils.TempDir() as temp:
        local_bucket = os.path.join(temp, perf.BUCKET)
        DownloadCloudBucket(local_bucket)

        # Aggregate all the result.json files into a single benchmark_data.json file
        # that has the same format as tools/perf/benchmark_data.json.
        benchmark_data = []
        for commit in commits:
            benchmarks = {}
            for app in APPS:
                for target in TARGETS:
                    filename = perf.GetArtifactLocation(app, target,
                                                        commit.hash(),
                                                        'result.json')
                    app_benchmark_data = ParseJsonFromCloudStorage(
                        filename, local_bucket)
                    if app_benchmark_data:
                        benchmarks[app] = app_benchmark_data
            if len(benchmarks):
                benchmark_data.append({
                    'author': commit.author_name(),
                    'hash': commit.hash(),
                    'submitted': commit.committer_timestamp(),
                    'title': commit.title(),
                    'benchmarks': benchmarks
                })

        # Serialize JSON to temp file.
        benchmark_data_file = os.path.join(temp, 'benchmark_data.json')
        with open(benchmark_data_file, 'w') as f:
            json.dump(benchmark_data, f)

        # Write output files to public bucket.
        perf.ArchiveOutputFile(benchmark_data_file,
                               'perf/benchmark_data.json',
                               bucket=BUCKET_PUBLIC,
                               header='Cache-Control:no-store')
        perf.ArchiveOutputFile(INDEX_HTML,
                               'perf/index.html',
                               bucket=BUCKET_PUBLIC)


if __name__ == '__main__':
    sys.exit(main())
