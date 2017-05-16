package org.jenkinsci.plugins.pipeline.maven;

import hudson.DescriptorExtensionList;
import hudson.ExtensionPoint;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.TaskListener;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.kohsuke.stapler.DataBoundSetter;
import org.w3c.dom.Element;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Experimental interface, likely to change in the future.
 *
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public abstract class MavenReporter extends AbstractDescribableImpl<MavenReporter> implements ExtensionPoint, Comparable<MavenReporter>, Serializable {

    private final static Logger LOGGER = Logger.getLogger(MavenReporter.class.getName());

    private boolean disabled;

    public boolean isDisabled() {
        return disabled;
    }

    @DataBoundSetter
    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }

    /**
     * @param context
     * @param mavenSpyLogsElt maven spy report. WARNING experimental structure for the moment, subject to change.
     * @throws IOException
     * @throws InterruptedException
     */
    public abstract void process(@Nonnull StepContext context, @Nonnull Element mavenSpyLogsElt) throws IOException, InterruptedException;

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Override
    public int compareTo(MavenReporter o) {
        return this.getDescriptor().compareTo(o.getDescriptor());
    }

    @Override
    public String toString() {
        return getClass().getName() + "[" +
                "disabled=" + disabled +
                ']';
    }

    public static abstract class DescriptorImpl extends Descriptor<MavenReporter> implements Comparable<DescriptorImpl> {
        /**
         *
         * @return the ordinal of this reporter to execute reporters in predictable order
         * @see #compareTo(MavenReporter)
         */
        public int ordinal() {
            return 100;
        }

        /**
         * Name of the marker file used to skip the maven reporter
         *
         * @return name of the marker file. {@code null} if no marker file is defined for this reporter
         */
        @Nullable
        abstract public String getSkipFileName();


        @Override
        public int compareTo(DescriptorImpl o) {
            int compare = Integer.compare(this.ordinal(), o.ordinal());

            if (compare == 0) {
                compare = this.getId().compareTo(o.getId());
            }

            return compare;
        }
    }

    @Nonnull
    public static List<MavenReporter> buildReportersList(@Nonnull List<MavenReporter> configuredReporters, @Nonnull TaskListener listener){

        // mavenReporter.descriptor.id -> mavenReporter
        Map<String, MavenReporter> configuredReportersById = new HashMap<>();
        for (MavenReporter mavenReporter : configuredReporters) {
            if (mavenReporter == null) {
                // skipp null reporter injected by Jenkins pipeline for an unknown reason
            } else {
                configuredReportersById.put(mavenReporter.getDescriptor().getId(), mavenReporter);
            }
        }

        // mavenReporter.descriptor.id -> mavenRepoer
        Map<String, MavenReporter> defaultReportersById = new HashMap<>();
        DescriptorExtensionList<MavenReporter, Descriptor<MavenReporter>> descriptorList = Jenkins.getInstance().getDescriptorList(MavenReporter.class);
        for (Descriptor<MavenReporter> descriptor:descriptorList) {
            if (configuredReportersById.containsKey(descriptor.getId())) {
                // skip, already provided with a configuration
            } else {
                try {
                    defaultReportersById.put(descriptor.getId(), descriptor.clazz.newInstance());
                } catch (InstantiationException | IllegalAccessException e) {
                    PrintWriter error = listener.error("[withMaven] Exception instantiation default config for Maven Reporter '" + descriptor.getDisplayName() + "' / " + descriptor.getId() + ": " + e);
                    e.printStackTrace(error);
                    error.close();
                    LOGGER.log(Level.WARNING, "Exception instantiating " + descriptor.clazz + ": " + e, e);
                    e.printStackTrace();
                }
            }
        }
        if (LOGGER.isLoggable(Level.FINE)) {
            listener.getLogger().println("[withMaven] Maven Reporters with configuration provided by the pipeline: " + configuredReportersById);
            listener.getLogger().println("[withMaven] Maven Reporters with default configuration: " + defaultReportersById);
        }

        List<MavenReporter> results = new ArrayList<>();
        results.addAll(configuredReportersById.values());
        results.addAll(defaultReportersById.values());
        Collections.sort(results);
        return results;
    }
}