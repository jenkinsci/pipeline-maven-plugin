package org.jenkinsci.plugins.pipeline.maven.publishers;

import hudson.FilePath;
import org.hamcrest.CoreMatchers;
import org.jenkinsci.plugins.pipeline.maven.MavenArtifact;
import org.jenkinsci.plugins.pipeline.maven.util.XmlUtils;
import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.InputStream;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class GeneratedArtifactsReporterTest {

    static final String PETCLINIC_VERSION = "1.5.1";

    /**
     * generated on MacOSX
     */
    Document mavenSpyLogsOnMacOSX;
    /**
     * generated on Windows
     */
    Document mavenSpyLogsOnWindows;
    GeneratedArtifactsPublisher generatedArtifactsReporter = new GeneratedArtifactsPublisher();

    @Before
    public void before() throws Exception {
        {
            String mavenSpyLogsOnMacOSXPath = "org/jenkinsci/plugins/pipeline/maven/maven-spy.xml";
            InputStream inMacOSX = Thread.currentThread().getContextClassLoader().getResourceAsStream(mavenSpyLogsOnMacOSXPath);
            mavenSpyLogsOnMacOSX = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(inMacOSX);
        }
        {
            String mavenSpyLogsOnWindowsPath = "org/jenkinsci/plugins/pipeline/maven/maven-spy-windows.xml";
            InputStream inWindows = Thread.currentThread().getContextClassLoader().getResourceAsStream(mavenSpyLogsOnWindowsPath);
            mavenSpyLogsOnWindows = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(inWindows);

        }
    }

    @Test
    public void testListArtifactsMacOSX() throws Exception {
        List<MavenArtifact> mavenArtifacts = XmlUtils.listGeneratedArtifacts(this.mavenSpyLogsOnMacOSX.getDocumentElement(), false);
        System.out.println(mavenArtifacts);
        assertThat(mavenArtifacts.size(), CoreMatchers.is(2));

        MavenArtifact pomArtifact = mavenArtifacts.get(0);
        assertThat(pomArtifact.getArtifactId(), CoreMatchers.is("spring-petclinic"));
        assertThat(pomArtifact.getFile(), CoreMatchers.is("/path/to/spring-petclinic/pom.xml"));
        assertThat(pomArtifact.getFileName(), CoreMatchers.is("spring-petclinic-" + PETCLINIC_VERSION + ".pom"));

        Element projectStartedElt = XmlUtils.getExecutionEvents(this.mavenSpyLogsOnMacOSX.getDocumentElement(), "ProjectStarted").get(0);
        String workspace = XmlUtils.getUniqueChildElement(projectStartedElt, "project").getAttribute("baseDir");

        String pomPathInWorkspace = XmlUtils.getPathInWorkspace(pomArtifact.getFile(), new FilePath(new File(workspace)));
        System.out.println("workspace: " + workspace);
        System.out.println("pomPathInWorkspace: " + pomPathInWorkspace);


        MavenArtifact mavenArtifact = mavenArtifacts.get(1);
        assertThat(mavenArtifact.getArtifactId(), CoreMatchers.is("spring-petclinic"));
        assertThat(mavenArtifact.getFile(), CoreMatchers.is("/path/to/spring-petclinic/target/spring-petclinic-" + PETCLINIC_VERSION + ".jar"));
        assertThat(mavenArtifact.getFileName(), CoreMatchers.is("spring-petclinic-" + PETCLINIC_VERSION + ".jar"));    }

    @Test
    public void testListArtifactsWindows() throws Exception {
        List<MavenArtifact> mavenArtifacts = XmlUtils.listGeneratedArtifacts(this.mavenSpyLogsOnWindows.getDocumentElement(), false);
        System.out.println(mavenArtifacts);
        assertThat(mavenArtifacts.size(), CoreMatchers.is(2));

        MavenArtifact pomArtifact = mavenArtifacts.get(0);
        assertThat(pomArtifact.getArtifactId(), CoreMatchers.is("spring-petclinic"));
        assertThat(pomArtifact.getFile(), CoreMatchers.is("C:\\path\\to\\spring-petclinic\\pom.xml"));
        assertThat(pomArtifact.getFileName(), CoreMatchers.is("spring-petclinic-" + PETCLINIC_VERSION + ".pom"));

        Element projectStartedElt = XmlUtils.getExecutionEvents(this.mavenSpyLogsOnWindows.getDocumentElement(), "ProjectStarted").get(0);
        String workspace = XmlUtils.getUniqueChildElement(projectStartedElt, "project").getAttribute("baseDir");

        String pomPathInWorkspace = XmlUtils.getPathInWorkspace(pomArtifact.getFile(), new FilePath(new File(workspace)));
        System.out.println("workspace: " + workspace);
        System.out.println("pomPathInWorkspace: " + pomPathInWorkspace);


        MavenArtifact mavenArtifact = mavenArtifacts.get(1);
        assertThat(mavenArtifact.getArtifactId(), CoreMatchers.is("spring-petclinic"));
        assertThat(mavenArtifact.getFile(), CoreMatchers.is("C:\\path\\to\\spring-petclinic\\target\\spring-petclinic-" + PETCLINIC_VERSION + ".jar"));
        assertThat(mavenArtifact.getFileName(), CoreMatchers.is("spring-petclinic-" + PETCLINIC_VERSION + ".jar"));
    }

    @Test
    public void testListAttachedArtifactsMacOSX() throws Exception {
        List<MavenArtifact> mavenArtifacts = XmlUtils.listGeneratedArtifacts(this.mavenSpyLogsOnMacOSX.getDocumentElement(), true);
        assertThat(mavenArtifacts.size(), CoreMatchers.is(3));
        MavenArtifact mavenArtifact = mavenArtifacts.get(2); // 1st is pom, 2nd is jar, 3rd is sources
        System.out.println(mavenArtifacts);
        assertThat(mavenArtifact.getArtifactId(), CoreMatchers.is("spring-petclinic"));
        assertThat(mavenArtifact.getClassifier(), CoreMatchers.is("sources"));
        assertThat(mavenArtifact.getFile(), CoreMatchers.is("/path/to/spring-petclinic/target/spring-petclinic-" + PETCLINIC_VERSION + "-sources.jar"));
    }

    @Test
    public void testListAttachedArtifactsWindows() throws Exception {
        List<MavenArtifact> mavenArtifacts = XmlUtils.listGeneratedArtifacts(this.mavenSpyLogsOnWindows.getDocumentElement(), true);
        assertThat(mavenArtifacts.size(), CoreMatchers.is(3));
        MavenArtifact mavenArtifact = mavenArtifacts.get(2); // 1st is pom, 2nd is jar, 3rd is sources
        System.out.println(mavenArtifacts);
        assertThat(mavenArtifact.getArtifactId(), CoreMatchers.is("spring-petclinic"));
        assertThat(mavenArtifact.getClassifier(), CoreMatchers.is("sources"));
        assertThat(mavenArtifact.getFile(), CoreMatchers.is("C:\\path\\to\\spring-petclinic\\target\\spring-petclinic-" + PETCLINIC_VERSION + "-sources.jar"));
    }

}
