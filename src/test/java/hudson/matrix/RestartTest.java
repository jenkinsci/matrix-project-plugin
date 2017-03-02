/*
 * The MIT License
 *
 * Copyright (c) Red Hat, Inc.
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

import hudson.model.Label;
import hudson.model.Queue;
import hudson.model.Result;
import org.hamcrest.Matchers;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.RestartableJenkinsRule;

import java.util.Arrays;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * @author ogondza.
 */
public class RestartTest {

    @Rule
    public RestartableJenkinsRule j = new RestartableJenkinsRule();

    private MatrixProject project;

    @Test
    public void resumeAllCombinations() throws Exception {
        j.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                project = j.j.createMatrixProject("p");
                project.setConcurrentBuild(true);
                project.setAxes(new AxisList(new LabelAxis("labels", Arrays.asList("foo", "bar"))));

                MatrixBuild parent = project.scheduleBuild2(0).waitForStart();
                assertTrue(parent.isBuilding());
                parent = project.scheduleBuild2(0).waitForStart();
                assertTrue(parent.isBuilding());
                Thread.sleep(1000);

                assertThat(j.j.jenkins.getQueue().getItems(), Matchers.<Queue.Item>arrayWithSize(4));
            }
        });
        j.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                MatrixBuild p1 = project.getBuildByNumber(1);
                j.j.assertBuildStatus(Result.FAILURE, p1);
                MatrixBuild p2 = project.getBuildByNumber(1);
                j.j.assertBuildStatus(Result.FAILURE, p2);

                j.j.createOnlineSlave(Label.get("foo"));
                j.j.createOnlineSlave(Label.get("bar"));
                j.j.waitUntilNoActivity();

                assertThat(p1.getExactRuns(), Matchers.<MatrixRun>iterableWithSize(2));
                for (MatrixRun run : p1.getExactRuns()) {
                    j.j.assertBuildStatus(Result.SUCCESS, run);
                }
                assertThat(p2.getExactRuns(), Matchers.<MatrixRun>iterableWithSize(2));
                for (MatrixRun run : p2.getExactRuns()) {
                    j.j.assertBuildStatus(Result.SUCCESS, run);
                }
            }
        });
    }
}
