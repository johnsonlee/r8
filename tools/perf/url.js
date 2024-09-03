// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
const options = unescape(window.location.hash.substring(1)).split(',');

function contains(subject) {
  return options.includes(subject);
}

function matches(subject) {
  for (const filter of options.values()) {
    if (filter) {
      const match = subject.match(new RegExp(filter.replace("*", ".*")));
      if (match) {
        return true;
      }
    }
  }
  return false;
}

function updateHash(state) {
  window.location.hash =
      Array.from(state.selectedBenchmarks)
          .concat(
              state.selectedLegends.size == state.legends.size
                  ? []
                  : Array.from(state.selectedLegends))
          .join(',');
}

function values() {
  return options;
}

export default {
  contains: contains,
  matches: matches,
  updateHash: updateHash,
  values: values
};