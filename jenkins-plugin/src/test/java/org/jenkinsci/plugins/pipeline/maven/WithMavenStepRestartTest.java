/*
 * The MIT License
 *
 * Copyright 2016 CloudBees, Inc.
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
package org.jenkinsci.plugins.pipeline.maven;

import java.io.File;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.FileUtils;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsSessionRule;

public class WithMavenStepRestartTest {

    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();

    @Rule
    public JenkinsSessionRule rr = new JenkinsSessionRule();

    @Issue("JENKINS-39134")
    @Test
    public void resume() throws Throwable {
        rr.then(r -> {
            WorkflowJob p = r.createProject(WorkflowJob.class, "p");
            p.setDefinition(new CpsFlowDefinition("node {withMaven {semaphore 'wait'}}", true));
            WorkflowRun b = p.scheduleBuild2(0).waitForStart();
            SemaphoreStep.waitForStart("wait/1", b);
        });
        rr.then(r -> {
            WorkflowJob p = r.jenkins.getItemByFullName("p", WorkflowJob.class);
            WorkflowRun b = p.getBuildByNumber(1);
            @SuppressWarnings("deprecation")
            Class<?> deprecatedClass = org.jenkinsci.plugins.workflow.support.pickles.FilePathPickle.class;
            assertThat(FileUtils.readFileToString(new File(b.getRootDir(), "program.dat"), StandardCharsets.ISO_8859_1),
                not(containsString(deprecatedClass.getName())));
            SemaphoreStep.success("wait/1", null);
            r.assertBuildStatusSuccess(r.waitForCompletion(b));
            SemaphoreStep.success("wait/2", null);
            r.buildAndAssertSuccess(p);
        });
    }
}
