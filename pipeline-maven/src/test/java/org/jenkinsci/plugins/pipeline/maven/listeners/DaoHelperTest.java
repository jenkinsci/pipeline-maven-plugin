package org.jenkinsci.plugins.pipeline.maven.listeners;

import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import org.jenkinsci.plugins.pipeline.maven.GlobalPipelineMavenConfig;
import org.jenkinsci.plugins.pipeline.maven.MavenArtifact;
import org.jenkinsci.plugins.pipeline.maven.dao.PipelineMavenPluginDao;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class DaoHelperTest {

    @Mock
    private GlobalPipelineMavenConfig config;

    @InjectMocks
    private DaoHelper helper;

    @Mock
    private PipelineMavenPluginDao dao;

    @BeforeEach
    public void configureMocks() {
        when(config.getDao()).thenReturn(dao);
    }

    @AfterEach
    public void checkMocks() {
        verify(config).getDao();
        verifyNoMoreInteractions(config, dao);
    }

    @Test
    public void should_return_generated_artifacts_from_dao() {
        MavenArtifact artifact = new MavenArtifact("groupId:artifactId:version");
        when(dao.getGeneratedArtifacts("a job", 42)).thenReturn(Collections.singletonList(artifact));

        List<MavenArtifact> result = helper.getGeneratedArtifacts("a job", 42);

        assertThat(result).contains(artifact);
        verify(dao).getGeneratedArtifacts("a job", 42);
    }

    @Test
    public void should_return_generated_artifacts_from_memory() {
        MavenArtifact artifact = new MavenArtifact("groupId:artifactId:version");
        when(dao.getGeneratedArtifacts("a job", 42)).thenReturn(Collections.singletonList(artifact));

        helper.getGeneratedArtifacts("a job", 42);
        List<MavenArtifact> result = helper.getGeneratedArtifacts("a job", 42);

        assertThat(result).contains(artifact);
        verify(dao).getGeneratedArtifacts("a job", 42);
    }

    @Test
    public void should_list_downstream_jobs_by_artifact_from_dao() {
        MavenArtifact artifact = new MavenArtifact("groupId:artifactId:version");
        Map<MavenArtifact, SortedSet<String>> answer = singletonMap(artifact, new TreeSet<String>());
        answer.get(artifact).add("upstream");
        when(dao.listDownstreamJobsByArtifact("a job", 42)).thenReturn(answer);

        Map<MavenArtifact, SortedSet<String>> result = helper.listDownstreamJobsByArtifact("a job", 42);

        TreeSet<String> values = new TreeSet<>();
        values.add("upstream");
        assertThat(result).containsEntry(artifact, values);
        verify(dao).listDownstreamJobsByArtifact("a job", 42);
    }

    @Test
    public void should_list_downstream_jobs_by_artifact_from_memory() {
        MavenArtifact artifact = new MavenArtifact("groupId:artifactId:version");
        Map<MavenArtifact, SortedSet<String>> answer = singletonMap(artifact, new TreeSet<String>());
        answer.get(artifact).add("upstream");
        when(dao.listDownstreamJobsByArtifact("a job", 42)).thenReturn(answer);

        helper.listDownstreamJobsByArtifact("a job", 42);
        Map<MavenArtifact, SortedSet<String>> result = helper.listDownstreamJobsByArtifact("a job", 42);

        TreeSet<String> values = new TreeSet<>();
        values.add("upstream");
        assertThat(result).containsEntry(artifact, values);
        verify(dao).listDownstreamJobsByArtifact("a job", 42);
    }
}
