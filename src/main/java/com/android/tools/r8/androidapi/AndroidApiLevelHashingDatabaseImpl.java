// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.androidapi;

import static com.android.tools.r8.lightir.ByteUtils.isU2;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ThrowingCharIterator;
import com.android.tools.r8.utils.ThrowingFunction;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UTFDataFormatException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class AndroidApiLevelHashingDatabaseImpl implements AndroidApiLevelDatabase {

  private static final byte TYPE_IDENTIFIER = 0;
  private static final byte FIELD_IDENTIFIER = 1;
  private static final byte METHOD_IDENTIFIER = 2;

  private static final byte[] NON_EXISTING_DESCRIPTOR = new byte[0];

  private final List<DexString> androidApiExtensionPackages;
  private final Set<DexType> androidApiExtensionClasses;

  public static byte[] getNonExistingDescriptor() {
    return NON_EXISTING_DESCRIPTOR;
  }

  public static byte[] getUniqueDescriptorForReference(
      DexReference reference, ThrowingFunction<DexString, Integer, IOException> constantPoolLookup)
      throws IOException {
    if (reference.isDexType()) {
      return typeToBytes(constantPoolLookup.apply(reference.asDexType().getDescriptor()));
    }
    int holderId =
        constantPoolLookup.apply(reference.asDexMember().getHolderType().getDescriptor());
    if (holderId < 0) {
      return NON_EXISTING_DESCRIPTOR;
    }
    int nameId = constantPoolLookup.apply(reference.asDexMember().getName());
    if (nameId < 0) {
      return NON_EXISTING_DESCRIPTOR;
    }
    if (reference.isDexField()) {
      return fieldToBytes(
          holderId,
          nameId,
          constantPoolLookup.apply(reference.asDexField().getType().getDescriptor()));
    }
    assert reference.isDexMethod();
    return methodToBytes(holderId, nameId, reference.asDexMethod(), constantPoolLookup);
  }

  private static byte[] typeToBytes(int typeId) {
    if (typeId < 0) {
      return NON_EXISTING_DESCRIPTOR;
    }
    return new byte[] {
      TYPE_IDENTIFIER, getFirstByteFromShort(typeId), getSecondByteFromShort(typeId)
    };
  }

  private static byte[] fieldToBytes(int holderId, int nameId, int typeId) {
    if (holderId < 0 || nameId < 0 || typeId < 0) {
      return NON_EXISTING_DESCRIPTOR;
    }
    return new byte[] {
      FIELD_IDENTIFIER,
      getFirstByteFromShort(holderId),
      getSecondByteFromShort(holderId),
      getFirstByteFromShort(nameId),
      getSecondByteFromShort(nameId),
      getFirstByteFromShort(typeId),
      getSecondByteFromShort(typeId)
    };
  }

  private static byte[] methodToBytes(
      int holderId,
      int nameId,
      DexMethod method,
      ThrowingFunction<DexString, Integer, IOException> constantPoolLookup)
      throws IOException {
    if (holderId < 0 || nameId < 0) {
      return NON_EXISTING_DESCRIPTOR;
    }
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    baos.write(METHOD_IDENTIFIER);
    baos.write(getFirstByteFromShort(holderId));
    baos.write(getSecondByteFromShort(holderId));
    baos.write(getFirstByteFromShort(nameId));
    baos.write(getSecondByteFromShort(nameId));
    for (DexType parameter : method.proto.parameters) {
      int parameterId = constantPoolLookup.apply(parameter.getDescriptor());
      if (parameterId < 0) {
        return NON_EXISTING_DESCRIPTOR;
      }
      baos.write(getFirstByteFromShort(parameterId));
      baos.write(getSecondByteFromShort(parameterId));
    }
    int returnTypeId = constantPoolLookup.apply(method.getReturnType().getDescriptor());
    if (returnTypeId < 0) {
      return NON_EXISTING_DESCRIPTOR;
    }
    baos.write(getFirstByteFromShort(returnTypeId));
    baos.write(getSecondByteFromShort(returnTypeId));
    return baos.toByteArray();
  }

  private static byte getFirstByteFromShort(int value) {
    assert isU2(value);
    return (byte) (value >> 8);
  }

  private static byte getSecondByteFromShort(int value) {
    assert isU2(value);
    return (byte) value;
  }

  private final Map<DexReference, Optional<AndroidApiLevel>> lookupCache =
      new ConcurrentHashMap<>();
  private final Map<DexString, Integer> constantPoolCache = new ConcurrentHashMap<>();
  private final InternalOptions options;
  private final DiagnosticsHandler diagnosticsHandler;
  private static volatile AndroidApiDataAccess dataAccess;

  private static AndroidApiDataAccess getDataAccess(
      InternalOptions options, DiagnosticsHandler diagnosticsHandler) {
    if (dataAccess == null) {
      synchronized (AndroidApiDataAccess.class) {
        if (dataAccess == null) {
          dataAccess = AndroidApiDataAccess.create(options, diagnosticsHandler);
        }
      }
    }
    return dataAccess;
  }

  public AndroidApiLevelHashingDatabaseImpl(
      List<AndroidApiForHashingReference> predefinedApiTypeLookup,
      InternalOptions options,
      DiagnosticsHandler diagnosticsHandler) {
    this.options = options;
    this.diagnosticsHandler = diagnosticsHandler;
    predefinedApiTypeLookup.forEach(
        predefinedApiReference -> {
          // Do not use computeIfAbsent since a return value of null implies the key should not be
          // inserted.
          lookupCache.put(
              predefinedApiReference.getReference(),
              Optional.of(predefinedApiReference.getApiLevel()));
        });

    // Register classes in the extension libraries.
    {
      ImmutableSet.Builder<DexType> builder = ImmutableSet.builder();
      options
          .apiModelingOptions()
          .forEachAndroidApiExtensionClassDescriptor(
              descriptor -> builder.add(options.itemFactory.createType(descriptor)));
      this.androidApiExtensionClasses = builder.build();
    }
    // Register packages for extension libraries.
    // TODO(b/326252366): Remove support for  list of extension packages in favour of only
    //  supporting passing extension libraries as JAR files.
    {
      ImmutableList.Builder<DexString> builder = ImmutableList.builder();
      options
          .apiModelingOptions()
          .forEachAndroidApiExtensionPackage(
              pkg ->
                  builder.add(
                      options.itemFactory.createString(
                          "L"
                              + pkg.replace(
                                  DescriptorUtils.JAVA_PACKAGE_SEPARATOR,
                                  DescriptorUtils.DESCRIPTOR_PACKAGE_SEPARATOR)
                              + "/")));
      this.androidApiExtensionPackages = builder.build();
    }

    assert predefinedApiTypeLookup.stream()
        .allMatch(added -> added.getApiLevel().isEqualTo(lookupApiLevel(added.getReference())));
  }

  @Override
  public AndroidApiLevel getTypeApiLevel(DexType type) {
    return lookupApiLevel(type);
  }

  @Override
  public AndroidApiLevel getMethodApiLevel(DexMethod method) {
    return lookupApiLevel(method);
  }

  @Override
  public AndroidApiLevel getFieldApiLevel(DexField field) {
    return lookupApiLevel(field);
  }

  // CHeck that extensionPackage is the exact/full package of the descriptor.
  private boolean isPackageOfClass(DexString extensionPackage, DexString descriptor) {
    int packageLengthInBytes = extensionPackage.content.length;
    // DexString content bytes has a terminating '\0'.
    assert descriptor.content[packageLengthInBytes - 2] == '/';
    ThrowingCharIterator<UTFDataFormatException> charIterator =
        descriptor.iterator(packageLengthInBytes - 1);
    while (charIterator.hasNext()) {
      try {
        if (charIterator.nextChar() == '/') {
          // Found another package separator, som not exact package.
          return false;
        }
      } catch (UTFDataFormatException e) {
        assert false
            : "Iterating " + descriptor + " from index " + packageLengthInBytes + " caused " + e;
        return false;
      }
    }
    return true;
  }

  private AndroidApiLevel lookupApiLevel(DexReference reference) {
    // TODO(b/326252366): Assigning all extension items the same "fake" API level results in API
    //  outlines becoming mergable across extensions, which should be prevented.
    if (androidApiExtensionClasses.contains(reference.getContextType())) {
      return AndroidApiLevel.EXTENSION;
    }
    for (int i = 0; i < androidApiExtensionPackages.size(); i++) {
      DexString descriptor = reference.getContextType().getDescriptor();
      DexString extensionPackage = androidApiExtensionPackages.get(i);
      if (descriptor.startsWith(extensionPackage)
          && isPackageOfClass(extensionPackage, descriptor)) {
        return AndroidApiLevel.EXTENSION;
      }
    }
    Optional<AndroidApiLevel> result =
        lookupCache.computeIfAbsent(
            reference,
            ref -> {
              // Prefetch the data access
              if (dataAccess == null) {
                getDataAccess(options, diagnosticsHandler);
              }
              if (dataAccess.isNoBacking()) {
                return Optional.empty();
              }
              byte[] uniqueDescriptorForReference;
              try {
                uniqueDescriptorForReference =
                    getUniqueDescriptorForReference(
                        ref,
                        string ->
                            constantPoolCache.computeIfAbsent(
                                string, key -> dataAccess.getConstantPoolIndex(string)));
              } catch (Exception e) {
                uniqueDescriptorForReference = getNonExistingDescriptor();
              }
              if (uniqueDescriptorForReference == getNonExistingDescriptor()) {
                return Optional.empty();
              } else {
                byte apiLevelForReference =
                    dataAccess.getApiLevelForReference(uniqueDescriptorForReference, ref);
                return (apiLevelForReference <= 0)
                    ? Optional.empty()
                    : Optional.of(AndroidApiLevel.getAndroidApiLevel(apiLevelForReference));
              }
            });
    return result.orElse(null);
  }
}
