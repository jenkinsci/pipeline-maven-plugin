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

import org.jenkinsci.test.acceptance.docker.DockerRule;
import org.jenkinsci.utils.process.CommandBuilder;
import org.junit.Rule;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

public class JavaGitContainerTest {

    @Rule
    public DockerRule<JavaGitContainer> rule = new DockerRule<>(JavaGitContainer.class);

    @Test
    public void smokes() throws Exception {
        assertThat(rule.get().popen(new CommandBuilder("java", "-version")).verifyOrDieWith("could not launch Java"), containsString("openjdk version \"1.8.0_"));
        assertThat(rule.get().popen(new CommandBuilder("git", "version")).verifyOrDieWith("could not launch git"), containsString("git version 2."));
    }

}
