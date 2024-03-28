package org.jenkinsci.plugins.pipeline.maven.util;

import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.jupiter.api.condition.OS;

public class Conditions {

    public static boolean isLinuxAndDockerSocketExists() {
        return OS.current() == OS.LINUX && Files.exists(Paths.get("/var/run/docker.sock"));
    }
}
