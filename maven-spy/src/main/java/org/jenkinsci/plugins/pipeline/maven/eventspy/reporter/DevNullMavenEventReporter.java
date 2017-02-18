package org.jenkinsci.plugins.pipeline.maven.eventspy.reporter;

import org.codehaus.plexus.util.xml.Xpp3Dom;

/**
 * No Op {@link MavenEventReporter}, typically used to disable the agent.
 *
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class DevNullMavenEventReporter implements MavenEventReporter {
    @Override
    public void print(Object message) {

    }

    @Override
    public void print(Xpp3Dom element) {

    }

    @Override
    public void close() {

    }
}
