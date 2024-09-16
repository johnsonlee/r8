// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
function getSingleResult(benchmark, commit, resultName, resultIteration = 0) {
  if (!(benchmark in commit.benchmarks)) {
    return NaN;
  }
  const allResults = commit.benchmarks[benchmark].results;
  const resultsForIteration = allResults[resultIteration];
  // If a given iteration does not declare a result, then the result
  // was the same as the first run.
  if (resultIteration > 0 && !(resultName in resultsForIteration)) {
    return allResults.first()[resultName];
  }
  return resultsForIteration[resultName];
}

function getAllResults(benchmark, commit, resultName, transformation) {
  const result = [];
  const allResults = commit.benchmarks[benchmark].results;
  for (var iteration = 0; iteration < allResults.length; iteration++) {
    result.push(getSingleResult(benchmark, commit, resultName, iteration));
  }
  if (transformation) {
    return transformation(result);
  }
  return result;
}
