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

package org.jenkinsci.plugins.pipeline.maven.service;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.model.Item;
import java.io.PrintStream;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class ServiceLoggerImpl implements ServiceLogger {

    private final Logger logger = Logger.getLogger(ServiceLoggerImpl.class.getName());

    @NonNull
    private final PrintStream stdOut, stdErr;

    @Nullable
    String prefix;

    public ServiceLoggerImpl(@NonNull PrintStream stdOut, @NonNull PrintStream stdErr, @Nullable String prefix) {
        this.stdOut = stdOut;
        this.stdErr = stdErr;
        this.prefix = prefix;
    }

    @Override
    public boolean isLoggable(Level level) {
        return logger.isLoggable(level);
    }

    @Override
    public void log(Level level, String message) {
        if (!isLoggable(level)) {
            return;
        }
        StringBuilder messageToWrite = new StringBuilder();
        if (prefix != null && !prefix.isEmpty()) {
            messageToWrite.append(prefix).append(" ");
        }
        messageToWrite.append(level).append(" ").append(message);
        stdOut.println(messageToWrite.toString());
    }

    @Override
    public String modelHyperlinkNoteEncodeTo(@Nullable Item item) {
        return item == null ? "#null#" : item.getFullDisplayName();
    }
}
