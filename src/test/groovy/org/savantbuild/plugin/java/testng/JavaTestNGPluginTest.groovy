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

import org.savantbuild.dep.domain.*
import org.savantbuild.dep.workflow.FetchWorkflow
import org.savantbuild.dep.workflow.PublishWorkflow
import org.savantbuild.dep.workflow.Workflow
import org.savantbuild.dep.workflow.process.CacheProcess
import org.savantbuild.dep.workflow.process.URLProcess
import org.savantbuild.domain.Project
import org.savantbuild.io.FileTools
import org.savantbuild.output.Output
import org.savantbuild.output.SystemOutOutput
import org.testng.annotations.BeforeSuite
import org.testng.annotations.Test

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

import static org.testng.Assert.assertEquals
import static org.testng.Assert.assertTrue

/**
 * Tests the Java TestNG plugin.
 *
 * @author Brian Pontarelli
 */
class JavaTestNGPluginTest {
  public static Path projectDir

  @BeforeSuite
  public void beforeSuite() {
    projectDir = Paths.get("")
    if (!Files.isRegularFile(projectDir.resolve("LICENSE"))) {
      projectDir = Paths.get("../java-testng-plugin")
    }
  }

  @Test
  public void all() throws Exception {
    FileTools.prune(projectDir.resolve("build/cache"))
    FileTools.prune(projectDir.resolve("test-project/build/test-reports"))

    Output output = new SystemOutOutput(true)
    output.enableDebug()

    Project project = new Project(projectDir.resolve("test-project"), output)
    project.group = "org.savantbuild.test"
    project.name = "test-project"
    project.version = new Version("1.0")
    project.license = License.Apachev2

    project.publications.add("main", new Publication(new Artifact("org.savantbuild.test:test-project:1.0.0", License.Commercial), new ArtifactMetaData(null, License.Commercial),
        Paths.get("build/jars/test-project-1.0.0.jar"), null))
    project.publications.add("test", new Publication(new Artifact("org.savantbuild.test:test-project:test-project-test:1.0.0:jar", License.Commercial), new ArtifactMetaData(null, License.Commercial),
        Paths.get("build/jars/test-project-test-1.0.0.jar"), null))

    Path repositoryPath = Paths.get(System.getProperty("user.home"), "dev/inversoft/repositories/savant")
    project.dependencies = new Dependencies(new DependencyGroup("test-compile", false, new Dependency("org.testng:testng:6.8.7:jar", false)))
    project.workflow = new Workflow(
        new FetchWorkflow(output,
            new CacheProcess(output, projectDir.resolve("build/cache").toString()),
            new URLProcess(output, repositoryPath.toUri().toString(), null, null)
        ),
        new PublishWorkflow(
            new CacheProcess(output, projectDir.resolve("build/cache").toString())
        )
    )

    JavaTestNGPlugin plugin = new JavaTestNGPlugin(project, output)
    plugin.settings.javaVersion = "1.6"

    plugin.test()
    assertTrue(Files.isDirectory(projectDir.resolve("test-project/build/test-reports")))
    assertTrue(Files.isReadable(projectDir.resolve("test-project/build/test-reports/All Tests/All Tests.xml")))

    def testsuite = new XmlSlurper().parse(projectDir.resolve("test-project/build/test-reports/All Tests/All Tests.xml").toFile())
    assertEquals(testsuite.testcase.@classname.text(), "org.savantbuild.test.MyClassTest")
  }
}
