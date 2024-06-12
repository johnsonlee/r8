#!/usr/bin/env python3
# Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import historic_run
import json
import os
import perf
import utils

import sys

APPS = ['NowInAndroidApp', 'TiviApp']
TARGETS = ['r8-full']
NUM_COMMITS = 1000


def ParseJsonFromCloudStorage(filename):
    gs_location = perf.GetGSLocation(filename)
    if not utils.file_exists_on_cloud_storage(gs_location):
        return None
    content = utils.cat_file_on_cloud_storage(gs_location)
    try:
        return json.loads(''.join(content))
    except:
        return None


def main():
    if utils.get_HEAD_branch() != 'main':
        print('Expected to be on branch \'main\'')
        sys.exit(1)

    # Get the N most recent commits sorted by newest first.
    top = utils.get_HEAD_sha1()
    bottom = utils.get_nth_sha1_from_HEAD(NUM_COMMITS - 1)
    commits = historic_run.enumerate_git_commits(top, bottom)
    assert len(commits) == NUM_COMMITS

    # Aggregate all the result.json files into a single benchmark_data.json file
    # that has the same format as tools/perf/benchmark_data.json.
    benchmark_data = []
    for commit in commits:
        benchmarks = {}
        for app in APPS:
            for target in TARGETS:
                filename = perf.GetArtifactLocation(app, target, commit.hash(),
                                                    'result.json')
                app_benchmark_data = ParseJsonFromCloudStorage(filename)
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

    with utils.TempDir() as temp:
        benchmark_data_file = os.path.join(temp, 'benchmark_data.json')
        with open(benchmark_data_file, 'w') as f:
            json.dump(benchmark_data, f)
        perf.ArchiveOutputFile(benchmark_data_file, 'benchmark_data.json')


if __name__ == '__main__':
    sys.exit(main())
