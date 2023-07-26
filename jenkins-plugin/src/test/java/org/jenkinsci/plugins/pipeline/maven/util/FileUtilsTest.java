package org.jenkinsci.plugins.pipeline.maven.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class FileUtilsTest {

    @Test
    public void test_isAbsolutePath_with_windows_absolute_path() {
        assertThat(FileUtils.isAbsolutePath("c:\\jenkins\\workspace\\")).isTrue();
    }

    @Test
    public void test_isAbsolutePath_with_windows_relative_path() {
        assertThat(FileUtils.isAbsolutePath("jenkins\\workspace\\")).isFalse();
    }

    @Test
    public void test_isAbsolutePath_with_linux_absolute_path() {
        assertThat(FileUtils.isAbsolutePath("/var/lib/jenkins/workspace")).isTrue();
    }

    @Test
    public void test_isAbsolutePath_with_linux_relative_path() {
        assertThat(FileUtils.isAbsolutePath("jenkins/workspace")).isFalse();
    }

    @Test
    public void test_isAbsolutePath_with_windows_unc_absolute_path() {
        assertThat(FileUtils.isAbsolutePath("\\\\myserver\\jenkins\\workspace\\")).isTrue();
    }
}
