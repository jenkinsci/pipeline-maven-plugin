package org.jenkinsci.plugins.pipeline.maven.dao;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Short living memory for upstreams calculation. This is important for the
 * performance of PipelineMavenPluginDao.listTransitiveUpstreamJobs()
 *
 * This is no permanent cache, this instance is only used inside
 * DownstreamPipelineTriggerRunListener#onCompleted() as a local variable.
 *
 * @author Martin Aubele
 *
 */
public class UpstreamMemory {

    private static final AtomicInteger HITS = new AtomicInteger();
    private static final AtomicInteger MISSES = new AtomicInteger();

    static {
        MonitoringPipelineMavenPluginDaoDecorator.registerCacheStatsSupplier(
                () -> new CacheStats("listUpstreamJobs", HITS.get(), MISSES.get()));
    }

    // remember the already known upstreams
    private Map<String, Map<String, Integer>> upstreams = new HashMap<>();

    public Map<String, Integer> listUpstreamJobs(PipelineMavenPluginDao dao, String jobFullName, int buildNumber) {
        String key = jobFullName + '#' + buildNumber;
        if (upstreams.containsKey(key)) {
            HITS.incrementAndGet();
        } else {
            MISSES.incrementAndGet();
        }
        return upstreams.computeIfAbsent(key, k -> dao.listUpstreamJobs(jobFullName, buildNumber));
    }
}
