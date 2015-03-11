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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import hudson.model.Item;
import hudson.security.AuthorizationMatrixProperty;
import hudson.security.ProjectMatrixAuthorizationStrategy;

import java.util.Collections;

import org.acegisecurity.context.SecurityContextHolder;

import com.gargoylesoftware.htmlunit.xml.XmlPage;

import hudson.Functions;

import java.io.IOException;
import java.util.Set;

import org.jenkinsci.plugins.scriptsecurity.sandbox.RejectedAccessException;
import org.jenkinsci.plugins.scriptsecurity.scripts.ScriptApproval;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * @author Alan Harder
 */
public class MatrixTest {

    @Rule public JenkinsRule j = new JenkinsRule();

    /**
     * Test that spaces are encoded as %20 for project name, axis name and axis value.
     */
    @Test public void spaceInUrl() {
        MatrixProject mp = new MatrixProject("matrix test");
        MatrixConfiguration mc = new MatrixConfiguration(mp, Combination.fromString("foo bar=baz bat"));
        assertEquals("job/matrix%20test/", mp.getUrl());
        assertEquals("job/matrix%20test/foo%20bar=baz%20bat/", mc.getUrl());
    }
    
    /**
     * Test that project level permissions apply to child configurations as well.
     */
    @Issue("JENKINS-9293")
    @Test public void configurationACL() throws Exception {
        j.jenkins.setAuthorizationStrategy(new ProjectMatrixAuthorizationStrategy());
        MatrixProject mp = j.createMatrixProject();
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

    @Test public void api() throws Exception {
        MatrixProject project = j.createMatrixProject();
        project.setAxes(new AxisList(
                new Axis("FOO", "abc", "def"),
                new Axis("BAR", "uvw", "xyz")));
        XmlPage xml = j.createWebClient().goToXml(project.getUrl() + "api/xml");
        assertEquals(4, xml.getByXPath("//matrixProject/activeConfiguration").size());
    }

    @Issue("JENKINS-27162")
    @Test public void completedLogging() throws Exception {
        MatrixProject project = j.createMatrixProject();
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
    @Test public void combinationFilterSecurity() throws Exception {
        MatrixProject project = j.createMatrixProject();
        String combinationFilter = "jenkins.model.Jenkins.getInstance().setSystemMessage('hacked')";
        expectRejection(project, combinationFilter, "staticMethod jenkins.model.Jenkins getInstance");
        assertNull(j.jenkins.getSystemMessage());
        expectRejection(project, combinationFilter, "method jenkins.model.Jenkins setSystemMessage java.lang.String");
        assertNull(j.jenkins.getSystemMessage());
        project.setCombinationFilter(combinationFilter);
        assertEquals("you asked for it", "hacked", j.jenkins.getSystemMessage());
    }
    private static void expectRejection(MatrixProject project, String combinationFilter, String signature) throws IOException {
        ScriptApproval scriptApproval = ScriptApproval.get();
        assertEquals(Collections.emptySet(), scriptApproval.getPendingSignatures());
        try {
            project.setCombinationFilter(combinationFilter);
        } catch (RejectedAccessException x) {
            assertEquals(Functions.printThrowable(x), signature, x.getSignature());
        }
        Set<ScriptApproval.PendingSignature> pendingSignatures = scriptApproval.getPendingSignatures();
        assertEquals(1, pendingSignatures.size());
        assertEquals(signature, pendingSignatures.iterator().next().signature);
        scriptApproval.approveSignature(signature);
        assertEquals(Collections.emptySet(), scriptApproval.getPendingSignatures());
    }
}
