package org.jenkinsci.plugins.maven;

import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.test.acceptance.po.Jenkins;
import org.jenkinsci.test.acceptance.po.PageObject;
import org.jenkinsci.test.acceptance.po.WorkflowJob;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

public class SnippetGenerator extends PageObject {

    private static final String URI = "pipeline-syntax/";

    public SnippetGenerator(Jenkins jenkins) {
        super(jenkins.injector, jenkins.url(URI));
    }

    public SnippetGenerator(final WorkflowJob context) {
        super(context, context.url(URI));
    }

    public String generateScript() {
        WebElement generateButton = find(By.id("generatePipelineScript"));
        generateButton.click();

        WebElement snippet = find(By.id("prototypeText"));
        waitFor().until(() -> StringUtils.isNotBlank(snippet.getAttribute("value")));

        return StringUtils.defaultString(snippet.getAttribute("value"));
    }
}
