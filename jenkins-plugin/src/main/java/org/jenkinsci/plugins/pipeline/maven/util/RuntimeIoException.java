package org.jenkinsci.plugins.pipeline.maven.util;

/**
 * We prefer {@link RuntimeException} to catched exceptions.
 *
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class RuntimeIoException extends RuntimeException {
    public RuntimeIoException() {
        super();
    }

    public RuntimeIoException(String message) {
        super(message);
    }

    public RuntimeIoException(String message, Throwable cause) {
        super(message, cause);
    }

    public RuntimeIoException(Throwable cause) {
        super(cause);
    }

    public RuntimeIoException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
