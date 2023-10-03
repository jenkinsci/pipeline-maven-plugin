package org.jenkinsci.plugins.pipeline.maven.console;

import hudson.console.ConsoleLogFilter;
import hudson.model.Run;
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
    private byte[][] notes = MavenConsoleAnnotator.createNotes();

    public MavenColorizerConsoleLogFilter(String charset) {
        this.charset = charset;
    }

    private Object readResolve() {
        if (notes == null) { // old program.dat
            notes = MavenConsoleAnnotator.createNotes();
        }
        return this;
    }

    @Override
    public OutputStream decorateLogger(Run run, final OutputStream logger) throws IOException, InterruptedException {
        return new MavenConsoleAnnotator(logger, Charset.forName(charset), notes);
    }
}
