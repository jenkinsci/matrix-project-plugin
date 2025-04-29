/*
 * The MIT License
 *
 * Copyright 2016 EvilTK.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.matrix;

import hudson.matrix.MatrixConfiguration.ParentBuildAction;
import hudson.model.Label;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Result;
import hudson.model.StringParameterDefinition;
import org.hamcrest.Matchers;
import org.hamcrest.core.AnyOf;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsSessionRule;

import java.util.Arrays;

import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class RestartingRestoreTest {

    @Rule public JenkinsSessionRule sessions = new JenkinsSessionRule();

    private String matrixBuildId;

    /**
     * Makes sure that the parent of a MatrixRun survives a restart.
     */
    @Test public void persistenceOfParentInMatrixRun() throws Throwable {
        sessions.then(j -> {
                MatrixProject p = j.createProject(MatrixProject.class, "project");
                p.setAxes(new AxisList(new TextAxis("AXIS", "VALUE")));

                // Schedule and wait for build to finish
                p.scheduleBuild2(0).get();

                MatrixRun run = p.getItem("AXIS=VALUE").getLastBuild();
                ParentBuildAction a = run.getAction(ParentBuildAction.class);
                matrixBuildId = a.getMatrixBuild().getExternalizableId();
        });
        sessions.then(j -> {
                MatrixProject p = j.jenkins.getItemByFullName("project", MatrixProject.class);

                MatrixRun run = p.getItem("AXIS=VALUE").getLastBuild();
                ParentBuildAction a = run.getAction(ParentBuildAction.class);
                String restoredBuildId = a.getMatrixBuild().getExternalizableId();

                assertEquals(matrixBuildId, restoredBuildId);
        });
    }

    @Test public void resumeAllCombinations() throws Throwable {
        sessions.then(j -> {
                MatrixProject project = j.createProject(MatrixProject.class, "p");
                project.setConcurrentBuild(true);
                project.setAxes(new AxisList(new LabelAxis("labels", Arrays.asList("foo", "bar"))));
        });
        resumeBuildAfterRestart();
    }

    @Test public void resumeAllCombinationsWithParameters() throws Throwable {
        sessions.then(j -> {
                MatrixProject project = j.createProject(MatrixProject.class, "p");
                project.setConcurrentBuild(true);
                project.setAxes(new AxisList(new LabelAxis("labels", Arrays.asList("foo", "bar"))));
                project.addProperty(new ParametersDefinitionProperty(new StringParameterDefinition("foo", "bar")));
        });
        resumeBuildAfterRestart();
    }

    private void resumeBuildAfterRestart() throws Throwable {
        sessions.then(j -> {
                MatrixProject project = j.jenkins.getItemByFullName("p", MatrixProject.class);
                MatrixBuild parent = project.scheduleBuild2(0).waitForStart();
                assertTrue(parent.isBuilding());
                parent = project.scheduleBuild2(0).waitForStart();
                assertTrue(parent.isBuilding());
                Thread.sleep(1000);

                assertThat(j.jenkins.getQueue().getItems(), Matchers.arrayWithSize(4));
        });
        sessions.then(j -> {
                Thread.sleep(10000); // If the jobs is loaded too soon, its builds are never loaded.
                MatrixProject project = j.jenkins.getItemByFullName("p", MatrixProject.class);
                MatrixBuild p1 = project.getBuildByNumber(1);
                MatrixBuild p2 = project.getBuildByNumber(2);

                AnyOf<Result> isFailedOrAborted = anyOf(equalTo(Result.ABORTED), equalTo(Result.FAILURE));
                assertThat(p1.getResult(), isFailedOrAborted);
                assertThat(p2.getResult(), isFailedOrAborted);

                j.createOnlineSlave(Label.get("foo"));
                j.createOnlineSlave(Label.get("bar"));
                j.waitUntilNoActivity();

                assertThat(p1.getExactRuns(), Matchers.iterableWithSize(2));
                for (MatrixRun run : p1.getExactRuns()) {
                    j.assertBuildStatus(Result.SUCCESS, run);
                }
                assertThat(p2.getExactRuns(), Matchers.iterableWithSize(2));
                for (MatrixRun run : p2.getExactRuns()) {
                    j.assertBuildStatus(Result.SUCCESS, run);
                }
        });
    }
}
