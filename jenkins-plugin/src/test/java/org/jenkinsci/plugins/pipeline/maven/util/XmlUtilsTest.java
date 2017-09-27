package org.jenkinsci.plugins.pipeline.maven.util;

import hudson.FilePath;
import org.hamcrest.CoreMatchers;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.Arrays;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class XmlUtilsTest {
    private DocumentBuilder documentBuilder;

    @Before
    public void before() throws Exception {
        documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
    }

    @Test
    public void getUniqueChildElementOrNull_two_levels_children() throws Exception {
        String xml =
                "<a>" +
                        "   <b><c>my text</c></b>" +
                        "   <b2>b2</b2>" +
                        "</a>";
        Element documentElement = toXml(xml);
        Element actualElement = XmlUtils.getUniqueChildElementOrNull(documentElement, "b", "c");
        Assert.assertThat(actualElement.getTextContent(), CoreMatchers.is("my text"));
    }

    @Test
    public void getUniqueChildElementOrNull_return_null_on_first_level() throws Exception {
        String xml =
                "<a>" +
                        "   <b><c>my text</c></b>" +
                        "   <b2>b2</b2>" +
                        "</a>";
        Element documentElement = toXml(xml);
        Element actualElement = XmlUtils.getUniqueChildElementOrNull(documentElement, "does-npt-exist", "c");
        Assert.assertThat(actualElement, CoreMatchers.nullValue());
    }

    @Test
    public void getUniqueChildElementOrNull_return_null_on_second_level() throws Exception {
        String xml =
                "<a>" +
                        "   <b><c>my text</c></b>" +
                        "   <b2>b2</b2>" +
                        "</a>";
        Element documentElement = toXml(xml);
        Element actualElement = XmlUtils.getUniqueChildElementOrNull(documentElement, "b", "does-not-exist");
        Assert.assertThat(actualElement, CoreMatchers.nullValue());
    }

    @Test
    public void test_getUniqueChildElementOrNull_one_level_child() throws Exception {
        String xml =
                "<a>" +
                        "   <b><c>my text</c></b>" +
                        "   <b2>b2</b2>" +
                        "</a>";
        Element documentElement = toXml(xml);
        Element actualElement = XmlUtils.getUniqueChildElementOrNull(documentElement, "b");
        Assert.assertThat(actualElement.getTextContent(), CoreMatchers.is("my text"));
    }

    @Test
    public void test_getExecutionEvents_search_one_type() throws Exception {
        String xml =
                "<mavenExecution>" +
                        "<ExecutionEvent type='ProjectSucceeded' />" +
                        "</mavenExecution>";
        Element documentElement = toXml(xml);
        List<Element> actualElements = XmlUtils.getExecutionEvents(documentElement, "ProjectSucceeded");
        Assert.assertThat(actualElements.size(), CoreMatchers.is(1));
    }

    @Test
    public void test_getExecutionEvents_search_two_types() throws Exception {
        String xml =
                "<mavenExecution>" +
                        "<ExecutionEvent type='ProjectSucceeded' />" +
                        "</mavenExecution>";
        Element documentElement = toXml(xml);
        List<Element> actualElements = XmlUtils.getExecutionEvents(documentElement, "ProjectSucceeded", "ProjectFailed");
        Assert.assertThat(actualElements.size(), CoreMatchers.is(1));
    }

    @Test
    public void test_getExecutionEvents_return_empty_searching_one_type() throws Exception {
        String xml =
                "<mavenExecution>" +
                        "<ExecutionEvent type='ProjectSkipped' />" +
                        "</mavenExecution>";
        Element documentElement = toXml(xml);
        List<Element> actualElements = XmlUtils.getExecutionEvents(documentElement, "ProjectSucceeded");
        Assert.assertThat(actualElements.size(), CoreMatchers.is(0));
    }

    @Test
    public void test_getExecutionEvents_return_empty_searching_two_types() throws Exception {
        String xml =
                "<mavenExecution>" +
                        "<ExecutionEvent type='ProjectSkipped' />" +
                        "</mavenExecution>";
        Element documentElement = toXml(xml);
        List<Element> actualElements = XmlUtils.getExecutionEvents(documentElement, "ProjectSucceeded", "ProjectFailed");
        Assert.assertThat(actualElements.size(), CoreMatchers.is(0));
    }

    private Element toXml(String xml) throws SAXException, IOException {
        return documentBuilder.parse(new InputSource(new StringReader(xml))).getDocumentElement();
    }

    @Test
    public void concatenate_two_strings(){
        List<String> elements = Arrays.asList("a", "b", "c");
        String actual = XmlUtils.join(elements, ",");
        Assert.assertThat(actual, CoreMatchers.is("a,b,c"));
    }

    @Test
    public void test_getPathInWorkspace_linux_ok(){
        String workspace = "/path/to/spring-petclinic";
        String absolutePath = "/path/to/spring-petclinic/pom.xml";
        String actual = XmlUtils.getPathInWorkspace(absolutePath, new FilePath(new File(workspace)));
        String expected = "pom.xml";
        Assert.assertThat(actual, CoreMatchers.is(expected));
    }

    @Test(expected = IllegalArgumentException.class)
    public void test_getPathInWorkspace_linux_ko(){
        String workspace = "/path/to/spring-petclinic";
        String absolutePath = "/different/path/to/spring-petclinic/pom.xml";
        String actual = XmlUtils.getPathInWorkspace(absolutePath, new FilePath(new File(workspace)));
        String expected = "pom.xml";
        Assert.assertThat(actual, CoreMatchers.is(expected));
    }

    @Test
    public void test_getPathInWorkspace_linux_with_trailing_file_separator_ok(){
        String workspace = "/path/to/spring-petclinic/";
        String absolutePath = "/path/to/spring-petclinic/pom.xml";
        String actual = XmlUtils.getPathInWorkspace(absolutePath, new FilePath(new File(workspace)));
        String expected = "pom.xml";
        Assert.assertThat(actual, CoreMatchers.is(expected));
    }

    @Test
    public void test_getPathInWorkspace_windows_ok(){
        String workspace = "C:\\path\\to\\spring-petclinic";
        String absolutePath = "C:\\path\\to\\spring-petclinic\\pom.xml";
        String actual = XmlUtils.getPathInWorkspace(absolutePath, new FilePath(new File(workspace)));
        String expected = "pom.xml";
        Assert.assertThat(actual, CoreMatchers.is(expected));
    }

    @Test
    public void test_getPathInWorkspace_windows_with_mixed_separators_ok(){
        String workspace = "C:\\path\\to\\spring-petclinic";
        String absolutePath = "C:\\path\\to\\spring-petclinic\\target/abc.xml";
        String actual = XmlUtils.getPathInWorkspace(absolutePath, new FilePath(new File(workspace)));
        String expected = "target\\abc.xml";
        Assert.assertThat(actual, CoreMatchers.is(expected));
    }

    @Issue("JENKINS-45221")
    @Test
    public void test_getPathInWorkspace_windows_mixed_case_ok_JENKINS_45221() {
        // lowercase versus uppercase "d:\"
        String workspace = "d:\\jenkins\\workspace\\d.admin_feature_Jenkinsfile-SCSMHLROYAGBAWY5ZNNG6ALR77MVLEH3F3EFF3O7XN3RO5BL6AMA";
        String absolutePath = "D:\\jenkins\\workspace\\d.admin_feature_Jenkinsfile-SCSMHLROYAGBAWY5ZNNG6ALR77MVLEH3F3EFF3O7XN3RO5BL6AMA\\admin\\xyz\\target\\pad-admin-xyz-2.4.0-SNAPSHOT-tests.jar";
        String actual = XmlUtils.getPathInWorkspace(absolutePath, new FilePath(new File(workspace)));
        String expected = "admin\\xyz\\target\\pad-admin-xyz-2.4.0-SNAPSHOT-tests.jar";
        Assert.assertThat(actual, CoreMatchers.is(expected));
    }

    @Issue("JENKINS-46084")
    @Test
    public void test_getPathInWorkspace_symlink_on_workspace_ok_JENKINS_46084() {
        String workspace = "/var/lib/jenkins/workspace/testjob";
        String absolutePath = "/app/Jenkins/home/workspace/testjob/pom.xml";
        String actual = XmlUtils.getPathInWorkspace(absolutePath, new FilePath(new File(workspace)));
        String expected = "pom.xml";
        Assert.assertThat(actual, CoreMatchers.is(expected));
    }

    @Issue("JENKINS-46084")
    @Test
    public void test_getPathInWorkspace_symlink_on_workspace_ok_JENKINS_46084_scenario_2() {
        String workspace = "/var/lib/jenkins/jobs/Test-Pipeline/workspace";
        String absolutePath = "/storage/jenkins/jobs/Test-Pipeline/workspace/pom.xml";
        String actual = XmlUtils.getPathInWorkspace(absolutePath, new FilePath(new File(workspace)));
        String expected = "pom.xml";
        Assert.assertThat(actual, CoreMatchers.is(expected));
    }

    @Test(expected = IllegalArgumentException.class)
    public void test_getPathInWorkspace_windows_ko(){
        String workspace = "C:\\path\\to\\spring-petclinic";
        String absolutePath = "C:\\different\\path\\to\\spring-petclinic\\pom.xml";
        String actual = XmlUtils.getPathInWorkspace(absolutePath, new FilePath(new File(workspace)));
        Assert.fail("should have thrown an IllegalArgumentException, not return " + actual);
    }

    @Test
    public void test_getPathInWorkspace_windows_with_trailing_file_separator_ok(){
        String workspace = "C:\\path\\to\\spring-petclinic\\";
        String absolutePath = "C:\\path\\to\\spring-petclinic\\pom.xml";
        String actual = XmlUtils.getPathInWorkspace(absolutePath, new FilePath(new File(workspace)));
        String expected = "pom.xml";
        Assert.assertThat(actual, CoreMatchers.is(expected));
    }

    @Test
    public void test_getPathInWorkspace_windows_JENKINS_44088(){
        String workspace = "C:/Jenkins/workspace/maven-pipeline-plugin-test";
        String absolutePath = "C:\\Jenkins\\workspace\\maven-pipeline-plugin-test\\pom.xml";
        String actual = XmlUtils.getPathInWorkspace(absolutePath, new FilePath(new File(workspace)));
        String expected = "pom.xml";
        Assert.assertThat(actual, CoreMatchers.is(expected));
    }

    @Test
    public void test_getPathInWorkspace_macosx_edge_case(){
        // java.lang.IllegalArgumentException:
        // Cannot relativize '/private/var/folders/lq/50t8n2nx7l316pwm8gc_2rt40000gn/T/jenkinsTests.tmp/jenkins3845105900446934883test/workspace/build-on-master-with-tool-provided-maven/pom.xml'
        // relatively to '/var/folders/lq/50t8n2nx7l316pwm8gc_2rt40000gn/T/jenkinsTests.tmp/jenkins3845105900446934883test/workspace/build-on-master-with-tool-provided-maven'

        String workspace = "/var/folders/lq/50t8n2nx7l316pwm8gc_2rt40000gn/T/jenkinsTests.tmp/jenkins3845105900446934883test/workspace/build-on-master-with-tool-provided-maven";
        String absolutePath = "/private/var/folders/lq/50t8n2nx7l316pwm8gc_2rt40000gn/T/jenkinsTests.tmp/jenkins3845105900446934883test/workspace/build-on-master-with-tool-provided-maven/pom.xml";
        String actual = XmlUtils.getPathInWorkspace(absolutePath, new FilePath(new File(workspace)));
        String expected = "pom.xml";
        Assert.assertThat(actual, CoreMatchers.is(expected));
    }

    @Test
    public void test_getExecutedLifecyclePhases() throws Exception {
        InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream("org/jenkinsci/plugins/pipeline/maven/maven-spy-package-jar.xml");
        in.getClass(); // check non null
        Element mavenSpyLogs = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in).getDocumentElement();
        List<String> executedLifecyclePhases = XmlUtils.getExecutedLifecyclePhases(mavenSpyLogs);
        System.out.println(executedLifecyclePhases);
        Assert.assertThat(executedLifecyclePhases, Matchers.contains("process-resources", "compile", "process-test-resources", "test-compile", "test", "package"));
    }
}
