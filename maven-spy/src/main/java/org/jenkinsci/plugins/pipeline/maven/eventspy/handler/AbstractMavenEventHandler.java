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

import org.apache.maven.artifact.Artifact;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.jenkinsci.plugins.pipeline.maven.eventspy.reporter.MavenEventReporter;

import java.io.File;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public abstract class AbstractMavenEventHandler<E> implements MavenEventHandler<E> {
    protected final MavenEventReporter reporter;

    protected AbstractMavenEventHandler(MavenEventReporter reporter) {
        this.reporter = reporter;
    }


    @Override
    public boolean handle(Object event) {
        Type type = getSupportedType();
        Class<E> clazz = (Class<E>) type;
        if (clazz.isAssignableFrom(event.getClass())) {
            return _handle((E) event);
        } else {
            // print("event " + event + " not handled by " + toString());
            return false;
        }
    }

    private Type getSupportedType() {
        return ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[0];
    }

    protected abstract boolean _handle(E e);

    @Override
    public String toString() {
        return getClass().getName() + "[type=" + getSupportedType() + "]";
    }


    public Xpp3Dom newElement(String name, String value) {
        Xpp3Dom element = new Xpp3Dom(name);
        element.setValue(value);
        return element;
    }

    public Xpp3Dom newElement(@Nonnull String name, @Nullable MavenProject project) {
        Xpp3Dom projectElt = new Xpp3Dom(name);
        if (project == null) {
            return projectElt;
        }

        projectElt.setAttribute("name", project.getName());
        projectElt.setAttribute("groupId", project.getGroupId());
        projectElt.setAttribute("artifactIdId", project.getArtifactId());
        projectElt.setAttribute("version", project.getVersion());
        return projectElt;
    }

    public Xpp3Dom newElement(@Nonnull String name, @Nullable File file) {
        Xpp3Dom element = new Xpp3Dom(name);
        element.setValue(file == null ? null : file.getAbsolutePath());
        return element;
    }

    public Xpp3Dom newElement(@Nonnull String name, @Nullable Artifact artifact) {
        Xpp3Dom element = new Xpp3Dom(name);
        if (artifact == null) {
            return element;
        }

        element.setAttribute("groupId", artifact.getGroupId());
        element.setAttribute("artifactId", artifact.getArtifactId());
        element.setAttribute("version", artifact.getVersion());
        if (artifact.getClassifier() != null) {
            element.setAttribute("classifier", artifact.getClassifier());
        }
        element.setAttribute("type", artifact.getType());
        element.setAttribute("id", artifact.getId());

        return element;
    }
}
