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

import static org.assertj.core.api.Assertions.assertThat;
import static org.jenkinsci.plugins.pipeline.maven.TestUtils.runAfterMethod;
import static org.jenkinsci.plugins.pipeline.maven.TestUtils.runBeforeMethod;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.FileUtils;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TemporaryDirectoryAllocator;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
@TestMethodOrder(OrderAnnotation.class)
public class WithMavenStepRestartTest {

    public static BuildWatcher buildWatcher;

    private static File home;

    public JenkinsRule jenkinsRule;

    @BeforeAll
    public static void setupWatcher() throws IOException {
        buildWatcher = new BuildWatcher();
        runBeforeMethod(buildWatcher);

        home = new TemporaryDirectoryAllocator().allocate();
    }

    @BeforeEach
    public void configureJenkins(JenkinsRule r) throws Throwable {
        r.after();
        r.with(() -> home);
        r.before();
        jenkinsRule = r;
    }

    @AfterAll
    public static void stopWatcher() {
        runAfterMethod(buildWatcher);
    }

    @Issue("JENKINS-39134")
    @Test
    @Order(1)
    public void configure() throws Throwable {
        WorkflowJob p = jenkinsRule.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("node {withMaven {semaphore 'wait'}}", true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        SemaphoreStep.waitForStart("wait/1", b);
    }

    @Issue("JENKINS-39134")
    @Test
    @Order(2)
    public void resume() throws Throwable {
        WorkflowJob p = jenkinsRule.jenkins.getItemByFullName("p", WorkflowJob.class);
        WorkflowRun b = p.getBuildByNumber(1);
        @SuppressWarnings("deprecation")
        Class<?> deprecatedClass = org.jenkinsci.plugins.workflow.support.pickles.FilePathPickle.class;
        assertThat(FileUtils.readFileToString(new File(b.getRootDir(), "program.dat"), StandardCharsets.ISO_8859_1)).doesNotContain(deprecatedClass.getName());
        SemaphoreStep.success("wait/1", null);
        jenkinsRule.assertBuildStatusSuccess(jenkinsRule.waitForCompletion(b));
        SemaphoreStep.success("wait/2", null);
        jenkinsRule.buildAndAssertSuccess(p);
    }
}
