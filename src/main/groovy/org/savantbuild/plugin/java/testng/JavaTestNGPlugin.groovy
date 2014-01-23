/*
 * Copyright (c) 2013, Inversoft Inc., All Rights Reserved
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific
 * language governing permissions and limitations under the License.
 */
package org.savantbuild.plugin.java.testng

import groovy.xml.MarkupBuilder
import org.savantbuild.dep.domain.ArtifactID
import org.savantbuild.domain.Project
import org.savantbuild.io.FileTools
import org.savantbuild.lang.Classpath
import org.savantbuild.output.Output
import org.savantbuild.plugin.dep.DependencyPlugin
import org.savantbuild.plugin.groovy.BaseGroovyPlugin

import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.jar.JarFile

/**
 * The Java TestNG plugin. The public methods on this class define the features of the plugin.
 */
class JavaTestNGPlugin extends BaseGroovyPlugin {
  public static
  final String ERROR_MESSAGE = "You must create the file [~/.savant/plugins/org.savantbuild.plugin.java.properties] " +
      "that contains the system configuration for the Java system. This file should include the location of the JDK " +
      "(java and javac) by version. These properties look like this:\n\n" +
      "  1.6=/Library/Java/JavaVirtualMachines/1.6.0_65-b14-462.jdk/Contents/Home\n" +
      "  1.7=/Library/Java/JavaVirtualMachines/jdk1.7.0_10.jdk/Contents/Home\n" +
      "  1.8=/Library/Java/JavaVirtualMachines/jdk1.8.0.jdk/Contents/Home\n"
  JavaTestNGSettings settings = new JavaTestNGSettings()
  Properties properties
  Path javaPath
  DependencyPlugin dependencyPlugin

  JavaTestNGPlugin(Project project, Output output) {
    super(project, output)
    properties = loadConfiguration(new ArtifactID("org.savantbuild.plugin", "java", "java", "jar"), ERROR_MESSAGE)
    dependencyPlugin = new DependencyPlugin(project, output)
  }

  /**
   * Runs the TestNG tests.
   */
  void test() {
    initialize()

    Classpath classpath = dependencyPlugin.classpath {
      settings.dependencies.each { deps -> dependencies(deps) }
      project.publications.group("main").each { publication -> path(location: publication.file) }
      project.publications.group("test").each { publication -> path(location: publication.file) }
    }

    Path xmlFile = buildXMLFile()

    String command = "${javaPath} ${settings.jvmArguments} ${classpath.toString("-classpath ")} org.testng.TestNG -d ${settings.reportDirectory} ${xmlFile}"
    Process process = command.execute(null, project.directory.toFile())
    process.consumeProcessOutput(System.out, System.err)

    int result = process.waitFor()
    Files.delete(xmlFile)
    if (result != 0) {
      fail("Build failed.")
    }
  }

  Path buildXMLFile() {
    Set<String> classNames = new TreeSet<>()
    project.publications.group("test").each { publication ->
      JarFile jarFile = new JarFile(project.directory.resolve(publication.file).toFile())
      jarFile.entries().each { entry ->
        if (!entry.directory && entry.name.endsWith("Test.class")) {
          classNames.add(entry.name.replace("/", ".").replace(".class", ""))
        }
      }
    }

    Path xmlFile = FileTools.createTempPath("savant", "testng.xml", true)
    BufferedWriter writer = Files.newBufferedWriter(xmlFile, Charset.forName("UTF-8"))
    MarkupBuilder xml = new MarkupBuilder(writer)
    xml.suite(name: "All Tests", "allow-return-values": "true", verbose: "${settings.verbosity}") {
      test(name: "All Tests") {
        classes {
          classNames.each { className -> "class"(name: className) }
        }
      }
    }

    writer.flush()
    writer.close()
    output.debug("TestNG XML file contents are:\n${new String(Files.readAllBytes(xmlFile), "UTF-8")}")
    return xmlFile
  }

  private void initialize() {
    if (!settings.javaVersion) {
      fail("You must configure the Java version to use with the settings object. It will look something like this:\n\n" +
          "  javaTestNG.settings.javaVersion=\"1.7\"")
    }

    String javaHome = properties.getProperty(settings.javaVersion)
    if (!javaHome) {
      fail("No JDK is configured for version [${settings.javaVersion}].\n\n${ERROR_MESSAGE}")
    }

    javaPath = Paths.get(javaHome, "bin/java")
    if (!Files.isRegularFile(javaPath)) {
      fail("The java executable [${javaPath.toAbsolutePath()}] does not exist.")
    }
    if (!Files.isExecutable(javaPath)) {
      fail("The java executable [${javaPath.toAbsolutePath()}] is not executable.")
    }
  }
}
