package com.microsoft.gradle.manager;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import com.microsoft.gradle.utils.StringReaderSourceWithURI;

import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.SourceUnit;

import groovy.lang.GroovyClassLoader;

public class GradleCompilationUnitManager {

  private Map<URI, CompilationUnit> compilationUnits = new HashMap<>();

  public CompilationUnit getCompilationUnit(URI uri, GradleFilesManager manager) {
    if (compilationUnits.containsKey(uri)) {
      return compilationUnits.get(uri);
    }
    // new compilationUnit
    CompilerConfiguration config = new CompilerConfiguration();
    GroovyClassLoader classLoader = new GroovyClassLoader(ClassLoader.getSystemClassLoader().getParent(), config, true);
    CompilationUnit compilationUnit = new CompilationUnit(config, null, classLoader);
    String contents = manager.getContents(uri);
    addFileToCompilationUnit(uri, contents, compilationUnit);
    return compilationUnit;
  }

  private void addFileToCompilationUnit(URI uri, String contents, CompilationUnit compilationUnit) {
    Path filePath = Paths.get(uri);
    SourceUnit sourceUnit = new SourceUnit(filePath.toString(),
        new StringReaderSourceWithURI(contents, uri, compilationUnit.getConfiguration()),
        compilationUnit.getConfiguration(), compilationUnit.getClassLoader(), compilationUnit.getErrorCollector());
    compilationUnit.addSource(sourceUnit);
  }
}
