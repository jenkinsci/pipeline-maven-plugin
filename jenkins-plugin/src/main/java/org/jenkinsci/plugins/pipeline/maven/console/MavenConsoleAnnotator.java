/*
 * The MIT License
 *
 * Copyright (c) 2010, InfraDNA, Inc.
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
package org.jenkinsci.plugins.pipeline.maven.console;

import hudson.console.LineTransformationOutputStream;
import hudson.tasks._maven.Maven3MojoNote;
import hudson.tasks._maven.MavenErrorNote;
import hudson.tasks._maven.MavenMojoNote;
import hudson.tasks._maven.MavenWarningNote;
import java.io.ByteArrayOutputStream;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.regex.Matcher;
import java.util.stream.Stream;
import jenkins.util.JenkinsJVM;

// adapted from version in hudson.tasks._maven

/**
 * Filter {@link OutputStream} that places annotations that marks various Maven outputs.
 */
class MavenConsoleAnnotator extends LineTransformationOutputStream {

    static byte[][] createNotes() {
        JenkinsJVM.checkJenkinsJVM();
        return Stream.of(new MavenMojoNote(), new Maven3MojoNote(), new MavenWarningNote(), new MavenErrorNote()).map(note -> {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try {
                note.encodeTo(baos);
            } catch (IOException x) { // should be impossible
                throw new RuntimeException(x);
            }
            return baos.toByteArray();
        }).toArray(byte[][]::new);
    }

    private final OutputStream out;
    private final Charset charset;
    /** Serialized, signed, and Base64-encoded forms of {@link MavenMojoNote}, {@link Maven3MojoNote}, {@link MavenWarningNote}, and {@link MavenErrorNote}, respectively. */
    private final byte[][] notes;

    MavenConsoleAnnotator(OutputStream out, Charset charset, byte[][] notes) {
        this.out = out;
        this.charset = charset;
        this.notes = notes;
    }

    @Override
    protected void eol(byte[] b, int len) throws IOException {
        String line = charset.decode(ByteBuffer.wrap(b, 0, len)).toString();

        // trim off CR/LF from the end
        line = trimEOL(line);

        Matcher m = MavenMojoNote.PATTERN.matcher(line);
        if (m.matches()) {
            out.write(notes[0]);
        }

        m = Maven3MojoNote.PATTERN.matcher(line);
        if (m.matches()) {
            out.write(notes[1]);
        }

        m = MavenWarningNote.PATTERN.matcher(line);
        if (m.find()) {
            out.write(notes[2]);
        }

        m = MavenErrorNote.PATTERN.matcher(line);
        if (m.find()) {
            out.write(notes[3]);
        }

        out.write(b,0,len);
    }

    @Override
    public void flush() throws IOException {
        out.flush();
    }

    @Override
    public void close() throws IOException {
        super.close();
        out.close();
    }

}
