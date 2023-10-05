package org.jenkinsci.plugins.pipeline.maven.listeners;

import hudson.Extension;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.listeners.ItemListener;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.pipeline.maven.GlobalPipelineMavenConfig;
import org.jenkinsci.plugins.workflow.flow.BlockableResume;

/**
 * Maintains the database in sync with the jobs and builds.
 *
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
@Extension
public class DatabaseSyncItemListener extends ItemListener {
    private static final Logger LOGGER = Logger.getLogger(DatabaseSyncItemListener.class.getName());

    @Inject
    public GlobalPipelineMavenConfig globalPipelineMavenConfig;

    @Override
    public void onDeleted(Item item) {
        if (item instanceof BlockableResume) {
            LOGGER.log(Level.FINE, "onDeleted({0})", item);
            globalPipelineMavenConfig.getDao().deleteJob(item.getFullName());
        } else {
            LOGGER.log(Level.FINE, "Ignore onDeleted({0})", new Object[] {item});
        }
    }

    @Override
    public void onRenamed(Item item, String oldName, String newName) {
        if (item instanceof BlockableResume) {
            LOGGER.log(Level.FINE, "onRenamed({0}, {1}, {2})", new Object[] {item, oldName, newName});

            String oldFullName;
            ItemGroup parent = item.getParent();
            if (parent.equals(Jenkins.get())) {
                oldFullName = oldName;
            } else {
                oldFullName = parent.getFullName() + "/" + oldName;
            }
            String newFullName = item.getFullName();
            globalPipelineMavenConfig.getDao().renameJob(oldFullName, newFullName);
        } else {
            LOGGER.log(Level.FINE, "Ignore onRenamed({0}, {1}, {2})", new Object[] {item, oldName, newName});
        }
    }

    @Override
    public void onLocationChanged(Item item, String oldFullName, String newFullName) {
        if (item instanceof BlockableResume) {
            LOGGER.log(Level.FINE, "onLocationChanged({0}, {1}, {2})", new Object[] {item, oldFullName, newFullName});
            globalPipelineMavenConfig.getDao().renameJob(oldFullName, newFullName);
        } else {
            LOGGER.log(
                    Level.FINE, "Ignore onLocationChanged({0}, {1}, {2})", new Object[] {item, oldFullName, newFullName
                    });
        }
    }
}
