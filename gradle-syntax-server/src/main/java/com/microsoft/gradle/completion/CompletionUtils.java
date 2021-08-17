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
package com.microsoft.gradle.completion;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.InsertTextFormat;

public class CompletionUtils {

  public static CompletionItem getCompletionSnippetItem(String label, String insertText, String detail) {
    CompletionItem item = new CompletionItem();
    item.setLabel(label);
    item.setInsertText(insertText);
    item.setDetail(detail);
    item.setInsertTextFormat(InsertTextFormat.Snippet);
    item.setKind(CompletionItemKind.Snippet);
    return item;
  }

  public static CompletionItem getCompletionItem(String label, String insertText, String detail, CompletionItemKind kind) {
    CompletionItem item = new CompletionItem();
    item.setLabel(label);
    item.setInsertText(insertText);
    item.setDetail(detail);
    item.setKind(kind);
    return item;
  }
}
