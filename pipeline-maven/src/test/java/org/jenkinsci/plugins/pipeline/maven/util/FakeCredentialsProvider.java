package org.jenkinsci.plugins.pipeline.maven.util;

import static com.cloudbees.plugins.credentials.CredentialsScope.GLOBAL;
import static java.util.Arrays.asList;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.model.ItemGroup;
import java.util.List;
import org.acegisecurity.Authentication;

public class FakeCredentialsProvider extends CredentialsProvider {

    private String id;
    private String username;
    private String password;
    private boolean usernameIsSecret;

    public FakeCredentialsProvider(String id, String username, String password, boolean usernameIsSecret) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.usernameIsSecret = usernameIsSecret;
    }

    @Override
    public boolean isEnabled(Object context) {
        return true;
    }

    @Override
    public <C extends Credentials> List<C> getCredentials(
            Class<C> type,
            ItemGroup itemGroup,
            Authentication authentication,
            List<DomainRequirement> domainRequirements) {
        UsernamePasswordCredentialsImpl creds;
        try {
            creds = new UsernamePasswordCredentialsImpl(GLOBAL, id, "", username, password);
            creds.setUsernameSecret(usernameIsSecret);
            return (List<C>) asList(creds);
        } catch (FormException e) {
            throw new IllegalStateException("Cannot create fake credentials", e);
        }
    }

    @Override
    public <C extends Credentials> List<C> getCredentials(
            Class<C> type, ItemGroup itemGroup, Authentication authentication) {
        return getCredentials(type, itemGroup, authentication, null);
    }
}
