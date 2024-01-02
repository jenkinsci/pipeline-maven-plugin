package org.jenkinsci.plugins.pipeline.maven.util;

import hudson.FilePath;
import hudson.tasks.Maven;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import jenkins.model.Jenkins;
import org.jvnet.hudson.test.JenkinsRule;

public class MavenUtil {

    public static final String MAVEN_VERSION = System.getProperty("maven.version", "3.8.8");

    public static Maven.MavenInstallation configureDefaultMaven(FilePath agentRootPath) throws Exception {
        return configureMaven(agentRootPath, MAVEN_VERSION, "default");
    }

    public static Maven.MavenInstallation configureMaven(FilePath agentRootPath, String mavenVersion, String toolName)
            throws Exception {
        Path mavenArchive =
                Paths.get(System.getProperty("buildDirectory", "target"), "apache-maven-" + mavenVersion + "-bin.zip");
        if (!mavenArchive.toFile().exists()) {
            URL mavenArchiveUrl = new URL("https://archive.apache.org/dist/maven/maven-3/" + mavenVersion
                    + "/binaries/apache-maven-" + mavenVersion + "-bin.zip");
            try (InputStream is = mavenArchiveUrl.openStream();
                    FileOutputStream os = new FileOutputStream(mavenArchive.toFile())) {
                os.getChannel().transferFrom(Channels.newChannel(is), 0, Long.MAX_VALUE);
            }
        }
        FilePath mvnHome = agentRootPath.child("apache-maven-" + mavenVersion);
        if (!mvnHome.exists()) {
            FilePath mvn = agentRootPath.createTempFile("maven", "zip");
            mvn.copyFrom(Files.newInputStream(mavenArchive));
            mvn.unzip(agentRootPath);
        }
        Maven.MavenInstallation mavenInstallation =
                new Maven.MavenInstallation(toolName, mvnHome.getRemote(), JenkinsRule.NO_PROPERTIES);
        Jenkins.get().getDescriptorByType(Maven.DescriptorImpl.class).setInstallations(mavenInstallation);

        return mavenInstallation;
    }
}
