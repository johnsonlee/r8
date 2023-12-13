// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.android.tools.r8.PartitionMapConsumer;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.retrace.MappingPartition;
import com.android.tools.r8.retrace.MappingPartitionMetadata;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.Box;
import com.android.tools.r8.utils.StringUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MappingHeaderContentTest extends TestBase {

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public MappingHeaderContentTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  @Test
  public void test() throws Exception {
    Box<MappingPartitionMetadata> metadataBox = new Box<>();
    List<MappingPartition> partitions = new ArrayList<>();
    StringBuilder builder = new StringBuilder();
    testForR8(Backend.DEX)
        .addInnerClasses(MappingHeaderContentTest.class)
        .addKeepMainRule(TestClass.class)
        .setMinApi(AndroidApiLevel.B)
        .apply(b -> b.getBuilder().setProguardMapConsumer((s, unused) -> builder.append(s)))
        .apply(
            b ->
                b.getBuilder()
                    .setPartitionMapConsumer(
                        new PartitionMapConsumer() {
                          @Override
                          public void acceptMappingPartition(MappingPartition mappingPartition) {
                            partitions.add(mappingPartition);
                          }

                          @Override
                          public void acceptMappingPartitionMetadata(
                              MappingPartitionMetadata mappingPartitionMetadata) {
                            assertNull(metadataBox.get());
                            metadataBox.set(mappingPartitionMetadata);
                          }
                        }))
        .compile();
    assertNotNull(metadataBox.get());
    assertFalse(partitions.isEmpty());
    String mapping = builder.toString();
    List<String> mapIdLines =
        StringUtils.splitLines(mapping).stream()
            .filter(s -> s.startsWith("# pg_map_id:"))
            .collect(Collectors.toList());
    assertEquals(
        "Expected single pg_map_id line, found multiple:\n" + String.join("\n", mapIdLines) + "\n",
        1,
        mapIdLines.size());
  }

  static class A {}

  static class B {}

  static class TestClass {

    public static void main(String[] args) {
      System.out.println(args.length == 0 ? A.class : B.class);
    }
  }
}
