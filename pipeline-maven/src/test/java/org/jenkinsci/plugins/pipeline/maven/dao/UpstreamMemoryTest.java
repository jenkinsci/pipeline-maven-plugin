package org.jenkinsci.plugins.pipeline.maven.dao;

import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class UpstreamMemoryTest {

    @InjectMocks
    private UpstreamMemory memory;

    @Mock
    private PipelineMavenPluginDao dao;

    @Test
    public void should_list_upstream_jobs_from_dao() {
        when(dao.listUpstreamJobs("a job", 42)).thenReturn(singletonMap("upstream", 1));

        Map<String, Integer> result = memory.listUpstreamJobs(dao, "a job", 42);

        assertThat(result).containsEntry("upstream", 1);
        verify(dao).listUpstreamJobs("a job", 42);
        verifyNoMoreInteractions(dao);
    }

    @Test
    public void should_list_upstream_jobs_from_memory() {
        when(dao.listUpstreamJobs("a job", 42)).thenReturn(singletonMap("upstream", 1));

        memory.listUpstreamJobs(dao, "a job", 42);
        Map<String, Integer> result = memory.listUpstreamJobs(dao, "a job", 42);

        assertThat(result).containsEntry("upstream", 1);
        verify(dao).listUpstreamJobs("a job", 42);
        verifyNoMoreInteractions(dao);
    }
}
