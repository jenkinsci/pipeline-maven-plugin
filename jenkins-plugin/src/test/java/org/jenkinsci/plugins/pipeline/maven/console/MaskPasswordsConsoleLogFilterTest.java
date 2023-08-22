package org.jenkinsci.plugins.pipeline.maven.console;

import static com.cloudbees.plugins.credentials.CredentialsScope.GLOBAL;
import static java.util.Arrays.asList;

import java.util.ArrayList;
import java.util.List;

import org.acegisecurity.Authentication;
import org.jenkinsci.plugins.configfiles.GlobalConfigFiles;
import org.jenkinsci.plugins.configfiles.maven.GlobalMavenSettingsConfig;
import org.jenkinsci.plugins.configfiles.maven.job.MvnGlobalSettingsProvider;
import org.jenkinsci.plugins.configfiles.maven.security.ServerCredentialMapping;
import org.jenkinsci.plugins.pipeline.maven.AbstractIntegrationTest;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.jvnet.hudson.test.Issue;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;

import hudson.ExtensionList;
import hudson.model.ItemGroup;
import hudson.model.Result;
import jenkins.model.Jenkins;
import jenkins.mvn.GlobalMavenConfig;

public class MaskPasswordsConsoleLogFilterTest extends AbstractIntegrationTest {

    @Issue("SECURITY-3257")
    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    public void should_hide_server_username_and_password(boolean usernameIsSecret) throws Exception {
        //@formatter:off
        String pipelineScript = "node() {\n" +
            "    withMaven(traceability: true, globalMavenSettingsConfig: 'maven-global-config-test') {\n" +
            "        sh 'cat \"$GLOBAL_MVN_SETTINGS\"'\n" +
            "    }\n" +
            "}";

        //@formatter:off
        String mavenGlobalSettings = "<?xml version='1.0' encoding='UTF-8'?>\n" +
            "<settings \n" +
            "        xmlns='http://maven.apache.org/SETTINGS/1.0.0'\n" +
            "        xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance'\n" +
            "        xsi:schemaLocation='http://maven.apache.org/SETTINGS/1.0.0 http://maven.apache.org/xsd/settings-1.0.0.xsd'>\n" +
            "    <servers>\n" +
            "       <server>\n" +
            "           <id>server-id</id>\n" +
            "       </server>\n" +
            "    </servers>\n" +
            "</settings>\n";
        //@formatter:on

        ExtensionList<CredentialsProvider> extensionList = Jenkins.getInstance().getExtensionList(CredentialsProvider.class);
        extensionList.add(extensionList.size(), new FakeCredentialsProvider(usernameIsSecret));

        List<ServerCredentialMapping> mappings = new ArrayList<>();
        mappings.add(new ServerCredentialMapping("server-id", "creds-id"));
        GlobalMavenSettingsConfig mavenGlobalSettingsConfig = new GlobalMavenSettingsConfig("maven-global-config-test", "maven-global-config-test", "",
                mavenGlobalSettings, true, mappings);

        GlobalConfigFiles.get().save(mavenGlobalSettingsConfig);
        GlobalMavenConfig.get().setGlobalSettingsProvider(new MvnGlobalSettingsProvider(mavenGlobalSettingsConfig.id));

        try {
            WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, "hide-credentials");
            pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
            WorkflowRun build = jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));
            jenkinsRule.assertLogContains(
                    "[withMaven] using Maven global settings.xml 'maven-global-config-test' with Maven servers credentials provided by Jenkins (replaceAll: true): [mavenServerId: 'server-id', jenkinsCredentials: 'creds-id']",
                    build);
            jenkinsRule.assertLogContains("<id>server-id</id>", build);
            if (usernameIsSecret) {
                jenkinsRule.assertLogNotContains("<username>aUser</username>", build);
            } else {
                jenkinsRule.assertLogContains("<username>aUser</username>", build);
            }
            jenkinsRule.assertLogNotContains("<password>aPass</password>", build);
        } finally {
            GlobalMavenConfig.get().setSettingsProvider(null);
        }
    }

    private static class FakeCredentialsProvider extends CredentialsProvider {
        private boolean usernameIsSecret;

        public FakeCredentialsProvider(boolean usernameIsSecret) {
            this.usernameIsSecret = usernameIsSecret;
        }

        @Override
        public boolean isEnabled(Object context) {
            return true;
        }

        @Override
        public <C extends Credentials> List<C> getCredentials(Class<C> type, ItemGroup itemGroup, Authentication authentication,
                List<DomainRequirement> domainRequirements) {
            UsernamePasswordCredentialsImpl creds = new UsernamePasswordCredentialsImpl(GLOBAL, "creds-id", "", "aUser", "aPass");
            creds.setUsernameSecret(usernameIsSecret);
            return (List<C>) asList(creds);
        }

        @Override
        public <C extends Credentials> List<C> getCredentials(Class<C> type, ItemGroup itemGroup, Authentication authentication) {
            return getCredentials(type, itemGroup, authentication, null);
        }
    }

}
