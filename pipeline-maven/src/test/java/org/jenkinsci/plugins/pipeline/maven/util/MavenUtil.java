package org.jenkinsci.plugins.pipeline.maven.util;

import java.nio.file.Files;
import java.nio.file.Paths;

import org.jvnet.hudson.test.JenkinsRule;

import hudson.FilePath;
import hudson.tasks.Maven;
import jenkins.model.Jenkins;

public class MavenUtil {

    public static final String MAVEN_VERSION = System.getProperty("maven.version", "3.8.8");

    public static Maven.MavenInstallation configureDefaultMaven(FilePath agentRootPath) throws Exception {
        String mavenVersion = MAVEN_VERSION;
        FilePath mvnHome = agentRootPath.child("apache-maven-" + mavenVersion);
        if (!mvnHome.exists()) {
            FilePath mvn = agentRootPath.createTempFile("maven", "zip");
            mvn.copyFrom(Files.newInputStream(Paths.get(System.getProperty("buildDirectory", "target"), "apache-maven-" + mavenVersion + "-bin.zip")));
            mvn.unzip(agentRootPath);
        }
        Maven.MavenInstallation mavenInstallation = new Maven.MavenInstallation("default", mvnHome.getRemote(), JenkinsRule.NO_PROPERTIES);
        Jenkins.get().getDescriptorByType(Maven.DescriptorImpl.class).setInstallations(mavenInstallation);

        return mavenInstallation;
    }

}
