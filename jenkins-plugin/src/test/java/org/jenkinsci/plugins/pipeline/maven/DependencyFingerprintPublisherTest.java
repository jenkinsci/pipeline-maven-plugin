package org.jenkinsci.plugins.pipeline.maven;

import hudson.model.Fingerprint;
import hudson.model.Job;
import hudson.model.Result;
import hudson.tasks.Maven;
import jenkins.mvn.DefaultGlobalSettingsProvider;
import jenkins.mvn.DefaultSettingsProvider;
import jenkins.mvn.GlobalMavenConfig;
import jenkins.plugins.git.GitSampleRepoRule;
import jenkins.scm.impl.mock.GitSampleRepoRuleUtils;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.ExtendedToolInstallations;
import org.jvnet.hudson.test.JenkinsRule;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Hashtable;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class DependencyFingerprintPublisherTest extends AbstractIntegrationTest {

    /**
     * Two (2) pipeline maven jobs consume the same commons-lang3-3.5.jar dependency.
     * Verify that withMaven fingerprints commons-lang3-3.5.jar on each build
     * @throws Exception
     */
    @Test
    public void verify_fingerprinting_of_dependencies() throws Exception {

        loadMonoDependencyMavenProjectInGitRepo(this.gitRepoRule);

        String pipelineScript = "node('master') {\n" +
                "    git($/" + gitRepoRule.toString() + "/$)\n" +
                "    withMaven(options:[dependenciesFingerprintPublisher(includeReleaseVersions:true)]) {\n" +
                "        sh 'mvn package'\n" +
                "    }\n" +
                "}";

        String commonsLang3version35Md5 = "780b5a8b72eebe6d0dbff1c11b5658fa";

        WorkflowJob firstPipeline;
        { // first job using commons-lang3:3.5
            firstPipeline = jenkinsRule.createProject(WorkflowJob.class, "build-mono-dependency-maven-project-1");
            firstPipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
            jenkinsRule.assertBuildStatus(Result.SUCCESS, firstPipeline.scheduleBuild2(0));

            Fingerprint fingerprint = jenkinsRule.jenkins.getFingerprintMap().get(commonsLang3version35Md5);
            assertThat(fingerprint, not(nullValue()));

            assertThat(fingerprint.getFileName(), is("org/apache/commons/commons-lang3/3.5/commons-lang3-3.5.jar"));
            Fingerprint.BuildPtr original = fingerprint.getOriginal();
            assertThat(original, is(nullValue()));
            Hashtable<String, Fingerprint.RangeSet> usages = fingerprint.getUsages();
            assertThat(usages.size(), is(1));
            assertThat(usages.containsKey(firstPipeline.getName()), is(true));
        }
        { // second job using commons-lang3:3.5
            WorkflowJob secondPipeline = jenkinsRule.createProject(WorkflowJob.class, "build-mono-dependency-maven-project-2");
            secondPipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
            jenkinsRule.assertBuildStatus(Result.SUCCESS, secondPipeline.scheduleBuild2(0));

            Fingerprint fingerprint = jenkinsRule.jenkins.getFingerprintMap().get(commonsLang3version35Md5);
            assertThat(fingerprint, not(nullValue()));
            Hashtable<String, Fingerprint.RangeSet> usages = fingerprint.getUsages();
            assertThat(usages.size(), is(2));
            assertThat(usages.containsKey(firstPipeline.getName()), is(true));
            assertThat(usages.containsKey(secondPipeline.getName()), is(true));
        }

    }
}
