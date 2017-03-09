/*
 * The MIT License
 *
 * Copyright (c) 2015 Red Hat, Inc.
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;

import hudson.model.JDK;

import org.hamcrest.collection.IsEmptyCollection;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;

import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;

public class AxisTest {

    public @Rule JenkinsRule j = new JenkinsRule();

    private MatrixProject p;
    private WebClient wc;

    @Before
    public void setUp() throws Exception {
        wc = j.createWebClient();
        p = j.createMatrixProject();

        // Setup to make all axes available
        j.jenkins.getJDKs().add(new JDK("jdk1.7", "/fake/home"));
        j.createSlave();
    }

    @Test
    public void submitEmptyAxisName() throws Exception {
        wc.setThrowExceptionOnFailingStatusCode(false);

        final String expectedMsg = "Matrix axis name '' is invalid: Axis name can not be empty";
        assertFailedWith(expectedMsg, withName("", "User-defined Axis"));
        assertFailedWith(expectedMsg, withName("", "Slaves"));
        assertFailedWith(expectedMsg, withName("", "Label expression"));
    }

    @Test
    public void submitInvalidAxisName() throws Exception {
        wc.setThrowExceptionOnFailingStatusCode(false);

        String expectedMsg = "Matrix axis name 'a,b' is invalid: ‘,’ is an unsafe character";
        assertFailedWith(expectedMsg, withName("a,b", "User-defined Axis"));
        assertFailedWith(expectedMsg, withName("a,b", "Slaves"));
        assertFailedWith(expectedMsg, withName("a,b", "Label expression"));

        expectedMsg = "Matrix axis name 'a=b' is invalid: ‘=’ is an unsafe character";
        assertFailedWith(expectedMsg, withName("a=b", "User-defined Axis"));
        assertFailedWith(expectedMsg, withName("a=b", "Slaves"));
        assertFailedWith(expectedMsg, withName("a=b", "Label expression"));
    }

    @Test
    public void submitInvalidAxisValue() throws Exception {
        wc.setThrowExceptionOnFailingStatusCode(false);

        HtmlForm form = addAxis("User-defined Axis");
        form.getInputByName("_.name").setValueAttribute("a_name");
        form.getInputByName("_.valueString").setValueAttribute("a,b");
        assertFailedWith("Matrix axis value 'a,b' is invalid: ‘,’ is an unsafe character", j.submit(form));

        form = addAxis("Label expression");
        form.getInputByName("_.name").setValueAttribute("a_name");
        form.getElementsByAttribute("textarea", "name", "values").get(0).setTextContent("a,b");
        assertFailedWith("Matrix axis value 'a,b' is invalid: ‘,’ is an unsafe character", j.submit(form));
    }

    @Test
    public void emptyAxisValueListResultInNoConfigurations() throws Exception {
        emptyValue("User-defined Axis");
        emptyValue("Slaves");
        emptyValue("Label expression");
        emptyValue("JDK");

        MatrixBuild build = j.buildAndAssertSuccess(p);
        assertThat(build.getRuns(), new IsEmptyCollection<MatrixRun>());
        assertThat(p.getItems(), new IsEmptyCollection<MatrixConfiguration>());

        for (Axis axis : p.getAxes()) {
            assertEquals("", axis.getValueString());
        }
    }

    private HtmlPage withName(String value, String axis) throws Exception {
        HtmlForm form = addAxis(axis);
        form.getInputByName("_.name").setValueAttribute(value);
        HtmlPage ret = j.submit(form);
        return ret;
    }

    private HtmlPage emptyValue(String axis) throws Exception {
        HtmlForm form = addAxis(axis);
        if (!"JDK".equals(axis)) { // No "name" attribute
            form.getInputByName("_.name").setValueAttribute("some_name");
        }

        HtmlPage ret = j.submit(form);
        return ret;
    }

    private void assertFailedWith(String expected, HtmlPage res) {
        String actual = res.getWebResponse().getContentAsString();

        assertThat(actual, res.getWebResponse().getStatusCode(), equalTo(400));
        assertThat(actual, containsString(expected));
    }

    private HtmlForm addAxis(String axis) throws Exception {
        HtmlPage page = wc.getPage(p, "configure");
        HtmlForm form = page.getFormByName("config");
        form.getButtonByCaption("Add axis").click();
        page.getAnchorByText(axis).click();
        return form;
    }
}
