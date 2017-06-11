package org.jenkinsci.plugins.pipeline.maven;

import org.junit.Test;

import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.*;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class MavenSpyLogProcessorTest {

    @Test
    public void test_mavenArtifact_is_snapshot() {

        //  <artifact
        // extension="jar"
        // groupId="org.jmxtrans.embedded"
        // artifactId="embedded-jmxtrans"
        // id="org.jmxtrans.embedded:embedded-jmxtrans:jar:1.2.2-SNAPSHOT"
        // type="jar"
        // version="1.2.2-SNAPSHOT">

        MavenSpyLogProcessor.MavenArtifact artifact = new MavenSpyLogProcessor.MavenArtifact();
        artifact.groupId = "org.jmxtrans.embedded";
        artifact.artifactId = "embedded-jmxtrans";
        artifact.version = "1.2.2-SNAPSHOT";
        artifact.type = "jar";
        artifact.extension = "jar";

        assertThat(artifact.isSnapshot(), is(true));
    }

    @Test
    public void test_mavenArtifact_is_not_snapshot() {

        //  <artifact
        // extension="jar"
        // groupId="org.jmxtrans.embedded"
        // artifactId="embedded-jmxtrans"
        // id="org.jmxtrans.embedded:embedded-jmxtrans:jar:1.2.1"
        // type="jar"
        // version="1.2.1">

        MavenSpyLogProcessor.MavenArtifact artifact = new MavenSpyLogProcessor.MavenArtifact();
        artifact.groupId = "org.jmxtrans.embedded";
        artifact.artifactId = "embedded-jmxtrans";
        artifact.version = "1.2.1";
        artifact.type = "jar";
        artifact.extension = "jar";

        assertThat(artifact.isSnapshot(), is(false));
    }
}
