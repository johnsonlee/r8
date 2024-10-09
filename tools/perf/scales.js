// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
import state from "./state.js";

function get() {
  const scales = {};
  scales.x = {};
  if (state.hasLegend('Dex size')) {
    scales.y = {
      position: 'left',
      title: {
        display: true,
        text: 'Dex size (bytes)'
      }
    };
  } else {
    console.assert(!state.hasLegend('Instruction size'));
    console.assert(!state.hasLegend('Composable size'));
    console.assert(!state.hasLegend('Oat size'));
  }
  console.assert(state.hasLegend('Runtime'));
  console.assert(state.hasLegend('Runtime variance'));
  console.assert(state.hasLegend('Warmup'));
  scales.y_runtime = {
    position: state.hasLegend('Dex size') ? 'right' : 'left',
    title: {
      display: true,
      text: 'Runtime (seconds)'
    }
  };
  if (state.hasLegend('Instruction size') || state.hasLegend('Composable size')) {
    scales.y_ins_code_size = {
      position: 'left',
      title: {
        display: true,
        text: 'Instruction size (bytes)'
      }
    };
  }
  if (state.hasLegend('Oat size')) {
    scales.y_oat_code_size = {
      position: 'left',
      title: {
        display: true,
        text: 'Oat size (bytes)'
      }
    };
  }
  return scales;
}

function update(scales) {
  if (scales.y) {
    scales.y.display = state.isLegendSelected('Dex size');
  }
  if (scales.y_ins_code_size) {
    scales.y_ins_code_size.display =
        state.isLegendSelected('Instruction size') || state.isLegendSelected('Composable size');
  }
  if (scales.y_oat_code_size) {
    scales.y_oat_code_size.display = state.isLegendSelected('Oat size');
  }
  if (scales.y_runtime) {
    scales.y_runtime.display =
        state.isLegendSelected('Runtime')
            || state.isLegendSelected('Runtime variance')
            || state.isLegendSelected('Warmup');
  }
}

export default {
  get: get,
  update: update
};