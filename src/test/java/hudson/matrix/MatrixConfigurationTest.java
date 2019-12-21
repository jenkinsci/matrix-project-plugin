/*
 * The MIT License
 * 
 * Copyright (c), Red Hat, Inc.
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

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Collection;

import org.hamcrest.Matchers;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;

import com.gargoylesoftware.htmlunit.html.HtmlPage;

public class MatrixConfigurationTest {
    
    @Rule public JenkinsRule r = new JenkinsRule();
    
    @Test
    public void testDelete() throws Exception{
        MatrixProject project = r.createProject(MatrixProject.class);
        AxisList axes = new AxisList(
            new Axis("a","active1","active2", "unactive"));
        project.setAxes(axes);
        project.setCombinationFilter("a!=\"unactive\"");
        MatrixConfiguration toDelete = project.getItem("a=unactive");
        toDelete.delete();
        assertFalse("Configuration should be deleted for disk", toDelete.getRootDir().exists());
        assertNull("Configuration should be deleted from parent matrix project", project.getItem(toDelete.getCombination()));
        MatrixConfiguration notDelete = project.getItem("a=active1");
        notDelete.delete();
        assertTrue("Active configuration should not be deleted for disk", notDelete.getRootDir().exists());
        assertNotNull("Active configuration should not be deleted from parent matrix project", project.getItem(notDelete.getCombination()));
        assertFalse("Active configuration should not be disabled,", notDelete.isDisabled());
    }
    
    @Test @Issue("JENKINS-32423")
    public void doNotServeConfigurePage() throws Exception {
        MatrixProject p = r.createProject(MatrixProject.class);
        p.setAxes(new AxisList(new Axis("a", "b")));

        WebClient wc = r.createWebClient();
        wc.getOptions().setThrowExceptionOnFailingStatusCode(false);
        wc.getOptions().setPrintContentOnFailingStatusCode(false);

        HtmlPage page = wc.getPage(p.getItem("a=b"), "configure");
        assertEquals("Page should not exist", 404, page.getWebResponse().getStatusCode());
    }

    @Test
    public void labelAxis() throws Exception {
        LabelAxis label = new LabelAxis("label", Arrays.asList("a", "b"));
        LabelExpAxis expr = new LabelExpAxis("expr", Arrays.asList("a||b", "a&&b"));

        MatrixProject labelP = r.createProject(MatrixProject.class);
        labelP.setAxes(new AxisList(label));
        MatrixProject exprP = r.createProject(MatrixProject.class);
        exprP.setAxes(new AxisList(expr));
        MatrixProject combinedP = r.createProject(MatrixProject.class);
        combinedP.setAxes(new AxisList(expr, label));

        Collection<MatrixConfiguration> lc = labelP.getItems();
        assertThat(lc, Matchers.<MatrixConfiguration>iterableWithSize(2));
        assertEquals("a", labelP.getItem("label=a").getAssignedLabel().toString());
        assertEquals("b", labelP.getItem("label=b").getAssignedLabel().toString());

        Collection<MatrixConfiguration> ec = exprP.getItems();
        assertThat(ec, Matchers.<MatrixConfiguration>iterableWithSize(2));
        assertEquals("a||b", exprP.getItem("expr=a||b").getAssignedLabel().toString());
        assertEquals("a&&b", exprP.getItem("expr=a&&b").getAssignedLabel().toString());

        Collection<MatrixConfiguration> cc = combinedP.getItems();
        assertThat(cc, Matchers.<MatrixConfiguration>iterableWithSize(4));
        assertEquals("a&&b&&a", combinedP.getItem("expr=a&&b,label=a").getAssignedLabel().toString());
        assertEquals("a&&b&&b", combinedP.getItem("expr=a&&b,label=b").getAssignedLabel().toString());
        assertEquals("a||b&&a", combinedP.getItem("expr=a||b,label=a").getAssignedLabel().toString());
        assertEquals("a||b&&b", combinedP.getItem("expr=a||b,label=b").getAssignedLabel().toString());
    }
}
