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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.SemanticTokens;
import org.eclipse.lsp4j.SemanticTokensLegend;
import org.eclipse.lsp4j.SemanticTokensParams;

public class SemanticTokensHandler {

  SemanticTokensHandler() {}

  public static SemanticTokens full(SemanticTokensParams params) {
    return null;
  }

  public static SemanticTokensLegend legend() {
		return new SemanticTokensLegend(
			Arrays.stream(TokenType.values()).map(TokenType::toString).collect(Collectors.toList()),
			new ArrayList<>()
		);
	}
}
