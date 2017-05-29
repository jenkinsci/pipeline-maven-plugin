package org.jenkinsci.plugins.pipeline.maven;

import hudson.DescriptorExtensionList;
import hudson.ExtensionPoint;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.TaskListener;
import jenkins.model.Jenkins;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.beanutils.BeanUtilsBean2;
import org.apache.commons.beanutils.PropertyUtils;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.kohsuke.stapler.DataBoundSetter;
import org.w3c.dom.Element;

import java.beans.PropertyDescriptor;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Experimental interface, likely to change in the future.
 *
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public abstract class MavenPublisher extends AbstractDescribableImpl<MavenPublisher> implements ExtensionPoint, Comparable<MavenPublisher>, Serializable {

    private final static Logger LOGGER = Logger.getLogger(MavenPublisher.class.getName());

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
    public int compareTo(MavenPublisher o) {
        return this.getDescriptor().compareTo(o.getDescriptor());
    }

    @Override
    public String toString() {
        return getClass().getName() + "[" +
                "disabled=" + disabled +
                ']';
    }

    public static abstract class DescriptorImpl extends Descriptor<MavenPublisher> implements Comparable<DescriptorImpl> {
        /**
         * @return the ordinal of this reporter to execute publishers in predictable order
         * @see #compareTo(MavenPublisher)
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

    /**
     * <p>Build the list of {@link MavenPublisher}s that should be invoked for the build execution of the given {@link TaskListener}
     * with the desired configuration.
     * </p>
     * <p>
     * The desired configuration is based on:
     * </p>
     * <ul>
     * <li>The default configuration of the publishers</li>
     * <li>The global configuration of the publishers defined in the "Global Tools Configuration' section</li>
     * <li>The configuration specified in the {@code withMaven(options=[...])} step</li>
     * </ul>
     *
     * @param configuredPublishers
     * @param listener
     */
    @Nonnull
    public static List<MavenPublisher> buildPublishersList(@Nonnull List<MavenPublisher> configuredPublishers, @Nonnull TaskListener listener) {

        // configuration passed as parameter of "withMaven(options=[...]){}"
        // mavenPublisher.descriptor.id -> mavenPublisher
        Map<String, MavenPublisher> configuredPublishersById = new HashMap<>();
        for (MavenPublisher mavenPublisher : configuredPublishers) {
            if (mavenPublisher == null) {
                // skip null publisher options injected by Jenkins pipeline, probably caused by a missing plugin
            } else {
                configuredPublishersById.put(mavenPublisher.getDescriptor().getId(), mavenPublisher);
            }
        }

        // configuration defined globally
        Map<String, MavenPublisher> globallyConfiguredPublishersById = new HashMap<>();
        GlobalPipelineMavenConfig globalPipelineMavenConfig = GlobalPipelineMavenConfig.get();

        List<MavenPublisher> globallyConfiguredPublishers = globalPipelineMavenConfig == null ? Collections.<MavenPublisher>emptyList() : globalPipelineMavenConfig.getPublisherOptions();
        if (globallyConfiguredPublishers == null) {
            globallyConfiguredPublishers = Collections.emptyList();
        }
        for (MavenPublisher mavenPublisher : globallyConfiguredPublishers) {
            globallyConfiguredPublishersById.put(mavenPublisher.getDescriptor().getId(), mavenPublisher);
        }


        // mavenPublisher.descriptor.id -> mavenPublisher
        Map<String, MavenPublisher> defaultPublishersById = new HashMap<>();
        DescriptorExtensionList<MavenPublisher, Descriptor<MavenPublisher>> descriptorList = Jenkins.getInstance().getDescriptorList(MavenPublisher.class);
        for (Descriptor<MavenPublisher> descriptor : descriptorList) {
            try {
                defaultPublishersById.put(descriptor.getId(), descriptor.clazz.newInstance());
            } catch (InstantiationException | IllegalAccessException e) {
                PrintWriter error = listener.error("[withMaven] Exception instantiation default config for Maven Publisher '" + descriptor.getDisplayName() + "' / " + descriptor.getId() + ": " + e);
                e.printStackTrace(error);
                error.close();
                LOGGER.log(Level.WARNING, "Exception instantiating " + descriptor.clazz + ": " + e, e);
                e.printStackTrace();
            }
        }


        if (LOGGER.isLoggable(Level.FINE)) {
            listener.getLogger().println("[withMaven] Maven Publishers with configuration provided by the pipeline: " + configuredPublishersById.values());
            listener.getLogger().println("[withMaven] Maven Publishers with configuration defined globally: " + globallyConfiguredPublishersById.values());
            listener.getLogger().println("[withMaven] Maven Publishers with default configuration: " + defaultPublishersById.values());
        }

        // TODO FILTER
        List<MavenPublisher> results = new ArrayList<>();
        for (Map.Entry<String, MavenPublisher> entry : defaultPublishersById.entrySet()) {
            String publisherId = entry.getKey();
            MavenPublisher publisher = buildConfiguredMavenPublisher(
                    configuredPublishersById.get(publisherId),
                    globallyConfiguredPublishersById.get(publisherId),
                    entry.getValue(),
                    listener);
            results.add(publisher);
        }
        Collections.sort(results);
        return results;
    }

    public static MavenPublisher buildConfiguredMavenPublisher(@Nullable MavenPublisher pipelinePublisher, @Nullable MavenPublisher globallyConfiguredPublisher, @Nonnull MavenPublisher defaultPublisher, @Nonnull TaskListener listener) {

        MavenPublisher result;
        String logMessage;

        if (pipelinePublisher == null && globallyConfiguredPublisher == null) {
            result = defaultPublisher;
            logMessage = "default";
        } else if (pipelinePublisher == null && globallyConfiguredPublisher != null) {
            result = globallyConfiguredPublisher;
            logMessage = "globally";
        } else if (pipelinePublisher != null && globallyConfiguredPublisher == null) {
            result = pipelinePublisher;
            logMessage = "pipeline";
        } else if (pipelinePublisher != null && globallyConfiguredPublisher != null)  {
            // workaround FindBugs "Bug kind and pattern: NP - NP_NULL_ON_SOME_PATH"
            // check pipelinePublisher and globallyConfiguredPublisher are non null even if it is useless

            result = pipelinePublisher;
            logMessage = "pipeline";
            listener.getLogger().println("[withMaven] WARNING merging publisher configuration defined in the 'Global Tool Configuration' and at the pipeline level is not yet supported." +
                    " Use pipeline level configuration for '" + result.getDescriptor().getDisplayName() + "'");
//
//            PropertyDescriptor[] propertyDescriptors = PropertyUtils.getPropertyDescriptors(defaultPublisher);
//            for(PropertyDescriptor propertyDescriptor: propertyDescriptors) {
//                Method readMethod = propertyDescriptor.getReadMethod();
//                Method writeMethod = propertyDescriptor.getWriteMethod();
//
//                Object defaultValue = readMethod.invoke(defaultPublisher);
//                Object globallyDefinedValue = readMethod.invoke(globallyConfiguredPublisher);
//                Object pipelineValue = readMethod.invoke(pipelinePublisher);
//            }
        } else {
            throw new IllegalStateException("Should not happen, workaround for Findbugs NP_NULL_ON_SOME_PATH above");
        }

        if (LOGGER.isLoggable(Level.FINE))
            listener.getLogger().println("[withMaven] Use " + logMessage + " defined publisher for '" + result.getDescriptor().getDisplayName() + "'");
        return result;

    }
}