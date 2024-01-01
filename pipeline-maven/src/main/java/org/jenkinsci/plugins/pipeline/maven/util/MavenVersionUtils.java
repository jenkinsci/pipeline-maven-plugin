package org.jenkinsci.plugins.pipeline.maven.util;

import java.util.function.Predicate;

public class MavenVersionUtils {

    public static Predicate<String> containsMavenVersion() {
        return string -> string != null && string.matches("Apache Maven \\d+\\.\\d+\\.\\d+ \\(.*\\)");
    }

    public static MavenVersion parseMavenVersion(String outputLine) {
        int prefixLength = "Apache Maven ".length();
        int startHashIndex = outputLine.indexOf("(");
        String version = startHashIndex != -1
                ? outputLine.substring(prefixLength, startHashIndex)
                : outputLine.substring(prefixLength);
        return MavenVersion.fromString(version.trim());
    }
}
