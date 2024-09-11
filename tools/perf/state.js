// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
import url from "./url.js";

var commits = null;

const benchmarks = new Set();
const selectedBenchmarks = new Set();

const legends = new Set();
const selectedLegends = new Set();

const zoom = { left: -1, right: -1 };

function forEachBenchmark(callback) {
  for (const benchmark of benchmarks.values()) {
    callback(benchmark, selectedBenchmarks.has(benchmark));
  }
}

function forEachSelectedBenchmark(callback) {
  forEachBenchmark((benchmark, selected) => {
    if (selected) {
      callback(benchmark);
    }
  });
}

function hasLegend(legend) {
  return legends.has(legend);
}

function importCommits(url) {
  return import(url, { with: { type: "json" }})
      .then(module => {
        commits = module.default;
        commits.reverseInPlace();
        // Amend the commits with their unique index.
        for (var i = 0; i < commits.length; i++) {
          commits[i].index = i;
        }
        return commits;
      });
}

function initializeBenchmarks() {
  for (const commit of commits.values()) {
    for (const benchmark in commit.benchmarks) {
        benchmarks.add(benchmark);
    }
  }
  for (const benchmark of benchmarks.values()) {
    if (url.matches(benchmark)) {
      selectedBenchmarks.add(benchmark);
    }
  }
  if (selectedBenchmarks.size == 0) {
    const randomBenchmarkIndex = Math.floor(Math.random() * benchmarks.size);
    const randomBenchmark = Array.from(benchmarks)[randomBenchmarkIndex];
    selectedBenchmarks.add(randomBenchmark);
  }
}

function initializeLegends(legendsInfo) {
  for (var legend in legendsInfo) {
    legends.add(legend);
    if (url.contains(legend)) {
      selectedLegends.add(legend);
    }
  }
  if (selectedLegends.size == 0) {
    for (let [legend, legendInfo] of Object.entries(legendsInfo)) {
      if (legendInfo.default) {
        selectedLegends.add(legend);
      }
    }
  }
}

function initializeZoom() {
  zoom.left = Math.max(0, commits.length - 75);
  zoom.right = commits.length;
  for (const urlOption of url.values()) {
    if (urlOption.startsWith('L')) {
      var left = parseInt(urlOption.substring(1));
      if (isNaN(left)) {
        continue;
      }
      left = left >= 0 ? left : commits.length + left;
      if (left < 0) {
        zoom.left = 0;
      } else if (left >= commits.length) {
        zoom.left = commits.length - 1;
      } else {
        zoom.left = left;
      }
    }
  }
}

function handleKeyDownEvent(e, callback) {
  if (selectedBenchmarks.size != 1) {
    return;
  }
  const [selectedBenchmark] = selectedBenchmarks;
  var benchmarkToSelect = null;
  var previousBenchmark = null;
  for (const benchmark of benchmarks.values()) {
    if (previousBenchmark != null) {
      if (e.key == 'ArrowLeft' && benchmark == selectedBenchmark) {
        benchmarkToSelect = previousBenchmark;
        break;
      } else if (e.key === 'ArrowRight' && previousBenchmark == selectedBenchmark) {
        benchmarkToSelect = benchmark;
        break;
      }
    }
    previousBenchmark = benchmark;
  }
  if (benchmarkToSelect != null) {
    selectedBenchmarks.clear();
    selectedBenchmarks.add(benchmarkToSelect);
    document.getElementById(selectedBenchmark).checked = false;
    document.getElementById(benchmarkToSelect).checked = true;
    callback();
  }
}

function isLegendSelected(legend) {
  return selectedLegends.has(legend);
}

export default {
  benchmarks: benchmarks,
  commits: zoom => zoom ? commits.slice(zoom.left, zoom.right) : commits,
  legends: legends,
  selectedBenchmarks: selectedBenchmarks,
  selectedLegends: selectedLegends,
  forEachBenchmark: forEachBenchmark,
  forEachSelectedBenchmark: forEachSelectedBenchmark,
  handleKeyDownEvent: handleKeyDownEvent,
  hasLegend: hasLegend,
  initializeBenchmarks: initializeBenchmarks,
  initializeLegends: initializeLegends,
  initializeZoom: initializeZoom,
  importCommits: importCommits,
  isLegendSelected: isLegendSelected,
  zoom: zoom
};
