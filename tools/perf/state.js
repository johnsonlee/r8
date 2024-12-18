// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
import chart from "./chart.js";
import dom from "./dom.js";
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

function getCommit(index, zoom) {
  return getCommits(zoom)[index];
}

function getCommitFromContext(context) {
  const elementInfo = context[0];
  var commit;
  if (elementInfo.dataset.type == 'line') {
    commit = getCommit(elementInfo.dataIndex, zoom);
  } else {
    console.assert(elementInfo.dataset.type == 'scatter');
    commit = getCommit(elementInfo.raw.x, null);
  }
  return commit;
}

function getCommitDescriptionFromContext(context) {
  const commit = getCommitFromContext(context);
  const elementInfo = context[0];
  const dataset = chart.get().data.datasets[elementInfo.datasetIndex];
  return `App: ${dataset.benchmark}\n`
      + `Author: ${commit.author}\n`
      + `Submitted: ${new Date(commit.submitted * 1000).toLocaleString()}\n`
      + `Hash: ${commit.hash}\n`
      + `Index: ${commit.index}`;
}

function getCommits(zoom) {
  const commitsForSelectedBranch = commits.filter(commit => dom.isCommitSelected(commit));
  if (zoom) {
    return commitsForSelectedBranch.slice(zoom.left, zoom.right);
  }
  return commitsForSelectedBranch;
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
        var mainIndex = 0;
        var releaseIndex = 0;
        for (var i = 0; i < commits.length; i++) {
          const commit = commits[i];
          if (commit.version) {
            commit.index = releaseIndex++;
          } else {
            commit.index = mainIndex++;
          }
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
  const filteredCommits = resetZoom();
  for (const urlOption of url.values()) {
    if (urlOption.startsWith('L')) {
      var left = parseInt(urlOption.substring(1));
      if (isNaN(left)) {
        continue;
      }
      left = left >= 0 ? left : filteredCommits.length + left;
      if (left < 0) {
        zoom.left = 0;
      } else if (left >= filteredCommits.length) {
        zoom.left = filteredCommits.length - 1;
      } else {
        zoom.left = left;
      }
    }
  }
}

function resetZoom() {
  const filteredCommits = getCommits(null);
  zoom.left = Math.max(0, filteredCommits.length - 75);
  zoom.right = filteredCommits.length;
  return filteredCommits;
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
  commits: getCommits,
  legends: legends,
  selectedBenchmarks: selectedBenchmarks,
  selectedLegends: selectedLegends,
  forEachBenchmark: forEachBenchmark,
  forEachSelectedBenchmark: forEachSelectedBenchmark,
  getCommit: getCommit,
  getCommitFromContext: getCommitFromContext,
  getCommitDescriptionFromContext: getCommitDescriptionFromContext,
  handleKeyDownEvent: handleKeyDownEvent,
  hasLegend: hasLegend,
  initializeBenchmarks: initializeBenchmarks,
  initializeLegends: initializeLegends,
  initializeZoom: initializeZoom,
  importCommits: importCommits,
  isLegendSelected: isLegendSelected,
  resetZoom: resetZoom,
  zoom: zoom
};
