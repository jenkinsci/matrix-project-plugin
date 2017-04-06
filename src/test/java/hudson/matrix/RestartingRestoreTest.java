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

import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.*;

import hudson.model.Label;
import hudson.model.Queue;
import hudson.model.Result;
import org.hamcrest.Matchers;
import org.hamcrest.core.AnyOf;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.RestartableJenkinsRule;

import java.util.Arrays;

public class RestartingRestoreTest {

    @Rule public RestartableJenkinsRule r = new RestartableJenkinsRule();

    private String matrixBuildId;

    /**
     * Makes sure that the parent of a MatrixRun survives a restart.
     */
    @Test public void persistenceOfParentInMatrixRun() {
        r.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                MatrixProject p = r.j.createMatrixProject("project");
                p.setAxes(new AxisList(new TextAxis("AXIS", "VALUE")));

                // Schedule and wait for build to finish
                p.scheduleBuild2(0).get();

                MatrixRun run = p.getItem("AXIS=VALUE").getLastBuild();
                ParentBuildAction a = run.getAction(ParentBuildAction.class);
                matrixBuildId = a.getMatrixBuild().getExternalizableId();
            }
        });
        r.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                MatrixProject p = r.j.jenkins.getItemByFullName("project", MatrixProject.class);

                MatrixRun run = p.getItem("AXIS=VALUE").getLastBuild();
                ParentBuildAction a = run.getAction(ParentBuildAction.class);
                String restoredBuildId = a.getMatrixBuild().getExternalizableId();

                assertEquals(matrixBuildId, restoredBuildId);
            }
        });
    }

    @Test public void resumeAllCombinations() throws Exception {
        r.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                MatrixProject project = r.j.createMatrixProject("p");
                project.setConcurrentBuild(true);
                project.setAxes(new AxisList(new LabelAxis("labels", Arrays.asList("foo", "bar"))));

                MatrixBuild parent = project.scheduleBuild2(0).waitForStart();
                assertTrue(parent.isBuilding());
                parent = project.scheduleBuild2(0).waitForStart();
                assertTrue(parent.isBuilding());
                Thread.sleep(1000);

                assertThat(r.j.jenkins.getQueue().getItems(), Matchers.<Queue.Item>arrayWithSize(4));
            }
        });
        r.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                Thread.sleep(10000); // If the jobs is loaded too soon, its builds are never loaded.
                MatrixProject project = r.j.jenkins.getItemByFullName("p", MatrixProject.class);
                MatrixBuild p1 = project.getBuildByNumber(1);
                MatrixBuild p2 = project.getBuildByNumber(2);

                AnyOf<Result> isFailedOrAborted = anyOf(equalTo(Result.ABORTED), equalTo(Result.FAILURE));
                assertThat(p1.getResult(), isFailedOrAborted);
                assertThat(p2.getResult(), isFailedOrAborted);

                r.j.createOnlineSlave(Label.get("foo"));
                r.j.createOnlineSlave(Label.get("bar"));
                r.j.waitUntilNoActivity();

                assertThat(p1.getExactRuns(), Matchers.<MatrixRun>iterableWithSize(2));
                for (MatrixRun run : p1.getExactRuns()) {
                    r.j.assertBuildStatus(Result.SUCCESS, run);
                }
                assertThat(p2.getExactRuns(), Matchers.<MatrixRun>iterableWithSize(2));
                for (MatrixRun run : p2.getExactRuns()) {
                    r.j.assertBuildStatus(Result.SUCCESS, run);
                }
            }
        });
    }
}
