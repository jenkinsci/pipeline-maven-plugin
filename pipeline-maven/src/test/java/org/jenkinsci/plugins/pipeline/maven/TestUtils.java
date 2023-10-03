package org.jenkinsci.plugins.pipeline.maven;

import static org.springframework.util.ReflectionUtils.findField;
import static org.springframework.util.ReflectionUtils.findMethod;
import static org.springframework.util.ReflectionUtils.invokeMethod;
import static org.springframework.util.ReflectionUtils.makeAccessible;
import static org.springframework.util.ReflectionUtils.setField;

import hudson.model.Run;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class TestUtils {

    public static Collection<String> artifactsToArtifactsFileNames(
            Iterable<Run<WorkflowJob, WorkflowRun>.Artifact> artifacts) {
        List<String> result = new ArrayList<>();
        for (Run.Artifact artifact : artifacts) {
            result.add(artifact.getFileName());
        }
        return result;
    }

    public static void runBeforeMethod(Object rule) {
        runMethod(rule, "before");
    }

    public static void runAfterMethod(Object rule) {
        runMethod(rule, "after");
    }

    private static void runMethod(Object rule, String name) {
        Method method = findMethod(rule.getClass(), name);
        makeAccessible(method);
        invokeMethod(method, rule);
    }

    public static void setFieldValue(Object dest, String name, Object value) {
        Field field = findField(dest.getClass(), name);
        makeAccessible(field);
        setField(field, dest, value);
    }
}
