package org.jenkinsci.plugins.pipeline.maven;

import static java.util.stream.Collectors.joining;

import java.util.List;

import org.jenkinsci.plugins.pipeline.maven.publishers.MavenPipelinePublisherException;

public class MavenPipelineException extends RuntimeException {

    private static final long serialVersionUID = 4164091766147994893L;

    public MavenPipelineException(Throwable cause) {
        super("Exception occured in withMaven pipeline step: " + cause.getMessage(), cause);
    }

    public MavenPipelineException(List<MavenPipelinePublisherException> publishersExceptions) {
        super(publishersExceptions.size() + " exceptions occured within the publishers of the withMaven pipeline step:\n"
                + publishersExceptions.stream().map(e -> {
                    StringBuilder builder = new StringBuilder("- ");
                    builder.append(e.getMessage());
                    if (e.getCause() != null) {
                        builder.append(": ").append(e.getCause().getMessage());
                    }
                    return builder.toString();
                }).collect(joining()));
    }
}
