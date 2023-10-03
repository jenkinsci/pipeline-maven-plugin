package org.jenkinsci.plugins.pipeline.maven.eventspy.handler;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.jenkinsci.plugins.pipeline.maven.eventspy.reporter.OutputStreamEventReporter;
import org.junit.jupiter.api.Test;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class AbstractMavenEventHandlerTest {

    @Test
    public void test_getMavenFlattenPluginFlattenedPomFilename_nameDefinedAtTheExecutionLevel() throws Exception {
        test_getMavenFlattenPluginFlattenedPomFilename(
                "org/jenkinsci/plugins/pipeline/maven/eventspy/pom-flatten-plugin-flattenedPomFilename.xml",
                "${project.artifactId}-${project.version}.pom");
    }

    @Test
    public void test_getMavenFlattenPluginFlattenedPomFilename_nameDefinedAtThePluginLevel() throws Exception {
        test_getMavenFlattenPluginFlattenedPomFilename(
                "org/jenkinsci/plugins/pipeline/maven/eventspy/pom-flatten-plugin-flattenedPomFilename2.xml",
                "${project.artifactId}-${project.version}.flatten-pom");
    }

    @Test
    public void test_getMavenFlattenPluginFlattenedPomFilename_nameNotDefined() throws Exception {
        test_getMavenFlattenPluginFlattenedPomFilename("org/jenkinsci/plugins/pipeline/maven/eventspy/pom.xml", null);
    }

    protected void test_getMavenFlattenPluginFlattenedPomFilename(String pomFile, String expected)
            throws IOException, XmlPullParserException {
        InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(pomFile);
        Model mavenProjectModel = new MavenXpp3Reader().read(in);

        MavenProject mavenProject = new MavenProject(mavenProjectModel);
        AbstractMavenEventHandler mavenEventHandler =
                new AbstractMavenEventHandler(new OutputStreamEventReporter(System.err)) {
                    @Override
                    protected boolean _handle(Object o) {
                        return false;
                    }
                };
        String actual = mavenEventHandler.getMavenFlattenPluginFlattenedPomFilename(mavenProject);
        // this unit test does not expand Maven variables
        assertThat(actual).isEqualTo(expected);
    }
}
