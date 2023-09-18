package org.jenkinsci.plugins.pipeline.maven.cause;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.jenkinsci.plugins.pipeline.maven.MavenArtifact;
import org.junit.jupiter.api.Test;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class MavenDependencyCauseHelperTest {

    @Test
    public void isSameCause_singleArtifact_noBaseVersion_sameSnapshot_false() {
        MavenArtifact firstArtifact = new MavenArtifact("com.example:my-jar:jar:1.0-SNAPSHOT");
        MavenArtifact secondArtifact = new MavenArtifact("com.example:my-jar:jar:1.0-SNAPSHOT");

        List<MavenArtifact> matchingArtifacts = MavenDependencyCauseHelper.isSameCause(new MavenDependencyTestCause(firstArtifact),
                new MavenDependencyTestCause(secondArtifact));

        assertThat(matchingArtifacts).isEmpty();
    }

    @Test
    public void isSameCause_singleArtifact_noBaseVersion_false() {
        MavenArtifact firstArtifact = new MavenArtifact("com.example:my-jar:jar:1.0-SNAPSHOT");
        MavenArtifact secondArtifact = new MavenArtifact("com.example:my-jar:jar:1.1-SNAPSHOT");

        List<MavenArtifact> matchingArtifacts = MavenDependencyCauseHelper.isSameCause(new MavenDependencyTestCause(firstArtifact),
                new MavenDependencyTestCause(secondArtifact));

        assertThat(matchingArtifacts).isEmpty();
    }

    @Test
    public void isSameCause_singleArtifact_withBaseVersion_true() {
        MavenArtifact firstArtifact = new MavenArtifact("com.example:my-jar:jar:1.0-20100529-1213-1");
        firstArtifact.setBaseVersion("1.0-SNAPSHOT");
        MavenArtifact secondArtifact = new MavenArtifact("com.example:my-jar:jar:1.0-20100529-1213-1");
        secondArtifact.setBaseVersion("1.0-SNAPSHOT");

        List<MavenArtifact> matchingArtifacts = MavenDependencyCauseHelper.isSameCause(new MavenDependencyTestCause(firstArtifact),
                new MavenDependencyTestCause(secondArtifact));

        assertThat(matchingArtifacts).isNotEmpty();
    }

    @Test
    public void isSameCause_singleArtifact_withBaseVersion_false() {
        MavenArtifact firstArtifact = new MavenArtifact("com.example:my-jar:jar:1.0-20100529-1213-1");
        firstArtifact.setBaseVersion("1.0-SNAPSHOT");
        MavenArtifact secondArtifact = new MavenArtifact("com.example:my-jar:jar:1.0-20100530-2101-3");
        secondArtifact.setBaseVersion("1.0-SNAPSHOT");

        List<MavenArtifact> matchingArtifacts = MavenDependencyCauseHelper.isSameCause(new MavenDependencyTestCause(firstArtifact),
                new MavenDependencyTestCause(secondArtifact));

        assertThat(matchingArtifacts).isEmpty();
    }

    @Test
    public void isSameCause_singleArtifact_mixedBaseVersion_false() {
        MavenArtifact firstArtifact = new MavenArtifact("com.example:my-jar:jar:1.0-20100529-1213-1");
        firstArtifact.setBaseVersion("1.0-SNAPSHOT");
        MavenArtifact secondArtifact = new MavenArtifact("com.example:my-jar:jar:1.0-20100530-2101-1");

        List<MavenArtifact> matchingArtifacts = MavenDependencyCauseHelper.isSameCause(new MavenDependencyTestCause(firstArtifact),
                new MavenDependencyTestCause(secondArtifact));

        assertThat(matchingArtifacts).isEmpty();
    }

    @Test
    public void isSameCause_singleArtifact_multiClassifiers_on_firstCause_withBaseVersion_true() {
        MavenArtifact firstArtifact = new MavenArtifact("com.example:my-jar:jar:1.0-20100529-1213-1");
        firstArtifact.setBaseVersion("1.0-SNAPSHOT");
        MavenArtifact firstArtifactSources = new MavenArtifact("com.example:my-jar:jar:1.0-20100529-1213-1");
        firstArtifactSources.setBaseVersion("1.0-SNAPSHOT");
        firstArtifactSources.setClassifier("sources");

        MavenArtifact secondArtifact = new MavenArtifact("com.example:my-jar:jar:1.0-20100529-1213-1");
        secondArtifact.setBaseVersion("1.0-SNAPSHOT");

        List<MavenArtifact> matchingArtifacts = MavenDependencyCauseHelper.isSameCause(new MavenDependencyTestCause(firstArtifact, firstArtifactSources),
                new MavenDependencyTestCause(secondArtifact));

        assertThat(matchingArtifacts).isNotEmpty();
    }

    @Test
    public void isSameCause_singleArtifact_multiClassifiers_on_secondCause_withBaseVersion_true() {
        MavenArtifact firstArtifact = new MavenArtifact("com.example:my-jar:jar:1.0-20100529-1213-1");
        firstArtifact.setBaseVersion("1.0-SNAPSHOT");

        MavenArtifact secondArtifact = new MavenArtifact("com.example:my-jar:jar:1.0-20100529-1213-1");
        secondArtifact.setBaseVersion("1.0-SNAPSHOT");
        MavenArtifact secondArtifactSources = new MavenArtifact("com.example:my-jar:jar:1.0-20100529-1213-1");
        secondArtifactSources.setBaseVersion("1.0-SNAPSHOT");
        secondArtifactSources.setClassifier("sources");

        List<MavenArtifact> matchingArtifacts = MavenDependencyCauseHelper.isSameCause(new MavenDependencyTestCause(firstArtifact),
                new MavenDependencyTestCause(secondArtifact, secondArtifactSources));

        assertThat(matchingArtifacts).isNotEmpty();
    }

    @Test
    public void isSameCause_multiArtifact_multiClassifiers_on_firstCause_withBaseVersion_true() {
        MavenArtifact firstArtifact = new MavenArtifact("com.example:my-jar:jar:1.0-20100529-1213-1");
        firstArtifact.setBaseVersion("1.0-SNAPSHOT");
        MavenArtifact firstArtifactSources = new MavenArtifact("com.example:my-jar:jar:1.0-20100529-1213-1");
        firstArtifactSources.setBaseVersion("1.0-SNAPSHOT");
        firstArtifactSources.setClassifier("sources");

        MavenArtifact secondArtifact = new MavenArtifact("com.example:my-second-jar:jar:1.0-20100529-1214-1");
        secondArtifact.setBaseVersion("1.0-SNAPSHOT");

        MavenArtifact sameAsFirstArtifact = new MavenArtifact("com.example:my-jar:jar:1.0-20100529-1213-1");
        sameAsFirstArtifact.setBaseVersion("1.0-SNAPSHOT");

        List<MavenArtifact> matchingArtifacts = MavenDependencyCauseHelper.isSameCause(
                new MavenDependencyTestCause(firstArtifact, firstArtifactSources, secondArtifact), new MavenDependencyTestCause(sameAsFirstArtifact));

        assertThat(matchingArtifacts).isNotEmpty();
    }

    @Test
    public void isSameCause_multiArtifact_multiClassifiers_on_secondCause_withBaseVersion_true() {
        MavenArtifact firstArtifact = new MavenArtifact("com.example:my-jar:jar:1.0-20100529-1213-1");
        firstArtifact.setBaseVersion("1.0-SNAPSHOT");

        MavenArtifact secondArtifact = new MavenArtifact("com.example:my-second-jar:jar:1.0-20100529-1214-1");
        secondArtifact.setBaseVersion("1.0-SNAPSHOT");

        MavenArtifact sameAsFirstArtifact = new MavenArtifact("com.example:my-jar:jar:1.0-20100529-1213-1");
        sameAsFirstArtifact.setBaseVersion("1.0-SNAPSHOT");

        MavenArtifact sameAsFirstArtifactSources = new MavenArtifact("com.example:my-jar:jar:1.0-20100529-1213-1");
        sameAsFirstArtifactSources.setBaseVersion("1.0-SNAPSHOT");
        sameAsFirstArtifactSources.setClassifier("sources");

        List<MavenArtifact> matchingArtifacts = MavenDependencyCauseHelper.isSameCause(new MavenDependencyTestCause(firstArtifact),
                new MavenDependencyTestCause(sameAsFirstArtifact, sameAsFirstArtifactSources, secondArtifact));

        assertThat(matchingArtifacts).isNotEmpty();
    }

    static class MavenDependencyTestCause extends MavenDependencyAbstractCause {
        MavenDependencyTestCause(MavenArtifact artifact) {
            super();
            this.setMavenArtifacts(Collections.singletonList(artifact));
        }

        MavenDependencyTestCause(MavenArtifact... artifact) {
            super();
            this.setMavenArtifacts(Arrays.asList(artifact));
        }

        @Override
        public String getShortDescription() {
            return "MavenDependencyTestCause: " + getMavenArtifactsDescription();
        }

        @Override
        public String toString() {
            return getMavenArtifactsDescription();
        }
    }
}
