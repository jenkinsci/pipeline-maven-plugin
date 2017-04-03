package jenkins.scm.impl.mock;

import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class SampleDVCSRepoRuleUtilsTest {

    @Test
    public void testCopy() throws Throwable {
        System.out.println("SampleDVCSRepoRuleUtilsTest new File('.'): " + new File(".").getAbsolutePath());

        AbstractSampleDVCSRepoRule sampleDVCSRepoRule = new AbstractSampleDVCSRepoRule() {
            @Override
            public void init() throws Exception {

            }
        };

        sampleDVCSRepoRule.before();

         File mavenProjectRoot = new File("src/test/test-maven-projects/maven-jar-project");
        if (! mavenProjectRoot.exists()) {
            throw new IllegalStateException("Folder '" + mavenProjectRoot.getAbsolutePath() + "' not found");
        }

        SampleDVCSRepoRuleUtils.addFiles(mavenProjectRoot.toPath(), sampleDVCSRepoRule);

        Path dest = sampleDVCSRepoRule.sampleRepo.toPath();
        List<String> expected = Arrays.asList("src", "pom.xml", "target");
        for(Path path : Files.newDirectoryStream(dest)) {
            Path relativePath = dest.relativize(path);
            String actual = relativePath.toString();
            Assert.assertTrue(actual + " in " + expected, expected.contains(actual));
        }
    }
}
