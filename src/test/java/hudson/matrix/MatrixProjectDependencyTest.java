package hudson.matrix;

import hudson.model.AbstractProject;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.tasks.BuildTrigger;
import hudson.util.RunList;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Stefan Wolf
 */
@WithJenkins
class MatrixProjectDependencyTest {

    /**
     * Checks if the MatrixProject adds and Triggers downstream Projects via
     * the DependencyGraph
     */
    @Test
    void matrixProjectTriggersDependencies(JenkinsRule j) throws Exception {
        MatrixProject matrixProject = j.createProject(MatrixProject.class);
        FreeStyleProject freestyleProject = j.createFreeStyleProject();
        matrixProject.getPublishersList().add(new BuildTrigger(freestyleProject.getName(), false));

        j.jenkins.rebuildDependencyGraph();

        j.buildAndAssertSuccess(matrixProject);
        j.waitUntilNoActivity();

        RunList<FreeStyleBuild> builds = freestyleProject.getBuilds();
        assertEquals(1, builds.size(), "There should only be one FreestyleBuild");
        FreeStyleBuild build = builds.iterator().next();
        assertEquals(Result.SUCCESS, build.getResult());
        List<AbstractProject> downstream = j.jenkins.getDependencyGraph().getDownstream(matrixProject);
        assertTrue(downstream.contains(freestyleProject));
    }
}
