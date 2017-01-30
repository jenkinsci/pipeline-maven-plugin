package org.jenkinsci.plugins.pipeline.maven;

import org.hamcrest.CoreMatchers;
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
public class MavenSpyLogProcessorTest {

    Document doc;
    MavenSpyLogProcessor mavenSpyLogProcessor = new MavenSpyLogProcessor();

    @Before
    public void before() throws Exception {
        String mavenSpyLogs = "org/jenkinsci/plugins/pipeline/maven/maven-spy-20170131-135316-432.log.xml";
        InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(mavenSpyLogs);
        doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in);
    }

    @Test
    public void testListArtifacts() throws Exception {
        List<MavenSpyLogProcessor.MavenArtifact> mavenArtifacts = mavenSpyLogProcessor.listArtifacts(doc.getDocumentElement());
        Assert.assertThat(mavenArtifacts.size(), CoreMatchers.is(1));
        MavenSpyLogProcessor.MavenArtifact mavenArtifact = mavenArtifacts.get(0);
        System.out.println(mavenArtifacts);
        Assert.assertThat(mavenArtifact.artifactId, CoreMatchers.is("spring-petclinic"));
        Assert.assertThat(mavenArtifact.file, CoreMatchers.is("/path/to/spring-petclinic/target/spring-petclinic-1.4.2.jar"));
    }

    @Test
    public void testListAttachedArtifacts() throws Exception {
        List<MavenSpyLogProcessor.MavenArtifact> mavenArtifacts = mavenSpyLogProcessor.listAttachedArtifacts(doc.getDocumentElement());
        Assert.assertThat(mavenArtifacts.size(), CoreMatchers.is(1));
        MavenSpyLogProcessor.MavenArtifact mavenArtifact = mavenArtifacts.get(0);
        System.out.println(mavenArtifacts);
        Assert.assertThat(mavenArtifact.artifactId, CoreMatchers.is("spring-petclinic"));
        Assert.assertThat(mavenArtifact.classifier, CoreMatchers.is("sources"));
        Assert.assertThat(mavenArtifact.file, CoreMatchers.is("/path/to/spring-petclinic/target/spring-petclinic-1.4.2-sources.jar"));
    }
}
