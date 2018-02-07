package org.jenkinsci.plugins.pipeline.maven.publishers;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nonnull;

import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.pipeline.maven.MavenPublisher;
import org.jenkinsci.plugins.pipeline.maven.util.XmlUtils;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.kohsuke.stapler.DataBoundConstructor;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import hudson.Extension;
import hudson.model.Action;
import hudson.model.Run;
import jenkins.tasks.SimpleBuildStep.LastBuildAction;

public class MavenLinkerPublisher extends MavenPublisher implements LastBuildAction {

    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(MavenLinkerPublisher.class.getName());
    private transient List<Artifact> artifacts = new ArrayList<>();

    @DataBoundConstructor
    public MavenLinkerPublisher() {
        // default DataBoundConstructor
    }

    @Override
    public void process(StepContext context, Element mavenSpyLogsElt) throws IOException, InterruptedException {

        Run<?, ?> run = context.get(Run.class);

        for (Element repositoryEvent : getArtifactDeployedEvents(mavenSpyLogsElt)) {
            Artifact artifact = getArtifact(repositoryEvent);
            artifacts.add(artifact);
        }

        run.addAction(this);
    }

    @Nonnull
    static List<Element> getArtifactDeployedEvents(@Nonnull Element mavenSpyLogs) {
        List<Element> elements = new ArrayList<>();

        NodeList nodes = mavenSpyLogs.getChildNodes();

        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node instanceof Element) {
                Element element = (Element) node;
                if (StringUtils.equals(element.getNodeName(), "RepositoryEvent")) {
                    Attr type = element.getAttributeNode("type");
                    if (null != type && StringUtils.equals(type.getValue(), "ARTIFACT_DEPLOYED")) {
                        elements.add(element);
                    }
                }
            }
        }

        LOGGER.log(Level.FINE, "Found {0} deployed elements", elements.size());
        return elements;
    }

    /*

    <RepositoryEvent type="ARTIFACT_DEPLOYED" class="org.eclipse.aether.RepositoryEvent" _time="2018-02-07 23:12:00.936">
    <artifact extension="pom" file="/home/ubuntu/jenkins-aws-home/workspace/plugins/pipeline-maven-plugin/my-jar/pom.xml" baseVersion="0.5-SNAPSHOT" groupId="com.example" classifier="" artifactId="my-jar" id="com.example:my-jar:pom:0.5-20180207.231200-16" version="0.5-20180207.231200-16" snapshot="true"/>
    <repository layout="default" id="nexus.beescloud.com" url="https://nexus.beescloud.com/content/repositories/snapshots/"/>
  </RepositoryEvent>
     */
    @Nonnull
    static Artifact getArtifact(@Nonnull Element repositoryEvent) {

        Element artifactElement = XmlUtils.getUniqueChildElement(repositoryEvent, "artifact");

        String groupId = artifactElement.getAttribute("groupId");
        String artifactId = artifactElement.getAttribute("artifactId");
        String baseVersion = artifactElement.getAttribute("baseVersion");
        String version = artifactElement.getAttribute("version");
        String classifier = artifactElement.getAttribute("classifier");
        String extension = artifactElement.getAttribute("extension");

        Element repositoryElement = XmlUtils.getUniqueChildElement(repositoryEvent, "repository");
        String repositoryUrl = repositoryElement.getAttribute("url");

        String fileName = artifactId + "-" + version;
        if (!StringUtils.isBlank(classifier)) {
            fileName += "-" + classifier;
        }
        fileName += "." + extension;

        String url = repositoryUrl + "/" + groupId.replace('.', '/') + "/" + artifactId + "/" + baseVersion + "/" + fileName;


        return new Artifact(fileName, url);
    }

    public List<Artifact> getArtifacts() {
        LOGGER.log(Level.FINE, "displaying {0} artifacts", artifacts.size());
        return artifacts;
    }

    @Override
    public String getIconFileName() {
        return null;
    }

    @Override
    public String getDisplayName() {
        return "Maven Linker Action";
    }

    @Override
    public String getUrlName() {
        return null;
    }

    @Override
    public Collection<? extends Action> getProjectActions() {
        return new ArrayList<>();
    }

    private void readObject(ObjectInputStream stream) throws ClassNotFoundException, IOException {
        stream.defaultReadObject();
        artifacts = new ArrayList<>();
    }

    @Extension
    public static class DescriptorImpl extends MavenPublisher.DescriptorImpl {

        @Override
        public String getSkipFileName() {
            return ".skip-maven-linker-publisher";
        }

        @Override
        public String getDisplayName() {
            return "Maven Linker Publisher";
        }
    }

    public static class Artifact {

        String name;
        String url;

        public Artifact(String name, String url) {
            super();
            this.name = name;
            this.url = url;
        }

        public String getName() {
            return name;
        }

        public String getUrl() {
            return url;
        }
    }
}
