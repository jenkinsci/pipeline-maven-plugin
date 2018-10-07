package org.jenkinsci.plugins.pipeline.maven.service;

import hudson.console.ModelHyperlinkNote;
import hudson.model.Item;

import java.util.logging.Level;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Our own logger to output in the build logs or in the CLI stdout
 *
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
interface ServiceLogger {
    boolean isLoggable(Level level);

    void log(Level level, String message);

    @Nonnull
    String modelHyperlinkNoteEncodeTo(@Nullable Item item);
}
