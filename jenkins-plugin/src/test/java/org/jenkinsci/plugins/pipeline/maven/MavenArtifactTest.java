package org.jenkinsci.plugins.pipeline.maven;

import org.hamcrest.Matchers;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class MavenArtifactTest {

    @Test
    public void test_equals() {
        MavenArtifact expectedMavenArtifact = new MavenArtifact();
        expectedMavenArtifact.setGroupId("com.mycompany");
        expectedMavenArtifact.setArtifactId("pipeline-framework");
        expectedMavenArtifact.setVersion("1.0-SNAPSHOT");
        expectedMavenArtifact.setType("jar");

        MavenArtifact actualMavenArtifact = new MavenArtifact();
        actualMavenArtifact.setGroupId("com.mycompany");
        actualMavenArtifact.setArtifactId("pipeline-framework");
        actualMavenArtifact.setVersion("1.0-SNAPSHOT");
        actualMavenArtifact.setType("jar");

        assertThat(actualMavenArtifact.hashCode(), Matchers.is(expectedMavenArtifact.hashCode()));
        assertThat(actualMavenArtifact, Matchers.is(expectedMavenArtifact));


    }
}
