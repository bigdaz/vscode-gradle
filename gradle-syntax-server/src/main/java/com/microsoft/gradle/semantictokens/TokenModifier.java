/*******************************************************************************
 * Copyright (c) 2021 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
*******************************************************************************/
package com.microsoft.gradle.semantictokens;

import org.eclipse.lsp4j.SemanticTokenModifiers;

public enum TokenModifier {

  DECLARATION(SemanticTokenModifiers.Declaration), DEFAULT_LIBRARY(SemanticTokenModifiers.DefaultLibrary);

  private String genericName;
  public final int bitmask = 1 << ordinal();

  TokenModifier(String genericName) {
    this.genericName = genericName;
  }

  @Override
  public String toString() {
    return genericName;
  }

  public static boolean isDefaultLibrary(String method) {
    switch (method) {
      case "dependencies":
      case "repositories":
      case "apply":
      case "configurations":
      case "task":
        return true;
      default:
        return false;
    }
  }
}
