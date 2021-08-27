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

public class DependencyCompletionProvider {

  // org.gradle.api.artifacts.dsl.DependencyHandler
  private static List<CompletionItem> gradleDependencyCompletions = new ArrayList<>();

  private static void init() {
    initCommon();
    initClosure();
  }

  private static void initCommon() {
    setCompletionItem("gradleApi()", "gradleApi()", "Creates a dependency on the API of the current version of Gradle.", CompletionItemKind.Method);
    setCompletionItem("gradleTestKit()", "gradleTestKit()", "Creates a dependency on the Gradle test-kit API.", CompletionItemKind.Method);
    setCompletionItem("localGroovy()", "localGroovy()", "Creates a dependency on the Groovy that is distributed with the current version of Gradle.", CompletionItemKind.Method);
  }

  private static void initClosure() {
    setCompletionSnippetItem("api", "api \"$0\"", "Declares a dependency in api configuration.");
    setCompletionSnippetItem("implementation", "implementation \"$0\"", "Declares a dependency in implementation configuration.");
    setCompletionSnippetItem("testImplementation", "testImplementation \"$0\"", "Declares a dependency in testImplementation configuration.");
    setCompletionSnippetItem("compile", "compile \"$0\"", "Declares a dependency in compile configuration.");
    setCompletionSnippetItem("compileClasspath", "compileClasspath \"$0\"", "Declares a dependency in compileClasspath configuration.");
    setCompletionSnippetItem("compileOnly", "compileOnly \"$0\"", "Declares a dependency in compileOnly configuration.");
    setCompletionSnippetItem("compileProtoPath", "compileProtoPath \"$0\"", "Declares a dependency in compileProtoPath configuration.");
    setCompletionSnippetItem("testCompile", "testCompile \"$0\"", "Declares a dependency in testCompile configuration.");
    setCompletionSnippetItem("testCompileClasspath", "testCompileClasspath \"$0\"", "Declares a dependency in testCompileClasspath configuration.");
    setCompletionSnippetItem("testCompileOnly", "testCompileOnly \"$0\"", "Declares a dependency in compileOnly testConfiguration.");
    setCompletionSnippetItem("testCompileProtoPath", "testCompileProtoPath \"$0\"", "Declares a dependency in testCompileProtoPath configuration.");
    setCompletionSnippetItem("runtime", "runtime \"$0\"", "Declares a dependency in runtime configuration.");
    setCompletionSnippetItem("runtimeClasspath", "runtimeClasspath \"$0\"", "Declares a dependency in runtimeClasspath configuration.");
    setCompletionSnippetItem("runtimeOnly", "runtimeOnly \"$0\"", "Declares a dependency in runtimeOnly configuration.");
    setCompletionSnippetItem("testRuntime", "testRuntime \"$0\"", "Declares a dependency in testRuntime configuration.");
    setCompletionSnippetItem("testRuntimeClasspath", "testRuntimeClasspath \"$0\"", "Declares a dependency in testRuntimeClasspath configuration.");
    setCompletionSnippetItem("testRuntimeOnly", "testRuntimeOnly \"$0\"", "Declares a dependency in testRuntimeOnly configuration.");
  }

  private static void setCompletionSnippetItem(String label, String insertText, String detail) {
    gradleDependencyCompletions.add(CompletionUtils.getCompletionSnippetItem(label, insertText, detail));
  }

  private static void setCompletionItem(String label, String insertText, String detail, CompletionItemKind kind) {
    gradleDependencyCompletions.add(CompletionUtils.getCompletionItem(label, insertText, detail, kind));
  }

  public static List<CompletionItem> get() {
    if (DependencyCompletionProvider.gradleDependencyCompletions.isEmpty()) {
      DependencyCompletionProvider.init();
    }
    return DependencyCompletionProvider.gradleDependencyCompletions;
  }
}
