// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
import state from "./state.js";

export default {
  callbacks: {
    title: context => state.getCommitFromContext(context).title,
    footer: context => state.getCommitDescriptionFromContext(context)
  }
};
