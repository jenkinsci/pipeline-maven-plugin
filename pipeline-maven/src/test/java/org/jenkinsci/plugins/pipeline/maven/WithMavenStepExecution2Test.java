package org.jenkinsci.plugins.pipeline.maven;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class WithMavenStepExecution2Test {

    @Test
    @Issue("JENKINS-57324")
    public void testEscapeWindowsBatchChars() {

        // @formatter:off
        String mavenConfig = "--batch-mode --show-version " +
                "--settings \"e:\\folder\\branches%2Ftest\\workspace@tmp\\withMaven94865076\\settings.xml\" " +
                "--global-settings \"e:\\folder\\branches%2Ftest\\workspace@tmp\\withMaven94865076\\globalSettings.xml\"";
        // @formatter:on

        String actualEscapedMavenConfig = mavenConfig.replace("%", "%%");
        // @formatter:off
        String expectedEscapedMavenConfig = "--batch-mode --show-version "
                + "--settings \"e:\\folder\\branches%%2Ftest\\workspace@tmp\\withMaven94865076\\settings.xml\" "
                + "--global-settings \"e:\\folder\\branches%%2Ftest\\workspace@tmp\\withMaven94865076\\globalSettings.xml\"";
        // formatter:on

        System.out.println("Expected escaped mavenConfig: " + expectedEscapedMavenConfig);

        assertThat(actualEscapedMavenConfig).isEqualTo(expectedEscapedMavenConfig);
    }

    @Test
    public void testIsWithContainerLauncherUnknown() {
        assertThat(WithMavenStepExecution2.isWithContainerLauncher("hudson.SomeUnknownLauncher")).isEmpty();
    }

    @Test
    public void testIsWithContainerLauncherWithContainerStep() {
        assertThat(WithMavenStepExecution2.isWithContainerLauncher("org.jenkinsci.plugins.docker.workflow.WithContainerStep.Decorator")).contains(true);
    }

    @Test
    public void testIsWithContainerLauncherKubernetesPluginContainerExecDecorator() {
        assertThat(WithMavenStepExecution2.isWithContainerLauncher("org.csanchez.jenkins.plugins.kubernetes.pipeline.ContainerExecDecorator")).contains(false);
    }

    @Test
    public void testIsWithContainerLauncherCustomContainerExecDecorator() {
        // for plugins that supporting launching their own containerized steps
        assertThat(WithMavenStepExecution2.isWithContainerLauncher("com.jenkins.plugins.custom.ContainerExecDecorator")).contains(true);
        assertThat(WithMavenStepExecution2.isWithContainerLauncher("com.jenkins.plugins.kubernetes.pipeline.MyContainerExecDecorator.MyContainerDecoratedLauncher")).contains(true);
    }
}
