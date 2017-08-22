package org.jenkinsci.plugins.pipeline.maven.console;

import hudson.console.LineTransformationOutputStream;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class MaskSecretsOutputStream extends LineTransformationOutputStream {
    private final Pattern secrets;
    private final Charset charset;
    private final OutputStream delegate;

    public MaskSecretsOutputStream(@Nonnull Pattern secrets, @Nonnull OutputStream delegate, @Nonnull Charset charset) {
        this.secrets = secrets;
        this.delegate = delegate;
        this.charset = charset;
    }

    @Override
    protected void eol(byte[] b, int len) throws IOException {
        if (secrets.toString().isEmpty()) {
            // Avoid byte -> char -> byte conversion unless we are actually doing something.
            delegate.write(b, 0, len);
        } else {
            Matcher matcher = secrets.matcher(new String(b, 0, len, charset));
            if (matcher.find()) {
                delegate.write(matcher.replaceAll("****").getBytes(charset));
            } else {
                // Avoid byte -> char -> byte conversion unless we are actually doing something.
                delegate.write(b, 0, len);
            }
        }
    }

    /**
     * Utility method for turning a collection of secret strings into a single {@link String} for pattern compilation.
     * <p>
     * Similar to org.jenkinsci.plugins.credentialsbinding.MultiBinding#getPatternStringForSecrets
     *
     * @param secrets A collection of secret strings
     * @return A {@link String} generated from that collection.
     */
    @Nonnull
    public static String getPatternStringForSecrets(@Nonnull Collection<String> secrets) {
        List<String> sortedByLength =  new ArrayList<>(secrets);
        Collections.sort(sortedByLength, stringLengthComparator);
        StringBuilder regexp = new StringBuilder();

        for (String secret : sortedByLength) {
            if (!secret.isEmpty()) {
                if (regexp.length() > 0) {
                    regexp.append('|');
                }
                regexp.append(Pattern.quote(secret));
            }
        }
        return regexp.toString();
    }

    private static final Comparator<String> stringLengthComparator = new Comparator<String>() {
        @Override
        public int compare(String o1, String o2) {
            return o2.length() - o1.length();
        }
    };
}
