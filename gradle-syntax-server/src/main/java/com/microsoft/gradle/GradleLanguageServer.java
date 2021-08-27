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

import java.net.Socket;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.microsoft.gradle.manager.GradleCompilationUnitManager;
import com.microsoft.gradle.semantictokens.SemanticTokensHandler;

import org.eclipse.lsp4j.CompletionOptions;
import org.eclipse.lsp4j.DocumentFilter;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.SaveOptions;
import org.eclipse.lsp4j.SemanticTokensServerFull;
import org.eclipse.lsp4j.SemanticTokensWithRegistrationOptions;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.TextDocumentSyncOptions;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;

public class GradleLanguageServer implements LanguageServer, LanguageClientAware {

  private GradleServices gradleServices;

  public static void main(String[] args) {
    GradleLanguageServer server = new GradleLanguageServer(new GradleCompilationUnitManager());
    try {
      /*Launcher<LanguageClient> launcher = Launcher.createLauncher(server, LanguageClient.class, socket.getInputStream(),
          socket.getOutputStream());*/
      Launcher<LanguageClient> launcher = Launcher.createLauncher(server, LanguageClient.class, System.in,
          System.out);
      server.connect(launcher.getRemoteProxy());
      launcher.startListening();
    } catch (Exception e) {
      server.exit();
    }

  }

  public GradleLanguageServer(GradleCompilationUnitManager gradleCompilationUnitManager) {
    this.gradleServices = new GradleServices(gradleCompilationUnitManager);
  }

  @Override
  public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
    ServerCapabilities serverCapabilities = new ServerCapabilities();
    SemanticTokensWithRegistrationOptions semanticOptions = new SemanticTokensWithRegistrationOptions();
    semanticOptions.setFull(new SemanticTokensServerFull(false /** delta */
    ));
    semanticOptions.setRange(false);
    semanticOptions.setDocumentSelector(List.of(new DocumentFilter("gradle", "file", null)));
    semanticOptions.setLegend(SemanticTokensHandler.legend());
    serverCapabilities.setSemanticTokensProvider(semanticOptions);
    serverCapabilities.setDocumentSymbolProvider(true);
    TextDocumentSyncOptions textDocumentSyncOptions = new TextDocumentSyncOptions();
    textDocumentSyncOptions.setOpenClose(Boolean.TRUE);
    textDocumentSyncOptions.setSave(new SaveOptions(Boolean.TRUE));
    textDocumentSyncOptions.setChange(TextDocumentSyncKind.Incremental);
    serverCapabilities.setTextDocumentSync(textDocumentSyncOptions);
    CompletionOptions completionOptions = new CompletionOptions(false, Arrays.asList(".", "-", "<", ":"));
    serverCapabilities.setCompletionProvider(completionOptions);
    InitializeResult initializeResult = new InitializeResult(serverCapabilities);
    return CompletableFuture.completedFuture(initializeResult);
  }

  @Override
  public CompletableFuture<Object> shutdown() {
    return CompletableFuture.completedFuture(new Object());
  }

  @Override
  public void exit() {
    System.exit(0);
  }

  @Override
  public TextDocumentService getTextDocumentService() {
    return this.gradleServices;
  }

  @Override
  public WorkspaceService getWorkspaceService() {
    return this.gradleServices;
  }

  @Override
  public void connect(LanguageClient client) {
    this.gradleServices.connect(client);
  }
}