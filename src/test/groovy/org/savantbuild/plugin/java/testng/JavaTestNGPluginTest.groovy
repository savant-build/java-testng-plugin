/*
 * Copyright (c) 2014, Inversoft Inc., All Rights Reserved
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

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

import org.savantbuild.dep.domain.Artifact
import org.savantbuild.dep.domain.ArtifactMetaData
import org.savantbuild.dep.domain.Dependencies
import org.savantbuild.dep.domain.DependencyGroup
import org.savantbuild.dep.domain.License
import org.savantbuild.dep.domain.Publication
import org.savantbuild.dep.domain.ReifiedArtifact
import org.savantbuild.dep.workflow.FetchWorkflow
import org.savantbuild.dep.workflow.PublishWorkflow
import org.savantbuild.dep.workflow.Workflow
import org.savantbuild.dep.workflow.process.CacheProcess
import org.savantbuild.dep.workflow.process.URLProcess
import org.savantbuild.domain.Project
import org.savantbuild.domain.Version
import org.savantbuild.io.FileTools
import org.savantbuild.output.Output
import org.savantbuild.output.SystemOutOutput
import org.savantbuild.runtime.RuntimeConfiguration
import org.testng.annotations.BeforeMethod
import org.testng.annotations.BeforeSuite
import org.testng.annotations.Test

import groovy.xml.XmlSlurper
import static java.util.Arrays.asList
import static org.testng.Assert.assertEquals
import static org.testng.Assert.assertFalse
import static org.testng.Assert.assertTrue
import static org.testng.Assert.fail

/**
 * Tests the Java TestNG plugin.
 *
 * @author Brian Pontarelli
 */
class JavaTestNGPluginTest {
  public static Path projectDir

  Output output

  Project project

  @BeforeSuite
  void beforeSuite() {
    projectDir = Paths.get("")
    if (!Files.isRegularFile(projectDir.resolve("LICENSE"))) {
      projectDir = Paths.get("../java-testng-plugin")
    }
  }

  @BeforeMethod
  void beforeMethod() {
    FileTools.prune(projectDir.resolve("build/cache"))
    FileTools.prune(projectDir.resolve("test-project/build/test-reports"))

    output = new SystemOutOutput(true)
    output.enableDebug()

    project = new Project(projectDir.resolve("test-project"), output)
    project.group = "org.savantbuild.test"
    project.name = "test-project"
    project.version = new Version("1.0")
    project.licenses.add(License.parse("ApacheV2_0", null))

    project.publications.add("main", new Publication(new ReifiedArtifact("org.savantbuild.test:test-project:1.0.0", [License.parse("Commercial", "License")]), new ArtifactMetaData(null, [License.parse("Commercial", "License")]),
        project.directory.resolve("build/jars/test-project-1.0.0.jar"), null))
    project.publications.add("test", new Publication(new ReifiedArtifact("org.savantbuild.test:test-project:test-project-test:1.0.0:jar", [License.parse("Commercial", "License")]), new ArtifactMetaData(null, [License.parse("Commercial", "License")]),
        project.directory.resolve("build/jars/test-project-test-1.0.0.jar"), null))

    project.dependencies = new Dependencies(new DependencyGroup("test-compile", false, new Artifact("org.testng:testng:6.8.7:jar", false)))
    project.workflow = new Workflow(
        new FetchWorkflow(output,
            new CacheProcess(output, projectDir.resolve("build/cache").toString()),
            new URLProcess(output, "https://repository.savantbuild.org", null, null)
        ),
        new PublishWorkflow(
            new CacheProcess(output, projectDir.resolve("build/cache").toString())
        )
    )
  }

  @Test
  void test() throws Exception {
    JavaTestNGPlugin plugin = new JavaTestNGPlugin(project, new RuntimeConfiguration(), output)
    plugin.settings.javaVersion = "1.8"

    plugin.test()
    assertTestsRan("org.savantbuild.test.MyClassTest", "org.savantbuild.test.MyClassIntegrationTest", "org.savantbuild.test.MyClassUnitTest")

    plugin.test(null)
    assertTestsRan("org.savantbuild.test.MyClassTest", "org.savantbuild.test.MyClassIntegrationTest", "org.savantbuild.test.MyClassUnitTest")
  }

  @Test
  void skipTests() throws Exception {
    RuntimeConfiguration runtimeConfiguration = new RuntimeConfiguration()
    runtimeConfiguration.switches.booleanSwitches.add("skipTests")

    JavaTestNGPlugin plugin = new JavaTestNGPlugin(project, runtimeConfiguration, output)
    plugin.settings.javaVersion = "1.8"

    plugin.test()
    assertFalse(Files.isDirectory(projectDir.resolve("test-project/build/test-reports")))
  }

  @Test
  void testSwitch() throws Exception {
    RuntimeConfiguration runtimeConfiguration = new RuntimeConfiguration()

    JavaTestNGPlugin plugin = new JavaTestNGPlugin(project, runtimeConfiguration, output)
    plugin.settings.javaVersion = "1.8"

    // Simple name
    runtimeConfiguration.switches.add("test", "MyClassTest")
    plugin.test()
    assertTestsRan("org.savantbuild.test.MyClassTest")
    assertTestsDidNotRun("org.savantbuild.test.MyClassIntegrationTest", "org.savantbuild.test.MyClassUnitTest")

    // Fully qualified name
    runtimeConfiguration.switches.add("test", "org.savantbuild.test.MyClassTest")
    plugin.test()
    assertTestsRan("org.savantbuild.test.MyClassTest")
    assertTestsDidNotRun("org.savantbuild.test.MyClassIntegrationTest", "org.savantbuild.test.MyClassUnitTest")

    // Fuzzy
    runtimeConfiguration.switches.add("test", "MyClass")
    plugin.test()
    assertTestsRan("org.savantbuild.test.MyClassTest", "org.savantbuild.test.MyClassIntegrationTest", "org.savantbuild.test.MyClassUnitTest")
    assertTestsDidNotRun()
  }

  @Test
  void withExclude() throws Exception {
    JavaTestNGPlugin plugin = new JavaTestNGPlugin(project, new RuntimeConfiguration(), output)
    plugin.settings.javaVersion = "1.8"

    plugin.test(exclude: ["unit"])
    assertTestsRan("org.savantbuild.test.MyClassTest", "org.savantbuild.test.MyClassIntegrationTest")

    plugin.test(exclude: ["integration"])
    assertTestsRan("org.savantbuild.test.MyClassTest", "org.savantbuild.test.MyClassUnitTest")
  }

  @Test
  void withGroup() throws Exception {
    JavaTestNGPlugin plugin = new JavaTestNGPlugin(project, new RuntimeConfiguration(), output)
    plugin.settings.javaVersion = "1.8"

    plugin.test(groups: ["unit"])
    assertTestsRan("org.savantbuild.test.MyClassUnitTest")

    plugin.test(groups: ["integration"])
    assertTestsRan("org.savantbuild.test.MyClassIntegrationTest")
  }

  static void assertTestsDidNotRun(String... classNames) {
    assertTrue(Files.isDirectory(projectDir.resolve("test-project/build/test-reports")))
    assertTrue(Files.isReadable(projectDir.resolve("test-project/build/test-reports/All Tests/All Tests.xml")))

    def testsuite = new XmlSlurper().parse(projectDir.resolve("test-project/build/test-reports/All Tests/All Tests.xml").toFile())
    Set<String> tested = new HashSet<>()
    testsuite.testcase.each { testcase -> tested << testcase.@classname.text() }

    for (String className : classNames) {
      if (tested.contains(className)) {
        fail("Test [" + className + "] was not expected to run.")
      }
    }
  }

  static void assertTestsRan(String... classNames) {
    assertTrue(Files.isDirectory(projectDir.resolve("test-project/build/test-reports")))
    assertTrue(Files.isReadable(projectDir.resolve("test-project/build/test-reports/All Tests/All Tests.xml")))

    def testsuite = new XmlSlurper().parse(projectDir.resolve("test-project/build/test-reports/All Tests/All Tests.xml").toFile())
    Set<String> tested = new HashSet<>()
    testsuite.testcase.each { testcase -> tested << testcase.@classname.text() }

    assertEquals(tested, new HashSet<>(asList(classNames)))
  }
}
