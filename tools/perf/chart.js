// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
import annotations from "./annotations.js";
import dom from "./dom.js";
import scales from "./scales.js";
import state from "./state.js";
import url from "./url.js";

var chart = null;
var dataProvider = null;
var filteredCommits = null;

function getBenchmarkColors(theChart) {
  const benchmarkColors = {};
  for (var datasetIndex = 0;
      datasetIndex < theChart.data.datasets.length;
      datasetIndex++) {
    if (theChart.getDatasetMeta(datasetIndex).hidden) {
      continue;
    }
    const dataset = theChart.data.datasets[datasetIndex];
    const benchmark = dataset.benchmark;
    const benchmarkColor = dataset.borderColor;
    if (!(benchmark in benchmarkColors)) {
      benchmarkColors[benchmark] = benchmarkColor;
    }
  }
  return benchmarkColors;
}

function get() {
  return chart;
}

function getChartData() {
  return dataProvider(filteredCommits);
}

function getDataLabelFormatter(value, context) {
  const percentageChange = getDataPercentageChange(context);
  const commit = getFilteredCommit(context.dataIndex);
  var label = "";
  // For try commits, always include the initials in the data label.
  if ('parent_hash' in commit) {
    const initials =
        commit.author
            .split(/\s/)
            .reduce((initials, name) => initials += name.slice(0,1), '');
    label += initials;
  }
  if (percentageChange !== null && Math.abs(percentageChange) >= 0.1) {
    const glyph = percentageChange < 0 ? '▼' : '▲';
    label += glyph + ' ' + percentageChange.toFixed(2) + '%';
  }
  return label;
}

function getDataPercentageChange(context) {
  var i = context.dataIndex;
  var value = context.dataset.data[i];
  var j = i;
  var previousValue;
  do {
    if (j == 0) {
      return null;
    }
    previousValue = context.dataset.data[--j];
  } while (previousValue === undefined || isNaN(previousValue));
  return (value - previousValue) / previousValue * 100;
}

function getFilteredCommit(index) {
  return filteredCommits[index];
}

function initializeChart(options) {
  setFilteredCommits();
  chart = new Chart(dom.canvas, {
    data: getChartData(),
    options: options,
    plugins: [ChartDataLabels]
  });
  // Setup annotations.
  annotations.configure(chart, filteredCommits);
  // Hide disabled legends.
  if (state.selectedLegends.size < state.legends.size) {
    update(false, true);
  } else {
    update(false, false);
  }
  document.addEventListener('keydown', e => {
    state.handleKeyDownEvent(e, () => update(true, false));
  });
}

function setDataProvider(theDataProvider) {
 dataProvider = theDataProvider;
}

function setFilteredCommits() {
  filteredCommits = state.commits(state.zoom);
}

function update(dataChanged, legendsChanged) {
  console.assert(state.zoom.left <= state.zoom.right);

  // Update datasets.
  if (dataChanged) {
    setFilteredCommits();
    Object.assign(chart.data, getChartData());
    // Update annotations.
    annotations.configure(chart, filteredCommits);
    // Update chart.
    chart.update();
  }

  // Update legends.
  if (legendsChanged || (dataChanged && state.selectedLegends.size < state.legends.size)) {
    for (var datasetIndex = 0;
        datasetIndex < chart.data.datasets.length;
        datasetIndex++) {
      const datasetMeta = chart.getDatasetMeta(datasetIndex);
      datasetMeta.hidden = !state.isLegendSelected(datasetMeta.label);
    }

    // Update scales.
    scales.update(chart.options.scales);

    // Update chart.
    chart.update();
  }

  dom.updateBenchmarkColors(getBenchmarkColors(chart));
  dom.updateChartNavigation();
  url.updateHash(state);
}

export default {
  get: get,
  getDataLabelFormatter: getDataLabelFormatter,
  getDataPercentageChange: getDataPercentageChange,
  getFilteredCommit: getFilteredCommit,
  initializeChart: initializeChart,
  setDataProvider: setDataProvider,
  update: update
};
