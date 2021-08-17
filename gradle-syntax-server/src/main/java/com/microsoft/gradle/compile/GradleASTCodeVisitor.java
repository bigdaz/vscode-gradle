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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import com.microsoft.gradle.semantictokens.TokenType;
import com.microsoft.gradle.utils.GradleUtils;

import org.apache.groovy.parser.antlr4.util.StringUtils;
import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.AnnotatedNode;
import org.codehaus.groovy.ast.ClassCodeVisitorSupport;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.ConstructorNode;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.ImportNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.ModuleNode;
import org.codehaus.groovy.ast.Parameter;
import org.codehaus.groovy.ast.PropertyNode;
import org.codehaus.groovy.ast.expr.ArgumentListExpression;
import org.codehaus.groovy.ast.expr.ArrayExpression;
import org.codehaus.groovy.ast.expr.AttributeExpression;
import org.codehaus.groovy.ast.expr.BinaryExpression;
import org.codehaus.groovy.ast.expr.BitwiseNegationExpression;
import org.codehaus.groovy.ast.expr.BooleanExpression;
import org.codehaus.groovy.ast.expr.CastExpression;
import org.codehaus.groovy.ast.expr.ClassExpression;
import org.codehaus.groovy.ast.expr.ClosureExpression;
import org.codehaus.groovy.ast.expr.ClosureListExpression;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.ConstructorCallExpression;
import org.codehaus.groovy.ast.expr.ElvisOperatorExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.FieldExpression;
import org.codehaus.groovy.ast.expr.GStringExpression;
import org.codehaus.groovy.ast.expr.ListExpression;
import org.codehaus.groovy.ast.expr.MapEntryExpression;
import org.codehaus.groovy.ast.expr.MapExpression;
import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.codehaus.groovy.ast.expr.MethodPointerExpression;
import org.codehaus.groovy.ast.expr.NotExpression;
import org.codehaus.groovy.ast.expr.PostfixExpression;
import org.codehaus.groovy.ast.expr.PrefixExpression;
import org.codehaus.groovy.ast.expr.PropertyExpression;
import org.codehaus.groovy.ast.expr.RangeExpression;
import org.codehaus.groovy.ast.expr.SpreadExpression;
import org.codehaus.groovy.ast.expr.SpreadMapExpression;
import org.codehaus.groovy.ast.expr.StaticMethodCallExpression;
import org.codehaus.groovy.ast.expr.TernaryExpression;
import org.codehaus.groovy.ast.expr.TupleExpression;
import org.codehaus.groovy.ast.expr.UnaryMinusExpression;
import org.codehaus.groovy.ast.expr.UnaryPlusExpression;
import org.codehaus.groovy.ast.expr.VariableExpression;
import org.codehaus.groovy.ast.stmt.AssertStatement;
import org.codehaus.groovy.ast.stmt.BlockStatement;
import org.codehaus.groovy.ast.stmt.BreakStatement;
import org.codehaus.groovy.ast.stmt.CaseStatement;
import org.codehaus.groovy.ast.stmt.CatchStatement;
import org.codehaus.groovy.ast.stmt.ContinueStatement;
import org.codehaus.groovy.ast.stmt.DoWhileStatement;
import org.codehaus.groovy.ast.stmt.EmptyStatement;
import org.codehaus.groovy.ast.stmt.ExpressionStatement;
import org.codehaus.groovy.ast.stmt.ForStatement;
import org.codehaus.groovy.ast.stmt.IfStatement;
import org.codehaus.groovy.ast.stmt.ReturnStatement;
import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.ast.stmt.SwitchStatement;
import org.codehaus.groovy.ast.stmt.SynchronizedStatement;
import org.codehaus.groovy.ast.stmt.ThrowStatement;
import org.codehaus.groovy.ast.stmt.TryCatchStatement;
import org.codehaus.groovy.ast.stmt.WhileStatement;
import org.codehaus.groovy.classgen.BytecodeExpression;
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
  private Stack<ASTNode> stack = new Stack<>();
  private Map<URI, List<ASTNode>> nodesByURI = new HashMap<>();
  private Map<URI, List<ClassNode>> classNodesByURI = new HashMap<>();
  private Map<ASTLookupKey, ASTNodeLookupData> lookup = new HashMap<>();

  private class SemanticToken {
    private final TokenType tokenType;
    private final int tokenModifiers;
    private final int line;
    private final int column;
    private final int length;
    private final String name;

    public SemanticToken(int line, int column, int length, TokenType tokenType, int tokenModifiers, String name) {
      this.line = line;
      this.column = column;
      this.length = length;
      this.tokenType = tokenType;
      this.tokenModifiers = tokenModifiers;
      this.name = name;
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

  private void addToken(int line, int column, int length, TokenType tokenType, int modifiers, String name) {
    if (length > 0) {
      tokens.add(new SemanticToken(line, column, length, tokenType, modifiers, name));
    }
  }

  private void addToken(ASTNode node, TokenType tokenType, int modifiers, String name) {
    addToken(node.getLineNumber(), node.getColumnNumber(), node.getLength(), tokenType, modifiers, name);
  }

  private void addToken(ASTNode node, TokenType tokenType) {
    addToken(node, tokenType, 0, node.getText());
  }

  public void visitCompilationUnit(CompilationUnit cu) {
    nodesByURI.clear();
    classNodesByURI.clear();
    lookup.clear();
    cu.iterator().forEachRemaining(unit -> visitSourceUnit(unit));
  }

  public void visitSourceUnit(SourceUnit unit) {
    this.sourceUnit = unit;
    URI uri = sourceUnit.getSource().getURI();
    nodesByURI.put(uri, new ArrayList<>());
    classNodesByURI.put(uri, new ArrayList<>());
    stack.clear();
    this.documentSymbols.remove(this.sourceUnit.getSource().getURI());
    this.semanticTokens.remove(this.sourceUnit.getSource().getURI());
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
  private class ASTLookupKey {
    public ASTLookupKey(ASTNode node) {
      this.node = node;
    }

    private ASTNode node;

    @Override
    public boolean equals(Object o) {
      // some ASTNode subclasses, like ClassNode, override equals() with
      // comparisons that are not strict. we need strict.
      ASTLookupKey other = (ASTLookupKey) o;
      return node == other.node;
    }

    @Override
    public int hashCode() {
      return node.hashCode();
    }
  }

  private class ASTNodeLookupData {
    public ASTNode parent;
    public URI uri;
  }

  private void pushASTNode(ASTNode node) {
    boolean isSynthetic = false;
    if (node instanceof AnnotatedNode) {
      AnnotatedNode annotatedNode = (AnnotatedNode) node;
      isSynthetic = annotatedNode.isSynthetic();
    }
    if (!isSynthetic) {
      URI uri = sourceUnit.getSource().getURI();
      nodesByURI.get(uri).add(node);

      ASTNodeLookupData data = new ASTNodeLookupData();
      data.uri = uri;
      if (stack.size() > 0) {
        data.parent = stack.lastElement();
      }
      lookup.put(new ASTLookupKey(node), data);
    }

    stack.add(node);
  }

  private void popASTNode() {
    stack.pop();
  }

  public List<ClassNode> getClassNodes() {
    List<ClassNode> result = new ArrayList<>();
    for (List<ClassNode> nodes : classNodesByURI.values()) {
      result.addAll(nodes);
    }
    return result;
  }

  public List<ASTNode> getNodes() {
    List<ASTNode> result = new ArrayList<>();
    for (List<ASTNode> nodes : nodesByURI.values()) {
      result.addAll(nodes);
    }
    return result;
  }

  public List<ASTNode> getNodes(URI uri) {
    List<ASTNode> nodes = nodesByURI.get(uri);
    if (nodes == null) {
      return Collections.emptyList();
    }
    return nodes;
  }

  public ASTNode getParent(ASTNode child) {
    if (child == null) {
      return null;
    }
    ASTNodeLookupData data = lookup.get(new ASTLookupKey(child));
    if (data == null) {
      return null;
    }
    return data.parent;
  }

  public boolean contains(ASTNode ancestor, ASTNode descendant) {
    ASTNode current = getParent(descendant);
    while (current != null) {
      if (current.equals(ancestor)) {
        return true;
      }
      current = getParent(current);
    }
    return false;
  }

  public URI getURI(ASTNode node) {
    ASTNodeLookupData data = lookup.get(new ASTLookupKey(node));
    if (data == null) {
      return null;
    }
    return data.uri;
  }

  public void visitModuleSemantic(ModuleNode node) {
    pushASTNode(node);
    try {
      node.getClasses().forEach(classInUnit -> {
        visitClass(classInUnit);
      });
    } finally {
      popASTNode();
    }
  }

  // GroovyClassVisitor

  public void visitClass(ClassNode node) {
    URI uri = sourceUnit.getSource().getURI();
    classNodesByURI.get(uri).add(node);
    pushASTNode(node);
    try {
      ClassNode unresolvedSuperClass = node.getUnresolvedSuperClass();
      if (unresolvedSuperClass != null && unresolvedSuperClass.getLineNumber() != -1) {
        pushASTNode(unresolvedSuperClass);
        popASTNode();
      }
      for (ClassNode unresolvedInterface : node.getUnresolvedInterfaces()) {
        if (unresolvedInterface.getLineNumber() == -1) {
          continue;
        }
        pushASTNode(unresolvedInterface);
        popASTNode();
      }
      super.visitClass(node);
    } finally {
      popASTNode();
    }
  }

  @Override
  public void visitImports(ModuleNode node) {
    if (node != null) {
      for (ImportNode importNode : node.getImports()) {
        pushASTNode(importNode);
        visitAnnotations(importNode);
        importNode.visit(this);
        popASTNode();
      }
      for (ImportNode importStarNode : node.getStarImports()) {
        pushASTNode(importStarNode);
        visitAnnotations(importStarNode);
        importStarNode.visit(this);
        popASTNode();
      }
      for (ImportNode importStaticNode : node.getStaticImports().values()) {
        pushASTNode(importStaticNode);
        visitAnnotations(importStaticNode);
        importStaticNode.visit(this);
        popASTNode();
      }
      for (ImportNode importStaticStarNode : node.getStaticStarImports().values()) {
        pushASTNode(importStaticStarNode);
        visitAnnotations(importStaticStarNode);
        importStaticStarNode.visit(this);
        popASTNode();
      }
    }
  }

  public void visitConstructor(ConstructorNode node) {
    pushASTNode(node);
    try {
      super.visitConstructor(node);
      for (Parameter parameter : node.getParameters()) {
        visitParameter(parameter);
      }
    } finally {
      popASTNode();
    }
  }

  public void visitMethod(MethodNode node) {
    pushASTNode(node);
    try {
      super.visitMethod(node);
      for (Parameter parameter : node.getParameters()) {
        visitParameter(parameter);
      }
    } finally {
      popASTNode();
    }
  }

  protected void visitParameter(Parameter node) {
    pushASTNode(node);
    try {
    } finally {
      popASTNode();
    }
  }

  public void visitField(FieldNode node) {
    pushASTNode(node);
    try {
      super.visitField(node);
    } finally {
      popASTNode();
    }
  }

  public void visitProperty(PropertyNode node) {
    pushASTNode(node);
    try {
      super.visitProperty(node);
    } finally {
      popASTNode();
    }
  }

  // GroovyCodeVisitor

  public void visitBlockStatement(BlockStatement node) {
    pushASTNode(node);
    try {
      super.visitBlockStatement(node);
    } finally {
      popASTNode();
    }
  }

  public void visitForLoop(ForStatement node) {
    pushASTNode(node);
    try {
      super.visitForLoop(node);
    } finally {
      popASTNode();
    }
  }

  public void visitWhileLoop(WhileStatement node) {
    pushASTNode(node);
    try {
      super.visitWhileLoop(node);
    } finally {
      popASTNode();
    }
  }

  public void visitDoWhileLoop(DoWhileStatement node) {
    pushASTNode(node);
    try {
      super.visitDoWhileLoop(node);
    } finally {
      popASTNode();
    }
  }

  public void visitIfElse(IfStatement node) {
    pushASTNode(node);
    try {
      super.visitIfElse(node);
    } finally {
      popASTNode();
    }
  }

  public void visitExpressionStatement(ExpressionStatement node) {
    pushASTNode(node);
    try {
      super.visitExpressionStatement(node);
    } finally {
      popASTNode();
    }
  }

  public void visitReturnStatement(ReturnStatement node) {
    pushASTNode(node);
    try {
      super.visitReturnStatement(node);
    } finally {
      popASTNode();
    }
  }

  public void visitAssertStatement(AssertStatement node) {
    pushASTNode(node);
    try {
      super.visitAssertStatement(node);
    } finally {
      popASTNode();
    }
  }

  public void visitTryCatchFinally(TryCatchStatement node) {
    pushASTNode(node);
    try {
      super.visitTryCatchFinally(node);
    } finally {
      popASTNode();
    }
  }

  public void visitEmptyStatement(EmptyStatement node) {
    pushASTNode(node);
    try {
      super.visitEmptyStatement(node);
    } finally {
      popASTNode();
    }
  }

  public void visitSwitch(SwitchStatement node) {
    pushASTNode(node);
    try {
      super.visitSwitch(node);
    } finally {
      popASTNode();
    }
  }

  public void visitCaseStatement(CaseStatement node) {
    pushASTNode(node);
    try {
      super.visitCaseStatement(node);
    } finally {
      popASTNode();
    }
  }

  public void visitBreakStatement(BreakStatement node) {
    pushASTNode(node);
    try {
      super.visitBreakStatement(node);
    } finally {
      popASTNode();
    }
  }

  public void visitContinueStatement(ContinueStatement node) {
    pushASTNode(node);
    try {
      super.visitContinueStatement(node);
    } finally {
      popASTNode();
    }
  }

  public void visitSynchronizedStatement(SynchronizedStatement node) {
    pushASTNode(node);
    try {
      super.visitSynchronizedStatement(node);
    } finally {
      popASTNode();
    }
  }

  public void visitThrowStatement(ThrowStatement node) {
    pushASTNode(node);
    try {
      super.visitThrowStatement(node);
    } finally {
      popASTNode();
    }
  }

  public void visitMethodCallExpression(MethodCallExpression node) {
    if (node.getMethod().getText().equals("dependencies")) {
      addToken(node.getMethod(), TokenType.KEYWORD);
    } else {
      addToken(node.getMethod(), TokenType.FUNCTION);
    }
    pushASTNode(node);
    try {
      super.visitMethodCallExpression(node);
    } finally {
      popASTNode();
    }
  }

  public void visitStaticMethodCallExpression(StaticMethodCallExpression node) {
    pushASTNode(node);
    try {
      super.visitStaticMethodCallExpression(node);
    } finally {
      popASTNode();
    }
  }

  public void visitConstructorCallExpression(ConstructorCallExpression node) {
    pushASTNode(node);
    try {
      super.visitConstructorCallExpression(node);
    } finally {
      popASTNode();
    }
  }

  public void visitBinaryExpression(BinaryExpression node) {
    pushASTNode(node);
    try {
      super.visitBinaryExpression(node);
    } finally {
      popASTNode();
    }
  }

  public void visitTernaryExpression(TernaryExpression node) {
    pushASTNode(node);
    try {
      super.visitTernaryExpression(node);
    } finally {
      popASTNode();
    }
  }

  public void visitShortTernaryExpression(ElvisOperatorExpression node) {
    pushASTNode(node);
    try {
      super.visitShortTernaryExpression(node);
    } finally {
      popASTNode();
    }
  }

  public void visitPostfixExpression(PostfixExpression node) {
    pushASTNode(node);
    try {
      super.visitPostfixExpression(node);
    } finally {
      popASTNode();
    }
  }

  public void visitPrefixExpression(PrefixExpression node) {
    pushASTNode(node);
    try {
      super.visitPrefixExpression(node);
    } finally {
      popASTNode();
    }
  }

  public void visitBooleanExpression(BooleanExpression node) {
    pushASTNode(node);
    try {
      super.visitBooleanExpression(node);
    } finally {
      popASTNode();
    }
  }

  public void visitNotExpression(NotExpression node) {
    pushASTNode(node);
    try {
      super.visitNotExpression(node);
    } finally {
      popASTNode();
    }
  }

  public void visitClosureExpression(ClosureExpression node) {
    pushASTNode(node);
    try {
      super.visitClosureExpression(node);
    } finally {
      popASTNode();
    }
  }

  public void visitTupleExpression(TupleExpression node) {
    pushASTNode(node);
    try {
      super.visitTupleExpression(node);
    } finally {
      popASTNode();
    }
  }

  public void visitListExpression(ListExpression node) {
    pushASTNode(node);
    try {
      super.visitListExpression(node);
    } finally {
      popASTNode();
    }
  }

  public void visitArrayExpression(ArrayExpression node) {
    pushASTNode(node);
    try {
      super.visitArrayExpression(node);
    } finally {
      popASTNode();
    }
  }

  public void visitMapExpression(MapExpression node) {
    pushASTNode(node);
    try {
      super.visitMapExpression(node);
    } finally {
      popASTNode();
    }
  }

  public void visitMapEntryExpression(MapEntryExpression node) {
    addToken(node.getKeyExpression(), TokenType.PARAMETER);
    pushASTNode(node);
    try {
      super.visitMapEntryExpression(node);
    } finally {
      popASTNode();
    }
  }

  public void visitRangeExpression(RangeExpression node) {
    pushASTNode(node);
    try {
      super.visitRangeExpression(node);
    } finally {
      popASTNode();
    }
  }

  public void visitSpreadExpression(SpreadExpression node) {
    pushASTNode(node);
    try {
      super.visitSpreadExpression(node);
    } finally {
      popASTNode();
    }
  }

  public void visitSpreadMapExpression(SpreadMapExpression node) {
    pushASTNode(node);
    try {
      super.visitSpreadMapExpression(node);
    } finally {
      popASTNode();
    }
  }

  public void visitMethodPointerExpression(MethodPointerExpression node) {
    pushASTNode(node);
    try {
      super.visitMethodPointerExpression(node);
    } finally {
      popASTNode();
    }
  }

  public void visitUnaryMinusExpression(UnaryMinusExpression node) {
    pushASTNode(node);
    try {
      super.visitUnaryMinusExpression(node);
    } finally {
      popASTNode();
    }
  }

  public void visitUnaryPlusExpression(UnaryPlusExpression node) {
    pushASTNode(node);
    try {
      super.visitUnaryPlusExpression(node);
    } finally {
      popASTNode();
    }
  }

  public void visitBitwiseNegationExpression(BitwiseNegationExpression node) {
    pushASTNode(node);
    try {
      super.visitBitwiseNegationExpression(node);
    } finally {
      popASTNode();
    }
  }

  public void visitCastExpression(CastExpression node) {
    pushASTNode(node);
    try {
      super.visitCastExpression(node);
    } finally {
      popASTNode();
    }
  }

  public void visitConstantExpression(ConstantExpression node) {
    pushASTNode(node);
    try {
      super.visitConstantExpression(node);
    } finally {
      popASTNode();
    }
  }

  public void visitClassExpression(ClassExpression node) {
    pushASTNode(node);
    try {
      super.visitClassExpression(node);
    } finally {
      popASTNode();
    }
  }

  public void visitVariableExpression(VariableExpression node) {
    addToken(node, TokenType.VARIABLE);
    pushASTNode(node);
    try {
      super.visitVariableExpression(node);
    } finally {
      popASTNode();
    }
  }

  // this calls visitBinaryExpression()
  /*
   * public void visitDeclarationExpression(DeclarationExpression node) {
   * pushASTNode(node); try { super.visitDeclarationExpression(node); } finally {
   * popASTNode(); } }
   */

  public void visitPropertyExpression(PropertyExpression node) {
    addToken(node.getProperty(), TokenType.PROPERTY);
    pushASTNode(node);
    try {
      super.visitPropertyExpression(node);
    } finally {
      popASTNode();
    }
  }

  public void visitAttributeExpression(AttributeExpression node) {
    pushASTNode(node);
    try {
      super.visitAttributeExpression(node);
    } finally {
      popASTNode();
    }
  }

  public void visitFieldExpression(FieldExpression node) {
    pushASTNode(node);
    try {
      super.visitFieldExpression(node);
    } finally {
      popASTNode();
    }
  }

  public void visitGStringExpression(GStringExpression node) {
    pushASTNode(node);
    try {
      super.visitGStringExpression(node);
    } finally {
      popASTNode();
    }
  }

  public void visitCatchStatement(CatchStatement node) {
    pushASTNode(node);
    try {
      super.visitCatchStatement(node);
    } finally {
      popASTNode();
    }
  }

  // this calls visitTupleListExpression()
  /*
   * public void visitArgumentlistExpression(ArgumentListExpression node) {
   * pushASTNode(node); try { super.visitArgumentlistExpression(node); } finally {
   * popASTNode(); } }
   */

  public void visitClosureListExpression(ClosureListExpression node) {
    pushASTNode(node);
    try {
      super.visitClosureListExpression(node);
    } finally {
      popASTNode();
    }
  }

  public void visitBytecodeExpression(BytecodeExpression node) {
    pushASTNode(node);
    try {
      super.visitBytecodeExpression(node);
    } finally {
      popASTNode();
    }
  }
}
