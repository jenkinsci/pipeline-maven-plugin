package org.jenkinsci.plugins.pipeline.maven.dao;

import com.google.common.annotations.VisibleForTesting;

import javax.annotation.Nonnull;
import javax.sql.DataSource;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public interface PipelineMavenPluginJdbcDao extends PipelineMavenPluginDao {

    @Nonnull
    @VisibleForTesting
    DataSource getDataSource();

}
