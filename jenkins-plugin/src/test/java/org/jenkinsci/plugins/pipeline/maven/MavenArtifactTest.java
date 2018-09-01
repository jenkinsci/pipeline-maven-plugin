package org.jenkinsci.plugins.pipeline.maven;

import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class MavenArtifactTest {

    @Test
    public void test_equals() {
        MavenArtifact expectedMavenArtifact = new MavenArtifact();
        expectedMavenArtifact.groupId = "com.mycompany";
        expectedMavenArtifact.artifactId = "pipeline-framework";
        expectedMavenArtifact.version = "1.0-SNAPSHOT";
        expectedMavenArtifact.type = "jar";

        MavenArtifact actualMavenArtifact = new MavenArtifact();
        actualMavenArtifact.groupId = "com.mycompany";
        actualMavenArtifact.artifactId = "pipeline-framework";
        actualMavenArtifact.version = "1.0-SNAPSHOT";
        actualMavenArtifact.type = "jar";

        Assert.assertThat(actualMavenArtifact.hashCode(), Matchers.is(expectedMavenArtifact.hashCode()));
        Assert.assertThat(actualMavenArtifact, Matchers.is(expectedMavenArtifact));


    }
}
