package org.jenkinsci.plugins.pipeline.maven.util;

import com.cloudbees.hudson.plugins.folder.computed.FolderComputation;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;

import javax.annotation.Nonnull;

import static org.junit.Assert.fail;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class WorkflowMultibranchProjectTestsUtils {
    /**
     *
     * @see org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProjectTest#scheduleAndFindBranchProject
     */
    @Nonnull
    public static WorkflowJob scheduleAndFindBranchProject(@Nonnull WorkflowMultiBranchProject mp, @Nonnull String name) throws Exception {
        mp.scheduleBuild2(0).getFuture().get();
        return findBranchProject(mp, name);
    }

    /**
     * @see org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProjectTest#findBranchProject
     */
    @Nonnull
    public static WorkflowJob findBranchProject(@Nonnull WorkflowMultiBranchProject mp, @Nonnull String name) throws Exception {
        WorkflowJob p = mp.getItem(name);
        showIndexing(mp);
        if (p == null) {
            fail(name + " project not found");
        }
        return p;
    }

    /**
     * @see org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProjectTest#showIndexing
     */
    static void showIndexing(@Nonnull WorkflowMultiBranchProject mp) throws Exception {
        FolderComputation<?> indexing = mp.getIndexing();
        System.out.println("---%<--- " + indexing.getUrl());
        indexing.writeWholeLogTo(System.out);
        System.out.println("---%<--- ");
    }
}
