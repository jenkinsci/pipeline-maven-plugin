package org.jenkinsci.plugins.pipeline.maven.dao;

import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;

public class CustomTypePipelineMavenPluginDaoDecoratorTest {

    private static final String JOB_FULL_NAME = "jobFullName";
    private static final int BUILD_NUMBER = 0;
    private static final String GROUP_ID = "groupId";
    private static final String ARTIFACT_ID = "artifactId";
    private static final String VERSION = "version";
    private static final String BASE_VERSION = "baseVersion";
    private static final String REPOSITORY_URL = "repositoryUrl";
    private static final boolean SKIP_DOWNSTREAM_TRIGGERS = false;
    private static final String CLASSIFIER = "classifier";

    private PipelineMavenPluginDao mockDelegate;
    private CustomTypePipelineMavenPluginDaoDecorator decorator;

    @BeforeEach
    public void setupMockDelegateAndDecorator() {
        mockDelegate = Mockito.mock(PipelineMavenPluginDao.class);
        decorator = new CustomTypePipelineMavenPluginDaoDecorator(mockDelegate);
    }

    @ParameterizedTest
    @MethodSource
    public void testHandlingOfCustomJarTypes(
            String type, String extension, List<String> additionalExpectedReportedTypes) {
        recordGeneratedArtifact(type, extension);

        verifyRecordGeneratedArtifactCalled(type, extension);
        for (String additionalExpectedReportedType : additionalExpectedReportedTypes) {
            verifyRecordGeneratedArtifactCalled(additionalExpectedReportedType, extension);
        }

        verifyNoMoreInteractions(mockDelegate);
    }

    static Stream<Arguments> testHandlingOfCustomJarTypes() {
        return Stream.of(
                // simple cases
                createTestParameters("pom", "pom"),
                createTestParameters("jar", "jar"),
                createTestParameters("war", "war"),
                createTestParameters("ear", "ear"),
                createTestParameters("rar", "rar"),

                // known types with different extension
                createTestParameters("test-jar", "jar"),
                createTestParameters("maven-plugin", "jar"),
                createTestParameters("ejb", "jar"),
                createTestParameters("ejb-client", "jar"),
                createTestParameters("java-source", "jar"),
                createTestParameters("javadoc", "jar"),

                // unknown types with different extension
                createTestParameters("nbm", "jar", "jar"), // JENKINS-52303
                createTestParameters("bundle", "jar", "jar"), // JENKINS-47069
                createTestParameters("docker-info", "jar", "jar") // JENKINS-59500
                );
    }

    private static Arguments createTestParameters(
            String type, String extension, String... additionalExpectedReportedTypes) {
        return Arguments.of(type, extension, Arrays.asList(additionalExpectedReportedTypes));
    }

    private void recordGeneratedArtifact(String type, String extension) {
        recordGeneratedArtifact(decorator, type, extension);
    }

    private void verifyRecordGeneratedArtifactCalled(String type, String extension) {
        recordGeneratedArtifact(Mockito.verify(mockDelegate), type, extension);
    }

    private void recordGeneratedArtifact(PipelineMavenPluginDao dao, String type, String extension) {
        dao.recordGeneratedArtifact(
                JOB_FULL_NAME,
                BUILD_NUMBER,
                GROUP_ID,
                ARTIFACT_ID,
                VERSION,
                type,
                BASE_VERSION,
                REPOSITORY_URL,
                SKIP_DOWNSTREAM_TRIGGERS,
                extension,
                CLASSIFIER);
    }
}
