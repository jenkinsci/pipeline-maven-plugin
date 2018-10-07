package org.jenkinsci.plugins.pipeline.maven.service;

import hudson.model.Item;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class ServiceLoggerImpl implements ServiceLogger {

    private final Logger logger = Logger.getLogger(ServiceLoggerImpl.class.getName());

    @Nonnull
    private final PrintStream stdOut, stdErr;
    @Nullable
    String prefix;

    public ServiceLoggerImpl(@Nonnull PrintStream stdOut, @Nonnull PrintStream stdErr, @Nullable String prefix) {
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
        if (prefix != null && ! prefix.isEmpty()) {
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
