package org.jenkinsci.plugins.pipeline.maven.util;

import hudson.FilePath;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class FileUtils {

    public static boolean isAbsolutePath(@NonNull String path) {
        if (isWindows(path)) {
            if (path.length() > 3 && path.charAt(1) == ':' && path.charAt(2) == '\\') {
                // windows path such as "C:\path\to\..."
                return true;
            } else // Microsoft Windows UNC mount ("\\myserver\myfolder")
                if (path.length() > 3 && path.charAt(1) == ':' && path.charAt(2) == '/') {
                // nasty windows path such as "C:/path/to/...". See JENKINS-44088
                return true;
            } else return path.length() > 2 && path.charAt(0) == '\\' && path.charAt(1) == '\\';
        } else {
            // see java.io.UnixFileSystem.prefixLength()
            return path.charAt(0) == '/';
        }

    }

    public static boolean isWindows(@NonNull FilePath path) {
        return isWindows(path.getRemote());
    }

    public static boolean isWindows(@NonNull String path) {
        if (path.length() > 3 && path.charAt(1) == ':' && path.charAt(2) == '\\') {
            // windows path such as "C:\path\to\..."
            return true;
        } else if (path.length() > 3 && path.charAt(1) == ':' && path.charAt(2) == '/') {
            // nasty windows path such as "C:/path/to/...". See JENKINS-44088
            return true;
        } else if (path.length() > 2 && path.charAt(0) == '\\' && path.charAt(1) == '\\') {
            // Microsoft Windows UNC mount ("\\myserver\myfolder")
            return true;
        }
        int indexOfSlash = path.indexOf('/');
        int indexOfBackSlash = path.indexOf('\\');
        if (indexOfSlash == -1) {
            return true;
        } else if (indexOfBackSlash == -1) {
            return false;
        } else return indexOfSlash >= indexOfBackSlash;
    }
}
