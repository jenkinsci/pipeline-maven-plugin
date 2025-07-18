package org.jenkinsci.plugins.pipeline.maven.publishers;

import static org.assertj.core.api.Assertions.assertThat;

import hudson.model.Fingerprint;
import hudson.model.Result;
import java.util.Hashtable;
import org.jenkinsci.plugins.pipeline.maven.AbstractIntegrationTest;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.jupiter.api.Test;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class DependencyFingerprintPublisherTest extends AbstractIntegrationTest {

    /**
     * Two (2) pipeline maven jobs consume the same commons-lang3-3.5.jar
     * dependency. Verify that withMaven fingerprints commons-lang3-3.5.jar on each
     * build
     *
     * @throws Exception
     */
    @Test
    public void verify_fingerprinting_of_dependencies() throws Exception {

        loadMonoDependencyMavenProjectInGitRepo(this.gitRepoRule);

        // @formatter:off
        String pipelineScript = "node() {\n" +
            "    git($/" + gitRepoRule.toString() + "/$)\n" +
            "    withMaven(options:[dependenciesFingerprintPublisher(includeReleaseVersions:true)]) {\n" +
            "        if (isUnix()) {\n" +
            "            sh 'mvn package'\n" +
            "        } else {\n" +
            "            bat 'mvn package'\n" +
            "        }\n" +
            "    }\n" +
            "}";
        // @formatter:on

        String commonsLang3version310Md5 = "48b9886957920a4cdb602780ca345087";

        WorkflowJob firstPipeline;
        { // first job using commons-lang3:3.5
            firstPipeline = jenkinsRule.createProject(WorkflowJob.class, "build-mono-dependency-maven-project-1");
            firstPipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
            jenkinsRule.assertBuildStatus(Result.SUCCESS, firstPipeline.scheduleBuild2(0));

            Fingerprint fingerprint = jenkinsRule.jenkins.getFingerprintMap().get(commonsLang3version310Md5);
            assertThat(fingerprint).isNotNull();

            assertThat(fingerprint.getFileName())
                    .isEqualTo("org/apache/commons/commons-lang3/3.18.0/commons-lang3-3.18.0.jar");
            Fingerprint.BuildPtr original = fingerprint.getOriginal();
            assertThat(original).isNull();
            Hashtable<String, Fingerprint.RangeSet> usages = fingerprint.getUsages();
            assertThat(usages).hasSize(1);
            assertThat(usages).containsKey(firstPipeline.getName());
        }
        { // second job using commons-lang3:3.5
            WorkflowJob secondPipeline =
                    jenkinsRule.createProject(WorkflowJob.class, "build-mono-dependency-maven-project-2");
            secondPipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
            jenkinsRule.assertBuildStatus(Result.SUCCESS, secondPipeline.scheduleBuild2(0));

            Fingerprint fingerprint = jenkinsRule.jenkins.getFingerprintMap().get(commonsLang3version310Md5);
            assertThat(fingerprint).isNotNull();
            Hashtable<String, Fingerprint.RangeSet> usages = fingerprint.getUsages();
            assertThat(usages).hasSize(2);
            assertThat(usages).containsKey(firstPipeline.getName());
            assertThat(usages).containsKey(secondPipeline.getName());
        }
    }
}
