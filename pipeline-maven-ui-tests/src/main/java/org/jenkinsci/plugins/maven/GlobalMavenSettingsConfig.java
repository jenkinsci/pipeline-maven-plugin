package org.jenkinsci.plugins.maven;

import org.jenkinsci.test.acceptance.plugins.config_file_provider.ConfigFileProvider;
import org.jenkinsci.test.acceptance.plugins.config_file_provider.ProvidedFile;
import org.jenkinsci.test.acceptance.po.CodeMirror;
import org.jenkinsci.test.acceptance.po.Describable;

@Describable("Global Maven settings.xml")
public class GlobalMavenSettingsConfig extends ProvidedFile {

    public GlobalMavenSettingsConfig(ConfigFileProvider context, String id) {
        super(context, id);
    }

    @Override
    public void content(String mvnSettings) {
        new CodeMirror(this, "/config/content").set(mvnSettings);
    }
}
