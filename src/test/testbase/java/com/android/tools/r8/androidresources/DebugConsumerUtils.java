// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.androidresources;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.StringConsumer;
import com.google.common.base.CharMatcher;
import java.util.ArrayList;
import java.util.List;

public class DebugConsumerUtils {

  private static void ensureReachableOptimized(
      List<String> logStrings, String type, String name, boolean reachable) {
    assertEquals(
        reachable, logStrings.stream().anyMatch(s -> s.startsWith(type + ":" + name + ":")));
  }

  private static void ensureDexReachableResourcesState(
      List<String> logStrings, String type, String name, boolean reachable) {
    // Example line:
    // Marking drawable:foobar:2130771968 reachable: referenced from classes.dex
    assertEquals(
        reachable,
        logStrings.stream()
            .anyMatch(
                s ->
                    s.contains("Marking " + type + ":" + name + ":")
                        && s.contains("reachable: referenced from")));
  }

  private static void ensureResourceReachabilityState(
      List<String> logStrings, String type, String name, boolean reachable) {
    // Example line:
    // @packagename:string/bar : reachable=true
    assertTrue(
        logStrings.stream()
            .anyMatch(s -> s.contains(type + "/" + name + " : reachable=" + reachable)));
  }

  private static void ensureRootResourceState(
      List<String> logStrings, String type, String name, boolean isRoot) {
    assertEquals(isRoot, isInSection(logStrings, type, name, "The root reachable resources are:"));
  }

  private static void ensureUnusedState(
      List<String> logStrings, String type, String name, boolean isUnused) {
    assertEquals(isUnused, isInSection(logStrings, type, name, "Unused resources are:"));
  }

  private static boolean isInSection(
      List<String> logStrings, String type, String name, String sectionHeader) {
    // Example for roots
    // "The root reachable resources are:"
    // " drawable:foobar:2130771968"
    boolean isInSection = false;
    for (String logString : logStrings) {
      if (logString.equals(sectionHeader)) {
        isInSection = true;
        continue;
      }
      if (isInSection) {
        if (!logString.startsWith(" ")) {
          return false;
        }
        if (logString.startsWith(" " + type + ":" + name)) {
          return true;
        }
      }
    }
    return false;
  }

  private static void ensureDexReachable(
      List<String> logLines, String type, String dexReachableString) {
    ensureDexReachableResourcesState(logLines, type, dexReachableString, true);
    ensureResourceReachabilityState(logLines, type, dexReachableString, true);
    ensureRootResourceState(logLines, type, dexReachableString, true);
    ensureUnusedState(logLines, type, dexReachableString, false);
  }

  private static void ensureUnreachable(List<String> logLines, String type, String unused) {
    ensureDexReachableResourcesState(logLines, type, unused, false);
    ensureResourceReachabilityState(logLines, type, unused, false);
    ensureRootResourceState(logLines, type, unused, false);
    ensureUnusedState(logLines, type, unused, true);
  }

  private static void ensureResourceRoot(List<String> logLines, String type, String name) {
    ensureDexReachableResourcesState(logLines, type, name, false);
    ensureResourceReachabilityState(logLines, type, name, true);
    ensureRootResourceState(logLines, type, name, true);
    ensureUnusedState(logLines, type, name, false);
  }

  public interface ResourceShrinkerLogInspector {
    List<String> getLogLines();

    boolean getFinished();

    void ensureDexReachable(String type, String dexReachableString);

    void ensureUnreachable(String type, String unused);

    void ensureResourceRoot(String type, String name);

    void ensureReachableOptimized(String type, String name);

    void ensureUnreachableOptimized(String type, String name);
  }

  public static class TestDebugConsumer implements StringConsumer, ResourceShrinkerLogInspector {
    private boolean finished = false;
    private List<String> logLines = new ArrayList<>();

    @Override
    public void finished(DiagnosticsHandler handler) {
      finished = true;
      StringConsumer.super.finished(handler);
    }

    @Override
    public void accept(String string, DiagnosticsHandler handler) {
      assert !finished;
      // Includes trailing newline, strip these.
      logLines.add(CharMatcher.whitespace().trimTrailingFrom(string));
    }

    public void ensureDexReachable(String type, String dexReachableString) {
      DebugConsumerUtils.ensureDexReachable(getLogLines(), type, dexReachableString);
    }

    @Override
    public void ensureUnreachable(String type, String unused) {
      DebugConsumerUtils.ensureUnreachable(getLogLines(), type, unused);
    }

    @Override
    public void ensureResourceRoot(String type, String name) {
      DebugConsumerUtils.ensureResourceRoot(getLogLines(), type, name);
    }

    @Override
    public void ensureReachableOptimized(String type, String name) {
      DebugConsumerUtils.ensureReachableOptimized(getLogLines(), type, name, true);
    }

    @Override
    public void ensureUnreachableOptimized(String type, String name) {
      DebugConsumerUtils.ensureReachableOptimized(getLogLines(), type, name, false);
    }

    public List<String> getLogLines() {
      return logLines;
    }

    public boolean getFinished() {
      return finished;
    }
  }
}
