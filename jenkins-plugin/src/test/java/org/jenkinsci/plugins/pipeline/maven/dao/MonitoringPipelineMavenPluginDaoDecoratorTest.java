package org.jenkinsci.plugins.pipeline.maven.dao;

import static java.util.regex.Pattern.DOTALL;
import static java.util.regex.Pattern.compile;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.text.MatchesPattern.matchesPattern;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.junit.After;
import org.junit.Test;

public class MonitoringPipelineMavenPluginDaoDecoratorTest {

    private PipelineMavenPluginDao delegate = mock(PipelineMavenPluginDao.class);

    private MonitoringPipelineMavenPluginDaoDecorator decorator = new MonitoringPipelineMavenPluginDaoDecorator(delegate);

    @After
    public void checkDelegate() {
        verify(delegate).toPrettyString();
        verifyNoMoreInteractions(delegate);
    }

    @Test
    public void shoudIncrementWriteWhenRecordDependency() {
        decorator.recordDependency("j", 42, "g", "a", "v", "t", "s", false, "c");

        assertThat(decorator.toPrettyString(), matchesPattern(compile(".*find:.*count=0.*write:.*count=1.*Cache.*", DOTALL)));

        verify(delegate).recordDependency("j", 42, "g", "a", "v", "t", "s", false, "c");
    }

    @Test
    public void shoudIncrementWriteWhenRecordParentProject() {
        decorator.recordParentProject("j", 42, "g", "a", "v", false);

        assertThat(decorator.toPrettyString(), matchesPattern(compile(".*find:.*count=0.*write:.*count=1.*Cache.*", DOTALL)));

        verify(delegate).recordParentProject("j", 42, "g", "a", "v", false);
    }

    @Test
    public void shoudIncrementWriteWhenRecordGeneratedArtifact() {
        decorator.recordGeneratedArtifact("j", 42, "g", "a", "v", "t", "bv", "r", false, "e", "c");

        assertThat(decorator.toPrettyString(), matchesPattern(compile(".*find:.*count=0.*write:.*count=1.*Cache.*", DOTALL)));

        verify(delegate).recordGeneratedArtifact("j", 42, "g", "a", "v", "t", "bv", "r", false, "e", "c");
    }

    @Test
    public void shoudIncrementWriteWhenRecordBuildUpstreamCause() {
        decorator.recordBuildUpstreamCause("j", 42, "d", 4242);

        assertThat(decorator.toPrettyString(), matchesPattern(compile(".*find:.*count=0.*write:.*count=1.*Cache.*", DOTALL)));

        verify(delegate).recordBuildUpstreamCause("j", 42, "d", 4242);
    }

    @Test
    public void shoudIncrementReadWhenListDependencies() {
        decorator.listDependencies("j", 42);

        assertThat(decorator.toPrettyString(), matchesPattern(compile(".*find:.*count=1.*write:.*count=0.*Cache.*", DOTALL)));

        verify(delegate).listDependencies("j", 42);
    }

    @Test
    public void shoudIncrementReadWhenGetGeneratedArtifacts() {
        decorator.getGeneratedArtifacts("j", 42);

        assertThat(decorator.toPrettyString(), matchesPattern(compile(".*find:.*count=1.*write:.*count=0.*Cache.*", DOTALL)));

        verify(delegate).getGeneratedArtifacts("j", 42);
    }

    @Test
    public void shoudIncrementWriteWhenRenameJob() {
        decorator.renameJob("o", "n");

        assertThat(decorator.toPrettyString(), matchesPattern(compile(".*find:.*count=0.*write:.*count=1.*Cache.*", DOTALL)));

        verify(delegate).renameJob("o", "n");
    }

    @Test
    public void shoudIncrementWriteWhenDeleteJob() {
        decorator.deleteJob("j");

        assertThat(decorator.toPrettyString(), matchesPattern(compile(".*find:.*count=0.*write:.*count=1.*Cache.*", DOTALL)));

        verify(delegate).deleteJob("j");
    }

    @Test
    public void shoudIncrementWriteWhenDeleteBuild() {
        decorator.deleteBuild("j", 42);

        assertThat(decorator.toPrettyString(), matchesPattern(compile(".*find:.*count=0.*write:.*count=1.*Cache.*", DOTALL)));

        verify(delegate).deleteBuild("j", 42);
    }

    @Test
    public void shoudIncrementReadWhenGetListDownstreamJobs() {
        decorator.listDownstreamJobs("j", 42);

        assertThat(decorator.toPrettyString(), matchesPattern(compile(".*find:.*count=1.*write:.*count=0.*Cache.*", DOTALL)));

        verify(delegate).listDownstreamJobs("j", 42);
    }

    @Test
    public void shoudIncrementReadWhenGetListDownstreamJobsByArtifact() {
        decorator.listDownstreamJobsByArtifact("j", 42);

        assertThat(decorator.toPrettyString(), matchesPattern(compile(".*find:.*count=1.*write:.*count=0.*Cache.*", DOTALL)));

        verify(delegate).listDownstreamJobsByArtifact("j", 42);
    }

    @Test
    public void shoudIncrementReadWhenGetListDownstreamJobsWithArtifact() {
        decorator.listDownstreamJobs("g", "a", "v", "bv", "t", "c");

        assertThat(decorator.toPrettyString(), matchesPattern(compile(".*find:.*count=1.*write:.*count=0.*Cache.*", DOTALL)));

        verify(delegate).listDownstreamJobs("g", "a", "v", "bv", "t", "c");
    }

    @Test
    public void shoudIncrementReadWhenGetListUpstreamJobs() {
        decorator.listUpstreamJobs("j", 42);

        assertThat(decorator.toPrettyString(), matchesPattern(compile(".*find:.*count=1.*write:.*count=0.*Cache.*", DOTALL)));

        verify(delegate).listUpstreamJobs("j", 42);
    }

    @Test
    public void shoudIncrementReadWhenGetListTransitiveUpstreamJobs() {
        decorator.listTransitiveUpstreamJobs("j", 42);

        assertThat(decorator.toPrettyString(), matchesPattern(compile(".*find:.*count=1.*write:.*count=0.*Cache.*", DOTALL)));

        verify(delegate).listTransitiveUpstreamJobs("j", 42);
    }

    @Test
    public void shoudIncrementWriteWhenCleanup() {
        decorator.cleanup();

        assertThat(decorator.toPrettyString(), matchesPattern(compile(".*find:.*count=0.*write:.*count=1.*Cache.*", DOTALL)));

        verify(delegate).cleanup();
    }

}
