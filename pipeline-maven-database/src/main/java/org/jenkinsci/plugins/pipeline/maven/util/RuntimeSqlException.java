package org.jenkinsci.plugins.pipeline.maven.util;

/**
 * We prefer {@link RuntimeException} to catched exceptions.
 *
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class RuntimeSqlException extends RuntimeException {
    static final long serialVersionUID = 1L;

    public RuntimeSqlException() {
        super();
    }

    public RuntimeSqlException(String message) {
        super(message);
    }

    public RuntimeSqlException(String message, Throwable cause) {
        super(message, cause);
    }

    public RuntimeSqlException(Throwable cause) {
        super(cause);
    }

    protected RuntimeSqlException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
