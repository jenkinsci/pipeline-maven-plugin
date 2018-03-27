package org.jenkinsci.plugins.pipeline.maven.publishers;

import hudson.plugins.analysis.core.HealthAwarePublisher;
import org.jenkinsci.plugins.pipeline.maven.MavenPublisher;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * @see hudson.plugins.analysis.core.HealthAwarePublisher
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public abstract class AbstractHealthAwarePublisher extends MavenPublisher {

    /**
     * Default threshold priority limit.
     */
    private static final String DEFAULT_PRIORITY_THRESHOLD_LIMIT = "low";

    /**
     * Report health as 100% when the number of warnings is less than this value.
     *
     * @see hudson.plugins.analysis.core.HealthAwareReporter#healthy
     */
    private String healthy = "";
    /**
     * Report health as 0% when the number of warnings is greater than this value.
     *
     * @see hudson.plugins.analysis.core.HealthAwareReporter#unHealthy
     */
    private String unHealthy = "";

    /**
     * Determines which warning priorities should be considered when evaluating the build health.
     *
     * @see hudson.plugins.analysis.core.HealthAwareReporter#thresholdLimit
     */
    private String thresholdLimit = DEFAULT_PRIORITY_THRESHOLD_LIMIT;


    public String getHealthy() {
        return healthy;
    }

    @DataBoundSetter
    public void setHealthy(String healthy) {
        this.healthy = healthy;
    }

    public String getUnHealthy() {
        return unHealthy;
    }

    @DataBoundSetter
    public void setUnHealthy(String unHealthy) {
        this.unHealthy = unHealthy;
    }

    public String getThresholdLimit() {
        return thresholdLimit;
    }

    @DataBoundSetter
    public void setThresholdLimit(String thresholdLimit) {
        this.thresholdLimit = thresholdLimit;
    }

    protected void setHealthAwarePublisherAttributes(HealthAwarePublisher healthAwarePublisher) {
        healthAwarePublisher.setHealthy(this.healthy);
        healthAwarePublisher.setUnHealthy(this.unHealthy);
        healthAwarePublisher.setThresholdLimit(this.thresholdLimit);
    }

    /**
     * Required by org/jenkinsci/plugins/pipeline/maven/publishers/AbstractHealthAwarePublisher/health.jelly
     */
    public static abstract class DescriptorImpl extends MavenPublisher.DescriptorImpl  {

    }


}