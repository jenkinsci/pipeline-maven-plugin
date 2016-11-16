package org.jenkinsci.plugins.pipeline.maven.WithMavenStep
def f = namespace(lib.FormTagLib) as lib.FormTagLib

f.entry(field: 'maven', title: _('Maven')) {
    f.select()
}

f.entry(field: 'jdk', title: _('JDK')) {
    f.select()
}

f.entry(field: 'mavenSettingsConfig', title: _('Maven Settings Config')) {
    f.select()
}

f.entry(field: 'mavenSettingsFilePath', title: _('Maven Settings File Path')) {
    f.textbox()
}

f.entry(field: 'globalMavenSettingsConfig', title: _('Global Maven Settings Config')) {
    f.select()
}

f.entry(field: 'globalMavenSettingsFilePath', title: _('Global Maven Settings File Path')) {
    f.textbox()
}

f.entry(field: 'mavenOpts', title: _('Maven JVM Opts')) {
    f.textbox()
}

f.entry(field: 'mavenLocalRepo', title: _('Maven Local Repository')) {
    f.textbox()
}