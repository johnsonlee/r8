// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
import chart from "./chart.js";
import state from "./state.js";
import url from "./url.js";

// DOM references.
const benchmarkSelectors = document.getElementById('benchmark-selectors');
const branchMain = document.getElementById('branch-main');
const branchRelease = document.getElementById('branch-release');
const canvas = document.getElementById('myChart');
const runtimeMin = document.getElementById('runtime-min');
const runtimeP50 = document.getElementById('runtime-p50');
const runtimeP90 = document.getElementById('runtime-p90');
const runtimeP95 = document.getElementById('runtime-p95');
const runtimeMax = document.getElementById('runtime-max');
const showMoreLeft = document.getElementById('show-more-left');
const showLessLeft = document.getElementById('show-less-left');
const showLessRight = document.getElementById('show-less-right');
const showMoreRight = document.getElementById('show-more-right');

function getSelectedBranch() {
  console.assert(branchMain.checked || branchRelease.checked);
  return branchMain.checked ? 'main' : 'release';
}

function initialize() {
  initializeBenchmarkSelectors();
  initializeBranchSelectors();
  initializeChartNavigation();
}

function initializeBenchmarkSelectors() {
  state.forEachBenchmark(
    (benchmark, selected) => {
      const input = document.createElement('input');
      input.type = 'checkbox';
      input.name = 'benchmark';
      input.id = benchmark;
      input.value = benchmark;
      input.checked = selected;
      input.onchange = function (e) {
        if (e.target.checked) {
          state.selectedBenchmarks.add(e.target.value);
        } else {
          state.selectedBenchmarks.delete(e.target.value);
        }
        chart.update(true, false);
      };

      const label = document.createElement('label');
      label.id = benchmark + 'Label';
      label.htmlFor = benchmark;
      label.innerHTML = benchmark;

      benchmarkSelectors.appendChild(input);
      benchmarkSelectors.appendChild(label);
    });
}

function initializeBranchSelectors() {
  if (url.contains('release')) {
    branchRelease.checked = true;
  }
}

function initializeChartNavigation() {
  const zoom = state.zoom;

  branchMain.onclick = event => { state.resetZoom(); chart.update(true, false); };
  branchRelease.onclick = event => { state.resetZoom(); chart.update(true, false); };

  canvas.onclick = event => {
    const points =
        chart.get().getElementsAtEventForMode(
            event, 'nearest', { intersect: true }, true);
    if (points.length > 0) {
      const point = points[0];
      const commit = state.getCommit(point.index, null);
      window.open('https://r8.googlesource.com/r8/+/' + commit.hash, '_blank');
    }
  };

  runtimeMin.onclick = event => chart.update(true, false);
  runtimeP50.onclick = event => chart.update(true, false);
  runtimeP90.onclick = event => chart.update(true, false);
  runtimeP95.onclick = event => chart.update(true, false);
  runtimeMax.onclick = event => chart.update(true, false);

  showMoreLeft.onclick = event => {
    if (zoom.left == 0) {
      return;
    }
    const currentSize = zoom.right - zoom.left;
    zoom.left = zoom.left - currentSize;
    if (zoom.left < 0) {
      zoom.left = 0;
    }
    chart.update(true, false);
  };

  showLessLeft.onclick = event => {
    const currentSize = zoom.right - zoom.left;
    zoom.left = zoom.left + Math.floor(currentSize / 2);
    if (zoom.left >= zoom.right) {
      zoom.left = zoom.right - 1;
    }
    chart.update(true, false);
  };

  showLessRight.onclick = event => {
    if (zoom.right == 0) {
      return;
    }
    const currentSize = zoom.right - zoom.left;
    zoom.right = zoom.right - Math.floor(currentSize / 2);
    if (zoom.right < zoom.left) {
      zoom.right = zoom.left;
    }
    chart.update(true, false);
  };

  showMoreRight.onclick = event => {
    const currentSize = zoom.right - zoom.left;
    zoom.right = zoom.right + currentSize;
    if (zoom.right > state.commits().length) {
      zoom.right = state.commits().length;
    }
    chart.update(true, false);
  };
}

function isCommitSelected(commit) {
  if (branchRelease.checked) {
    return commit.version;
  } else {
    return !commit.version;
  }
}

function transformRuntimeData(results) {
  if (runtimeMin.checked) {
    return results.min();
  } else if (runtimeP50.checked) {
    return results.p(50);
  } else if (runtimeP90.checked) {
    return results.p(90);
  } else if (runtimeP95.checked) {
    return results.p(95);
  } else {
    console.assert(runtimeMax.checked);
    return results.max();
  }
}

function updateBenchmarkColors(benchmarkColors) {
  state.forEachBenchmark(
    benchmark => {
      const benchmarkLabel = document.getElementById(benchmark + 'Label');
      const benchmarkColor = benchmarkColors[benchmark] || '#000000';
      const benchmarkFontWeight = benchmark in benchmarkColors ? 'bold' : 'normal';
      benchmarkLabel.style.color = benchmarkColor;
      benchmarkLabel.style.fontWeight = benchmarkFontWeight;
    });
}

function updateChartNavigation() {
  const zoom = state.zoom;
  showMoreLeft.disabled = zoom.left == 0;
  showLessLeft.disabled = zoom.left == zoom.right - 1;
  showLessRight.disabled = zoom.left == zoom.right - 1;
  showMoreRight.disabled = zoom.right == state.commits().length;
}

export default {
  canvas: canvas,
  getSelectedBranch: getSelectedBranch,
  initialize: initialize,
  isCommitSelected: isCommitSelected,
  transformRuntimeData: transformRuntimeData,
  updateBenchmarkColors: updateBenchmarkColors,
  updateChartNavigation: updateChartNavigation
};