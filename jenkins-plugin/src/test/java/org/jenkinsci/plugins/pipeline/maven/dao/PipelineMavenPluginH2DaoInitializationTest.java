package org.jenkinsci.plugins.pipeline.maven.dao;

import org.h2.jdbcx.JdbcConnectionPool;
import org.junit.Test;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class PipelineMavenPluginH2DaoInitializationTest {

    @Test
    public void initialize_database(){
        JdbcConnectionPool jdbcConnectionPool = JdbcConnectionPool.create("jdbc:h2:mem:", "sa", "");

        PipelineMavenPluginH2Dao dao  = new PipelineMavenPluginH2Dao(jdbcConnectionPool);
    }
}
