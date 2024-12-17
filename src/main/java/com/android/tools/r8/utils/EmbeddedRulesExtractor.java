// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import com.android.tools.r8.DataDirectoryResource;
import com.android.tools.r8.DataEntryResource;
import com.android.tools.r8.DataResourceProvider;
import com.android.tools.r8.ResourceException;
import com.android.tools.r8.shaking.ProguardConfigurationParser;
import com.android.tools.r8.shaking.ProguardConfigurationSource;
import com.android.tools.r8.shaking.ProguardConfigurationSourceBytes;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class EmbeddedRulesExtractor implements DataResourceProvider.Visitor {

  private final Supplier<SemanticVersion> compilerVersionSupplier;
  private final Reporter reporter;
  private final List<ProguardConfigurationSource> proguardSources = new ArrayList<>();
  private final List<ProguardConfigurationSource> r8Sources = new ArrayList<>();
  private SemanticVersion compilerVersion;

  public EmbeddedRulesExtractor(
      Reporter reporter, Supplier<SemanticVersion> compilerVersionSupplier) {
    this.compilerVersionSupplier = compilerVersionSupplier;
    this.reporter = reporter;
  }

  @Override
  public void visit(DataDirectoryResource directory) {
    // Don't do anything.
  }

  @Override
  public void visit(DataEntryResource resource) {
    if (isRelevantProguardResource(resource)) {
      assert !isRelevantR8Resource(resource);
      readProguardConfigurationSource(resource, proguardSources::add);
    } else if (isRelevantR8Resource(resource)) {
      assert !isRelevantProguardResource(resource);
      readProguardConfigurationSource(resource, r8Sources::add);
    }
  }

  private void readProguardConfigurationSource(
      DataEntryResource resource, Consumer<ProguardConfigurationSource> consumer) {
    try (InputStream in = resource.getByteStream()) {
      consumer.accept(new ProguardConfigurationSourceBytes(in, resource.getOrigin()));
    } catch (ResourceException e) {
      reporter.error(
          new StringDiagnostic("Failed to open input: " + e.getMessage(), resource.getOrigin()));
    } catch (Exception e) {
      reporter.error(new ExceptionDiagnostic(e, resource.getOrigin()));
    }
  }

  private boolean isRelevantProguardResource(DataEntryResource resource) {
    // Configurations in META-INF/com.android.tools/proguard/ are ignored.
    final String proguardPrefix = "META-INF/proguard";
    if (!resource.getName().startsWith(proguardPrefix)) {
      return false;
    }
    String withoutPrefix = resource.getName().substring(proguardPrefix.length());
    return withoutPrefix.startsWith("/");
  }

  private boolean isRelevantR8Resource(DataEntryResource resource) {
    final String r8Prefix = "META-INF/com.android.tools/r8";
    if (!resource.getName().startsWith(r8Prefix)) {
      return false;
    }
    String withoutPrefix = resource.getName().substring(r8Prefix.length());
    if (withoutPrefix.startsWith("/")) {
      // Everything under META-INF/com.android.tools/r8/ is included (not version specific).
      return true;
    }
    // Expect one of the following patterns:
    //   com.android.tools/r8-from-1.5.0/
    //   com.android.tools/r8-upto-1.6.0/
    //   com.android.tools/r8-from-1.5.0-upto-1.6.0/
    final String fromPrefix = "-from-";
    final String uptoPrefix = "-upto-";
    if (!withoutPrefix.startsWith(fromPrefix) && !withoutPrefix.startsWith(uptoPrefix)) {
      return false;
    }

    SemanticVersion from = SemanticVersion.min();
    SemanticVersion upto = null;

    if (withoutPrefix.startsWith(fromPrefix)) {
      withoutPrefix = withoutPrefix.substring(fromPrefix.length());
      int versionEnd = StringUtils.indexOf(withoutPrefix, '-', '/');
      if (versionEnd == -1) {
        return false;
      }
      try {
        from = SemanticVersion.parse(withoutPrefix.substring(0, versionEnd));
      } catch (IllegalArgumentException e) {
        return false;
      }
      withoutPrefix = withoutPrefix.substring(versionEnd);
    }
    if (withoutPrefix.startsWith(uptoPrefix)) {
      withoutPrefix = withoutPrefix.substring(uptoPrefix.length());
      int versionEnd = withoutPrefix.indexOf('/');
      if (versionEnd == -1) {
        return false;
      }
      try {
        upto = SemanticVersion.parse(withoutPrefix.substring(0, versionEnd));
      } catch (IllegalArgumentException e) {
        return false;
      }
    }
    if (compilerVersion == null) {
      compilerVersion = compilerVersionSupplier.get();
    }
    return compilerVersion.isNewerOrEqual(from) && (upto == null || upto.isNewer(compilerVersion));
  }

  private void parse(
      List<ProguardConfigurationSource> sources, ProguardConfigurationParser parser) {
    for (ProguardConfigurationSource source : sources) {
      try {
        parser.parse(source);
      } catch (Exception e) {
        reporter.error(new ExceptionDiagnostic(e, source.getOrigin()));
      }
    }
  }

  private List<ProguardConfigurationSource> getRelevantRules() {
    return !r8Sources.isEmpty() ? r8Sources : proguardSources;
  }

  public void parseRelevantRules(ProguardConfigurationParser parser) {
    parse(getRelevantRules(), parser);
  }

  public void visitRelevantRules(Consumer<ProguardConfigurationSource> consumer) {
    getRelevantRules().forEach(consumer);
  }
}
