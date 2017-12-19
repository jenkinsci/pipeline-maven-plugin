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

package org.jenkinsci.plugins.pipeline.maven.eventspy.reporter;

import org.codehaus.plexus.util.xml.PrettyPrintXMLWriter;
import org.codehaus.plexus.util.xml.XMLWriter;
import org.codehaus.plexus.util.xml.XmlWriterUtil;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.Xpp3DomWriter;
import org.jenkinsci.plugins.pipeline.maven.eventspy.RuntimeIOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
@ThreadSafe
public class FileMavenEventReporter implements MavenEventReporter {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * report file gets initially created with a "maven-spy-*.log.tmp" file extension and gets renamed "maven-spy-*.log"
     * at the end of the execution
     */
    @GuardedBy("this")
    File outFile;
    @GuardedBy("this")
    PrintWriter out;
    @GuardedBy("this")
    XMLWriter xmlWriter;
    /**
     * used to support multiple calls of {@link #close()} }
     */
    boolean isOpen;

    public FileMavenEventReporter() throws IOException {
        String reportsFolderPath = System.getProperty("org.jenkinsci.plugins.pipeline.maven.reportsFolder");
        File reportsFolder;
        if (reportsFolderPath == null) {
            reportsFolder = new File(".");
        } else {
            reportsFolder = new File(reportsFolderPath);
            if (reportsFolder.exists()) {

            } else {
                boolean created = reportsFolder.mkdirs();
                if (!created) {
                    reportsFolder = new File(".");
                    logger.warn("[jenkins-event-spy] Failure to create folder '" + reportsFolder.getAbsolutePath() +
                            "', generate report in '" + reportsFolder.getAbsolutePath() + "'");
                }
            }
        }

        String now = new SimpleDateFormat("yyyyMMdd-HHmmss-S").format(new Date());
        outFile = File.createTempFile("maven-spy-" + now, ".log.tmp", reportsFolder);

        out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(outFile), "UTF-8"));
        xmlWriter = new PrettyPrintXMLWriter(out);
        xmlWriter.startElement("mavenExecution");
        xmlWriter.addAttribute("_time", new Timestamp(System.currentTimeMillis()).toString());

        try {
            logger.info("[jenkins-event-spy] Generate " + outFile.getCanonicalPath() + " ...");
        } catch (IOException e) {
            throw new RuntimeIOException(e);
        }
        isOpen = true;
    }

    @Override
    public synchronized void print(Object message) {
        XmlWriterUtil.writeComment(xmlWriter, new Timestamp(System.currentTimeMillis()) + " - " + message);
        XmlWriterUtil.writeLineBreak(xmlWriter);
    }

    @Override
    public synchronized void print(Xpp3Dom element) {
        element.setAttribute("_time", new Timestamp(System.currentTimeMillis()).toString());
        Xpp3DomWriter.write(xmlWriter, element);
        XmlWriterUtil.writeLineBreak(xmlWriter);
    }

    @Override
    public synchronized void close() {
        if (isOpen) {
            xmlWriter.endElement();
            out.close();

            isOpen = false;

            String filePath = outFile.getAbsolutePath();
            filePath = filePath.substring(0, filePath.length() - ".tmp".length());
            File finalFile = new File(filePath);

            boolean result = outFile.renameTo(finalFile);
            if (result == false) {
                logger.warn("[jenkins-event-spy] Failure to rename " + outFile + " into " + finalFile);
            } else {
                outFile = finalFile;
            }
            try {
                logger.info("[jenkins-event-spy] Generated " + outFile.getCanonicalPath());
            } catch (IOException e) {
                throw new RuntimeIOException(e);
            }
        }
    }

    /**
     * Visible for test
     */
    public synchronized File getFinalFile() {
        return outFile;
    }
}
