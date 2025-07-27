package org.jenkinsci.plugins.pipeline.maven.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.fail;

import hudson.FilePath;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.Arrays;
import java.util.List;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import junit.framework.AssertionFailedError;
import org.jenkinsci.plugins.pipeline.maven.MavenArtifact;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class XmlUtilsTest {
    private DocumentBuilder documentBuilder;

    @BeforeEach
    public void before() throws Exception {
        documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
    }

    @Test
    public void getUniqueChildElementOrNull_two_levels_children() throws Exception {
        String xml = "<a>" + "   <b><c>my text</c></b>" + "   <b2>b2</b2>" + "</a>";
        Element documentElement = toXml(xml);
        Element actualElement = XmlUtils.getUniqueChildElementOrNull(documentElement, "b", "c");
        assertThat(actualElement.getTextContent()).isEqualTo("my text");
    }

    @Test
    public void getUniqueChildElementOrNull_return_null_on_first_level() throws Exception {
        String xml = "<a>" + "   <b><c>my text</c></b>" + "   <b2>b2</b2>" + "</a>";
        Element documentElement = toXml(xml);
        Element actualElement = XmlUtils.getUniqueChildElementOrNull(documentElement, "does-npt-exist", "c");
        assertThat(actualElement).isNull();
    }

    @Test
    public void getUniqueChildElementOrNull_return_null_on_second_level() throws Exception {
        String xml = "<a>" + "   <b><c>my text</c></b>" + "   <b2>b2</b2>" + "</a>";
        Element documentElement = toXml(xml);
        Element actualElement = XmlUtils.getUniqueChildElementOrNull(documentElement, "b", "does-not-exist");
        assertThat(actualElement).isNull();
    }

    @Test
    public void test_getUniqueChildElementOrNull_one_level_child() throws Exception {
        String xml = "<a>" + "   <b><c>my text</c></b>" + "   <b2>b2</b2>" + "</a>";
        Element documentElement = toXml(xml);
        Element actualElement = XmlUtils.getUniqueChildElementOrNull(documentElement, "b");
        assertThat(actualElement.getTextContent()).isEqualTo("my text");
    }

    @Test
    public void test_getExecutionEvents_search_one_type() throws Exception {
        String xml = "<mavenExecution>" + "<ExecutionEvent type='ProjectSucceeded' />" + "</mavenExecution>";
        Element documentElement = toXml(xml);
        List<Element> actualElements = XmlUtils.getExecutionEvents(documentElement, "ProjectSucceeded");
        assertThat(actualElements.size()).isEqualTo(1);
    }

    @Test
    public void test_getExecutionEvents_search_two_types() throws Exception {
        String xml = "<mavenExecution>" + "<ExecutionEvent type='ProjectSucceeded' />" + "</mavenExecution>";
        Element documentElement = toXml(xml);
        List<Element> actualElements =
                XmlUtils.getExecutionEvents(documentElement, "ProjectSucceeded", "ProjectFailed");
        assertThat(actualElements.size()).isEqualTo(1);
    }

    @Test
    public void test_getExecutionEvents_return_empty_searching_one_type() throws Exception {
        String xml = "<mavenExecution>" + "<ExecutionEvent type='ProjectSkipped' />" + "</mavenExecution>";
        Element documentElement = toXml(xml);
        List<Element> actualElements = XmlUtils.getExecutionEvents(documentElement, "ProjectSucceeded");
        assertThat(actualElements.size()).isEqualTo(0);
    }

    @Test
    public void test_getExecutionEvents_return_empty_searching_two_types() throws Exception {
        String xml = "<mavenExecution>" + "<ExecutionEvent type='ProjectSkipped' />" + "</mavenExecution>";
        Element documentElement = toXml(xml);
        List<Element> actualElements =
                XmlUtils.getExecutionEvents(documentElement, "ProjectSucceeded", "ProjectFailed");
        assertThat(actualElements.size()).isEqualTo(0);
    }

    @Test
    public void test_resolveMavenPlaceholders_no_placeholder() throws Exception {
        Element dirElement = toXml("<directory>/aDir</directory>");

        String result = XmlUtils.resolveMavenPlaceholders(dirElement, null);

        assertThat(result).isEqualTo("/aDir");
    }

    @Test
    public void test_resolveMavenPlaceholders_projectBuildDir_not_found() throws Exception {
        Element dirElement = toXml("<directory>/${project.build.directory}</directory>");
        Element projectElement = toXml("<project></project>");

        String result = XmlUtils.resolveMavenPlaceholders(dirElement, projectElement);

        assertThat(result).isNull();
    }

    @Test
    public void test_resolveMavenPlaceholders_projectBuildDir() throws Exception {
        Element dirElement = toXml("<directory>/${project.build.directory}</directory>");
        Element projectElement =
                toXml("<project baseDir=\"projectBaseDir\"><build directory=\"projectBuildDir\"/></project>");

        String result = XmlUtils.resolveMavenPlaceholders(dirElement, projectElement);

        assertThat(result).isEqualTo("/projectBuildDir");
    }

    @Test
    public void test_resolveMavenPlaceholders_projectReportDir_not_found() throws Exception {
        Element dirElement = toXml("<directory>/${project.reporting.outputDirectory}</directory>");
        Element projectElement = toXml("<project></project>");

        String result = XmlUtils.resolveMavenPlaceholders(dirElement, projectElement);

        assertThat(result).isNull();
    }

    @Test
    public void test_resolveMavenPlaceholders_projectReportDir() throws Exception {
        Element dirElement = toXml("<directory>/${project.reporting.outputDirectory}</directory>");
        Element projectElement =
                toXml("<project baseDir=\"projectBaseDir\"><build directory=\"projectBuildDir\"/></project>");

        String result = XmlUtils.resolveMavenPlaceholders(dirElement, projectElement);

        assertThat(result).isEqualTo("/projectBuildDir/site");
    }

    @Test
    public void test_resolveMavenPlaceholders_projectBaseDir_not_found() throws Exception {
        Element dirElement = toXml("<directory>/${basedir}</directory>");
        Element projectElement = toXml("<project></project>");

        String result = XmlUtils.resolveMavenPlaceholders(dirElement, projectElement);

        assertThat(result).isNull();
    }

    @Test
    public void test_resolveMavenPlaceholders_projectBaseDir() throws Exception {
        Element dirElement = toXml("<directory>/${basedir}</directory>");
        Element projectElement =
                toXml("<project baseDir=\"projectBaseDir\"><build directory=\"projectBuildDir\"/></project>");

        String result = XmlUtils.resolveMavenPlaceholders(dirElement, projectElement);

        assertThat(result).isEqualTo("/projectBaseDir");
    }

    @Test
    public void test_resolveMavenPlaceholders_both() throws Exception {
        Element dirElement = toXml("<directory>/${project.build.directory} - ${basedir}</directory>");
        Element projectElement =
                toXml("<project baseDir=\"projectBaseDir\"><build directory=\"projectBuildDir\"/></project>");

        String result = XmlUtils.resolveMavenPlaceholders(dirElement, projectElement);

        assertThat(result).isEqualTo("/projectBuildDir - ${basedir}");
    }

    @Test
    public void test_resolveMavenPlaceholders_relative() throws Exception {
        Element dirElement = toXml("<directory>a/Dir</directory>");
        Element projectElement =
                toXml("<project baseDir=\"projectBaseDir\"><build directory=\"projectBuildDir\"/></project>");

        String result = XmlUtils.resolveMavenPlaceholders(dirElement, projectElement);

        assertThat(result).isEqualTo("projectBaseDir/a/Dir");
    }

    private Element toXml(String xml) throws SAXException, IOException {
        return documentBuilder.parse(new InputSource(new StringReader(xml))).getDocumentElement();
    }

    @Test
    public void concatenate_two_strings() {
        List<String> elements = Arrays.asList("a", "b", "c");
        String actual = XmlUtils.join(elements, ",");
        assertThat(actual).isEqualTo("a,b,c");
    }

    @Test
    public void test_getPathInWorkspace_linux_ok() {
        String workspace = "/path/to/spring-petclinic";
        String absolutePath = "/path/to/spring-petclinic/pom.xml";
        String actual = XmlUtils.getPathInWorkspace(absolutePath, new FilePath(new File(workspace)));
        String expected = "pom.xml";
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void test_getPathInWorkspace_linux_ko() {
        Throwable ex = catchThrowable(() -> {
            String workspace = "/path/to/spring-petclinic";
            String absolutePath = "/different/path/to/spring-petclinic/pom.xml";
            XmlUtils.getPathInWorkspace(absolutePath, new FilePath(new File(workspace)));
        });
        assertThat(ex).isNotNull();
        assertThat(ex).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void test_getPathInWorkspace_linux_with_trailing_file_separator_ok() {
        String workspace = "/path/to/spring-petclinic/";
        String absolutePath = "/path/to/spring-petclinic/pom.xml";
        String actual = XmlUtils.getPathInWorkspace(absolutePath, new FilePath(new File(workspace)));
        String expected = "pom.xml";
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void test_getPathInWorkspace_windows_ok() {
        String workspace = "C:\\path\\to\\spring-petclinic";
        String absolutePath = "C:\\path\\to\\spring-petclinic\\pom.xml";
        String actual = XmlUtils.getPathInWorkspace(absolutePath, new FilePath(new File(workspace)));
        String expected = "pom.xml";
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void test_getPathInWorkspace_windows_with_mixed_separators_ok() {
        String workspace = "C:\\path\\to\\spring-petclinic";
        String absolutePath = "C:\\path\\to\\spring-petclinic\\target/abc.xml";
        String actual = XmlUtils.getPathInWorkspace(absolutePath, new FilePath(new File(workspace)));
        String expected = "target\\abc.xml";
        assertThat(actual).isEqualTo(expected);
    }

    @Issue("JENKINS-45221")
    @Test
    public void test_getPathInWorkspace_windows_mixed_case_ok_JENKINS_45221() {
        // lowercase versus uppercase "d:\"
        String workspace =
                "d:\\jenkins\\workspace\\d.admin_feature_Jenkinsfile-SCSMHLROYAGBAWY5ZNNG6ALR77MVLEH3F3EFF3O7XN3RO5BL6AMA";
        String absolutePath =
                "D:\\jenkins\\workspace\\d.admin_feature_Jenkinsfile-SCSMHLROYAGBAWY5ZNNG6ALR77MVLEH3F3EFF3O7XN3RO5BL6AMA\\admin\\xyz\\target\\pad-admin-xyz-2.4.0-SNAPSHOT-tests.jar";
        String actual = XmlUtils.getPathInWorkspace(absolutePath, new FilePath(new File(workspace)));
        String expected = "admin\\xyz\\target\\pad-admin-xyz-2.4.0-SNAPSHOT-tests.jar";
        assertThat(actual).isEqualTo(expected);
    }

    @Issue("JENKINS-46084")
    @Test
    public void test_getPathInWorkspace_symlink_on_workspace_ok_JENKINS_46084() {
        String workspace = "/var/lib/jenkins/workspace/testjob";
        String absolutePath = "/app/Jenkins/home/workspace/testjob/pom.xml";
        String actual = XmlUtils.getPathInWorkspace(absolutePath, new FilePath(new File(workspace)));
        String expected = "pom.xml";
        assertThat(actual).isEqualTo(expected);
    }

    @Issue("JENKINS-46084")
    @Test
    public void test_getPathInWorkspace_symlink_on_workspace_ok_JENKINS_46084_scenario_2() {
        String workspace = "/var/lib/jenkins/jobs/Test-Pipeline/workspace";
        String absolutePath = "/storage/jenkins/jobs/Test-Pipeline/workspace/pom.xml";
        String actual = XmlUtils.getPathInWorkspace(absolutePath, new FilePath(new File(workspace)));
        String expected = "pom.xml";
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void test_getPathInWorkspace_windows_ko() {
        Throwable ex = catchThrowable(() -> {
            String workspace = "C:\\path\\to\\spring-petclinic";
            String absolutePath = "C:\\different\\path\\to\\spring-petclinic\\pom.xml";
            String actual = XmlUtils.getPathInWorkspace(absolutePath, new FilePath(new File(workspace)));
            fail("should have thrown an IllegalArgumentException, not return " + actual);
        });
        assertThat(ex).isNotNull();
        assertThat(ex).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void test_getPathInWorkspace_windows_with_trailing_file_separator_ok() {
        String workspace = "C:\\path\\to\\spring-petclinic\\";
        String absolutePath = "C:\\path\\to\\spring-petclinic\\pom.xml";
        String actual = XmlUtils.getPathInWorkspace(absolutePath, new FilePath(new File(workspace)));
        String expected = "pom.xml";
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void test_getPathInWorkspace_windows_JENKINS_44088() {
        String workspace = "C:/Jenkins/workspace/maven-pipeline-plugin-test";
        String absolutePath = "C:\\Jenkins\\workspace\\maven-pipeline-plugin-test\\pom.xml";
        String actual = XmlUtils.getPathInWorkspace(absolutePath, new FilePath(new File(workspace)));
        String expected = "pom.xml";
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void test_getPathInWorkspace_macosx_edge_case() {
        // java.lang.IllegalArgumentException:
        // Cannot relativize
        // '/private/var/folders/lq/50t8n2nx7l316pwm8gc_2rt40000gn/T/jenkinsTests.tmp/jenkins3845105900446934883test/workspace/build-on-master-with-tool-provided-maven/pom.xml'
        // relatively to
        // '/var/folders/lq/50t8n2nx7l316pwm8gc_2rt40000gn/T/jenkinsTests.tmp/jenkins3845105900446934883test/workspace/build-on-master-with-tool-provided-maven'

        String workspace =
                "/var/folders/lq/50t8n2nx7l316pwm8gc_2rt40000gn/T/jenkinsTests.tmp/jenkins3845105900446934883test/workspace/build-on-master-with-tool-provided-maven";
        String absolutePath =
                "/private/var/folders/lq/50t8n2nx7l316pwm8gc_2rt40000gn/T/jenkinsTests.tmp/jenkins3845105900446934883test/workspace/build-on-master-with-tool-provided-maven/pom.xml";
        String actual = XmlUtils.getPathInWorkspace(absolutePath, new FilePath(new File(workspace)));
        String expected = "pom.xml";
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void test_getExecutedLifecyclePhases() throws Exception {
        InputStream in = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream("org/jenkinsci/plugins/pipeline/maven/maven-spy-package-jar.xml");
        assertThat(in).isNotNull();
        Element mavenSpyLogs = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(in)
                .getDocumentElement();
        List<String> executedLifecyclePhases = XmlUtils.getExecutedLifecyclePhases(mavenSpyLogs);
        System.out.println(executedLifecyclePhases);
        assertThat(executedLifecyclePhases)
                .contains("process-resources", "compile", "process-test-resources", "test-compile", "test", "package");
    }

    @Test
    public void test_getArtifactDeployedEvent() throws Exception {
        InputStream in = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream("org/jenkinsci/plugins/pipeline/maven/maven-spy-deploy-jar.xml");
        assertThat(in).isNotNull();
        Element mavenSpyLogs = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(in)
                .getDocumentElement();
        List<Element> artifactDeployedEvents = XmlUtils.getArtifactDeployedEvents(mavenSpyLogs);
        assertThat(artifactDeployedEvents.size()).isEqualTo(3);

        Element artifactDeployedEvent = XmlUtils.getArtifactDeployedEvent(
                artifactDeployedEvents, "/path/to/my-jar/target/my-jar-0.5-SNAPSHOT.jar");
        String repositoryUrl = XmlUtils.getUniqueChildElement(artifactDeployedEvent, "repository")
                .getAttribute("url");
        assertThat(repositoryUrl).isEqualTo("https://nexus.beescloud.com/content/repositories/snapshots/");
    }

    @Test
    public void test_getExecutionEventsByPlugin() throws Exception {
        InputStream in = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream("org/jenkinsci/plugins/pipeline/maven/maven-spy-deploy-jar.xml");
        assertThat(in).isNotNull();
        Element mavenSpyLogs = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(in)
                .getDocumentElement();

        List<Element> executionEvents = XmlUtils.getExecutionEventsByPlugin(
                mavenSpyLogs,
                "org.apache.maven.plugins",
                "maven-deploy-plugin",
                "deploy",
                "MojoSucceeded",
                "MojoFailed");

        assertThat(executionEvents.size()).isEqualTo(1);
        Element deployExecutionEvent = executionEvents.get(0);
        assertThat(deployExecutionEvent.getAttribute("type")).isEqualTo("MojoSucceeded");
    }

    @Test
    public void test_listGeneratedArtifacts() throws Exception {
        InputStream in = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream("org/jenkinsci/plugins/pipeline/maven/maven-spy-deploy-jar.xml");
        assertThat(in).isNotNull();
        Element mavenSpyLogs = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(in)
                .getDocumentElement();
        List<MavenArtifact> generatedArtifacts = XmlUtils.listGeneratedArtifacts(mavenSpyLogs, false);
        assertThat(generatedArtifacts.size()).isEqualTo(2); // a jar file and a pom file are generated

        for (MavenArtifact mavenArtifact : generatedArtifacts) {
            assertThat(mavenArtifact.getGroupId()).isEqualTo("com.example");
            assertThat(mavenArtifact.getArtifactId()).isEqualTo("my-jar");
            if ("pom".equals(mavenArtifact.getType())) {
                assertThat(mavenArtifact.getExtension()).isEqualTo("pom");
                assertThat(mavenArtifact.getClassifier()).isNullOrEmpty();
            } else if ("jar".equals(mavenArtifact.getType())) {
                assertThat(mavenArtifact.getExtension()).isEqualTo("jar");
                assertThat(mavenArtifact.getClassifier()).isNullOrEmpty();
            } else {
                throw new AssertionFailedError("Unsupported type for " + mavenArtifact);
            }
        }
    }

    @Test
    public void test_listGeneratedArtifacts_deploy_2_8() throws Exception {
        InputStream in = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream("org/jenkinsci/plugins/pipeline/maven/maven-spy-deploy-2.8.xml");
        assertThat(in).isNotNull();
        Element mavenSpyLogs = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(in)
                .getDocumentElement();

        List<MavenArtifact> generatedArtifacts = XmlUtils.listGeneratedArtifacts(mavenSpyLogs, false);

        assertThat(generatedArtifacts.size()).isEqualTo(2);

        assertThat(generatedArtifacts.get(0).getGroupId()).isEqualTo("com.acme.maven.plugins");
        assertThat(generatedArtifacts.get(0).getArtifactId()).isEqualTo("postgresql-compare-maven-plugin");
        assertThat(generatedArtifacts.get(0).getExtension()).isEqualTo("pom");
        assertThat(generatedArtifacts.get(0).getClassifier()).isNullOrEmpty();
        assertThat(generatedArtifacts.get(0).getBaseVersion()).isEqualTo("1.0.2-SNAPSHOT");
        assertThat(generatedArtifacts.get(0).getType()).isEqualTo("pom");
        assertThat(generatedArtifacts.get(0).getVersion()).isEqualTo("1.0.2-20220904.210621-1");
        assertThat(generatedArtifacts.get(0).isSnapshot()).isTrue();

        assertThat(generatedArtifacts.get(1).getGroupId()).isEqualTo("com.acme.maven.plugins");
        assertThat(generatedArtifacts.get(1).getArtifactId()).isEqualTo("postgresql-compare-maven-plugin");
        assertThat(generatedArtifacts.get(1).getExtension()).isEqualTo("jar");
        assertThat(generatedArtifacts.get(1).getClassifier()).isNullOrEmpty();
        assertThat(generatedArtifacts.get(1).getBaseVersion()).isEqualTo("1.0.2-SNAPSHOT");
        assertThat(generatedArtifacts.get(1).getType()).isEqualTo("maven-plugin");
        assertThat(generatedArtifacts.get(1).getVersion()).isEqualTo("1.0.2-20220904.210621-1");
        assertThat(generatedArtifacts.get(1).isSnapshot()).isTrue();
    }

    @Test
    public void test_listGeneratedArtifacts_deploy_3_0() throws Exception {
        InputStream in = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream("org/jenkinsci/plugins/pipeline/maven/maven-spy-deploy-3.0.xml");
        assertThat(in).isNotNull();
        Element mavenSpyLogs = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(in)
                .getDocumentElement();

        List<MavenArtifact> generatedArtifacts = XmlUtils.listGeneratedArtifacts(mavenSpyLogs, false);

        assertThat(generatedArtifacts.size()).isEqualTo(2);

        assertThat(generatedArtifacts.get(0).getGroupId()).isEqualTo("com.acme.maven.plugins");
        assertThat(generatedArtifacts.get(0).getArtifactId()).isEqualTo("postgresql-compare-maven-plugin");
        assertThat(generatedArtifacts.get(0).getExtension()).isEqualTo("pom");
        assertThat(generatedArtifacts.get(0).getClassifier()).isNullOrEmpty();
        assertThat(generatedArtifacts.get(0).getBaseVersion()).isEqualTo("1.0.2-SNAPSHOT");
        assertThat(generatedArtifacts.get(0).getType()).isEqualTo("pom");
        assertThat(generatedArtifacts.get(0).getVersion()).isEqualTo("1.0.2-20220904.210621-1");
        assertThat(generatedArtifacts.get(0).isSnapshot()).isTrue();

        assertThat(generatedArtifacts.get(1).getGroupId()).isEqualTo("com.acme.maven.plugins");
        assertThat(generatedArtifacts.get(1).getArtifactId()).isEqualTo("postgresql-compare-maven-plugin");
        assertThat(generatedArtifacts.get(1).getExtension()).isEqualTo("jar");
        assertThat(generatedArtifacts.get(1).getClassifier()).isNullOrEmpty();
        assertThat(generatedArtifacts.get(1).getBaseVersion()).isEqualTo("1.0.2-SNAPSHOT");
        assertThat(generatedArtifacts.get(1).getType()).isEqualTo("maven-plugin");
        assertThat(generatedArtifacts.get(1).getVersion()).isEqualTo("1.0.2-20220904.210621-1");
        assertThat(generatedArtifacts.get(1).isSnapshot()).isTrue();
    }

    @Test
    public void test_listGeneratedArtifacts_including_generated_artifacts() throws Exception {
        InputStream in = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream("org/jenkinsci/plugins/pipeline/maven/maven-spy-deploy-jar.xml");
        assertThat(in).isNotNull();
        Element mavenSpyLogs = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(in)
                .getDocumentElement();
        List<MavenArtifact> generatedArtifacts = XmlUtils.listGeneratedArtifacts(mavenSpyLogs, true);
        assertThat(generatedArtifacts.size()).isEqualTo(3); // a jar file and a pom file are generated

        for (MavenArtifact mavenArtifact : generatedArtifacts) {
            assertThat(mavenArtifact.getGroupId()).isEqualTo("com.example");
            assertThat(mavenArtifact.getArtifactId()).isEqualTo("my-jar");
            assertThat(mavenArtifact.getBaseVersion()).isEqualTo("0.5-SNAPSHOT");
            assertThat(mavenArtifact.getVersion()).isEqualTo("0.5-20180304.184830-1");
            assertThat(mavenArtifact.isSnapshot()).isTrue();
            if ("pom".equals(mavenArtifact.getType())) {
                assertThat(mavenArtifact.getExtension()).isEqualTo("pom");
                assertThat(mavenArtifact.getClassifier()).isNullOrEmpty();
            } else if ("jar".equals(mavenArtifact.getType())) {
                assertThat(mavenArtifact.getExtension()).isEqualTo("jar");
                assertThat(mavenArtifact.getClassifier()).isNullOrEmpty();
            } else if ("java-source".equals(mavenArtifact.getType())) {
                assertThat(mavenArtifact.getExtension()).isEqualTo("jar");
                assertThat(mavenArtifact.getClassifier()).isEqualTo("sources");
            } else {
                throw new AssertionFailedError("Unsupported type for " + mavenArtifact);
            }
        }
    }

    @Test
    public void test_listGeneratedArtifacts_includeAttachedArtifacts() throws Exception {
        InputStream in = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream("org/jenkinsci/plugins/pipeline/maven/maven-spy-include-attached-artifacts.xml");
        assertThat(in).isNotNull();
        Element mavenSpyLogs = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(in)
                .getDocumentElement();
        List<MavenArtifact> generatedArtifacts = XmlUtils.listGeneratedArtifacts(mavenSpyLogs, true);
        assertThat(generatedArtifacts.size()).isEqualTo(2); // pom artifact plus 1 attachment

        for (MavenArtifact mavenArtifact : generatedArtifacts) {
            assertThat(mavenArtifact.getGroupId()).isEqualTo("com.example");
            assertThat(mavenArtifact.getArtifactId()).isEqualTo("my-jar");
            assertThat(mavenArtifact.getBaseVersion()).isEqualTo("0.5-SNAPSHOT");
            assertThat(mavenArtifact.getVersion()).isEqualTo("0.5-20180410.070244-14");
            assertThat(mavenArtifact.isSnapshot()).isTrue();
            if ("pom".equals(mavenArtifact.getType())) {
                assertThat(mavenArtifact.getExtension()).isEqualTo("pom");
                assertThat(mavenArtifact.getClassifier()).isNullOrEmpty();
            } else if ("ova".equals(mavenArtifact.getType())) {
                assertThat(mavenArtifact.getExtension()).isEqualTo("ova");
                assertThat(mavenArtifact.getClassifier()).isNullOrEmpty();
            } else {
                throw new AssertionFailedError("Unsupported type for " + mavenArtifact);
            }
        }
    }
}
