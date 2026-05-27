package org.jenkinsci.plugins.pipeline.maven.publishers;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

public class GeneratedArtifactsPublisherTest {

    private final Path globalTempPath = Paths.get(System.getProperty("java.io.tmpdir"));

    @Test
    public void file_directly_under_temp_dir_is_temporary() throws Exception {
        String artifactFile = globalTempPath.resolve("foo.jar").toString();

        assertThat(GeneratedArtifactsPublisher.isUnderGlobalTempDir(artifactFile))
                .isTrue();
    }

    @Test
    public void file_nested_under_temp_dir_is_temporary() throws Exception {
        String artifactFile = globalTempPath
                .resolve(Paths.get("maven-x", "target", "foo.jar"))
                .toString();

        assertThat(GeneratedArtifactsPublisher.isUnderGlobalTempDir(artifactFile))
                .isTrue();
    }

    @Test
    public void file_in_workspace_is_not_temporary() throws Exception {
        String artifactFile = globalTempPath
                .resolveSibling("workspace")
                .resolve(Paths.get("job", "target", "foo.jar"))
                .toString();

        assertThat(GeneratedArtifactsPublisher.isUnderGlobalTempDir(artifactFile))
                .isFalse();
    }

    @Test
    public void relative_path_is_not_temporary() throws Exception {
        String artifactFile = Paths.get("target", "foo.jar").toString();

        assertThat(GeneratedArtifactsPublisher.isUnderGlobalTempDir(artifactFile))
                .isFalse();
    }
}
