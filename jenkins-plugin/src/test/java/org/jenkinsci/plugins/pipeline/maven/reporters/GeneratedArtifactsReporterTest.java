package org.jenkinsci.plugins.pipeline.maven.reporters;

import org.hamcrest.CoreMatchers;
import org.jenkinsci.plugins.pipeline.maven.MavenSpyLogProcessor;
import org.jenkinsci.plugins.pipeline.maven.reporters.GeneratedArtifactsReporter;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Document;

import java.io.InputStream;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class GeneratedArtifactsReporterTest {

    static final String PETCLINIC_VERSION = "1.5.1";

    Document doc;
    GeneratedArtifactsReporter generatedArtifactsReporter = new GeneratedArtifactsReporter();

    @Before
    public void before() throws Exception {
        String mavenSpyLogs = "org/jenkinsci/plugins/pipeline/maven/maven-spy.xml";
        InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(mavenSpyLogs);
        doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in);
    }

    @Test
    public void testListArtifacts() throws Exception {
        List<MavenSpyLogProcessor.MavenArtifact> mavenArtifacts = generatedArtifactsReporter.listArtifacts(doc.getDocumentElement());
        System.out.println(mavenArtifacts);
        Assert.assertThat(mavenArtifacts.size(), CoreMatchers.is(2));

        MavenSpyLogProcessor.MavenArtifact pomArtifact = mavenArtifacts.get(0);
        Assert.assertThat(pomArtifact.artifactId, CoreMatchers.is("spring-petclinic"));
        Assert.assertThat(pomArtifact.file, CoreMatchers.is("/path/to/spring-petclinic/pom.xml"));
        Assert.assertThat(pomArtifact.getFileName(), CoreMatchers.is("spring-petclinic-" + PETCLINIC_VERSION + ".pom"));

        MavenSpyLogProcessor.MavenArtifact mavenArtifact = mavenArtifacts.get(1);
        Assert.assertThat(mavenArtifact.artifactId, CoreMatchers.is("spring-petclinic"));
        Assert.assertThat(mavenArtifact.file, CoreMatchers.is("/path/to/spring-petclinic/target/spring-petclinic-" + PETCLINIC_VERSION + ".jar"));
        Assert.assertThat(mavenArtifact.getFileName(), CoreMatchers.is("spring-petclinic-" + PETCLINIC_VERSION + ".jar"));

    }

    @Test
    public void testListAttachedArtifacts() throws Exception {
        List<MavenSpyLogProcessor.MavenArtifact> mavenArtifacts = generatedArtifactsReporter.listAttachedArtifacts(doc.getDocumentElement());
        Assert.assertThat(mavenArtifacts.size(), CoreMatchers.is(1));
        MavenSpyLogProcessor.MavenArtifact mavenArtifact = mavenArtifacts.get(0);
        System.out.println(mavenArtifacts);
        Assert.assertThat(mavenArtifact.artifactId, CoreMatchers.is("spring-petclinic"));
        Assert.assertThat(mavenArtifact.classifier, CoreMatchers.is("sources"));
        Assert.assertThat(mavenArtifact.file, CoreMatchers.is("/path/to/spring-petclinic/target/spring-petclinic-" + PETCLINIC_VERSION + "-sources.jar"));
    }
}
