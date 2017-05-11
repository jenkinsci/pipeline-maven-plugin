package org.jenkinsci.plugins.pipeline.maven;

import hudson.model.Run;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class TestUtils {

    public static Collection<String> artifactsToArtifactsFileNames(Iterable<Run<WorkflowJob, WorkflowRun>.Artifact> artifacts){
        List<String> result = new ArrayList<>();
        for(Run.Artifact artifact:artifacts) {
            result.add(artifact.getFileName());
        }
        return result;
    }
}
