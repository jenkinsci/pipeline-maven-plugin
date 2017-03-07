package org.jenkinsci.plugins.pipeline.maven.util;

import hudson.AbortException;
import org.junit.Test;

import java.io.File;
import java.net.URL;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class XmlUtilsTest {

    @Test
    public void checkFileIsaMavenSettingsFile_accepts_maven_settings_file() throws AbortException {
        checkMavenSettingsFile("org/jenkinsci/plugins/pipeline/maven/util/settings.xml");
    }


    @Test(expected = AbortException.class)
    public void checkFileIsaMavenSettingsFile_rejects_random_xml_file() throws AbortException {
        checkMavenSettingsFile("org/jenkinsci/plugins/pipeline/maven/util/random-xml-file.xml");
    }

    @Test(expected = AbortException.class)
    public void checkFileIsaMavenSettingsFile_rejects_random_non_xml_file() throws AbortException {
        checkMavenSettingsFile("org/jenkinsci/plugins/pipeline/maven/util/random-text-file.txt");
    }


    @Test(expected = AbortException.class)
    public void checkFileIsaMavenSettingsFile_rejects_files_that_does_not_exist() throws AbortException {
        XmlUtils.checkFileIsaMavenSettingsFile(new File(("org/jenkinsci/plugins/pipeline/maven/util/does-not-exist.txt")));
    }

    private void checkMavenSettingsFile(String mavenSettingsFilePath) throws AbortException {
        URL resource = Thread.currentThread().getContextClassLoader().getResource(mavenSettingsFilePath);
        XmlUtils.checkFileIsaMavenSettingsFile(new File(resource.getFile()));
    }
}
