package org.jenkinsci.plugins.maven;

import java.time.Duration;
import org.jenkinsci.test.acceptance.po.Control;
import org.jenkinsci.test.acceptance.po.Jenkins;
import org.jenkinsci.test.acceptance.po.WorkflowJob;
import org.openqa.selenium.By;

public class WithMavenSnippetGenerator extends SnippetGenerator {

    private static final String WITH_MAVEN_OPTION = "withMaven: Provide Maven environment";
    private final Control selectSampleStep = control("/");

    public WithMavenSnippetGenerator(final Jenkins jenkins) {
        super(jenkins);
    }

    public WithMavenSnippetGenerator(final WorkflowJob context) {
        super(context);
    }

    public WithMaven selectWithMaven() {
        selectSampleStep.select(WITH_MAVEN_OPTION);
        waitFor()
                .withTimeout(Duration.ofSeconds(5))
                .until(
                        find(By.xpath("//div[contains(@class, 'jenkins-form-label') and text() = 'withMaven']"))
                                ::isDisplayed);

        return new WithMaven(this, "/prototype");
    }
}
