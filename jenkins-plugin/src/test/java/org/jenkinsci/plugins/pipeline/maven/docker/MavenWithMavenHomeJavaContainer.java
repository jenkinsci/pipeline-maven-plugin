package org.jenkinsci.plugins.pipeline.maven.docker;

import org.jenkinsci.test.acceptance.docker.DockerFixture;
import org.jenkinsci.test.acceptance.docker.fixtures.JavaContainer;

@DockerFixture(id = "maven-with-maven-home-java", ports = {22, 8080})
public class MavenWithMavenHomeJavaContainer extends JavaContainer {
}
