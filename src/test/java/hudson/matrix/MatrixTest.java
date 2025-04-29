/*
 * The MIT License
 *
 * Copyright (c) 2010-2011, Alan Harder
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

import hudson.Functions;
import hudson.matrix.helper.DynamicTestAxis;
import hudson.model.Item;
import hudson.model.queue.QueueTaskFuture;
import hudson.security.AuthorizationMatrixProperty;
import hudson.security.ProjectMatrixAuthorizationStrategy;
import org.acegisecurity.context.SecurityContextHolder;
import org.apache.commons.lang.reflect.FieldUtils;
import org.htmlunit.xml.XmlPage;
import org.jenkinsci.plugins.scriptsecurity.sandbox.RejectedAccessException;
import org.jenkinsci.plugins.scriptsecurity.scripts.ScriptApproval;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Alan Harder
 */
@WithJenkins
class MatrixTest {

    /**
     * Test that spaces are encoded as %20 for project name, axis name and axis value.
     */
    @Test
    void spaceInUrl(JenkinsRule j) {
        MatrixProject mp = new MatrixProject("matrix test");
        MatrixConfiguration mc = new MatrixConfiguration(mp, Combination.fromString("foo bar=baz bat"));
        assertEquals("job/matrix%20test/", mp.getUrl());
        assertEquals("job/matrix%20test/foo%20bar=baz%20bat/", mc.getUrl());
    }

    /**
     * Test that project level permissions apply to child configurations as well.
     */
    @Issue("JENKINS-9293")
    @Test
    void configurationACL(JenkinsRule j) throws Exception {
        j.jenkins.setAuthorizationStrategy(new ProjectMatrixAuthorizationStrategy());
        MatrixProject mp = j.createProject(MatrixProject.class);
        mp.setAxes(new AxisList(new Axis("foo", "a", "b")));
        MatrixConfiguration mc = mp.getItem("foo=a");
        assertNotNull(mc);
        SecurityContextHolder.clearContext();
        assertFalse(mc.getACL().hasPermission(Item.READ));
        mp.addProperty(new AuthorizationMatrixProperty(
                Collections.singletonMap(Item.READ, Collections.singleton("anonymous"))));
        // Project-level permission should apply to single configuration too:
        assertTrue(mc.getACL().hasPermission(Item.READ));
    }

    @Test
    void api(JenkinsRule j) throws Exception {
        MatrixProject project = j.createProject(MatrixProject.class);
        project.setAxes(new AxisList(
                new Axis("FOO", "abc", "def"),
                new Axis("BAR", "uvw", "xyz")));
        XmlPage xml = j.createWebClient().goToXml(project.getUrl() + "api/xml");
        assertEquals(4, xml.getByXPath("//matrixProject/activeConfiguration").size());
    }

    @Issue("JENKINS-27162")
    @Test
    void completedLogging(JenkinsRule j) throws Exception {
        MatrixProject project = j.createProject(MatrixProject.class);
        project.setAxes(new AxisList(
                new Axis("axis", "a", "b")
        ));
        ((DefaultMatrixExecutionStrategyImpl) project.getExecutionStrategy())
                .setTouchStoneCombinationFilter("axis == 'a'")
        ;

        MatrixBuild build = project.scheduleBuild2(0).get();
        j.assertLogContains("test0 » a completed with result SUCCESS", build);
        j.assertLogContains("test0 » b completed with result SUCCESS", build);
    }

    @Issue("SECURITY-125")
    @Test
    void combinationFilterSecurity(JenkinsRule j) throws Exception {
        MatrixProject project = j.createProject(MatrixProject.class);
        String combinationFilter = "jenkins.model.Jenkins.getInstance().setSystemMessage('hacked')";
        expectRejection(project, combinationFilter, "staticMethod jenkins.model.Jenkins getInstance");
        assertNull(j.jenkins.getSystemMessage());
        expectRejection(project, combinationFilter, "method jenkins.model.Jenkins setSystemMessage java.lang.String");
        assertNull(j.jenkins.getSystemMessage());
        project.setCombinationFilter(combinationFilter);
        assertEquals("hacked", j.jenkins.getSystemMessage(), "you asked for it");
    }

    private static void expectRejection(MatrixProject project, String combinationFilter, String signature) throws IOException {
        ScriptApproval scriptApproval = ScriptApproval.get();
        assertEquals(Collections.emptySet(), scriptApproval.getPendingSignatures());
        try {
            project.setCombinationFilter(combinationFilter);
        } catch (RejectedAccessException x) {
            assertEquals(signature, x.getSignature(), Functions.printThrowable(x));
        }
        Set<ScriptApproval.PendingSignature> pendingSignatures = scriptApproval.getPendingSignatures();
        assertEquals(1, pendingSignatures.size());
        assertEquals(signature, pendingSignatures.iterator().next().signature);
        scriptApproval.approveSignature(signature);
        assertEquals(Collections.emptySet(), scriptApproval.getPendingSignatures());
    }

    @Issue("JENKINS-34389")
    @Test
    void axisValuesChanged(JenkinsRule j) throws Exception {
        // create project with dynamic axis
        MatrixProject project = j.createProject(MatrixProject.class);
        project.setAxes(new AxisList(
                new DynamicTestAxis("axis")
        ));
        project.setConcurrentBuild(true);

        // build project
        QueueTaskFuture<MatrixBuild> matrixBuildQueue = project.scheduleBuild2(0);
        matrixBuildQueue.waitForStart();

        QueueTaskFuture<MatrixBuild> matrixBuildQueue2 = project.scheduleBuild2(0);

        MatrixBuild build = matrixBuildQueue.get();
        MatrixBuild build2 = matrixBuildQueue2.get();

        // get axes from build
        AxisList axes = (AxisList) FieldUtils.readField(build, "axes", true);
        AxisList axes2 = (AxisList) FieldUtils.readField(build2, "axes", true);

        // check if axes are valid
        assertArrayEquals(axes.get(0).getValues().toArray(), Arrays.asList("1", "10").toArray());
        assertArrayEquals(axes2.get(0).getValues().toArray(), Arrays.asList("2", "20").toArray());
    }
}
