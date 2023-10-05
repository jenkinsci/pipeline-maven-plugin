package org.jenkinsci.plugins.pipeline.maven.publishers;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.jenkinsci.plugins.pipeline.maven.MavenDependency;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class DependenciesListerTest {

    private Document doc;

    @BeforeEach
    public void before() throws Exception {
        String mavenSpyLogs = "org/jenkinsci/plugins/pipeline/maven/maven-spy.xml";
        InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(mavenSpyLogs);
        doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in);
    }

    @Test
    public void listArtifactDependencies() throws Exception {
        List<MavenDependency> mavenArtifacts = DependenciesLister.listDependencies(doc.getDocumentElement(), null);

        assertThat(mavenArtifacts).hasSize(2);
        assertThat(mavenArtifacts)
                .anyMatch(dep -> "spring-test".equals(dep.getArtifactId())
                        && "/path/to/spring-petclinic/spring-test/3.2.16.RELEASE/spring-test-3.2.16.RELEASE.jar"
                                .equals(dep.getFile()));
        assertThat(mavenArtifacts)
                .anyMatch(dep -> "spring-core".equals(dep.getArtifactId())
                        && "/path/to/spring-petclinic/3.2.16.RELEASE/spring-core-3.2.16.RELEASE.jar"
                                .equals(dep.getFile()));
    }
}
