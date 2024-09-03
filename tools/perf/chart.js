// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
import dom from "./dom.js";
import scales from "./scales.js";
import state from "./state.js";
import url from "./url.js";

var chart = null;
var dataProvider = null;

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

function getData() {
  const filteredCommits = state.commits(state.zoom);
  return dataProvider(filteredCommits);
}

function getDataLabelFormatter(value, context) {
  var percentageChange = getDataPercentageChange(context);
  var percentageChangeTwoDecimals = Math.round(percentageChange * 100) / 100;
  var glyph = percentageChange < 0 ? '▼' : '▲';
  return glyph + ' ' + percentageChangeTwoDecimals + '%';
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

function initializeChart(options) {
  chart = new Chart(dom.canvas, {
    data: getData(),
    options: options,
    plugins: [ChartDataLabels]
  });
  // Hide disabled legends.
  if (state.selectedLegends.size < state.legends.size) {
    update(false, true);
  } else {
    update(false, false);
  }
}

function setDataProvider(theDataProvider) {
 dataProvider = theDataProvider;
}

function update(dataChanged, legendsChanged) {
  console.assert(state.zoom.left <= state.zoom.right);

  // Update datasets.
  if (dataChanged) {
    const newData = getData();
    Object.assign(chart.data, newData);
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
  initializeChart: initializeChart,
  setDataProvider: setDataProvider,
  update: update
};
