package org.jenkinsci.plugins.pipeline.maven.publishers;

import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.pipeline.maven.MavenSpyLogProcessor;
import org.jenkinsci.plugins.pipeline.maven.util.XmlUtils;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nonnull;

/**
 * List dependencies from the spy log.
 *
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class DependenciesLister {

    /**
     * @param mavenSpyLogs Root XML element
     * @return list of {@link MavenSpyLogProcessor.MavenArtifact}
     */
    @Nonnull
    public static List<MavenSpyLogProcessor.MavenDependency> listDependencies(final Element mavenSpyLogs,
                                                                              final Logger logger) {

        final List<MavenSpyLogProcessor.MavenDependency> result = new ArrayList<>();

        for (final Element dependencyResolutionResult : XmlUtils.getChildrenElements(mavenSpyLogs,
                "DependencyResolutionResult")) {
            final Element resolvedDependenciesElt = XmlUtils.getUniqueChildElementOrNull(
                    dependencyResolutionResult, "resolvedDependencies");

            if (resolvedDependenciesElt == null) {
                continue;
            }

            for (final Element dependencyElt : XmlUtils.getChildrenElements(resolvedDependenciesElt,
                    "dependency")) {
                final MavenSpyLogProcessor.MavenDependency dependencyArtifact = XmlUtils.newMavenDependency(
                        dependencyElt);

                final Element fileElt = XmlUtils.getUniqueChildElementOrNull(dependencyElt, "file");
                if (fileElt == null || fileElt.getTextContent() == null
                        || fileElt.getTextContent().isEmpty()) {
                    logger.log(Level.WARNING, "listDependencies: no associated file found for "
                            + dependencyArtifact + " in " + XmlUtils.toString(dependencyElt));
                } else {
                    dependencyArtifact.file = StringUtils.trim(fileElt.getTextContent());
                }

                result.add(dependencyArtifact);
            }
        }

        return result;
    }

    /**
     * @param mavenSpyLogs Root XML element
     * @return list of {@link MavenSpyLogProcessor.MavenArtifact}
     */
    @Nonnull
    public static List<MavenSpyLogProcessor.MavenArtifact> listParentProjects(final Element mavenSpyLogs,
                                                                              final Logger logger) {

        final List<MavenSpyLogProcessor.MavenArtifact> result = new ArrayList<>();

        for (final Element dependencyResolutionResult : XmlUtils.getExecutionEvents(mavenSpyLogs,
                "ProjectStarted")) {
            final Element parentProjectElt = XmlUtils.getUniqueChildElementOrNull(
                    dependencyResolutionResult, "parentProject");

            if (parentProjectElt == null) {
                continue;
            }
            final MavenSpyLogProcessor.MavenArtifact parentProject = new MavenSpyLogProcessor.MavenArtifact();

            parentProject.groupId = parentProjectElt.getAttribute("groupId");
            parentProject.artifactId = parentProjectElt.getAttribute("artifactId");
            parentProject.version = parentProjectElt.getAttribute("version");
            parentProject.baseVersion = parentProject.version;
            parentProject.snapshot = parentProject.version.endsWith("-SNAPSHOT");

            result.add(parentProject);
        }

        return result;
    }

}
