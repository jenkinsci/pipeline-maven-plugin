package org.jenkinsci.plugins.pipeline.maven;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import com.cloudbees.hudson.plugins.folder.Folder;
import org.htmlunit.html.HtmlButton;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.html.HtmlSelect;
import org.jenkinsci.plugins.configfiles.GlobalConfigFiles;
import org.jenkinsci.plugins.configfiles.maven.GlobalMavenSettingsConfig;
import org.jenkinsci.plugins.configfiles.maven.job.MvnGlobalSettingsProvider;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

public class MavenConfigFolderOverridePropertyTest {

    @WithJenkins
    @Test
    public void testConfigRoundTrip(JenkinsRule r) throws Exception {

        GlobalMavenSettingsConfig mavenGlobalSettingsConfig1 =
                new GlobalMavenSettingsConfig("maven-global-config-test-folder1", "name 1", "", "");
        GlobalConfigFiles.get().save(mavenGlobalSettingsConfig1);
        GlobalMavenSettingsConfig mavenGlobalSettingsConfig2 =
                new GlobalMavenSettingsConfig("maven-global-config-test-folder2", "name 2", "", "");
        GlobalConfigFiles.get().save(mavenGlobalSettingsConfig2);

        final Folder folder1 = r.createProject(Folder.class, "folder1");
        MavenConfigFolderOverrideProperty configOverrideProperty = new MavenConfigFolderOverrideProperty();
        configOverrideProperty.setOverride(true);
        configOverrideProperty.setGlobalSettings(new MvnGlobalSettingsProvider(mavenGlobalSettingsConfig1.id));
        folder1.addProperty(configOverrideProperty);

        JenkinsRule.WebClient webClient = r.createWebClient();
        HtmlPage page = webClient.getPage(folder1, "configure");
        HtmlForm form = page.getFormByName("config");

        HtmlSelect select = form.getSelectByName("_.settingsConfigId");
        assertThat(select.getOptions().size(), equalTo(3));
        assertThat(select.getOption(0).getText(), equalTo("please select"));
        assertThat(select.getOption(1).getText(), equalTo("name 1"));
        assertThat(select.getOption(2).getText(), equalTo("name 2"));
        assertThat(select.getSelectedOptions().get(0).getText(), equalTo("name 1"));

        select.setSelectedIndex(2);
        final HtmlButton button = form.getButtonByName("Submit");
        form.submit(button);

        page = webClient.getPage(folder1, "configure");
        form = page.getFormByName("config");
        select = form.getSelectByName("_.settingsConfigId");
        assertThat(select.getOptions().size(), equalTo(3));
        assertThat(select.getOption(0).getText(), equalTo("please select"));
        assertThat(select.getOption(1).getText(), equalTo("name 1"));
        assertThat(select.getOption(2).getText(), equalTo("name 2"));
        assertThat(select.getSelectedOptions().get(0).getText(), equalTo("name 2"));
    }
}
