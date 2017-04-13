package org.jvnet.hudson.test;

import hudson.tasks.Maven;
import jenkins.model.Jenkins;

/**
 * TODO remove once https://github.com/jenkinsci/jenkins-test-harness-tools/pull/5 is merged
 *
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class ExtendedToolInstallations {
    /**
     * Declare "Maven 3.5.0" as the "default" Maven installation in Jenkins and as the Maven installation named "apache-maven-3.5.0".
     * Note that both {@link hudson.tasks.Maven.MavenInstallation} share the same Maven binaries.
     *
     * @return the "apache-maven-3.5.0" Maven {@link hudson.tasks.Maven.MavenInstallation}
     * @throws Exception
     */
    public static Maven.MavenInstallation configureMaven35() throws Exception {
        Maven.MavenInstallation mvn =  ToolInstallations.configureDefaultMaven("apache-maven-3.5.0", Maven.MavenInstallation.MAVEN_30);

        Maven.MavenInstallation maven350 = new Maven.MavenInstallation("apache-maven-3.5.0", mvn.getHome(), JenkinsRule.NO_PROPERTIES);
        Jenkins.getInstance().getDescriptorByType(Maven.DescriptorImpl.class).setInstallations(maven350);
        return maven350;
    }
}
