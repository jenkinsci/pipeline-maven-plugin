package org.jenkinsci.plugins.pipeline.maven.eventspy.handler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.eclipse.aether.RepositoryEvent;
import org.jenkinsci.plugins.pipeline.maven.eventspy.reporter.MavenEventReporter;

public class ArtifactDeployedEventHandler implements MavenEventHandler {

    protected final MavenEventReporter reporter;

    public ArtifactDeployedEventHandler(MavenEventReporter reporter) {
        this.reporter = reporter;
    }

    @Override
    public boolean handle(Object event) {

        if (event instanceof RepositoryEvent) {
            RepositoryEvent repositoryEvent = (RepositoryEvent) event;

            if (repositoryEvent.getType() == RepositoryEvent.EventType.ARTIFACT_DEPLOYED) {
                reporter.print(newElement(repositoryEvent));
                return true;
            }
        }

        return false;
    }

    protected Xpp3Dom newElement(@Nullable org.eclipse.aether.RepositoryEvent event) {
        Xpp3Dom element = new Xpp3Dom("RepositoryEvent");
        if (event == null) {
            return element;
        }

        element.setAttribute("class", event.getClass().getName());
        element.setAttribute("type", event.getType().toString());
        element.addChild(newElement("artifact", event.getArtifact()));
        element.addChild(newElement("repository", event.getRepository()));

        return element;
    }

    protected Xpp3Dom newElement(String name, @Nullable org.eclipse.aether.repository.ArtifactRepository repository) {
        Xpp3Dom element = new Xpp3Dom(name);
        if (repository == null) {
            return element;
        }

        element.setAttribute("id", repository.getId());
        element.setAttribute("layout", repository.getContentType());
        String repoString = repository.toString();
        element.setAttribute("url", repoString.substring(repoString.indexOf("(") + 1, repoString.indexOf(",")));

        return element;
    }

    protected Xpp3Dom newElement(@Nonnull String name, @Nullable org.eclipse.aether.artifact.Artifact artifact) {
        Xpp3Dom element = new Xpp3Dom(name);
        if (artifact == null) {
            return element;
        }

        element.setAttribute("id", artifact.toString());
        element.setAttribute("groupId", artifact.getGroupId());
        element.setAttribute("artifactId", artifact.getArtifactId());
        element.setAttribute("baseVersion", artifact.getBaseVersion());
        element.setAttribute("version", artifact.getVersion());
        element.setAttribute("classifier", artifact.getClassifier());
        element.setAttribute("snapshot", String.valueOf(artifact.isSnapshot()));
        element.setAttribute("file", artifact.getFile().getAbsolutePath());
        element.setAttribute("extension", artifact.getExtension());


        return element;
    }
}
