// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.graph.AccessFlags;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Path;

public class AutoCloseableAndroidLibraryFileData extends TestBase {

  public static Path compileAutoCloseableAndroidLibraryClasses(
      TestBase testBase, TestParameters parameters) throws Exception {
    return testBase
        .testForD8()
        .addProgramClassFileData(getAutoCloseableAndroidClassData(parameters))
        .setMinApi(parameters)
        .compile()
        .writeToZip();
  }

  public static ImmutableList<byte[]> getAutoCloseableAndroidClassData(TestParameters parameters)
      throws Exception {
    return ImmutableList.of(
        getMediaDrm(parameters),
        getContentProviderClient(parameters),
        getDrmManagerClient(parameters),
        getMediaMetadataRetriever(parameters),
        getTypedArray(parameters),
        getTransformedBuildVERSIONClass());
  }

  private static byte[] getContentProviderClient(TestParameters parameters) throws IOException {
    return getTransformedClass(
        parameters,
        AndroidApiLevel.N,
        ContentProviderClientApiLevel24.class,
        ContentProviderClient.class,
        DexItemFactory.androidContentContentProviderClientDescriptorString);
  }

  private static byte[] getDrmManagerClient(TestParameters parameters) throws IOException {
    return getTransformedClass(
        parameters,
        AndroidApiLevel.N,
        DrmManagerClientApiLevel24.class,
        DrmManagerClient.class,
        DexItemFactory.androidDrmDrmManagerClientDescriptorString);
  }

  private static byte[] getMediaDrm(TestParameters parameters) throws IOException {
    return getTransformedClass(
        parameters,
        AndroidApiLevel.P,
        MediaDrmApiLevel28.class,
        MediaDrm.class,
        DexItemFactory.androidMediaMediaDrmDescriptorString);
  }

  private static byte[] getMediaMetadataRetriever(TestParameters parameters) throws IOException {
    return getTransformedClass(
        parameters,
        AndroidApiLevel.Q,
        MediaMetadataRetrieverApiLevel29.class,
        MediaMetadataRetriever.class,
        DexItemFactory.androidMediaMediaMetadataRetrieverDescriptorString);
  }

  private static byte[] getTypedArray(TestParameters parameters) throws IOException {
    return getTransformedClass(
        parameters,
        AndroidApiLevel.S,
        TypedArrayAndroidApiLevel31.class,
        TypedArray.class,
        DexItemFactory.androidContentResTypedArrayDescriptorString);
  }

  private static byte[] getTransformedClass(
      TestParameters parameters,
      AndroidApiLevel minApiLevel,
      Class<?> lowApiClass,
      Class<?> highApiClass,
      String descriptorString)
      throws IOException {
    if (parameters.getApiLevel().isGreaterThanOrEqualTo(minApiLevel)) {
      return transformer(lowApiClass).setClassDescriptor(descriptorString).clearNest().transform();
    } else {
      return transformer(highApiClass).setClassDescriptor(descriptorString).clearNest().transform();
    }
  }

  private static byte[] getTransformedBuildVERSIONClass() throws IOException, NoSuchFieldException {
    return transformer(VERSION.class)
        .setClassDescriptor("Landroid/os/Build$VERSION;")
        .setAccessFlags(VERSION.class.getDeclaredField("SDK_INT"), AccessFlags::setFinal)
        .transform();
  }

  // Minimal android.os.Build$VERSION for runtime classpath.
  public static class /*android.os.Build$*/ VERSION {

    public static /*final*/ int SDK_INT = -1;
  }

  public static class ContentProviderClient {

    public boolean wasClosed = false;

    public void close() {
      throw new AssertionError("close should not be called");
    }

    public boolean release() {
      wasClosed = true;
      return wasClosed;
    }
  }

  public static class ContentProviderClientApiLevel24 implements AutoCloseable {

    public boolean wasClosed = false;

    public void close() {
      wasClosed = true;
    }

    public boolean release() {
      throw new AssertionError("release should not be called");
    }
  }

  public static class DrmManagerClient {

    public boolean wasClosed = false;

    public void close() {
      throw new AssertionError("close should not be called");
    }

    public void release() {
      wasClosed = true;
    }
  }

  public static class DrmManagerClientApiLevel24 implements AutoCloseable {

    public boolean wasClosed = false;

    public void close() {
      wasClosed = true;
    }

    public void release() {
      throw new AssertionError("release should not be called");
    }
  }

  public static class MediaDrm {

    public boolean wasClosed = false;

    public void close() {
      throw new AssertionError("close should not be called");
    }

    public void release() {
      wasClosed = true;
    }
  }

  public static class MediaDrmApiLevel28 implements AutoCloseable {

    public boolean wasClosed = false;

    public void close() {
      wasClosed = true;
    }

    public void release() {
      throw new AssertionError("release should not be called");
    }
  }

  public static class MediaMetadataRetriever {

    public boolean wasClosed = false;

    public void close() {
      throw new AssertionError("close should not be called");
    }

    public void release() {
      wasClosed = true;
    }
  }

  public static class MediaMetadataRetrieverApiLevel29 implements AutoCloseable {

    public boolean wasClosed = false;

    public void close() {
      wasClosed = true;
    }

    public void release() {
      throw new AssertionError("release should not be called");
    }
  }

  public static class TypedArray {

    public boolean wasClosed = false;

    public void close() {
      throw new AssertionError("close should not be called");
    }

    public void recycle() {
      wasClosed = true;
    }
  }

  public static class TypedArrayAndroidApiLevel31 implements AutoCloseable {

    public boolean wasClosed = false;

    public void close() {
      wasClosed = true;
    }

    public void recycle() {
      throw new AssertionError("recycle should not be called");
    }
  }
}
