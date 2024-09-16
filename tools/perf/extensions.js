// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
Array.prototype.any = function(predicate) {
  for (const element of this.values()) {
    if (predicate(element)) {
      return true;
    }
  }
  return false;
};
Array.prototype.first = function() {
  return this[0];
};
Array.prototype.avg = function() {
  return this.reduce(function(x, y) { return x + y; }, 0) / this.length;
};
Array.prototype.max = function() {
  return this.reduce(function(x, y) { return x === null ? y : Math.max(x, y); }, null);
};
Array.prototype.min = function() {
  return this.reduce(function(x, y) { return x === null ? y : Math.min(x, y); }, null);
};
Array.prototype.p = function(value) {
  const copy = [...this];
  copy.sort();
  const index = Math.floor(copy.length * value / 100);
  if (copy.length % 2 == 0) {
    return (copy[index - 1] + copy[index]) / 2;
  } else {
    return copy[index];
  }
};
Array.prototype.reverseInPlace = function() {
  for (var i = 0; i < Math.floor(this.length / 2); i++) {
    var temp = this[i];
    this[i] = this[this.length - i - 1];
    this[this.length - i - 1] = temp;
  }
};
Number.prototype.ns_to_s = function() {
  const seconds = this/10E8;
  const seconds_with_one_decimal = Math.round(seconds*10)/10;
  return seconds;
};