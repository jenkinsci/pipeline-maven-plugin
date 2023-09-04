package org.jenkinsci.plugins.pipeline.maven.db.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jenkinsci.plugins.pipeline.maven.db.util.ClassUtils.getResourceAsStream;

import org.junit.jupiter.api.Test;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class ClassUtilsTest {

    @Test
    public void testGetResource() {
        assertThat(getResourceAsStream("org/jenkinsci/plugins/pipeline/maven/util/classutils-test-1.txt")).isNotNull();
    }
}
