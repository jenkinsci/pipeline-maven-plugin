package org.jenkinsci.plugins.pipeline.maven.model;

import org.hamcrest.Matchers;
import org.jenkinsci.plugins.pipeline.maven.publishers.MavenBuildDetailsPublisher;
import org.junit.Assert;
import org.junit.Test;
import org.w3c.dom.Document;

import java.io.InputStream;
import java.util.List;
import java.util.SortedSet;

import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.Assert.*;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class ObjectFactoryTest {
    @Test
    public void analyseMavenMojoExecutions_simple_jar_build() throws Exception {

        InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream("org/jenkinsci/plugins/pipeline/maven/maven-spy-deploy-jar.xml");
        Document mavenBuildSpyLogs = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in);
        ObjectFactory objectFactory = new ObjectFactory();
        List<MavenMojoExecutionDetails> mavenMojoExecutionDetails = objectFactory.analyseMavenMojoExecutions(mavenBuildSpyLogs.getDocumentElement());
        for (MavenMojoExecutionDetails mojoTimer : mavenMojoExecutionDetails) {
            System.out.println(mojoTimer + " " + mojoTimer.getDuration());
        }
        Assert.assertThat(mavenMojoExecutionDetails.size(), Matchers.is(10));
    }

    @Test
    public void analyseMavenProjectExecutions_simple_jar_build() throws Exception {

        InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream("org/jenkinsci/plugins/pipeline/maven/maven-spy-deploy-jar.xml");
        Document mavenBuildSpyLogs = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in);
        ObjectFactory objectFactory = new ObjectFactory();
        MavenExecutionDetails executionDetails = objectFactory.analyseMavenBuildExecution(mavenBuildSpyLogs.getDocumentElement());

        Assert.assertThat(executionDetails.getMavenProjectExecutionDetails().size(), Matchers.is(1));
        MavenProjectExecutionDetails mavenProjectExecutionDetails = executionDetails.getMavenProjectExecutionDetails().first();
        System.out.println(mavenProjectExecutionDetails + " " + mavenProjectExecutionDetails.getDuration());

        SortedSet<MavenMojoExecutionDetails> mojoExecutionDetails = mavenProjectExecutionDetails.getMojoExecutionDetails();
        Assert.assertThat(mojoExecutionDetails.size(), Matchers.is(10));
        // TODO



    }

    @Test
    public void analyseMavenMojoExecutions_multi_module_build() throws Exception {

        InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream("org/jenkinsci/plugins/pipeline/maven/maven-spy-deploy-multi-module.xml");
        Document mavenBuildSpyLogs = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in);
        ObjectFactory objectFactory = new ObjectFactory();
        MavenExecutionDetails executionDetails = objectFactory.analyseMavenBuildExecution(mavenBuildSpyLogs.getDocumentElement());
        Assert.assertThat(executionDetails.getMavenProjectExecutionDetails().size(), Matchers.is(4));

        for (MavenProjectExecutionDetails mavenProjectExecutionDetails : executionDetails.getMavenProjectExecutionDetails()) {
            System.out.println(mavenProjectExecutionDetails + " " + mavenProjectExecutionDetails.getDuration());
        }

        System.out.println("****************");

        System.out.println(executionDetails.getExecutionDurationDetails());
    }

//    @Test
//    public void analyseMavenProjectExecutions_multi_module_build() throws Exception {
//
//        InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream("org/jenkinsci/plugins/pipeline/maven/maven-spy-deploy-multi-module.xml");
//        Document mavenBuildSpyLogs = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in);
//        ObjectFactory objectFactory = new ObjectFactory();
//        List<MavenMojoExecutionDetails> mavenMojoExecutionDetails = objectFactory.analyseMavenMojoExecutions(mavenBuildSpyLogs.getDocumentElement());
//
//        for (MavenMojoExecutionDetails mojoTimer : mavenMojoExecutionDetails) {
//            System.out.println(mojoTimer + " " + mojoTimer.getDuration());
//
//            for (MavenMojoExecutionDetails mojoTimer : mojoTimer.getTimers()) {
//                System.out.println("\t" + mojoTimer + " " + mojoTimer.getDuration());
//            }
//        }
//    }

    @Test
    public void process_multi_module_build() throws Exception {

        InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream("org/jenkinsci/plugins/pipeline/maven/maven-spy-deploy-multi-module.xml");
        Document mavenBuildSpyLogs = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in);
        MavenBuildDetailsPublisher mavenBuildDetailsPublisher = new MavenBuildDetailsPublisher();
        mavenBuildDetailsPublisher.process(mavenBuildSpyLogs.getDocumentElement(), System.out);
    }
}