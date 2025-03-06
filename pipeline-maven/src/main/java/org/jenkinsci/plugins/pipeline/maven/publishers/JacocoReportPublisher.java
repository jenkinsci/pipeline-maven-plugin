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

package org.jenkinsci.plugins.pipeline.maven.publishers;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.AbortException;
import hudson.Extension;
import java.io.IOException;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.pipeline.maven.MavenPublisher;
import org.jenkinsci.plugins.pipeline.maven.Messages;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.kohsuke.stapler.DataBoundConstructor;
import org.w3c.dom.Element;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 *
 * @deprecated since TODO
 */
@Deprecated
public class JacocoReportPublisher extends MavenPublisher {
    private static final long serialVersionUID = 1L;

    @DataBoundConstructor
    public JacocoReportPublisher() {}

    /*
     * <ExecutionEvent type="MojoStarted" class="org.apache.maven.lifecycle.internal.DefaultExecutionEvent" _time="2018-09-24 18:00:21.408">
     *   <project baseDir="/path/to/my-webapp" file="/path/to/my-webapp/pom.xml" groupId="com.mycompany.compliance" name="my-webapp" artifactId="my-webapp" packaging="jar" version="0.0.1-SNAPSHOT">
     *     <build sourceDirectory="/path/to/my-webapp/src/main/java" directory="/path/to/my-webapp/target"/>
     *   </project>
     *   <plugin executionId="default-prepare-agent" goal="prepare-agent" lifecyclePhase="initialize" groupId="org.jacoco" artifactId="jacoco-maven-plugin" version="0.8.2">
     *     <address>${jacoco.address}</address>
     *     <append>${jacoco.append}</append>
     *     <classDumpDir>${jacoco.classDumpDir}</classDumpDir>
     *     <destFile>${jacoco.destFile}</destFile>
     *     <dumpOnExit>${jacoco.dumpOnExit}</dumpOnExit>
     *     <exclClassLoaders>${jacoco.exclClassLoaders}</exclClassLoaders>
     *     <excludes>
     *       <exclude>com/example/exclude/*</exclude>
     *     </excludes>
     *     <inclBootstrapClasses>${jacoco.inclBootstrapClasses}</inclBootstrapClasses>
     *     <inclNoLocationClasses>${jacoco.inclNoLocationClasses}</inclNoLocationClasses>
     *     <jmx>${jacoco.jmx}</jmx>
     *     <output>${jacoco.output}</output>
     *     <pluginArtifactMap>${plugin.artifactMap}</pluginArtifactMap>
     *     <port>${jacoco.port}</port>
     *     <project>${project}</project>
     *     <propertyName>${jacoco.propertyName}</propertyName>
     *     <sessionId>${jacoco.sessionId}</sessionId>
     *     <skip>${jacoco.skip}</skip>
     *   </plugin>
     * </ExecutionEvent>
     * <ExecutionEvent type="MojoSucceeded" class="org.apache.maven.lifecycle.internal.DefaultExecutionEvent" _time="2018-09-24 18:00:22.635">
     *   <project baseDir="/path/to/my-webapp" file="/path/to/my-webapp/pom.xml" groupId="com.mycompany.compliance" name="my-webapp" artifactId="my-webapp" packaging="jar" version="0.0.1-SNAPSHOT">
     *     <build sourceDirectory="/path/to/my-webapp/src/main/java" directory="/path/to/my-webapp/target"/>
     *   </project>
     *   <plugin executionId="default-prepare-agent" goal="prepare-agent" lifecyclePhase="initialize" groupId="org.jacoco" artifactId="jacoco-maven-plugin" version="0.8.2">
     *     <address>${jacoco.address}</address>
     *     <append>${jacoco.append}</append>
     *     <classDumpDir>${jacoco.classDumpDir}</classDumpDir>
     *     <destFile>${jacoco.destFile}</destFile>
     *     <dumpOnExit>${jacoco.dumpOnExit}</dumpOnExit>
     *     <exclClassLoaders>${jacoco.exclClassLoaders}</exclClassLoaders>
     *     <excludes>
     *       <exclude>com/example/exclude/*</exclude>
     *     </excludes>
     *     <inclBootstrapClasses>${jacoco.inclBootstrapClasses}</inclBootstrapClasses>
     *     <inclNoLocationClasses>${jacoco.inclNoLocationClasses}</inclNoLocationClasses>
     *     <jmx>${jacoco.jmx}</jmx>
     *     <output>${jacoco.output}</output>
     *     <pluginArtifactMap>${plugin.artifactMap}</pluginArtifactMap>
     *     <port>${jacoco.port}</port>
     *     <project>${project}</project>
     *     <propertyName>${jacoco.propertyName}</propertyName>
     *     <sessionId>${jacoco.sessionId}</sessionId>
     *     <skip>${jacoco.skip}</skip>
     *   </plugin>
     * </ExecutionEvent>
     */

    /**
     * TODO only collect the jacoco report if unit tests have run
     * @param context
     * @param mavenSpyLogsElt maven spy report. WARNING experimental structure for the moment, subject to change.
     * @throws IOException
     * @throws InterruptedException
     */
    @Override
    public void process(@NonNull StepContext context, @NonNull Element mavenSpyLogsElt)
            throws IOException, InterruptedException {
        throw new AbortException(
                """
        The jacocoPublisher is deprecated as is the Jacoco plugin and you should not use it.
        Alternatively, you should rely on Coverage plugin and see the configuration required at https://plugins.jenkins.io/coverage/#plugin-content-pipeline-example.
        """);
    }

    @Symbol("jacocoPublisher")
    @Extension
    public static class DescriptorImpl extends AbstractHealthAwarePublisher.DescriptorImpl {
        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.publisher_jacoco_report_description();
        }

        @Override
        public int ordinal() {
            return 20;
        }

        @NonNull
        @Override
        public String getSkipFileName() {
            return ".skip-publish-jacoco-results";
        }
    }
}
