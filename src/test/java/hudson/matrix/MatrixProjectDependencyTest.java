package hudson.matrix;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import hudson.model.AbstractProject;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.tasks.BuildTrigger;
import hudson.util.RunList;

/**
 * @author Stefan Wolf
 */
public class MatrixProjectDependencyTest {

    @Rule public JenkinsRule j = new JenkinsRule();

	/**
	 * Checks if the MatrixProject adds and Triggers downstream Projects via
	 * the DependencyGraph 
	 */
	@Test public void matrixProjectTriggersDependencies() throws Exception {
		MatrixProject matrixProject = j.createProject(MatrixProject.class);
		FreeStyleProject freestyleProject = j.createFreeStyleProject();
		matrixProject.getPublishersList().add(new BuildTrigger(freestyleProject.getName(), false));
		
		j.jenkins.rebuildDependencyGraph();
		
		j.buildAndAssertSuccess(matrixProject);
		j.waitUntilNoActivity();
		
		RunList<FreeStyleBuild> builds = freestyleProject.getBuilds();
		assertEquals("There should only be one FreestyleBuild", 1, builds.size());
		FreeStyleBuild build = builds.iterator().next();
		assertEquals(Result.SUCCESS, build.getResult());
		List<AbstractProject> downstream = j.jenkins.getDependencyGraph().getDownstream(matrixProject);
		assertTrue(downstream.contains(freestyleProject));		
	}
}
