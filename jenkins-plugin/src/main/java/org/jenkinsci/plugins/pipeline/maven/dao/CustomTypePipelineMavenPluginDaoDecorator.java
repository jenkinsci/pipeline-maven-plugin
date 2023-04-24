package org.jenkinsci.plugins.pipeline.maven.dao;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This decorator handles the reporting of generated artifacts with custom types which do not match the artifact extension.
 */
public class CustomTypePipelineMavenPluginDaoDecorator extends AbstractPipelineMavenPluginDaoDecorator {

    /**
     * These types are known to have extensions which are not matching their type. Since these types
     * are handled by Maven without having to install an extension, they can be ignored.
     * <p>
     * See https://maven.apache.org/ref/3.8.4/maven-core/artifact-handlers.html for more details.
     */
    private static final List<String> KNOWN_JAR_TYPES_WITH_DIFFERENT_EXTENSION = Arrays.asList(
            "test-jar",
            "maven-plugin",
            "ejb",
            "ejb-client",
            "java-source",
            "javadoc"
    );

    private final Logger LOGGER = Logger.getLogger(getClass().getName());

    public CustomTypePipelineMavenPluginDaoDecorator(@Nonnull PipelineMavenPluginDao delegate) {
        super(delegate);
    }

    @Override
    public void recordGeneratedArtifact(@Nonnull String jobFullName, int buildNumber, @Nonnull String groupId, @Nonnull String artifactId, @Nonnull String version, @Nonnull String type, @Nonnull String baseVersion, @Nullable String repositoryUrl, boolean skipDownstreamTriggers, String extension, String classifier) {
        super.recordGeneratedArtifact(jobFullName, buildNumber, groupId, artifactId, version, type, baseVersion, repositoryUrl, skipDownstreamTriggers, extension, classifier);

        if (shouldReportAgainWithExtensionAsType(type, extension)) {
            LOGGER.log(Level.FINE, "Recording generated artifact " + groupId + ":" + artifactId + ":" + version + " as " + extension + " (in addition to " + type + ")");
            super.recordGeneratedArtifact(jobFullName, buildNumber, groupId, artifactId, version, extension, baseVersion, repositoryUrl, skipDownstreamTriggers, extension, classifier);
        }
    }

    private boolean shouldReportAgainWithExtensionAsType(String type, String extension) {
        if (KNOWN_JAR_TYPES_WITH_DIFFERENT_EXTENSION.contains(type)) {
            return false;
        }

        return type != null && !type.equals(extension);
    }

}
