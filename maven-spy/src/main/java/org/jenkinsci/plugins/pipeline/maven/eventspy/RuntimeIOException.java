package org.jenkinsci.plugins.pipeline.maven.eventspy;

/**
 * Subclass of {@link RuntimeException} to propagate and {@link java.io.IOException}
 *
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class RuntimeIOException extends RuntimeException {
    public RuntimeIOException(Throwable cause) {
        super(cause);
    }

    public RuntimeIOException(String message) {
        super(message);
    }

    public RuntimeIOException(String message, Throwable cause) {
        super(message, cause);
    }
}
