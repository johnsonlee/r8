// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.backports;

public final class FloatMethods {

  public static boolean isFinite(float f) {
    return !Float.isInfinite(f) && !Float.isNaN(f);
  }

  /*
   * Source: The Android Open Source Project
   * License: the Apache License, Version 2.0
   * Github: https://github.com/caoccao/Javet/blob/main/src/main/java/com/caoccao/javet/utils/Float16.java
   */
  public static float float16ToFloat(short float16) {
    // The float16 format has 1 sign bit, 5 exponent bits, and 10 mantissa bits.
    // The exponent bias is 15.
    int signMask = 0x8000;
    int exponentShift = 10;
    int shiftedExponentMask = 0x1f;
    int significandMask = 0x3ff;
    int fp32DenormalMagic = 126 << 23;
    float fp32DenormalFloat = Float.intBitsToFloat(fp32DenormalMagic);
    int fp32QnanMask = 0x400000;
    int exponentBias = 15;
    int fp32ExponentBias = 127;
    int fp32ExponentShift = 23;

    int bits = float16 & 0xffff;
    int s = bits & signMask;
    int e = (bits >>> exponentShift) & shiftedExponentMask;
    int m = (bits) & significandMask;
    int outE = 0;
    int outM = 0;
    if (e == 0) { // Denormal or 0
      if (m != 0) {
        // Convert denorm fp16 into normalized fp32
        float o = Float.intBitsToFloat(fp32DenormalMagic + m);
        o -= fp32DenormalFloat;
        return s == 0 ? o : -o;
      }
    } else {
      outM = m << 13;
      if (e == 0x1f) { // Infinite or NaN
        outE = 0xff;
        if (outM != 0) { // SNaNs are quieted
          outM |= fp32QnanMask;
        }
      } else {
        outE = e - exponentBias + fp32ExponentBias;
      }
    }
    int out = (s << 16) | (outE << fp32ExponentShift) | outM;
    return Float.intBitsToFloat(out);
  }

  /*
   * Source: The Android Open Source Project
   * License: the Apache License, Version 2.0
   * Github: https://github.com/caoccao/Javet/blob/main/src/main/java/com/caoccao/javet/utils/Float16.java
   */
  public static short floatToFloat16(float flt) {
    // The float16 format has 1 sign bit, 5 exponent bits, and 10 mantissa bits.
    // The exponent bias is 15.
    int exponentShift = 10;
    int signShift = 15;
    int exponentBias = 15;
    int fp32ExponentBias = 127;
    int fp32ExponentShift = 23;
    int fp32SignShift = 31;
    int fp32ShiftedExponentMask = 0xff;
    int fp32SignificandMask = 0x7fffff;

    int bits = Float.floatToRawIntBits(flt);
    int s = (bits >>> fp32SignShift);
    int e = (bits >>> fp32ExponentShift) & fp32ShiftedExponentMask;
    int m = (bits) & fp32SignificandMask;
    int outE = 0;
    int outM = 0;
    if (e == 0xff) { // Infinite or NaN
      outE = 0x1f;
      outM = m != 0 ? 0x200 : 0;
    } else {
      e = e - fp32ExponentBias + exponentBias;
      if (e >= 0x1f) { // Overflow
        outE = 0x1f;
      } else if (e <= 0) { // Underflow
        if (e < -10) {
          // The absolute fp32 value is less than MIN_VALUE, flush to +/-0
        } else {
          // The fp32 value is a normalized float less than MIN_NORMAL,
          // we convert to a denorm fp16
          m = m | 0x800000;
          int shift = 14 - e;
          outM = m >> shift;
          int lowm = m & ((1 << shift) - 1);
          int hway = 1 << (shift - 1);
          // if above halfway or exactly halfway and outM is odd
          if (lowm + (outM & 1) > hway) {
            // Round to nearest even
            // Can overflow into exponent bit, which surprisingly is OK.
            // This increment relies on the +outM in the return statement below
            outM++;
          }
        }
      } else {
        outE = e;
        outM = m >> 13;
        // if above halfway or exactly halfway and outM is odd
        if ((m & 0x1fff) + (outM & 0x1) > 0x1000) {
          // Round to nearest even
          // Can overflow into exponent bit, which surprisingly is OK.
          // This increment relies on the +outM in the return statement below
          outM++;
        }
      }
    }
    // The outM is added here as the +1 increments for outM above can
    // cause an overflow in the exponent bit which is OK.
    return (short) ((s << signShift) | (outE << exponentShift) + outM);
  }
}
