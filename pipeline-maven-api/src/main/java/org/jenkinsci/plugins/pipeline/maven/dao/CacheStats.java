package org.jenkinsci.plugins.pipeline.maven.dao;

public class CacheStats {

    private String name;

    private int hits;

    private int misses;

    public CacheStats(String name, int hits, int misses) {
        this.name = name;
        this.hits = hits;
        this.misses = misses;
    }

    public String getName() {
        return name;
    }

    public int getHits() {
        return hits;
    }

    public int getMisses() {
        return misses;
    }
}
