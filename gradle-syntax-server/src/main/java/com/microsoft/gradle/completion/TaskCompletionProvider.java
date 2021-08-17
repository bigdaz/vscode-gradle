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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;

public class TaskCompletionProvider {

  // org.gradle.api.DefaultTask
  // org.gradle.api.internal.AbstractTask
  private static List<CompletionItem> gradleRepositoryCompletions = new ArrayList<>();

  private static void init() {
    initCommon();
    initClosure();
  }

  private static void initCommon() {
    setCompletionItem("actions", "actions", "Sets the sequence of Action objects which will be executed by this task.",
        CompletionItemKind.Property);
    setCompletionItem("description", "description", "Sets a description for this task.", CompletionItemKind.Property);
    setCompletionItem("dependsOn", "dependsOn", "Sets the dependencies of this task.", CompletionItemKind.Property);
    setCompletionItem("didWork", "didWork", "Sets whether the task actually did any work.",
        CompletionItemKind.Property);
    setCompletionItem("enabled", "enabled", "Set the enabled state of a task.", CompletionItemKind.Property);
    setCompletionItem("finalizedBy", "finalizedBy", "Specifies the set of finalizer tasks for this task.",
        CompletionItemKind.Property);
    setCompletionItem("group", "group", "Sets the task group which this task belongs to.",
        CompletionItemKind.Property);
    setCompletionItem("mustRunAfter", "mustRunAfter", "Specifies the set of tasks that this task must run after.", CompletionItemKind.Property);
    setCompletionItem("onlyIf", "onlyIf", "Execute the task only if the given closure returns true.", CompletionItemKind.Property);
    setCompletionItem("property", "property", "Sets a property of this task.",
        CompletionItemKind.Property);
    setCompletionItem("shouldRunAfter", "shouldRunAfter", "Specifies the set of tasks that this task should run after.", CompletionItemKind.Property);
  }

  private static void initClosure() {
    setCompletionSnippetItem("configure(Closure closure)", "configure {$0}",
        "Applies the statements of the closure against this task object.");
    setCompletionSnippetItem("doFirst(Closure closure)", "doFirst {$0}",
        "Adds the given closure to the beginning of this task's action list.");
    setCompletionSnippetItem("doLast(Closure closure)", "doLast {$0}",
        "Adds the given closure to the end of this task's action list.");
  }

  private static void setCompletionSnippetItem(String label, String insertText, String detail) {
    gradleRepositoryCompletions.add(CompletionUtils.getCompletionSnippetItem(label, insertText, detail));
  }

  private static void setCompletionItem(String label, String insertText, String detail, CompletionItemKind kind) {
    gradleRepositoryCompletions.add(CompletionUtils.getCompletionItem(label, insertText, detail, kind));
  }

  public static List<CompletionItem> get() {
    if (TaskCompletionProvider.gradleRepositoryCompletions.isEmpty()) {
      TaskCompletionProvider.init();
    }
    return TaskCompletionProvider.gradleRepositoryCompletions;
  }
}
