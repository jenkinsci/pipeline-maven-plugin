package org.jenkinsci.plugins.pipeline.maven;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

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

        assertThat(actualMavenArtifact.hashCode()).isEqualTo(expectedMavenArtifact.hashCode());
        assertThat(actualMavenArtifact).isEqualTo(expectedMavenArtifact);
    }
}
