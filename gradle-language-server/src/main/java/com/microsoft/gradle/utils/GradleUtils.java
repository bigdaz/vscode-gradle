/*******************************************************************************
 * Copyright (c) 2021 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Microsoft Corporation - initial API and implementation
 *******************************************************************************/

package com.microsoft.gradle.utils;

import org.codehaus.groovy.ast.expr.Expression;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

public final class GradleUtils {

  public static Range getExpressionLSPRange(Expression expression) {
    // LSP Range start from 0, while groovy exp line number start from 1
    return new Range(new Position(expression.getLineNumber() - 1, expression.getColumnNumber() - 1),
        new Position(expression.getLastLineNumber() - 1, expression.getLastColumnNumber() - 1));
  }

  public static Range getDependencyLSPRange(Expression expression) {
    // For dependency, we get the ConstantExpression/GStringExpression, which includes single/double quotes
    // So we exclude them here.
    return new Range(new Position(expression.getLineNumber() - 1, expression.getColumnNumber()),
        new Position(expression.getLastLineNumber() - 1, expression.getLastColumnNumber() - 2));
  }

  public static String getStringBeforePosition(String original, Range range, Position position) {
    Position start = range.getStart();
    // Currently doesn't support multiple line
    if (start.getLine() != position.getLine()) {
      return original;
    }
    return original.substring(0, position.getCharacter() - start.getCharacter());
  }
}
