package org.jenkinsci.plugins.pipeline.maven.fix.jenkins49337;

import hudson.util.DaemonThreadFactory;
import hudson.util.NamingThreadFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class GeneralNonBlockingStepExecutionUtils {
    private static ExecutorService executorService;

    /**
     * Workaround visibility restriction of {@code org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution#getExecutorService()}
     */
    static synchronized ExecutorService getExecutorService() {
        if (executorService == null) {
            executorService = Executors.newCachedThreadPool(new NamingThreadFactory(new DaemonThreadFactory(), "org.jenkinsci.plugins.pipeline.maven.fix.jenkins49337.GeneralNonBlockingStepExecution"));
        }
        return executorService;
    }
}
