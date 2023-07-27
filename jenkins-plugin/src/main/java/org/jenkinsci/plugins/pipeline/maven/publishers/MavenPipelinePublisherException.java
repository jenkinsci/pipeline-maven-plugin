package org.jenkinsci.plugins.pipeline.maven.publishers;

public class MavenPipelinePublisherException extends RuntimeException {

    private static final long serialVersionUID = -5374242713614539146L;

    private String name;

    private String step;

    public MavenPipelinePublisherException(String name, String step, Throwable cause) {
        super(name + " faced exception while " + step, cause);
        this.name = name;
        this.step = step;
    }

    public String getName() {
        return name;
    }

    public String getStep() {
        return step;
    }
}
