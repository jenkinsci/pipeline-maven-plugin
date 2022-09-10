package org.jenkinsci.plugins.pipeline.maven;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.jenkinsci.plugins.pipeline.maven.dao.PipelineMavenPluginDao;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class NonProductionGradeDatabaseWarningAdministrativeMonitorTest {

    @InjectMocks
    private NonProductionGradeDatabaseWarningAdministrativeMonitor monitor;

    @Mock
    private GlobalPipelineMavenConfig config;

    @Mock
    private PipelineMavenPluginDao dao;

    @Test
    public void test_is_activated_no_config_and_uninitialized() {
        try (MockedStatic<GlobalPipelineMavenConfig> c = mockStatic(GlobalPipelineMavenConfig.class)) {
            c.when(GlobalPipelineMavenConfig::get).thenReturn(config);
            when(config.getJdbcUrl()).thenReturn(null);
            when(config.isDaoInitialized()).thenReturn(false);

            assertThat(monitor.isActivated(), is(false));

            verify(config).getJdbcUrl();
            verify(config).isDaoInitialized();
            verifyNoMoreInteractions(config, dao);
        }
    }

    @Test
    public void test_is_activated_empty_config_and_uninitialized() {
        try (MockedStatic<GlobalPipelineMavenConfig> c = mockStatic(GlobalPipelineMavenConfig.class)) {
            c.when(GlobalPipelineMavenConfig::get).thenReturn(config);
            when(config.getJdbcUrl()).thenReturn("");
            when(config.isDaoInitialized()).thenReturn(false);

            assertThat(monitor.isActivated(), is(false));

            verify(config).getJdbcUrl();
            verify(config).isDaoInitialized();
            verifyNoMoreInteractions(config, dao);
        }
    }

    @Test
    public void test_is_activated_no_config_and_initialized_non_production() {
        try (MockedStatic<GlobalPipelineMavenConfig> c = mockStatic(GlobalPipelineMavenConfig.class)) {
            c.when(GlobalPipelineMavenConfig::get).thenReturn(config);
            when(config.getJdbcUrl()).thenReturn(null);
            when(config.isDaoInitialized()).thenReturn(true);
            when(config.getDao()).thenReturn(dao);
            when(dao.isEnoughProductionGradeForTheWorkload()).thenReturn(false);

            assertThat(monitor.isActivated(), is(true));

            verify(config).getJdbcUrl();
            verify(config).getDao();
            verify(config).isDaoInitialized();
            verify(dao).isEnoughProductionGradeForTheWorkload();
            verifyNoMoreInteractions(config, dao);
        }
    }

    @Test
    public void test_is_activated_empty_config_and_initialized_non_production() {
        try (MockedStatic<GlobalPipelineMavenConfig> c = mockStatic(GlobalPipelineMavenConfig.class)) {
            c.when(GlobalPipelineMavenConfig::get).thenReturn(config);
            when(config.getJdbcUrl()).thenReturn("");
            when(config.isDaoInitialized()).thenReturn(true);
            when(config.getDao()).thenReturn(dao);
            when(dao.isEnoughProductionGradeForTheWorkload()).thenReturn(false);

            assertThat(monitor.isActivated(), is(true));

            verify(config).getJdbcUrl();
            verify(config).getDao();
            verify(config).isDaoInitialized();
            verify(dao).isEnoughProductionGradeForTheWorkload();
            verifyNoMoreInteractions(config, dao);
        }
    }

    @Test
    public void test_is_activated_no_config_and_initialized_production() {
        try (MockedStatic<GlobalPipelineMavenConfig> c = mockStatic(GlobalPipelineMavenConfig.class)) {
            c.when(GlobalPipelineMavenConfig::get).thenReturn(config);
            when(config.getJdbcUrl()).thenReturn(null);
            when(config.isDaoInitialized()).thenReturn(true);
            when(config.getDao()).thenReturn(dao);
            when(dao.isEnoughProductionGradeForTheWorkload()).thenReturn(true);

            assertThat(monitor.isActivated(), is(false));

            verify(config).getJdbcUrl();
            verify(config).getDao();
            verify(config).isDaoInitialized();
            verify(dao).isEnoughProductionGradeForTheWorkload();
            verifyNoMoreInteractions(config, dao);
        }
    }

    @Test
    public void test_is_activated_empty_config_and_initialized_production() {
        try (MockedStatic<GlobalPipelineMavenConfig> c = mockStatic(GlobalPipelineMavenConfig.class)) {
            c.when(GlobalPipelineMavenConfig::get).thenReturn(config);
            when(config.getJdbcUrl()).thenReturn("");
            when(config.isDaoInitialized()).thenReturn(true);
            when(config.getDao()).thenReturn(dao);
            when(dao.isEnoughProductionGradeForTheWorkload()).thenReturn(true);

            assertThat(monitor.isActivated(), is(false));

            verify(config).getJdbcUrl();
            verify(config).getDao();
            verify(config).isDaoInitialized();
            verify(dao).isEnoughProductionGradeForTheWorkload();
            verifyNoMoreInteractions(config, dao);
        }
    }

    @Test
    public void test_is_activated_with_h2_config() {
        try (MockedStatic<GlobalPipelineMavenConfig> c = mockStatic(GlobalPipelineMavenConfig.class)) {
            c.when(GlobalPipelineMavenConfig::get).thenReturn(config);
            when(config.getJdbcUrl()).thenReturn("jdbc:h2:some url");

            assertThat(monitor.isActivated(), is(true));

            verify(config).getJdbcUrl();
            verifyNoMoreInteractions(config, dao);
        }
    }

    @Test
    public void test_is_activated_with_mysql_config() {
        try (MockedStatic<GlobalPipelineMavenConfig> c = mockStatic(GlobalPipelineMavenConfig.class)) {
            c.when(GlobalPipelineMavenConfig::get).thenReturn(config);
            when(config.getJdbcUrl()).thenReturn("jdbc:mysql:some url");

            assertThat(monitor.isActivated(), is(false));

            verify(config).getJdbcUrl();
            verifyNoMoreInteractions(config, dao);
        }
    }

    @Test
    public void test_is_activated_with_postgresql_config() {
        try (MockedStatic<GlobalPipelineMavenConfig> c = mockStatic(GlobalPipelineMavenConfig.class)) {
            c.when(GlobalPipelineMavenConfig::get).thenReturn(config);
            when(config.getJdbcUrl()).thenReturn("jdbc:postgresql:some url");

            assertThat(monitor.isActivated(), is(false));

            verify(config).getJdbcUrl();
            verifyNoMoreInteractions(config, dao);
        }
    }
}
