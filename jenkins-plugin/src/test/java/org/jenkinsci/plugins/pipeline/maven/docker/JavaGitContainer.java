package org.jenkinsci.plugins.pipeline.maven.docker;

import org.jenkinsci.test.acceptance.docker.DockerFixture;
import org.jenkinsci.test.acceptance.docker.fixtures.JavaContainer;

/**
 * Fixture capable of running java programs and git over ssh.
 *
 * @author Cyrille Le Clerc
 */
@DockerFixture(id="javagit", ports={22,8080})
public class JavaGitContainer extends JavaContainer {
}
