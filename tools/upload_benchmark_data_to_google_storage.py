#!/usr/bin/env python3
# Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import historic_run
import perf
import utils

import argparse
import json
import os
import re
import subprocess
import sys

TARGETS = ['r8-full']
NUM_COMMITS = 1000

FILES = [
    'chart.js', 'd8.html', 'dom.js', 'extensions.js', 'r8.html', 'retrace.html',
    'scales.js', 'state.js', 'stylesheet.css', 'tooltip.js', 'url.js',
    'utils.js'
]


def DownloadCloudBucket(dest):
    os.makedirs(dest)
    utils.download_file_from_cloud_storage(perf.GetGSLocation('*'),
                                           dest,
                                           concurrent=True,
                                           flags=['-R'])


def GetMainCommits():
    top = utils.get_sha1_from_revision('origin/main')
    bottom = utils.get_nth_sha1_from_revision(NUM_COMMITS - 1, 'origin/main')
    commits = historic_run.enumerate_git_commits(top, bottom)
    assert len(commits) == NUM_COMMITS
    return commits


def GetReleaseBranches():
    remote_branches = subprocess.check_output(
        ['git', 'branch', '-r']).decode('utf-8').strip().splitlines()
    result = []
    for remote_branch in remote_branches:
        remote_branch = remote_branch.strip()

        # Strip 'origin/'.
        try:
            remote_name_end_index = remote_branch.index('/')
            remote_branch = remote_branch[remote_name_end_index + 1:]
        except ValueError:
            pass

        # Filter out branches that are not on the form X.Y
        if not re.search('^(0|[1-9]\d*)\.(0|[1-9]\d*)$', remote_branch):
            continue

        # Filter out branches prior to 8.9.
        dot_index = remote_branch.index('.')
        [major, minor] = remote_branch.split('.')
        if int(major) < 8 or (major == '8' and int(minor) < 9):
            continue

        result.append(remote_branch)
    return result


def GetReleaseCommits():
    release_commits = []
    for branch in GetReleaseBranches():
        (major, minor) = branch.split('.')
        candidate_commits = subprocess.check_output([
            'git', 'log', '--grep=-dev', '--max-count=100',
            '--pretty=format:%H %s', 'origin/' + branch, '--',
            'src/main/java/com/android/tools/r8/Version.java'
        ]).decode('utf-8').strip().splitlines()
        for candidate_commit in candidate_commits:
            separator_index = candidate_commit.index(' ')
            git_hash = candidate_commit[:separator_index]
            git_title = candidate_commit[separator_index + 1:]
            if not re.search(
                    '^Version %s\.%s\.(0|[1-9]\d*)-dev$' %
                (major, minor), git_title):
                continue
            release_commits.append(historic_run.git_commit_from_hash(git_hash))
    return release_commits


def ParseJsonFromCloudStorage(filename, local_bucket_dict):
    if not filename in local_bucket_dict:
        return None
    return json.loads(local_bucket_dict[filename])


def RecordBenchmarkResult(commit, benchmark, benchmark_info, local_bucket_dict,
                          target, benchmarks):
    if not target in benchmark_info['targets']:
        return
    sub_benchmarks = benchmark_info.get('subBenchmarks', {})
    sub_benchmarks_for_target = sub_benchmarks.get(target, [])
    if sub_benchmarks_for_target:
        for sub_benchmark in sub_benchmarks_for_target:
            RecordSingleBenchmarkResult(commit, benchmark + sub_benchmark,
                                        local_bucket_dict, target, benchmarks)
    else:
        RecordSingleBenchmarkResult(commit, benchmark, local_bucket_dict,
                                    target, benchmarks)


def RecordSingleBenchmarkResult(commit, benchmark, local_bucket_dict, target,
                                benchmarks):
    filename = perf.GetArtifactLocation(benchmark,
                                        target,
                                        commit.hash(),
                                        'result.json',
                                        branch=commit.branch())
    benchmark_data = ParseJsonFromCloudStorage(filename, local_bucket_dict)
    if benchmark_data:
        benchmarks[benchmark] = benchmark_data


def RecordBenchmarkResults(commit, benchmarks, benchmark_data):
    if benchmarks or benchmark_data:
        data = {
            'author': commit.author_name(),
            'hash': commit.hash(),
            'submitted': commit.committer_timestamp(),
            'title': commit.title(),
            'benchmarks': benchmarks
        }
        version = commit.version()
        if version:
            data['version'] = version
        benchmark_data.append(data)


def TrimBenchmarkResults(benchmark_data):
    new_benchmark_data_len = len(benchmark_data)
    while new_benchmark_data_len > 0:
        candidate_len = new_benchmark_data_len - 1
        if not benchmark_data[candidate_len]['benchmarks']:
            new_benchmark_data_len = candidate_len
        else:
            break
    return benchmark_data[0:new_benchmark_data_len]


def ArchiveBenchmarkResults(benchmark_data, dest, outdir, temp):
    # Serialize JSON to temp file.
    benchmark_data_file = os.path.join(temp, dest)
    with open(benchmark_data_file, 'w') as f:
        json.dump(benchmark_data, f)

    # Write output files to public bucket.
    perf.ArchiveOutputFile(benchmark_data_file,
                           dest,
                           header='Cache-Control:no-store',
                           outdir=outdir)


def run_bucket():
    # Get the N most recent commits sorted by newest first.
    main_commits = GetMainCommits()

    # Get all release commits from 8.9 and onwards.
    release_commits = GetReleaseCommits()

    # Download all benchmark data from the cloud bucket to a temp folder.
    with utils.TempDir() as temp:
        local_bucket = os.path.join(temp, perf.BUCKET)
        DownloadCloudBucket(local_bucket)
        run(main_commits + release_commits, local_bucket, temp)


def run_local(local_bucket):
    commit_hashes = set()
    for benchmark in os.listdir(local_bucket):
        benchmark_dir = os.path.join(local_bucket, benchmark)
        if not os.path.isdir(benchmark_dir):
            continue
        for target in os.listdir(benchmark_dir):
            target_dir = os.path.join(local_bucket, benchmark, target)
            if not os.path.isdir(target_dir):
                continue
            for commit_hash in os.listdir(target_dir):
                commit_hash_dir = os.path.join(local_bucket, benchmark, target,
                                               commit_hash)
                if not os.path.isdir(commit_hash_dir):
                    continue
                commit_hashes.add(commit_hash)
    commits = []
    for commit_hash in commit_hashes:
        commits.append(historic_run.git_commit_from_hash(commit_hash))
    commits.sort(key=lambda c: c.committer_timestamp(), reverse=True)
    with utils.TempDir() as temp:
        outdir = os.path.join(utils.TOOLS_DIR, 'perf')
        run(commits, local_bucket, temp, outdir=outdir)


def run(commits, local_bucket, temp, outdir=None):
    print('Loading bucket into memory')
    local_bucket_dict = {}
    for (root, dirs, files) in os.walk(local_bucket):
        for file in files:
            if file != 'result.json':
                continue
            abs_path = os.path.join(root, file)
            rel_path = os.path.relpath(abs_path, local_bucket)
            with open(abs_path, 'r') as f:
                local_bucket_dict[rel_path] = f.read()

    # Aggregate all the result.json files into a single file that has the
    # same format as tools/perf/benchmark_data.json.
    print('Processing commits')
    d8_benchmark_data = []
    r8_benchmark_data = []
    retrace_benchmark_data = []
    for commit in commits:
        d8_benchmarks = {}
        r8_benchmarks = {}
        retrace_benchmarks = {}
        for benchmark, benchmark_info in perf.ALL_BENCHMARKS.items():
            RecordBenchmarkResult(commit, benchmark, benchmark_info,
                                  local_bucket_dict, 'd8', d8_benchmarks)
            RecordBenchmarkResult(commit, benchmark, benchmark_info,
                                  local_bucket_dict, 'r8-full', r8_benchmarks)
            RecordBenchmarkResult(commit, benchmark, benchmark_info,
                                  local_bucket_dict, 'retrace',
                                  retrace_benchmarks)
        RecordBenchmarkResults(commit, d8_benchmarks, d8_benchmark_data)
        RecordBenchmarkResults(commit, r8_benchmarks, r8_benchmark_data)
        RecordBenchmarkResults(commit, retrace_benchmarks,
                               retrace_benchmark_data)

    # Trim data.
    print('Trimming data')
    d8_benchmark_data = TrimBenchmarkResults(d8_benchmark_data)
    r8_benchmark_data = TrimBenchmarkResults(r8_benchmark_data)
    retrace_benchmark_data = TrimBenchmarkResults(retrace_benchmark_data)

    # Write output JSON files to public bucket, or to tools/perf/ if running
    # with --local-bucket.
    print('Writing JSON')
    ArchiveBenchmarkResults(d8_benchmark_data, 'd8_benchmark_data.json', outdir,
                            temp)
    ArchiveBenchmarkResults(r8_benchmark_data, 'r8_benchmark_data.json', outdir,
                            temp)
    ArchiveBenchmarkResults(retrace_benchmark_data,
                            'retrace_benchmark_data.json', outdir, temp)

    # Write remaining files to public bucket.
    print('Writing static files')
    if outdir is None:
        for file in FILES:
            dest = os.path.join(utils.TOOLS_DIR, 'perf', file)
            perf.ArchiveOutputFile(dest, file)


def ParseOptions():
    result = argparse.ArgumentParser()
    result.add_argument('--local-bucket', help='Local results dir.')
    return result.parse_known_args()


def main():
    options, args = ParseOptions()
    if options.local_bucket:
        run_local(options.local_bucket)
    else:
        run_bucket()


if __name__ == '__main__':
    sys.exit(main())
