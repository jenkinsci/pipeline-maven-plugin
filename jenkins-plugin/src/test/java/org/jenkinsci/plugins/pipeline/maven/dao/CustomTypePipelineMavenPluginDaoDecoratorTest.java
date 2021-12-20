package org.jenkinsci.plugins.pipeline.maven.dao;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

@RunWith(Parameterized.class)
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

    private final String type;
    private final String extension;
    private final List<String> additionalExpectedReportedTypes;

    private PipelineMavenPluginDao mockDelegate;
    private CustomTypePipelineMavenPluginDaoDecorator decorator;

    public CustomTypePipelineMavenPluginDaoDecoratorTest(String type, String extension, List<String> additionalExpectedReportedTypes) {
        this.type = type;
        this.extension = extension;
        this.additionalExpectedReportedTypes = additionalExpectedReportedTypes;
    }

    @Before
    public void setupMockDelegateAndDecorator() {
        mockDelegate = Mockito.mock(PipelineMavenPluginDao.class);
        decorator = new CustomTypePipelineMavenPluginDaoDecorator(mockDelegate);
    }

    @Test
    public void testHandlingOfCustomJarTypes() {
        recordGeneratedArtifact(type, extension);

        verifyRecordGeneratedArtifactCalled(type, extension);
        for (String additionalExpectedReportedType : additionalExpectedReportedTypes) {
            verifyRecordGeneratedArtifactCalled(additionalExpectedReportedType, extension);
        }

        verifyNoMoreInteractionsOnDelegate();
    }

    @Parameters(name = "type={0}, extension={1}, additionalExpectedReportedTypes={2}")
    public static Collection<Object[]> data() {
        List<Object[]> parameters = new ArrayList<>();

        // simple cases
        parameters.add(createTestParameters("pom", "pom"));
        parameters.add(createTestParameters("jar", "jar"));
        parameters.add(createTestParameters("war", "war"));
        parameters.add(createTestParameters("ear", "ear"));
        parameters.add(createTestParameters("rar", "rar"));

        // known types with different extension
        parameters.add(createTestParameters("test-jar", "jar"));
        parameters.add(createTestParameters("maven-plugin", "jar"));
        parameters.add(createTestParameters("ejb", "jar"));
        parameters.add(createTestParameters("ejb-client", "jar"));
        parameters.add(createTestParameters("java-source", "jar"));
        parameters.add(createTestParameters("javadoc", "jar"));

        // unknown types with different extension
        parameters.add(createTestParameters("nbm", "jar", "jar")); // JENKINS-52303
        parameters.add(createTestParameters("bundle", "jar", "jar")); // JENKINS-47069
        parameters.add(createTestParameters("docker-info", "jar", "jar")); // JENKINS-59500

        return parameters;
    }

    private static Object[] createTestParameters(String type, String extension, String... additionalExpectedReportedTypes) {
        return new Object[]{type, extension, Arrays.asList(additionalExpectedReportedTypes)};
    }

    private void recordGeneratedArtifact(String type, String extension) {
        recordGeneratedArtifact(decorator, type, extension);
    }

    private void verifyRecordGeneratedArtifactCalled(String type, String extension) {
        recordGeneratedArtifact(Mockito.verify(mockDelegate), type, extension);
    }

    private void recordGeneratedArtifact(PipelineMavenPluginDao dao, String type, String extension) {
        dao.recordGeneratedArtifact(JOB_FULL_NAME, BUILD_NUMBER, GROUP_ID, ARTIFACT_ID, VERSION, type, BASE_VERSION, REPOSITORY_URL, SKIP_DOWNSTREAM_TRIGGERS, extension, CLASSIFIER);
    }

    private void verifyNoMoreInteractionsOnDelegate() {
        Mockito.verifyNoMoreInteractions(mockDelegate);
    }

}