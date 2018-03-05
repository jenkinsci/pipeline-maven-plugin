package org.jenkinsci.plugins.pipeline.maven.publishers;

import hudson.FilePath;
import org.hamcrest.CoreMatchers;
import org.jenkinsci.plugins.pipeline.maven.MavenArtifact;
import org.jenkinsci.plugins.pipeline.maven.util.XmlUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.File;
import java.io.InputStream;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

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
        Assert.assertThat(mavenArtifacts.size(), CoreMatchers.is(2));

        MavenArtifact pomArtifact = mavenArtifacts.get(0);
        Assert.assertThat(pomArtifact.artifactId, CoreMatchers.is("spring-petclinic"));
        Assert.assertThat(pomArtifact.file, CoreMatchers.is("/path/to/spring-petclinic/pom.xml"));
        Assert.assertThat(pomArtifact.getFileName(), CoreMatchers.is("spring-petclinic-" + PETCLINIC_VERSION + ".pom"));

        Element projectStartedElt = XmlUtils.getExecutionEvents(this.mavenSpyLogsOnMacOSX.getDocumentElement(), "ProjectStarted").get(0);
        String workspace = XmlUtils.getUniqueChildElement(projectStartedElt, "project").getAttribute("baseDir");

        String pomPathInWorkspace = XmlUtils.getPathInWorkspace(pomArtifact.file, new FilePath(new File(workspace)));
        System.out.println("workspace: " + workspace);
        System.out.println("pomPathInWorkspace: " + pomPathInWorkspace);


        MavenArtifact mavenArtifact = mavenArtifacts.get(1);
        Assert.assertThat(mavenArtifact.artifactId, CoreMatchers.is("spring-petclinic"));
        Assert.assertThat(mavenArtifact.file, CoreMatchers.is("/path/to/spring-petclinic/target/spring-petclinic-" + PETCLINIC_VERSION + ".jar"));
        Assert.assertThat(mavenArtifact.getFileName(), CoreMatchers.is("spring-petclinic-" + PETCLINIC_VERSION + ".jar"));    }

    @Test
    public void testListArtifactsWindows() throws Exception {
        List<MavenArtifact> mavenArtifacts = XmlUtils.listGeneratedArtifacts(this.mavenSpyLogsOnWindows.getDocumentElement(), false);
        System.out.println(mavenArtifacts);
        Assert.assertThat(mavenArtifacts.size(), CoreMatchers.is(2));

        MavenArtifact pomArtifact = mavenArtifacts.get(0);
        Assert.assertThat(pomArtifact.artifactId, CoreMatchers.is("spring-petclinic"));
        Assert.assertThat(pomArtifact.file, CoreMatchers.is("C:\\path\\to\\spring-petclinic\\pom.xml"));
        Assert.assertThat(pomArtifact.getFileName(), CoreMatchers.is("spring-petclinic-" + PETCLINIC_VERSION + ".pom"));

        Element projectStartedElt = XmlUtils.getExecutionEvents(this.mavenSpyLogsOnWindows.getDocumentElement(), "ProjectStarted").get(0);
        String workspace = XmlUtils.getUniqueChildElement(projectStartedElt, "project").getAttribute("baseDir");

        String pomPathInWorkspace = XmlUtils.getPathInWorkspace(pomArtifact.file, new FilePath(new File(workspace)));
        System.out.println("workspace: " + workspace);
        System.out.println("pomPathInWorkspace: " + pomPathInWorkspace);


        MavenArtifact mavenArtifact = mavenArtifacts.get(1);
        Assert.assertThat(mavenArtifact.artifactId, CoreMatchers.is("spring-petclinic"));
        Assert.assertThat(mavenArtifact.file, CoreMatchers.is("C:\\path\\to\\spring-petclinic\\target\\spring-petclinic-" + PETCLINIC_VERSION + ".jar"));
        Assert.assertThat(mavenArtifact.getFileName(), CoreMatchers.is("spring-petclinic-" + PETCLINIC_VERSION + ".jar"));
    }

    @Test
    public void testListAttachedArtifactsMacOSX() throws Exception {
        List<MavenArtifact> mavenArtifacts = XmlUtils.listGeneratedArtifacts(this.mavenSpyLogsOnMacOSX.getDocumentElement(), true);
        Assert.assertThat(mavenArtifacts.size(), CoreMatchers.is(3));
        MavenArtifact mavenArtifact = mavenArtifacts.get(2); // 1st is pom, 2nd is jar, 3rd is sources
        System.out.println(mavenArtifacts);
        Assert.assertThat(mavenArtifact.artifactId, CoreMatchers.is("spring-petclinic"));
        Assert.assertThat(mavenArtifact.classifier, CoreMatchers.is("sources"));
        Assert.assertThat(mavenArtifact.file, CoreMatchers.is("/path/to/spring-petclinic/target/spring-petclinic-" + PETCLINIC_VERSION + "-sources.jar"));
    }

    @Test
    public void testListAttachedArtifactsWindows() throws Exception {
        List<MavenArtifact> mavenArtifacts = XmlUtils.listGeneratedArtifacts(this.mavenSpyLogsOnWindows.getDocumentElement(), true);
        Assert.assertThat(mavenArtifacts.size(), CoreMatchers.is(3));
        MavenArtifact mavenArtifact = mavenArtifacts.get(2); // 1st is pom, 2nd is jar, 3rd is sources
        System.out.println(mavenArtifacts);
        Assert.assertThat(mavenArtifact.artifactId, CoreMatchers.is("spring-petclinic"));
        Assert.assertThat(mavenArtifact.classifier, CoreMatchers.is("sources"));
        Assert.assertThat(mavenArtifact.file, CoreMatchers.is("C:\\path\\to\\spring-petclinic\\target\\spring-petclinic-" + PETCLINIC_VERSION + "-sources.jar"));
    }

}
