package org.jenkinsci.plugins.pipeline.maven.listeners;

import hudson.Extension;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.listeners.ItemListener;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.pipeline.maven.GlobalPipelineMavenConfig;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
@Extension
public class PipelineMavenPluginItemListener extends ItemListener {
    private final static Logger LOGGER = Logger.getLogger(PipelineMavenPluginItemListener.class.getName());

    @Override
    public void onDeleted(Item item) {
        if (item instanceof WorkflowJob) {
            WorkflowJob pipeline = (WorkflowJob) item;
            onDeleted(pipeline);
        } else {
            LOGGER.log(Level.FINE, "Ignore onDeleted({0})", new Object[]{item});
        }
    }

    public void onDeleted(WorkflowJob pipeline) {
        LOGGER.log(Level.FINE, "onDeleted({0})", pipeline);
        GlobalPipelineMavenConfig.getDao().deleteJob(pipeline.getFullName());
    }

    @Override
    public void onRenamed(Item item, String oldName, String newName) {
        if (item instanceof WorkflowJob) {
            WorkflowJob pipeline = (WorkflowJob) item;
            onRenamed(pipeline, oldName, newName);
        } else {
            LOGGER.log(Level.FINE, "Ignore onRenamed({0}, {1}, {2})", new Object[]{item, oldName, newName});
        }
    }

    public void onRenamed(WorkflowJob pipeline, String oldName, String newName) {
        LOGGER.log(Level.FINE, "onRenamed({0}, {1}, {2})", new Object[]{pipeline, oldName, newName});

        String oldFullName;
        ItemGroup parent = pipeline.getParent();
        if (parent.equals(Jenkins.getInstance())) {
            oldFullName = oldName;
        } else {
            oldFullName = parent.getFullName() + "/" + oldName;
        }
        String newFullName = pipeline.getFullName();
        GlobalPipelineMavenConfig.getDao().renameJob(oldFullName, newFullName);
    }

    @Override
    public void onLocationChanged(Item item, String oldFullName, String newFullName) {
        if (item instanceof WorkflowJob) {
            WorkflowJob pipeline = (WorkflowJob) item;
            onLocationChanged(pipeline, oldFullName, newFullName);
        } else {
            LOGGER.log(Level.FINE, "Ignore onLocationChanged({0}, {1}, {2})", new Object[]{item, oldFullName, newFullName});
        }
    }

    public void onLocationChanged(WorkflowJob pipeline, String oldFullName, String newFullName) {
        LOGGER.log(Level.FINE, "onLocationChanged({0}, {1}, {2})", new Object[]{pipeline, oldFullName, newFullName});
        GlobalPipelineMavenConfig.getDao().renameJob(oldFullName, newFullName);
    }
}
