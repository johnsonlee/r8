// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.d8;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.ByteDataView;
import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.DexFilePerClassFileConsumer;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.ExtractMarker;
import com.android.tools.r8.ExtractMarkerCommand;
import com.android.tools.r8.MarkerInfo;
import com.android.tools.r8.MarkerInfoConsumer;
import com.android.tools.r8.MarkerInfoConsumerData;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.dex.Marker;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.StringUtils;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class IntermediateModeMarkerTest extends TestBase {

  static final String EXPECTED = StringUtils.lines("Hello, world");

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultDexRuntime().build();
  }

  private final TestParameters parameters;

  public IntermediateModeMarkerTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    Map<String, byte[]> intermediatesA = new HashMap<>();
    Map<String, byte[]> intermediatesB = new HashMap<>();
    Map<String, byte[]> intermediatesC = new HashMap<>();

    testForD8(Backend.DEX)
        .addProgramClasses(A.class)
        .setMinApi(1)
        .setProgramConsumer(
            new DexFilePerClassFileConsumer.ForwardingConsumer(null) {
              @Override
              public void accept(
                  String primaryClassDescriptor,
                  ByteDataView data,
                  Set<String> descriptors,
                  DiagnosticsHandler handler) {
                byte[] old = intermediatesA.put(primaryClassDescriptor, data.copyByteData());
                assertNull(old);
              }

              @Override
              public boolean combineSyntheticClassesWithPrimaryClass() {
                return false;
              }
            })
        .compile();

    testForD8(Backend.DEX)
        .addProgramClasses(B.class)
        .setMinApi(2)
        .setProgramConsumer(
            new DexFilePerClassFileConsumer.ForwardingConsumer(null) {
              @Override
              public void accept(
                  String primaryClassDescriptor,
                  ByteDataView data,
                  Set<String> descriptors,
                  DiagnosticsHandler handler) {
                byte[] old = intermediatesB.put(primaryClassDescriptor, data.copyByteData());
                assertNull(old);
              }

              @Override
              public boolean combineSyntheticClassesWithPrimaryClass() {
                return true;
              }
            })
        .compile();

    testForD8(Backend.DEX)
        .addProgramClasses(C.class)
        .setMinApi(3)
        .setIntermediate(true)
        .setProgramConsumer(
            new DexIndexedConsumer.ForwardingConsumer(null) {
              @Override
              public void accept(
                  int fileIndex,
                  ByteDataView data,
                  Set<String> descriptors,
                  DiagnosticsHandler handler) {
                assertEquals(2, descriptors.size());
                byte[] old = intermediatesC.put("indexed", data.copyByteData());
                assertNull(old);
              }
            })
        .compile();

    // The per-class output has two outputs. Each has min-api 1.
    assertEquals(2, intermediatesA.size());
    assertMinApiMarkers(1, intermediatesA.values());
    // The per-input-class-file output has one output with min-api 2.
    assertEquals(1, intermediatesB.size());
    assertMinApiMarkers(2, intermediatesB.values());
    // The indexed output has one output with min-api 3.
    assertEquals(1, intermediatesC.size());
    assertMinApiMarkers(3, intermediatesC.values());

    testForD8(Backend.DEX)
        .addProgramDexFileData(intermediatesA.values())
        .addProgramDexFileData(intermediatesB.values())
        .addProgramDexFileData(intermediatesC.values())
        .setMinApi(4)
        .compile()
        .inspect(
            inspector -> {
              // Final merge has exactly three markers, one for each min-api intermediate.
              // The merge does not add a new marker.
              Collection<Marker> markers = inspector.getMarkers();
              assertEquals(3, markers.size());
              assertTrue(markers.stream().anyMatch(m -> m.getMinApi() == 1));
              assertTrue(markers.stream().anyMatch(m -> m.getMinApi() == 2));
              assertTrue(markers.stream().anyMatch(m -> m.getMinApi() == 3));
              assertTrue(markers.stream().noneMatch(m -> m.getMinApi() == 4));
            })
        .run(parameters.getRuntime(), A.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  private static void assertMinApiMarkers(int expectedMinApi, Collection<byte[]> dexPayloads)
      throws CompilationFailedException {
    for (byte[] data : dexPayloads) {
      ExtractMarker.run(
          ExtractMarkerCommand.builder()
              .addDexProgramData(data, Origin.unknown())
              .setMarkerInfoConsumer(
                  new MarkerInfoConsumer() {
                    @Override
                    public void acceptMarkerInfo(MarkerInfoConsumerData data) {
                      assertTrue(data.hasMarkers());
                      for (MarkerInfo marker : data.getMarkers()) {
                        assertEquals(expectedMinApi, marker.getMinApi());
                      }
                    }

                    @Override
                    public void finished() {}
                  })
              .build());
    }
  }

  public static class A {

    public static void main(String[] args) {
      B.foo(() -> "Hello");
    }
  }

  public static class B {

    public static void foo(Supplier<String> fn) {
      C.foo(() -> fn.get() + ", ");
    }
  }

  public static class C {

    public static void foo(Supplier<String> fn) {
      bar(() -> fn.get() + "world");
    }

    public static void bar(Supplier<String> fn) {
      System.out.println(fn.get());
    }
  }
}
