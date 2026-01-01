package org.jenkinsci.plugins.pipeline.maven.util;

import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MavenVersionUtils {

    private static final Pattern PATTERN = Pattern.compile(".*Apache Maven (\\d+\\.\\d+\\.\\d+)(.*)( \\(.*\\))?");

    public static Predicate<String> containsMavenVersion() {
        return string -> string != null && PATTERN.matcher(string).matches();
    }

    public static MavenVersion parseMavenVersion(String outputLine) {
        Matcher m = PATTERN.matcher(outputLine);
        return m.matches() && m.groupCount() > 1 ? MavenVersion.fromString(m.group(1)) : null;
    }
}
