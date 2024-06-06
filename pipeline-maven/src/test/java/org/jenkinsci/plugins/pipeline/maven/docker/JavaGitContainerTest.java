/*
 * The MIT License
 *
 * Copyright 2017 CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.jenkinsci.plugins.pipeline.maven.docker;

import static org.assertj.core.api.Assertions.assertThat;

import org.jenkinsci.plugins.pipeline.maven.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@EnabledIf(
        value = "org.jenkinsci.plugins.pipeline.maven.util.Conditions#isLinuxAndDockerSocketExists",
        disabledReason = "Needs Docker and Docker does not work on Windows 2019 servers CI agents")
@Testcontainers
public class JavaGitContainerTest extends AbstractIntegrationTest {

    @Container
    public GenericContainer<?> containerRule =
            new GenericContainer<>("localhost/pipeline-maven/java-git").withExposedPorts(22);

    @Test
    public void smokes() throws Exception {
        assertThat(containerRule.execInContainer("java", "-version").getStderr())
                .contains("openjdk version \"17");
        assertThat(containerRule.execInContainer("git", "--version").getStdout())
                .contains("git version 2.");
    }
}
