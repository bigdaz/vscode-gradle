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

package com.microsoft.gradle;

import java.net.URI;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import com.microsoft.gradle.compile.GradleASTCodeVisitor;
import com.microsoft.gradle.completion.DependencyCompletionHandler;
import com.microsoft.gradle.manager.GradleCompilationUnitManager;
import com.microsoft.gradle.manager.GradleFilesManager;

import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.ErrorCollector;
import org.codehaus.groovy.control.Phases;
import org.codehaus.groovy.control.messages.Message;
import org.codehaus.groovy.control.messages.SyntaxErrorMessage;
import org.codehaus.groovy.syntax.SyntaxException;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SemanticTokens;
import org.eclipse.lsp4j.SemanticTokensParams;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;
import org.eclipse.lsp4j.util.Ranges;

public class GradleServices implements TextDocumentService, WorkspaceService, LanguageClientAware {

  private LanguageClient languageClient;
  private GradleCompilationUnitManager gradleCompilationUnitManager;
  private GradleASTCodeVisitor astVisitor;
  private GradleFilesManager gradleFilesManager = new GradleFilesManager();
  private DependencyCompletionHandler dependencyCompletionHandler = new DependencyCompletionHandler();

  public GradleServices(GradleCompilationUnitManager gradleCompilationUnitManager) {
    this.gradleCompilationUnitManager = gradleCompilationUnitManager;
    this.astVisitor = new GradleASTCodeVisitor();
  }

  @Override
  public void connect(LanguageClient client) {
    languageClient = client;
  }

  @Override
  public void didOpen(DidOpenTextDocumentParams params) {
    gradleFilesManager.didOpen(params);
    URI uri = URI.create(params.getTextDocument().getUri());
    compileAndVisitAST(uri);
  }

  @Override
  public void didChange(DidChangeTextDocumentParams params) {
    gradleFilesManager.didChange(params);
    URI uri = URI.create(params.getTextDocument().getUri());
    compileAndVisitAST(uri);
  }

  @Override
  public void didClose(DidCloseTextDocumentParams params) {
    gradleFilesManager.didClose(params);
  }

  @Override
  public void didSave(DidSaveTextDocumentParams params) {
  }

  @Override
  public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {
  }

  @Override
  public void didChangeConfiguration(DidChangeConfigurationParams params) {
  }

  private void compileAndVisitAST(URI uri) {
    CompilationUnit compilationUnit = this.gradleCompilationUnitManager.getCompilationUnit(uri,
        this.gradleFilesManager);
    if (compile(uri, compilationUnit)) {
      this.astVisitor.visitCompilationUnit(compilationUnit);
    }
  }

  private boolean compile(URI uri, CompilationUnit compilationUnit) {
    boolean hasError = false;
    if (compilationUnit == null) {
      return false;
    }
    try {
      compilationUnit.compile(Phases.CANONICALIZATION);
    } catch (Exception e) {
      hasError = true;
    }
    Set<PublishDiagnosticsParams> diagnostics = handleErrorCollector(compilationUnit.getErrorCollector());
    if (diagnostics.isEmpty()) {
      PublishDiagnosticsParams emptyParams = new PublishDiagnosticsParams(uri.toString(), new ArrayList<>());
      languageClient.publishDiagnostics(emptyParams);
    } else {
      diagnostics.stream().forEach(languageClient::publishDiagnostics);
    }
    return !hasError;
  }

  private Set<PublishDiagnosticsParams> handleErrorCollector(ErrorCollector collector) {
    Map<URI, List<Diagnostic>> diagnosticsByFile = new HashMap<>();
    List<? extends Message> errors = collector.getErrors();
    if (errors != null) {
      errors.stream().filter((Object message) -> message instanceof SyntaxErrorMessage).forEach((Object message) -> {
        SyntaxErrorMessage syntaxErrorMessage = (SyntaxErrorMessage) message;
        SyntaxException cause = syntaxErrorMessage.getCause();
        Range range = new Range(new Position(cause.getStartLine() - 1, cause.getStartColumn() - 1),
            new Position(cause.getEndLine() - 1, cause.getEndColumn() - 1));
        Diagnostic diagnostic = new Diagnostic();
        diagnostic.setRange(range);
        diagnostic.setSeverity(DiagnosticSeverity.Error);
        diagnostic.setMessage(cause.getMessage());
        URI uri = Paths.get(cause.getSourceLocator()).toUri();
        diagnosticsByFile.computeIfAbsent(uri, (key) -> new ArrayList<>()).add(diagnostic);
      });
    }

    return diagnosticsByFile.entrySet().stream()
        .map(entry -> new PublishDiagnosticsParams(entry.getKey().toString(), entry.getValue()))
        .collect(Collectors.toSet());
  }

  @Override
  public CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> documentSymbol(
      DocumentSymbolParams params) {
    URI uri = URI.create(params.getTextDocument().getUri());
    List<Either<SymbolInformation, DocumentSymbol>> result = new ArrayList<>();
    for (Map.Entry<URI, List<DocumentSymbol>> entry : this.astVisitor.getDocumentSymbols().entrySet()) {
      if (entry.getKey().getPath().equals(uri.getPath())) {
        for (DocumentSymbol symbol : entry.getValue()) {
          result.add(Either.forRight(symbol));
        }
        break;
      }
    }
    return CompletableFuture.completedFuture(result);
  }

  @Override
  public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(CompletionParams params) {
    URI uri = URI.create(params.getTextDocument().getUri());
    for (Map.Entry<URI, List<DocumentSymbol>> entry : this.astVisitor.getDocumentDependencies().entrySet()) {
      if (entry.getKey().getPath().equals(uri.getPath())) {
        for (DocumentSymbol symbol : entry.getValue()) {
          if (Ranges.containsPosition(symbol.getRange(), params.getPosition())) {
            Either<List<CompletionItem>, CompletionList> completionItems = this.dependencyCompletionHandler
                .completion(symbol, params.getPosition());
            return CompletableFuture.completedFuture(completionItems);
          }
        }
        break;
      }
    }
    // File root
    for (Map.Entry<URI, List<DocumentSymbol>> entry : this.astVisitor.getDocumentSymbols().entrySet()) {
      if (entry.getKey().getPath().equals(uri.getPath())) {
        int i = 0;
        for (; i < entry.getValue().size(); i++) {
          DocumentSymbol symbol = entry.getValue().get(i);
          if (Ranges.containsPosition(symbol.getRange(), params.getPosition())) {
            switch (symbol.getName()) {
              // IDEA GradleProjectContributor
              case "allprojects":
              case "subprojects":
              case "project":
              case "configure":
                return CompletableFuture.completedFuture(this.dependencyCompletionHandler.completionForProject());
              case "dependencies":
                return CompletableFuture.completedFuture(this.dependencyCompletionHandler.completionForDependencies());
              case "repositories":
                return CompletableFuture.completedFuture(this.dependencyCompletionHandler.completionForRepositories());
              case "task":
                return CompletableFuture.completedFuture(this.dependencyCompletionHandler.completionForTasks());
            }
            break;
          }
        }
        if (i == entry.getValue().size()) {
          return CompletableFuture.completedFuture(this.dependencyCompletionHandler.completionForProject());
        }
      }
    }
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public CompletableFuture<SemanticTokens> semanticTokensFull(SemanticTokensParams params) {
    return CompletableFuture
        .completedFuture(astVisitor.getSemanticTokens(URI.create(params.getTextDocument().getUri())));
  }
}
