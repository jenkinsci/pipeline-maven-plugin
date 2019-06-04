package org.jenkinsci.plugins.pipeline.maven;

import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class WithMavenStepExecution2Test {

    @Test
    @Issue("JENKINS-57324")
    public void testEscapeWindowsBatchChars(){

        String mavenConfig ="--batch-mode --show-version " +
                "--settings \"e:\\folder\\branches%2Ftest\\workspace@tmp\\withMaven94865076\\settings.xml\" " +
                "--global-settings \"e:\\folder\\branches%2Ftest\\workspace@tmp\\withMaven94865076\\globalSettings.xml\"";

       String actualEscapedMavenConfig = mavenConfig.replace("%", "%%");
       String expectedEscapedMavenConfig = "--batch-mode --show-version " +
               "--settings \"e:\\folder\\branches%%2Ftest\\workspace@tmp\\withMaven94865076\\settings.xml\" " +
               "--global-settings \"e:\\folder\\branches%%2Ftest\\workspace@tmp\\withMaven94865076\\globalSettings.xml\"";
        System.out.println("Expected escaped mavenConfig: " + expectedEscapedMavenConfig);
        Assert.assertThat(actualEscapedMavenConfig, Matchers.is(expectedEscapedMavenConfig));

    }
}
