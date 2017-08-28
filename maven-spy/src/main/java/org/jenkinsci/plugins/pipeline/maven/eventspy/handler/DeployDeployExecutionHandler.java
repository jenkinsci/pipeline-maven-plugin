package org.jenkinsci.plugins.pipeline.maven.eventspy.handler;

import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.execution.ExecutionEvent;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.jenkinsci.plugins.pipeline.maven.eventspy.reporter.MavenEventReporter;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class DeployDeployExecutionHandler extends CatchAllExecutionHandler {
    public DeployDeployExecutionHandler(@Nonnull MavenEventReporter reporter) {
        super(reporter);
    }

    @Override
    protected void addDetails(@Nonnull ExecutionEvent executionEvent, @Nonnull Xpp3Dom root) {
        super.addDetails(executionEvent, root);
        ArtifactRepository artifactRepository = executionEvent.getProject().getDistributionManagementArtifactRepository();
        Xpp3Dom artifactRepositoryElt = new Xpp3Dom("artifactRepository");
        root.addChild(artifactRepositoryElt);
        if (artifactRepository == null) {

        } else {
            Xpp3Dom idElt = new Xpp3Dom("id");
            idElt.setValue(artifactRepository.getId());
            artifactRepositoryElt.addChild(idElt);

            Xpp3Dom urlElt = new Xpp3Dom("url");
            urlElt.setValue(artifactRepository.getUrl());
            artifactRepositoryElt.addChild(urlElt);
        }

    }

    @Nullable
    @Override
    protected ExecutionEvent.Type getSupportedType() {
        return ExecutionEvent.Type.MojoSucceeded;
    }


    @Nullable
    @Override
    protected String getSupportedPluginGoal() {
        return "org.apache.maven.plugins:maven-deploy-plugin:deploy";
    }
}
