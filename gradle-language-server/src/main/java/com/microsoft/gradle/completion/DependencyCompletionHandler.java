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

package com.microsoft.gradle.completion;

import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.microsoft.gradle.utils.GradleUtils;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

public class DependencyCompletionHandler {

  private static String URL_BASIC_SEARCH = "https://search.maven.org/solrsearch/select?q=";
  private DocumentSymbol symbol;
  private Position position;

  private enum DependencyCompletionKind {
    ID,
    VERSION
  }

  public Either<List<CompletionItem>, CompletionList> completion(DocumentSymbol symbol, Position position) {
    this.symbol = symbol;
    this.position = position;
    String validText = GradleUtils.getStringBeforePosition(symbol.getName(), symbol.getRange(), position);
    List<CompletionItem> completionItems = getCompletionsForDependencies(validText);
    CompletionList completionList = new CompletionList(false, completionItems);
    return Either.forRight(completionList);
  }

  public Either<List<CompletionItem>, CompletionList> completionForProject() {
    return Either.forLeft(ProjectCompletionProvider.get());
  }

  public Either<List<CompletionItem>, CompletionList> completionForDependencies() {
    return Either.forLeft(DependencyCompletionProvider.get());
  }

  public Either<List<CompletionItem>, CompletionList> completionForRepositories() {
    return Either.forLeft(RepositoryCompletionProvider.get());
  }

  public Either<List<CompletionItem>, CompletionList> completionForTasks() {
    return Either.forLeft(TaskCompletionProvider.get());
  }

  private List<CompletionItem> getCompletionsForDependencies(String validText) {
    String[] existing = validText.split(":", -1);
    switch (existing.length) {
      case 0:
        // Empty string, try to search in mavenlocal?
        return new ArrayList<>();
      case 1:
        // GroupId incomplete
        return getCompletionsForInCompleteGroup(existing[0]);
      case 2:
        // GroupId complete, artifactId incomplete
        return getCompletionsForInCompleteArtifact(existing[0]);
      case 3:
        // GroupId complete, artifactId complete, version incomplete
        return getCompletionsForInCompleteVersion(existing[0], existing[1]);
      default:
        return new ArrayList<>();
    }
  }

  private List<CompletionItem> getCompletionsForInCompleteGroup(String group) {
    if (group.length() < 3) {
      return new ArrayList<>();
    }
    String query = group + "&rows=50&wt=json";
    String url = URL_BASIC_SEARCH + query;
    return getResultFromRestAPI(url, DependencyCompletionKind.ID);
  }

  private List<CompletionItem> getCompletionsForInCompleteArtifact(String group) {
    String query = "g:%22" + group + "%22&rows=50&wt=json";
    String url = URL_BASIC_SEARCH + query;
    return getResultFromRestAPI(url, DependencyCompletionKind.ID);
  }

  private List<CompletionItem> getCompletionsForInCompleteVersion(String group, String artifact) {
    String query = "g:%22" + group + "%22+AND+a:%22" + artifact + "%22&core=gav&rows=50&wt=json";
    String url = URL_BASIC_SEARCH + query;
    return getResultFromRestAPI(url, DependencyCompletionKind.VERSION);
  }

  private List<CompletionItem> getResultFromRestAPI(String url, DependencyCompletionKind kind) {
    try (InputStreamReader reader = new InputStreamReader(new URL(url).openStream())) {
      JsonObject jsonResult = new Gson().fromJson(reader, JsonObject.class);
      JsonObject response = jsonResult.getAsJsonObject("response");
      JsonArray docs = response.getAsJsonArray("docs");
      List<CompletionItem> completions = new ArrayList<>();
      for (int i = 0; i < docs.size(); i++) {
        JsonElement element = docs.get(i);
        if (element instanceof JsonObject) {
          CompletionItem completionItem = new CompletionItem();
          if (kind == DependencyCompletionKind.ID) {
            JsonElement labelContent =((JsonObject) element).get("id");
            String label = labelContent.getAsJsonPrimitive().getAsString();
            TextEdit textEdit = new TextEdit(new Range(this.symbol.getRange().getStart(), this.position), label + ":");
            completionItem.setLabel(label);
            completionItem.setSortText(String.valueOf(i));
            completionItem.setTextEdit(Either.forLeft(textEdit));
            completionItem.setKind(CompletionItemKind.Module);
            completionItem.setDetail("mavenCentral");
          } else {
            JsonElement version = ((JsonObject) element).get("v");
            JsonElement labelContent = ((JsonObject) element).get("id");
            String versionString = version.getAsJsonPrimitive().getAsString();
            String label = labelContent.getAsJsonPrimitive().getAsString();
            TextEdit textEdit = new TextEdit(new Range(this.symbol.getRange().getStart(), this.position), label);
            completionItem.setLabel(versionString);
            completionItem.setFilterText(label);
            completionItem.setSortText(String.valueOf(i));
            completionItem.setTextEdit(Either.forLeft(textEdit));
            completionItem.setKind(CompletionItemKind.Text);
            completionItem.setDetail("version");
          }
          completions.add(completionItem);
        }
      }
      return completions;
    } catch (Exception e) {
      // TODO
    }
    return new ArrayList<>();
  }
}
