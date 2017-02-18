/*
 * The MIT License
 *
 * Copyright (c) 2016, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.jenkinsci.plugins.pipeline.maven.eventspy.handler;

import org.jenkinsci.plugins.pipeline.maven.eventspy.reporter.FileMavenEventReporter;
import org.sonatype.aether.RepositoryEvent;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class RepositoryEventHandler extends AbstractMavenEventHandler<RepositoryEvent> {
    protected RepositoryEventHandler(FileMavenEventReporter reporter) {
        super(reporter);
    }

    long currentEventStartTimeInNanos;
    long artifactDownloadDurationInNanos;

    @Override
    protected boolean _handle(RepositoryEvent repositoryEvent) {
        if (repositoryEvent.getType() == RepositoryEvent.EventType.ARTIFACT_DOWNLOADING) {
            currentEventStartTimeInNanos = System.nanoTime();
        } else if (repositoryEvent.getType() == RepositoryEvent.EventType.ARTIFACT_DOWNLOADED) {
            long durationInNanos = System.nanoTime() - currentEventStartTimeInNanos;
            artifactDownloadDurationInNanos += durationInNanos;

            print(repositoryEvent, durationInNanos);
            currentEventStartTimeInNanos = 0;
        }
        if (repositoryEvent.getType() == RepositoryEvent.EventType.METADATA_DOWNLOADING) {
            currentEventStartTimeInNanos = System.nanoTime();
        } else if (repositoryEvent.getType() == RepositoryEvent.EventType.METADATA_DOWNLOADED) {
            long durationInNanos = System.nanoTime() - currentEventStartTimeInNanos;
            artifactDownloadDurationInNanos += durationInNanos;

            print(repositoryEvent, durationInNanos);
            currentEventStartTimeInNanos = 0;
        } else {

        }

        return true;
    }

    private void print(RepositoryEvent repositoryEvent, long durationInNanos) {
        reporter.print(repositoryEvent.getArtifact().toString() + "-" + repositoryEvent.getType() + "-" + durationInNanos + "nanos");
    }
}
