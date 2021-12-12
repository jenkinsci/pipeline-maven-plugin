package org.jenkinsci.plugins.pipeline.maven.docker;

import org.jenkinsci.test.acceptance.docker.DockerFixture;
import org.jenkinsci.test.acceptance.docker.fixtures.JavaContainer;

@DockerFixture(id = "nonmavenjava", ports = {22, 8080})
public class NonMavenJavaContainer extends JavaContainer {
}
