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

import com.github.olivergondza.dumpling.factory.JvmRuntimeFactory;
import com.github.olivergondza.dumpling.model.jvm.JvmRuntime;
import hudson.matrix.MatrixConfiguration.ParentBuildAction;
import hudson.model.Label;
import hudson.model.Queue;
import hudson.model.Result;
import org.hamcrest.Matchers;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.RestartableJenkinsRule;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

// I was not able to get 2 tests working in single RestartableJenkinsRule no matter what I do
public class RestartingRestoreTest2 {

    @Rule public RestartableJenkinsRule r = new RestartableJenkinsRule();

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
                JvmRuntime before = new JvmRuntimeFactory().currentRuntime();
                JvmRuntime after = new JvmRuntimeFactory().currentRuntime();
                System.out.println(before.getThreads());
                System.out.println('=');
                System.out.println(after.getThreads());
                MatrixProject project = r.j.jenkins.getItemByFullName("p", MatrixProject.class);
                MatrixBuild p1 = project.getBuildByNumber(1);
                r.j.assertBuildStatus(Result.FAILURE, p1);
                MatrixBuild p2 = project.getBuildByNumber(1);
                r.j.assertBuildStatus(Result.FAILURE, p2);

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
