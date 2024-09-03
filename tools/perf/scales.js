// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
import state from "./state.js";

function get() {
  return {
    x: {},
    y: {
      position: 'left',
      title: {
        display: true,
        text: 'Dex size (bytes)'
      }
    },
    y_runtime: {
      position: 'right',
      title: {
        display: true,
        text: 'Runtime (seconds)'
      }
    },
    y_ins_code_size: {
      position: 'left',
      title: {
        display: true,
        text: 'Instruction size (bytes)'
      }
    },
    y_oat_code_size: {
      position: 'left',
      title: {
        display: true,
        text: 'Oat size (bytes)'
      }
    }
  };;
}

function update(scales) {
  scales.y.display = state.isLegendSelected('Dex size');
  scales.y_ins_code_size.display =
      state.isLegendSelected('Instruction size') || state.isLegendSelected('Composable size');
  scales.y_oat_code_size.display = state.isLegendSelected('Oat size');
  scales.y_runtime.display =
      state.isLegendSelected('Runtime') || state.isLegendSelected('Runtime variance');
}

export default {
  get: get,
  update: update
};