// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.keepanno.keeprules;

import com.android.tools.r8.keepanno.ast.KeepOptions.KeepOption;

public abstract class KeepRuleExtractorOptions {

  private static final KeepRuleExtractorOptions PG_OPTIONS =
      new KeepRuleExtractorOptions() {
        @Override
        public boolean hasCheckDiscardSupport() {
          return false;
        }

        @Override
        public boolean hasAllowAccessModificationOptionSupport() {
          return false;
        }

        @Override
        public boolean hasAllowAnnotationRemovalOptionSupport() {
          return false;
        }

        @Override
        public boolean hasAllowSignatureRemovalOptionSupport() {
          return false;
        }

        @Override
        public boolean hasFieldTypeBackReference() {
          return false;
        }

        @Override
        public boolean hasMethodReturnTypeBackReference() {
          return false;
        }

        @Override
        public boolean hasMethodParameterTypeBackReference() {
          return false;
        }

        @Override
        public boolean hasMethodParameterListBackReference() {
          return false;
        }
      };

  private static final KeepRuleExtractorOptions R8_OPTIONS =
      new KeepRuleExtractorOptions() {
        @Override
        public boolean hasCheckDiscardSupport() {
          return true;
        }

        @Override
        public boolean hasAllowAccessModificationOptionSupport() {
          return true;
        }

        @Override
        public boolean hasAllowAnnotationRemovalOptionSupport() {
          // Allow annotation removal is currently a testing only option.
          return false;
        }

        @Override
        public boolean hasAllowSignatureRemovalOptionSupport() {
          // There is no allow option for signature removal.
          return false;
        }

        @Override
        public boolean hasFieldTypeBackReference() {
          return true;
        }

        @Override
        public boolean hasMethodReturnTypeBackReference() {
          return true;
        }

        @Override
        public boolean hasMethodParameterTypeBackReference() {
          return true;
        }

        @Override
        public boolean hasMethodParameterListBackReference() {
          // TODO(b/265892343): R8 does not support backrefs for `(...)`.
          //  When resolving this the options need to be split in legacy R8 and current R8.
          return false;
        }
      };

  public static KeepRuleExtractorOptions getPgOptions() {
    return PG_OPTIONS;
  }

  public static KeepRuleExtractorOptions getR8Options() {
    return R8_OPTIONS;
  }

  private KeepRuleExtractorOptions() {}

  public abstract boolean hasCheckDiscardSupport();

  public abstract boolean hasAllowAccessModificationOptionSupport();

  public abstract boolean hasAllowAnnotationRemovalOptionSupport();

  public abstract boolean hasAllowSignatureRemovalOptionSupport();

  public abstract boolean hasFieldTypeBackReference();

  public abstract boolean hasMethodReturnTypeBackReference();

  public abstract boolean hasMethodParameterTypeBackReference();

  public abstract boolean hasMethodParameterListBackReference();

  public boolean isKeepOptionSupported(KeepOption keepOption) {
    switch (keepOption) {
      case ACCESS_MODIFICATION:
        return hasAllowAccessModificationOptionSupport();
      case ANNOTATION_REMOVAL:
        return hasAllowAnnotationRemovalOptionSupport();
      case SIGNATURE_REMOVAL:
        return hasAllowSignatureRemovalOptionSupport();
      default:
        return true;
    }
  }
}
