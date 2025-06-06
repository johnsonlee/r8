// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.timing;

import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.SystemPropertyUtils;
import com.android.tools.r8.utils.ThrowingAction;
import com.android.tools.r8.utils.ThrowingSupplier;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

public class TimingImpl extends Timing {

  private static final int MINIMUM_REPORT_MS =
      SystemPropertyUtils.parseSystemPropertyOrDefault(
          "com.android.tools.r8.printtimes.minvalue_ms", 10);
  private static final int MINIMUM_REPORT_PERCENTAGE =
      SystemPropertyUtils.parseSystemPropertyOrDefault(
          "com.android.tools.r8.printtimes.minvalue", 0);

  private final Node top;
  private final Deque<Node> stack;
  private final boolean trackMemory;

  TimingImpl(String title, InternalOptions options) {
    this.trackMemory = options.printMemory;
    stack = new ArrayDeque<>();
    top = new Node(title, trackMemory);
    stack.push(top);
  }

  private static class MemInfo {
    final long used;

    MemInfo(long used) {
      this.used = used;
    }

    public static MemInfo fromTotalAndFree(long total, long free) {
      return new MemInfo(total - free);
    }

    long usedDelta(MemInfo previous) {
      return used - previous.used;
    }
  }

  static class Node {
    final String title;
    final boolean trackMemory;

    final Map<String, Node> children = new LinkedHashMap<>();
    long duration = 0;
    long start_time;
    Map<String, MemInfo> startMemory;
    Map<String, MemInfo> endMemory;

    Node(String title, boolean trackMemory) {
      this.title = title;
      this.trackMemory = trackMemory;
      if (trackMemory) {
        startMemory = computeMemoryInformation();
      }
      this.start_time = System.nanoTime();
    }

    void restart() {
      assert start_time == -1;
      if (trackMemory) {
        startMemory = computeMemoryInformation();
      }
      start_time = System.nanoTime();
    }

    void end() {
      duration += System.nanoTime() - start_time;
      start_time = -1;
      assert duration() >= 0;
      if (trackMemory) {
        endMemory = computeMemoryInformation();
      }
    }

    long duration() {
      return duration;
    }

    @Override
    public String toString() {
      return title + ": " + prettyTime(duration());
    }

    public String toString(Node top) {
      if (this == top) return toString();
      return "(" + prettyPercentage(duration(), top.duration()) + ") " + toString();
    }

    public void report(int depth, Node top) {
      assert duration() >= 0;
      if (durationInMs(duration()) < MINIMUM_REPORT_MS) {
        return;
      }
      if (percentage(duration(), top.duration()) < MINIMUM_REPORT_PERCENTAGE) {
        return;
      }
      printPrefix(depth);
      System.out.println(toString(top));
      if (trackMemory) {
        printMemory(depth);
      }
      if (children.isEmpty()) {
        return;
      }
      Collection<Node> childNodes = children.values();
      long childTime = 0;
      for (Node childNode : childNodes) {
        childTime += childNode.duration();
      }
      if (childTime < duration()) {
        long unaccounted = duration() - childTime;
        if (durationInMs(unaccounted) >= MINIMUM_REPORT_MS
            && percentage(unaccounted, top.duration()) >= MINIMUM_REPORT_PERCENTAGE) {
          printPrefix(depth + 1);
          System.out.println(
              "("
                  + prettyPercentage(unaccounted, top.duration())
                  + ") Unaccounted: "
                  + prettyTime(unaccounted));
        }
      }
      childNodes.forEach(p -> p.report(depth + 1, top));
    }

    void printPrefix(int depth) {
      if (depth > 0) {
        System.out.print("  ".repeat(depth));
        System.out.print("- ");
      }
    }

    void printMemory(int depth) {
      for (Entry<String, MemInfo> start : startMemory.entrySet()) {
        if (start.getKey().equals("Memory")) {
          for (int i = 0; i <= depth; i++) {
            System.out.print("  ");
          }
          MemInfo endValue = endMemory.get(start.getKey());
          MemInfo startValue = start.getValue();
          System.out.println(
              start.getKey()
                  + " start: "
                  + prettySize(startValue.used)
                  + ", end: "
                  + prettySize(endValue.used)
                  + ", delta: "
                  + prettySize(endValue.usedDelta(startValue)));
        }
      }
    }
  }

  private static class TimingMergerImpl implements TimingMerger {

    final Node parent;
    final Node merged;

    private int taskCount = 0;
    private Node slowest = new Node("<zero>", false);

    TimingMergerImpl(String title, int numberOfThreads, TimingImpl timing) {
      Deque<Node> stack = timing.stack;
      parent = stack.peek();
      merged =
          new Node(title, timing.trackMemory) {
            @Override
            public void report(int depth, Node top) {
              assert duration() >= 0;
              printPrefix(depth);
              System.out.print(toString());
              if (numberOfThreads <= 0) {
                System.out.println(" (unknown thread count)");
              } else {
                long walltime = parent.duration();
                long perThreadTime = duration() / numberOfThreads;
                System.out.println(
                    ", tasks: "
                        + taskCount
                        + ", threads: "
                        + numberOfThreads
                        + ", utilization: "
                        + TimingImpl.prettyPercentage(perThreadTime, walltime));
              }
              if (trackMemory) {
                printMemory(depth);
              }
              // Report children with this merge node as "top" so times are relative to the total
              // merge.
              children.forEach((title, node) -> node.report(depth + 1, this));
              // Print the slowest entry if one was found.
              if (slowest != null && slowest.duration > 0) {
                printPrefix(depth);
                System.out.println("SLOWEST " + slowest.toString(this));
                slowest.children.forEach((title, node) -> node.report(depth + 1, this));
              }
            }

            @Override
            public String toString() {
              return "MERGE " + super.toString();
            }
          };
    }

    @Override
    public TimingMerger disableSlowestReporting() {
      slowest = null;
      return this;
    }

    @Override
    public boolean isEmpty() {
      return false;
    }

    private static class Item {

      final Node mergeTarget;
      final Node mergeSource;

      public Item(Node mergeTarget, Node mergeSource) {
        this.mergeTarget = mergeTarget;
        this.mergeSource = mergeSource;
      }
    }

    @Override
    public void add(Collection<Timing> timings) {
      final boolean trackMemory = merged.trackMemory;
      Deque<Item> worklist = new ArrayDeque<>();
      for (Timing timing : timings) {
        if (timing instanceof TimingDelegate) {
          timing = ((TimingDelegate) timing).getDelegate();
        }
        if (timing == Timing.empty()) {
          continue;
        }
        assert timing instanceof TimingImpl;
        TimingImpl timingImpl = (TimingImpl) timing;
        Deque<Node> stack = timingImpl.stack;
        assert stack.isEmpty() : "Expected sub-timing to have completed prior to merge";
        ++taskCount;
        merged.duration += timingImpl.top.duration;
        if (slowest != null && timingImpl.top.duration > slowest.duration) {
          slowest = timingImpl.top;
        }
        worklist.addLast(new Item(merged, timingImpl.top));
      }
      while (!worklist.isEmpty()) {
        Item item = worklist.pollFirst();
        item.mergeSource.children.forEach(
            (title, child) -> {
              Node mergeTarget =
                  item.mergeTarget.children.computeIfAbsent(title, t -> new Node(t, trackMemory));
              mergeTarget.duration += child.duration;
              mergeTarget.endMemory = child.endMemory;
              if (!child.children.isEmpty()) {
                worklist.addLast(new Item(mergeTarget, child));
              }
            });
      }
    }

    @Override
    public void end() {
      assert TimingImpl.verifyUnambiguous(parent, merged.title);
      merged.end();
      parent.children.put(merged.title, merged);
    }
  }

  private static boolean verifyUnambiguous(Node node, String title) {
    // If this assertion fails, then the merger is reusing the same name as a previous point.
    // The point should likely be disambiguated by adding a timing.begin/end in the call chain.
    assert !node.children.containsKey(title) : "Ambiguous timing chain. Insert a begin/end to fix";
    return true;
  }

  @Override
  public TimingMerger beginMerger(String title, int numberOfThreads) {
    assert !stack.isEmpty();
    assert verifyUnambiguous(stack.peekFirst(), title);
    return new TimingMergerImpl(title, numberOfThreads, this);
  }

  private static long durationInMs(long value) {
    return value / 1_000_000;
  }

  private static long durationInS(long value) {
    return value / 1_000_000_000;
  }

  private static long percentage(long part, long total) {
    return part * 100 / total;
  }

  private static String prettyPercentage(long part, long total) {
    return percentage(part, total) + "%";
  }

  private static String prettyTime(long value) {
    long seconds = durationInS(value);
    long HH = seconds / 3600;
    long MM = (seconds % 3600) / 60;
    long SS = seconds % 60;
    if (HH > 0) {
      return String.format("%sms (%sh%sm%ss)", durationInMs(value), HH, MM, SS);
    }
    if (MM > 0) {
      return String.format("%sms (%sm%ss)", durationInMs(value), MM, SS);
    }
    if (SS > 0) {
      return String.format("%sms (%ss)", durationInMs(value), SS);
    }
    return String.format("%sms", durationInMs(value));
  }

  private static String prettySize(long value) {
    return prettyNumber(value / 1024) + "k";
  }

  private static String prettyNumber(long value) {
    String printed = "" + Math.abs(value);
    if (printed.length() < 4) {
      return "" + value;
    }
    StringBuilder builder = new StringBuilder();
    if (value < 0) {
      builder.append('-');
    }
    int prefix = printed.length() % 3;
    builder.append(printed, 0, prefix);
    for (int i = prefix; i < printed.length(); i += 3) {
      if (i > 0) {
        builder.append('.');
      }
      builder.append(printed, i, i + 3);
    }
    return builder.toString();
  }

  @Override
  public Timing begin(String title) {
    Node parent = stack.peek();
    Node child;
    if (parent.children.containsKey(title)) {
      child = parent.children.get(title);
      child.restart();
    } else {
      child = new Node(title, trackMemory);
      parent.children.put(title, child);
    }
    stack.push(child);
    return this;
  }

  @Override
  public <E extends Exception> void time(String title, ThrowingAction<E> action) throws E {
    begin(title);
    try {
      action.execute();
    } finally {
      end();
    }
  }

  @Override
  public <T, E extends Exception> T time(String title, ThrowingSupplier<T, E> supplier) throws E {
    begin(title);
    try {
      return supplier.get();
    } finally {
      end();
    }
  }

  @Override
  public final void close() {
    end();
  }

  @Override
  public Timing end() {
    stack.peek().end(); // record time.
    stack.pop();
    return this;
  }

  @Override
  public void report() {
    assert stack.size() == 1 : "Unexpected non-singleton stack: " + stack;
    Node top = stack.peek();
    assert top == this.top;
    top.end();
    System.out.println("Recorded timings:");
    top.report(0, top);
  }

  private static Map<String, MemInfo> computeMemoryInformation() {
    System.gc();
    Map<String, MemInfo> info = new LinkedHashMap<>();
    info.put(
        "Memory",
        MemInfo.fromTotalAndFree(
            Runtime.getRuntime().totalMemory(), Runtime.getRuntime().freeMemory()));
    return info;
  }
}
