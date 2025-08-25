// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

function configure(chart, filteredCommits) {
  // Create an opaque red background behind all try commits.
  const annotations = [];
  for (var i = 0; i < filteredCommits.length; i++) {
    const commit = filteredCommits[i];
    if ('parent_hash' in commit) {
      annotations.push({
        drawTime: 'beforeDatasetsDraw',
        type: 'box',
        xScaleID: 'x',
        yScaleID: 'y',
        xMin: i - 0.5,
        xMax: i + 0.5,
        backgroundColor: 'rgba(255, 0, 0, 0.4)',
        borderWidth: 0
      });
    }
  }
  chart.options.plugins.annotation = {
    annotations: annotations
  };
}

export default {
  configure: configure
};
