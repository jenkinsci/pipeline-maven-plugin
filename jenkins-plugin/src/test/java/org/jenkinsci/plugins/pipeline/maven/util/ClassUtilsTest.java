package org.jenkinsci.plugins.pipeline.maven.util;

import org.junit.Test;

import java.io.InputStream;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class ClassUtilsTest {

    @Test
    public void testGetResource(){
        InputStream in = ClassUtils.getResourceAsStream("org/jenkinsci/plugins/pipeline/maven/util/classutils-test-1.txt");


    }
}
