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

package com.microsoft.gradle.compile;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.microsoft.gradle.semantictokens.TokenModifier;
import com.microsoft.gradle.semantictokens.TokenType;
import com.microsoft.gradle.utils.GradleUtils;

import org.apache.groovy.parser.antlr4.util.StringUtils;
import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.ClassCodeVisitorSupport;
import org.codehaus.groovy.ast.ModuleNode;
import org.codehaus.groovy.ast.expr.ArgumentListExpression;
import org.codehaus.groovy.ast.expr.BinaryExpression;
import org.codehaus.groovy.ast.expr.ClosureExpression;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.GStringExpression;
import org.codehaus.groovy.ast.expr.MapEntryExpression;
import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.codehaus.groovy.ast.expr.PropertyExpression;
import org.codehaus.groovy.ast.expr.VariableExpression;
import org.codehaus.groovy.ast.stmt.BlockStatement;
import org.codehaus.groovy.ast.stmt.ExpressionStatement;
import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.SourceUnit;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.SemanticTokens;
import org.eclipse.lsp4j.SymbolKind;

public class GradleASTCodeVisitor extends ClassCodeVisitorSupport {

  private SourceUnit sourceUnit;

  @Override
  protected SourceUnit getSourceUnit() {
    return sourceUnit;
  }

  private List<SemanticToken> tokens = new ArrayList<>();
  private Map<URI, List<DocumentSymbol>> documentSymbols = new HashMap<>();
  private Map<URI, List<DocumentSymbol>> documentDependencies = new HashMap<>();
  private Map<URI, SemanticTokens> semanticTokens = new HashMap<>();
  private Map<URI, Set<MethodCallExpression>> methodCalls = new HashMap<>();

  private class SemanticToken {
    private final TokenType tokenType;
    private final int tokenModifiers;
    private final int line;
    private final int column;
    private final int length;

    public SemanticToken(int line, int column, int length, TokenType tokenType, int tokenModifiers) {
      this.line = line;
      this.column = column;
      this.length = length;
      this.tokenType = tokenType;
      this.tokenModifiers = tokenModifiers;
    }

    public TokenType getTokenType() {
      return tokenType;
    }

    public int getTokenModifiers() {
      return tokenModifiers;
    }

    public int getLine() {
      return line;
    }

    public int getColumn() {
      return column;
    }

    public int getLength() {
      return length;
    }
  }

  public SemanticTokens getSemanticTokens(URI uri) {
    return this.semanticTokens.get(uri);
  }

  public Map<URI, Set<MethodCallExpression>> getCalls() {
    return this.methodCalls;
  }

  private List<Integer> encodedTokens() {
    tokens.sort(new Comparator<SemanticToken>() {
      @Override public int compare(final SemanticToken a, final SemanticToken b) {
        int lineResult = Integer.valueOf(a.getLine()).compareTo(Integer.valueOf(b.getLine()));
        if (lineResult == 0) {
          return Integer.valueOf(a.getColumn()).compareTo(Integer.valueOf(b.getColumn()));
        }
        return lineResult;
      }});
    int numTokens = tokens.size();
    List<Integer> data = new ArrayList<>(numTokens * 5);
    int currentLine = 0;
    int currentColumn = 0;
    for (int i = 0; i < numTokens; i++) {
      SemanticToken token = tokens.get(i);
      int line = token.getLine() - 1;
      int column = token.getColumn() - 1;
      if (line < 0 || column < 0) {
        continue;
      }
      int deltaLine = line - currentLine;
      if (deltaLine != 0) {
        currentLine = line;
        currentColumn = 0;
      }
      int deltaColumn = column - currentColumn;
      currentColumn = column;
      // Disallow duplicate/conflict token (if exists)
      if (deltaLine != 0 || deltaColumn != 0 || i == 0) {
        int tokenTypeIndex = token.getTokenType().ordinal();
        int tokenModifiers = token.getTokenModifiers();
        data.add(deltaLine);
        data.add(deltaColumn);
        data.add(token.getLength());
        data.add(tokenTypeIndex);
        data.add(tokenModifiers);
      }
    }
    this.tokens.clear();
    return data;
  }

  private void addToken(int line, int column, int length, TokenType tokenType, int modifiers) {
    if (length > 0) {
      tokens.add(new SemanticToken(line, column, length, tokenType, modifiers));
    }
  }

  private void addToken(ASTNode node, TokenType tokenType, int modifiers) {
    addToken(node.getLineNumber(), node.getColumnNumber(), node.getLength(), tokenType, modifiers);
  }

  private void addToken(ASTNode node, TokenType tokenType) {
    addToken(node, tokenType, 0);
  }

  public void visitCompilationUnit(CompilationUnit cu) {
    cu.iterator().forEachRemaining(unit -> visitSourceUnit(unit));
  }

  public void visitSourceUnit(SourceUnit unit) {
    this.sourceUnit = unit;
    this.documentSymbols.remove(this.sourceUnit.getSource().getURI());
    this.semanticTokens.remove(this.sourceUnit.getSource().getURI());
    this.methodCalls.remove(this.sourceUnit.getSource().getURI());
    ModuleNode moduleNode = unit.getAST();
    if (moduleNode != null) {
      visitModule(moduleNode);
    }
    this.semanticTokens.put(this.sourceUnit.getSource().getURI(), new SemanticTokens(encodedTokens()));
    this.sourceUnit = null;
  }

  public void visitModule(ModuleNode node) {
    visitModuleSemantic(node);
    BlockStatement blockStatement = node.getStatementBlock();
    List<Statement> statements = blockStatement.getStatements();
    for (Statement statement : statements) {
      if (statement instanceof ExpressionStatement) {
        visitExpressionStatementSymbol((ExpressionStatement) statement);
      }
    }
  }

  public void visitExpressionStatementSymbol(ExpressionStatement statement) {
    Expression expression = statement.getExpression();
    DocumentSymbol symbol = null;
    if (expression instanceof MethodCallExpression) {
      symbol = getSymbolInMethodCallExpression((MethodCallExpression) expression);
    } else if (expression instanceof BinaryExpression) {
      symbol = getSymbolInBinaryExpression((BinaryExpression) expression);
    } else {
      return;
    }

    if (symbol == null || symbol.getName() == null) {
      return;
    }

    URI uri = this.sourceUnit.getSource().getURI();
    if (this.documentSymbols.containsKey(uri)) {
      this.documentSymbols.get(uri).add(symbol);
    } else {
      List<DocumentSymbol> symbols = new ArrayList<>();
      symbols.add(symbol);
      this.documentSymbols.put(uri, symbols);
    }
  }

  private DocumentSymbol getSymbolInBinaryExpression(BinaryExpression expression) {
    Expression left = expression.getLeftExpression();
    Expression right = expression.getRightExpression();
    if (left instanceof VariableExpression) {
      VariableExpression variableLeftExpression = ((VariableExpression) left);
      DocumentSymbol symbol = new DocumentSymbol();
      symbol.setName(variableLeftExpression.getName());
      if (right instanceof ConstantExpression) {
        symbol.setDetail(((ConstantExpression) right).getText());
      }
      symbol.setKind(SymbolKind.Property);
      symbol.setRange(GradleUtils.getExpressionLSPRange(variableLeftExpression));
      symbol.setSelectionRange(GradleUtils.getExpressionLSPRange(variableLeftExpression));
      return symbol;
    } else if (left instanceof PropertyExpression) {
      PropertyExpression propertyLeftExpression = ((PropertyExpression) left);
      DocumentSymbol symbol = new DocumentSymbol();
      symbol.setName(propertyLeftExpression.getText());
      if (right instanceof ConstantExpression) {
        symbol.setDetail(((ConstantExpression) right).getText());
      }
      symbol.setKind(SymbolKind.Property);
      symbol.setRange(GradleUtils.getExpressionLSPRange(propertyLeftExpression));
      symbol.setSelectionRange(GradleUtils.getExpressionLSPRange(propertyLeftExpression));
      return symbol;
    }
    return null;
  }

  private DocumentSymbol getSymbolInMethodCallExpression(MethodCallExpression expression) {
    DocumentSymbol symbol = new DocumentSymbol();
    symbol.setKind(SymbolKind.Function);
    String name = getNameInMethodCallExpression(expression);
    if (name == null) {
      return null;
    }
    if (name.equals("dependencies")) {
      List<DocumentSymbol> dependencies = getDependencies(expression);
      URI uri = this.sourceUnit.getSource().getURI();
      this.documentDependencies.put(uri, dependencies);
      symbol.setChildren(dependencies);
    }
    symbol.setName(name);
    symbol.setSelectionRange(GradleUtils.getExpressionLSPRange(expression.getMethod()));
    symbol.setRange(GradleUtils.getExpressionLSPRange(expression));
    return symbol;
  }

  private String getNameInMethodCallExpression(MethodCallExpression expression) {
    Expression objectExpression = expression.getObjectExpression();
    if (objectExpression instanceof VariableExpression
        && ((VariableExpression) objectExpression).getName().equals("this")) {
      String name = expression.getMethodAsString();
      Expression arguments = expression.getArguments();
      if (arguments instanceof ArgumentListExpression) {
        List<Expression> expressions = ((ArgumentListExpression) arguments).getExpressions();
        for (Expression exp : expressions) {
          if (exp instanceof MethodCallExpression) {
            name = name + " " + getNameInMethodCallExpression((MethodCallExpression) exp);
          }
        }
      }
      return name;
    }
    String text = expression.getText();
    int offset = text.indexOf("(");
    if (offset == -1) {
      offset = text.indexOf("{");
    }
    return (offset == -1) ? text : text.substring(0, offset);
  }

  private List<DocumentSymbol> getDependencies(MethodCallExpression expression) {
    Expression argument = expression.getArguments();
    if (argument instanceof ArgumentListExpression) {
      return getDependenciesInArgumentListExpression((ArgumentListExpression) argument);
    }
    return List.of();
  }

  private List<DocumentSymbol> getDependenciesInArgumentListExpression(ArgumentListExpression argumentListExpression) {
    List<Expression> expressions = argumentListExpression.getExpressions();
    List<DocumentSymbol> symbols = new ArrayList<>();
    for (Expression expression : expressions) {
      if (expression instanceof ClosureExpression) {
        symbols.addAll(getDependenciesInClosureExpression((ClosureExpression) expression));
      }
    }
    return symbols;
  }

  private List<DocumentSymbol> getDependenciesInClosureExpression(ClosureExpression expression) {
    Statement code = expression.getCode();
    if (code instanceof BlockStatement) {
      return getDependenciesInBlockStatement((BlockStatement) code);
    }
    return List.of();
  }

  private List<DocumentSymbol> getDependenciesInBlockStatement(BlockStatement blockStatement) {
    List<Statement> statements = blockStatement.getStatements();
    List<DocumentSymbol> symbols = new ArrayList<>();
    for (Statement statement : statements) {
      if (statement instanceof ExpressionStatement) {
        symbols.addAll(getDependenciesInExpressionStatement((ExpressionStatement) statement));
      }
    }
    return symbols;
  }

  private List<DocumentSymbol> getDependenciesInExpressionStatement(ExpressionStatement expressionStatement) {
    Expression expression = expressionStatement.getExpression();
    List<DocumentSymbol> symbols = new ArrayList<>();
    if (expression instanceof MethodCallExpression) {
      symbols.addAll(getDependenciesInMethodCallExpression((MethodCallExpression) expression, null));
    }
    return symbols;
  }

  private List<DocumentSymbol> getDependenciesInMethodCallExpression(MethodCallExpression expression,
      String configuration) {
    Expression arguments = expression.getArguments();
    if (configuration == null) {
      configuration = expression.getMethodAsString();
    }
    List<DocumentSymbol> symbols = new ArrayList<>();
    if (arguments instanceof ArgumentListExpression) {
      List<Expression> args = ((ArgumentListExpression) arguments).getExpressions();
      for (Expression arg : args) {
        if (arg instanceof ConstantExpression || arg instanceof GStringExpression) {
          DocumentSymbol symbol = generateDependencySymbolFromExpression(arg, configuration);
          if (symbol != null) {
            symbols.add(symbol);
          }
        } else if (arg instanceof MethodCallExpression) {
          symbols.addAll(getDependenciesInMethodCallExpression((MethodCallExpression) arg, configuration));
        }
      }
    }
    return symbols;
  }

  private DocumentSymbol generateDependencySymbolFromExpression(Expression expression, String configuration) {
    DocumentSymbol symbol = new DocumentSymbol();
    String name = expression.getText();
    if (StringUtils.isEmpty(name)) {
      return null;
    }
    symbol.setName(name);
    if (configuration != null) {
      symbol.setDetail(configuration);
    }
    symbol.setKind(SymbolKind.Constant);
    symbol.setRange(GradleUtils.getDependencyLSPRange(expression));
    symbol.setSelectionRange(GradleUtils.getDependencyLSPRange(expression));
    return symbol;
  }

  public Map<URI, List<DocumentSymbol>> getDocumentSymbols() {
    return this.documentSymbols;
  }

  public Map<URI, List<DocumentSymbol>> getDocumentDependencies() {
    return this.documentDependencies;
  }

  // Semantic part
  public void visitModuleSemantic(ModuleNode node) {
    node.getClasses().forEach(classInUnit -> {
      visitClass(classInUnit);
    });
  }

  // GroovyClassVisitor
  public void visitMethodCallExpression(MethodCallExpression node) {
    if (TokenModifier.isDefaultLibrary(node.getMethod().getText())) {
      addToken(node.getMethod(), TokenType.FUNCTION, TokenModifier.DEFAULT_LIBRARY.bitmask);
    } else {
      addToken(node.getMethod(), TokenType.FUNCTION);
    }
    URI uri = this.sourceUnit.getSource().getURI();
    if (this.methodCalls.containsKey(uri)) {
      this.methodCalls.get(uri).add(node);
    } else {
      Set<MethodCallExpression> calls = new HashSet<>();
      calls.add(node);
      this.methodCalls.put(uri, calls);
    }
    super.visitMethodCallExpression(node);
  }

  public void visitMapEntryExpression(MapEntryExpression node) {
    addToken(node.getKeyExpression(), TokenType.PARAMETER);
    super.visitMapEntryExpression(node);
  }

  public void visitVariableExpression(VariableExpression node) {
    addToken(node, TokenType.VARIABLE);
    super.visitVariableExpression(node);
  }

  public void visitPropertyExpression(PropertyExpression node) {
    addToken(node.getProperty(), TokenType.PROPERTY);
    super.visitPropertyExpression(node);
  }
}
