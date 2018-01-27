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

    static Artifact getArtifact(@Nonnull Element repositoryEvent) {

        Artifact artifact = null;
        String groupId = null;
        String artifactId = null;
        String baseVersion = null;
        String version = null;
        String classifier = null;
        String type = null;
        String url = null;

        NodeList nodes = repositoryEvent.getChildNodes();

        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node instanceof Element) {
                Element element = (Element) node;
                if (StringUtils.equals(element.getNodeName(), "artifact")) {
                    groupId = element.getAttribute("groupId");
                    artifactId = element.getAttribute("artifactId");
                    baseVersion = element.getAttribute("baseVersion");
                    version = element.getAttribute("version");
                    classifier = element.getAttribute("classifier");
                    type = element.getAttribute("type");
                }
                if (StringUtils.equals(element.getNodeName(), "repository")) {
                    url = element.getAttribute("url");
                    url += "/" + groupId.replace('.', '/') + "/" + artifactId + "/" + baseVersion + "/" + artifactId
                            + "-" + version;
                    if (!StringUtils.isBlank(classifier)) {
                        url += "-" + classifier;
                    }
                    url += "." + type;
                    artifact = new Artifact(getNameFromUrl(url), url);
                }
            }
        }

        return artifact;
    }

    static String getNameFromUrl(@Nonnull String url) {
        return url.substring(url.lastIndexOf('/') + 1, url.length());
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
