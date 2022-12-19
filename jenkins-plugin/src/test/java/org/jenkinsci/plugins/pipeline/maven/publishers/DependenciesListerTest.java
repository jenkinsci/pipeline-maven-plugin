package org.jenkinsci.plugins.pipeline.maven.publishers;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsIterableContaining.hasItem;

import java.io.InputStream;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.jenkinsci.plugins.pipeline.maven.MavenDependency;
import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Document;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class DependenciesListerTest {
    Document doc;

    @Before
    public void before() throws Exception {
        String mavenSpyLogs = "org/jenkinsci/plugins/pipeline/maven/maven-spy.xml";
        InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(mavenSpyLogs);
        doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in);
    }

    @Test
    public void listArtifactDependencies() throws Exception {
        List<MavenDependency> mavenArtifacts = DependenciesLister.listDependencies(doc.getDocumentElement(), null);

        assertThat(mavenArtifacts.size(), is(2));
        assertThat(mavenArtifacts, hasItem(new BaseMatcher<MavenDependency>() {
            @Override
            public boolean matches(Object actual) {
                if (!(actual instanceof MavenDependency)) {
                    return false;
                }
                MavenDependency dep = (MavenDependency) actual;
                return "spring-test".equals(dep.getArtifactId())
                        && "/path/to/spring-petclinic/spring-test/3.2.16.RELEASE/spring-test-3.2.16.RELEASE.jar"
                                .equals(dep.getFile());
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("spring-test dependency");
            }
        }));
        assertThat(mavenArtifacts, hasItem(new BaseMatcher<MavenDependency>() {
            @Override
            public boolean matches(Object actual) {
                if (!(actual instanceof MavenDependency)) {
                    return false;
                }
                MavenDependency dep = (MavenDependency) actual;
                return "spring-core".equals(dep.getArtifactId())
                        && "/path/to/spring-petclinic/3.2.16.RELEASE/spring-core-3.2.16.RELEASE.jar"
                                .equals(dep.getFile());
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("spring-core dependency");
            }
        }));
    }
}
