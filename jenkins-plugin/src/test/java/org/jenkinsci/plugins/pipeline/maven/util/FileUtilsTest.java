package org.jenkinsci.plugins.pipeline.maven.util;

import org.hamcrest.CoreMatchers;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class FileUtilsTest {

    @Test
    public void test_isAbsolutePath_with_windows_absolute_path() {
        Assert.assertThat(FileUtils.isAbsolutePath("c:\\jenkins\\workspace\\"), Matchers.is(true));
    }

    @Test
    public void test_isAbsolutePath_with_windows_relative_path() {
        Assert.assertThat(FileUtils.isAbsolutePath("jenkins\\workspace\\"), Matchers.is(false));
    }

    @Test
    public void test_isAbsolutePath_with_linux_absolute_path() {
        Assert.assertThat(FileUtils.isAbsolutePath("/var/lib/jenkins/workspace"), Matchers.is(true));
    }

    @Test
    public void test_isAbsolutePath_with_linux_relative_path() {
        Assert.assertThat(FileUtils.isAbsolutePath("jenkins/workspace"), Matchers.is(false));
    }

    @Test
    public void test_isAbsolutePath_with_windows_unc_absolute_path() {
        Assert.assertThat(FileUtils.isAbsolutePath("\\\\myserver\\jenkins\\workspace\\"), Matchers.is(true));
    }
}
