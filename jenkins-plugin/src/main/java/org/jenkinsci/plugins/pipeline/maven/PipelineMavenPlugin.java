package org.jenkinsci.plugins.pipeline.maven;

import hudson.Extension;
import hudson.Plugin;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.pipeline.maven.dao.PipelineMavenPluginDao;
import org.jenkinsci.plugins.pipeline.maven.dao.PipelineMavenPluginH2Dao;

import java.io.Closeable;
import java.util.logging.Logger;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
@Extension
public class PipelineMavenPlugin extends Plugin {
    private final static Logger LOGGER = Logger.getLogger(PipelineMavenPlugin.class.getName());

    private PipelineMavenPluginDao dao;

    @Override
    public void start() throws Exception {
        super.start();
    }

    @Override
    public void postInitialize() throws Exception {
        super.postInitialize();
        this.dao = new PipelineMavenPluginH2Dao(Jenkins.getInstance().getRootDir());
    }

    @Override
    public void stop() throws Exception {
        super.stop();

        if (dao instanceof Closeable) {
            ((Closeable) dao).close();
        }
    }
}
