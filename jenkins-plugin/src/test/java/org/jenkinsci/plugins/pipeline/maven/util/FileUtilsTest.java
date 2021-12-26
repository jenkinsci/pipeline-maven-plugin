package org.jenkinsci.plugins.pipeline.maven.util;

import org.hamcrest.Matchers;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class FileUtilsTest {

    @Test
    public void test_isAbsolutePath_with_windows_absolute_path() {
        assertThat(FileUtils.isAbsolutePath("c:\\jenkins\\workspace\\"), Matchers.is(true));
    }

    @Test
    public void test_isAbsolutePath_with_windows_relative_path() {
        assertThat(FileUtils.isAbsolutePath("jenkins\\workspace\\"), Matchers.is(false));
    }

    @Test
    public void test_isAbsolutePath_with_linux_absolute_path() {
        assertThat(FileUtils.isAbsolutePath("/var/lib/jenkins/workspace"), Matchers.is(true));
    }

    @Test
    public void test_isAbsolutePath_with_linux_relative_path() {
        assertThat(FileUtils.isAbsolutePath("jenkins/workspace"), Matchers.is(false));
    }

    @Test
    public void test_isAbsolutePath_with_windows_unc_absolute_path() {
        assertThat(FileUtils.isAbsolutePath("\\\\myserver\\jenkins\\workspace\\"), Matchers.is(true));
    }
}
