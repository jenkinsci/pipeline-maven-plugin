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

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;

import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
@ThreadSafe
public class OutputStreamEventReporter implements MavenEventReporter {

    @GuardedBy("this")
    final PrintWriter out;
    @GuardedBy("this")
    final XMLWriter xmlWriter;

    public OutputStreamEventReporter(OutputStream out) {
        this(new OutputStreamWriter(out, StandardCharsets.UTF_8));
    }

    public OutputStreamEventReporter(Writer out) {
        if (out instanceof PrintWriter) {
            this.out = (PrintWriter) out;
        } else {
            this.out = new PrintWriter(out, true);
        }
        this.xmlWriter = new PrettyPrintXMLWriter(out);
        xmlWriter.startElement("mavenExecution");

    }

    @Override
    public synchronized void print(Object message) {
        String comment = new Timestamp(System.currentTimeMillis()) + " - " + message;
        XmlWriterUtil.writeComment(xmlWriter, comment);
        XmlWriterUtil.writeLineBreak(xmlWriter);

        out.flush();
    }

    @Override
    public synchronized void print(Xpp3Dom element) {
        Xpp3DomWriter.write(xmlWriter, element);
        XmlWriterUtil.writeLineBreak(xmlWriter);

        out.flush();
    }

    @Override
    public synchronized void close() {
        xmlWriter.endElement();
        out.flush();
    }
}
