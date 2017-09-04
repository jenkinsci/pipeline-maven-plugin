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

package org.jenkinsci.plugins.pipeline.maven.eventspy;

import java.io.File;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.maven.eventspy.EventSpy;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.FileUtils;
import org.hamcrest.CoreMatchers;
import org.jenkinsci.plugins.pipeline.maven.eventspy.reporter.FileMavenEventReporter;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class JenkinsMavenEventSpyMTTest {

    MavenProject project;

    @Before
    public void before() throws Exception {
        System.setProperty("org.jenkinsci.plugins.pipeline.maven.reportsFolder", "target");
    }

    private JenkinsMavenEventSpy createSpy() throws Exception {
        FileMavenEventReporter reporter = new FileMavenEventReporter();

        JenkinsMavenEventSpy spy = new JenkinsMavenEventSpy(reporter) {
            @Override
            protected boolean isEventSpyDisabled() {
                return false;
            }
        };

        spy.init(new EventSpy.Context() {
            @Override
            public Map<String, Object> getData() {
                return new HashMap();
            }
        });

        MavenXpp3Reader mavenXpp3Reader = new MavenXpp3Reader();
        InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream("org/jenkinsci/plugins/pipeline/maven/eventspy/pom.xml");

        Assert.assertThat(in, CoreMatchers.notNullValue());
        Model model = mavenXpp3Reader.read(in);
        project = new MavenProject(model);
        project.setGroupId(model.getGroupId());
        project.setArtifactId(model.getArtifactId());
        project.setVersion(model.getVersion());
        project.setName(model.getName());
        return spy;
    }

    @Test //Issue JENKINS-46579
    public void testMavenExecutionMTSpyReporters() throws Exception {
        int numThreads = 100;
        final CyclicBarrier barrier = new CyclicBarrier(numThreads + 1); //we need to also stop the test thread (current)
        final AtomicInteger counter = new AtomicInteger(0);
        final ExceptionHolder exceptionHolder = new ExceptionHolder();

        final Vector<JenkinsMavenEventSpy> spyList = new Vector<JenkinsMavenEventSpy>(numThreads);

        //Test some concurrency around persisted state. Launch 100 threads from which 1/3 will try to change the
        //persisted state, the rest will read it a couple of times.
        for (int i = 0; i < numThreads; i++) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        barrier.await();
                        //Thread.sleep(RandomUtils.nextInt(0, 500));
                        JenkinsMavenEventSpy spy = createSpy();
                        spyList.add(spy);
                        DefaultMavenExecutionRequest request = new DefaultMavenExecutionRequest();
                        request.setPom(new File("path/to/pom.xml"));
                        request.setGoals(Arrays.asList("clean", "source:jar", "deploy"));

                        for (int i = 0; i < 100; i++) {
                            spy.onEvent(request);
                        }

                    } catch (Exception e) {
                        exceptionHolder.e = e;
                    }
                    counter.incrementAndGet();
                }
            }).start();
        }
        barrier.await();

        long start = System.currentTimeMillis();

        while (true) {
            int c = counter.get();
            long finish = System.currentTimeMillis();
            if ((finish - start) > 2000L) { // 2 seconds is the limit
                //ThreadDumps.threadDumpModern(System.out); //FIXME
                Assert.fail("Threads taking too long to finish " + (finish - start) + "ms");
            }

            if (c >= numThreads) {
                System.out.println("==== All threads finished");
                System.out.println("==== Time: " + (finish - start) + " ms.");
                break;
            }
            System.out.println("==== Waiting for threads to finish. Counter " + c + ". Waiting 200 ms.");
            Thread.sleep(200);
        }

        if (exceptionHolder.e != null) {
            // fail the test is there was some exception on threads
            throw exceptionHolder.e;
        }

        for (JenkinsMavenEventSpy spy : spyList) {
            spy.close();
            File outFile = ((FileMavenEventReporter) spy.getReporter()).getFinalFile();
            System.out.println("Generated file: " + outFile);
            String actual = FileUtils.fileRead(outFile);
            Assert.assertThat(actual, CoreMatchers.containsString("MavenExecutionRequest"));
            validateXMLDocument(outFile);
        }
    }

    @Test //Issue JENKINS-46579
    public void testMavenExecutionMTRequestsSingleSpyReporter() throws Exception {
        int numThreads = 100;
        final CyclicBarrier barrier = new CyclicBarrier(numThreads + 1); //we need to also stop the test thread (current)
        final AtomicInteger counter = new AtomicInteger(0);
        final ExceptionHolder exceptionHolder = new ExceptionHolder();

        final JenkinsMavenEventSpy spy = createSpy();

        //Test some concurrency around persisted state. Launch 100 threads from which 1/3 will try to change the
        //persisted state, the rest will read it a couple of times.
        for (int i = 0; i < numThreads; i++) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        barrier.await();
                        //Thread.sleep(RandomUtils.nextInt(0, 500));

                        DefaultMavenExecutionRequest request = new DefaultMavenExecutionRequest();
                        request.setPom(new File("path/to/pom.xml"));
                        request.setGoals(Arrays.asList("clean", "source:jar", "deploy"));
                        for (int i = 0; i < 100; i++) {
                            spy.onEvent(request);
                        }

                    } catch (Exception e) {
                        exceptionHolder.e = e;
                    }
                    counter.incrementAndGet();
                }
            }).start();
        }
        barrier.await();

        long start = System.currentTimeMillis();

        while (true) {
            int c = counter.get();
            long finish = System.currentTimeMillis();
            if ((finish - start) > 2000L) { // 2 seconds is the limit
                //ThreadDumps.threadDumpModern(System.out); //FIXME
                Assert.fail("Threads taking too long to finish " + (finish - start) + "ms");
            }

            if (c >= numThreads) {
                System.out.println("==== All threads finished");
                System.out.println("==== Time: " + (finish - start) + " ms.");
                break;
            }
            System.out.println("==== Waiting for threads to finish. Counter " + c + ". Waiting 200 ms.");
            Thread.sleep(200);
        }

        if (exceptionHolder.e != null) {
            // fail the test is there was some exception on threads
            throw exceptionHolder.e;
        }

        spy.close();
        File outFile = ((FileMavenEventReporter) spy.getReporter()).getFinalFile();
        System.out.println("Generated file: " + outFile);
        String actual = FileUtils.fileRead(outFile);
        Assert.assertThat(actual, CoreMatchers.containsString("MavenExecutionRequest"));
        validateXMLDocument(outFile);
    }

    DocumentBuilder documentBuilder;

    public void validateXMLDocument(File document) {
        if (documentBuilder == null) {
            try {
                documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            } catch (ParserConfigurationException e) {
                throw new IllegalStateException("Failure to create a DocumentBuilder", e);
            }
        }

        try {
            documentBuilder.parse(document);
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail("Failed to parse spylog: " + document + " error:" + e);
        }

    }

    public static class ExceptionHolder {
        public Exception e;
    }
}
