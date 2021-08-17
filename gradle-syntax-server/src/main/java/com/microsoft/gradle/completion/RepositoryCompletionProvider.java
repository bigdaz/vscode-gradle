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

public class RepositoryCompletionProvider {

  // org.gradle.api.artifacts.dsl.DependencyHandler
  private static List<CompletionItem> gradleRepositoryCompletions = new ArrayList<>();

  private static void init() {
    initCommon();
    initClosure();
  }

  private static void initCommon() {
    setCompletionItem("gradlePluginPortal()", "gradlePluginPortal()", "Adds a repository which looks in Gradle Central Plugin Repository for dependencies.", CompletionItemKind.Method);
    setCompletionItem("jcenter()", "jcenter()", "Adds a repository which looks in Bintray's JCenter repository for dependencies.", CompletionItemKind.Method);
    setCompletionItem("mavenCentral()", "mavenCentral()", "Adds a repository which looks in the Maven central repository for dependencies.", CompletionItemKind.Method);
    setCompletionItem("mavenLocal()", "mavenLocal()", "Adds a repository which looks in the local Maven cache for dependencies.", CompletionItemKind.Method);
    setCompletionItem("google()", "google()", "Adds a repository which looks in Google's Maven repository for dependencies.", CompletionItemKind.Method);
    setCompletionItem("mavenCentral()", "mavenCentral()", "Adds a repository which looks in the Maven central repository for dependencies.", CompletionItemKind.Method);
  }

  private static void initClosure() {
    setCompletionSnippetItem("flatDir(Closure configureClosure)", "flatDir {$0}", "Adds an configures a repository which will look for dependencies in a number of local directories.");
    setCompletionSnippetItem("maven(Closure closure)", "maven {$0}", "Adds and configures a Maven repository.");
    setCompletionSnippetItem("ivy(Closure closure)", "ivy {$0}", "Adds and configures an Ivy repository.");
  }

  private static void setCompletionSnippetItem(String label, String insertText, String detail) {
    gradleRepositoryCompletions.add(CompletionUtils.getCompletionSnippetItem(label, insertText, detail));
  }

  private static void setCompletionItem(String label, String insertText, String detail, CompletionItemKind kind) {
    gradleRepositoryCompletions.add(CompletionUtils.getCompletionItem(label, insertText, detail, kind));
  }

  public static List<CompletionItem> get() {
    if (RepositoryCompletionProvider.gradleRepositoryCompletions.isEmpty()) {
      RepositoryCompletionProvider.init();
    }
    return RepositoryCompletionProvider.gradleRepositoryCompletions;
  }
}
