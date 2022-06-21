package org.jenkinsci.plugins.pipeline.maven.publishers;

import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.pipeline.maven.MavenArtifact;
import org.jenkinsci.plugins.pipeline.maven.MavenDependency;
import org.jenkinsci.plugins.pipeline.maven.util.XmlUtils;
import org.w3c.dom.Element;

import javax.annotation.Nonnull;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * List dependencies from the spy log.
 *
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class DependenciesLister {

    /**
     * @param mavenSpyLogs Root XML element
     * @return list of {@link MavenArtifact}
     */
    @Nonnull
    public static Set<MavenDependency> listDependencies(final Element mavenSpyLogs,
                                                         final Logger logger) {

        final Set<MavenDependency> result = new HashSet<>();

        for (final Element dependencyResolutionResult : XmlUtils.getChildrenElements(mavenSpyLogs,
                "DependencyResolutionResult")) {
            final Element resolvedDependenciesElt = XmlUtils.getUniqueChildElementOrNull(
                    dependencyResolutionResult, "resolvedDependencies");

            if (resolvedDependenciesElt == null) {
                continue;
            }

            for (final Element dependencyElt : XmlUtils.getChildrenElements(resolvedDependenciesElt,
                    "dependency")) {
                final MavenDependency dependencyArtifact = XmlUtils.newMavenDependency(
                        dependencyElt);

                final Element fileElt = XmlUtils.getUniqueChildElementOrNull(dependencyElt, "file");
                if (fileElt == null || fileElt.getTextContent() == null
                        || fileElt.getTextContent().isEmpty()) {
                    logger.log(Level.WARNING, "listDependencies: no associated file found for "
                            + dependencyArtifact + " in " + XmlUtils.toString(dependencyElt));
                } else {
                    dependencyArtifact.setFile(StringUtils.trim(fileElt.getTextContent()));
                }

                result.add(dependencyArtifact);
            }
        }

        return result;
    }

    /**
     * @param mavenSpyLogs Root XML element
     * @return list of {@link MavenArtifact}
     */
    @Nonnull
    public static Set<MavenArtifact> listParentProjects(final Element mavenSpyLogs,
                                                         final Logger logger) {

        final Set<MavenArtifact> result = new HashSet<>();

        for (final Element dependencyResolutionResult : XmlUtils.getExecutionEvents(mavenSpyLogs,
                "ProjectStarted")) {
            final Element parentProjectElt = XmlUtils.getUniqueChildElementOrNull(
                    dependencyResolutionResult, "parentProject");

            if (parentProjectElt == null) {
                continue;
            }
            final MavenArtifact parentProject = new MavenArtifact();

            parentProject.setGroupId(parentProjectElt.getAttribute("groupId"));
            parentProject.setArtifactId(parentProjectElt.getAttribute("artifactId"));
            parentProject.setVersion(parentProjectElt.getAttribute("version"));
            parentProject.setBaseVersion(parentProject.getVersion());
            parentProject.setSnapshot(parentProject.getVersion().endsWith("-SNAPSHOT"));

            result.add(parentProject);
        }

        return result;
    }

}
