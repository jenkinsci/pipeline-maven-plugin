package org.jenkinsci.plugins.pipeline.maven.console;

import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey;
import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.common.PasswordCredentials;
import hudson.console.ConsoleLogFilter;
import hudson.model.Run;
import hudson.util.Secret;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import org.jenkinsci.plugins.credentialsbinding.masking.SecretPatterns;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class MaskPasswordsConsoleLogFilter extends ConsoleLogFilter implements Serializable {
    private static final long serialVersionUID = 1;
    private final static Logger LOGGER = Logger.getLogger(MaskPasswordsConsoleLogFilter.class.getName());

    private final Secret secretsAsRegexp;
    private final String charsetName;

    public MaskPasswordsConsoleLogFilter(@Nonnull Collection<String> secrets, @Nonnull String charsetName) {
        this.secretsAsRegexp = Secret.fromString(SecretPatterns.getAggregateSecretPattern(secrets).toString());
        this.charsetName = charsetName;
    }

    @Override
    public OutputStream decorateLogger(Run build, final OutputStream logger) throws IOException, InterruptedException {
        return new SecretPatterns.MaskingOutputStream(logger, () -> Pattern.compile(secretsAsRegexp.getPlainText()), charsetName);
    }

    @Nonnull
    public static MaskPasswordsConsoleLogFilter newMaskPasswordsConsoleLogFilter(@Nonnull Iterable<Credentials> credentials, @Nonnull Charset charset){
        Collection<String> secrets = toString(credentials);
        return new MaskPasswordsConsoleLogFilter(secrets, charset.name());
    }

    @Nonnull
    protected static Collection<String> toString(@Nonnull Iterable<Credentials> credentials) {
        List<String> result = new ArrayList<>();
        for (Credentials creds : credentials) {
            if (creds instanceof PasswordCredentials) {
                PasswordCredentials passwordCredentials = (PasswordCredentials) creds;
                result.add(passwordCredentials.getPassword().getPlainText());
            } else if (creds instanceof SSHUserPrivateKey) {
                SSHUserPrivateKey sshUserPrivateKey = (SSHUserPrivateKey) creds;
                Secret passphrase = sshUserPrivateKey.getPassphrase();
                if (passphrase != null) {
                    result.add(passphrase.getPlainText());
                }
                // omit the private key, there
            } else {
                LOGGER.log(Level.FINE, "Skip masking of unsupported credentials type {0}: {1}", new Object[]{creds.getClass(), creds.getDescriptor().getDisplayName()});
            }
        }
        return result;
    }
}
