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

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class FileMavenEventReporter implements MavenEventReporter {
    File outFile;
    PrintWriter out;

    XMLWriter xmlWriter;

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
                    System.err.println("Failure to create " + reportsFolder.getAbsolutePath());
                    reportsFolder = new File(".");
                }
            }
        }

        String now = new SimpleDateFormat("yyyyMMdd-HHmmss-S").format(new Date());
        outFile = new File(reportsFolder, "maven-spy-" + now + ".log");
        out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(outFile), "UTF-8"));
        xmlWriter = new PrettyPrintXMLWriter(out);
        xmlWriter.startElement("mavenExecution");
        xmlWriter.addAttribute("_time", new Timestamp(System.currentTimeMillis()).toString());
    }

    @Override
    public void print(Object message) {
        XmlWriterUtil.writeComment(xmlWriter, new Timestamp(System.currentTimeMillis()) + " - " + message);
        XmlWriterUtil.writeLineBreak(xmlWriter);
    }

    @Override
    public void print(Xpp3Dom element) {
        element.setAttribute("_time", new Timestamp(System.currentTimeMillis()).toString());
        Xpp3DomWriter.write(xmlWriter, element);
        XmlWriterUtil.writeLineBreak(xmlWriter);
    }

    @Override
    public void close() {
        xmlWriter.endElement();

        out.close();
        System.out.println("JenkinsMavenEventSpy generated " + outFile.getAbsolutePath());
    }
}
