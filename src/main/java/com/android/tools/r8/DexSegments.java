// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.ProgramResource.Kind;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.dex.DexParser;
import com.android.tools.r8.dex.DexSection;
import com.android.tools.r8.origin.CommandLineOrigin;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.StringDiagnostic;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Closer;
import it.unimi.dsi.fastutil.ints.Int2ReferenceLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Map;

public class DexSegments {
  public static class Command extends BaseCommand {

    private final boolean csv;

    public static class Builder
        extends BaseCommand.Builder<Command, Builder> {

      private boolean csv = false;

      @Override
      Command.Builder self() {
        return this;
      }

      private Builder setCsv(boolean csv) {
        this.csv = csv;
        return self();
      }

      @Override
      protected Command makeCommand() {
        // If printing versions ignore everything else.
        if (isPrintHelp()) {
          return new Command(isPrintHelp());
        }
        return new Command(getAppBuilder().build(), csv);
      }
    }

    static final String USAGE_MESSAGE = String.join("\n", ImmutableList.of(
        "Usage: dexsegments [options] <input-files>",
        " where <input-files> are dex files",
        "  --version               # Print the version of r8.",
        "  --help                  # Print this message."));

    public static Command.Builder builder() {
      return new Command.Builder();
    }

    public static Command.Builder parse(String[] args) {
      Command.Builder builder = builder();
      parse(args, builder);
      return builder;
    }

    private static void parse(String[] args, Command.Builder builder) {
      for (int i = 0; i < args.length; i++) {
        String arg = args[i].trim();
        if (arg.length() == 0) {
          continue;
        } else if (arg.equals("--help")) {
          builder.setPrintHelp(true);
        } else if (arg.equals("--csv")) {
          builder.setCsv(true);
        } else {
          if (arg.startsWith("--")) {
            builder.getReporter().error(new StringDiagnostic("Unknown option: " + arg,
                CommandLineOrigin.INSTANCE));
          }
          builder.addProgramFiles(Paths.get(arg));
        }
      }
    }

    private Command(AndroidApp inputApp, boolean csv) {
      super(inputApp);
      this.csv = csv;
    }

    private Command(boolean printHelp) {
      super(printHelp, false);
      this.csv = false;
    }

    @Override
    InternalOptions getInternalOptions() {
      return new InternalOptions();
    }
  }

  public static void main(String[] args)
      throws IOException, CompilationFailedException, ResourceException {
    Command.Builder builder = Command.parse(args);
    Command cmd = builder.build();
    Map<Integer, SegmentInfo> result = run(cmd);
    if (result == null) {
      return;
    }
    if (cmd.csv) {
      System.out.println("\"Name\",\"Size\",\"Items\"");
      result.forEach(
          (key, value) -> {
            System.out.println(
                "\"" + DexSection.typeName(key) + "\", " + value.size + ", " + value.items);
            if (key == Constants.TYPE_TYPE_LIST) {
              // Type items header is just a uint, and each element is a ushort. see
              // https://source.android.com/devices/tech/dalvik/dex-format#type-list.
              int typeItemsSize = (value.size - value.items * 4);
              System.out.println(
                  "\"TypeItems\", " + typeItemsSize + ", " + (typeItemsSize / 2) + "");
            }
          });
    } else {
      System.out.println("Segments in dex application (name: size / items):");
      // This output is parsed by tools/test_framework.py. Check the parsing there when updating.
      result.forEach(
          (key, value) -> {
            System.out.print(
                " - " + DexSection.typeName(key) + ": " + value.size + " / " + value.items);
            if (key == Constants.TYPE_TYPE_LIST) {
              // Type items header is just a uint, and each element is a ushort. see
              // https://source.android.com/devices/tech/dalvik/dex-format#type-list.
              int typeItemsSize = (value.size - value.items * 4);
              System.out.print(" (TypeItems: " + typeItemsSize + " / " + (typeItemsSize / 2) + ")");
            }
            System.out.println();
          });
    }
  }

  public static Map<Integer, SegmentInfo> runForTesting(Command command)
      throws IOException, ResourceException {
    return run(command);
  }

  public static Int2ReferenceMap<SegmentInfo> run(Command command)
      throws IOException, ResourceException {
    if (command.isPrintHelp()) {
      System.out.println(Command.USAGE_MESSAGE);
      return null;
    }
    return run(command.getInputApp());
  }

  public static Map<Integer, SegmentInfo> runForTesting(AndroidApp app)
      throws IOException, ResourceException {
    return run(app);
  }

  public static Int2ReferenceMap<SegmentInfo> run(AndroidApp app)
      throws IOException, ResourceException {
    Int2ReferenceMap<SegmentInfo> result = new Int2ReferenceLinkedOpenHashMap<>();
    for (int benchmark : DexSection.getConstants()) {
      result.put(benchmark, new SegmentInfo());
    }
    try (Closer closer = Closer.create()) {
      for (ProgramResource resource : app.computeAllProgramResources()) {
        if (resource.getKind() == Kind.DEX) {
          for (DexSection dexSection :
              DexParser.parseMapFrom(
                  closer.register(resource.getByteStream()), resource.getOrigin())) {
            assert result.containsKey(dexSection.type) : dexSection.typeName();
            SegmentInfo info = result.get(dexSection.type);
            info.increment(dexSection.length, dexSection.size());
          }
        }
      }
    }
    return result;
  }

  public static class SegmentInfo {
    private int items;
    private int size;

    SegmentInfo() {
      this.items = 0;
      this.size = 0;
    }

    void increment(int items, int size) {
      this.items += items;
      this.size += size;
    }

    public int getItemCount() {
      return items;
    }

    public int getSegmentSize() {
      return size;
    }
  }
}
