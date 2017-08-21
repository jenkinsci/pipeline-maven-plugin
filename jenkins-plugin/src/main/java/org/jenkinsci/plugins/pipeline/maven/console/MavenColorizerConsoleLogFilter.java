package org.jenkinsci.plugins.pipeline.maven.console;

import hudson.console.ConsoleLogFilter;
import hudson.model.Run;
import hudson.tasks._maven.MavenConsoleAnnotator;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.nio.charset.Charset;

/**
 * Filter to apply {@link MavenConsoleAnnotator} to a pipeline job: pretty print maven output.
 */
public class MavenColorizerConsoleLogFilter extends ConsoleLogFilter implements Serializable {
    private static final long serialVersionUID = 1;
    private final String charset;

    public MavenColorizerConsoleLogFilter(String charset) {
        this.charset = charset;
    }

    @Override
    public OutputStream decorateLogger(Run run, final OutputStream logger)
            throws IOException, InterruptedException {
        return new MavenConsoleAnnotator(logger, Charset.forName(charset));
    }
}
