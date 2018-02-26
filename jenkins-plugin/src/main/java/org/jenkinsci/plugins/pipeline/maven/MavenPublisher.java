package org.jenkinsci.plugins.pipeline.maven;

import hudson.ExtensionPoint;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.kohsuke.stapler.DataBoundSetter;
import org.w3c.dom.Element;

import java.io.IOException;
import java.io.Serializable;
import java.util.logging.Logger;

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


}