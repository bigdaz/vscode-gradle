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

public class ProjectCompletionProvider {

  // org.gradle.api.Project
  private static List<CompletionItem> gradleProjectCompletions = new ArrayList<>();

  private static void init() {
    initCommon();
    initClosure();
  }

  private static void initCommon() {
    setCompletionItem("buildDir", "buildDir", "Sets the build directory of this project.", CompletionItemKind.Property);
    setCompletionItem("description", "description", "Sets a description for this project.", CompletionItemKind.Property);
    setCompletionItem("group", "group", "Sets the group of this project.", CompletionItemKind.Property);
    setCompletionItem("version", "version", "Sets the version of this project.", CompletionItemKind.Property);
    setCompletionItem("status", "status", "Sets the status of this project.", CompletionItemKind.Property);
    setCompletionItem("defaultTasks", "defaultTasks", "Sets the names of the default tasks of this project.", CompletionItemKind.Property);
  }

  private static void initClosure() {
    setCompletionSnippetItem("buildDir(Closure closure)", "buildDir {\n  $0\n}", "Sets the build directory of this project.");
    setCompletionSnippetItem("group(Closure closure)", "group {\n  $0\n}", "Sets the group of this project.");
    setCompletionSnippetItem("version(Closure closure)", "version {\n  $0\n}", "Sets the version of this project.");
    setCompletionSnippetItem("status(Closure closure)", "status {\n  $0\n}", "Sets the status of this project.");
    setCompletionSnippetItem("ant(Closure configureClosure)", "ant {\n  $0\n}", "Executes the given closure against the AntBuilder for this project.");
    setCompletionSnippetItem("configurations(Closure configureClosure)", "configurations {\n  $0\n}", "Configures the dependency configurations for this project.");
    setCompletionSnippetItem("artifacts(Closure configureClosure)", "artifacts {\n  $0\n}", "Configures the published artifacts for this project.");
    setCompletionSnippetItem("subprojects(Closure configureClosure)", "subprojects {\n  $0\n}", "Configures the sub-projects of this project.");
    setCompletionSnippetItem("allprojects(Closure configureClosure)", "allprojects {\n  $0\n}", "Configures this project and each of its sub-projects.");
    setCompletionSnippetItem("beforeEvaluate(Closure closure)", "beforeEvaluate {\n  $0\n}", "Adds a closure to be called immediately before this project is evaluated.");
    setCompletionSnippetItem("afterEvaluate(Closure closure)", "afterEvaluate {\n  $0\n}", "Adds a closure to be called immediately after this project has been evaluated.");
    setCompletionSnippetItem("repositories(Closure configureClosure)", "repositories {\n  $0\n}", "Configures the repositories for this project.");
    setCompletionSnippetItem("dependencies(Closure configureClosure)", "dependencies {\n  $0\n}", "Configures the dependencies for this project.");
    setCompletionSnippetItem("buildscript(Closure configureClosure)", "buildscript {\n  $0\n}", "Configures the build script classpath for this project.");
    setCompletionSnippetItem("copy(Closure closure)", "copy {\n  $0\n}", "Copies the specified files.");
    setCompletionSnippetItem("copySpec(Closure closure)", "copySpec {\n  $0\n}", "Creates a CopySpec which can later be used to copy files or create an archive.");
    setCompletionSnippetItem("task(Closure closure)", "task {\n  $0\n}", "Creates a Task with the given name and adds it to this project.");
  }

  private static void setCompletionSnippetItem(String label, String insertText, String detail) {
    gradleProjectCompletions.add(CompletionUtils.getCompletionSnippetItem(label, insertText, detail));
  }

  private static void setCompletionItem(String label, String insertText, String detail, CompletionItemKind kind) {
    gradleProjectCompletions.add(CompletionUtils.getCompletionItem(label, insertText, detail, kind));
  }

  public static List<CompletionItem> get() {
    if (ProjectCompletionProvider.gradleProjectCompletions.isEmpty()) {
      ProjectCompletionProvider.init();
    }
    return ProjectCompletionProvider.gradleProjectCompletions;
  }
}
