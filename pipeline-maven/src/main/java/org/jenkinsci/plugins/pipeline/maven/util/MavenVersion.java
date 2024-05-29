package org.jenkinsci.plugins.pipeline.maven.util;

import java.util.Objects;

public class MavenVersion {

    public static final MavenVersion UNKNOWN = new MavenVersion(-1, -1, -1);

    public static MavenVersion fromString(String str) {
        try {
            String[] parts = str.split("[.\\-]");
            return new MavenVersion(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        } catch (Exception ex) {
            throw new IllegalArgumentException("'" + str + "' is not a valid maven version", ex);
        }
    }

    private int major;

    private int minor;

    private int increment;

    public MavenVersion(int major, int minor, int increment) {
        this.major = major;
        this.minor = minor;
        this.increment = increment;
    }

    public boolean isAtLeast(int wantedMajor, int wantedMinor) {
        return major > wantedMajor || (major == wantedMajor && minor >= wantedMinor);
    }

    public boolean isAtLeast(int wantedMajor, int wantedMinor, int wantedIncrement) {
        return major > wantedMajor
                || (major == wantedMajor && minor > wantedMinor)
                || (major == wantedMajor && minor == wantedMinor && increment >= wantedIncrement);
    }

    @Override
    public String toString() {
        return major == -1 ? "UNKNOWN" : (major + "." + minor + "." + increment);
    }

    @Override
    public int hashCode() {
        return Objects.hash(increment, major, minor);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        MavenVersion other = (MavenVersion) obj;
        return increment == other.increment && major == other.major && minor == other.minor;
    }
}
