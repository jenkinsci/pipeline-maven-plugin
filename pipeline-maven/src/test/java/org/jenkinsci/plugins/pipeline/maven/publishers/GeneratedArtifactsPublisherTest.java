package org.jenkinsci.plugins.pipeline.maven.publishers;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

public class GeneratedArtifactsPublisherTest {

    private static final Path TEMP_DIR = Paths.get(System.getProperty("java.io.tmpdir"));

    @Test
    public void isTemporaryMavenDeployPom_returns_true_for_matching_pom_in_temp_dir() {
        String path = TEMP_DIR.resolve("mvndeploy6910712014852835870.pom").toString();
        assertThat(GeneratedArtifactsPublisher.isTemporaryMavenDeployPom(path))
                .isTrue()
                .as("should return true on matching paths within tmp");
    }

    @Test
    public void isTemporaryMavenDeployPom_returns_false_for_matching_pom_outside_temp_dir() {
        String path = "/workspace/mvndeploy6910712014852835870.pom";
        assertThat(GeneratedArtifactsPublisher.isTemporaryMavenDeployPom(path))
                .isFalse()
                .as("should return false on paths outside of tmp");
    }

    @Test
    public void isTemporaryMavenDeployPom_returns_false_for_wrong_extension_in_temp_dir() {
        String path = TEMP_DIR.resolve("mvndeploy6910712014852835870.jar").toString();
        assertThat(GeneratedArtifactsPublisher.isTemporaryMavenDeployPom(path))
                .isFalse()
                .as("should return false on files without .pom extension");
    }

    @Test
    public void isTemporaryMavenDeployPom_returns_false_for_wrong_filename_in_temp_dir() {
        String path = TEMP_DIR.resolve("some-file.pom").toString();
        assertThat(GeneratedArtifactsPublisher.isTemporaryMavenDeployPom(path))
                .isFalse()
                .as("should return false on files without matching filename");
    }

    @Test
    public void isTemporaryMavenDeployPom_returns_false_for_paths_without_file() {
        String path = TEMP_DIR.toString();
        assertThat(GeneratedArtifactsPublisher.isTemporaryMavenDeployPom(path))
                .isFalse()
                .as("should return false on paths without file");
    }
}
